package link.roadio.riff.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundFlipTapTest {
    @Test
    fun `short stationary unconsumed single pointer tap is eligible`() {
        assertTrue(
            backgroundFlipTapEligible(
                cancelled = false,
                multiplePointers = false,
                maximumDistancePx = 7.9f,
                elapsedMillis = 499L,
                touchSlopPx = 8f,
                longPressTimeoutMillis = 500L,
            ),
        )
    }

    @Test
    fun `cancelled long moved and multipointer gestures are rejected`() {
        val valid =
            BackgroundTapEvidence(
                cancelled = false,
                multiplePointers = false,
                maximumDistancePx = 7.9f,
                elapsedMillis = 499L,
            )
        assertRejected(valid.copy(cancelled = true))
        assertRejected(valid.copy(multiplePointers = true))
        assertRejected(valid.copy(maximumDistancePx = 8.1f))
        assertRejected(valid.copy(elapsedMillis = 500L))
    }

    private fun assertRejected(evidence: BackgroundTapEvidence) {
        assertFalse(
            backgroundFlipTapEligible(
                cancelled = evidence.cancelled,
                multiplePointers = evidence.multiplePointers,
                maximumDistancePx = evidence.maximumDistancePx,
                elapsedMillis = evidence.elapsedMillis,
                touchSlopPx = 8f,
                longPressTimeoutMillis = 500L,
            ),
        )
    }

    private data class BackgroundTapEvidence(
        val cancelled: Boolean,
        val multiplePointers: Boolean,
        val maximumDistancePx: Float,
        val elapsedMillis: Long,
    )
}
