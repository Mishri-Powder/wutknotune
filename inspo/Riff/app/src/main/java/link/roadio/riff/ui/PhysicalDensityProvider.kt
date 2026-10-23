package link.roadio.riff.ui

import android.content.Context
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Adapts raw window bounds to the single logical density used by the Player composition.
 *
 * Authority: https://wiki.271.pics/Riff/LATEST/ui-foundation
 * AI-INTENT: Device identity is deliberately absent. Calibration must exercise the same path on every
 * test device; raw-pixel calibration itself stays outside this density-adjusted composition.
 */
class PhysicalDensityProvider(
    private val applicationContext: Context,
) {
    fun densityDpi(
        hostContext: Context,
        settings: PlayerSettingsUiState,
    ): Int =
        (resolveDensity(hostContext, settings) * DENSITY_DEFAULT_DPI)
            .roundToInt()
            .coerceAtLeast(1)

    private fun resolveDensity(
        hostContext: Context,
        settings: PlayerSettingsUiState,
    ): Float {
        val bounds = hostContext.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        return PhysicalDensityResolver.resolve(
            settings = settings,
            screenWidthPx = bounds.width(),
            screenHeightPx = bounds.height(),
            reportedDensity = applicationContext.resources.displayMetrics.density,
        )
    }

    private companion object {
        const val DENSITY_DEFAULT_DPI = 160f
    }
}
