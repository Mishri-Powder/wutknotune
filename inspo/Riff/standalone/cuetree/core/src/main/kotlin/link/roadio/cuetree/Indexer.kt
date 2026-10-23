// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.CachedMediaInput
import java.io.IOException
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class ScanOptions(
    val forceRefresh: Boolean = false,
    val metadataRevision: String? = null,
    val pipelineDepth: Int = 3,
    val mediaExtensions: Set<String> = setOf("mp3", "flac", "ogg", "opus", "m4a", "m4b", "mp4", "wav", "aac", "aiff", "alac"),
)

enum class ScanCheckpointKind { MEDIA, EXPLICIT_TABLE, DIRECTORY, STAGE }

enum class ScanStage { DISCOVERY, ACQUISITION }

private data class HashKey(
    val source: Int,
    val stamp: Stamp,
    val span: Span?,
    val low: Boolean,
)

data class ScanProgress(
    val completedMedia: Long,
    val completedDirectories: Long,
    val completedCueSheets: Long,
    val kind: ScanCheckpointKind,
    val factsRevision: Long = 0,
    val stage: ScanStage = ScanStage.ACQUISITION,
    val totalMedia: Long = 0,
)

/** Each scan owns a working set. Provider handles and parsed album candidates are transient. */
class Indexer(
    private val identity: MediaIdGenerator,
    private val ttl: Long,
    providers: List<SourceProvider> = listOf(PosixProvider()),
    private val metadata: MetadataReader = CuegrainMetadataReader(),
    private val genres: GenreResolver = GenreResolver { GenreRef(text = canonical(it)) },
    private val ids: IdAllocator = RandomIds,
    private val grouping: AlbumGroupingPolicy = DefaultAlbumGroupingPolicy,
    private val vocabulary: CatalogVocabulary = CatalogVocabulary.EMPTY,
) {
    init {
        require(ttl >= 0)
    }

    private val providers = providers.associateBy { it.id }.also { require(it.size == providers.size) }

    fun scan(
        roots: List<RootSpec>,
        previous: Snapshot = Snapshot.empty(identity.scheme),
        generation: Long,
        options: ScanOptions = ScanOptions(),
        cancellation: Cancellation = Cancellation(),
        onCheckpoint: ((ScanProgress, () -> Snapshot) -> Boolean)? = null,
        onObservation: ((Node, Observation) -> Unit)? = null,
        onSource: ((Node, SourceProvider, SourceHandle) -> Unit)? = null,
        acquisitionGuard: AcquisitionGuard = UnguardedAcquisition,
    ): Snapshot {
        require(previous.identityScheme == identity.scheme) { "Identity scheme mismatch; rebuild with the intended generator" }
        require(generation > previous.generation && generation >= 0)
        require(roots.map { it.provider to it.locator }.distinct().size == roots.size)
        require(roots.all { it.provider in providers }) { "Missing source provider" }
        require(options.pipelineDepth in 1..8)
        require(previous.nodes.isEmpty() || options.metadataRevision == null || previous.metadataRevision == options.metadataRevision) {
            "Metadata revision mismatch; completely recreate the index"
        }
        validate(previous)
        return Scan(roots, previous, generation, options, cancellation, onCheckpoint, onObservation, onSource, acquisitionGuard).run()
    }

    private inner class Scan(
        val roots: List<RootSpec>,
        val previous: Snapshot,
        var generation: Long,
        val options: ScanOptions,
        val cancel: Cancellation,
        val onCheckpoint: ((ScanProgress, () -> Snapshot) -> Boolean)?,
        val onObservation: ((Node, Observation) -> Unit)?,
        val onSource: ((Node, SourceProvider, SourceHandle) -> Unit)?,
        val acquisitionGuard: AcquisitionGuard,
        val acquisitionLock: ReentrantReadWriteLock = ReentrantReadWriteLock(true),
    ) {
        // Update incompatibility is rejected before scanning; successful kinds need no completion bit.
        // https://wiki.271.pics/Cuetree/persistence
        private val seed = previous
        private var stage = ScanStage.DISCOVERY
        private val pendingFiles = mutableListOf<FileWork>()
        var catalogPrevious = seed
        var tablesStarted = false
        val nodes = seed.nodes.map<Node, Node?> { it }.toMutableList()
        val seen = mutableSetOf<Int>()
        val acquired = mutableSetOf<Int>()
        val unidentified = mutableSetOf<Int>()
        val handles = mutableMapOf<Int, Pair<SourceProvider, SourceHandle>>()
        val observations = mutableMapOf<Int, Observation>()
        private val hashes = mutableMapOf<HashKey, MediaId>()
        private val sourceFailures = mutableMapOf<Int, PreparedFile>()
        val sheets = mutableListOf<Sheet>()
        private var materializationMedia: Map<Long, List<Int>>? = null
        private var materializationFragments: MutableMap<Pair<MediaId?, Span?>, MutableList<Int>>? = null
        private var materializationDirectories: Map<MediaId, Int>? = null
        val announcedTables = mutableSetOf<Pair<Int, Int>>()
        private var totalMedia = 0L
        var completedMedia = 0L
        var completedDirectories = 0L
        var completedCueSheets = 0L
        var factsRevision = 0L

        fun changed(
            before: Node?,
            after: Node?,
        ) {
            fun comparable(node: Node?) = node?.copy(life = node.life.copy(generation = 0))
            if (comparable(before) != comparable(after)) factsRevision++
        }

        val diagnostics = mutableListOf<Diagnostic>()
        val explicit = mutableListOf<ExplicitAlbum>()
        val expansions = mutableListOf<ChapterExpansion>()
        val folderIds =
            previous.nodes
                .filter { it.kind == NodeKind.DIRECTORY || it.kind == NodeKind.METADATA }
                .map { it.id.hi }
                .toMutableSet()
        val physical = mutableMapOf<Pair<MediaId?, String>, Int>()

        fun run(): Snapshot {
            expire()
            nodes.indices.filter { nodes[it]?.kind != NodeKind.FRAGMENT && nodes[it] != null }.forEach { i ->
                val n = nodes[i]!!
                physical[n.parentId to n.name] = i
            }
            for (root in roots) {
                cancel.check()
                val old =
                    nodes.indexOfFirst { node ->
                        node?.isRoot == true &&
                            node.source?.provider == root.provider &&
                            if (root.identity == null) {
                                node.attributes[ROOT_IDENTITY_ATTRIBUTE] == null && node.source.locator == root.locator
                            } else {
                                node.attributes[ROOT_IDENTITY_ATTRIBUTE]?.singleOrNull() == root.identity ||
                                    (node.attributes[ROOT_IDENTITY_ATTRIBUTE] == null && node.source.locator == root.locator)
                            }
                    }
                val source = SourceInfo(root.label, provider = root.provider, locator = root.locator)
                val index = if (old >= 0) old else add(Node(NodeKind.DIRECTORY, newFolderId(), source = source, life = Life(generation, Flags.ACSS)))
                val attributes =
                    nodes[index]!!.attributes - ROOT_IDENTITY_ATTRIBUTE - "mediaKind" +
                        (if (root.book) mapOf("mediaKind" to listOf("2")) else emptyMap()) +
                        (root.identity?.let { mapOf(ROOT_IDENTITY_ATTRIBUTE to listOf(it)) } ?: emptyMap())
                val before = nodes[index]
                nodes[index] = nodes[index]!!.copy(source = source, attributes = attributes)
                changed(before, nodes[index])
                val provider = providers.getValue(root.provider)
                try {
                    walk(index, provider, provider.root(root.locator), root.initialStat)
                } catch (e: IOException) {
                    problem("ACCESS", root.label, e)
                }
            }
            nodes.indices.filter { nodes[it]?.kind != NodeKind.FRAGMENT && nodes[it] != null && it !in seen }.forEach(::missing)
            totalMedia = pendingFiles.count { it.hint != "cue" }.toLong()
            stage = ScanStage.ACQUISITION
            checkpoint(ScanCheckpointKind.STAGE)
            processFiles()
            unidentified.forEach { nodes[it] = null }
            resolveCollisions(mediaOnly = true)
            processTables()
            nodes.indices.filter { nodes[it]?.kind == NodeKind.FRAGMENT && it !in seen }.forEach(::missing)
            expire()
            resolveCollisions()
            nodes.indices.filter { nodes[it]?.kind == NodeKind.FRAGMENT }.forEach { i ->
                if (matchingBase(nodes[i]!!.parentId, availableOnly = true) == null) missing(i)
            }
            expire()
            cancel.check()
            return finish()
        }

        fun newFolderId(): MediaId {
            repeat(100) {
                val id = ids.next()
                if (folderIds.add(id)) return MediaId(id)
            }
            error("ID allocator repeatedly returned existing Folder IDs")
        }

        fun add(node: Node): Int {
            nodes += node
            factsRevision++
            return nodes.lastIndex
        }

        fun problem(
            code: String,
            name: String,
            e: Exception,
        ) {
            diagnostics += Diagnostic(code, name, e.message ?: e.javaClass.simpleName)
        }

        fun missing(index: Int) {
            nodes[index]?.let {
                nodes[index] = it.copy(life = it.life.observe(false, generation))
                changed(it, nodes[index])
            }
        }

        fun directory(id: MediaId?): Int? {
            if (id == null) return null
            return materializationDirectories?.get(id)
                ?: nodes.indices.firstOrNull { nodes[it]?.kind == NodeKind.DIRECTORY && nodes[it]?.id == id }
        }

        fun expire() {
            // Expiration may remove prior unavailable facts before the first checkpoint.
            val before = nodes.toList()
            expireNodes(nodes, generation, ttl)
            if (before != nodes) factsRevision++
        }

        fun admit(
            parent: Int,
            name: String,
            kind: NodeKind,
        ): Int {
            val parentId = nodes[parent]!!.id
            val existing = physical[parentId to name]
            if (existing != null && nodes[existing]?.kind == kind) return existing
            if (existing != null) {
                nodes[existing] = null
                expire()
            }
            val index = add(Node(kind, if (kind == NodeKind.DIRECTORY || kind == NodeKind.METADATA) newFolderId() else MediaId(0), parentId, SourceInfo(name), life = Life(generation, Flags.ACSS)))
            if (kind == NodeKind.MEDIA) unidentified += index
            physical[parentId to name] = index
            return index
        }

        fun walk(
            index: Int,
            provider: SourceProvider,
            handle: SourceHandle,
            listedStat: SourceStat?,
        ) {
            cancel.check()
            val stat = listedStat ?: provider.stat(handle)
            if (!stat.directory) throw IOException("Directory became a file")
            val children = provider.children(handle)
            if (children.map { it.name }.distinct().size != children.size || children.any { !component(it.name) }) throw IOException("Ambiguous or invalid child names")
            val n = nodes[index]!!
            nodes[index] = n.copy(source = n.source!!.copy(modified = stat.stamp.modified, size = stat.stamp.size), life = Life(generation))
            changed(n, nodes[index])
            seen += index
            handles[index] = provider to handle
            val files = mutableListOf<FileWork>()
            val listingEvidence = mutableListOf<Pair<String, SourceStat>>()
            var completeEvidence = true
            for (child in children.sortedBy { it.name }) {
                cancel.check()
                try {
                    val statChild = child.stat ?: provider.stat(child.handle)
                    listingEvidence += child.name to statChild
                    if (statChild.directory) {
                        walk(admit(index, child.name, NodeKind.DIRECTORY), provider, child.handle, statChild)
                    } else {
                        val hint = child.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val kind =
                            when {
                                hint == "cue" -> NodeKind.METADATA
                                (statChild.mediaCandidate ?: (hint in options.mediaExtensions)) -> NodeKind.MEDIA
                                else -> null
                            }
                        if (kind != null) {
                            val existing =
                                physical[nodes[index]!!.id to child.name]
                                    ?.takeIf { nodes[it]?.kind == kind || nodes[it]?.kind == NodeKind.FAILEDL1 }
                            val old =
                                existing?.let { nodes[it]!! }
                                    ?: Node(kind, MediaId(0), nodes[index]!!.id, SourceInfo(child.name), life = Life(generation, Flags.ACSS))
                            if (existing != null) {
                                seen += existing
                                if (old.source?.stamp != statChild.stamp) {
                                    val pending = old.copy(identityFlags = old.identityFlags and Flags.FAILEDL2.inv())
                                    nodes[existing] = pending
                                    changed(old, pending)
                                    missing(existing)
                                } else if (old.kind == NodeKind.FAILEDL1) {
                                    nodes[existing] = old.copy(life = Life(generation))
                                } else {
                                    handles[existing] = provider to child.handle
                                    if (old.kind == NodeKind.MEDIA && old.tables.isNotEmpty()) {
                                        observations[existing] = Observation(emptyMap(), old.tables, emptyList())
                                    }
                                }
                            }
                            files += FileWork(existing ?: -1, index, child.name, provider, child.handle, statChild.stamp, hint, old, existing == null)
                        }
                    }
                } catch (e: IOException) {
                    completeEvidence = false
                    problem("ACCESS", child.name, e)
                }
            }
            val listedSize = FilenameOrdinals.listedSize(listingEvidence, completeEvidence)
            val directory = requireNotNull(nodes[index])
            val attributes = directory.attributes - FilenameOrdinals.SIZE + (listedSize?.let { mapOf(FilenameOrdinals.SIZE to listOf(it.toString())) } ?: emptyMap())
            nodes[index] = directory.copy(attributes = attributes)
            changed(directory, nodes[index])
            pendingFiles += files
            completedDirectories++
            checkpoint(ScanCheckpointKind.DIRECTORY)
        }

        private fun source(file: FileWork) = SourceAcquisition(file.provider, file.handle, file.stamp, nodes[file.directory]!!.id, file.name, file.hint)

        private fun processFiles() {
            val work = pendingFiles.groupBy { it.directory }.values.flatMap { it.shuffled() }

            fun mode(file: FileWork): AcquisitionMode =
                if (file.old.kind == NodeKind.FAILEDL1 && file.old.source?.stamp == file.stamp) {
                    AcquisitionMode.NORMAL
                } else {
                    acquisitionGuard.mode(source(file))
                }
            val isolated = work.filter { mode(it) != AcquisitionMode.NORMAL }
            val normal = work.filter { mode(it) == AcquisitionMode.NORMAL }

            fun consume(result: PreparedFile) {
                val file = result.file
                val failed = physical[nodes[file.directory]!!.id to file.name]?.let(sourceFailures::get)
                val prepared =
                    if (failed != null && failed.file.stamp == file.stamp) {
                        failed.copy(file = failed.file.copy(stamp = file.stamp), error = null, failedSkip = true)
                    } else {
                        result
                    }
                if (prepared.error != null || prepared.failedSkip) {
                    acceptFailure(prepared)
                    if (prepared.error != null) {
                        problem("SOURCE_FAILED", file.name, prepared.error)
                        acquisitionGuard.failed(source(file))
                    }
                } else if (file.hint == "cue") {
                    acceptSheet(prepared)
                    announceReadyTables()
                } else {
                    val mediaIndex = acceptMedia(prepared)
                    if (observations[mediaIndex]?.tables?.any { table ->
                            table.sections.size > 1 &&
                                table.sections.all { it.reference == null } &&
                                (table.attributes.first("album") != null || table.attributes.first("title") != null)
                        } == true
                    ) {
                        checkpoint(ScanCheckpointKind.EXPLICIT_TABLE)
                    }
                    announceReadyTables()
                }
                if (file.hint == "cue") completedCueSheets++ else completedMedia++
                acquisitionGuard.resolved(source(file))
                checkpoint(ScanCheckpointKind.MEDIA)
            }

            for (file in isolated) {
                cancel.check()
                consume(prepareFile(file, cancel, mode(file)))
            }
            processOrderedPipeline(
                normal,
                options.pipelineDepth,
                {
                    try {
                        cancel.check()
                        false
                    } catch (_: java.util.concurrent.CancellationException) {
                        true
                    }
                },
                "CuetreeMetadata",
                prepare = { file, cancelled -> prepareFile(file, Cancellation(cancelled), AcquisitionMode.NORMAL) },
                consume = ::consume,
            )
        }

        private fun acceptFailure(prepared: PreparedFile) {
            val file = prepared.file
            val index =
                if (file.index >= 0) {
                    file.index
                } else {
                    val technical = MediaId(runCatching { file.provider.bindingId(file.handle) }.getOrNull() ?: newFolderId().hi)
                    add(Node(NodeKind.FAILEDL1, technical, nodes[file.directory]!!.id, SourceInfo(file.name), life = Life(generation), identityFlags = 0))
                }
            val current = nodes[index]!!
            val old =
                previous.nodes.getOrNull(index)?.takeIf {
                    it.parentId == current.parentId && it.name == current.name
                } ?: current
            val failed =
                old.copy(
                    kind = NodeKind.FAILEDL1,
                    id = if (file.newAdmission && file.index >= 0 && current.kind != NodeKind.FAILEDL1) MediaId(file.provider.bindingId(file.handle) ?: newFolderId().hi) else old.id,
                    source = old.source!!.copy(modified = file.stamp.modified, size = file.stamp.size),
                    life = Life(generation),
                    identityFlags = if (file.newAdmission) 0 else old.identityFlags and Flags.FAILEDL2.inv(),
                )
            nodes[index] = failed
            changed(old, failed)
            physical[failed.parentId to failed.name] = index
            unidentified -= index
            seen += index
            handles.remove(index)
            observations.remove(index)
            sourceFailures[index] = prepared.copy(file = file.copy(index = index))
        }

        fun prepareFile(
            file: FileWork,
            cancellation: Cancellation,
            mode: AcquisitionMode,
        ): PreparedFile =
            acquisitionLock.read {
                val old = file.old
                if (old.kind == NodeKind.FAILEDL1 && old.source?.stamp == file.stamp) {
                    return PreparedFile(file, false, null, old.id, failedSkip = true)
                }
                if (mode == AcquisitionMode.QUARANTINE) {
                    return PreparedFile(file, false, null, old.id, IOException("Repeated Indexer termination during isolated L1 acquisition"))
                }
                // The directory observation gates refresh; a scan assumes source stability during acquisition.
                // https://wiki.271.pics/Cuetree/API/Indexing#indexer
                val refreshIdentity = options.forceRefresh || old.source?.stamp != file.stamp || file.newAdmission || (old.mediaId == null && file.hint != "cue")
                val refresh = refreshIdentity
                if (!refresh) return PreparedFile(file, false, if (file.hint == "cue") Observation(emptyMap(), old.tables, emptyList()) else null, old.id)
                val descriptor = source(file)
                return try {
                    acquisitionGuard.acquire(descriptor, mode, cancellation) {
                        val observation: Observation
                        val id: MediaId
                        file.provider.open(file.handle).use { input ->
                            val shared = CachedMediaInput(input)
                            val acquisition =
                                metadata.acquire(
                                    shared,
                                    file.hint,
                                    cancellation,
                                    if (file.hint != "cue" && refreshIdentity) ({ source -> identity.high(source, null, cancellation) }) else null,
                                )
                            observation = acquisition.observation
                            id =
                                if (file.hint == "cue" || !refreshIdentity) {
                                    old.id
                                } else {
                                    val high = requireNotNull(acquisition.high)
                                    val low = if (!file.newAdmission && old.id.hi == high && old.id.lo != null) identity.low(shared, null, cancellation) else null
                                    MediaId(high, low)
                                }
                        }
                        PreparedFile(file, true, observation, id, identityRefreshed = refreshIdentity)
                    }
                } catch (error: SourceAcquisitionFailure) {
                    PreparedFile(file, true, null, old.id, error)
                }
            }

        fun acceptMedia(prepared: PreparedFile): Int {
            val file = prepared.file
            val index = if (file.index >= 0) file.index else admit(file.directory, file.name, NodeKind.MEDIA)
            val old = nodes[index]!!
            val observation = prepared.observation
            val id = prepared.id
            val tables = observation?.tables ?: old.tables
            nodes[index] =
                old.copy(
                    kind = NodeKind.MEDIA,
                    id = id,
                    source = old.source!!.copy(modified = file.stamp.modified, size = file.stamp.size),
                    attributes = observation?.attributes?.let(::normalizeAttributes) ?: old.attributes,
                    tables = tables,
                    sourceEvidence = observation?.sourceEvidence ?: old.sourceEvidence,
                    life = Life(generation),
                    identityFlags = (if (prepared.identityRefreshed) (if (id.lo == null || (old.identityFlags and Flags.SHRT != 0 && old.id.hi == id.hi && (old.id.lo == null || old.id.lo == id.lo))) Flags.SHRT else 0) else old.identityFlags) or (if (old.source.stamp == file.stamp) old.identityFlags and Flags.FAILEDL2 else 0),
                )
            changed(old, nodes[index])
            if (tables.isNotEmpty() || old.identityFlags and Flags.RESL != 0) factsRevision++
            unidentified -= index
            if (tables.isNotEmpty()) observations[index] = Observation(emptyMap(), tables, emptyList())
            observation?.let {
                diagnostics += it.diagnostics
                if (prepared.refreshed) onObservation?.invoke(requireNotNull(nodes[index]), it)
            }
            if (prepared.identityRefreshed) acquired += index
            seen += index
            handles[index] = file.provider to file.handle
            onSource?.invoke(requireNotNull(nodes[index]), file.provider, file.handle)
            return index
        }

        fun checkpoint(kind: ScanCheckpointKind) {
            var offered: Snapshot? = null
            val published =
                try {
                    onCheckpoint?.invoke(ScanProgress(completedMedia, completedDirectories, completedCueSheets, kind, factsRevision, stage, totalMedia)) {
                        if (offered == null) offered = partialSnapshot()
                        requireNotNull(offered)
                    } ?: false
                } catch (e: IOException) {
                    // Provider errors are recoverable; a failed publication callback is not.
                    throw IllegalStateException("Checkpoint publication failed", e)
                }
            if (published) {
                require(offered != null) { "A successful checkpoint must request its snapshot" }
                catalogPrevious = requireNotNull(offered)
                require(generation < Long.MAX_VALUE) { "Observation generation exhausted" }
                generation++
            } else {
                offered?.let { catalogPrevious = it }
            }
        }

        fun acceptSheet(prepared: PreparedFile) {
            val file = prepared.file
            val index = if (file.index >= 0) file.index else admit(file.directory, file.name, NodeKind.METADATA)
            val old = nodes[index]!!
            val observation = requireNotNull(prepared.observation)
            nodes[index] =
                old.copy(
                    kind = NodeKind.METADATA,
                    source = old.source!!.copy(modified = file.stamp.modified, size = file.stamp.size),
                    tables = observation.tables,
                    life = Life(generation),
                    identityFlags = 0,
                )
            changed(old, nodes[index])
            seen += index
            sheets += Sheet(file.directory, file.name, observation)
            if (observation.tables.isNotEmpty()) factsRevision++
        }

        fun tableReady(
            sheet: Sheet,
            table: TableObservation,
        ): Boolean =
            table.sections.size > 1 &&
                table.sections.all { section ->
                    val path = section.reference ?: return@all false
                    val target =
                        try {
                            reference(sheet.directory, path)
                        } catch (_: IOException) {
                            return@all false
                        }
                    target in seen && target !in unidentified && nodes[target]?.life?.available == true
                }

        fun announceReadyTables() {
            sheets.forEachIndexed { sheetIndex, sheet ->
                sheet.observation.tables.forEachIndexed { tableIndex, table ->
                    if (tableReady(sheet, table) && announcedTables.add(sheetIndex to tableIndex)) {
                        factsRevision++
                        checkpoint(ScanCheckpointKind.EXPLICIT_TABLE)
                    }
                }
            }
        }

        /** Copy the working set so a checkpoint cannot mutate the still-running traversal. */
        fun partialSnapshot(): Snapshot =
            acquisitionLock.write {
                if (stage == ScanStage.DISCOVERY) return retainedDiscoverySnapshot()
                val copy = Scan(roots, previous, generation, options, cancel, null, null, null, acquisitionGuard, acquisitionLock)
                copy.catalogPrevious = catalogPrevious
                copy.nodes.clear()
                copy.nodes.addAll(nodes)
                copy.seen.addAll(seen)
                copy.acquired.addAll(acquired)
                copy.unidentified.addAll(unidentified)
                copy.handles.putAll(handles)
                copy.observations.putAll(observations)
                copy.hashes.putAll(hashes)
                copy.sourceFailures.putAll(sourceFailures)
                copy.sheets.addAll(sheets)
                copy.diagnostics.addAll(diagnostics)
                copy.explicit.addAll(explicit)
                copy.expansions.addAll(expansions)
                copy.folderIds.addAll(folderIds)
                copy.physical.putAll(physical)
                copy.resolveCollisions(mediaOnly = true)
                if (!tablesStarted) copy.processTables(readyOnly = true)
                copy.resolveCollisions()
                copy.nodes.indices.filter { copy.nodes[it]?.kind == NodeKind.FRAGMENT }.forEach { i ->
                    if (copy.matchingBase(copy.nodes[i]!!.parentId, availableOnly = true) == null) copy.missing(i)
                }
                val result = copy.finish()
                hashes.putAll(copy.hashes)
                copy.sourceFailures.forEach { (index, failure) ->
                    if (index !in sourceFailures) {
                        acceptFailure(failure)
                        nodes[index] = copy.nodes[index]
                    }
                }
                return result
            }

        // L0 never acquires identity or rematerializes structures. Its old catalogs stay coherent.
        // https://wiki.271.pics/Cuetree/API/Progress
        private fun retainedDiscoverySnapshot(): Snapshot {
            val indices = nodes.indices.filter { nodes[it] != null }
            val mapping = indices.withIndex().associate { it.value to it.index }
            val physical = indices.map { nodes[it]!! }
            val factual = Snapshot(generation, identity.scheme, physical, metadataRevision = options.metadataRevision ?: previous.metadataRevision)
            val coherentNodes =
                physical.map { node ->
                    if (node.kind == NodeKind.FRAGMENT && !factual.available(requireNotNull(node.parentId))) {
                        node.copy(life = node.life.observe(false, generation))
                    } else {
                        node
                    }
                }
            val sources = factual.copy(nodes = coherentNodes)
            val albums =
                previous.albums.map { album ->
                    val positions = album.sources.mapNotNull(mapping::get)
                    val tracks = orderedAlbumTrackIds(coherentNodes, positions)
                    val available = (album.kind == AlbumKind.SINGLES && tracks.isEmpty()) || tracks.any(sources::available)
                    album.copy(sources = positions, tracks = tracks, life = album.life.referenced(positions.isNotEmpty(), available, generation))
                }
            val byId = albums.associateBy { it.id }

            fun available(ids: List<Long>) = ids.any { byId[it]?.life?.available == true }
            val artists = previous.artists.map { it.copy(life = it.life.referenced(it.albums.isNotEmpty(), available(it.albums), generation)) }
            val genres = previous.genres.map { it.copy(life = it.life.referenced(it.albums.isNotEmpty(), available(it.albums), generation)) }
            return sources
                .copy(albums = albums, artists = artists, genres = genres)
                .withAttributes(previous, ttl)
                .also(::validate)
                .ordered()
                .also(::validate)
        }

        fun processTables(readyOnly: Boolean = false) {
            tablesStarted = true
            // The media set is fixed while tables materialize. Indexing these lookups once avoids
            // rescanning the growing node vector for every CUE/TOC/CHAP section.
            materializationMedia = nodes.indices.filter { nodes[it]?.baseIdentity == true }.groupBy { nodes[it]!!.id.hi }
            materializationDirectories = nodes.indices.filter { nodes[it]?.kind == NodeKind.DIRECTORY }.associate { nodes[it]!!.id to it }
            materializationFragments =
                nodes.indices
                    .filter { nodes[it]?.kind == NodeKind.FRAGMENT }
                    .groupBy { nodes[it]!!.parentId to nodes[it]!!.span }
                    .mapValues { it.value.toMutableList() }
                    .toMutableMap()
            try {
                for ((owner, observation) in observations) {
                    val n = nodes[owner] ?: continue
                    val parent = directory(n.parentId) ?: continue
                    observation.tables.forEach { materialize(parent, n.name, owner, it) }
                }
                for (sheet in sheets) {
                    cancel.check()
                    try {
                        diagnostics += sheet.observation.diagnostics
                        sheet.observation.tables.forEach { table ->
                            if (!readyOnly || tableReady(sheet, table)) materialize(sheet.directory, sheet.name, -1, table)
                        }
                    } catch (e: IOException) {
                        problem("STRUCTURE_ACCESS", sheet.name, e)
                    }
                }
            } finally {
                materializationMedia = null
                materializationFragments = null
                materializationDirectories = null
            }
        }

        fun materialize(
            directory: Int,
            name: String,
            owner: Int,
            table: TableObservation,
        ) {
            try {
                require(table.sections.size > 1)
                val reserved = mutableSetOf<Int>()
                val prepared =
                    table.sections.mapIndexed { ordinal, section ->
                        cancel.check()
                        val requestedBase = if (section.reference == null) owner.takeIf { it >= 0 } ?: throw IOException("External sheet omitted its FILE") else reference(directory, section.reference)
                        val requested = nodes[requestedBase] ?: throw IOException("Missing referenced media")
                        val base = matchingBase(requested.id, availableOnly = true) ?: throw IOException("Referenced media unavailable")
                        val parentId = nodes[base]!!.id
                        // Field-level precedence preserves file tags only where both CUE scopes omit the field.
                        // https://wiki.271.pics/Cuetree/API/Indexing#indexer
                        val attrs = normalizeAttributes((requested.attributes - RIFF_FEATURE_ATTRIBUTES).overlaidBy(table.attributes - RIFF_FEATURE_ATTRIBUTES).overlaidBy(section.attributes)) + mapOf("fragment" to listOf((ordinal + 1).toString()), "totalFragments" to listOf(table.sections.size.toString()))
                        val matching =
                            materializationFragments?.get(parentId to section.span).orEmpty().filter { nodes[it] != null }
                        val oldIndex = matching.firstOrNull { it !in seen && it !in reserved && nodes[it]!!.attributes == attrs }
                        if (oldIndex != null) reserved += oldIndex
                        val evidence = matching.firstOrNull()?.let { nodes[it] }
                        val id = if (evidence != null && base !in acquired) evidence.id else hash(base, section.span, false)
                        val node = Node(NodeKind.FRAGMENT, id, parentId, span = section.span, attributes = attrs, life = Life(generation), identityFlags = (if (id.lo == null || evidence?.identityFlags?.and(Flags.SHRT) != 0) Flags.SHRT else 0))
                        oldIndex to node
                    }
                val members =
                    prepared.map { (oldIndex, node) ->
                        val index = oldIndex ?: add(node)
                        nodes[index] = node
                        materializationFragments?.getOrPut(node.parentId to node.span) { mutableListOf() }?.let { candidates ->
                            if (index !in candidates) candidates += index
                        }
                        seen += index
                        index
                    }
                // Preserve the observed section order until the album projection is built.
                val tableAttrs = normalizeAttributes(table.attributes)
                val referenced =
                    table.sections
                        .map { section ->
                            if (section.reference == null) owner else reference(directory, section.reference)
                        }.mapNotNull { nodes.getOrNull(it)?.attributes }
                val common =
                    referenced.firstOrNull().orEmpty().filter { (key, values) ->
                        key in setOf("album", "albumArtist", "artist", "year", "genre", "genreId", "mediaKind") &&
                            referenced.all { it[key] == values }
                    }
                val fallbackTitle =
                    if (owner >= 0) {
                        nodes[owner]?.attributes?.let { it.first("album") ?: it.first("title") }
                    } else {
                        common.first("album") ?: nodes[directory]?.name
                    }
                val titleFromMedia = tableAttrs.first("album") == null && tableAttrs.first("title") == null && fallbackTitle != null
                val title = tableAttrs.first("album") ?: tableAttrs.first("title") ?: fallbackTitle ?: "[[UNTITLED]]"
                val attrs = common.overlaidBy(tableAttrs).let { if (titleFromMedia) it + ("album" to listOf(requireNotNull(fallbackTitle))) else it }
                if (owner >= 0 && table.sections.all { it.reference == null } && tableAttrs.first("album") == null && tableAttrs.first("title") == null) {
                    expansions += ChapterExpansion(owner, members, title, attrs)
                } else {
                    explicit += ExplicitAlbum(members, title, attrs)
                }
            } catch (e: IOException) {
                problem("STRUCTURE_ACCESS", name, e)
            }
        }

        fun reference(
            directory: Int,
            path: String,
        ): Int {
            if (path.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(path)) throw IOException("Absolute CUE reference is not admitted")
            var index = directory
            val pieces = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            if (pieces.isEmpty()) throw IOException("Empty reference")
            for (piece in pieces) {
                val n = nodes[index] ?: throw IOException("Missing reference component")
                index =
                    if (piece == "..") {
                        directory(n.parentId) ?: throw IOException("Reference escapes root")
                    } else {
                        physical[n.id to piece] ?: throw IOException("Reference is not indexed: $piece")
                    }
            }
            if (nodes[index]?.kind != NodeKind.MEDIA) throw IOException("Reference is not media")
            return index
        }

        fun hash(
            base: Int,
            span: Span?,
            low: Boolean,
        ): MediaId =
            acquisitionLock.write {
                val (provider, handle) = handles[base] ?: throw IOException("No current source handle")
                val stamp = nodes[base]?.source?.stamp ?: throw IOException("Base media is no longer admitted")
                val key = HashKey(base, stamp, span, low)
                hashes[key]?.let { cached ->
                    cancel.check()
                    return cached
                }
                if (span != null) {
                    val known = requireNotNull(nodes[base]).id
                    // Tail acquisition for collision resolution is shared by all clips of this binding.
                    identity.fragmentComponent(known.hi, span, false, cancel)?.let { high ->
                        val value =
                            if (!low) {
                                high
                            } else {
                                val component = known.lo ?: hash(base, null, true).hi
                                requireNotNull(identity.fragmentComponent(component, span, true, cancel))
                            }
                        return MediaId(value).also { hashes[key] = it }
                    }
                }
                val node = requireNotNull(nodes[base])
                val file =
                    FileWork(
                        base,
                        requireNotNull(directory(node.parentId)),
                        node.name,
                        provider,
                        handle,
                        stamp,
                        node.name.substringAfterLast('.', "").lowercase(Locale.ROOT),
                        node,
                        base >= previous.nodes.size,
                    )
                val descriptor = source(file)
                try {
                    return acquisitionGuard.acquire(descriptor, acquisitionGuard.mode(descriptor), cancel) {
                        provider.open(handle).use { input ->
                            val value = if (low) identity.low(input, span, cancel) else identity.high(input, span, cancel)
                            MediaId(value).also { hashes[key] = it }
                        }
                    }
                } catch (error: SourceAcquisitionFailure) {
                    acceptFailure(PreparedFile(file, true, null, node.id, error))
                    acquisitionGuard.failed(descriptor)
                    throw error
                } finally {
                    acquisitionGuard.resolved(descriptor)
                }
            }

        fun matchingBase(
            id: MediaId?,
            availableOnly: Boolean = false,
        ): Int? {
            if (id == null) return null
            val candidates =
                (materializationMedia?.get(id.hi) ?: nodes.indices.toList()).filter { i ->
                    val n = nodes[i]
                    n?.baseIdentity == true && (!availableOnly || n.kind == NodeKind.MEDIA) && i !in unidentified && IdOrder.matches(n.id, n.flags, id)
                }
            return candidates.firstOrNull { nodes[it]!!.life.available && it in handles }
                ?: candidates.firstOrNull { nodes[it]!!.life.available }
                ?: if (availableOnly) null else candidates.firstOrNull()
        }

        // The anchor retains SHRT after expansion because persisted external short keys still name it.
        // https://wiki.271.pics/Cuetree/identity-access#short-id-alias
        fun resolveCollisions(mediaOnly: Boolean = false) {
            val buckets = nodes.indices.filter { nodes[it]?.mediaId != null && (!mediaOnly || nodes[it]?.baseIdentity == true) }.groupBy { nodes[it]!!.id.hi }
            for ((high, bucket) in buckets) {
                cancel.check()
                if (bucket.size == 1 && nodes[bucket[0]]!!.id.lo == null) {
                    nodes[bucket[0]] = nodes[bucket[0]]!!.copy(identityFlags = nodes[bucket[0]]!!.identityFlags and Flags.RESL.inv() or Flags.SHRT)
                    continue
                }
                val anchor =
                    bucket.firstOrNull { it < previous.nodes.size && nodes[it]!!.identityFlags and Flags.SHRT != 0 }
                        ?: bucket.firstOrNull { nodes[it]!!.id.lo == null && nodes[it]!!.identityFlags and Flags.SHRT != 0 }
                        ?: bucket.firstOrNull { nodes[it]!!.identityFlags and Flags.SHRT != 0 }
                for (index in bucket) {
                    val n = nodes[index]!!
                    if (n.id.lo != null || !n.playable) continue
                    try {
                        val base = if (n.kind == NodeKind.FRAGMENT) matchingBase(n.parentId, true) ?: throw IOException("No accessible base") else index
                        val full = n.id.copy(lo = hash(base, n.span, true).hi)
                        nodes[index] = n.copy(id = full)
                        // A short reference predates expansion and belongs to its retained short anchor.
                        if (n.kind == NodeKind.MEDIA && index == anchor) {
                            nodes.indices.forEach { f ->
                                val fragment = nodes[f]
                                if (fragment?.kind == NodeKind.FRAGMENT && fragment.parentId == n.id) nodes[f] = fragment.copy(parentId = full)
                            }
                        }
                    } catch (e: IOException) {
                        problem("IDENTITY_ACCESS", n.name, e)
                        if (nodes[index]?.kind != NodeKind.FAILEDL1) {
                            if (index >= previous.nodes.size && index != anchor) nodes[index] = null else missing(index)
                        }
                    }
                }
                val retained = bucket.filter { nodes[it]?.mediaId != null && nodes[it]!!.id.hi == high }
                val pending = retained.any { nodes[it]!!.id.lo == null }
                for (index in retained) {
                    val n = nodes[index]!!
                    val flags = n.identityFlags and (Flags.SHRT or Flags.RESL).inv() or (if (pending) Flags.RESL else 0) or (if (index == anchor || n.id.lo == null) Flags.SHRT else 0)
                    nodes[index] = n.copy(identityFlags = flags)
                }
            }
        }

        fun finish(): Snapshot {
            val indices = nodes.indices.filter { nodes[it] != null }
            val mapping = indices.withIndex().associate { it.value to it.index }
            val compact = indices.map { nodes[it]!! }

            fun occurrenceKey(node: Node): List<Any?> = listOf(node.kind, node.id, node.parentId, node.source?.name, node.source?.provider, node.source?.locator, node.span, node.attributes)
            val currentByKey = indices.groupBy { occurrenceKey(nodes[it]!!) }
            val sourceIndex =
                catalogPrevious.nodes.indices.associateWith { i ->
                    if (catalogPrevious === previous) i else currentByKey[occurrenceKey(catalogPrevious.nodes[i])]?.firstOrNull()
                }
            val old = catalogPrevious.copy(albums = catalogPrevious.albums.map { a -> a.copy(sources = a.sources.mapNotNull { i -> sourceIndex[i]?.let(mapping::get) }) })
            val candidates = explicit.filter { a -> a.sources.all { it in mapping } }.map { it.copy(sources = it.sources.map(mapping::getValue)) }
            val chapterExpansions = expansions.filter { e -> e.base in mapping && e.sources.all { it in mapping } }.map { e -> e.copy(base = mapping.getValue(e.base), sources = e.sources.map(mapping::getValue)) }
            val factual = Snapshot(generation, identity.scheme, compact, diagnostics = diagnostics.toList(), metadataRevision = options.metadataRevision ?: previous.metadataRevision)
            return CatalogBuilder(ids, genres, grouping, ttl, cancel, vocabulary)
                .build(factual, old, candidates, chapterExpansions)
                .withAttributes(catalogPrevious, ttl)
                .also(::validate)
                .ordered()
                .also(::validate)
        }
    }

    private data class FileWork(
        val index: Int,
        val directory: Int,
        val name: String,
        val provider: SourceProvider,
        val handle: SourceHandle,
        val stamp: Stamp,
        val hint: String,
        val old: Node,
        val newAdmission: Boolean,
    )

    private data class PreparedFile(
        val file: FileWork,
        val refreshed: Boolean,
        val observation: Observation?,
        val id: MediaId,
        val error: Exception? = null,
        val identityRefreshed: Boolean = refreshed,
        val failedSkip: Boolean = false,
    )

    private data class Sheet(
        val directory: Int,
        val name: String,
        val observation: Observation,
    )
}

internal fun component(name: String) = name.isNotEmpty() && name !in setOf(".", "..") && '/' !in name && '\u0000' !in name
