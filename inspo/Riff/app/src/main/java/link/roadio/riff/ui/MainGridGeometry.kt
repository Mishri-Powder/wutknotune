package link.roadio.riff.ui

import kotlin.math.roundToInt

/**
 * Fits MAIN's fixed row forms inside the measured grid viewport, in physical dp.
 *
 * The caller excludes safe insets, margins, top chrome and the status slot by measuring the body.
 * Authority: https://wiki.271.pics/Riff/LATEST/main-screen-scope-and-platform-boundary#geometry-and-appearance
 * AI-INTENT: Keep the 128dp floor and normal sp text; only an actually undersized viewport needs scrolling.
 */
object MainGridGeometry {
    private const val MIN_TILE_DP = 128f

    fun tileSideDp(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        columnCount: Int,
        rowCount: Int,
        requestedTileDp: Float,
        gapDp: Float,
        density: Float = 1f,
    ): Float {
        require(density.isFinite() && density > 0f)
        // Fit the actual individually rounded layout units; fractional gaps cannot accumulate overflow.
        val gap = (gapDp * density).roundToInt()
        val widthFit = ((viewportWidthDp * density).roundToInt() - gap * (columnCount - 1)) / columnCount
        val heightFit = ((viewportHeightDp * density).roundToInt() - gap * (rowCount - 1)) / rowCount
        val requested = (requestedTileDp * density).roundToInt()
        val minimum = (MIN_TILE_DP * density).roundToInt()
        return minOf(requested, widthFit, heightFit).coerceAtLeast(minimum) / density
    }
}
