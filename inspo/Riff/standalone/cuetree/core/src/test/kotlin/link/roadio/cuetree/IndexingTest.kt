// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.ByteArrayInput
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CancellationException

class IndexingTest {
    @Test fun stableRootIdentitySurvivesLocatorChangeAndSeparatesReplacement() {
        val provider = MemoryProvider()
        provider.file("song.mp3")
        val indexer = Indexer(MockMediaID(), 1000, listOf(provider), emptyMetadata)
        val first = indexer.scan(listOf(RootSpec(provider.id, "grant-a", "Music", "volume-a")), generation = 1)
        val root = first.nodes.single { it.isRoot }
        val moved = indexer.scan(listOf(RootSpec(provider.id, "grant-b", "Music", "volume-a")), first, 2)
        assertEquals(root.id, moved.nodes.single { it.isRoot }.id)
        assertEquals(
            "grant-b",
            moved.nodes
                .single { it.isRoot }
                .source
                ?.locator,
        )
        assertEquals(
            "volume-a",
            moved.nodes
                .single { it.isRoot }
                .attributes[ROOT_IDENTITY_ATTRIBUTE]
                ?.single(),
        )
        val decoded = FlatCatalogs.encode(moved).snapshot()
        assertEquals(
            "volume-a",
            decoded.nodes
                .single { it.isRoot }
                .attributes[ROOT_IDENTITY_ATTRIBUTE]
                ?.single(),
        )
        val replaced = indexer.scan(listOf(RootSpec(provider.id, "grant-b", "Music", "volume-b")), moved, 3)
        assertNotEquals(root.id, replaced.nodes.single { it.isRoot && it.life.available }.id)
        assertFalse(
            replaced.nodes
                .single { it.id == root.id }
                .life.available,
        )
    }

    @Test fun mockHashesWholeFileInBoundedChunks() {
        val input = ByteArrayInput(ByteArray(200_003) { it.toByte() })
        val a = MockMediaID(7)
        val b = MockMediaID(65536)
        assertEquals(a.high(input, null, Cancellation()), b.high(input, null, Cancellation()))
        assertEquals(a.low(input, null, Cancellation()), b.low(input, null, Cancellation()))
        assertNotEquals(a.high(input, null, Cancellation()), a.low(input, null, Cancellation()))
        assertEquals(0x2d06800538d394c2L, a.high(ByteArrayInput(byteArrayOf()), null, Cancellation()))
        assertNotEquals(a.high(input, null, Cancellation()), a.high(input, Span(), Cancellation()))
        assertNotEquals(a.high(input, Span(0, 1), Cancellation()), a.high(input, Span(0, 2), Cancellation()))
    }

    @Test fun noTailUntilCollisionAndZeroIsAValidTail() {
        val p = MemoryProvider()
        p.file("a.mp3", byteArrayOf(0))
        val id = CountingIdentity()
        val indexer = Indexer(id, 1000, listOf(p), emptyMetadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 10)
        assertEquals(0, id.lows)
        assertTrue(first.nodes.filter { it.kind == NodeKind.MEDIA }.all { it.identityFlags and Flags.SHRT != 0 })
        assertNull(
            first.nodes
                .single { it.kind == NodeKind.MEDIA }
                .mediaId!!
                .lo,
        )
        p.file("b.mp3", byteArrayOf(1))
        val second = indexer.scan(listOf(p.rootSpec()), first, 11)
        assertEquals(2, id.highs)
        assertEquals(2, id.lows)
        assertEquals(setOf(0L, 1L), second.nodes.mapNotNull { it.mediaId?.lo }.toSet())
        assertEquals(1, second.sources(MediaId(123, 0)).size)
        assertEquals("a.mp3", second.nodes[second.sources(MediaId(123)).single()].name)
    }

