package link.roadio.riff.ui

import link.roadio.riff.player.core.selection.AiHeatmap
import link.roadio.riff.player.core.selection.AiPteBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEmptyStatePolicyTest {
    @Test
    fun noDataZerosOnlyCanvasCoordinatesAndDisablesPlay() {
        val selection =
            AiSelectionUiState(
                size = 50,
                pteBands = setOf(AiPteBand.SLOW, AiPteBand.FAST),
                arousal = 0.75f,
                valence = -0.5f,
            )
        val state = state(selection = selection)

        val effective = state.effectiveAiSelection()

        assertFalse(state.aiDataReady)
        assertFalse(state.aiPlayEnabled)
        assertEquals(0f, effective.arousal)
        assertEquals(0f, effective.valence)
        assertEquals(selection.size, effective.size)
        assertEquals(selection.pteBands, effective.pteBands)
        assertEquals("A 0.00 V 0.00 C  0", formatAiStatusLine(effective.arousal, effective.valence, 0))
    }

    @Test
    fun readyDataPreservesSelectionAndRequiresAvailableStorageForPlay() {
        val selection = AiSelectionUiState(arousal = -0.25f, valence = 0.5f)
        val ready = state(selection, AiHeatmap.empty(1, 1), aiDataAvailable = true)

        assertTrue(ready.aiDataReady)
        assertTrue(ready.aiPlayEnabled)
        assertSame(selection, ready.effectiveAiSelection())
        assertFalse(ready.copy(selectedStorageAvailable = false).aiPlayEnabled)
    }

    @Test
    fun heatmapWithoutUsableMdauDataIsNotReady() {
        val state = state(AiSelectionUiState(), AiHeatmap.empty(1, 1))

        assertFalse(state.aiDataReady)
        assertFalse(state.aiPlayEnabled)
    }

    @Test
    fun pointPlayAndMainEntryRequireReadyAiData() {
        assertTrue(PlayerUiAction.SetAiPoint(0.5f, -0.5f).requiresReadyAiData())
        assertTrue(PlayerUiAction.CreateAiQueue.requiresReadyAiData())
        assertTrue(PlayerUiAction.OpenCategory("category:ai").requiresReadyAiData())
        assertFalse(PlayerUiAction.OpenCategory("category:genres").requiresReadyAiData())
        assertFalse(PlayerUiAction.SetAiSize(10).requiresReadyAiData())
        assertFalse(PlayerUiAction.ToggleAiPteBand(AiPteBand.MEDIUM).requiresReadyAiData())
        assertFalse(PlayerUiAction.OpenAiPlaylistSelector.requiresReadyAiData())
    }

    private fun state(
        selection: AiSelectionUiState,
        heatmap: AiHeatmap? = null,
        aiDataAvailable: Boolean = false,
    ): PlayerUiState =
        PlayerUiState(
            screen = PlayerScreen.AI,
            root = BrowserItem("root", "Riff", BrowserKind.ROOT),
            nowPlaying = NowPlayingUiState(),
            aiSelection = selection,
            aiDataAvailable = aiDataAvailable,
            aiHeatmap = heatmap,
        )
}
