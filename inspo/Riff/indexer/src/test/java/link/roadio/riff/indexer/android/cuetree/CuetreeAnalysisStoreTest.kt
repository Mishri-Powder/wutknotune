package link.roadio.riff.indexer.android.cuetree

import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.util.zip.CRC32

class CuetreeAnalysisStoreTest {
    @Test fun cancellationBeforePointerCommitRetainsCurrentAndFallback() {
        val root = Files.createTempDirectory("analysis-cancel")
        try {
            val store = AndroidCuetreeAnalysisStore(root) { CRC32().apply { update(it) }.value }
            store.publish(AnalysisSnapshot(1, emptyList()))
            store.publish(AnalysisSnapshot(2, emptyList()))
            var checks = 0
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                store.publish(AnalysisSnapshot(3, emptyList()), link.roadio.cuetree.Cancellation { ++checks >= 2 })
            }
            assertEquals(2L, store.read()?.generation)
            val current = root.resolve(String(Files.readAllBytes(root.resolve("CURRENT")), Charsets.UTF_8).trim())
            Files.write(current, ByteArray(12))
            assertEquals(1L, store.read()?.generation)
            assertEquals(4L, store.nextGeneration())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun previousPointerIsDurableBeforeCurrentSwitch() {
        val root = Files.createTempDirectory("analysis-commit-order")
        try {
            val digest: (ByteArray) -> Long = { CRC32().apply { update(it) }.value }
            AndroidCuetreeAnalysisStore(root, hash = digest).publish(AnalysisSnapshot(1, emptyList()))
            val original = String(Files.readAllBytes(root.resolve("CURRENT")))
            val interrupted =
                AndroidCuetreeAnalysisStore(
                    root,
                    beforeCurrentSwitch = {
                        assertEquals(original, String(Files.readAllBytes(root.resolve("CURRENT"))))
                        assertEquals(original, String(Files.readAllBytes(root.resolve("PREVIOUS"))))
                        throw IOException("simulated process death before CURRENT switch")
                    },
                    hash = digest,
                )
            assertThrows(IOException::class.java) { interrupted.publish(AnalysisSnapshot(2, emptyList())) }
            assertEquals(original, String(Files.readAllBytes(root.resolve("CURRENT"))))
            assertEquals(1L, AndroidCuetreeAnalysisStore(root, hash = digest).read()?.generation)
            assertEquals(3L, AndroidCuetreeAnalysisStore(root, hash = digest).nextGeneration())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun cancellationAtTheSwitchBoundaryLeavesTheAcceptedFileCurrent() {
        val root = Files.createTempDirectory("analysis-cancel-switch")
        try {
            val digest: (ByteArray) -> Long = { CRC32().apply { update(it) }.value }
            AndroidCuetreeAnalysisStore(root, hash = digest).publish(AnalysisSnapshot(1, emptyList()))
            var cancelled = false
            val store = AndroidCuetreeAnalysisStore(root, beforeCurrentSwitch = { cancelled = true }, hash = digest)
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                store.publish(AnalysisSnapshot(2, emptyList()), link.roadio.cuetree.Cancellation { cancelled })
            }
            assertEquals(1L, store.read()?.generation)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun reopeningPrunesUncommittedPayloadSidecarAndStagingPointer() {
        val root = Files.createTempDirectory("analysis-staging-recovery")
        try {
            val digest: (ByteArray) -> Long = { CRC32().apply { update(it) }.value }
            AndroidCuetreeAnalysisStore(root, hash = digest).publish(AnalysisSnapshot(1, emptyList()))
            Files.write(root.resolve("unrelated.txt"), "keep".toByteArray())
            val interrupted =
                AndroidCuetreeAnalysisStore(
                    root,
                    beforeCurrentSwitch = { throw IOException("interrupted") },
                    hash = digest,
                )
            assertThrows(IOException::class.java) { interrupted.publish(AnalysisSnapshot(2, emptyList())) }
            assertEquals(1L, AndroidCuetreeAnalysisStore(root, hash = digest).read()?.generation)
            val current = String(Files.readAllBytes(root.resolve("CURRENT")), Charsets.UTF_8).trim()
            assertEquals(
                setOf("CURRENT", "PREVIOUS", "LAST_GENERATION", current, "$current.xxh3", "unrelated.txt"),
                Files.list(root).use { paths ->
                    paths
                        .iterator()
                        .asSequence()
                        .map { it.fileName.toString() }
                        .toSet()
                },
            )
            assertEquals("keep", String(Files.readAllBytes(root.resolve("unrelated.txt")), Charsets.UTF_8))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun corruptCurrentFallsBackToCommittedPreviousAndKeepsGeneration() {
        val root = Files.createTempDirectory("analysis-recovery")
        try {
            val store = AndroidCuetreeAnalysisStore(root) { CRC32().apply { update(it) }.value }
            store.publish(AnalysisSnapshot(1, emptyList()))
            store.publish(AnalysisSnapshot(2, emptyList()))
            val current = root.resolve(String(Files.readAllBytes(root.resolve("CURRENT")), Charsets.UTF_8).trim())
            Files.write(current, ByteArray(12))
            assertEquals(1L, store.read()?.generation)
            assertEquals(3L, store.nextGeneration())
            store.publish(AnalysisSnapshot(3, emptyList()))
            assertEquals(3L, store.read()?.generation)
            assertEquals(2L, Files.list(root).use { paths -> paths.filter { it.toString().endsWith(".mdrg") }.count() })
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun uncommittedCompleteFileCannotBecomeCurrent() {
        val root = Files.createTempDirectory("analysis-uncommitted")
        try {
            val store = AndroidCuetreeAnalysisStore(root) { CRC32().apply { update(it) }.value }
            store.publish(AnalysisSnapshot(1, emptyList()))
            Files.delete(root.resolve("CURRENT"))
            assertNull(store.read())
            assertEquals(2L, store.nextGeneration())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun explicitResetKeepsMonotonicWatermark() {
        val root = Files.createTempDirectory("analysis-reset")
        try {
            val store = AndroidCuetreeAnalysisStore(root) { CRC32().apply { update(it) }.value }
            store.publish(AnalysisSnapshot(99, emptyList()))
            store.rememberGeneration()
            Files.list(root).use { paths -> paths.filter { it.fileName.toString() != "LAST_GENERATION" }.forEach { Files.delete(it) } }
            assertNull(store.read())
            assertEquals(100L, store.nextGeneration())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
