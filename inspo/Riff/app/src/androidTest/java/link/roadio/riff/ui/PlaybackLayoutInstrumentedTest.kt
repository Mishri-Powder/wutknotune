package link.roadio.riff.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real local screens inside bounded synthetic windows; only the disposable app is touched. */
class PlaybackLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private var width by mutableStateOf(600f)
    private var height by mutableStateOf(1400f)
    private var fontScale by mutableStateOf(1f)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private var config by mutableStateOf(UiConfig.fromUserText("{}"))
    private var state by mutableStateOf(
        PlayerUiState(
            screen = PlayerScreen.PLAYBACK,
            root =
                BrowserItem(
                    "root",
                    "Fixture",
                    BrowserKind.ROOT,
                    children =
                        HierarchyRoot.entries.map { BrowserItem(it.categoryId, it.label, BrowserKind.CATEGORY, it.icon) } +
                            listOf(
                                BrowserItem("category:books", "Books", BrowserKind.CATEGORY, "audiobook"),
                                BrowserItem("category:ai", "AI", BrowserKind.AI, "ai"),
                            ),
                ),
            nowPlaying =
                NowPlayingUiState(
                    mediaId = MediaId(123),
                    title = "Playback fixture",
                    artist = "Artist",
                    album = "Album",
                    aiReactionEligible = true,
                ),
            indexerStatus = "Indexing fixture",
        ),
    )
    private val actions = mutableListOf<PlayerUiAction>()
    private var density = 1f

    private fun install() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            // Synthetic windows live inside the real safe area; consume real bars once at its boundary.
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
            ) {
                density = minOf(1f, constraints.maxWidth / 1400f, constraints.maxHeight / 1650f)
                CompositionLocalProvider(
                    LocalDensity provides Density(density, fontScale),
                    LocalLayoutDirection provides direction,
                ) {
                    Box(Modifier.size(width.dp, height.dp)) {
                        PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = actions::add)
                    }
                }
            }
        }
    }

    private fun bounds(label: String): Rect = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot

    private fun tagged(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertStatusCorner() {
        val viewport = tagged("status:viewport")
        val status = tagged("status-line")
        assertEquals(viewport.left, status.left, 1f)
        assertEquals(viewport.top, status.top, 1f)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "playback-layout").apply { mkdirs() }
        File(output, "$name.png").outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun aspectRowsKeepEqualHeightsSafeBottomAndZeroGapStatus() {
        install()
        // Include WIDE portrait and NARROW landscape: aspect never changes token resolution.
        for ((w, h) in listOf(600f to 1400f, 1200f to 800f, 900f to 1400f, 700f to 600f)) {
            for (rtl in listOf(false, true)) {
                compose.runOnIdle {
                    width = w
                    height = h
                    fontScale = if (rtl) 2f else 1f
                    direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    state = state.copy(settings = state.settings.copy(colorScheme = if (rtl) ColorScheme.NIGHT else ColorScheme.DAY))
                }
                val safe = tagged("playback:safe")
                val previous = bounds("Previous")
                val next = bounds("Next")
                val shuffle = bounds("Shuffle off")
                val play = bounds("Play")
                val repeat = bounds("Repeat off")
                val ban = bounds("Ban current track")
                val dislike = bounds("Dislike current track")
                val like = bounds("Like current track")
                val seek = tagged("playback:seek")
                listOf(previous, next, shuffle, play, repeat, ban, dislike, like).forEach {
                    assertEquals("Full target height: $it safe=$safe viewport=${w}x$h rtl=$rtl", 120f * density, it.height, 1.1f)
                    assertTrue("Safe horizontal bounds: $safe $it", it.left >= safe.left - 1f && it.right <= safe.right + 1f)
                }
                assertEquals(safe.bottom, ban.bottom, 1f)
                assertEquals(ban.top, dislike.top, 1f)
                assertEquals(ban.top, like.top, 1f)
                assertEquals(seek.bottom, ban.top, 1f)
                assertEquals(64f * density, seek.height, 1f)
                assertEquals(play.bottom, seek.top, 1f)
                assertEquals(previous.top, next.top, 1f)
                if (h > w) {
                    assertEquals(previous.width, next.width, 1f)
                    assertEquals(24f * density, shuffle.top - previous.bottom, 1.1f)
                    assertEquals(shuffle.width, play.width, 1f)
                    assertEquals(play.width, repeat.width, 1f)
                } else {
                    assertEquals(shuffle.top, previous.top, 1f)
                    assertEquals(previous.top, play.top, 1f)
                    assertEquals(play.top, repeat.top, 1f)
                }
                assertStatusCorner()
                assertTrue(if (rtl) previous.left > next.left else previous.left < next.left)
                assertTrue(actions.isEmpty())
                if (!rtl && (w >= 900f || w == 600f)) capture("${w.toInt()}x${h.toInt()}")
            }
        }
    }

    @Test
    fun transportCommandsAndAudiobookSlotsSurviveRepacking() {
        install()
        for ((w, h) in listOf(600f to 1400f, 1200f to 800f)) {
            compose.runOnIdle {
                width = w
                height = h
                actions.clear()
            }
            listOf("Previous", "Next", "Shuffle off", "Play", "Repeat off").forEach {
                compose.onNodeWithContentDescription(it).performTouchInput { click() }
            }
            compose.runOnIdle {
                assertEquals(
                    listOf(
                        PlayerUiAction.Previous,
                        PlayerUiAction.Next,
                        PlayerUiAction.ToggleShuffle,
                        PlayerUiAction.TogglePlayback,
                        PlayerUiAction.ToggleRepeat,
                    ),
                    actions,
                )
                actions.clear()
            }
            compose.onNodeWithTag("playback:seek").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.5f)) }
            compose.runOnIdle {
                assertEquals(listOf(PlayerUiAction.Seek(0.5f)), actions)
                actions.clear()
            }
            val ban = bounds("Ban current track")
            val like = bounds("Like current track")
            compose.runOnIdle {
                state =
                    state.copy(
                        contentMode = ContentMode.AUDIOBOOK,
                        bookmarkMarkAllowed = true,
                        nowPlaying = state.nowPlaying.copy(effectiveBook = true, bookTitleContext = true, aiReactionEligible = false),
                    )
            }
            assertEquals(ban, bounds("Audiobook speed 1.0x; change to 1.1x"))
            assertEquals(like, bounds("Bookmark current position"))
            compose.onNodeWithContentDescription("Dislike current track").assertDoesNotExist()
            compose.runOnIdle {
                state =
                    state.copy(
                        contentMode = ContentMode.MUSIC,
                        nowPlaying = state.nowPlaying.copy(effectiveBook = false, bookTitleContext = false, aiReactionEligible = true),
                    )
            }
        }
    }

    @Test
    fun statusIsNonsemanticPassesTouchesAndReservesNoSpaceOnEitherScreen() {
        fontScale = 2f
        direction = LayoutDirection.Rtl
        install()
        for (screen in listOf(PlayerScreen.PLAYBACK, PlayerScreen.MAIN)) {
            compose.runOnIdle {
                state = state.copy(screen = screen)
                actions.clear()
            }
            val status = tagged("status-line")
            val node = compose.onNodeWithTag("status-line").fetchSemanticsNode()
            listOf(
                SemanticsProperties.Text,
                SemanticsProperties.ContentDescription,
                SemanticsProperties.Focused,
                SemanticsProperties.LiveRegion,
            ).forEach {
                assertFalse("Decorative status has no $it", node.config.contains(it))
            }
            assertFalse(node.config.contains(SemanticsActions.OnClick))
            val value = if (screen == PlayerScreen.PLAYBACK) playbackStatusLine(state.playbackDiagnostics) else mainStatusLine(state)
            compose.onAllNodesWithText(value).assertCountEquals(0)
            compose.onAllNodesWithContentDescription(value).assertCountEquals(0)
            val before = if (screen == PlayerScreen.PLAYBACK) bounds("Play") else bounds("Albums")
            val viewport = if (screen == PlayerScreen.MAIN) tagged("main:grid-viewport") else tagged("playback:safe")
            assertStatusCorner()
            val point = Offset(status.left + 2f, status.center.y)
            if (screen == PlayerScreen.MAIN) capture("main-status")
            compose.onRoot().performTouchInput { click(point) }
            compose.runOnIdle {
                assertEquals(listOf(PlayerUiAction.TogglePlaybackSurface), actions)
                actions.clear()
            }
            compose.runOnIdle { config = UiConfig.fromUserText("""{"skins":{"default":{"behavior":{"enableStatusLines":false}}}}""") }
            compose.onNodeWithTag("status-line").assertDoesNotExist()
            assertEquals(before, if (screen == PlayerScreen.PLAYBACK) bounds("Play") else bounds("Albums"))
            assertEquals(viewport, if (screen == PlayerScreen.MAIN) tagged("main:grid-viewport") else tagged("playback:safe"))
            compose.runOnIdle {
                config = UiConfig.fromUserText("{}")
                state = state.copy(indexerStatus = "")
            }
        }
    }

    @Test
    fun opaqueArtworkPaintsAboveStatusAndShortWindowsRetainReachableTargets() {
        width = 1200f
        height = 800f
        config = UiConfig.fromUserText("""{"skins":{"default":{"behavior":{"artworkAlpha":255}}}}""")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        state =
            state.copy(
                nowPlaying = state.nowPlaying.copy(artwork = ArtworkUiState(bitmap = bitmap)),
                playbackDiagnostics = PlaybackDiagnosticsUiState(genre = PlaybackGenreDiagnostic(0.9f, "Reggae ".repeat(20))),
            )
        install()
        val before = compose.onRoot().captureToImage().toPixelMap()
        val statusBounds = tagged("status-line")
        compose.runOnIdle {
            config =
                UiConfig.fromUserText("""{"skins":{"default":{"behavior":{"artworkAlpha":255,"enableStatusLines":false}}}}""")
        }
        val after = compose.onRoot().captureToImage().toPixelMap()
        val safe = tagged("status:viewport")
        var visibleStatusPixels = 0
        for (y in statusBounds.top.toInt() until statusBounds.bottom.toInt()) {
            for (x in safe.left.toInt() until (width * density * 0.25f).toInt()) {
                if (before[x, y] != after[x, y]) visibleStatusPixels++
            }
        }
        assertTrue("Status must render in the exposed corner", visibleStatusPixels > 20)
        for (y in safe.top.toInt() until safe.bottom.toInt()) {
            for (x in (width * density * 0.6f).toInt() until safe.right.toInt()) {
                assertEquals("Status must remain below opaque artwork", before[x, y], after[x, y])
            }
        }
        compose.runOnIdle {
            width = 600f
            height = 450f
            config = UiConfig.fromUserText("{}")
        }
        // Deliberate overflow keeps bottom controls at full height and permits reaching earlier ones.
        assertEquals(120f * density, bounds("Ban current track").height, 1.1f)
        val fixedStatus = tagged("status-line")
        compose.onNodeWithContentDescription("Switch to library").performScrollTo().assertIsDisplayed()
        assertEquals(fixedStatus, tagged("status-line"))
        assertStatusCorner()
        compose.onNodeWithContentDescription("Previous").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerUiAction.Previous), actions) }
    }
}
