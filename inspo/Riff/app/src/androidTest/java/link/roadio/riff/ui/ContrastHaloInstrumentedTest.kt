package link.roadio.riff.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.selection.AiHeatmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

/** Render real production painters/controls in the disposable app; no working media or preferences. */
class ContrastHaloInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun adoptedIconsKeepTheirCoresAndBoundsWithContrastingDenseSurround() {
        var halo by mutableStateOf(false)
        var night by mutableStateOf(false)
        var drawable by mutableStateOf("ic_play")
        var foreground by mutableStateOf(Color(0xff152536))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(
                    Modifier.size(300.dp).background(if (night) Color.White else Color.Black).testTag("fixture"),
                    contentAlignment = Alignment.Center,
                ) {
                    RiffIcon(
                        drawable,
                        foreground,
                        modifier =
                            Modifier.size(80.dp).testTag("icon").contrastHalo(
                                if (halo) {
                                    if (night) Color.Black else Color.White
                                } else {
                                    null
                                },
                            ),
                    )
                }
            }
        }
        val icons =
            listOf(
                "ic_shuffle",
                "ic_rew",
                "ic_play",
                "ic_pause",
                "ic_fwd",
                "ic_repeat",
                "ic_like",
                "ic_like_filled",
                "ic_dislike",
                "ic_dislike_filled",
                "ic_ban",
                "ic_ban_filled",
                "ic_bookmark_add",
            )
        for (dark in listOf(false, true)) {
            icons.forEachIndexed { index, icon ->
                compose.runOnIdle {
                    night = dark
                    drawable = icon
                    foreground =
                        Color(
                            if (dark) {
                                listOf(0xffdee5ed, 0xff9abcd8, 0xffadb9c7)[index % 3]
                            } else {
                                listOf(0xff152536, 0xff174b75, 0xff44566a)[index % 3]
                            },
                        )
                    halo = false
                }
                val bounds = compose.onNodeWithTag("icon").fetchSemanticsNode().boundsInRoot
                val before = compose.onNodeWithTag("fixture").captureToImage().toPixelMap()
                compose.runOnIdle { halo = true }
                val after = compose.onNodeWithTag("fixture").captureToImage().toPixelMap()
                assertEquals(bounds, compose.onNodeWithTag("icon").fetchSemanticsNode().boundsInRoot)
                val background = if (dark) Color.White else Color.Black
                var cores = 0
                var protectedEdges = 0
                var blurPixels = 0
                for (y in 2 until after.height - 2) {
                    for (x in 2 until after.width - 2) {
                        if (before[x, y] == foreground) {
                            assertTrue("Changed core: $icon night=$dark", difference(after[x, y], foreground) <= 4f / 255f)
                            cores++
                            listOf(
                                x - 1 to y,
                                x + 1 to y,
                                x to y - 1,
                                x to y + 1,
                                x - 2 to y,
                                x + 2 to y,
                                x to y - 2,
                                x to y + 2,
                            ).forEach { (nx, ny) ->
                                if (before[nx, ny] == background) {
                                    assertTrue("Dense edge contrast: $icon night=$dark", contrast(foreground, after[nx, ny]) >= 4.5)
                                    protectedEdges++
                                }
                            }
                        }
                        if (x < bounds.left - 4 || x > bounds.right + 4 || y < bounds.top - 4 || y > bounds.bottom + 4) {
                            if (difference(before[x, y], after[x, y]) > 0.005f) blurPixels++
                        }
                    }
                }
                assertTrue("Opaque cores: $icon", cores > 50)
                assertTrue("Dense adjacent surround: $icon edges=$protectedEdges", protectedEdges > 5)
                assertTrue("Broad blur beyond contour: $icon pixels=$blurPixels", blurPixels > 50)
                assertEquals("No plate", before[0, 0], after[0, 0])
            }
        }
    }

    @Test
    fun productionPlaybackKeepsActionsDisabledStatesAndSeekAcrossThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.PLAYBACK,
                root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
                nowPlaying = NowPlayingUiState(mediaId = MediaId(123), title = "Fixture", aiReactionEligible = true),
            ),
        )
        val actions = mutableListOf<PlayerUiAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = actions::add)
            }
        }
        val seek = compose.onNodeWithContentDescription("Playback position")
        val seekBounds = seek.fetchSemanticsNode().boundsInRoot
        for (dark in listOf(false, true)) {
            compose.runOnIdle {
                state =
                    state.copy(settings = state.settings.copy(colorScheme = if (dark) ColorScheme.NIGHT else ColorScheme.DAY))
            }
            compose.onNodeWithContentDescription("Like current track").assertIsEnabled().performClick()
            seek.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.75f)) }
            compose.runOnIdle {
                state =
                    state.copy(nowPlaying = state.nowPlaying.copy(positionFraction = 0.75f, shuffle = true, repeat = true))
            }
            assertEquals(seekBounds, seek.fetchSemanticsNode().boundsInRoot)
            assertTrue(actions.contains(PlayerUiAction.Seek(0.75f)))
            seek.performTouchInput { click(center) }
            compose.runOnIdle {
                state =
                    state.copy(
                        nowPlaying = state.nowPlaying.copy(bookTitleContext = true),
                        bookmarkMarkAllowed = true,
                    )
            }
            compose.onNodeWithContentDescription("Bookmark current position").assertIsEnabled().performClick()
            assertTrue(actions.contains(PlayerUiAction.MarkBookmark))
            compose.runOnIdle { state = state.copy(bookmarkMarkAllowed = false) }
            compose.onNodeWithContentDescription("Bookmark current position; unavailable").assertIsNotEnabled()
            compose.runOnIdle { state = state.copy(nowPlaying = state.nowPlaying.copy(bookTitleContext = false)) }
        }
        assertTrue(actions.filterIsInstance<PlayerUiAction.Seek>().size >= 4)
    }

    @Test
    fun aiOverlaysHaveOpaqueCoresAndRemainPartOfOneCanvasSemantics() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        val heatmap = AiHeatmap.fromCounts(1, 1, intArrayOf(256))
        var night by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(
                    PlayerUiState(
                        screen = PlayerScreen.AI,
                        root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
                        nowPlaying = NowPlayingUiState(),
                        settings = PlayerSettingsUiState(colorScheme = if (night) ColorScheme.NIGHT else ColorScheme.DAY),
                        aiDataAvailable = true,
                        aiHeatmap = heatmap,
                    ),
                    config,
                    CalibrationSessionState(),
                    context,
                    dispatch = {},
                )
            }
        }
        val directory = File(context.filesDir, "halo-fixtures").also { it.mkdirs() }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night = dark }
            val node = compose.onNodeWithContentDescription("Arousal 0.00; Valence 0.00; AI can make mistakes.")
            compose
                .onAllNodesWithContentDescription("Arousal 0.00; Valence 0.00; AI can make mistakes.")
                .assertCountEquals(1)
            compose.waitUntil(timeoutMillis = 10_000) {
                val image = node.captureToImage().toPixelMap()
                val sample = image[image.width / 4, image.height / 4]
                abs(sample.red - sample.green) > 0.1f
            }
            val pixels = node.captureToImage().toPixelMap()
            val foreground = if (dark) Color.White else Color.Black
            val surround = if (dark) Color.Black else Color.White
            var cores = 0
            var adjacent = 0
            for (y in 1 until pixels.height - 1) {
                for (x in 1 until pixels.width - 1) {
                    if (pixels[x, y] == foreground) {
                        cores++
                        if (difference(pixels[x + 1, y], surround) < 0.05f) adjacent++
                    }
                }
            }
            assertTrue("AI opaque text cores night=$dark: $cores", cores > 20)
            assertTrue("AI dense surround night=$dark: $adjacent", adjacent > 5)
            assertEquals(21.0, contrast(foreground, surround), 0.001)
            val name = "AI_${if (dark) "NIGHT" else "DAY"}.png"
            File(directory, name).outputStream().use {
                compose
                    .onRoot()
                    .captureToImage()
                    .asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun difference(
        a: Color,
        b: Color,
    ) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        fun luminance(c: Color): Double {
            fun linear(v: Float) = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            return 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
        }
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
