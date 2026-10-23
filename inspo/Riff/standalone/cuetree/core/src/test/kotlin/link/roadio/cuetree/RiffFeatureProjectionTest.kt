// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RiffFeatureProjectionTest {
    @Test fun cueEntryAiPairsStayIndependentFromWholeSheetAndOtherEntries() {
        val p = MemoryProvider()
        p.file("image.mp3", byteArrayOf(1))
        p.file(
            "disc.cue",
            """
            REM RIFF_PTE 0.9
            REM RIFF_PTE_CONFIDENCE 1.0
            FILE "image.mp3" WAVE
              TRACK 01 AUDIO
                REM RIFF_AROUSAL 0.2
                REM RIFF_AROUSAL_CONFIDENCE 0.8
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                REM RIFF_PTE 0.3
                REM RIFF_PTE_CONFIDENCE 0.7
                INDEX 01 00:10:00
            """.trimIndent().toByteArray(),
        )
        val result = Indexer(MockMediaID(), 100, listOf(p)).scan(listOf(p.rootSpec()), generation = 1)
        val fragments = result.nodes.filter { it.kind == NodeKind.FRAGMENT }.sortedBy { it.span!!.beginUs }
        assertEquals(2, fragments.size)
        assertFalse("Whole-sheet AI must not become clip evidence", "riffPte" in fragments[0].attributes)
        assertEquals(listOf("0.2"), fragments[0].attributes["riffArousal"])
        assertEquals(listOf("0.3"), fragments[1].attributes["riffPte"])
        assertFalse("Neighboring clip fields stay separate", "riffArousal" in fragments[1].attributes)
        assertEquals(result, FlatCatalogs.encode(result).snapshot().copy(diagnostics = result.diagnostics))
    }
}
