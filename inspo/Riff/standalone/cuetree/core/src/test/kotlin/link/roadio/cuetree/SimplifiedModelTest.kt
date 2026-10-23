// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.MediaInput
import org.junit.Assert.*
import org.junit.Test

class SimplifiedModelTest {
    private fun cue(
        firstTitle: String = "One",
        file: String = "image.flac",
    ) = """
        PERFORMER "Artist"
        TITLE "Album"
        FILE "$file" WAVE
          TRACK 01 AUDIO
            TITLE "$firstTitle"
            INDEX 01 00:00:00
          TRACK 02 AUDIO
            TITLE "Two"
            INDEX 01 00:01:00
        """.trimIndent().toByteArray()

    private fun explicit(snapshot: Snapshot) = snapshot.albums.filter { it.kind == AlbumKind.EXPLICIT && it.sources.isNotEmpty() }

    private fun assertNoExtraTracks(snapshot: Snapshot) {
        assertTrue(snapshot.albums.filter { it.kind != AlbumKind.EXPLICIT }.all { it.tracks.isEmpty() })
    }

    @Test fun embeddedChaptersUseContainingMediaAlbumWhenTableHasNoTitle() {
        val p = MemoryProvider()
        p.file("book.m4b", byteArrayOf(1, 2, 3))
        val metadata =
            MetadataReader { _, _, _ ->
                Observation(
                    mapOf("album" to listOf("Book Album"), "title" to listOf("Book File")),
                    listOf(
                        TableObservation(
                            "chapters",
                            emptyMap(),
                            listOf(
                                Section(null, Span(0, 10), mapOf("title" to listOf("Chapter One"))),
                                Section(null, Span(10, 20), mapOf("title" to listOf("Chapter Two"))),
                            ),
                        ),
                    ),
                    emptyList(),
                )
            }
        val result = Indexer(MockMediaID(), 10, listOf(p), metadata).scan(listOf(p.rootSpec()), generation = 1)
        val album = result.albums.single { it.kind == AlbumKind.ORDINARY }
        assertEquals("Book Album", album.title)
        assertEquals(listOf("Book Album"), album.attributes["album"])
        assertEquals(listOf("Chapter One", "Chapter Two"), album.sources.map { result.nodes[it].attributes.first("title") })
        assertEquals(result, FlatCatalogs.encode(result).snapshot())
        val stale = result.copy(albums = listOf(album.copy(kind = AlbumKind.EXPLICIT, title = "[[UNTITLED]]", attributes = emptyMap())))
        val refreshed = Indexer(MockMediaID(), 10, listOf(p), metadata).scan(listOf(p.rootSpec()), stale, generation = 2)
        assertEquals(listOf("Book Album"), refreshed.albums.filter { it.sources.isNotEmpty() && it.kind != AlbumKind.SINGLES }.map { it.title })
    }

