package link.roadio.riff.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Synthetic pixels and labels only; no access to the working Player, library or playback. */
class GlyphBackdropTextInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun backdropPaintsBeyondGlyphsInBothPolaritiesWithoutChangingTextOrBounds() {
        var protected by mutableStateOf(false)
        var night by mutableStateOf(true)
        var status by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var label by mutableStateOf("MMMM IIII")
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                Box(
                    Modifier.size(600.dp, 240.dp).background(if (night) Color.White else Color.Black).testTag("fixture"),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphBackdropText(
                        value = label,
                        style =
                            TextStyle(
                                color = if (night) Color(0xffdee5ed) else Color(0xff152536),
                                fontSize = if (status) 20.sp else 44.sp,
                                fontFamily = if (status) FontFamily.Monospace else FontFamily.Default,
                            ),
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 40.dp).testTag("label").semantics {
                                contentDescription = label
                            },
                        backdropColor =
                            if (protected) {
                                if (night) Color.Black else Color.White
                            } else {
                                null
                            },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        listOf(true, false).forEach { dark ->
            listOf(false, true).forEach { diagnostic ->
                compose.runOnIdle {
                    night = dark
                    status = diagnostic
                    protected = false
                }
                val bounds = compose.onNodeWithTag("label").fetchSemanticsNode().boundsInRoot
                val before = compose.onNodeWithTag("fixture").captureToImage().toPixelMap()
                compose.runOnIdle { protected = true }
                val after = compose.onNodeWithTag("fixture").captureToImage().toPixelMap()
                assertEquals(bounds, compose.onNodeWithTag("label").fetchSemanticsNode().boundsInRoot)
                compose.onAllNodesWithContentDescription(label, useUnmergedTree = true).assertCountEquals(1)
                var changed = 0
                var preservedForeground = 0
                val foreground = if (dark) Color(0xffdee5ed) else Color(0xff152536)
                for (y in 0 until after.height) {
                    for (x in 0 until after.width) {
                        if (before[x, y] == foreground) {
                            // Antialiased edges can round to the foreground over the plain background.
                            // The halo changes those edge pixels, while solid glyph cores retain the color.
                            assertEquals("Foreground red", foreground.red, after[x, y].red, 4f / 255f)
                            assertEquals("Foreground green", foreground.green, after[x, y].green, 4f / 255f)
                            assertEquals("Foreground blue", foreground.blue, after[x, y].blue, 4f / 255f)
                            preservedForeground++
                        }
                        val difference = after[x, y].red - before[x, y].red
                        if (abs(difference) > 0.08f) {
                            assertTrue("Wrong shadow polarity", if (dark) difference < 0 else difference > 0)
                            changed++
                        }
                    }
                }
                assertTrue("Backdrop must be visibly painted: night=$dark status=$diagnostic pixels=$changed", changed > 500)
                assertTrue("Foreground glyph cores must exist", preservedForeground > 20)
                assertEquals("No full-area plate", before[0, 0], after[0, 0])
            }
        }
        compose.runOnIdle {
            label = "Испытание — العربية — very long chapter title with a complete accessible label"
            fontScale = 2f
            direction = LayoutDirection.Rtl
            protected = false
        }

        fun layout(): TextLayoutResult {
            val result = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
            return result.single()
        }
        val before = layout()
        val bounds = compose.onNodeWithTag("label").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { protected = true }
        val after = layout()
        assertTrue("Fixture exercises ellipsis", before.isLineEllipsized(0))
        assertEquals(before.size, after.size)
        assertEquals(before.getLineEnd(0), after.getLineEnd(0))
        assertEquals(bounds, compose.onNodeWithTag("label").fetchSemanticsNode().boundsInRoot)
        compose.onAllNodesWithContentDescription(label, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun playbackExamplesCoverBrightDarkAndPatternedArtworkInBothThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        var night by mutableStateOf(false)
        var artwork by mutableStateOf(fixtureArtwork("white"))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(
                    PlayerUiState(
                        screen = PlayerScreen.PLAYBACK,
                        root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
                        settings = PlayerSettingsUiState(colorScheme = if (night) ColorScheme.NIGHT else ColorScheme.DAY),
                        nowPlaying =
                            NowPlayingUiState(
                                mediaId = MediaId(123),
                                aiReactionEligible = true,
                                shuffle = true,
                                positionFraction = 0.4f,
                                title = "The Beatles — Across the Universe",
                                artist = "The Beatles / Abbey Road sessions",
                                album = "Revolver · Deluxe Edition",
                                chapter = "1. Испытание, 3:21",
                                artwork = ArtworkUiState(bitmap = artwork),
                            ),
                        playbackDiagnostics =
                            PlaybackDiagnosticsUiState(
                                arousal = 0.7f,
                                valence = 0.8f,
                                pte = 0.6f,
                                replayGainDb = -6.1f,
                                genre = PlaybackGenreDiagnostic(0.9f, "Electronic music / Progressive rock"),
                            ),
                    ),
                    config,
                    CalibrationSessionState(),
                    context,
                    dispatch = {},
                )
            }
        }
        val directory = File(context.filesDir, "artwork-text-fixtures").also { it.mkdirs() }
        listOf(false, true).forEach { dark ->
            listOf("white", "black", "pattern").forEach { kind ->
                compose.runOnIdle {
                    night = dark
                    artwork = fixtureArtwork(kind)
                }
                compose.onAllNodesWithContentDescription("The Beatles — Across the Universe", useUnmergedTree = true).assertCountEquals(1)
                val name = "PLAY_${if (dark) "NIGHT" else "DAY"}_$kind.png"
                File(directory, name).outputStream().use {
                    compose
                        .onRoot()
                        .captureToImage()
                        .asAndroidBitmap()
                        .compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    private fun fixtureArtwork(kind: String): Bitmap =
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(
                        x,
                        y,
                        when (kind) {
                            "white" -> android.graphics.Color.WHITE
                            "black" -> android.graphics.Color.BLACK
                            else -> if ((x / 16 + y / 16) % 2 == 0) android.graphics.Color.WHITE else android.graphics.Color.BLUE
                        },
                    )
                }
            }
        }
}
