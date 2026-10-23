// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AlbumGroupingPolicyTest {
    @Test fun injectedPolicyOnlyReceivesSourcesRemainingAfterExplicitAlbums() {
        val provider = MemoryProvider()
        provider.file("disc.wav", ByteArray(32) { it.toByte() })
        provider.file(
            "disc.cue",
            """
            FILE "disc.wav" WAVE
              TRACK 01 AUDIO
                TITLE "First"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Second"
                INDEX 01 00:10:00
            """.trimIndent().toByteArray(),
        )
        provider.file("chosen.mp3")
        provider.file("leftover.mp3")
        val seen = mutableListOf<List<String>>()
        val policy =
            AlbumGroupingPolicy { input ->
                val names = input.eligibleSources.map { input.snapshot.nodes[it].name }
                seen += names
                val chosen = input.eligibleSources.single { input.snapshot.nodes[it].name == "chosen.mp3" }
                listOf(OrdinaryAlbumGroup(listOf(chosen), "Selected", mapOf("album" to listOf("Selected"))))
            }

        val snapshot = Indexer(MockMediaID(), 100, listOf(provider), grouping = policy).scan(listOf(provider.rootSpec()), generation = 1)

        val explicit = snapshot.albums.single { it.kind == AlbumKind.EXPLICIT }
        val ordinary = snapshot.albums.single { it.kind == AlbumKind.ORDINARY }
        val singles = snapshot.albums.single { it.kind == AlbumKind.SINGLES }
        assertEquals(setOf("chosen.mp3", "leftover.mp3"), seen.single().toSet())
        assertEquals(listOf("First", "Second"), explicit.sources.map { snapshot.nodes[it].name })
        assertEquals(listOf("chosen.mp3"), ordinary.sources.map { snapshot.nodes[it].name })
        assertEquals("Selected", ordinary.title)
        assertEquals(listOf("leftover.mp3"), singles.sources.map { snapshot.nodes[it].name })
    }

    @Test fun invalidPolicyCannotPublishOverPriorSnapshot() {
        val provider = MemoryProvider()
        provider.file("a.mp3")
        val first = Indexer(MockMediaID(), 100, listOf(provider)).scan(listOf(provider.rootSpec()), generation = 1)
        val invalid =
            AlbumGroupingPolicy { input ->
                val source = input.eligibleSources.single()
                listOf(OrdinaryAlbumGroup(listOf(source, source), "Duplicate", emptyMap()))
            }

        try {
            Indexer(MockMediaID(), 100, listOf(provider), grouping = invalid).scan(listOf(provider.rootSpec()), first, 2)
            fail("Expected invalid policy result to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("assigned more than once"))
        }
        assertEquals(1L, first.generation)
        assertEquals(listOf(AlbumKind.SINGLES), first.albums.map { it.kind })
    }
}
