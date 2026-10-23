// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import com.dynatrace.hash4j.hashing.Hashing
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException

class PersistenceTest {
    private fun fixture(): Snapshot {
        val p = MemoryProvider()
        p.file("a.mp3")
        p.file("b.mp3")
        return Indexer(
            CountingIdentity().also { it.fixedLow = 0 },
            1000,
            listOf(p),
            metadataByName(mapOf("a.mp3" to mapOf("artist" to listOf("Artist"), "genre" to listOf("Rock")))),
        ).scan(listOf(p.rootSpec()), generation = 1)
    }

    @Test fun separateFlatBuffersRoundTripAndReadyChildLists() {
        val snapshot = fixture()
        val buffers = FlatCatalogs.encode(snapshot)
        val decoded = buffers.snapshot()
        assertEquals(snapshot, decoded)
        assertEquals(3, buffers.mdia().nodesLength())
        assertEquals(2, buffers.mdia().mediaByKey(123).sourcesLength())
        assertNull(buffers.mdia().mediaByKey(999))
        assertEquals(1, buffers.albm().albumsLength())
        assertEquals(1, buffers.arts().artistsLength())
        assertEquals(1, buffers.gnrv().genresLength())
        assertTrue(buffers.buffer("MDIA").isReadOnly)
        assertEquals(
            0L,
            decoded.nodes
                .first { it.kind == NodeKind.MEDIA }
                .mediaId!!
                .lo,
        )
    }

    @Test fun mixedGenerationsAndTruncationAreRejected() {
        val snapshot = fixture()
        val a = FlatCatalogs.encode(snapshot)
        val b = FlatCatalogs.encode(snapshot.copy(generation = 2))
        val files = FlatCatalogs.names.associateWith { a.bytes(it) }.toMutableMap()
        files["ARTS"] = b.bytes("ARTS")
        assertThrows(IOException::class.java) { FlatCatalogs.read(files) }
        files["ARTS"] = a.bytes("ARTS").copyOf(10)
        assertThrows(IOException::class.java) { FlatCatalogs.read(files) }
    }

