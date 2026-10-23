package link.roadio.riff.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer

/** One post-composition adjustment; input, semantics and component styles remain untouched. */
@Composable
internal fun Modifier.selectorAdjustment(
    enabled: Boolean,
    background: Color,
    subtract: Int,
): Modifier {
    if (!enabled || subtract == 0) return this
    val filter =
        remember(subtract) {
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        1f,
                        0f,
                        0f,
                        0f,
                        -subtract.toFloat(),
                        0f,
                        1f,
                        0f,
                        0f,
                        -subtract.toFloat(),
                        0f,
                        0f,
                        1f,
                        0f,
                        -subtract.toFloat(),
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                    ),
                ),
            )
        }
    // AI-INTENT: Offset the composited RGB bytes once, including gaps, without scaling or alpha dimming.
    // https://wiki.271.pics/Riff/LATEST/ui-tokens#search-and-disabled-control-configuration
    return graphicsLayer { colorFilter = filter }.background(background)
}
