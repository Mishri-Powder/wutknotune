package link.roadio.riff.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.player.core.selection.AiHeatmap
import link.roadio.riff.player.core.selection.AiPteBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MainAiReadinessInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aiTileBlocksEntryUntilUsableMdauHeatmapIsReady() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        val ai = BrowserItem("category:ai", "AI", BrowserKind.AI, "ai")
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.MAIN,
                root = BrowserItem("root", "Library", BrowserKind.ROOT, children = listOf(ai)),
                nowPlaying = NowPlayingUiState(),
            ),
        )
        val commands = mutableListOf<PlayerUiAction>()
        compose.setContent {
            PlayerComposeUi(state, config, CalibrationSessionState(), context) { action ->
                commands += action
            }
        }

        val tile = compose.onNodeWithContentDescription("AI mood selection")
        tile.assertIsNotEnabled()
        assertTrue(commands.isEmpty())

        compose.runOnIdle {
            state =
                state.copy(
                    aiDataAvailable = true,
                    aiHeatmap = AiHeatmap.empty(1, 1),
                )
        }
        tile.assertIsEnabled()
        tile.performClick()
        compose.runOnIdle {
            assertEquals(PlayerUiAction.OpenCategory("category:ai"), commands.single())
        }
    }

    @Test
    fun pteChoicesDisableTheThirdSelectionAndUnlockItAfterClearing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        var state by
            mutableStateOf(
                PlayerUiState(
                    screen = PlayerScreen.AI,
                    root = BrowserItem("root", "Library", BrowserKind.ROOT),
                    nowPlaying = NowPlayingUiState(),
                    aiSelection =
                        AiSelectionUiState(
                            pteBands = setOf(AiPteBand.SLOW, AiPteBand.MEDIUM),
                        ),
                    aiDataAvailable = true,
                    aiPteAvailable = true,
                    aiHeatmap = AiHeatmap.empty(1, 1),
                ),
            )
        compose.setContent {
            PlayerComposeUi(state, config, CalibrationSessionState(), context) { action ->
                if (action is PlayerUiAction.ToggleAiPteBand) {
                    state = state.copy(aiSelection = state.aiSelection.togglePteBand(action.pteBand))
                }
            }
        }

        compose.onNodeWithContentDescription("Slow perceived speed; selected").assertIsEnabled()
        compose.onNodeWithContentDescription("Medium perceived speed; selected").assertIsEnabled()
        compose.onNodeWithContentDescription("Fast perceived speed; not selected").assertIsNotEnabled()

        compose.onNodeWithContentDescription("Slow perceived speed; selected").performClick()
        compose.onNodeWithContentDescription("Fast perceived speed; not selected").assertIsEnabled()
    }

    @Test
    fun oldReleaseShowsOneButtonWarningThenMainWorksNormally() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        val children =
            listOf(
                BrowserItem("category:albums", "Albums", BrowserKind.ALBUM, "album"),
                BrowserItem("category:books", "Books", BrowserKind.CATEGORY, "audiobook"),
                BrowserItem("category:ai", "AI", BrowserKind.AI, "ai"),
            )
        val state =
            PlayerUiState(
                screen = PlayerScreen.MAIN,
                root = BrowserItem("root", "Library", BrowserKind.ROOT, children = children),
                nowPlaying = NowPlayingUiState(),
                aiDataAvailable = true,
                aiHeatmap = AiHeatmap.empty(1, 1),
            )
        val commands = mutableListOf<PlayerUiAction>()
        compose.setContent {
            var warningVisible by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(true)
            }
            PlayerComposeUi(
                state = state,
                config = config,
                calibrationSession = CalibrationSessionState(),
                hostContext = context,
                releaseIdentity = ReleaseIdentity(ReleaseChannel.DEBUG, LocalDate.of(2026, 1, 1)),
                releaseAgeWarningVisible = warningVisible,
                onAcknowledgeReleaseAge = { warningVisible = false },
                dispatch = commands::add,
            )
        }

        compose.onNodeWithText("Old Debug build").assertExists()
        compose.onNodeWithContentDescription("OK").performClick()
        compose.onNodeWithContentDescription("Albums").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerUiAction.OpenCategory("category:albums")), commands) }
    }
}
