package link.roadio.riff.ui

import androidx.test.platform.app.InstrumentationRegistry

/** Exercise NARROW with integer density on any physical window width. */
internal fun narrowBooleanFixtureDensity(): Float =
    (
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources.displayMetrics.widthPixels / 800 + 1
    ).toFloat()
