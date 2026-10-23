package link.roadio.cuetree

import link.roadio.cuegrain.MediaInput
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class AcquisitionRegressionTest {
    @Test fun collisionTailReadsHaveAnActiveSourceAttempt() {
        val provider = MemoryProvider()
        provider.file("a.mp3")
        provider.file("b.mp3")
        val active = AtomicInteger()
        val guarded = mutableListOf<Boolean>()
        val identity =
            object : MediaIdGenerator {
                override val scheme = "audit"

                override fun high(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = 1L

                override fun low(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ): Long {
                    guarded += active.get() > 0
                    return input.id
                        .first()
                        .code
                        .toLong()
                }
            }
        val guard =
            object : AcquisitionGuard {
                override fun begin(
                    source: SourceAcquisition,
                    mode: AcquisitionMode,
                    cancellation: Cancellation,
                ) {
                    active.incrementAndGet()
                }

                override fun finish(
                    source: SourceAcquisition,
                    outcome: AcquisitionOutcome,
                ) {
                    active.decrementAndGet()
                }
            }
        Indexer(identity, 5, listOf(provider), emptyMetadata).scan(
            listOf(provider.rootSpec()),
            generation = 1,
            acquisitionGuard = guard,
        )
        println("AUDIT collision tail guarded = " + guarded)
        assertTrue("Every media read must be inside its source attempt", guarded.isNotEmpty() && guarded.all { it })
    }

    @Test fun failedCollisionTailProducesSuppressedPhysicalBinding() {
        val provider = MemoryProvider()
        provider.file("a.mp3")
        provider.file("b.mp3")
        val identity =
            object : MediaIdGenerator {
                override val scheme = "audit"

                override fun high(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = 1L

                override fun low(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ): Long {
                    if (input.id == "b.mp3") throw IOException("Synthetic tail read failure")
                    return 10L
                }
            }
        val indexer = Indexer(identity, 5, listOf(provider), emptyMetadata)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        val before = provider.opened
        val second = indexer.scan(listOf(provider.rootSpec()), first, 2)
        println(
            "AUDIT collision failure first=" + first.nodes.map { it.name to it.kind } +
                " second=" + second.nodes.map { it.name to it.kind } +
                " reopened=" + (provider.opened - before),
        )
        assertEquals(NodeKind.FAILEDL1, second.nodes.singleOrNull { it.name == "b.mp3" }?.kind)
        assertEquals("Unchanged failed source must not reopen", before, provider.opened)
    }

    @Test fun partialPositionCopiesCollapseWhenOnlyOneAssignmentExists() {
        val root = Node(NodeKind.DIRECTORY, MediaId(-1), source = SourceInfo("root", provider = "memory", locator = "/"), life = Life(1))
        val attrs = mapOf("album" to listOf("Same album"), "artist" to listOf("Band"), "title" to listOf("Same song"), "trackNumber" to listOf("1"))
        val known = Node(NodeKind.MEDIA, MediaId(10, 20), root.id, SourceInfo("a.mp3"), attributes = attrs + ("discNumber" to listOf("1")), life = Life(1))
        val partial = known.copy(source = SourceInfo("b.mp3"), attributes = attrs)
        val snapshot = Snapshot(1, "audit", listOf(root, known, partial))
        val groups = DefaultAlbumGroupingPolicy.group(AlbumGroupingInput(snapshot, listOf(1, 2), Cancellation()))
        println("AUDIT partial copies sources=" + groups.map { it.sources })
        assertEquals("One recording at one uniquely assignable position", 1, groups.single().sources.size)
    }

    @Test fun checkpointFailureSurvivesAndTailAcquisitionIsQuiescent() {
        val provider = MemoryProvider()
        listOf("a.mp3", "b.mp3", "c.mp3").forEach { provider.file(it) }
        val active = AtomicInteger()
        var failedTailReads = 0
        var failureReports = 0
        val identity =
            object : MediaIdGenerator {
                override val scheme = "test.checkpoint-failure"

                override fun high(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = 1L

                override fun low(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ): Long {
                    assertEquals("Checkpoint reads must not overlap preparation", 1, active.get())
                    if (input.id == "b.mp3") {
                        failedTailReads++
                        throw IOException("Synthetic tail failure")
                    }
                    return input.id
                        .first()
                        .code
                        .toLong()
                }
            }
        val guard =
            object : AcquisitionGuard {
                override fun begin(
                    source: SourceAcquisition,
                    mode: AcquisitionMode,
                    cancellation: Cancellation,
                ) {
                    active.incrementAndGet()
                }

                override fun finish(
                    source: SourceAcquisition,
                    outcome: AcquisitionOutcome,
                ) {
                    active.decrementAndGet()
                }

                override fun failed(source: SourceAcquisition) {
                    failureReports++
                }
            }
        val indexer = Indexer(identity, 100, listOf(provider), emptyMetadata)
        val first =
            indexer.scan(
                listOf(provider.rootSpec()),
                generation = 1,
                acquisitionGuard = guard,
                onCheckpoint = { progress, snapshot ->
                    if (progress.stage == ScanStage.DISCOVERY) assertEquals(0, provider.opened)
                    snapshot()
                    true
                },
            )
        assertEquals(NodeKind.FAILEDL1, first.nodes.single { it.name == "b.mp3" }.kind)
        assertNull(first.nodes.single { it.name == "b.mp3" }.mediaId)
        assertEquals(1, failureReports)
        assertEquals(1, failedTailReads)
        val before = provider.opened
        indexer.scan(listOf(provider.rootSpec()), first, first.generation + 1, acquisitionGuard = guard)
        assertEquals(before, provider.opened)
    }

    @Test fun journalFailureDuringCollisionAbortsInsteadOfCreatingFerr() {
        val provider = MemoryProvider()
        provider.file("a.mp3")
        provider.file("b.mp3")
        val begun = mutableMapOf<String, Int>()
        var failed = 0
        val guard =
            object : AcquisitionGuard {
                override fun begin(
                    source: SourceAcquisition,
                    mode: AcquisitionMode,
                    cancellation: Cancellation,
                ) {
                    val count = (begun[source.name] ?: 0) + 1
                    begun[source.name] = count
                    if (count > 1) throw IOException("Peer disconnected")
                }

                override fun failed(source: SourceAcquisition) {
                    failed++
                }
            }
        val identity =
            object : MediaIdGenerator {
                override val scheme = "test.journal-failure"

                override fun high(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = 1L

                override fun low(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = 2L
            }
        assertThrows(IllegalStateException::class.java) {
            Indexer(identity, 5, listOf(provider), emptyMetadata).scan(
                listOf(provider.rootSpec()),
                generation = 1,
                acquisitionGuard = guard,
            )
        }
        assertEquals(0, failed)
    }
}
