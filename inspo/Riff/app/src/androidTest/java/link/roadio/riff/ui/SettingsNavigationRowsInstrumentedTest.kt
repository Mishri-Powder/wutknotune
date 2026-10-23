package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsNavigationRowsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun moreAndMediaFoldersRowsDispatchFromTheWholeRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.SETTINGS,
                root = BrowserItem("root", "Root", BrowserKind.ROOT),
                nowPlaying = NowPlayingUiState(),
                settingsDraft = PlayerSettingsUiState(),
            ),
        )
        val actions = mutableListOf<PlayerUiAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context) { action ->
                    actions += action
                    if (action == PlayerUiAction.OpenMoreSettings) {
                        state = state.copy(screen = PlayerScreen.SETTINGS_MORE, moreSettingsDraft = state.settings)
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Open more settings").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Edit media folders").assertHasClickAction().performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(PlayerUiAction.OpenMoreSettings, PlayerUiAction.OpenSafGrantSelector),
                actions.filter { it == PlayerUiAction.OpenMoreSettings || it == PlayerUiAction.OpenSafGrantSelector },
            )
        }
    }
}
