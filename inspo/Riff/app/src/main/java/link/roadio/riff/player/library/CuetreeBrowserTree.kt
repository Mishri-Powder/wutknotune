package link.roadio.riff.player.library

import link.roadio.cuetree.Album
import link.roadio.cuetree.Flags
import link.roadio.cuetree.Node
import link.roadio.cuetree.NodeKind
import link.roadio.cuetree.ROOT_IDENTITY_ATTRIBUTE
import link.roadio.cuetree.Snapshot
import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot
import link.roadio.riff.indexer.core.analysis.AnalysisState
import link.roadio.riff.indexer.core.analysis.resolve
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.selection.AiFeaturePoint
import link.roadio.riff.player.storage.StoredAudiobookOverride
import link.roadio.riff.player.storage.StoredContentMode
import link.roadio.riff.storage.hash.nativeXxh3
import link.roadio.riff.ui.BrowserItem
import link.roadio.riff.ui.BrowserKind
import link.roadio.riff.ui.PlaybackSourceRef
import link.roadio.riff.ui.PlaybackSourceSegment
import link.roadio.riff.ui.trackReactionKey

/**
 * Indexer owns catalog membership; Player turns the supplied facts into rows and queue occurrences.
 * Folders alone apply cached source availability: https://wiki.271.pics/Riff/LATEST/cuetree-integration
 */
internal object CuetreeBrowserTree {
    /** Static loading shell; catalog rows appear only after Indexer publishes a complete cohort. */
    fun emptyRoot(mode: StoredContentMode = StoredContentMode.MUSIC): BrowserItem =
        BrowserItem(
            id = "root",
            label = "Library",
            kind = BrowserKind.ROOT,
            children =
                listOf(
                    BrowserItem(
                        "category:albums",
                        if (mode ==
                            StoredContentMode.AUDIOBOOK
                        ) {
                            "Titles"
                        } else {
                            "Albums"
                        },
                        BrowserKind.CATEGORY,
                        "album",
                        enabled = false,
                    ),
                    BrowserItem(
                        "category:artists",
                        if (mode ==
                            StoredContentMode.AUDIOBOOK
                        ) {
                            "Authors"
                        } else {
                            "Artists"
                        },
                        BrowserKind.CATEGORY,
                        "artist",
                        enabled = false,
                    ),
                    BrowserItem("category:genres", "Genres", BrowserKind.CATEGORY, "genre"),
                    BrowserItem("category:playlists", "Playlists", BrowserKind.CATEGORY, "playlist"),
                    BrowserItem("category:folders", "Folders", BrowserKind.CATEGORY, "folder"),
                    BrowserItem("category:books", "Books", BrowserKind.CATEGORY, "audiobook"),
                    BrowserItem("category:ai", "AI", BrowserKind.AI, "ai"),
                ),
        )

