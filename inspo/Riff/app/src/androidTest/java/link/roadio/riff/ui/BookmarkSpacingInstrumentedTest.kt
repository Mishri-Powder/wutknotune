package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.storage.PlaybackCheckpoint
import link.roadio.riff.player.storage.PlaybackQueueKind
import link.roadio.riff.player.storage.PlaybackQueueOccurrence
import link.roadio.riff.player.storage.PlaybackQueueSnapshot
import link.roadio.riff.player.storage.PlayerStateStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

/** Production Bookmark rendering with committed spacing state in the disposable app only. */
class BookmarkSpacingInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bookmarkButtonTracksBothBoundariesAndCannotActivateWhileDisabledAcrossThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "bookmark-spacing").toFile()
        try {
            PlayerStateStore(directory).use { store ->
                val id = MediaId(11L)
                val queue =
                    PlaybackQueueSnapshot(
                        1L,
                        PlaybackQueueKind.MANUAL,
                        1,
                        false,
                        listOf(PlaybackQueueOccurrence(1L, 0, 0, id)),
                        PlaybackCheckpoint(1L),
                        contextKey = "album:spacing",
                        bookTitleContext = true,
                        bookTitleMembers = listOf(id),
                        bookQueueOrdinals = listOf(0),
                    )
                store.markBook("album:spacing", "Fixture", listOf(id), 0, 30_000_000L, "One", 90_000_000L)
                var state by mutableStateOf(
                    PlayerUiState(
                        screen = PlayerScreen.PLAYBACK,
                        root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
                        nowPlaying =
                            NowPlayingUiState(
                                mediaId = id,
                                title = "Fixture",
                                bookTitleContext = true,
                                aiReactionEligible = false,
                            ),
                    ),
                )
                val actions = mutableListOf<PlayerUiAction>()
                compose.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        PlayerComposeUi(state, UiConfig.defaults(context), CalibrationSessionState(), context, dispatch = actions::add)
                    }
                }
                var stableBounds: androidx.compose.ui.geometry.Rect? = null
                for (theme in listOf(ColorScheme.DAY, ColorScheme.NIGHT)) {
                    for (position in listOf(15_000_000L, 15_000_001L, 30_000_000L, 44_999_999L, 45_000_000L)) {
                        val allowed = store.canMarkBook(queue, 0, id, position, 90_000_000L)
                        compose.runOnIdle {
                            state =
                                state.copy(bookmarkMarkAllowed = allowed, settings = state.settings.copy(colorScheme = theme))
                        }
                        val node = compose.onNodeWithContentDescription("Bookmark current position" + if (allowed) "" else "; unavailable")
                        val bounds = node.fetchSemanticsNode().boundsInRoot
                        stableBounds?.let { assertEquals(it, bounds) }
                        stableBounds = bounds
                        val before = actions.size
                        if (allowed) {
                            node.assertIsEnabled().performClick()
                            assertEquals(PlayerUiAction.MarkBookmark, actions.last())
                            assertEquals(before + 1, actions.size)
                        } else {
                            node.assertIsNotEnabled().performTouchInput { click(center) }
                            assertEquals(before, actions.size)
                        }
                    }
                }
                assertEquals(
                    1,
                    store
                        .listPlaylists()
                        .single()
                        .marks.size,
                )
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
