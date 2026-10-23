package link.roadio.riff.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class SelectorAdjustmentInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fixedOffsetClampsAllByteValuesAfterCompositingAndRestoresOnDisable() {
        var enabled by mutableStateOf(false)
        var amount by mutableStateOf(32)
        val background = Color(64, 96, 128)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                Box(Modifier.size(256.dp, 272.dp).selectorAdjustment(enabled, background, amount).testTag("adjustment:fixture")) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(background)
                        val cell = Size(size.width / 16f, size.height / 17f)
                        repeat(272) { index ->
                            val value = index % 256
                            drawRect(
                                Color(value, (value + 85) % 256, (value + 170) % 256, if (index < 256) 255 else (index - 256) * 17),
                                topLeft = Offset((index % 16) * cell.width, (index / 16) * cell.height),
                                size = cell,
                            )
                        }
                    }
                }
            }
        }
        val node = compose.onNodeWithTag("adjustment:fixture")
        val before = node.captureToImage().toPixelMap()
        listOf(32, 16, 0).forEach { subtract ->
            compose.runOnIdle {
                enabled = true
                amount = subtract
            }
            val after = node.captureToImage().toPixelMap()
            repeat(272) { index ->
                val x = ((index % 16 + 0.5f) * before.width / 16f).toInt()
                val y = ((index / 16 + 0.5f) * before.height / 17f).toInt()
                val original = before[x, y]
                val actual = after[x, y]
                val channels =
                    listOf(
                        original.red to actual.red,
                        original.green to actual.green,
                        original.blue to actual.blue,
                    )
                channels.forEach { (input, output) ->
                    val expected = ((input * 255f).roundToInt() - subtract).coerceAtLeast(0)
                    assertEquals("Channel offset $subtract for swatch $index", expected.toFloat(), output * 255f, 1f)
                }
                assertEquals("Rendered alpha must stay unchanged", original.alpha, actual.alpha, 0f)
            }
        }
        compose.runOnIdle { enabled = false }
        val restored = node.captureToImage().toPixelMap()
        repeat(before.width) { x ->
            repeat(before.height) { y -> assertEquals(before[x, y], restored[x, y]) }
        }
    }
}
