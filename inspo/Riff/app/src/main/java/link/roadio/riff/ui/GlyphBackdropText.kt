@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** Text shaping and semantics remain BasicText-owned; the common halo decorates its rendered silhouette. */
@Composable
internal fun GlyphBackdropText(
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    backdropColor: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    BasicText(
        text = value,
        modifier = modifier.contrastHalo(backdropColor),
        style = style,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = TextOverflow.Ellipsis,
    )
}
