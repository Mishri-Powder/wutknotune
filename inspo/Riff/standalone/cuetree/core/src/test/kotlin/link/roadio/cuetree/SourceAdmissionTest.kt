// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceAdmissionTest {
    @Test fun providerCandidacyOverridesExtensionButCueRemainsMetadata() {
        val memory = MemoryProvider()
        memory.file("audio.bin")
        memory.file("rejected.mp3")
        memory.file("album.cue")
        val provider =
            object : SourceProvider by memory {
                override fun stat(handle: SourceHandle): SourceStat =
                    memory.stat(handle).copy(
                        mediaCandidate = memory.items.getValue(handle as MemoryProvider.Handle).name != "rejected.mp3",
                    )
            }
        val result =
            Indexer(MockMediaID(), 10, listOf(provider), emptyMetadata)
                .scan(listOf(memory.rootSpec()), generation = 1)
        assertEquals(
            mapOf("audio.bin" to NodeKind.MEDIA, "album.cue" to NodeKind.METADATA),
            result.nodes.filter { it.kind != NodeKind.DIRECTORY }.associate { it.name to it.kind },
        )
    }
}
