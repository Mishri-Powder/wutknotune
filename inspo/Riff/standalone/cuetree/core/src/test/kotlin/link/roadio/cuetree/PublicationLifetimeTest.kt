// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test

class PublicationLifetimeTest {
    @Test fun sourceAndOrphanCatalogsExpireOnProjectionPublications() {
        val p = MemoryProvider()
        p.file("track.mp3")
        val indexer =
            Indexer(
                MockMediaID(),
                3,
                listOf(p),
                MetadataReader { _, _, _ ->
                    Observation(mapOf("album" to listOf("Album"), "artist" to listOf("Artist"), "genre" to listOf("Rock")), emptyList(), emptyList())
                },
            )
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        val missing = indexer.scan(emptyList(), first, 2)
        val retained = missing.advancePublication(3, 3)
        assertEquals(missing.nodes, retained.nodes)
        val expired = retained.advancePublication(4, 3)
        assertTrue(expired.nodes.isEmpty())
        assertTrue(expired.albums.all { it.sources.isEmpty() && it.tracks.isEmpty() })
        assertTrue(expired.artists.all { it.albums.isEmpty() && it.life.generation == 3L })
        assertTrue(expired.genres.all { it.albums.isEmpty() && it.life.generation == 3L })
        val removed = expired.advancePublication(6, 3)
        assertTrue(removed.artists.isEmpty() && removed.genres.isEmpty() && removed.attributeCatalog.isEmpty())
        assertEquals(removed, FlatCatalogs.encode(removed).snapshot())
    }

    @Test fun unrelatedExpiryPreservesAvailableChapterExpansionAndExcludesContainer() {
        val p = MemoryProvider()
        p.file("book.m4b", byteArrayOf(1, 2, 3))
        val unrelated = p.file("loose.mp3")
        val reader =
            MetadataReader { input, _, _ ->
                if (input.id == "book.m4b") {
                    Observation(
                        mapOf("album" to listOf("Book"), "artist" to listOf("Author")),
                        listOf(
                            TableObservation(
                                "chapters",
                                emptyMap(),
                                listOf(
                                    Section(null, Span(0, 10), mapOf("title" to listOf("First"))),
                                    Section(null, Span(10, 20), mapOf("title" to listOf("Second"))),
                                ),
                            ),
                        ),
                        emptyList(),
                        sourceEvidence = mapOf(SOURCE_DURATION_US to listOf("1000000")),
                    )
                } else {
                    Observation(emptyMap(), emptyList(), emptyList())
                }
            }
        val indexer = Indexer(MockMediaID(), 10, listOf(p), reader)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        p.items.remove(unrelated)
        val retained = indexer.scan(listOf(p.rootSpec()), first, 2)
        val expired = retained.advancePublication(11, 10)
        val album = expired.albums.single { it.sources.isNotEmpty() }
        assertEquals(AlbumKind.ORDINARY, album.kind)
        assertEquals(listOf("First", "Second"), album.sources.map { expired.nodes[it].name })
        assertTrue(album.sources.all { expired.nodes[it].kind == NodeKind.FRAGMENT })
        assertTrue(expired.albums.filter { it.kind.isSingles }.all { it.sources.isEmpty() })
    }

    @Test fun removingAnAncestorTransfersShortAliasOnlyToEquivalentSurvivor() {
        val oldRoot = MediaId(1)
        val root = MediaId(2)
        val full = MediaId(10, 1)
        val contender = MediaId(10, 2)
        val snapshot =
            Snapshot(
                2,
                "test",
                listOf(
                    Node(NodeKind.DIRECTORY, oldRoot, source = SourceInfo("Old", provider = "posix", locator = "/old"), life = Life(1, Flags.ACSS)),
                    Node(NodeKind.DIRECTORY, root, source = SourceInfo("Music", provider = "posix", locator = "/music"), life = Life(2)),
                    Node(NodeKind.MEDIA, full, oldRoot, SourceInfo("old.flac"), life = Life(2, Flags.ACSS), identityFlags = Flags.SHRT),
                    Node(NodeKind.MEDIA, full, root, SourceInfo("copy.flac"), life = Life(2)),
                    Node(NodeKind.MEDIA, contender, root, SourceInfo("other.flac"), life = Life(2)),
                ),
            )
        val next = snapshot.advancePublication(3, 2)
        assertEquals(listOf("copy.flac"), next.sources(MediaId(10)).map { next.nodes[it].name })
        assertEquals(0, next.nodes.single { it.id == contender }.identityFlags and Flags.SHRT)
        assertEquals(
            2L,
            next.nodes
                .single { it.id == full }
                .life.generation,
        )
        validate(next)
    }
}