    @Test fun everyCollisionCheckpointRoundTripsAsOneValidCohort() {
        val p = MemoryProvider()
        p.file("a.mp3", byteArrayOf(0))
        p.file("b.mp3", byteArrayOf(1))
        val checkpoints = mutableListOf<Snapshot>()
        val final =
            Indexer(CountingIdentity(), 1000, listOf(p), emptyMetadata).scan(
                listOf(p.rootSpec()),
                generation = 1,
                onCheckpoint = { progress, offer ->
                    if (progress.kind != ScanCheckpointKind.MEDIA) return@scan false
                    val candidate = offer()
                    checkpoints += FlatCatalogs.encode(candidate).snapshot()
                    false
                },
            )
        assertEquals(2, checkpoints.size)
        assertEquals(1, checkpoints.first().nodes.count { it.kind == NodeKind.MEDIA })
        assertEquals(
            setOf(0L, 1L),
            checkpoints
                .last()
                .nodes
                .mapNotNull { it.mediaId?.lo }
                .toSet(),
        )
        assertEquals(setOf(0L, 1L), final.nodes.mapNotNull { it.mediaId?.lo }.toSet())
    }

    @Test fun inaccessibleAnchorKeepsShortIdentityUntilReturn() {
        val p = MemoryProvider()
        val a = p.file("a.mp3", byteArrayOf(7))
        val id = CountingIdentity()
        val indexer = Indexer(id, 1000, listOf(p), emptyMetadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 10)
        p.denied += a
        p.file("b.mp3", byteArrayOf(9))
        val second = indexer.scan(listOf(p.rootSpec()), first, 11)
        val short = second.sources(MediaId(123)).single()
        assertEquals("a.mp3", second.nodes[short].name)
        assertFalse(second.nodes[short].life.available)
        assertTrue(second.nodes.filter { it.mediaId != null }.all { it.identityFlags and Flags.RESL != 0 })
        p.denied.clear()
        val third = indexer.scan(listOf(p.rootSpec()), second, 12)
        assertEquals(setOf(7L, 9L), third.nodes.mapNotNull { it.mediaId?.lo }.toSet())
        assertTrue(third.nodes.all { it.identityFlags and Flags.RESL == 0 })
        assertEquals("a.mp3", third.nodes[third.sources(MediaId(123)).single()].name)
    }

    @Test fun sameFullIdRetainsAlternativeSources() {
        val p = MemoryProvider()
        p.file("a.mp3")
        p.file("b.mp3")
        val generator = CountingIdentity().also { it.fixedLow = 0 }
        val indexer = Indexer(generator, 1000, listOf(p), emptyMetadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        assertEquals(2, first.sources(MediaId(123, 0)).size)
        p.denied += p.items.keys.first { p.items[it]?.name == "a.mp3" }
        val next = indexer.scan(listOf(p.rootSpec()), first, 2)
        assertTrue(next.available(MediaId(123, 0)))
    }

    @Test fun effectiveAvailabilityAndTtlAreTopDown() {
        val p = MemoryProvider()
        val dir = p.directory("Disc")
        p.file("track.mp3", parent = dir)
        val indexer = Indexer(MockMediaID(), 10, listOf(p), emptyMetadata)
        val options = ScanOptions()
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1, options = options)
        p.denied += p.rootHandle
        p.calls.clear()
        val missing = indexer.scan(listOf(p.rootSpec()), first, 2, options)
        assertEquals(listOf("root:opaque-grant"), p.calls)
        assertTrue(missing.nodes.all { it.flags and Flags.ACSS != 0 && it.life.generation == 1L })
        val again = indexer.scan(listOf(p.rootSpec()), missing, 5, options)
        assertTrue(again.nodes.all { !it.life.available && it.life.generation == 1L })
        p.denied.clear()
        p.denied += dir
        val partial = indexer.scan(listOf(p.rootSpec()), again, 8, options)
        assertTrue(
            partial.nodes
                .single { it.isRoot }
                .life.available,
        )
        assertFalse(
            partial.nodes
                .single { it.kind == NodeKind.MEDIA }
                .life.available,
        )
        p.denied.clear()
        val back = indexer.scan(listOf(p.rootSpec()), partial, 9, options)
        assertEquals(first.nodes.map { it.folderId }, back.nodes.map { it.folderId })
        assertTrue(back.nodes.all { it.life.available && it.life.generation == back.generation })
        p.denied += p.rootHandle
        val lost = indexer.scan(listOf(p.rootSpec()), back, 10, options)
        val expired = indexer.scan(listOf(p.rootSpec()), lost, 20, options)
        assertTrue(expired.nodes.none { it.kind == NodeKind.MEDIA })
        p.denied.clear()
        val readmitted = indexer.scan(listOf(p.rootSpec()), expired, 21, options)
        assertNotEquals(first.nodes.single { it.isRoot }.folderId, readmitted.nodes.single { it.isRoot }.folderId)
    }

