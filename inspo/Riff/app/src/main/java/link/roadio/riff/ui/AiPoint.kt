package link.roadio.riff.ui

import link.roadio.riff.player.core.selection.AiAxisRange

internal data class AiPoint(
    val arousal: Float,
    val valence: Float,
)

internal data class AiCanvasPoint(
    val x: Float,
    val y: Float,
)

internal fun aiPointFromCanvas(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    rtl: Boolean,
    arousalRange: AiAxisRange = AiAxisRange.FULL,
    valenceRange: AiAxisRange = AiAxisRange.FULL,
): AiPoint {
    require(width > 0f && height > 0f)
    val physicalX = (x / width).coerceIn(0f, 1f)
    val logicalX = if (rtl) 1f - physicalX else physicalX
    return AiPoint(
        arousal = arousalRange.valueAt(1f - (y / height).coerceIn(0f, 1f)),
        valence = valenceRange.valueAt(logicalX),
    )
}

internal fun aiPointToCanvas(
    point: AiPoint,
    width: Float,
    height: Float,
    rtl: Boolean,
    arousalRange: AiAxisRange = AiAxisRange.FULL,
    valenceRange: AiAxisRange = AiAxisRange.FULL,
): AiCanvasPoint {
    require(width > 0f && height > 0f)
    val logicalX = valenceRange.fraction(point.valence)
    return AiCanvasPoint(
        x = width * if (rtl) 1f - logicalX else logicalX,
        y = height * (1f - arousalRange.fraction(point.arousal)),
    )
}
