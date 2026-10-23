package link.roadio.riff.ui

import androidx.test.platform.app.InstrumentationRegistry

/** Render a 640dp-wide fixture so NARROW tests do not depend on the display pixel count. */
internal fun narrowAzFixtureDensity(): Float {
    val width =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources.displayMetrics.widthPixels
    return (width / 640f).coerceAtLeast(1f)
}
