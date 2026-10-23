package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real row/layout in the disposable test application, with no production profile writes. */
class LeadingArticlesSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wideBooleanRowsUseIconsAndRetainDraftIsolation() = exercise(1f)

    @Test fun narrowBooleanRowsUseIconsAndRetainDraftIsolation() = exercise(narrowBooleanFixtureDensity())

    private fun exercise(density: Float) {
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
        val choices = mutableListOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context) { action ->
                    when (action) {
                        is PlayerUiAction.SetDraftIgnoreLeadingArticles -> {
                            choices += action.enabled
                            state =
                                state.copy(settingsDraft = requireNotNull(state.settingsDraft).copy(ignoreLeadingArticles = action.enabled))
                        }

                        is PlayerUiAction.SetDraftAutoSearch -> {
                            state = state.copy(settingsDraft = requireNotNull(state.settingsDraft).copy(autoSearch = action.enabled))
                        }

                        is PlayerUiAction.SetDraftLongPress -> {
                            state =
                                state.copy(
                                    settingsDraft = requireNotNull(state.settingsDraft).copy(longPressStartsPlayback = action.enabled),
                                )
                        }

                        is PlayerUiAction.RememberSelectorPosition -> {
                            state = state.copy(selectorIndex = action.index, selectorOffset = action.offset, pageSize = action.pageSize)
                        }

                        else -> {
                            Unit
                        }
                    }
                }
            }
        }
        val initial = state.settings
        listOf(
            Triple(SettingsItem.LONG_PRESS, "Long press starts playback", false),
            Triple(SettingsItem.AUTO_SEARCH, "Auto search", true),
            Triple(SettingsItem.IGNORE_LEADING_ARTICLES, "Ignore leading articles", true),
        ).forEach { (item, heading, defaultValue) ->
            compose.onNodeWithTag("selector:list").performScrollToIndex(settingsItems().indexOf(item))
            compose.onNodeWithText(heading).assertExists()
            compose.onAllNodesWithText("Yes", useUnmergedTree = true).assertCountEquals(0)
            compose.onAllNodesWithText("No", useUnmergedTree = true).assertCountEquals(0)

            fun control(value: Boolean) =
                compose.onNodeWithContentDescription("$heading: ${if (value) "Yes" else "No"}; toggle", substring = true)
            val original = control(defaultValue)
            if (defaultValue) original.assertIsSelected() else original.assertIsNotSelected()
            val bounds = original.fetchSemanticsNode().boundsInRoot
            val target = if (context.resources.displayMetrics.widthPixels / density >= 800f) 100f else 76f
            assertEquals(target * density, bounds.width, 1f)
            assertEquals(target * density, bounds.height, 1f)
            original.performTouchInput { click() }
            val toggled = control(!defaultValue)
            if (defaultValue) toggled.assertIsNotSelected() else toggled.assertIsSelected()
            assertEquals(bounds, toggled.fetchSemanticsNode().boundsInRoot)
            toggled.performTouchInput { click() }
            assertEquals(bounds, control(defaultValue).fetchSemanticsNode().boundsInRoot)
        }
        compose.runOnIdle {
            assertEquals(listOf(false, true), choices)
            assertEquals(initial, state.settings)
            assertEquals(initial, state.settingsDraft)
        }
    }
}
