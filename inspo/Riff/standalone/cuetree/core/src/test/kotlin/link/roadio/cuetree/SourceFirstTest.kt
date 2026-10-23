// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test

class SourceFirstTest {
    private fun Snapshot.factsOnly() = copy(albums = emptyList(), artists = emptyList(), genres = emptyList())

    private fun assertSameFacts(
        a: Snapshot,
        b: Snapshot,
    ) {
        assertEquals(a.nodes, b.nodes)
        val referenced =
            a.nodes
                .flatMap { it.attributes.typed() }
                .map { it.id }
                .toSet()
        assertEquals(a.attributeCatalog.filter { it.id in referenced }, b.attributeCatalog.filter { it.id in referenced })
        assertEquals(a.nodes, FlatCatalogs.encode(b).snapshot().nodes)
    }

    private fun cue(
        first: String = "z.wav",
        second: String = "a.wav",
    ) = """PERFORMER "Artist"
            |TITLE "Explicit"
            |REM GENRE "Rock"
            |FILE "$first" WAVE
            |TRACK 01 AUDIO
            |TITLE "First"
            |INDEX 01 00:00:00
            |FILE "$second" WAVE
            |TRACK 02 AUDIO
            |TITLE "Second"
            |INDEX 01 00:00:00
        """.trimMargin().toByteArray()

    @Test fun separateTrackCueOverridesOnlyPresentFieldsAndReusesStoredEvidence() {
        val provider = MemoryProvider()
        provider.file("one.flac", byteArrayOf(1))
        provider.file("two.flac", byteArrayOf(2))
        provider.file("disc.cue", byteArrayOf(3))
        val reader =
            MetadataReader { input, hint, _ ->
                if (hint == "cue") {
                    Observation(
                        emptyMap(),
                        listOf(
                            TableObservation(
                                "cue",
                                mapOf("album" to listOf("CUE album"), "year" to listOf("2026")),
                                listOf(
                                    Section("one.flac", Span(0), mapOf("title" to listOf("CUE first"))),
                                    Section("two.flac", Span(0), emptyMap()),
                                ),
                            ),
                        ),
                        emptyList(),
                    )
                } else {
                    Observation(
                        mapOf(
                            "album" to listOf("file album"),
                            "title" to listOf(if (input.id == "one.flac") "File first" else "File second"),
                            "artist" to listOf("File artist"),
                            "year" to listOf("2001"),
                        ),
                        emptyList(),
                        emptyList(),
                    )
                }
            }
        val indexer = Indexer(MockMediaID(), 10, listOf(provider), reader)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        val stored = FlatCatalogs.encode(first).snapshot()
        assertEquals(1, stored.nodes.count { it.kind == NodeKind.METADATA })
        assertEquals(first.nodes.single { it.kind == NodeKind.METADATA }.tables, stored.nodes.single { it.kind == NodeKind.METADATA }.tables)
        val opened = provider.opened
        val admitted = mutableListOf<String>()
        val next =
            indexer.scan(
                listOf(provider.rootSpec()),
                stored,
                2,
                onSource = { node, _, _ -> admitted += node.name },
            )
        assertEquals(opened, provider.opened)
        assertEquals(setOf("one.flac", "two.flac"), admitted.toSet())
        val album = next.albums.single { it.kind == AlbumKind.EXPLICIT && it.life.available }
        assertEquals("CUE album", album.title)
        val tracks = album.sources.map { next.nodes[it] }
        assertEquals(listOf("CUE first", "File second"), tracks.map { it.attributes.first("title") })
        assertEquals(listOf("File artist", "File artist"), tracks.map { it.attributes.first("artist") })
        assertEquals(listOf("2026", "2026"), tracks.map { it.attributes.first("year") })
    }

    @Test fun projectionsCannotChangeFactualRefreshAbsenceReturnOrExpiry() {
        val p = MemoryProvider()
        p.file("a.wav")
        p.file("z.wav")
        p.file("disc.cue", cue())
        val indexer = Indexer(MockMediaID(), 10, listOf(p))
        val first = indexer.scan(listOf(p.rootSpec()), generation = 100)
        val facts = FlatCatalogs.encode(first.factsOnly()).snapshot()
        val normal = indexer.scan(listOf(p.rootSpec()), first, 101)
        val rebuilt = indexer.scan(listOf(p.rootSpec()), facts, 101)
        assertSameFacts(normal, rebuilt)
        val album = rebuilt.albums.single { it.kind == AlbumKind.EXPLICIT }
        assertEquals(listOf("z.wav", "a.wav"), album.sources.map { rebuilt.nodes[requireNotNull(rebuilt.parentOf(it))].name })
        assertEquals("Explicit", album.title)

        val lost = indexer.scan(emptyList(), normal, 104)
        val lostWithoutCatalog = indexer.scan(emptyList(), rebuilt.factsOnly(), 104)
        assertSameFacts(lost, lostWithoutCatalog)
        assertTrue(lostWithoutCatalog.nodes.all { !it.life.available && it.life.generation == 101L })
        assertTrue(lostWithoutCatalog.albums.none { it.kind == AlbumKind.EXPLICIT })
        assertEquals(album.tracks, lost.albums.single { it.kind == AlbumKind.EXPLICIT }.tracks)

        val back = indexer.scan(listOf(p.rootSpec()), lost, 105)
        val backWithoutCatalog = indexer.scan(listOf(p.rootSpec()), lostWithoutCatalog.factsOnly(), 105)
        assertSameFacts(back, backWithoutCatalog)
        assertTrue(backWithoutCatalog.nodes.all { it.life.available && it.life.generation == 105L })

        val expired = indexer.scan(emptyList(), back, 115)
        val expiredWithoutCatalog = indexer.scan(emptyList(), backWithoutCatalog.factsOnly(), 115)
        assertSameFacts(expired, expiredWithoutCatalog)
        assertTrue(expired.nodes.isEmpty())
    }

