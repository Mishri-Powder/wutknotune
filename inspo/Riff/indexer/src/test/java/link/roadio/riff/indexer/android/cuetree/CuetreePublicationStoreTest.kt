package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.Flags
import link.roadio.cuetree.Life
import link.roadio.cuetree.MediaId
import link.roadio.cuetree.Node
import link.roadio.cuetree.NodeKind
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.SnapshotStore
import link.roadio.cuetree.SourceInfo
import link.roadio.cuetree.withCompleteAttributeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CancellationException

class CuetreePublicationStoreTest {
    @Test fun indexResetRetainsGenerationWatermark() {
        val root = Files.createTempDirectory("cuetree-rebuild-generation")
        try {
            val store = CuetreePublicationStore(root, 3)
            store.publish(Snapshot(99, "test", emptyList()), Cancellation()) { it }
            store.rememberGeneration()
            root
                .toFile()
                .listFiles()
                .orEmpty()
                .filter { it.name != "LAST_GENERATION" }
                .forEach { it.deleteRecursively() }
            assertEquals(100L, CuetreePublicationStore(root, 3).nextGeneration())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun restartServesExistingProjectionBeforeAnyChangedProjection() {
        val root = Files.createTempDirectory("cuetree-publication")
        try {
            val original = Snapshot(4, "test", emptyList())
            val store = CuetreePublicationStore(root, 3)
            store.publish(original, Cancellation()) { it }
            val restarted = CuetreePublicationStore(root, 3)
            restarted.initialize { error("Must not replace the committed projection on startup") }
            assertEquals(original, restarted.readPlayer()?.snapshot())
            assertFalse(restarted.refresh { it })
            assertEquals(4, restarted.read()?.generation?.toInt())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun staleFactualRevisionCannotCreateAPlayerProjectionOnStartup() {
        val root = Files.createTempDirectory("cuetree-stale-projection")
        try {
            val stale = Snapshot(4, "test", emptyList(), metadataRevision = "old")
            SnapshotStore(root).write(stale)
            val store = CuetreePublicationStore(root, 3)
            store.initialize(eligible = { it.metadataRevision == "current" }) {
                error("Stale metadata cannot be projected")
            }
            assertEquals(stale, store.read())
            assertEquals(null, store.readPlayer())
            assertEquals(5L, store.nextGeneration())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun failedOrCancelledProjectionKeepsPriorCohortAndFactualInput() {
        val root = Files.createTempDirectory("cuetree-publication-failure")
        try {
            val original = Snapshot(4, "test", emptyList())
            val store = CuetreePublicationStore(root, 3)
            store.publish(original, Cancellation()) { it }
            assertThrows(IllegalArgumentException::class.java) {
                store.publish(original.copy(generation = 5), Cancellation()) { it.copy(identityScheme = "") }
            }
            assertEquals(original, store.read())
            assertEquals(original, store.readPlayer()?.snapshot())
            assertThrows(CancellationException::class.java) {
                store.publish(original.copy(generation = 5), Cancellation { true }) { it }
            }
            assertEquals(original, store.readPlayer()?.snapshot())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun repeatedAnalysisRefreshKeepsTwoCohortsAndProjectsOnce() {
        val root = Files.createTempDirectory("cuetree-analysis-retention")
        try {
            val original = Snapshot(1, "test", emptyList())
            val store = CuetreePublicationStore(root, 3)
            store.publish(original, Cancellation()) { it }
            for (iteration in 1..6) {
                var calls = 0
                assertTrue(
                    store.refresh { snapshot ->
                        calls++
                        snapshot.copy(
                            nodes =
                                listOf(
                                    Node(
                                        NodeKind.DIRECTORY,
                                        MediaId(100L + iteration),
                                        source = SourceInfo("Music", provider = "saf", locator = "content://music"),
                                        life = Life(snapshot.generation),
                                    ),
                                ),
                        )
                    },
                )
                assertEquals(1, calls)
            }
            for (directory in listOf(root, root.resolve("player"))) {
                val names =
                    Files.list(directory).use { stream ->
                        stream
                            .filter {
                                Files.isDirectory(it) && it.fileName.toString().matches(Regex("[0-9a-f-]{36}"))
                            }.map { it.fileName.toString() }
                            .toList()
                            .toSet()
                    }
                assertEquals(
                    setOf(
                        Files.readAllBytes(directory.resolve("CURRENT")).toString(Charsets.UTF_8).trim(),
                        Files.readAllBytes(directory.resolve("PREVIOUS")).toString(Charsets.UTF_8).trim(),
                    ),
                    names,
                )
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun changedProjectionAgesTtlAndDoesNotRepublishForOrderingOrLifeOnly() {
        val root = Files.createTempDirectory("cuetree-publication-ttl")
        try {
            val folder = MediaId(1)
            val original =
                Snapshot(
                    4,
                    "test",
                    listOf(
                        Node(
                            NodeKind.DIRECTORY,
                            folder,
                            source = SourceInfo("Music", provider = "saf", locator = "content://music"),
                            life = Life(4),
                        ),
                        Node(NodeKind.MEDIA, MediaId(2), folder, SourceInfo("lost.flac"), life = Life(2, Flags.ACSS)),
                    ),
                )
            val store = CuetreePublicationStore(root, 3)
            store.publish(original, Cancellation()) { it }

            fun project(snapshot: Snapshot) =
                snapshot
                    .copy(
                        nodes =
                            snapshot.nodes.map { node ->
                                node.copy(attributes = node.attributes + ("newProjection" to listOf("yes")))
                            },
                    ).withCompleteAttributeCatalog()
            assertTrue(store.refresh(project = ::project))
            assertEquals(5L, store.read()?.generation)
            assertEquals(1, store.read()?.nodes?.size)
            assertEquals(
                4L,
                store
                    .read()
                    ?.nodes
                    ?.single()
                    ?.life
                    ?.generation,
            )
            assertFalse(store.refresh(project = ::project))
            assertEquals(5L, store.readPlayer()?.snapshot()?.generation)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