    fun build(
        snapshot: Snapshot,
        projection: PlayerLibraryProjection,
        excludedGenreIds: Set<Long>,
        analysis: AnalysisSnapshot? = null,
        aiGenreDisplay: Map<Long, Pair<Long, String>> = emptyMap(),
        hash: (ByteArray) -> Long = ::nativeXxh3,
    ): PlayerLibraryModels {
        val nodes = snapshot.nodes
        val aliases =
            nodes
                .asSequence()
                .filter { it.mediaId != null && it.id.lo != null && it.identityFlags and Flags.SHRT != 0 }
                .associate { MediaId(it.id.hi, 0L, false) to MediaId(it.id.hi, requireNotNull(it.id.lo), true) }
        val shortByFull = aliases.entries.associate { (short, full) -> full to short }

        fun id(node: Node) = MediaId(node.id.hi, node.id.lo ?: 0L, node.id.lo != null)

        fun key(node: Node): String = id(node).hex()

        fun occurrenceKey(index: Int): String {
            val node = nodes[index]
            return "${key(node)}:${node.parentId}:${node.source?.name}:${node.span}:${node.attributes.toSortedMap()}:$index"
        }

        fun binding(
            index: Int,
            base: Int,
        ): PlaybackSourceRef? {
            val source = nodes[base]
            if (!nodes[index].playable || !source.playable) return null
            val path = mutableListOf<String>()
            var cursor = base
            while (true) {
                val current = nodes[cursor]
                if (current.isRoot) break
                path += current.name
                cursor = snapshot.parentOf(cursor) ?: return null
            }
            val root = nodes[cursor]
            val rootIdentity = root.attributes[ROOT_IDENTITY_ATTRIBUTE]?.singleOrNull() ?: return null
            val components = path.asReversed()
            val identity = "$rootIdentity\u0000${components.joinToString(
                "\u0000",
            )}\u0000${key(nodes[index])}\u0000${nodes[index].span}\u0000${nodes[index].attributes.toSortedMap()}"
            return PlaybackSourceRef(
                bindingId = hash(identity.toByteArray(Charsets.UTF_8)),
                rootBindingId = hash(rootIdentity.toByteArray(Charsets.UTF_8)),
                segments = components.map { PlaybackSourceSegment(it, "") },
                beginUs = nodes[index].span?.beginUs,
                endUs = nodes[index].span?.endUs,
            )
        }
        val sourcesByIndex =
            nodes.indices.associateWith { index ->
                val node = nodes[index]
                val bases = if (node.kind == NodeKind.FRAGMENT) snapshot.baseSources(node) else listOf(index)
                bases.mapNotNull { binding(index, it) }
            }
        val fragmentsByBase =
            nodes.indices
                .filter { nodes[it].kind == NodeKind.FRAGMENT }
                .flatMap { fragment -> snapshot.baseSources(nodes[fragment]).map { it to fragment } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, fragments) ->
                    fragments.distinctBy { index -> Triple(nodes[index].id, nodes[index].span, nodes[index].attributes) }
                }
        val exactSourcesById =
            nodes.indices
                .filter { nodes[it].mediaId != null }
                .groupBy { id(nodes[it]) }
                .mapValues { (_, members) -> members.flatMap { sourcesByIndex.getValue(it) }.distinctBy(PlaybackSourceRef::bindingId) }
        val sourcesById =
            exactSourcesById +
                aliases.mapNotNull { (short, full) ->
                    exactSourcesById[full]?.let { short to it }
                }
        val aiFeatures = linkedMapOf<MediaId, AiFeaturePoint>()
        val pteFeatures = linkedMapOf<MediaId, PlayerPteFeature>()
        val gainValues = linkedMapOf<MediaId, PlayerReplayGainValue>()
        val topGenres = linkedMapOf<MediaId, PlayerTopInferredGenre>()
        val genreNames =
            snapshot.genres
                .mapNotNull { genre ->
                    genre.ref.dictionaryId?.let { it to genre.name }
                }.toMap()
        analysis?.resolve(snapshot)?.forEach { (resolvedId, record) ->
            val mediaId = MediaId(resolvedId.hi, resolvedId.lo ?: 0L, resolvedId.lo != null)
            if (record.aiState == AnalysisState.AVAILABLE) {
                if (record.arousal != null && record.valence != null) {
                    aiFeatures[mediaId] =
                        AiFeaturePoint(
                            mediaId,
                            requireNotNull(record.arousal),
                            requireNotNull(record.valence),
                            requireNotNull(record.arousalConfidence),
                            requireNotNull(record.valenceConfidence),
                        )
                }
                if (record.pte != null) {
                    pteFeatures[mediaId] =
                        PlayerPteFeature(
                            requireNotNull(record.pte),
                            requireNotNull(record.pteConfidence),
                        )
                }
                record.genres
                    .sortedWith(
                        compareByDescending<link.roadio.riff.indexer.core.analysis.AnalysisGenre> {
                            it.confidence
                        }.thenBy { it.genreId },
                    ).firstNotNullOfOrNull { genre ->
                        val visible = aiGenreDisplay[genre.genreId]
                        val visibleId = visible?.first ?: genre.genreId
                        if (genre.genreId in excludedGenreIds || visibleId in excludedGenreIds) {
                            null
                        } else {
                            (visible?.second ?: genreNames[visibleId])?.let { name ->
                                PlayerTopInferredGenre(genre.genreId, name, genre.confidence)
                            }
                        }
                    }?.let { topGenres[mediaId] = it }
            }
            if (record.trackGainQ8 != null || record.albumGainQ8 != null) {
                gainValues[mediaId] =
                    PlayerReplayGainValue(
                        record.trackGainQ8,
                        record.trackPeak,
                        record.albumGainQ8,
                        record.albumPeak,
                    )
            }
        }
        aliases.forEach { (short, full) ->
            aiFeatures[full]?.let { aiFeatures[short] = it }
            pteFeatures[full]?.let { pteFeatures[short] = it }
            gainValues[full]?.let { gainValues[short] = it }
            topGenres[full]?.let { topGenres[short] = it }
        }
        val albumByIndex = snapshot.albums.flatMap { album -> album.sources.map { it to album } }.toMap()

        fun folderIds(index: Int): List<String> {
            val reversed = mutableListOf<String>()
            var parent = snapshot.parentOf(index)
            while (parent != null) {
                val node = nodes[parent]
                if (node.kind == NodeKind.DIRECTORY) reversed += "folder:${node.id.hi.toULong().toString(16)}"
                parent = snapshot.parentOf(parent)
            }
            return reversed.asReversed()
        }
        val automaticBooks =
            nodes
                .filter { it.mediaId != null && it.attributes["automaticBook"]?.singleOrNull() == "1" }
                .mapTo(mutableSetOf(), ::id)
        snapshot.albums
            .filter { it.attributes["automaticBook"]?.singleOrNull() == "1" }
            .forEach { album -> album.sources.forEach { automaticBooks += id(nodes[it]) } }
        val bookIds = automaticBooks.toMutableSet()
        for (index in nodes.indices) {
            val node = nodes[index]
            if (node.mediaId == null) continue
            val mediaId = id(node)
            val album = albumByIndex[index]
            val choice =
                projection.audiobookOverrides[trackReactionKey(shortByFull[mediaId] ?: mediaId)]
                    ?: projection.audiobookOverrides[trackReactionKey(mediaId)]
                    ?: album?.let { projection.audiobookOverrides["album:${it.id.toULong().toString(16)}"] }
                    ?: folderIds(index).asReversed().firstNotNullOfOrNull(projection.audiobookOverrides::get)
            when (choice) {
                StoredAudiobookOverride.BOOK -> bookIds += mediaId
                StoredAudiobookOverride.MUSIC -> bookIds -= mediaId
                null -> Unit
            }
        }

        fun track(
            index: Int,
            folder: Boolean = false,
            albumContext: Album? = null,
            baseOverride: Int? = null,
        ): BrowserItem {
            val node = nodes[index]
            val mediaId = id(node)
            val source = baseOverride?.let { binding(index, it) } ?: sourcesByIndex.getValue(index).firstOrNull()
            val album = albumContext ?: albumByIndex[index]
            val fragment = node.kind == NodeKind.FRAGMENT
            val title = node.attributes["title"]?.firstOrNull() ?: if (fragment) node.name else node.source?.name.orEmpty()
            return BrowserItem(
                id = if (folder) "folder-track:${occurrenceKey(index)}" else "track:${occurrenceKey(index)}",
                label = title,
                kind = BrowserKind.TRACK,
                playable = source != null,
                enabled = source != null,
                mediaId = mediaId,
                artist = node.attributes["artist"].orEmpty().joinToString(", "),
                album = album?.playerDisplayTitle() ?: node.attributes["album"]?.firstOrNull().orEmpty(),
                sourceFileName =
                    source
                        ?.segments
                        ?.lastOrNull()
                        ?.displayName
                        .orEmpty(),
                fragment = fragment,
                discNumber = node.attributes["discNumber"]?.firstOrNull()?.toLongOrNull(),
                trackNumber = node.attributes["trackNumber"]?.firstOrNull()?.toLongOrNull(),
                reactionKey = trackReactionKey(shortByFull[mediaId] ?: mediaId),
                sourceBindingId = source?.bindingId,
                playbackSource = source,
                audiobook = mediaId in bookIds,
                automaticAudiobook = mediaId in automaticBooks,
                aiReactionEligible = mediaId !in bookIds,
                albumGroupId = album?.id,
            )
        }
        val retainedTracks =
            nodes.indices
                .filter { nodes[it].mediaId != null }
                .map(::track)
        val tracks =
            nodes.indices
                .filter { nodes[it].mediaId != null && sourcesByIndex.getValue(it).isNotEmpty() }
                .map(::track)
                .distinctBy { it.mediaId }

        fun folder(
            index: Int,
            inherited: StoredAudiobookOverride? = null,
        ): BrowserItem {
            val node = nodes[index]
            val own = projection.audiobookOverrides["folder:${node.id.hi.toULong().toString(16)}"]
            val effective = own ?: inherited
            val children =
                snapshot.childrenOf(index).filter { nodes[it].life.available }.mapNotNull { child ->
                    when (nodes[child].kind) {
                        NodeKind.DIRECTORY -> {
                            folder(child, effective)
                        }

                        NodeKind.FRAGMENT -> {
                            track(child, true)
                        }

                        NodeKind.METADATA -> {
                            null
                        }

                        NodeKind.FAILEDL1 -> {
                            null
                        }

                        NodeKind.MEDIA -> {
                            val fragments = fragmentsByBase[child].orEmpty().filter { nodes[it].life.available }
                            if (fragments.isEmpty()) {
                                track(child, true, baseOverride = child)
                            } else {
                                BrowserItem(
                                    id = "media-container:${occurrenceKey(child)}",
                                    label = nodes[child].name,
                                    kind = BrowserKind.FOLDER,
                                    playable = true,
                                    children =
                                        listOf(track(child, true, baseOverride = child).copy(label = "Full file")) +
                                            fragments.map { track(it, true, baseOverride = child) },
                                )
                            }
                        }
                    }
                }
            return BrowserItem(
                id = "folder:${node.id.hi.toULong().toString(16)}",
                label = node.name,
                kind = BrowserKind.FOLDER,
                playable = children.any(BrowserItem::playable),
                audiobook = effective == StoredAudiobookOverride.BOOK,
                children = children.sortedWith(compareBy<BrowserItem> { it.kind == BrowserKind.TRACK }.thenBy { it.label.lowercase() }),
            )
        }
        val folders = snapshot.childrenOf(-1).filter { nodes[it].life.available }.map(::folder)

        fun albumRow(album: Album): BrowserItem =
            BrowserItem(
                id = "album:${album.id.toULong().toString(16)}",
                label = album.playerDisplayTitle(),
                comparisonNameOverride = album.title,
                kind = BrowserKind.ALBUM,
                icon = "album",
                playable = album.sources.any { sourcesByIndex.getValue(it).isNotEmpty() },
                audiobook = album.sources.any { id(nodes[it]) in bookIds },
                aiReactionEligible = album.sources.none { id(nodes[it]) in bookIds },
                albumGroupId = album.id,
                children = album.sources.map { track(it, albumContext = album) },
            )

        fun project(mode: StoredContentMode): PlayerLibraryModel {
            val book = mode == StoredContentMode.AUDIOBOOK
            val albums =
                snapshot.albums.filter { album ->
                    album.sources.any { (id(nodes[it]) in bookIds) == book }
                }
            val albumRows = albums.associate { it.id to albumRow(it) }
            val artists =
                snapshot.artists.mapNotNull { artist ->
                    val children = artist.albums.mapNotNull(albumRows::get)
                    if (children.isEmpty()) {
                        null
                    } else {
                        BrowserItem(
                            id = "artist:${artist.id.toULong().toString(16)}",
                            label = artist.name,
                            kind = BrowserKind.ARTIST,
                            icon = "artist",
                            playable = true,
                            children = children,
                        )
                    }
                }
            val genres =
                snapshot.genres.filter { it.ref.dictionaryId !in excludedGenreIds }.mapNotNull { genre ->
                    val children = genre.albums.mapNotNull(albumRows::get)
                    if (children.isEmpty()) {
                        null
                    } else {
                        BrowserItem(
                            id = "genre:${genre.id.toULong().toString(16)}",
                            label = genre.name,
                            kind = BrowserKind.GENRE,
                            icon = "genre",
                            genreId = genre.ref.dictionaryId,
                            playable = true,
                            children = children,
                        )
                    }
                }

            fun category(
                id: String,
                label: String,
                kind: BrowserKind,
                children: List<BrowserItem>,
                icon: String? = null,
            ) = BrowserItem("category:$id", label, kind, icon = icon, children = children, enabled = true)
            val root =
                BrowserItem(
                    id = "root",
                    label = "Library",
                    kind = BrowserKind.ROOT,
                    children =
                        listOf(
                            category("albums", if (book) "Titles" else "Albums", BrowserKind.CATEGORY, albumRows.values.toList(), "album"),
                            category("artists", if (book) "Authors" else "Artists", BrowserKind.CATEGORY, artists, "artist"),
                            category("genres", "Genres", BrowserKind.CATEGORY, genres, "genre"),
                            category("playlists", if (book) "Bookmarks" else "Playlists", BrowserKind.CATEGORY, emptyList(), "playlist"),
                            category("folders", "Folders", BrowserKind.CATEGORY, folders, "folder"),
                            category("books", "Books", BrowserKind.CATEGORY, emptyList(), "audiobook"),
                            category("ai", "AI", BrowserKind.AI, emptyList(), "ai"),
                        ),
                )
            return PlayerLibraryModel(
                root = root,
                tracks = tracks,
                retainedTracks = retainedTracks,
                playbackSourcesByMediaId = sourcesById,
                generation = snapshot.generation,
                mediaIds = sourcesById.keys,
                mediaAliases = aliases,
                aiFeaturesByMediaId = aiFeatures,
                pteByMediaId = pteFeatures,
                topInferredGenreByMediaId = topGenres,
                augmentationGeneration = analysis?.generation,
                replayGainByMediaId = gainValues,
                replayGainGeneration = analysis?.generation,
                audiobookMediaIds = automaticBooks,
                effectiveBookMediaIds = bookIds,
                projection = projection.copy(contentMode = mode),
                catalogsReady = true,
            )
        }
        return PlayerLibraryModels(project(StoredContentMode.MUSIC), project(StoredContentMode.AUDIOBOOK))
    }
}
