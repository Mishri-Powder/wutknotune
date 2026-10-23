package link.roadio.riff.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHeatmapUpdatePolicyTest {
    @Test
    fun `only current request built for current numeric selection may install`() {
        val built = AiSelectionUiState(arousal = 0.25f, valence = -0.5f)

        assertTrue(shouldInstallAiHeatmapResult(4L, 4L, built, built))
        assertFalse(shouldInstallAiHeatmapResult(3L, 4L, built, built))
        assertFalse(
            shouldInstallAiHeatmapResult(
                4L,
                4L,
                built,
                built.copy(valence = -0.25f),
            ),
        )
    }
}
