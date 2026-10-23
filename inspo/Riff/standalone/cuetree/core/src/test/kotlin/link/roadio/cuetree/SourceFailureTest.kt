// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class SourceFailureTest {
    @Test fun failedFirstAcquisitionHasOnlyTechnicalIdentityAndIsSkippedUntilStatChanges() {
        val provider = MemoryProvider()
        val file = provider.file("unsupported.mp3")
        var fail = true
        var failedReports = 0
        val guard =
            object : AcquisitionGuard {
                override fun failed(source: SourceAcquisition) {
                    failedReports++
                }
            }
        val reader =
            MetadataReader { _, _, _ ->
                if (fail) throw IOException("unsupported")
                Observation(mapOf("title" to listOf("Recovered")), emptyList(), emptyList())
            }
        val indexer = Indexer(CountingIdentity(), 3, listOf(provider), reader)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1, acquisitionGuard = guard)
        val failed = first.nodes.single { it.name == "unsupported.mp3" }
        assertEquals(NodeKind.FAILEDL1, failed.kind)
        assertNull(failed.mediaId)
        assertFalse(failed.playable)
        assertEquals(1, provider.opened)
        assertEquals(1, failedReports)
        val second = indexer.scan(listOf(provider.rootSpec()), first, 2, acquisitionGuard = guard)
        assertEquals(1, provider.opened)
        assertEquals(1, failedReports)
        assertEquals(
            2,
            second.nodes
                .single { it.name == "unsupported.mp3" }
                .life.generation,
        )
        assertEquals(second.copy(diagnostics = emptyList()), FlatCatalogs.encode(second).snapshot())
        fail = false
        provider.items.getValue(file).modified++
        val third = indexer.scan(listOf(provider.rootSpec()), second, 3, acquisitionGuard = guard)
        assertEquals(2, provider.opened)
        assertEquals(NodeKind.MEDIA, third.nodes.single { it.name == "unsupported.mp3" }.kind)
        assertEquals(
            "Recovered",
            third.nodes
                .single { it.name == "unsupported.mp3" }
                .attributes
                .first("title"),
        )
    }

    @Test fun failedRefreshRetainsHistoricalFactsWithoutPlayback() {
        val provider = MemoryProvider()
        val file = provider.file("book.mp3")
        var fail = false
        val reader =
            MetadataReader { _, _, _ ->
                if (fail) throw IOException("cannot read")
                Observation(mapOf("title" to listOf("Known")), emptyList(), emptyList())
            }
        val indexer = Indexer(CountingIdentity(), 3, listOf(provider), reader)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        val old = first.nodes.single { it.kind == NodeKind.MEDIA }
        fail = true
        provider.items.getValue(file).modified++
        val second = indexer.scan(listOf(provider.rootSpec()), first, 2)
        val failed = second.nodes.single { it.name == "book.mp3" }
        assertEquals(NodeKind.FAILEDL1, failed.kind)
        assertEquals(old.mediaId, failed.mediaId)
        assertEquals(old.attributes, failed.attributes)
        assertFalse(failed.playable)
        assertFalse(second.available(requireNotNull(old.mediaId)))
        assertTrue(second.albums.any { album -> album.sources.any { second.nodes[it].kind == NodeKind.FAILEDL1 } })
        assertEquals(second.copy(diagnostics = emptyList()), FlatCatalogs.encode(second).snapshot())
        val third = indexer.scan(listOf(provider.rootSpec()), second, 3)
        assertEquals(2, provider.opened)
        assertEquals(
            3,
            third.nodes
                .single { it.name == "book.mp3" }
                .life.generation,
        )
    }

    @Test fun quarantineCreatesFailedBindingWithoutOpeningSource() {
        val provider = MemoryProvider()
        provider.file("crash.mp3")
        val guard =
            object : AcquisitionGuard {
                override fun mode(source: SourceAcquisition) = AcquisitionMode.QUARANTINE
            }
        val indexer = Indexer(CountingIdentity(), 3, listOf(provider), emptyMetadata)
        val snapshot = indexer.scan(listOf(provider.rootSpec()), generation = 1, acquisitionGuard = guard)
        assertEquals(0, provider.opened)
        assertEquals(NodeKind.FAILEDL1, snapshot.nodes.single { it.name == "crash.mp3" }.kind)
    }

    @Test fun cancellationNeverCommitsFailedSource() {
        val provider = MemoryProvider()
        val file = provider.file("book.mp3")
        var cancel = false
        val reader =
            MetadataReader { _, _, _ ->
                if (cancel) throw CancellationException("cancelled")
                Observation(emptyMap(), emptyList(), emptyList())
            }
        val indexer = Indexer(CountingIdentity(), 3, listOf(provider), reader)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        provider.items.getValue(file).modified++
        cancel = true
        assertThrows(CancellationException::class.java) {
            indexer.scan(listOf(provider.rootSpec()), first, 2)
        }
        assertEquals(NodeKind.MEDIA, first.nodes.single { it.name == "book.mp3" }.kind)
    }

    @Test fun failedL2BitRoundTripsAndClearsOnSourceChange() {
        val provider = MemoryProvider()
        val file = provider.file("song.mp3")
        val indexer = Indexer(CountingIdentity(), 3, listOf(provider), emptyMetadata)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        val flagged =
            first.copy(
                nodes =
                    first.nodes.map {
                        if (it.kind == NodeKind.MEDIA) it.copy(identityFlags = it.identityFlags or Flags.FAILEDL2) else it
                    },
            )
        validate(flagged)
        assertEquals(flagged, FlatCatalogs.encode(flagged).snapshot())
        val unchanged = indexer.scan(listOf(provider.rootSpec()), flagged, 2)
        assertTrue(unchanged.nodes.single { it.name == "song.mp3" }.identityFlags and Flags.FAILEDL2 != 0)
        provider.items.getValue(file).modified++
        val changed = indexer.scan(listOf(provider.rootSpec()), unchanged, 3)
        assertEquals(0, changed.nodes.single { it.name == "song.mp3" }.identityFlags and Flags.FAILEDL2)
    }
}
