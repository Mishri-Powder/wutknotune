package link.roadio.riff.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * One silhouette compositor for text, adopted resource painters and position shapes.
 * Authority: https://wiki.271.pics/Riff/LATEST/ui-contrast-halo
 */
@Composable
internal fun Modifier.contrastHalo(color: Color?): Modifier {
    if (color == null) return this
    val foreground = rememberGraphicsLayer()
    val silhouette = rememberGraphicsLayer()
    val far = rememberGraphicsLayer()
    val near = rememberGraphicsLayer()
    return drawWithCache {
        val farRadius = 24.dp.toPx()
        val nearRadius = 8.dp.toPx()
        val contourRadius = 2.dp.toPx()
        // Effect bounds are larger than layout bounds: tails must not become a rectangular clipped plate.
        val margin = ceil(farRadius * 3f).toInt()
        val padded = IntSize(ceil(size.width).toInt() + margin * 2, ceil(size.height).toInt() + margin * 2)
        val contour =
            List(32) { index ->
                val angle = 2.0 * PI * index / 32
                Offset((cos(angle) * contourRadius).toFloat(), (sin(angle) * contourRadius).toFloat())
            }
        silhouette.colorFilter = ColorFilter.tint(color, BlendMode.SrcIn)
        far.renderEffect = BlurEffect(farRadius, farRadius, TileMode.Decal)
        far.alpha = 0.85f
        near.renderEffect = BlurEffect(nearRadius, nearRadius, TileMode.Decal)
        near.alpha = 0.95f
        onDrawWithContent {
            // Retain the real shaping/painter output once; decoration never changes its paint or semantics.
            foreground.record { this@onDrawWithContent.drawContent() }
            silhouette.record(size = padded) {
                translate(margin.toFloat(), margin.toFloat()) { drawLayer(foreground) }
            }
            far.record(size = padded) { drawLayer(silhouette) }
            near.record(size = padded) { drawLayer(silhouette) }
            translate(-margin.toFloat(), -margin.toFloat()) {
                drawLayer(far)
                drawLayer(near)
                drawLayer(silhouette)
                contour.forEach { offset ->
                    translate(offset.x, offset.y) { drawLayer(silhouette) }
                }
            }
            drawLayer(foreground)
        }
    }
}
