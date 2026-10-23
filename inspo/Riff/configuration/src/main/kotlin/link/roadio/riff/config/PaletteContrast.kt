package link.roadio.riff.config

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** One admission policy for resolved roles and composited backgrounds; never a UI-time calculation. */
internal fun validatePaletteContrast(
    value: (String) -> Any,
    night: Boolean,
) {
    fun color(role: String): Int = parseColor(value("colors.$role") as String).also { require(it ushr 24 == 255) }
    val background = color("screenBackground")
    val solid = listOf(background, color("rowSurface"), color("rowSurfaceNowPlaying"), color("controlPlate"))
    val panelAlpha = (value("shape.panelAlpha") as Number).toDouble() / 255.0
    val surfaces = solid + listOf("rowSurface", "rowSurfaceNowPlaying").map { compositeColor(color(it), background, panelAlpha) }
    val readable =
        listOf(
            "textSecondaryColor",
            "textSuppressedColor",
            "iconInactive",
            "accentActive",
            "focusRingColor",
            "nowPlayingBorderColor",
            "positive",
            "error",
        )
    val circle = compositeColor(color("accentCircle"), background, (value("colors.circleFillActiveAlpha") as Number).toDouble())
    val subtract = (value("behavior.azRgbSubtract") as Number).toInt()
    // AI-INTENT: Validate the same post-composition RGB operation as the single GPU layer, not a backdrop-only approximation.
    // https://wiki.271.pics/Riff/LATEST/ui-color-system#validation-and-verification
    for (amount in listOf(0, subtract).distinct()) {
        fun adjusted(color: Int): Int = subtractRgb(color, amount)
        val adjustedSurfaces = surfaces.map(::adjusted)
        val phase = if (amount == 0) "" else " after A-Z RGB subtraction"

        fun requireContrast(
            role: String,
            minimum: Double,
        ) {
            require(adjustedSurfaces.all { colorContrast(adjusted(color(role)), it) >= minimum }) { "Insufficient $role contrast$phase" }
        }
        requireContrast("textPrimaryColor", 7.0)
        requireContrast("fragmentTitleColor", 7.0)
        readable.forEach { requireContrast(it, 4.5) }
        require(colorContrast(adjusted(color("textPrimaryColor")), adjusted(circle)) >= 7.0) { "Insufficient selected-text contrast$phase" }
        require(colorContrast(adjusted(color("accentActive")), adjusted(circle)) >= 4.5) { "Insufficient selected-icon contrast$phase" }
        if (night) {
            (listOf("textPrimaryColor", "fragmentTitleColor") + readable).forEach { role ->
                require(
                    adjustedSurfaces.all {
                        relativeLuminance(adjusted(color(role))) > relativeLuminance(it)
                    },
                ) { "Night content must be lighter than its surface$phase" }
            }
        }
    }
    val halo = if (night) 0xff000000.toInt() else 0xffffffff.toInt()
    (listOf("textPrimaryColor", "fragmentTitleColor") + readable).forEach { role ->
        require(colorContrast(color(role), halo) >= 4.5) { "Insufficient $role halo contrast" }
    }
}

internal fun subtractRgb(
    color: Int,
    amount: Int,
): Int {
    fun channel(shift: Int): Int = (((color ushr shift) and 255) - amount).coerceAtLeast(0) shl shift
    return (color and 0xff000000.toInt()) or channel(16) or channel(8) or channel(0)
}

internal fun compositeColor(
    foreground: Int,
    background: Int,
    alpha: Double,
): Int {
    fun channel(shift: Int): Int =
        ((((foreground ushr shift) and 255) * alpha + ((background ushr shift) and 255) * (1.0 - alpha)).roundToInt()) shl shift
    return 0xff000000.toInt() or channel(16) or channel(8) or channel(0)
}

internal fun relativeLuminance(color: Int): Double {
    fun linear(shift: Int): Double {
        val component = ((color ushr shift) and 255) / 255.0
        return if (component <= 0.04045) component / 12.92 else ((component + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
}

internal fun colorContrast(
    first: Int,
    second: Int,
): Double {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}
