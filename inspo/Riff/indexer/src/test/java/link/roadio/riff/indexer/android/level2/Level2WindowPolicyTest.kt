package link.roadio.riff.indexer.android.level2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Level2WindowPolicyTest {
    @Test
    fun baseStartsAreCenteredOnTenSecondAiWindows() {
        assertEquals(
            listOf(20_000_000L, 45_000_000L, 70_000_000L),
            level2BaseWindowStarts(100_000_000L),
        )
    }

    @Test
    fun unusableWindowMovesForwardTwiceByTwentySeconds() {
        assertEquals(
            listOf(20_000_000L, 40_000_000L, 60_000_000L),
            level2RetryWindowStarts(20_000_000L, 90_000_000L),
        )
    }

    @Test
    fun clampedRetryNeverRepeatsTheSameTailWindow() {
        assertEquals(
            listOf(70_000_000L, 90_000_000L),
            level2RetryWindowStarts(70_000_000L, 90_000_000L),
        )
    }

    @Test
    fun existingTrackGainLeavesOnlyTheTenSecondAiPayload() {
        assertEquals(LEVEL2_AI_EXCERPT_US, level2RequestedWindowUs(needsLoudness = false))
        assertEquals(LEVEL2_LOUDNESS_WINDOW_US, level2RequestedWindowUs(needsLoudness = true))
    }

    @Test
    fun fullAnalysisStatusContainsOnlyBoundedFilename() {
        val status = fullLoudnessAnalysisStatus("content://tree/music/artist/track.flac")

        assertEquals("Full loudness analysis: track.flac", status)
        assertFalse(status.contains("content://"))
        assertTrue(status.length <= 120)
    }
}
