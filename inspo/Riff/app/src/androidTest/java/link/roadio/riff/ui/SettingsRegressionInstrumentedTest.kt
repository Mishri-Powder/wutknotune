package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsRegressionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun morePageExposesReplayGainAndAudioPathControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.SETTINGS_MORE,
                root = BrowserItem("root", "Root", BrowserKind.ROOT),
                nowPlaying = NowPlayingUiState(),
                moreSettingsDraft = PlayerSettingsUiState(),
            ),
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(state, UiConfig.defaults(context), CalibrationSessionState(), context) { action ->
                    val draft = requireNotNull(state.moreSettingsDraft)
                    when (action) {
                        is PlayerUiAction.SetDraftReplayGainMode -> {
                            state = state.copy(moreSettingsDraft = draft.copy(replayGainMode = action.mode))
                        }

                        is PlayerUiAction.SetDraftReplayGainTarget -> {
                            state = state.copy(moreSettingsDraft = draft.copy(replayGainTarget = action.target))
                        }

                        else -> {
                            Unit
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("selector:list").performScrollToIndex(moreSettingsItems().indexOf(SettingsItem.REPLAY_GAIN))
        compose.onNodeWithContentDescription("ReplayGain mode F; change to N; selected").performClick()
        compose
            .onNodeWithContentDescription("ReplayGain target minus 23 LUFS; change to zero, unity gain; not selected; unavailable")
            .assertIsNotEnabled()
        compose.onNodeWithContentDescription("ReplayGain mode N; change to I; not selected").performClick()
        compose
            .onNodeWithContentDescription("ReplayGain target minus 23 LUFS; change to zero, unity gain; selected")
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle {
            assertEquals(ReplayGainMode.I, state.moreSettingsDraft?.replayGainMode)
            assertEquals(ReplayGainTarget.ZERO, state.moreSettingsDraft?.replayGainTarget)
        }
    }

    @Test
    fun storageWarningKeepsCompleteVisibleText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state =
            PlayerUiState(
                screen = PlayerScreen.DATA_STORAGE,
                root = BrowserItem("root", "Root", BrowserKind.ROOT),
                nowPlaying = NowPlayingUiState(),
                dataStorageLocations = listOf(DataStorageUiItem("internal", "Internal storage")),
            )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(state, UiConfig.defaults(context), CalibrationSessionState(), context) {}
            }
        }
        compose.onNodeWithContentDescription(DATA_STORAGE_WARNING).assertExists()
        compose.onNodeWithText(DATA_STORAGE_WARNING, useUnmergedTree = true).assertExists()
    }
}
