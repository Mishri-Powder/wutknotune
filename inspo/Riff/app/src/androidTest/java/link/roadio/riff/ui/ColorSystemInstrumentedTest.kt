package link.roadio.riff.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.player.core.settings.PlayerDimBacklightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

/** Production adapters and disposable synthetic state; no working media, grants or preferences. */
class ColorSystemInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sharedStateOutlinesRemainContrastingOnOppositeArtworkExtremes() {
        val defaults = UiConfig.defaults(context)
        var night by mutableStateOf(false)
        compose.setContent {
            val config = defaults.themed(night)
            val palette = if (night) UiPalette.night(config) else UiPalette.day(config)
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalRiffConfig provides config,
                LocalRiffPalette provides palette,
                LocalRiffUncontrolledBackground provides true,
            ) {
                Box(
                    Modifier.size(320.dp).background(if (night) Color.White else Color.Black).testTag("fixture"),
                    contentAlignment = Alignment.Center,
                ) {
                    RiffStateOutline(Modifier.size(240.dp), Color(palette.focusRing), RoundedCornerShape(12.dp), 3.dp)
                    SelectedControlCircle(Modifier.size(80.dp), enabled = true)
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night = dark }
            val palette = if (dark) UiPalette.night(defaults) else UiPalette.day(defaults)
            val image = compose.onNodeWithTag("fixture").captureToImage().toPixelMap()
            val foreground = Color(palette.focusRing)
            var cores = 0
            var edges = 0
            for (y in 3 until image.height - 3) {
                for (x in 3 until image.width - 3) {
                    if (close(image[x, y], foreground)) {
                        cores++
                        if (listOf(x - 2 to y, x + 2 to y, x to y - 2, x to y + 2).any { (nx, ny) ->
                                contrast(foreground, image[nx, ny]) >=
                                    4.5
                            }
                        ) {
                            edges++
                        }
                    }
                }
            }
            assertTrue("outline cores night=$dark", cores > 200)
            assertTrue("protected outline edges night=$dark: $edges", edges > 100)
            save("STATE_${if (dark) "NIGHT" else "DAY"}")
        }
    }

    @Test
    fun dimOffUsesExactBanMaskWithReadableInactiveTintAndFocus() {
        val config = UiConfig.defaults(context)
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.SETTINGS_MORE,
                root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
                nowPlaying = NowPlayingUiState(),
                settings = PlayerSettingsUiState(colorScheme = ColorScheme.DAY),
                moreSettingsDraft = PlayerSettingsUiState(dimBacklight = PlayerDimBacklightMode.OFF),
            ),
        )
        val actions = mutableListOf<PlayerUiAction>()
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = actions::add)
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle {
                state =
                    state.copy(settings = state.settings.copy(colorScheme = if (dark) ColorScheme.NIGHT else ColorScheme.DAY))
            }
            compose.onNodeWithTag("selector:list").performScrollToIndex(moreSettingsItems().indexOf(SettingsItem.DIM_BACKLIGHT))
            val button = compose.onNodeWithContentDescription("Dim Backlight: Off; next Day")
            compose.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
            button.assertIsEnabled().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            button.assertIsFocused()
            val pixels = button.captureToImage().toPixelMap()
            val palette = if (dark) UiPalette.night(config) else UiPalette.day(config)
            val expected = Bitmap.createBitmap(pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
            val drawable = UiDrawableRegistry.drawable(context, "ic_ban")
            drawable.setTint(palette.inactiveIcon)
            drawable.setBounds(10, 10, pixels.width - 10, pixels.height - 10)
            drawable.draw(Canvas(expected))
            var checked = 0
            var matched = 0
            for (y in 1 until pixels.height - 1) {
                for (x in 1 until pixels.width - 1) {
                    if (expected.getPixel(x, y) == palette.inactiveIcon &&
                        expected.getPixel(x + 1, y) == palette.inactiveIcon &&
                        expected.getPixel(x, y + 1) == palette.inactiveIcon
                    ) {
                        checked++
                        if (close(pixels[x, y], Color(palette.inactiveIcon))) matched++
                    }
                }
            }
            assertTrue("exact adopted Ban mask night=$dark checked=$checked matched=$matched", checked > 100 && matched >= checked * .98)
            button.performClick()
            assertTrue(actions.contains(PlayerUiAction.SetDraftDimBacklight(PlayerDimBacklightMode.DAY)))
            save("DIM_${if (dark) "NIGHT" else "DAY"}")
        }
    }

    @Test
    fun availableBanIsReadableDuringAzAndUnavailableBodyKeepsIndependentReactions() {
        val config = UiConfig.defaults(context)
        val rows =
            listOf(
                BrowserItem("banned", "Banned fixture", BrowserKind.TRACK, playable = true),
                BrowserItem("missing", "Missing fixture", BrowserKind.TRACK, enabled = false),
            )
        var state by mutableStateOf(
            PlayerUiState(
                screen = PlayerScreen.HIERARCHY,
                root = BrowserItem("root", "Fixture", BrowserKind.FOLDER, children = rows),
                nowPlaying = NowPlayingUiState(),
                settings = PlayerSettingsUiState(colorScheme = ColorScheme.DAY),
                reactions = mapOf("banned" to ReactionState(ownBanned = true)),
            ),
        )
        val actions = mutableListOf<PlayerUiAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = actions::add)
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle {
                state =
                    state.copy(
                        screen = PlayerScreen.HIERARCHY,
                        settings = state.settings.copy(colorScheme = if (dark) ColorScheme.NIGHT else ColorScheme.DAY),
                    )
            }
            compose.onNodeWithText("Banned fixture").assertIsEnabled().performClick()
            compose.onNodeWithText("Missing fixture").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Like Missing fixture").assertIsEnabled().performClick()
            assertTrue(actions.any { it is PlayerUiAction.SetReaction && it.itemId == "missing" })
            val palette = if (dark) UiPalette.night(config) else UiPalette.day(config)
            val ordinary = compose.onNodeWithText("Banned fixture").captureToImage().toPixelMap()
            assertTrue(count(ordinary, Color(palette.suppressed)) > 100)
            compose.runOnIdle {
                state =
                    state.copy(
                        screen = PlayerScreen.AZ_SEARCH,
                        azDraftSteps = emptyList(),
                        azSnapshot = AzPreparedSection(rows).snapshot(AzSearchQuery(rows, emptyList(), emptyList())),
                    )
            }
            val preview = compose.onNodeWithText("Banned fixture").captureToImage().toPixelMap()
            val original = Color(palette.suppressed)
            val subtract = config.int("behavior.azRgbSubtract") / 255f
            val adjusted =
                Color(
                    (original.red - subtract).coerceAtLeast(0f),
                    (original.green - subtract).coerceAtLeast(0f),
                    (original.blue - subtract).coerceAtLeast(0f),
                    original.alpha,
                )
            assertTrue("A-Z must apply the common subtraction to operable text night=$dark", count(preview, adjusted) > 100)
            compose.onNodeWithText("Banned fixture").assertIsEnabled()
            save("AZ_${if (dark) "NIGHT" else "DAY"}")
        }
    }

    @Test
    fun calibrationThemeChangesPaintWithoutChangingRawCandidate() {
        val config = UiConfig.defaults(context)
        val session = CalibrationSessionState()
        var night by mutableStateOf(false)
        compose.setContent {
            PlayerComposeUi(
                PlayerUiState(
                    screen = PlayerScreen.CALIBRATION,
                    root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
                    nowPlaying = NowPlayingUiState(),
                    settings = PlayerSettingsUiState(colorScheme = if (night) ColorScheme.NIGHT else ColorScheme.DAY),
                ),
                config,
                session,
                context,
                dispatch = {},
            )
        }
        compose.waitForIdle()
        val candidate = listOf(session.candidateXPxPerMm, session.candidateYPxPerMm, session.initialWidth, session.initialHeight)
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night = dark }
            val palette = if (dark) UiPalette.night(config) else UiPalette.day(config)
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            assertTrue(count(pixels, Color(palette.background)) > 1000)
            assertTrue(count(pixels, Color(palette.surface)) > 1000)
            assertTrue(count(pixels, Color(palette.text)) > 100)
            // This guidance-line gap lies inside the initial CARD hatch, not the clear canvas.
            // Protecting it proves the actual paint stack, beyond validating nominal palette pairs.
            val gap = pixels[(pixels.width * .48f).toInt(), (pixels.height * .49f).toInt()]
            assertTrue("Sample decoration must not repaint the guidance plate", close(gap, Color(palette.focused)))
            assertTrue(contrast(Color(palette.text), gap) >= 7.0)
            assertEquals(
                candidate,
                listOf(session.candidateXPxPerMm, session.candidateYPxPerMm, session.initialWidth, session.initialHeight),
            )
            save("CALIBRATION_${if (dark) "NIGHT" else "DAY"}")
        }
    }

    private fun count(
        pixels: PixelMap,
        color: Color,
    ): Int = (0 until pixels.height).sumOf { y -> (0 until pixels.width).count { x -> close(pixels[x, y], color) } }

    private fun close(
        a: Color,
        b: Color,
    ): Boolean = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha)) < 3f / 255f

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        fun l(c: Color): Double =
            listOf(c.red to .2126, c.green to .7152, c.blue to .0722).sumOf { (v, w) ->
                w *
                    if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
            }
        val x = l(a)
        val y = l(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }

    private fun save(name: String) {
        val directory = File(context.filesDir, "color-fixtures").also { it.mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