    @Test fun checkpointRetainsUnvisitedSourcesAndStableCatalogIdentity() {
        val p = MemoryProvider()
        p.file("a.mp3")
        val removed = p.file("z.mp3")
        val indexer =
            Indexer(
                MockMediaID(),
                1000,
                listOf(p),
                metadataByName(
                    mapOf(
                        "a.mp3" to mapOf("album" to listOf("Album")),
                        "b.mp3" to mapOf("album" to listOf("Album")),
                        "z.mp3" to mapOf("album" to listOf("Album")),
                    ),
                ),
            )
        val previous = indexer.scan(listOf(p.rootSpec()), generation = 1)
        p.items.remove(removed)
        p.file("b.mp3")
        var checkpoint: Snapshot? = null
        val final =
            indexer.scan(listOf(p.rootSpec()), previous, 2, onCheckpoint = { progress, offer ->
                if (progress.kind == ScanCheckpointKind.DIRECTORY && checkpoint == null) checkpoint = offer()
                false
            })
        val intermediate = requireNotNull(checkpoint)
        assertTrue(
            intermediate.nodes
                .single { it.name == "z.mp3" }
                .life.available,
        )
        assertFalse(
            final.nodes
                .single { it.name == "z.mp3" }
                .life.available,
        )

        fun albumFor(
            snapshot: Snapshot,
            source: String,
        ) = snapshot.albums.single { album ->
            album.sources.any { snapshot.nodes[it].name == source }
        }
        assertEquals(albumFor(previous, "a.mp3").id, albumFor(intermediate, "a.mp3").id)
        assertEquals(albumFor(intermediate, "a.mp3").id, albumFor(final, "a.mp3").id)
        validate(intermediate)
        validate(final)
    }

    @Test fun completedAlbumFolderOffersCatalogBeforeLaterFolder() {
        val p = MemoryProvider()
        val album = p.directory("A Album")
        p.file("01.mp3", parent = album)
        p.file("02.mp3", parent = album)
        val later = p.directory("Z Later")
        p.file("later.mp3", parent = later)
        val metadata =
            metadataByName(
                mapOf(
                    "01.mp3" to mapOf("album" to listOf("With The Beatles"), "trackNumber" to listOf("1/2")),
                    "02.mp3" to mapOf("album" to listOf("With The Beatles"), "trackNumber" to listOf("2/2")),
                ),
            )
        var early: Snapshot? = null
        Indexer(MockMediaID(), 1000, listOf(p), metadata).scan(
            listOf(p.rootSpec()),
            generation = 1,
            onCheckpoint = { progress, offer ->
                if (progress.kind == ScanCheckpointKind.MEDIA && progress.completedMedia == 2L) early = offer()
                false
            },
        )
        val checkpoint = requireNotNull(early)
        assertTrue(checkpoint.nodes.none { it.name == "later.mp3" })
        val albumRow = checkpoint.albums.single { it.title == "With The Beatles" }
        assertEquals(2, albumRow.sources.size)
        validate(checkpoint)
    }

    @Test fun publicationFailureAbortsScanWithoutChangingPrevious() {
        val p = MemoryProvider()
        p.file("song.mp3")
        val indexer = Indexer(MockMediaID(), 1000, listOf(p), emptyMetadata)
        val previous = indexer.scan(listOf(p.rootSpec()), generation = 1)
        val failure =
            assertThrows(IllegalStateException::class.java) {
                indexer.scan(listOf(p.rootSpec()), previous, 2, onCheckpoint = { _, _ ->
                    throw java.io.IOException("publication failed")
                })
            }
        assertEquals("Checkpoint publication failed", failure.message)
        assertEquals(1L, previous.generation)
        assertTrue(previous.nodes.all { it.life.available })
    }

    @Test fun playbackWalksOpaqueHandlesAndClosesInput() {
        val p = MemoryProvider()
        val d = p.directory("Album")
        p.file("song.mp3", parent = d)
        val snapshot = Indexer(MockMediaID(), 1000, listOf(p), emptyMetadata).scan(listOf(p.rootSpec()), generation = 1)
        p.calls.clear()
        SourceResolver(listOf(p)).resolve(snapshot, snapshot.nodes.indexOfFirst { it.kind == NodeKind.MEDIA }).open().use { assertEquals("song.mp3", it.id) }
        assertEquals(listOf("root:opaque-grant", "children:971", "children:${d.token}"), p.calls.take(3))
        assertEquals(p.opened, p.closed)
    }

