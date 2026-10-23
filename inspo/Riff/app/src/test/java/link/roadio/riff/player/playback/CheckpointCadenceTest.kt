package link.roadio.riff.player.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointCadenceTest {
    @Test
    fun pausedManipulationIsEventuallySavedWithoutMoreInput() {
        val cadence = CheckpointCadence(1_000)
        cadence.manipulated()
        assertFalse(cadence.due(30_999, false))
        assertTrue(cadence.due(31_000, false))
        cadence.submitted()
        cadence.saved(31_000)
        assertFalse(cadence.due(200_000, false))
    }

    @Test
    fun everySuccessResetsThePlaybackDeadlineAndFailuresDoNot() {
        val cadence = CheckpointCadence(0)
        assertFalse(cadence.due(59_999, true))
        assertTrue(cadence.due(60_000, true))
        cadence.submitted()
        assertTrue(cadence.due(61_000, true))
        cadence.saved(61_000)
        assertFalse(cadence.due(120_999, true))
        assertTrue(cadence.due(121_000, true))
    }

    @Test
    fun transitionSuccessResetsPendingManipulationAndCompletionCancelsIt() {
        val cadence = CheckpointCadence(0)
        cadence.manipulated()
        cadence.submitted()
        cadence.saved(10_000)
        assertFalse(cadence.due(30_000, false))
        cadence.manipulated()
        assertTrue(cadence.due(40_000, false))
        cadence.cancel()
        assertFalse(cadence.due(50_000, false))
    }
}
