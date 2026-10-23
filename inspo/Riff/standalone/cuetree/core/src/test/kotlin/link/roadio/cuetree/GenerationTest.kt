// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class GenerationTest {
    @Test fun sourceAvailabilityAndCatalogReferencesDriveIndependentTtl() {
        val p = MemoryProvider()
        p.file("image.mp3")
        p.file(
            "tracks.cue",
            """PERFORMER "Artist"
                |REM GENRE "Rock"
                |FILE "image.mp3" WAVE
                |TRACK 01 AUDIO
                |INDEX 01 00:00:00
                |TRACK 02 AUDIO
                |INDEX 01 00:01:00
            """.trimMargin().toByteArray(),
        )
        val indexer = Indexer(MockMediaID(), ttl = 10, providers = listOf(p))
        val first = indexer.scan(listOf(p.rootSpec()), generation = 100)

        fun retainedLife(snapshot: Snapshot) =
            snapshot.nodes.map { it.life } +
                snapshot.albums.filter { it.kind != AlbumKind.SINGLES }.map { it.life } +
                snapshot.artists.map { it.life } + snapshot.genres.map { it.life }

        assertEquals(100L, first.generation)
        assertTrue(first.nodes.any { it.kind == NodeKind.FRAGMENT })
        assertTrue(first.artists.isNotEmpty() && first.genres.isNotEmpty())
        assertTrue(retainedLife(first).all { it.available && it.generation == 100L })
        val lost = indexer.scan(emptyList(), first, 105)
        val again = indexer.scan(emptyList(), lost, 108)
        assertEquals(108L, again.generation)
        assertTrue(again.nodes.all { !it.life.available && it.life.generation == 100L })
        assertTrue((again.albums.filter { it.kind != AlbumKind.SINGLES }.map { it.life } + again.artists.map { it.life } + again.genres.map { it.life }).all { !it.available && it.generation == 108L })
        assertTrue(again.attributeCatalog.all { !it.life.available && it.life.generation == 108L })
        assertEquals(again.copy(diagnostics = emptyList()), FlatCatalogs.encode(again).snapshot())

        val back = indexer.scan(listOf(p.rootSpec()), again, 109)
        assertEquals(first.nodes.map { it.folderId }, back.nodes.map { it.folderId })
        assertEquals(first.albums.map { it.id }, back.albums.map { it.id })
        assertEquals(first.artists.map { it.id }, back.artists.map { it.id })
        assertTrue(retainedLife(back).all { it.available && it.generation == 109L })
        val missing = indexer.scan(emptyList(), back, 118)
        assertTrue(missing.nodes.all { !it.life.available && it.life.generation == 109L })
        assertTrue(missing.artists.all { !it.life.available && it.life.generation == 118L })
        val expired = indexer.scan(emptyList(), missing, 119)
        assertTrue(expired.nodes.isEmpty())
        assertTrue(expired.artists.all { !it.life.available && it.life.generation == 118L && it.albums.isEmpty() })
        assertTrue(expired.genres.all { !it.life.available && it.life.generation == 118L && it.albums.isEmpty() })
        val withinTtl = indexer.scan(emptyList(), expired, 127)
        assertEquals(expired.artists, withinTtl.artists)
        val collected = indexer.scan(emptyList(), withinTtl, 128)
        assertTrue(collected.artists.isEmpty() && collected.genres.isEmpty())
        assertTrue(collected.attributeCatalog.isEmpty())
        assertEquals(AlbumKind.SINGLES, collected.albums.single().kind)
        assertTrue(
            collected.albums
                .single()
                .tracks
                .isEmpty(),
        )
    }

    @Test fun eachSuccessfulCheckpointGetsItsOwnGenerationAndConsumesTtl() {
        val directory = Files.createTempDirectory("cuetree-checkpoint-generation")
        try {
            val p = MemoryProvider()
            val old = p.file("old.mp3")
            val indexer = Indexer(MockMediaID(), ttl = 2, providers = listOf(p), metadata = emptyMetadata)
            val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
            p.items.remove(old)
            p.file("new-a.mp3")
            p.file("new-b.mp3")
            val store = SnapshotStore(directory)
            store.write(first)
            val publications = mutableListOf<Long>()
            val final =
                indexer.scan(listOf(p.rootSpec()), first, 2, onCheckpoint = { progress, offer ->
                    if (progress.kind != ScanCheckpointKind.MEDIA) {
                        false
                    } else {
                        val offered = offer()
                        store.write(offered)
                        publications += offered.generation
                        true
                    }
                })
            assertEquals(listOf(2L, 3L), publications)
            assertEquals(4L, final.generation)
            assertTrue(final.nodes.none { it.name == "old.mp3" })
            store.write(final)
            assertEquals(4L, store.read()?.generation)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun firstMissingObservationDoesNotStartANewTtl() {
        val p = MemoryProvider()
        p.file("track.mp3")
        val indexer = Indexer(MockMediaID(), 10, listOf(p), emptyMetadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 100)
        val expired = indexer.scan(emptyList(), first, 110)
        assertTrue(expired.nodes.isEmpty())
    }

    @Test fun publicationsRequireIncreasingGeneration() {
        val directory = Files.createTempDirectory("cuetree-generation")
        try {
            val p = MemoryProvider()
            p.file("one.mp3")
            val indexer = Indexer(MockMediaID(), 10, listOf(p), emptyMetadata)
            val first = indexer.scan(listOf(p.rootSpec()), generation = 5000)
            val store = SnapshotStore(directory)
            store.write(first)
            val pointer = Files.readString(directory.resolve("CURRENT"))
            p.file("two.mp3")
            assertThrows(IllegalArgumentException::class.java) {
                indexer.scan(listOf(p.rootSpec()), first, 5000)
            }
            val next = indexer.scan(listOf(p.rootSpec()), first, 5001)
            assertEquals(5001L, next.generation)
            assertEquals(3, next.nodes.size)
            store.write(next)
            assertNotEquals(pointer, Files.readString(directory.resolve("CURRENT")))
            assertEquals(next, store.read())
            assertThrows(IllegalArgumentException::class.java) { store.write(next) }
            assertThrows(IllegalArgumentException::class.java) {
                indexer.scan(listOf(p.rootSpec()), next, 4999)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun zeroTtlKeepsSeenRecordsAndImmediatelyRemovesMissingOnes() {
        val p = MemoryProvider()
        p.file("track.mp3")
        val indexer = Indexer(MockMediaID(), 0, listOf(p), emptyMetadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = Long.MAX_VALUE)
        assertEquals(2, first.nodes.size)
        assertThrows(IllegalArgumentException::class.java) {
            indexer.scan(listOf(p.rootSpec()), first, Long.MAX_VALUE)
        }
    }
}
