// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.assertEquals
import org.junit.Test

class RandomizedAcquisitionOrderTest {
    @Test fun repeatedFreshPassesRetainCatalogTrackOrder() {
        val provider = MemoryProvider()
        provider.file("second.mp3", byteArrayOf(2))
        provider.file("first.mp3", byteArrayOf(1))
        provider.file("third.mp3", byteArrayOf(3))
        val metadata =
            metadataByName(
                (1..3).associate { number ->
                    val name = listOf("first.mp3", "second.mp3", "third.mp3")[number - 1]
                    name to
                        mapOf(
                            "album" to listOf("Stable"),
                            "trackNumber" to listOf("$number/3"),
                            "title" to listOf(name),
                        )
                },
            )
        repeat(24) {
            val snapshot =
                Indexer(MockMediaID(), 3, listOf(provider), metadata).scan(
                    listOf(provider.rootSpec()),
                    generation = 1,
                )
            val album = snapshot.albums.single { it.kind == AlbumKind.ORDINARY && it.title == "Stable" }
            assertEquals(
                listOf("first.mp3", "second.mp3", "third.mp3"),
                album.sources.map { snapshot.nodes[it].name },
            )
        }
    }
}