    @Test fun cueOrderChangesComeFromFilesAtNextGeneration() {
        val p = MemoryProvider()
        p.file("a.wav")
        p.file("z.wav")
        val sheet = p.file("disc.cue", cue())
        val indexer = Indexer(MockMediaID(), 10, listOf(p))
        val first = indexer.scan(listOf(p.rootSpec()), generation = 100)
        p.items.getValue(sheet).bytes = cue("a.wav", "z.wav")
        p.items.getValue(sheet).modified++
        val reordered = indexer.scan(listOf(p.rootSpec()), FlatCatalogs.encode(first).snapshot(), 101)
        val rebuilt = indexer.scan(listOf(p.rootSpec()), first.factsOnly(), 101)
        assertSameFacts(reordered, rebuilt)
        val album = reordered.albums.single { it.kind == AlbumKind.EXPLICIT && it.sources.all { source -> reordered.nodes[source].life.available } }
        assertNotEquals(first.albums.single { it.kind == AlbumKind.EXPLICIT }.id, album.id)
        assertEquals(listOf("a.wav", "z.wav"), album.sources.map { reordered.nodes[requireNotNull(reordered.parentOf(it))].name })
        assertEquals(album.tracks, rebuilt.albums.single { it.kind == AlbumKind.EXPLICIT }.tracks)
    }

    @Test fun missingCueDoesNotLetRetainedAlbumRefreshFragments() {
        val p = MemoryProvider()
        p.file("a.wav")
        p.file("z.wav")
        val sheet = p.file("disc.cue", cue())
        val indexer = Indexer(MockMediaID(), 10, listOf(p))
        val first = indexer.scan(listOf(p.rootSpec()), generation = 100)
        p.items.remove(sheet)
        val lost = indexer.scan(listOf(p.rootSpec()), first, 106)
        val withoutCatalog = indexer.scan(listOf(p.rootSpec()), first.factsOnly(), 106)
        assertSameFacts(lost, withoutCatalog)
        assertTrue(lost.nodes.filter { it.kind == NodeKind.FRAGMENT }.all { !it.life.available && it.life.generation == 100L })
        assertTrue(lost.nodes.filter { it.kind == NodeKind.MEDIA }.all { it.life.available && it.life.generation == 106L })
        val expired = indexer.scan(listOf(p.rootSpec()), lost, 110)
        assertTrue(expired.nodes.none { it.kind == NodeKind.FRAGMENT })
        assertTrue(expired.albums.single { it.kind == AlbumKind.EXPLICIT }.let { !it.life.available && it.sources.isEmpty() })
    }

    @Test fun embeddedTocRefreshAndRemovalUseMediaObservations() {
        val p = MemoryProvider()
        val image = p.file("image.mp3")
        var sections = listOf(Section(null, Span(0, 10), emptyMap()), Section(null, Span(10, 20), emptyMap()))
        val metadata =
            MetadataReader { _, _, _ ->
                Observation(emptyMap(), if (sections.isEmpty()) emptyList() else listOf(TableObservation("toc", emptyMap(), sections)), emptyList())
            }
        val indexer = Indexer(MockMediaID(), 10, listOf(p), metadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 100)
        val again = indexer.scan(listOf(p.rootSpec()), FlatCatalogs.encode(first.factsOnly()).snapshot(), 101)
        assertTrue(again.nodes.all { it.life.available && it.life.generation == 101L })
        assertEquals(first.nodes.map { it.mediaId }, again.nodes.map { it.mediaId })
        sections = emptyList()
        p.items.getValue(image).modified++
        val lost = indexer.scan(listOf(p.rootSpec()), again, 106)
        assertTrue(lost.nodes.filter { it.kind == NodeKind.FRAGMENT }.all { !it.life.available && it.life.generation == 101L })
        assertTrue(indexer.scan(listOf(p.rootSpec()), lost.factsOnly(), 111).nodes.none { it.kind == NodeKind.FRAGMENT })
    }
}
