package link.roadio.riff.ui

import kotlin.math.roundToInt

/** Pure presentation helpers for the live calibration candidate. */
object CalibrationProposal {
    fun roundedDpi(candidatePpiX: Float): Int = candidatePpiX.roundToInt()

    fun dpiLabel(
        candidatePpiX: Float,
        candidatePpiY: Float,
    ): String = "${roundedDpi(candidatePpiX)} x ${roundedDpi(candidatePpiY)} dpi"

    fun orientationInvariantInitialScale(
        screenWidth: Float,
        screenHeight: Float,
        referenceWidthMm: Float,
        referenceHeightMm: Float,
        measureBothAxes: Boolean,
        paddingFraction: Float,
        controlHeightFraction: Float,
        gapFraction: Float,
        initialCap: Float,
        initialReduction: Float,
    ): Float {
        fun fitScale(
            width: Float,
            height: Float,
        ): Float {
            val shortEdge = minOf(width, height)
            val canvasWidth = width - shortEdge * paddingFraction * 2f
            val canvasHeight =
                height -
                    shortEdge *
                    (paddingFraction * 2f + controlHeightFraction + gapFraction)
            val widthScale =
                if (measureBothAxes || width >= height) {
                    canvasWidth * initialCap / referenceWidthMm
                } else {
                    Float.POSITIVE_INFINITY
                }
            val heightScale =
                if (measureBothAxes || height > width) {
                    canvasHeight * initialCap / referenceHeightMm
                } else {
                    Float.POSITIVE_INFINITY
                }
            return minOf(widthScale, heightScale)
        }
        return minOf(
            fitScale(screenWidth, screenHeight),
            fitScale(screenHeight, screenWidth),
        ) * initialReduction
    }

    fun orientationMatchedCardReference(
        screenWidth: Float,
        screenHeight: Float,
        longEdgeMm: Float,
        shortEdgeMm: Float,
    ): Pair<Float, Float> =
        if (screenHeight > screenWidth) {
            shortEdgeMm to longEdgeMm
        } else {
            longEdgeMm to shortEdgeMm
        }

    fun orientationInvariantCardInitialScale(
        screenWidth: Float,
        screenHeight: Float,
        longEdgeMm: Float,
        shortEdgeMm: Float,
        paddingFraction: Float,
        controlHeightFraction: Float,
        gapFraction: Float,
        initialCap: Float,
        initialReduction: Float,
    ): Float {
        fun fitScale(
            width: Float,
            height: Float,
        ): Float {
            val shortScreenEdge = minOf(width, height)
            val canvasWidth = width - shortScreenEdge * paddingFraction * 2f
            val canvasHeight =
                height -
                    shortScreenEdge *
                    (paddingFraction * 2f + controlHeightFraction + gapFraction)
            val reference =
                orientationMatchedCardReference(
                    screenWidth = width,
                    screenHeight = height,
                    longEdgeMm = longEdgeMm,
                    shortEdgeMm = shortEdgeMm,
                )
            return minOf(
                canvasWidth * initialCap / reference.first,
                canvasHeight * initialCap / reference.second,
            )
        }
        return minOf(
            fitScale(screenWidth, screenHeight),
            fitScale(screenHeight, screenWidth),
        ) * initialReduction
    }

    fun nextIndex(
        currentIndex: Int,
        itemCount: Int,
    ): Int {
        require(itemCount > 0)
        require(currentIndex in 0 until itemCount)
        return (currentIndex + 1) % itemCount
    }
}

/** Mutable calibration session retained across configuration-driven view rebuilds. */
class CalibrationSessionState {
    var modeIndex: Int = 0
    var euroIndex: Int = 2
    var candidateXPxPerMm: Float? = null
    var candidateYPxPerMm: Float? = null
    var initialWidth: Float? = null
    var initialHeight: Float? = null

    val isInitialized: Boolean
        get() = candidateXPxPerMm != null && candidateYPxPerMm != null

    fun clear() {
        modeIndex = 0
        euroIndex = 2
        candidateXPxPerMm = null
        candidateYPxPerMm = null
        initialWidth = null
        initialHeight = null
    }
}
