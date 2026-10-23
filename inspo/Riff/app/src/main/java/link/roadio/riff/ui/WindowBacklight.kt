package link.roadio.riff.ui

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import link.roadio.riff.player.core.settings.PlayerDimBacklightMode

/** Decision authority: https://wiki.271.pics/Riff/LATEST/player-inactivity#window-brightness-policy */
internal fun dimmedWindowBrightness(
    mode: PlayerDimBacklightMode,
    night: Boolean,
    eligible: Boolean,
    idle: Boolean,
    systemBrightness: Int?,
    percent: Float,
): Float {
    val applicable =
        when (mode) {
            PlayerDimBacklightMode.OFF -> false
            PlayerDimBacklightMode.DAY -> !night
            PlayerDimBacklightMode.NIGHT -> night
            PlayerDimBacklightMode.AUTO -> true
        }
    return if (eligible && idle && applicable && systemBrightness != null && systemBrightness in 0..255) {
        relativeDimBrightness(systemBrightness, percent) / 255f
    } else {
        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
}

/** The baseline comes from Android, never from this window's potentially dimmed attributes. */
internal class WindowBacklight(
    private val window: Window,
    private val resolver: ContentResolver,
    private val onBaselineChanged: () -> Unit,
) : AutoCloseable {
    private var systemWorkaround: AndroidSystemBrightnessWorkaround? = null
    private var baseline: Int? = null
    private var baselineLoaded = false
    private val handler = Handler(Looper.getMainLooper())
    private var request = 0L
    private val observer =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                baselineLoaded = false
                onBaselineChanged()
            }
        }

    init {
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, observer)
    }

    fun configure(workaround: AndroidSystemBrightnessWorkaround?) {
        systemWorkaround?.close()
        systemWorkaround = workaround
        baselineLoaded = false
    }

    fun update(
        mode: PlayerDimBacklightMode,
        night: Boolean,
        eligible: Boolean,
        idle: Boolean,
        percent: Float,
    ) {
        if (eligible && idle && !baselineLoaded) {
            val current = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()
            baseline = systemWorkaround?.baseline(current) ?: current
            baselineLoaded = true
        }
        val brightness = dimmedWindowBrightness(mode, night, eligible, idle, baseline, percent)
        apply(brightness, percent.takeIf { brightness >= 0f })
    }

    fun restore() = apply(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, null)

    private fun apply(
        brightness: Float,
        percent: Float?,
    ) {
        val currentRequest = ++request
        val workaround = systemWorkaround
        // AI-INTENT: Use one output path; a window override can feed back into the global setting.
        // https://wiki.271.pics/Riff/LATEST/player-inactivity#optional-system-brightness-workaround
        setWindowBrightness(
            if (percent != null &&
                workaround?.canApply() == true
            ) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                brightness
            },
        )
        workaround?.apply(percent) { applied ->
            handler.post {
                // A delayed worker result must not dim after input, focus loss or destruction.
                if (request == currentRequest) {
                    setWindowBrightness(if (applied) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else brightness)
                }
            }
        }
    }

    private fun setWindowBrightness(brightness: Float) {
        val attributes = window.attributes
        if (attributes.screenBrightness == brightness) return
        attributes.screenBrightness = brightness
        window.attributes = attributes
    }

    override fun close() {
        restore()
        resolver.unregisterContentObserver(observer)
        systemWorkaround?.close()
    }
}
