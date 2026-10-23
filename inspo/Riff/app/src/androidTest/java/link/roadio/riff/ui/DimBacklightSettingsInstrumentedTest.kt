package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.player.core.settings.PlayerDimBacklightMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DimBacklightSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun singleThemeAndDimControlsCycleAndOffRemainsActionable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.SETTINGS,
                root = BrowserItem("root", "Root", BrowserKind.ROOT),
                nowPlaying = NowPlayingUiState(),
                settingsDraft = PlayerSettingsUiState(),
            ),
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(state, UiConfig.defaults(context), CalibrationSessionState(), context) { action ->
                    when (action) {
                        is PlayerUiAction.SetDraftTheme -> {
                            state =
                                state.copy(settingsDraft = state.settingsDraft?.copy(colorScheme = action.scheme))
                        }

                        is PlayerUiAction.SetDraftDimBacklight -> {
                            state =
                                state.copy(moreSettingsDraft = state.moreSettingsDraft?.copy(dimBacklight = action.mode))
                        }

                        else -> {
                            Unit
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("selector:list").performScrollToIndex(settingsItems().indexOf(SettingsItem.THEME))
        for (label in listOf("Auto; next Day", "Day; next Night", "Night; next Auto")) {
            compose.onNodeWithContentDescription("Theme: $label; selected").assertIsEnabled().performClick()
        }
        compose.runOnIdle {
            assertEquals(ColorScheme.SYSTEM, state.settingsDraft?.colorScheme)
            state = state.copy(screen = PlayerScreen.SETTINGS_MORE, moreSettingsDraft = state.settings)
        }
        compose.onNodeWithTag("selector:list").performScrollToIndex(moreSettingsItems().indexOf(SettingsItem.DIM_BACKLIGHT))
        for (label in listOf("Auto; next Off; selected", "Off; next Day", "Day; next Night; selected", "Night; next Auto; selected")) {
            compose.onNodeWithContentDescription("Dim Backlight: $label").assertIsEnabled().performClick()
        }
        compose.runOnIdle { assertEquals(PlayerDimBacklightMode.AUTO, state.moreSettingsDraft?.dimBacklight) }
    }
}
