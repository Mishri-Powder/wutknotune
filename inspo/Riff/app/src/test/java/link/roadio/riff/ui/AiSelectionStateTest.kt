package link.roadio.riff.ui

import link.roadio.riff.player.core.selection.AiPteBand
import org.junit.Assert.assertEquals
import org.junit.Test

class AiSelectionStateTest {
    @Test
    fun `fresh draft has neutral point no PTE band and size twenty`() {
        assertEquals(AiSelectionUiState(), AiSelectionUiState(size = 20, pteBands = emptySet(), arousal = 0f, valence = 0f))
    }

    @Test
    fun `PTE toggles allow at most two and unlock the third after clearing`() {
        val two =
            AiSelectionUiState()
                .togglePteBand(AiPteBand.SLOW)
                .togglePteBand(AiPteBand.MEDIUM)
        assertEquals(setOf(AiPteBand.SLOW, AiPteBand.MEDIUM), two.pteBands)
        assertEquals(two, two.togglePteBand(AiPteBand.FAST))

        val replaced =
            two
                .togglePteBand(AiPteBand.MEDIUM)
                .togglePteBand(AiPteBand.FAST)
        assertEquals(setOf(AiPteBand.SLOW, AiPteBand.FAST), replaced.pteBands)
        assertEquals(
            emptySet<AiPteBand>(),
            replaced.togglePteBand(AiPteBand.SLOW).togglePteBand(AiPteBand.FAST).pteBands,
        )
    }

    @Test
    fun `PTE availability and two selections disable the correct controls`() {
        val two =
            AiSelectionUiState(
                pteBands = setOf(AiPteBand.SLOW, AiPteBand.MEDIUM),
            )
        assertEquals(false, two.isPteBandEnabled(AiPteBand.SLOW, pteAvailable = false))
        assertEquals(true, two.isPteBandEnabled(AiPteBand.SLOW, pteAvailable = true))
        assertEquals(false, two.isPteBandEnabled(AiPteBand.FAST, pteAvailable = true))
        val one = two.togglePteBand(AiPteBand.MEDIUM)
        assertEquals(true, one.isPteBandEnabled(AiPteBand.FAST, pteAvailable = true))
    }

    @Test
    fun `size is always one allowed value`() {
        val selected = AiSelectionUiState().selectSize(50)
        assertEquals(50, selected.size)
        assertEquals(selected, selected.selectSize(12))
        assertEquals(10, selected.selectSize(10).size)
    }

    @Test
    fun `point clamps finite values and ignores invalid values`() {
        val clamped = AiSelectionUiState().selectPoint(arousal = 2f, valence = -2f)
        assertEquals(1f, clamped.arousal)
        assertEquals(-1f, clamped.valence)
        assertEquals(clamped, clamped.selectPoint(Float.NaN, 0f))
    }
}
