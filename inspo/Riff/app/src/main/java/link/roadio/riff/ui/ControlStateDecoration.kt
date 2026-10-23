@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LocalRiffUncontrolledBackground = staticCompositionLocalOf { false }
internal val LocalRiffControlEnabled = staticCompositionLocalOf { true }

/** Decorative stroke only: neither the plate nor its content can enter the halo silhouette. */
@Composable
internal fun RiffStateOutline(
    modifier: Modifier,
    color: Color,
    shape: Shape,
    width: Dp,
    surround: Boolean = LocalRiffUncontrolledBackground.current,
) {
    val night = LocalRiffConfig.current.night
    // Authority: https://wiki.271.pics/Riff/LATEST/ui-color-system#palette-and-semantic-roles
    // AI-INTENT: Keep selected and focused geometry distinct; all outlines share this adapter and compositor.
    Box(
        modifier
            .contrastHalo(
                if (surround) {
                    if (night) {
                        Color.Black
                    } else {
                        Color.White
                    }
                } else {
                    null
                },
            ).border(width, color, shape),
    )
}

@Composable
internal fun SelectedControlCircle(
    modifier: Modifier,
    enabled: Boolean,
    surround: Boolean = LocalRiffUncontrolledBackground.current,
) {
    val palette = LocalRiffPalette.current
    Box(
        modifier.background(
            Color(if (enabled) palette.accentCircle else palette.disabledIcon)
                .copy(alpha = if (enabled) palette.circleActiveAlpha else palette.circlePartialAlpha),
            CircleShape,
        ),
    ) {
        if (enabled) {
            RiffStateOutline(Modifier.matchParentSize(), Color(palette.primary), CircleShape, 2.dp, surround)
        }
    }
}
