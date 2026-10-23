package link.roadio.riff.ui

import kotlin.math.hypot

/**
 * Resolves committed measurements independently of Android and hardware identity.
 *
 * Card keeps the common comfort scale; Diagonal represents the selected physical size directly.
 * Authority: https://wiki.271.pics/Riff/LATEST/ui-foundation
 * AI-INTENT: Keep policy here, not in device allowlists or UI callers. Invalid persisted measurements must
 * follow the same AUTO fallback as a fresh installation, even when Android reports corrupt density.
 */
object PhysicalDensityResolver {
    private const val DP_PER_INCH = 160f
    private const val AUTO_DIAGONAL_INCHES = 9f
    private const val CARD_COMFORT_SCALE = 7f / 9f

    fun resolve(
        settings: PlayerSettingsUiState,
        screenWidthPx: Int,
        screenHeightPx: Int,
        reportedDensity: Float,
    ): Float {
        val measuredDensity =
            when (settings.diagonal) {
                DiagonalChoice.DIAGONAL -> settings.calibrationPpiX?.div(DP_PER_INCH)
                DiagonalChoice.CARD -> settings.calibrationPpiX?.div(DP_PER_INCH)?.times(CARD_COMFORT_SCALE)
                DiagonalChoice.AUTO -> null
            }
        val automaticDensity =
            if (screenWidthPx > 0 && screenHeightPx > 0) {
                diagonalPpi(screenWidthPx, screenHeightPx, AUTO_DIAGONAL_INCHES) / DP_PER_INCH
            } else {
                null
            }
        return measuredDensity.validDensity()
            ?: automaticDensity.validDensity()
            ?: reportedDensity.validDensity()
            ?: 1f
    }

    private fun Float?.validDensity(): Float? = this?.takeIf { it.isFinite() && it > 0f }

    internal fun diagonalPpi(
        screenWidthPx: Int,
        screenHeightPx: Int,
        diagonalInches: Float,
    ): Float = hypot(screenWidthPx.toFloat(), screenHeightPx.toFloat()) / diagonalInches
}
