package link.roadio.riff.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.playback.AudiobookSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AudiobookSpeedInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sharedSlotKeepsBoundsBoldMonospaceAndIsolatesShortAndLongPress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.PLAYBACK,
                root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
                nowPlaying = NowPlayingUiState(mediaId = MediaId(123), aiReactionEligible = true),
            ),
        )
        var width by mutableStateOf(1600f)
        var fontScale by mutableStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        val actions = mutableListOf<PlayerUiAction>()
        val config =
            UiConfig.fromUserText(
                """{"skins":{"default":{"behavior":{"maxStringLength":1},"typography":{"topBarFontFamily":"serif","topBarFontWeight":300}}}}""",
            )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.65f, fontScale), LocalLayoutDirection provides direction) {
                Box(Modifier.size(width.dp, 960.dp)) {
                    PlayerComposeUi(state, config, CalibrationSessionState(), context) {
                        actions += it
                        if (it == PlayerUiAction.AdvanceAudiobookSpeed) state = state.copy(audiobookSpeed = state.audiobookSpeed.next())
                        if (it == PlayerUiAction.ResetAudiobookSpeed) state = state.copy(audiobookSpeed = AudiobookSpeed.NORMAL)
                    }
                }
            }
        }

        fun speedDescription(): String = "Audiobook speed ${state.audiobookSpeed.label}; change to ${state.audiobookSpeed.next().label}"
        for (wide in listOf(true, false)) {
            for (night in listOf(false, true)) {
                compose.runOnIdle {
                    width = if (wide) 1600f else 600f
                    fontScale = if (night) 2f else 1f
                    direction = if (night) LayoutDirection.Rtl else LayoutDirection.Ltr
                    state =
                        state.copy(
                            contentMode = ContentMode.MUSIC,
                            settings = state.settings.copy(colorScheme = if (night) ColorScheme.NIGHT else ColorScheme.DAY),
                            nowPlaying = state.nowPlaying.copy(effectiveBook = false, bookTitleContext = false, aiReactionEligible = true),
                        )
                }
                val banBounds = compose.onNodeWithContentDescription("Ban current track").fetchSemanticsNode().boundsInRoot
                compose.runOnIdle {
                    state =
                        state.copy(
                            contentMode = ContentMode.AUDIOBOOK,
                            nowPlaying = state.nowPlaying.copy(effectiveBook = true, bookTitleContext = true, aiReactionEligible = false),
                            bookmarkMarkAllowed = true,
                            audiobookSpeed = AudiobookSpeed.NORMAL,
                        )
                }
                val control = compose.onNodeWithContentDescription(speedDescription())
                control.assertIsEnabled()
                assertEquals(banBounds, control.fetchSemanticsNode().boundsInRoot)
                val bookmark = compose.onNodeWithContentDescription("Bookmark current position").fetchSemanticsNode().boundsInRoot
                assertEquals(banBounds.width, bookmark.width, 1f)
                assertEquals(banBounds.height, bookmark.height, 0f)
                val layouts = mutableListOf<TextLayoutResult>()
                compose
                    .onNodeWithText(
                        "1.0x",
                        useUnmergedTree = true,
                    ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(
                    FontFamily.Monospace,
                    layouts
                        .single()
                        .layoutInput.style.fontFamily,
                )
                assertEquals(
                    FontWeight.Bold,
                    layouts
                        .single()
                        .layoutInput.style.fontWeight,
                )
                assertTrue(
                    layouts
                        .single()
                        .layoutInput.style.fontSize.value >= 36f,
                )
                compose.runOnIdle { actions.clear() }
                control.performClick()
                compose.runOnIdle {
                    assertEquals(listOf(PlayerUiAction.AdvanceAudiobookSpeed), actions)
                    assertEquals(AudiobookSpeed.ELEVEN_TENTHS, state.audiobookSpeed)
                    actions.clear()
                }
                repeat(AudiobookSpeed.entries.size - 1) {
                    compose.onNodeWithContentDescription(speedDescription()).performClick()
                }
                compose.runOnIdle {
                    assertEquals(List(AudiobookSpeed.entries.size - 1) { PlayerUiAction.AdvanceAudiobookSpeed }, actions)
                    assertEquals(AudiobookSpeed.NORMAL, state.audiobookSpeed)
                    actions.clear()
                    state = state.copy(audiobookSpeed = AudiobookSpeed.FIFTEEN_TENTHS)
                }
                compose.onNodeWithContentDescription(speedDescription()).performTouchInput { longClick() }
                compose.runOnIdle {
                    assertEquals(listOf(PlayerUiAction.ResetAudiobookSpeed), actions)
                    assertEquals(AudiobookSpeed.NORMAL, state.audiobookSpeed)
                    actions.clear()
                }
                compose
                    .onNodeWithContentDescription(
                        speedDescription(),
                    ).performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
                compose.runOnIdle {
                    assertEquals(listOf(PlayerUiAction.ResetAudiobookSpeed), actions)
                    actions.clear()
                    state =
                        state.copy(selectedStorageAvailable = false)
                }
                compose.onNodeWithContentDescription(speedDescription() + "; unavailable").assertIsNotEnabled().performClick()
                compose.runOnIdle {
                    assertTrue(actions.isEmpty())
                    state =
                        state.copy(
                            contentMode = ContentMode.MUSIC,
                            selectedStorageAvailable = true,
                            nowPlaying = state.nowPlaying.copy(effectiveBook = false, bookTitleContext = false, aiReactionEligible = true),
                        )
                }
                compose.onAllNodesWithText("1.0x").assertCountEquals(0)
                compose.onNodeWithContentDescription("Ban current track").assertIsEnabled()
            }
        }
    }
}
