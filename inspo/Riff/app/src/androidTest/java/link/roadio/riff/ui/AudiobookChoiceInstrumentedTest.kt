package link.roadio.riff.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Disposable-target fixture exercises the real renderer with no working-library mutations. */
class AudiobookChoiceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun togglesSwitchDirectlyAndReturnToAuto() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        val objectKey = "folder:7:fixture"
        val folder = BrowserItem(id = objectKey, label = "Fixture", kind = BrowserKind.FOLDER, playable = true, reactionKey = objectKey)
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.ROW_ACTIONS,
                root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(folder)),
                nowPlaying = NowPlayingUiState(),
                actionsItemId = folder.id,
            ),
        )
        val commands = mutableListOf<PlayerUiAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context) { action ->
                    commands += action
                    if (action is PlayerUiAction.SetAudiobookChoice) state = state.copy(audiobookChoiceDraft = action.choice)
                }
            }
        }

        fun choice(value: AudiobookChoice) = compose.onNodeWithTag("audiobook-choice:${value.name}")
        val music =
            choice(AudiobookChoice.MUSIC)
                .assertIsEnabled()
                .assertIsNotSelected()
                .fetchSemanticsNode()
                .boundsInRoot
        val book =
            choice(AudiobookChoice.BOOK)
                .assertIsEnabled()
                .assertIsNotSelected()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(abs(music.width - book.width) < 1f)
        assertEquals(config.float("geometry.playbackButtonHeight"), music.height, 1f)
        assertEquals(music.height, book.height, 1f)
        compose.onNodeWithText("Auto", useUnmergedTree = true).assertDoesNotExist()
        choice(AudiobookChoice.AUTO).assertDoesNotExist()
        compose.onNodeWithTag("audiobook-cause").assertDoesNotExist()
        val advisory = compose.onNodeWithText(ROW_ACTIONS_AI_ADVISORY).fetchSemanticsNode().boundsInRoot
        assertTrue(advisory.top >= book.bottom)

        val transitions =
            listOf(
                AudiobookChoice.MUSIC to AudiobookChoice.MUSIC,
                AudiobookChoice.BOOK to AudiobookChoice.BOOK,
                AudiobookChoice.MUSIC to AudiobookChoice.MUSIC,
                AudiobookChoice.MUSIC to AudiobookChoice.AUTO,
                AudiobookChoice.BOOK to AudiobookChoice.BOOK,
                AudiobookChoice.BOOK to AudiobookChoice.AUTO,
            )
        for ((pressed, expected) in transitions) {
            choice(pressed).performTouchInput { click() }
            for (button in listOf(AudiobookChoice.MUSIC, AudiobookChoice.BOOK)) {
                val node = choice(button).assertIsEnabled()
                if (button == expected) node.assertIsSelected() else node.assertIsNotSelected()
            }
            compose.runOnIdle {
                assertEquals(PlayerUiAction.SetAudiobookChoice(objectKey, expected), commands.last())
                assertEquals(expected, state.audiobookChoiceDraft)
                assertTrue(state.audiobookOverrides.isEmpty())
            }
        }
        for (kind in listOf(BrowserKind.ALBUM, BrowserKind.TRACK)) {
            compose.runOnIdle {
                val editable = folder.copy(id = kind.name, kind = kind, reactionKey = kind.name)
                state =
                    state.copy(
                        root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(editable)),
                        actionsItemId = editable.id,
                    )
            }
            choice(AudiobookChoice.MUSIC).assertIsEnabled()
            choice(AudiobookChoice.BOOK).assertIsEnabled()
            choice(AudiobookChoice.BOOK).performTouchInput { click() }
            choice(AudiobookChoice.MUSIC).performTouchInput { click() }
            choice(AudiobookChoice.MUSIC).performTouchInput { click() }
        }
        for (kind in listOf(BrowserKind.ARTIST, BrowserKind.GENRE)) {
            compose.runOnIdle {
                val readOnly = folder.copy(id = kind.name, kind = kind, reactionKey = kind.name)
                state =
                    state.copy(
                        root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(readOnly)),
                        actionsItemId = readOnly.id,
                    )
            }
            choice(AudiobookChoice.MUSIC).assertIsNotEnabled()
            choice(AudiobookChoice.BOOK).assertIsNotEnabled()
        }
        compose.runOnIdle {
            state =
                state.copy(
                    root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(folder)),
                    actionsItemId = folder.id,
                    audiobookCommitInProgress = true,
                )
        }
        choice(AudiobookChoice.MUSIC).assertIsNotEnabled()
        choice(AudiobookChoice.BOOK).assertIsNotEnabled()
        compose.runOnIdle {
            val playlist = folder.copy(id = "playlist", kind = BrowserKind.PLAYLIST)
            state =
                state.copy(root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(playlist)), actionsItemId = playlist.id)
        }
        choice(AudiobookChoice.MUSIC).assertDoesNotExist()
        choice(AudiobookChoice.BOOK).assertDoesNotExist()
        compose.onNodeWithText(ROW_ACTIONS_AI_ADVISORY).assertDoesNotExist()
    }

    @Test
    fun narrowLargeTextAdvisoryWrapsWithoutShrinkingTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        val folder =
            BrowserItem(
                id = "folder:long",
                label = "Fixture",
                kind = BrowserKind.FOLDER,
                reactionKey = "folder:long",
            )
        val state =
            PlayerUiState(
                screen = PlayerScreen.ROW_ACTIONS,
                root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(folder)),
                nowPlaying = NowPlayingUiState(),
                actionsItemId = folder.id,
            )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    PlayerComposeUi(state, config, CalibrationSessionState(), context) {}
                }
            }
        }
        val music =
            compose
                .onNodeWithTag("audiobook-choice:MUSIC")
                .performScrollTo()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(config.float("geometry.playbackButtonHeight"), music.height, 1f)
        val text = ROW_ACTIONS_AI_ADVISORY
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performScrollTo().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        assertTrue(layouts.single().lineCount > 1)
        assertFalse(layouts.single().hasVisualOverflow)
        assertEquals(
            text,
            layouts
                .single()
                .layoutInput.text.text,
        )
    }
}
