// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import com.google.flatbuffers.FlatBufferBuilder
import link.roadio.cuetree.fbs.*
import java.io.IOException
import java.nio.ByteBuffer
import link.roadio.cuetree.fbs.Album as FAlbum
import link.roadio.cuetree.fbs.Artist as FArtist
import link.roadio.cuetree.fbs.Genre as FGenre
import link.roadio.cuetree.fbs.MediaId as FMediaId
import link.roadio.cuetree.fbs.Node as FNode
import link.roadio.cuetree.fbs.SourceInfo as FSourceInfo
import link.roadio.cuetree.fbs.StructureSection as FStructureSection
import link.roadio.cuetree.fbs.StructureTable as FStructureTable

/** Generated accessors and binary ID lookup do not require a decoded tree for display. */
class FlatCatalogs private constructor(
    private val files: Map<String, ByteArray>,
) {
    fun mdia(): Mdia = Mdia.getRootAsMdia(buffer("MDIA"))

    fun albm(): Albm = Albm.getRootAsAlbm(buffer("ALBM"))

    fun arts(): Arts = Arts.getRootAsArts(buffer("ARTS"))

    fun gnrv(): Gnrv = Gnrv.getRootAsGnrv(buffer("GNRV"))

    fun buffer(name: String): ByteBuffer = ByteBuffer.wrap(files.getValue(name)).asReadOnlyBuffer()

    fun bytes(name: String): ByteArray = files.getValue(name).copyOf()

    fun sources(id: MediaId): List<Int> {
        val m = mdia()
        return IdOrder.range(m.nodesLength(), { readId(m.nodes(it)!!.id()!!) }, id.hi).filter {
            val n = m.nodes(it)!!
            val kind = n.flags() ushr Flags.KIND_SHIFT
            (
                kind == NodeKind.MEDIA.ordinal ||
                    kind == NodeKind.FRAGMENT.ordinal ||
                    (
                        kind == NodeKind.FAILEDL1.ordinal &&
                            n.flags() and Flags.IDHI == 0
                    )
            ) &&
                IdOrder.matches(readId(n.id()!!), n.flags(), id)
        }
    }

    fun album(id: MediaId): FAlbum? {
        val a = albm()
        return IdOrder
            .range(a.albumsLength(), { readId(a.albums(it)!!.id()!!) }, id.hi)
            .firstOrNull { readId(a.albums(it)!!.id()!!) == id }
            ?.let { a.albums(it) }
    }

    fun artist(id: MediaId): FArtist? {
        val a = arts()
        return IdOrder
            .range(a.artistsLength(), { readId(a.artists(it)!!.id()!!) }, id.hi)
            .firstOrNull { readId(a.artists(it)!!.id()!!) == id }
            ?.let { a.artists(it) }
    }

    fun genre(id: MediaId): FGenre? {
        val a = gnrv()
        return IdOrder
            .range(a.genresLength(), { readId(a.genres(it)!!.id()!!) }, id.hi)
            .firstOrNull { readId(a.genres(it)!!.id()!!) == id }
            ?.let { a.genres(it) }
    }

    fun attribute(id: MediaId): Attribute? {
        val a = mdia()
        return IdOrder
            .range(a.attributesLength(), { readId(a.attributes(it)!!.id()!!) }, id.hi)
            .firstOrNull { readId(a.attributes(it)!!.id()!!) == id }
            ?.let { a.attributes(it) }
    }

    private val children: Map<Int, List<Int>> by lazy {
        val m = mdia()
        val directories = (0 until m.nodesLength()).filter { m.nodes(it)!!.flags() ushr Flags.KIND_SHIFT == NodeKind.DIRECTORY.ordinal }.associateBy { readId(m.nodes(it)!!.id()!!) }
        (0 until m.nodesLength()).groupBy { i ->
            val n = m.nodes(i)!!
            val parent = n.parentId()?.let(::readId)
            if (parent == null) {
                -1
            } else if (n.flags() ushr Flags.KIND_SHIFT != NodeKind.FRAGMENT.ordinal) {
                directories.getValue(parent)
            } else {
                val candidates =
                    sources(parent).filter {
                        (
                            m.nodes(it)!!.flags() ushr Flags.KIND_SHIFT == NodeKind.MEDIA.ordinal ||
                                (
                                    m.nodes(it)!!.flags() ushr Flags.KIND_SHIFT == NodeKind.FAILEDL1.ordinal &&
                                        m.nodes(it)!!.flags() and Flags.IDHI == 0
                                )
                        )
                    }
                candidates.firstOrNull { m.nodes(it)!!.flags() and Flags.ACSS == 0 } ?: candidates.first()
            }
        }
    }

    fun childrenOf(index: Int): List<Int> = children[index].orEmpty()

    fun roots(): List<Int> = childrenOf(-1)

    fun snapshot(): Snapshot {
        try {
            val m = mdia()
            val a = albm()
            val r = arts()
            val g = gnrv()
            val headers = listOf(m.header(), a.header(), r.header(), g.header()).map { requireNotNull(it) }
            val h = headers.first()
            require(headers.all { it.version() == FORMAT_VERSION }) { "Unsupported Cuetree format" }
            require(headers.all { it.generation() == h.generation() && it.identityScheme() == h.identityScheme() && it.metadataRevision() == h.metadataRevision() })
            val dictionary =
                list(m.attributesLength()) { i ->
                    val x = requireNotNull(m.attributes(i))
                    val kind = AttributeKind.entries.single { it.code == x.kind() }
                    val value = AttributeValue(kind, x.valueText(), if (x.hasValueLong()) x.valueLong() else null)
                    require(value.id == readId(requireNotNull(x.id()))) { "Attribute ID mismatch" }
                    require(x.flags() and Flags.IDHI != 0)
                    AttributeRecord(value, readLife(x.generation(), x.flags(), Flags.IDHI or Flags.ACSS))
                }
            val values = dictionary.associate { it.id to it.value }
            require(values.size == dictionary.size)

            fun attrs(
                count: Int,
                ref: (Int) -> FMediaId?,
            ): Attributes {
                val result = linkedMapOf<String, MutableList<String>>()
                list(count) { i ->
                    val value = values.getValue(readId(requireNotNull(ref(i))))
                    val (key, text) = value.entry()
                    result.getOrPut(key) { mutableListOf() } += text
                }
                return result
            }
            val nodes =
                list(m.nodesLength()) { i ->
                    val n = requireNotNull(m.nodes(i))
                    val kind = NodeKind.entries[n.flags() ushr Flags.KIND_SHIFT]
                    val id = readId(requireNotNull(n.id()))
                    val highOnly = n.flags() and Flags.IDHI != 0
                    require(
                        highOnly == (
                            kind == NodeKind.DIRECTORY ||
                                kind == NodeKind.METADATA ||
                                (
                                    kind == NodeKind.FAILEDL1 &&
                                        id.lo == null &&
                                        n.flags() and Flags.SHRT == 0
                                )
                        ),
                    )
                    require(!highOnly || id.lo == null)
                    require(kind == NodeKind.FRAGMENT || (!n.hasBeginUs() && !n.hasEndUs()))
                    val source = n.source()?.let { x -> SourceInfo(requireNotNull(x.name()), if (x.hasModified()) x.modified() else null, if (x.hasSize()) x.size() else null, x.provider(), x.locator()) }
                    Node(
                        kind,
                        id,
                        n.parentId()?.let(::readId),
                        source,
                        if (kind == NodeKind.FRAGMENT) Span(if (n.hasBeginUs()) n.beginUs() else null, if (n.hasEndUs()) n.endUs() else null) else null,
                        attrs(n.attributesLength()) { n.attributes(it) },
                        Life(n.generation(), n.flags() and Flags.ACSS),
                        n.flags() and (Flags.ACSS or Flags.IDHI or (7 shl Flags.KIND_SHIFT)).inv(),
                        list(n.tablesLength()) { tableIndex ->
                            val table = requireNotNull(n.tables(tableIndex))
                            TableObservation(
                                requireNotNull(table.key()),
                                attrs(table.attributesLength()) { table.attributes(it) },
                                list(table.sectionsLength()) { sectionIndex ->
                                    val section = requireNotNull(table.sections(sectionIndex))
                                    Section(
                                        section.reference(),
                                        Span(
                                            if (section.hasBeginUs()) section.beginUs() else null,
                                            if (section.hasEndUs()) section.endUs() else null,
                                        ),
                                        attrs(section.attributesLength()) { section.attributes(it) },
                                    )
                                },
                            )
                        },
                        attrs(n.sourceEvidenceLength()) { n.sourceEvidence(it) },
                    )
                }

            fun high(
                id: FMediaId?,
                flags: Int,
            ): Long {
                val value = readId(requireNotNull(id))
                require(value.lo == null && flags and Flags.IDHI != 0)
                return value.hi
            }

            fun highRef(id: FMediaId?): Long {
                val value = readId(requireNotNull(id))
                require(value.lo == null)
                return value.hi
            }
            val albums =
                list(a.albumsLength()) { i ->
                    val x = requireNotNull(a.albums(i))
                    Album(
                        high(x.id(), x.flags()),
                        AlbumKind.entries[x.flags() ushr Flags.KIND_SHIFT],
                        requireNotNull(x.title()),
                        list(x.tracksLength()) { readId(requireNotNull(x.tracks(it))) },
                        list(x.sourcesLength()) { x.sources(it) },
                        attrs(x.attributesLength()) { x.attributes(it) },
                        readLife(x.generation(), x.flags(), Flags.IDHI or Flags.ACSS or (7 shl Flags.KIND_SHIFT)),
                    )
                }
            val artists =
                list(r.artistsLength()) { i ->
                    val x = requireNotNull(r.artists(i))
                    Artist(
                        high(x.id(), x.flags()),
                        requireNotNull(x.key()),
                        requireNotNull(x.name()),
                        list(x.albumsLength()) { highRef(x.albums(it)) },
                        readLife(x.generation(), x.flags(), Flags.IDHI or Flags.ACSS),
                    ).also {
                        require(attrs(x.attributesLength()) { j -> x.attributes(j) } == it.attributes)
                    }
                }
            val genres =
                list(g.genresLength()) { i ->
                    val x = requireNotNull(g.genres(i))
                    Genre(
                        GenreRef(if (x.hasDictionaryId()) x.dictionaryId() else null, x.text()),
                        requireNotNull(x.name()),
                        list(x.albumsLength()) { highRef(x.albums(it)) },
                        readLife(x.generation(), x.flags(), Flags.IDHI or Flags.ACSS),
                        high(x.id(), x.flags()),
                    ).also {
                        require(attrs(x.attributesLength()) { j -> x.attributes(j) } == it.attributes)
                    }
                }
            val snapshot = Snapshot(h.generation(), requireNotNull(h.identityScheme()), nodes, albums, artists, genres, attributeCatalog = dictionary, metadataRevision = h.metadataRevision())
            validate(snapshot)
            require(snapshot.nodes.zipWithNext().all { (x, y) -> IdOrder.compare(x.id, y.id) <= 0 })
            require(albums.zipWithNext().all { (x, y) -> x.id < y.id })
            require(artists.zipWithNext().all { (x, y) -> x.id < y.id })
            require(genres.zipWithNext().all { (x, y) -> x.id < y.id })
            require(dictionary.zipWithNext().all { (x, y) -> IdOrder.compare(x.id, y.id) < 0 })
            val expected =
                snapshot.nodes.indices
                    .filter { snapshot.nodes[it].mediaId != null }
                    .groupBy { snapshot.nodes[it].mediaId!!.hi }
                    .toSortedMap()
            val actual =
                list(m.mediaLength()) { i ->
                    val bucket = requireNotNull(m.media(i))
                    bucket.hi() to list(bucket.sourcesLength()) { bucket.sources(it) }
                }
            require(actual == expected.toList())
            return snapshot
        } catch (e: RuntimeException) {
            throw IOException("Invalid Cuetree FlatBuffers cohort", e)
        }
    }

    private fun <T> list(
        count: Int,
        read: (Int) -> T,
    ): List<T> {
        require(count in 0..1_000_000) { "Invalid vector length" }
        return List(count, read)
    }

    private fun readLife(
        generation: Long,
        flags: Int,
        allowed: Int,
    ): Life {
        require(flags and allowed.inv() == 0)
        return Life(generation, flags and Flags.ACSS)
    }

    private fun readId(id: FMediaId) = MediaId(id.hi(), if (id.hasLo()) id.lo() else null)

    companion object {
        const val FORMAT_VERSION: Long = 11
        val names = listOf("MDIA", "ALBM", "ARTS", "GNRV")

        fun read(files: Map<String, ByteArray>): FlatCatalogs {
            try {
                require(files.keys == names.toSet())
                names.forEach { name ->
                    val bytes = files.getValue(name)
                    require(bytes.size >= 12 && bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) == name)
                }
                return FlatCatalogs(files.mapValues { it.value.copyOf() }).also { it.snapshot() }
            } catch (e: IllegalArgumentException) {
                throw IOException("Invalid Cuetree files", e)
            }
        }

        fun encode(input: Snapshot): FlatCatalogs {
            validate(input)
            val needed = input.nodes.flatMap { node -> node.attributes.typed() + node.sourceEvidence.typed() + node.tables.flatMap { table -> table.attributes.typed() + table.sections.flatMap { it.attributes.typed() } } } + input.albums.flatMap { it.attributes.typed() } + input.artists.flatMap { it.attributes.typed() } + input.genres.flatMap { it.attributes.typed() }
            val ids = input.attributeCatalog.map { it.id }.toSet()
            val snapshot = (if (needed.any { it.id !in ids }) input.withAttributes(input, Long.MAX_VALUE) else input).ordered()
            val files = linkedMapOf<String, ByteArray>()
            for (name in names) {
                val b = FlatBufferBuilder(1024)

                fun str(s: String?) = s?.let(b::createString) ?: 0

                fun offsets(values: List<Int>): Int {
                    b.startVector(4, values.size, 4)
                    values.asReversed().forEach(b::addOffset)
                    return b.endVector()
                }

                fun ints(values: List<Int>) = FAlbum.createSourcesVector(b, values.toIntArray())

                fun optional(
                    value: Long?,
                    add: (Long) -> Unit,
                ) {
                    if (value != null) {
                        b.forceDefaults(true)
                        add(value)
                        b.forceDefaults(false)
                    }
                }

                fun id(value: MediaId): Int {
                    FMediaId.startMediaId(b)
                    optional(value.lo) { FMediaId.addLo(b, it) }
                    FMediaId.addHi(b, value.hi)
                    return FMediaId.endMediaId(b)
                }

                fun high(value: Long) = id(MediaId(value))

                fun attrs(values: Attributes) = offsets(values.typed().map { id(it.id) })

                fun highRefs(values: List<Long>) = offsets(values.map(::high))
                val h = Header.createHeader(b, FORMAT_VERSION, snapshot.generation, str(snapshot.identityScheme), str(snapshot.metadataRevision))
                val root =
                    when (name) {
                        "MDIA" -> {
                            val dictionary =
                                offsets(
                                    snapshot.attributeCatalog.map { x ->
                                        val key = id(x.id)
                                        val text = str(x.value.text)
                                        Attribute.startAttribute(b)
                                        Attribute.addId(b, key)
                                        Attribute.addKind(b, x.value.kind.code)
                                        Attribute.addValueText(b, text)
                                        optional(x.value.number) { Attribute.addValueLong(b, it) }
                                        Attribute.addGeneration(b, x.life.generation)
                                        Attribute.addFlags(b, x.flags)
                                        Attribute.endAttribute(b)
                                    },
                                )
                            val nodes =
                                offsets(
                                    snapshot.nodes.map { n ->
                                        val location =
                                            n.source?.let { source ->
                                                val name = str(source.name)
                                                val provider = str(source.provider)
                                                val locator = str(source.locator)
                                                FSourceInfo.startSourceInfo(b)
                                                FSourceInfo.addName(b, name)
                                                optional(source.modified) { FSourceInfo.addModified(b, it) }
                                                optional(source.size) { FSourceInfo.addSize(b, it) }
                                                FSourceInfo.addProvider(b, provider)
                                                FSourceInfo.addLocator(b, locator)
                                                FSourceInfo.endSourceInfo(b)
                                            } ?: 0
                                        val key = id(n.id)
                                        val parent = n.parentId?.let(::id) ?: 0
                                        val attributes = attrs(n.attributes)
                                        val sourceEvidence = attrs(n.sourceEvidence)
                                        val tables =
                                            offsets(
                                                n.tables.map { table ->
                                                    val key = str(table.key)
                                                    val tableAttrs = attrs(table.attributes)
                                                    val sections =
                                                        offsets(
                                                            table.sections.map { section ->
                                                                val reference = str(section.reference)
                                                                val sectionAttrs = attrs(section.attributes)
                                                                FStructureSection.startStructureSection(b)
                                                                FStructureSection.addReference(b, reference)
                                                                optional(section.span.beginUs) { FStructureSection.addBeginUs(b, it) }
                                                                optional(section.span.endUs) { FStructureSection.addEndUs(b, it) }
                                                                FStructureSection.addAttributes(b, sectionAttrs)
                                                                FStructureSection.endStructureSection(b)
                                                            },
                                                        )
                                                    FStructureTable.createStructureTable(b, key, tableAttrs, sections)
                                                },
                                            )
                                        FNode.startNode(b)
                                        FNode.addGeneration(b, n.life.generation)
                                        FNode.addId(b, key)
                                        FNode.addParentId(b, parent)
                                        FNode.addSource(b, location)
                                        optional(n.span?.beginUs) { FNode.addBeginUs(b, it) }
                                        optional(n.span?.endUs) { FNode.addEndUs(b, it) }
                                        FNode.addAttributes(b, attributes)
                                        FNode.addTables(b, tables)
                                        FNode.addSourceEvidence(b, sourceEvidence)
                                        FNode.addFlags(b, n.flags)
                                        FNode.endNode(b)
                                    },
                                )
                            val media =
                                offsets(
                                    snapshot.nodes.indices.filter { snapshot.nodes[it].mediaId != null }.groupBy { snapshot.nodes[it].mediaId!!.hi }.toSortedMap().map { (hi, sources) ->
                                        val members = ints(sources)
                                        b.forceDefaults(true)
                                        val bucket = MediaBucket.createMediaBucket(b, hi, members)
                                        b.forceDefaults(false)
                                        bucket
                                    },
                                )
                            Mdia.createMdia(b, h, nodes, media, dictionary)
                        }

                        "ALBM" -> {
                            Albm.createAlbm(
                                b,
                                h,
                                offsets(
                                    snapshot.albums.map { a ->
                                        FAlbum.createAlbum(b, high(a.id), a.flags, str(a.title), offsets(a.tracks.map(::id)), ints(a.sources), attrs(a.attributes), a.life.generation)
                                    },
                                ),
                            )
                        }

                        "ARTS" -> {
                            Arts.createArts(
                                b,
                                h,
                                offsets(
                                    snapshot.artists.map { a ->
                                        FArtist.createArtist(b, attrs(a.attributes), high(a.id), str(a.key), str(a.name), highRefs(a.albums), a.life.generation, a.flags)
                                    },
                                ),
                            )
                        }

                        else -> {
                            Gnrv.createGnrv(
                                b,
                                h,
                                offsets(
                                    snapshot.genres.map { g ->
                                        val key = high(g.id)
                                        val attributes = attrs(g.attributes)
                                        val text = str(g.ref.text)
                                        val label = str(g.name)
                                        val albums = highRefs(g.albums)
                                        FGenre.startGenre(b)
                                        FGenre.addId(b, key)
                                        FGenre.addAttributes(b, attributes)
                                        optional(g.ref.dictionaryId) { FGenre.addDictionaryId(b, it) }
                                        FGenre.addText(b, text)
                                        FGenre.addName(b, label)
                                        FGenre.addAlbums(b, albums)
                                        FGenre.addGeneration(b, g.life.generation)
                                        FGenre.addFlags(b, g.flags)
                                        FGenre.endGenre(b)
                                    },
                                ),
                            )
                        }
                    }
                b.finish(root, name)
                files[name] = b.sizedByteArray()
            }
            return FlatCatalogs(files)
        }
    }
}
