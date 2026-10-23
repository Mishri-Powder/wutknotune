package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real Compose controls in the isolated instrumented package; no user DB effects. */
class RebuildSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wideRebuildScopesHaveIndependentCheckboxSemantics() = exercise(1f)

    @Test fun narrowRebuildScopesHaveIndependentCheckboxSemantics() = exercise(2f)

    private fun exercise(density: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.SETTINGS_MORE,
                root = BrowserItem("root", "Root", BrowserKind.ROOT),
                nowPlaying = NowPlayingUiState(),
                moreSettingsDraft = PlayerSettingsUiState(),
            ),
        )
        val commands = mutableListOf<PlayerUiAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context) { action ->
                    commands += action
                    when (action) {
                        is PlayerUiAction.SetDraftRebuild -> {
                            val draft = requireNotNull(state.moreSettingsDraft)
                            val selected = draft.pendingRebuild
                            state =
                                state.copy(
                                    moreSettingsDraft =
                                        draft.copy(
                                            pendingRebuild =
                                                if (action.rebuild in
                                                    selected
                                                ) {
                                                    selected - action.rebuild
                                                } else {
                                                    selected + action.rebuild
                                                },
                                        ),
                                )
                        }

                        is PlayerUiAction.RememberSelectorPosition -> {
                            state =
                                state.copy(selectorIndex = action.index, selectorOffset = action.offset, pageSize = action.pageSize)
                        }

                        else -> {
                            Unit
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(ROW_ACTIONS_AI_ADVISORY).assertDoesNotExist()
        compose.onNodeWithTag("selector:list").performScrollToIndex(moreSettingsItems().lastIndex)

        fun control(
            label: String,
            selected: Boolean,
        ) = compose.onNodeWithContentDescription("$label; ${if (selected) "selected" else "not selected"}")
        val ia = "Rebuild Cuetree index"
        val au = "Rebuild audio analysis"
        control(au, false).assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        control(ia, false).assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        val bounds = control(ia, false).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width / density >= 76f && bounds.height / density >= 76f)
        control(ia, false).performTouchInput { click() }
        control(ia, true).assertIsOn()
        compose.runOnIdle {
            assertEquals(setOf(DatabaseRebuild.IA), state.moreSettingsDraft?.pendingRebuild)
        }
        control(au, false).performTouchInput { click() }
        control(au, true).assertIsOn()
        control(ia, true).assertIsOn()
        compose.runOnIdle { assertEquals(setOf(DatabaseRebuild.AU, DatabaseRebuild.IA), state.moreSettingsDraft?.pendingRebuild) }
        control(ia, true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(setOf(DatabaseRebuild.AU), state.moreSettingsDraft?.pendingRebuild) }
        control(au, true).performTouchInput { click() }
        compose.runOnIdle {
            assertTrue(requireNotNull(state.moreSettingsDraft).pendingRebuild.isEmpty())
            assertTrue(commands.none { it == PlayerUiAction.SaveMoreSettings })
        }
    }
}
