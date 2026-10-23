package link.roadio.riff.ui

import link.roadio.riff.player.core.settings.ApplicationMode
import kotlin.math.roundToInt

/** Authority: https://wiki.271.pics/Riff/LATEST/player-inactivity#window-brightness-policy */
internal fun relativeDimBrightness(
    systemBrightness: Int,
    percent: Float,
): Float = systemBrightness * percent / 100f

internal data class BrightnessLease(
    val baseline: Int,
    val applied: Int,
)

internal interface SystemBrightnessAccess {
    fun read(): Int?

    fun canWrite(): Boolean

    fun write(value: Int): Boolean

    fun savedLease(): BrightnessLease?

    fun saveLease(value: BrightnessLease)

    fun clearLease()
}

/** Authority: https://wiki.271.pics/Riff/LATEST/player-inactivity#optional-system-brightness-workaround */
internal class SystemBrightnessLease(
    private val access: SystemBrightnessAccess,
) {
    @Volatile private var lease = access.savedLease()

    // AI-INTENT: Observer callbacks must not scale our own global dim value a second time.
    fun baseline(current: Int?): Int? = lease?.takeIf { it.applied == current }?.baseline ?: current

    /** Calls are serialized by the Android worker; persist ownership before touching the setting. */
    fun dim(percent: Float): Boolean {
        val current = access.read()?.takeIf { it in 0..255 } ?: return false
        if (!access.canWrite()) return false
        val baseline = requireNotNull(baseline(current))
        val applied = relativeDimBrightness(baseline, percent).roundToInt()
        if (current == applied) return true
        val next = BrightnessLease(baseline, applied)
        access.saveLease(next)
        lease = next
        check(access.write(applied)) { "System brightness write failed" }
        return true
    }

    /** Recover after interruption, or release while respecting a newer external setting. */
    fun restore() {
        val previous = lease ?: return
        val current = access.read()?.takeIf { it in 0..255 } ?: return
        if (current == previous.applied) {
            if (!access.canWrite()) return
            check(access.write(previous.baseline)) { "System brightness restore failed" }
        }
        access.clearLease()
        lease = null
    }
}

internal fun systemBrightnessWorkaroundEnabled(
    configured: Boolean,
    mode: ApplicationMode,
): Boolean = configured && mode != ApplicationMode.AAOS
