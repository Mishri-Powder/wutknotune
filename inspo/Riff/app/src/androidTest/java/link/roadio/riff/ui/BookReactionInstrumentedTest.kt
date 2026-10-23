package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class BookReactionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun automaticBookHasNoHierarchyOrPlaybackReactionTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val track =
            BrowserItem(
                id = "track:1",
                label = "Automatic book",
                kind = BrowserKind.TRACK,
                playable = true,
                mediaId = MediaId(1L),
                automaticAudiobook = true,
                aiReactionEligible = false,
            )
        val album =
            BrowserItem(
                id = "album:1",
                label = "Automatic book",
                kind = BrowserKind.ALBUM,
                playable = true,
                automaticAudiobook = true,
                aiReactionEligible = false,
                children = listOf(track),
            )
        val albums =
            BrowserItem(
                id = HierarchyRoot.ALBUMS.categoryId,
                label = "Albums",
                kind = BrowserKind.CATEGORY,
                children = listOf(album),
            )
        var state by
            mutableStateOf(
                PlayerUiState(
                    screen = PlayerScreen.HIERARCHY,
                    root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(albums)),
                    nowPlaying = NowPlayingUiState(mediaId = MediaId(1L), title = track.label, aiReactionEligible = false),
                    browserPath = listOf(albums.id),
                    hierarchyRoot = HierarchyRoot.ALBUMS,
                ),
            )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(state, UiConfig.defaults(context), CalibrationSessionState(), context) {}
            }
        }

        compose.onNodeWithContentDescription("Ban Automatic book").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dislike Automatic book").assertDoesNotExist()
        compose.onNodeWithContentDescription("Like Automatic book").assertDoesNotExist()
        val titleBounds =
            compose
                .onNodeWithContentDescription("Automatic book; Albums; page 1 of 1")
                .fetchSemanticsNode()
                .boundsInRoot
        val moreBounds =
            compose
                .onNodeWithContentDescription("Actions for Automatic book")
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val titleToMoreGap = moreBounds.left - titleBounds.right
        assertTrue("title=$titleBounds more=$moreBounds gap=$titleToMoreGap", abs(titleToMoreGap - 24f) < 1f)

        compose.runOnIdle { state = state.copy(screen = PlayerScreen.PLAYBACK) }
        compose.onNodeWithContentDescription("Ban current track").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dislike current track").assertDoesNotExist()
        compose.onNodeWithContentDescription("Like current track").assertDoesNotExist()
    }
}
