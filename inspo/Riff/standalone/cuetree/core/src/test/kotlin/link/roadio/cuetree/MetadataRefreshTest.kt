// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.MediaInput
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataRefreshTest {
    @Test fun unchangedMediaReusesMetadataWithoutOpeningTheSource() {
        val provider = MemoryProvider()
        val handle = provider.file("book.m4b", byteArrayOf(1, 2, 3))
        var hashes = 0
        var observations = 0
        val identity =
            object : MediaIdGenerator {
                override val scheme = "metadata-refresh-test"

                override fun high(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ): Long {
                    hashes++
                    return 10
                }

                override fun low(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ): Long = 20
            }
        val metadata =
            MetadataReader { _, _, _ ->
                observations++
                Observation(
                    mapOf("title" to listOf("Pass $observations")),
                    emptyList(),
                    emptyList(),
                    sourceEvidence = mapOf("opaqueHint" to listOf("0")),
                )
            }
        val indexer = Indexer(identity, 3, listOf(provider), metadata)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        val opened = provider.opened
        assertEquals(
            mapOf("opaqueHint" to listOf("0")),
            FlatCatalogs
                .encode(first)
                .snapshot()
                .nodes
                .single { it.kind == NodeKind.MEDIA }
                .sourceEvidence,
        )
        val unchanged = indexer.scan(listOf(provider.rootSpec()), first, generation = 2)
        assertEquals(opened, provider.opened)
        assertEquals(1, hashes)
        assertEquals(1, observations)
        assertEquals(listOf("Pass 1"), unchanged.nodes.single { it.kind == NodeKind.MEDIA }.attributes["title"])
        provider.items.getValue(handle).modified++
        val changed = indexer.scan(listOf(provider.rootSpec()), unchanged, generation = 3)
        assertEquals(2, hashes)
        assertEquals(2, observations)
        assertEquals(listOf("Pass 2"), changed.nodes.single { it.kind == NodeKind.MEDIA }.attributes["title"])
    }
}
