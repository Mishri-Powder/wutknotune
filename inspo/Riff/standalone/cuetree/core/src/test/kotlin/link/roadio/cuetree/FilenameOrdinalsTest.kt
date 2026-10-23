// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test

class FilenameOrdinalsTest {
    private val file = SourceStat(false, Stamp(1, 2))

    private fun size(vararg names: String) = FilenameOrdinals.listedSize(names.map { it to file }, true)

    @Test fun completeRegularAndItunesSequencesIncludeUnsupportedAudio() {
        assertEquals(2, size("01 One.mp3", "02 Two.wma", "cover.jpg"))
        assertEquals(2, size("0 01 One.m4a", "0 02 Two.m4a"))
        assertNull(size("0 01 One.m4a", "0 03 Three.m4a"))
        assertNull(size("01 One.mp3", "01 Copy.mp3"))
        assertNull(size("01 One.mp3", "02 Two.mp3", "album.cue"))
        assertNull(size("01 One.mp3", "02 Two.mp3", "opaque.blob"))
        assertNull(FilenameOrdinals.listedSize(listOf("01 One.mp3" to file, "02 Two.mp3" to file), false))
    }

    @Test fun inferredOrdinalsOrderButDoNotInventAuthoredTotalsOrRewriteMetadata() {
        val p = MemoryProvider()
        p.file("0 02 Two.mp3")
        p.file("0 01 One.mp3")
        val attrs = mapOf("album" to listOf("Release"), "artist" to listOf("Band"))
        val scan = Indexer(MockMediaID(), 10, listOf(p), MetadataReader { _, _, _ -> Observation(attrs, emptyList(), emptyList()) }).scan(listOf(p.rootSpec()), generation = 1)
        val album = scan.albums.single { it.kind == AlbumKind.ORDINARY }
        assertEquals(listOf("0 01 One.mp3", "0 02 Two.mp3"), album.sources.map { scan.nodes[it].name })
        assertTrue(album.sources.all { "trackNumber" !in scan.nodes[it].attributes && "trackTotal" !in scan.nodes[it].attributes })
        assertEquals(listOf(1, 2), album.sources.map { AlbumEvidence(scan, album.sources, Cancellation()).facts.getValue(it).track })
    }

    @Test fun factualConflictDisablesInferenceForTheWholeDirectory() {
        val p = MemoryProvider()
        p.file("01 One.mp3")
        p.file("02 Two.mp3")
        val scan =
            Indexer(
                MockMediaID(),
                10,
                listOf(p),
                MetadataReader { input, _, _ ->
                    Observation(mapOf("album" to listOf("Release"), "artist" to listOf("Band")) + if (input.id.startsWith("01")) mapOf("trackNumber" to listOf("7")) else emptyMap(), emptyList(), emptyList())
                },
            ).scan(listOf(p.rootSpec()), generation = 1)
        assertTrue(FilenameOrdinals.derive(scan).isEmpty())
    }

    @Test fun supportedMp4NamesParticipateInCompleteListingEvidence() {
        assertEquals(2, size("01 One.mp4", "02 Two.mp4"))
    }

    @Test fun invalidDeclaredTotalsRemainNeutralForFilenameEvidence() {
        for (track in listOf("1/0", "1/-2", "1/garbage")) {
            val provider = MemoryProvider()
            provider.file("01 One.mp3")
            provider.file("02 Two.mp3")
            val scan =
                Indexer(
                    MockMediaID(),
                    10,
                    listOf(provider),
                    MetadataReader { input, _, _ ->
                        val attributes =
                            mapOf("album" to listOf("Release"), "artist" to listOf("Band")) +
                                if (input.id.startsWith("01")) mapOf("trackNumber" to listOf(track)) else emptyMap()
                        Observation(attributes, emptyList(), emptyList())
                    },
                ).scan(listOf(provider.rootSpec()), generation = 1)
            val input =
                scan.copy(
                    nodes =
                        scan.nodes.map { node ->
                            if (node.name == "01 One.mp3") node.copy(attributes = node.attributes + ("trackNumber" to listOf(track))) else node
                        },
                )
            assertEquals(2, FilenameOrdinals.derive(input).size)
            assertEquals(
                track,
                input.nodes
                    .single { it.name == "01 One.mp3" }
                    .attributes
                    .first("trackNumber"),
            )
        }
    }
}