    @Test fun ordinaryAlbumPreservesRepeatedChapterAudioAcrossPublicationAndRoundTrip() {
        val provider = MemoryProvider()
        provider.file("book.m4b", byteArrayOf(1, 2, 3))
        val identity =
            object : MediaIdGenerator {
                override val scheme = "repeated-chapter-test"

                override fun high(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = if (span == null) 1L else 2L

                override fun low(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = 3L
            }
        val reader =
            MetadataReader { _, _, _ ->
                Observation(
                    mapOf("album" to listOf("Book")),
                    listOf(
                        TableObservation(
                            "chapters",
                            emptyMap(),
                            listOf(
                                Section(null, Span(0, 10), mapOf("title" to listOf("A"))),
                                Section(null, Span(10, 20), mapOf("title" to listOf("B"))),
                            ),
                        ),
                    ),
                    emptyList(),
                )
            }
        val indexer = Indexer(identity, 10, listOf(provider), reader)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        val album = first.albums.single { it.kind == AlbumKind.ORDINARY }
        assertEquals(2, album.tracks.size)
        assertEquals(album.tracks.first(), album.tracks.last())
        val decoded = FlatCatalogs.encode(first).snapshot()
        assertEquals(first, decoded)
        val advanced = decoded.advancePublication(2, 10)
        assertEquals(album.tracks, advanced.albums.single { it.kind == AlbumKind.ORDINARY }.tracks)
        val next = indexer.scan(listOf(provider.rootSpec()), advanced, 3)
        assertEquals(
            listOf("A", "B"),
            next.albums
                .single { it.kind == AlbumKind.ORDINARY }
                .sources
                .map { next.nodes[it].name },
        )
    }

    @Test fun metadataRevisionRequiresFullIndexRecreation() {
        val provider = MemoryProvider()
        provider.file("book.m4b", byteArrayOf(1, 2, 3))
        var metadataReads = 0
        val reader =
            MetadataReader { _, _, _ ->
                metadataReads++
                Observation(mapOf("title" to listOf("Book")), emptyList(), emptyList())
            }
        val indexer = Indexer(MockMediaID(), 10, listOf(provider), reader)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1, options = ScanOptions(metadataRevision = "r1"))
        assertEquals(1, metadataReads)
        assertThrows(IllegalArgumentException::class.java) {
            indexer.scan(listOf(provider.rootSpec()), first, 2, ScanOptions(metadataRevision = "r2"))
        }
        assertEquals(1, metadataReads)
        val rebuilt = indexer.scan(listOf(provider.rootSpec()), generation = 2, options = ScanOptions(metadataRevision = "r2"))
        assertEquals(2, metadataReads)
        assertEquals("r2", rebuilt.metadataRevision)
        assertEquals(rebuilt, FlatCatalogs.encode(rebuilt).snapshot())
    }

    @Test fun multipartBookGroupsContainersBeforeExpandingChapters() {
        val p = MemoryProvider()
        p.file("01.m4b", byteArrayOf(1, 2, 3))
        p.file("01-copy.m4b", byteArrayOf(1, 2, 3))
        val second = p.file("02.m4b", byteArrayOf(4, 5, 6))
        val metadata =
            MetadataReader { input, _, _ ->
                val part = input.id.take(2).toInt()
                Observation(
                    mapOf(
                        "album" to listOf("One Book"),
                        "title" to listOf("Part $part"),
                        "artist" to listOf("Author"),
                        "trackNumber" to listOf(part.toString()),
                    ),
                    listOf(
                        TableObservation(
                            "chapters",
                            emptyMap(),
                            listOf(
                                Section(null, Span(0, 10), mapOf("title" to listOf("$part-A"))),
                                Section(null, Span(10, 20), mapOf("title" to listOf("$part-B"))),
                            ),
                        ),
                    ),
                    emptyList(),
                )
            }
        val indexer = Indexer(MockMediaID(), 10, listOf(p), metadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        val album = first.albums.single { it.kind == AlbumKind.ORDINARY }
        assertEquals("One Book", album.title)
        assertEquals(listOf("1-A", "1-B", "2-A", "2-B"), album.sources.map { first.nodes[it].attributes.first("title") })
        assertEquals(4, album.tracks.size)
        assertTrue(
            first.albums
                .single { it.kind == AlbumKind.SINGLES }
                .sources
                .isEmpty(),
        )
        assertTrue(explicit(first).isEmpty())
        val next = indexer.scan(listOf(p.rootSpec()), FlatCatalogs.encode(first).snapshot(), 2)
        assertEquals(album.id, next.albums.single { it.kind == AlbumKind.ORDINARY }.id)
        assertEquals(
            listOf("1-A", "1-B", "2-A", "2-B"),
            next.albums
                .single { it.kind == AlbumKind.ORDINARY }
                .sources
                .map { next.nodes[it].attributes.first("title") },
        )
        p.denied += second
        val offline = indexer.scan(listOf(p.rootSpec()), next, 3)
        assertEquals(
            listOf("1-A", "1-B", "2-A", "2-B"),
            offline.albums
                .single { it.kind == AlbumKind.ORDINARY }
                .sources
                .map { offline.nodes[it].attributes.first("title") },
        )
    }

    @Test fun exactFlacAndCueCopiesKeepOccurrencesButConvergeIntoOneAlbum() {
        val p = MemoryProvider()
        val a = p.directory("A")
        val b = p.directory("B")
        for (directory in listOf(a, b)) {
            p.file("image.flac", byteArrayOf(1, 2, 3), directory)
            p.file("disc.cue", cue(), directory)
        }
        val indexer = Indexer(MockMediaID(), 10, listOf(p))
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        assertEquals(2, first.nodes.count { it.kind == NodeKind.MEDIA })
        assertEquals(4, first.nodes.count { it.kind == NodeKind.FRAGMENT })
        val bases = first.nodes.filter { it.kind == NodeKind.MEDIA }
        assertEquals(1, bases.map { it.id }.distinct().size)
        assertNotNull(bases.first().id.lo)
        assertEquals(2, bases.map { it.parentId }.distinct().size)
        val album = explicit(first).single()
        assertEquals(listOf("One", "Two"), album.sources.map { first.nodes[it].name })
        assertEquals(2, album.tracks.size)
        assertNoExtraTracks(first)
        val next = indexer.scan(listOf(p.rootSpec()), FlatCatalogs.encode(first).snapshot(), 2)
        assertEquals(album.id, explicit(next).single().id)
        assertEquals(first.nodes.size, next.nodes.size)
        assertNoExtraTracks(next)
        p.denied += a
        val missing = indexer.scan(listOf(p.rootSpec()), next, 3)
        val expired = indexer.scan(listOf(p.rootSpec()), missing, 12)
        assertEquals(album.id, explicit(expired).single().id)
        assertEquals(1, expired.nodes.count { it.kind == NodeKind.MEDIA })
        assertEquals(2, expired.nodes.count { it.kind == NodeKind.FRAGMENT })
        assertTrue(explicit(expired).single().life.available)
        assertNoExtraTracks(expired)
        val flat = FlatCatalogs.encode(expired)
        for (i in expired.nodes.indices) assertEquals(expired.childrenOf(i), flat.childrenOf(i))
    }

    @Test fun twoIdenticalCueSheetsBesideOneFileDoNotCreateTwoAlbums() {
        val p = MemoryProvider()
        p.file("image.flac", byteArrayOf(1, 2, 3))
        p.file("disc.cue", cue())
        p.file("copy.cue", cue())
        val result = Indexer(MockMediaID(), 10, listOf(p)).scan(listOf(p.rootSpec()), generation = 1)
        assertEquals(4, result.nodes.count { it.kind == NodeKind.FRAGMENT })
        assertEquals(1, explicit(result).size)
        assertNoExtraTracks(result)
    }

    @Test fun sameAlbumTitleAndAudioWithDifferentPositionMetadataRemainTwoAlbums() {
        val p = MemoryProvider()
        p.file("image.flac", byteArrayOf(1, 2, 3))
        p.file("a.cue", cue())
        p.file("b.cue", cue("Different chapter"))
        val indexer = Indexer(MockMediaID(), 10, listOf(p))
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        val albums = explicit(first)
        assertEquals(2, albums.size)
        assertEquals(1, albums.map { it.tracks }.distinct().size)
        assertEquals(1, albums.map { it.attributes }.distinct().size)
        assertEquals(setOf("One", "Different chapter"), albums.map { first.nodes[it.sources.first()].name }.toSet())
        val next = indexer.scan(listOf(p.rootSpec()), FlatCatalogs.encode(first).snapshot(), 2)
        assertEquals(albums.map { it.id }.toSet(), explicit(next).map { it.id }.toSet())
        assertNoExtraTracks(next)
    }

    @Test fun identicalMediaIdsAtDifferentAlbumPositionsKeepTheirOrderAndMetadata() {
        val p = MemoryProvider()
        p.file("image.flac", byteArrayOf(1, 2, 3))
        p.file("disc.cue", cue())
        val identity =
            object : MediaIdGenerator {
                override val scheme = "test.repeated-position"

                override fun high(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = if (span == null) 1L else 2L

                override fun low(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = 0L
            }
        val result = Indexer(identity, 10, listOf(p)).scan(listOf(p.rootSpec()), generation = 1)
        val album = explicit(result).single()
        assertEquals(listOf(MediaId(2, 0), MediaId(2, 0)), album.tracks)
        assertEquals(listOf("One", "Two"), album.sources.map { result.nodes[it].name })
        assertEquals(result.copy(diagnostics = emptyList()), FlatCatalogs.encode(result).snapshot())
    }

    @Test fun nullableBoundsAndPhysicalSourceFieldsRoundTripWithoutSentinels() {
        val life = Life(0)
        val root = Node(NodeKind.DIRECTORY, MediaId(0), source = SourceInfo("Root", provider = "posix", locator = "/music"), life = life)
        val media = Node(NodeKind.MEDIA, MediaId(1), root.id, SourceInfo("image.flac", 0, 0), life = life)
        val spans = listOf(Span(), Span(0), Span(null, 10), Span(0, 10), Span(5, 10))
        val fragments = spans.mapIndexed { i, span -> Node(NodeKind.FRAGMENT, MediaId(i + 2L), media.id, span = span, attributes = mapOf("fragment" to listOf("${i + 1}"), "totalFragments" to listOf("5")), life = life) }
        val input = Snapshot(0, "test.null-bounds", listOf(root, media) + fragments)
        val flat = FlatCatalogs.encode(input)
        val result = flat.snapshot()
        assertEquals(spans, result.nodes.filter { it.kind == NodeKind.FRAGMENT }.map { it.span })
        for ((i, span) in spans.withIndex()) {
            val node = flat.mdia().nodes(flat.sources(MediaId(i + 2L)).single())!!
            assertNull(node.source())
            assertEquals(span.beginUs != null, node.hasBeginUs())
            assertEquals(span.endUs != null, node.hasEndUs())
        }
        val physical = flat.mdia().nodes(flat.sources(media.id).single())!!.source()!!
        assertTrue(physical.hasModified() && physical.hasSize())
        assertEquals(0L, physical.modified())
        assertEquals(0L, physical.size())
        assertEquals(listOf(0), flat.roots())
        for (i in result.nodes.indices) assertEquals(result.childrenOf(i), flat.childrenOf(i))
        assertThrows(IllegalArgumentException::class.java) { Span(-1, null) }
        assertThrows(IllegalArgumentException::class.java) { Span(0, -1) }
        assertThrows(IllegalArgumentException::class.java) { Span(10, 10) }
    }

    @Test fun parentIdsRejectDirectoryCycles() {
        val life = Life(0)
        val a = Node(NodeKind.DIRECTORY, MediaId(1), MediaId(2), SourceInfo("A"), life = life)
        val b = Node(NodeKind.DIRECTORY, MediaId(2), MediaId(1), SourceInfo("B"), life = life)
        assertThrows(IllegalArgumentException::class.java) { FlatCatalogs.encode(Snapshot(0, "test.cycle", listOf(a, b))) }
    }

    @Test fun laterBaseCollisionUpdatesExistingFragmentParentIdsAndStableAlbum() {
        val p = MemoryProvider()
        p.file("image.flac", byteArrayOf(1, 2, 3))
        p.file("disc.cue", cue())
        val indexer = Indexer(MockMediaID(), 10, listOf(p))
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        val original = first.nodes.single { it.kind == NodeKind.MEDIA }
        assertNull(original.id.lo)
        assertTrue(first.nodes.filter { it.kind == NodeKind.FRAGMENT }.all { it.parentId == original.id })
        p.file("copy.flac", byteArrayOf(1, 2, 3))
        val next = indexer.scan(listOf(p.rootSpec()), first, 2)
        val baseId = next.nodes.first { it.kind == NodeKind.MEDIA }.id
        assertNotNull(baseId.lo)
        assertTrue(next.nodes.filter { it.kind == NodeKind.FRAGMENT }.all { it.parentId == baseId })
        assertEquals(explicit(first).single().id, explicit(next).single().id)
        assertNoExtraTracks(next)
    }
}
