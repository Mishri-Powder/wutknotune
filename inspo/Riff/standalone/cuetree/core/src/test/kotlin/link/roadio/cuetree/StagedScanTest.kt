// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class StagedScanTest {
    @Test fun everyDirectoryIsEnumeratedBeforeTheFirstMetadataOrIdentityRead() {
        val provider = MemoryProvider()
        val a = provider.directory("a")
        val z = provider.directory("z")
        provider.file("first.mp3", byteArrayOf(1), a)
        provider.file("last.mp3", byteArrayOf(2), z)
        val expected = setOf(provider.rootHandle, a, z).map { "children:" + it.token }.toSet()

        fun assertDiscoveryComplete() {
            assertTrue("All L0 listings must precede every L1 read: " + provider.calls, provider.calls.containsAll(expected))
        }
        provider.onRead = ::assertDiscoveryComplete
        val reader =
            MetadataReader { _, _, _ ->
                assertDiscoveryComplete()
                Observation(emptyMap(), emptyList(), emptyList())
            }
        val result = Indexer(MockMediaID(), 100, listOf(provider), reader).scan(listOf(provider.rootSpec()), generation = 1)
        assertEquals(2, result.nodes.count { it.kind == NodeKind.MEDIA })
        assertEquals(provider.opened, provider.closed)
        assertEquals(3, provider.calls.count { it.startsWith("children:") })
    }

    @Test fun l0CheckpointRetainsUnvisitedFactsAndCannotPublishProvisionalMedia() {
        val provider = MemoryProvider()
        val a = provider.directory("a")
        val z = provider.directory("z")
        provider.file("first.mp3", byteArrayOf(1), a)
        val last = provider.file("last.mp3", byteArrayOf(2), z)
        val indexer = Indexer(MockMediaID(), 100, listOf(provider), metadataByName(mapOf("last.mp3" to mapOf("title" to listOf("retained")))))
        val prior = indexer.scan(listOf(provider.rootSpec()), generation = 1, options = ScanOptions(metadataRevision = "r1"))
        val old = prior.nodes.single { it.name == "last.mp3" }
        provider.items.remove(last)
        provider.file("new.mp3", byteArrayOf(3), a)
        var firstDirectory: Snapshot? = null
        indexer.scan(listOf(provider.rootSpec()), prior, 2, ScanOptions(metadataRevision = "r1"), onCheckpoint = { p, offer ->
            if (p.kind == ScanCheckpointKind.DIRECTORY && firstDirectory == null) firstDirectory = offer()
            false
        })
        val interim = requireNotNull(firstDirectory)
        val retained = interim.nodes.single { it.name == "last.mp3" }
        assertEquals(old.id, retained.id)
        assertEquals(old.attributes, retained.attributes)
        assertTrue(retained.life.available)
        assertFalse(interim.nodes.any { it.kind == NodeKind.MEDIA && it.name == "new.mp3" })
        validate(interim)
    }

    @Test fun cancellationRestartsFromCommittedFactsForChangedSources() {
        val provider = MemoryProvider()
        val a = provider.file("a.mp3", byteArrayOf(1))
        val b = provider.file("b.mp3", byteArrayOf(2))
        val reads = mutableListOf<String>()
        val reader =
            MetadataReader { input, _, _ ->
                reads += input.id
                Observation(mapOf("title" to listOf("read-" + input.id)), emptyList(), emptyList())
            }
        val indexer = Indexer(MockMediaID(), 100, listOf(provider), reader)
        val prior = indexer.scan(listOf(provider.rootSpec()), generation = 1, options = ScanOptions(metadataRevision = "r1", pipelineDepth = 1))
        provider.items.getValue(a).modified++
        provider.items.getValue(b).modified++
        reads.clear()
        var cancelled = false
        var partial: Snapshot? = null
        try {
            indexer.scan(
                listOf(provider.rootSpec()),
                prior,
                2,
                ScanOptions(metadataRevision = "r1", pipelineDepth = 1),
                cancellation = Cancellation { cancelled },
                onCheckpoint = { progress, offer ->
                    if (progress.kind == ScanCheckpointKind.MEDIA && progress.completedMedia == 1L) {
                        partial = offer()
                        cancelled = true
                    }
                    false
                },
            )
            fail("Cancellation must stop the pass")
        } catch (_: CancellationException) {
        }
        val committed = FlatCatalogs.encode(requireNotNull(partial)).snapshot()
        val firstRead = reads.single()
        reads.clear()
        val completed = indexer.scan(listOf(provider.rootSpec()), committed, committed.generation + 1, ScanOptions(metadataRevision = "r1", pipelineDepth = 1))
        assertEquals(1, reads.size)
        assertNotEquals(firstRead, reads.single())
        assertEquals(2, completed.nodes.count { it.kind == NodeKind.MEDIA && it.playable })
    }

    @Test fun knownBaseIdentityDerivesAllFragmentsWithoutSourceReopens() {
        val provider = MemoryProvider()
        provider.file("image.mp3", byteArrayOf(1))
        val id =
            object : MediaIdGenerator {
                override val scheme = "test.derived"

                override fun high(
                    input: link.roadio.cuegrain.MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ): Long {
                    assertNull(span)
                    return 1
                }

                override fun low(
                    input: link.roadio.cuegrain.MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ): Long = error("No collision")

                override fun fragmentComponent(
                    contentComponent: Long,
                    span: Span,
                    low: Boolean,
                    cancellation: Cancellation,
                ): Long = contentComponent + (span.beginUs ?: 0) + 10
            }
        val reader =
            MetadataReader { _, _, _ ->
                Observation(
                    emptyMap(),
                    listOf(
                        TableObservation(
                            "chap",
                            emptyMap(),
                            listOf(
                                Section(null, Span(0, 10), emptyMap()),
                                Section(null, Span(10, null), emptyMap()),
                            ),
                        ),
                    ),
                    emptyList(),
                )
            }
        val result = Indexer(id, 100, listOf(provider), reader).scan(listOf(provider.rootSpec()), generation = 1)
        assertEquals(2, result.nodes.count { it.kind == NodeKind.FRAGMENT })
        assertEquals(1, provider.opened)
        val before = provider.opened
        Indexer(id, 100, listOf(provider), reader).scan(listOf(provider.rootSpec()), result, 2)
        assertEquals(before, provider.opened)
    }
}