    @Test fun storePublicationSurvivesCancelledWriteAndFallsBackToPrevious() {
        val directory = Files.createTempDirectory("cuetree-store")
        try {
            val store = SnapshotStore(directory)
            val first = fixture()
            store.write(first)
            assertEquals(first, store.read())
            var checks = 0
            assertThrows(CancellationException::class.java) { store.write(first.copy(generation = 2), Cancellation { ++checks == 4 }) }
            assertEquals(first, store.read())
            store.write(first.copy(generation = 2))
            assertEquals(2L, store.read()!!.generation)
            val current = Files.readString(directory.resolve("CURRENT")).trim()
            val file = directory.resolve(current).resolve("MDIA")
            val bytes = Files.readAllBytes(file)
            bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
            Files.write(file, bytes)
            assertEquals(first, store.read())
            assertEquals(first, SnapshotStore(directory).read())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun cancellationAfterPreviousIsDurableCannotSwitchCurrent() {
        val directory = Files.createTempDirectory("cuetree-cancel-commit")
        try {
            val store = SnapshotStore(directory)
            val first = fixture()
            store.write(first)
            assertThrows(CancellationException::class.java) {
                store.write(first.copy(generation = 2), Cancellation { Files.exists(directory.resolve("PREVIOUS")) })
            }
            assertEquals(first, store.read())
            assertEquals(first, SnapshotStore(directory).read())
            assertEquals(1L, Files.list(directory).use { paths -> paths.filter { Files.isDirectory(it) }.count() })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun cancellationAfterCurrentCandidateIsDurableCannotSwitchCurrent() {
        val directory = Files.createTempDirectory("cuetree-cancel-current-candidate")
        try {
            val store = SnapshotStore(directory)
            val first = fixture()
            store.write(first)
            val cancel =
                Cancellation {
                    Files.newDirectoryStream(directory, "CURRENT.*").use { it.iterator().hasNext() }
                }
            assertThrows(CancellationException::class.java) { store.write(first.copy(generation = 2), cancel) }
            assertEquals(first, store.read())
            assertEquals(first, SnapshotStore(directory).read())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun manifestUsesXxh3AndRejectsOldDigestShape() {
        val directory = Files.createTempDirectory("cuetree-xxh3")
        try {
            val cohort = SnapshotStore(directory).write(fixture())
            val manifest = directory.resolve(cohort).resolve("MANIFEST")
            val rows = Files.readAllLines(manifest)
            assertEquals(FlatCatalogs.names.toSet(), rows.map { it.substringBefore(' ') }.toSet())
            for (row in rows) {
                val (name, size, digest) = row.split(' ')
                val bytes = Files.readAllBytes(directory.resolve(cohort).resolve(name))
                assertEquals(bytes.size.toString(), size)
                assertTrue(digest.matches(Regex("[0-9a-f]{16}")))
                val actualHash = Hashing.xxh3_64().hashBytesToLong(bytes)
                val unsignedHex = java.lang.Long.toUnsignedString(actualHash, 16)
                val expected = unsignedHex.padStart(16, '0')
                assertEquals(expected, digest)
            }
            val malformed =
                rows.joinToString("\n") { row ->
                    if (row.startsWith("MDIA ")) row.substringBeforeLast(' ') + " " + "0".repeat(64) else row
                } + "\n"
            Files.writeString(manifest, malformed)
            assertThrows(IOException::class.java) { SnapshotStore(directory).read() }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun onlyCurrentAndPreviousCohortsRemainAfterRepeatedPublications() {
        val directory = Files.createTempDirectory("cuetree-retention")
        try {
            val original = fixture()
            for (generation in 1L..8L) SnapshotStore(directory).write(original.copy(generation = generation))
            val current = Files.readString(directory.resolve("CURRENT")).trim()
            val previous = Files.readString(directory.resolve("PREVIOUS")).trim()
            val names =
                Files.list(directory).use { entries ->
                    entries
                        .filter { Files.isDirectory(it) }
                        .map { it.fileName.toString() }
                        .toList()
                        .toSet()
                }
            assertEquals(setOf(current, previous), names)
            assertEquals(8L, SnapshotStore(directory).read()!!.generation)
            assertEquals(7L, FlatCatalogs.read(FlatCatalogs.names.associateWith { Files.readAllBytes(directory.resolve(previous).resolve(it)) }).snapshot().generation)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun encodedPublicationRejectsMismatchedSnapshotBeforeCommit() {
        val directory = Files.createTempDirectory("cuetree-encoded-gate")
        try {
            val original = fixture()
            val flat = FlatCatalogs.encode(original)
            val store = SnapshotStore(directory)
            assertThrows(IllegalArgumentException::class.java) {
                store.writeFlat(flat, original.copy(generation = 2))
            }
            assertFalse(Files.exists(directory.resolve("CURRENT")))
            store.writeFlat(flat, original)
            assertEquals(original, store.read())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun staleWritesAndSemanticParentCyclesAreRejected() {
        val dir = Files.createTempDirectory("cuetree-store")
        try {
            val store = SnapshotStore(dir)
            val first = fixture()
            store.write(first.copy(generation = 2))
            assertThrows(IllegalArgumentException::class.java) { store.write(first) }
            val bad = first.copy(nodes = first.nodes.mapIndexed { i, n -> if (i == 1) n.copy(parentId = n.id) else n })
            assertThrows(IllegalArgumentException::class.java) { FlatCatalogs.encode(bad) }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test fun mediaIndexFindsZeroAndBothSignedBoundaries() {
        val highs = listOf(0L, Long.MIN_VALUE, -1L, Long.MAX_VALUE)
        val p = MemoryProvider()
        highs.indices.forEach { p.file("$it.mp3", byteArrayOf(it.toByte())) }
        val generator =
            object : MediaIdGenerator {
                override val scheme = "test.high-boundaries"

                override fun high(
                    input: link.roadio.cuegrain.MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = highs[input.read(0, 1)[0].toInt()]

                override fun low(
                    input: link.roadio.cuegrain.MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ): Long = error("No collision")
            }
        val snapshot = Indexer(generator, 1000, listOf(p), emptyMetadata).scan(listOf(p.rootSpec()), generation = 1)
        val flat = FlatCatalogs.encode(snapshot)
        for (hi in highs) assertEquals(hi, requireNotNull(flat.mdia().mediaByKey(hi)).hi())
        assertEquals(snapshot, flat.snapshot())
    }
}
