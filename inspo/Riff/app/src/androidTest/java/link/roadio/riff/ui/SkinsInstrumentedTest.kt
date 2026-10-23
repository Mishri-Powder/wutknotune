package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic configuration/UI only: never reads or modifies the user's working Beta. */
class SkinsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun namedSkinResolvesWidthAndThemeWithoutChangingGeometryOrNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config =
            UiConfig.fromUserText(
                """{
              "activeSkin":"test",
              "skins":{"test":{
                "geometry":{"navTarget":{"wide":100,"narrow":84},"topBarHeight":{"wide":100,"narrow":84}},
                "typography":{"topBarFontFamily":{"day":"serif","night":"monospace"}}
              }}}
            """,
            )
        var density by mutableStateOf(1f)
        var night by mutableStateOf(false)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        val actions = mutableListOf<PlayerUiAction>()
        compose.setContent {
            val settings = PlayerSettingsUiState(colorScheme = if (night) ColorScheme.NIGHT else ColorScheme.DAY)
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale = 2f),
                LocalLayoutDirection provides direction,
            ) {
                PlayerComposeUi(
                    PlayerUiState(
                        screen = PlayerScreen.SETTINGS,
                        root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
                        nowPlaying = NowPlayingUiState(),
                        settings = settings,
                        settingsDraft = settings,
                    ),
                    config,
                    CalibrationSessionState(),
                    context,
                    dispatch = actions::add,
                )
            }
        }
        listOf(1f, 3f).forEach { scale ->
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layout ->
                compose.runOnIdle {
                    density = scale
                    direction = layout
                    night = false
                }
                val day = compose.onNodeWithContentDescription("Save settings").fetchSemanticsNode().boundsInRoot
                val rootWidth =
                    compose
                        .onRoot()
                        .fetchSemanticsNode()
                        .boundsInRoot.width / scale
                val target = if (rootWidth >= 800) 100f else 84f
                assertEquals("scale=$scale direction=$layout rootDp=$rootWidth bounds=$day", target * scale, day.width, 1f)
                assertEquals(target * scale, day.height, 1f)
                compose.runOnIdle { night = true }
                val dark = compose.onNodeWithContentDescription("Save settings").fetchSemanticsNode().boundsInRoot
                assertEquals(day, dark)
            }
        }
        compose.runOnIdle {
            density = 1f
            direction = LayoutDirection.Ltr
            night = false
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText("Settings", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(
            FontFamily.Serif,
            layouts
                .last()
                .layoutInput.style.fontFamily,
        )
        compose.runOnIdle { night = true }
        layouts.clear()
        compose
            .onNodeWithText("Settings", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(
            FontFamily.Monospace,
            layouts
                .last()
                .layoutInput.style.fontFamily,
        )
        assertTrue(actions.none { it == PlayerUiAction.SaveSettings || it == PlayerUiAction.OpenMain })
    }

    @Test
    fun compiledDefaultsAreSafeToReadFromMainWithoutOpeningConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            val config = UiConfig.defaults(context)
            assertEquals("default", config.activeSkin)
            assertEquals(20, config.int("behavior.statusLineTextSp"))
            assertEquals(32, config.int("ai.heatmapArousalCells"))
        }
    }
}
