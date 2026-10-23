package link.roadio.riff.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.player.core.selection.AiHeatmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiTitlePlateInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun plateStaysAtPhysicalRightAfterResizeAndOpensOnlyTheExistingMenu() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        val selection = AiSelectionUiState(size = 50, arousal = 0.3f, valence = -0.2f)
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.AI,
                root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
                nowPlaying = NowPlayingUiState(),
                aiSelection = selection,
                aiDataAvailable = true,
                aiHeatmap = AiHeatmap.empty(1, 1),
            ),
        )
        var width by mutableStateOf(600f)
        var height by mutableStateOf(1500f)
        var fontScale by mutableStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        val commands = mutableListOf<PlayerUiAction>()
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val fit = minOf(1f, constraints.maxWidth / 1400f, constraints.maxHeight / 1650f)
                CompositionLocalProvider(
                    LocalDensity provides Density(fit, fontScale),
                    LocalLayoutDirection provides direction,
                ) {
                    Box(Modifier.size(width.dp, height.dp)) {
                        PlayerComposeUi(state, config, CalibrationSessionState(), context) { action ->
                            commands += action
                            when (action) {
                                PlayerUiAction.OpenAiActions -> state = state.copy(screen = PlayerScreen.AI_ACTIONS)
                                PlayerUiAction.CloseAiActions -> state = state.copy(screen = PlayerScreen.AI)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }

        fun verifyHeader() {
            val chrome = compose.onNodeWithTag("az:browser-chrome").fetchSemanticsNode().boundsInRoot
            val plate = compose.onNodeWithTag("ai:title-plate").assertIsEnabled().fetchSemanticsNode()
            val cancel = compose.onNodeWithContentDescription("Cancel AI queue").fetchSemanticsNode().boundsInRoot
            assertEquals(chrome.right, plate.boundsInRoot.right, 1f)
            assertEquals(chrome.left, cancel.left, 1f)
            assertTrue(cancel.right < plate.boundsInRoot.left)
            assertEquals(Role.Button, plate.config[SemanticsProperties.Role])
            compose.onAllNodes(hasContentDescription("AI actions") and hasClickAction()).assertCountEquals(1)
            compose.onAllNodes(hasClickAction() and hasTestTag("ai:title-plate")).assertCountEquals(1)
            compose.onNodeWithText("AI", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("ai:title-plate").performClick()
            compose.onNodeWithText("AI actions").assertExists()
            compose.onNodeWithContentDescription("Back to AI selection").performClick()
            compose.runOnIdle {
                assertEquals(selection, state.aiSelection)
                assertEquals(listOf(PlayerUiAction.OpenAiActions, PlayerUiAction.CloseAiActions), commands.takeLast(2))
            }
        }

        verifyHeader()
        compose.runOnIdle {
            width = 1300f
            height = 800f
        }
        verifyHeader()
        compose.runOnIdle {
            width = 600f
            height = 1500f
        }
        verifyHeader()
        compose.runOnIdle {
            width = 1300f
            height = 800f
            direction = LayoutDirection.Rtl
            fontScale = 2f
        }
        verifyHeader()
        compose.runOnIdle {
            width = 600f
            height = 1500f
            state =
                state.copy(settings = state.settings.copy(colorScheme = ColorScheme.NIGHT))
        }
        verifyHeader()
    }
}
