// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRevisionTest {
    @Test fun unchangedSourcesDoNotInvalidateAnAssessedCheckpoint() {
        val provider = MemoryProvider()
        provider.file("a.mp3")
        provider.file("b.mp3")
        val indexer = Indexer(MockMediaID(), 100, listOf(provider), emptyMetadata)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        val revisions = mutableListOf<Long>()
        indexer.scan(listOf(provider.rootSpec()), first, 2, onCheckpoint = { progress, _ ->
            revisions += progress.factsRevision
            false
        })
        assertTrue(revisions.size >= 3)
        assertEquals(1, revisions.distinct().size)
    }

    @Test fun aNewMediaFactInvalidatesTheCheckpoint() {
        val provider = MemoryProvider()
        provider.file("a.mp3")
        val indexer = Indexer(MockMediaID(), 100, listOf(provider), emptyMetadata)
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        provider.file("z.mp3")
        val revisions = mutableListOf<Long>()
        indexer.scan(listOf(provider.rootSpec()), first, 2, onCheckpoint = { progress, _ ->
            if (progress.kind == ScanCheckpointKind.STAGE || progress.kind == ScanCheckpointKind.MEDIA) revisions += progress.factsRevision
            false
        })
        assertEquals(3, revisions.size)
        assertTrue(revisions.drop(1).max() > revisions.first())
    }

    @Test fun aResolvedSidecarTableInvalidatesTheCheckpoint() {
        val provider = MemoryProvider()
        provider.file("audio.mp3")
        val indexer = Indexer(MockMediaID(), 100, listOf(provider))
        val first = indexer.scan(listOf(provider.rootSpec()), generation = 1)
        provider.file(
            "audio.cue",
            """
            FILE "audio.mp3" WAVE
              TRACK 01 AUDIO
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                INDEX 01 00:10:00
            """.trimIndent().toByteArray(),
        )
        val revisions = mutableListOf<Pair<ScanCheckpointKind, Long>>()
        indexer.scan(listOf(provider.rootSpec()), first, 2, onCheckpoint = { progress, _ ->
            revisions += progress.kind to progress.factsRevision
            false
        })
        val media = revisions.first { it.first == ScanCheckpointKind.STAGE }.second
        val ready = revisions.first { it.first == ScanCheckpointKind.EXPLICIT_TABLE }.second
        assertTrue(ready > media)
    }
}
