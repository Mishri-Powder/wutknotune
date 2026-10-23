package link.roadio.riff.ui

import kotlin.math.hypot

/** Pure raw-pixel clamp used by CALIBRATION_SCREEN relative drags. */
object CalibrationAdjustment {
    fun hasReachedActivationDistance(
        deltaX: Float,
        deltaY: Float,
        activationDistancePx: Float,
    ): Boolean = hypot(deltaX, deltaY) >= activationDistancePx

    fun apply(
        currentPx: Float,
        deltaPx: Float,
        initialPx: Float,
        canvasMaximumPx: Float,
        minimumScale: Float,
    ): Float = (currentPx + deltaPx).coerceIn(initialPx * minimumScale, canvasMaximumPx)
}
