package link.roadio.riff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compare the actual preference with the existing adopted-icon action renderer. */
class BooleanIconRenderingInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wideIconsKeepExactRenderingAtLargeFontScaleInBothThemes() = exercise(wide = true)

    @Test
    fun narrowIconsKeepExactRenderingAtLargeFontScaleInBothThemes() = exercise(wide = false)

    private fun exercise(wide: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = UiConfig.defaults(context)
        val density = if (wide) 1f else narrowBooleanFixtureDensity()
        val widthDp = context.resources.displayMetrics.widthPixels / density
        var night by mutableStateOf(false)
        var value by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)
        compose.setContent {
            val config = base.resolved(widthDp, night)
            val palette = if (night) UiPalette.night(config) else UiPalette.day(config)
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalLayoutDirection provides if (night) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalRiffConfig provides config,
                LocalRiffPalette provides palette,
            ) {
                val target = settingsTarget(wide)
                Column(
                    Modifier.fillMaxSize().background(Color(palette.background)),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(target)) {
                        BooleanSettingRow("Preference", value, wide) {}
                    }
                    Row(
                        Modifier.fillMaxWidth().height(target),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        RiffIconButton(
                            icon = if (value) "ic_yes" else "ic_no",
                            label = "Reference",
                            selected = value,
                            wide = wide,
                            onClick = {},
                            modifier = Modifier.size(target),
                        )
                    }
                }
            }
        }
        listOf(false, true).forEach { dark ->
            listOf(false, true).forEach { selected ->
                compose.runOnIdle {
                    night = dark
                    value = selected
                    fontScale = 1f
                }
                val actual = compose.onNodeWithContentDescription("Preference: ${if (selected) "Yes" else "No"}; toggle", substring = true)
                val expected = compose.onNodeWithContentDescription("Reference", substring = true)
                val originalBounds = actual.fetchSemanticsNode().boundsInRoot
                val normal = actual.captureToImage()
                assertSamePixels(normal, expected.captureToImage())
                compose.runOnIdle { fontScale = 2f }
                assertEquals(originalBounds, actual.fetchSemanticsNode().boundsInRoot)
                assertSamePixels(normal, actual.captureToImage())
                assertSamePixels(actual.captureToImage(), expected.captureToImage())
                compose.onAllNodesWithText("Yes", useUnmergedTree = true).assertCountEquals(0)
                compose.onAllNodesWithText("No", useUnmergedTree = true).assertCountEquals(0)
            }
        }
    }

    private fun assertSamePixels(
        first: ImageBitmap,
        second: ImageBitmap,
    ) {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        repeat(first.height) { y ->
            repeat(first.width) { x -> assertEquals("Pixel ($x,$y)", a[x, y], b[x, y]) }
        }
    }
}