    @Test fun cancellationDoesNotMutatePreviousSnapshot() {
        val p = MemoryProvider()
        p.file("a.mp3", ByteArray(200_000))
        val indexer = Indexer(MockMediaID(), 1000, listOf(p), emptyMetadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        var cancelled = false
        p.onRead = { cancelled = true }
        assertThrows(CancellationException::class.java) {
            indexer.scan(listOf(p.rootSpec()), first, 2, ScanOptions(forceRefresh = true), Cancellation { cancelled })
        }
        assertEquals(1L, first.generation)
        assertTrue(first.nodes.all { it.life.available })
        assertEquals(p.opened, p.closed)
    }

    @Test fun listedStatsDriveRefreshWithoutPerChildOrPostReadStat() {
        val memory = MemoryProvider()
        val folder = memory.directory("Album")
        val file = memory.file("a.mp3", parent = folder)
        val provider =
            object : SourceProvider by memory {
                override fun children(directory: SourceHandle): List<Child> =
                    memory.children(directory).map { child ->
                        val item = memory.items.getValue(child.handle as MemoryProvider.Handle)
                        child.copy(stat = SourceStat(item.bytes == null, Stamp(item.modified, item.bytes?.size?.toLong() ?: 0)))
                    }
            }
        val indexer = Indexer(MockMediaID(), 1000, listOf(provider), emptyMetadata)
        val root = memory.rootSpec().copy(initialStat = SourceStat(true, Stamp(1, 0)))
        val first = indexer.scan(listOf(root), generation = 1)
        assertFalse(memory.calls.any { it.startsWith("stat:") })
        val opened = memory.opened

        memory.calls.clear()
        memory.items.getValue(file).modified++
        val second = indexer.scan(listOf(root), first, 2)
        assertFalse(memory.calls.any { it.startsWith("stat:") })
        assertEquals(opened + 1, memory.opened)
        assertEquals(
            2L,
            second.nodes
                .single { it.kind == NodeKind.MEDIA }
                .source
                ?.modified,
        )

        memory.calls.clear()
        val mediaIndex = second.nodes.indexOfFirst { it.kind == NodeKind.MEDIA }
        SourceResolver(listOf(provider)).resolve(second, mediaIndex).open().use { it.read(0, 1) }
        assertEquals(listOf("stat:${file.token}"), memory.calls.filter { it.startsWith("stat:") })
    }

    @Test fun realPosixUsesMtimeAndDoesNotFollowSymlinks() {
        val root = Files.createTempDirectory("cuetree-media")
        try {
            val dir = Files.createDirectory(root.resolve("Album"))
            Files.write(dir.resolve("song.wav"), byteArrayOf(1, 2, 3))
            Files.createSymbolicLink(root.resolve("loop"), root)
            val indexer = Indexer(MockMediaID(), 1000, metadata = emptyMetadata)
            val first = indexer.scan(listOf(RootSpec("posix", root.toString())), generation = 1)
            assertEquals(3, first.nodes.size)
            assertTrue(first.nodes.filter { !it.isRoot }.all { !it.isRoot && '/' !in it.name })
            SourceResolver(listOf(PosixProvider())).resolve(first, first.nodes.indexOfFirst { it.kind == NodeKind.MEDIA }).open().use { assertArrayEquals(byteArrayOf(1, 2, 3), it.read(0, 3)) }
            Files.write(dir.resolve("song.wav"), byteArrayOf(1, 2, 3, 4))
            val next = indexer.scan(listOf(RootSpec("posix", root.toString())), first, 2)
            assertNotEquals(first.nodes.single { it.kind == NodeKind.MEDIA }.mediaId, next.nodes.single { it.kind == NodeKind.MEDIA }.mediaId)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun schemeAndClockMismatchAreRejected() {
        val indexer = Indexer(MockMediaID(), 1000)
        assertThrows(IllegalArgumentException::class.java) { indexer.scan(emptyList(), Snapshot.empty("android"), 1) }
        assertThrows(IllegalArgumentException::class.java) { indexer.scan(emptyList(), Snapshot.empty(MockMediaID().scheme).copy(generation = 5), 4) }
    }
}
