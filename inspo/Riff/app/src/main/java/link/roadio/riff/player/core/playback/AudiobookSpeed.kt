package link.roadio.riff.player.core.playback

import java.util.Locale

/** Fixed profile preference; integer tenths keep persistence and stepping exact. */
enum class AudiobookSpeed(
    val tenths: Int,
) {
    SEVEN_TENTHS(7),
    EIGHT_TENTHS(8),
    NINE_TENTHS(9),
    NORMAL(10),
    ELEVEN_TENTHS(11),
    TWELVE_TENTHS(12),
    THIRTEEN_TENTHS(13),
    FOURTEEN_TENTHS(14),
    FIFTEEN_TENTHS(15),
    ;

    val multiplier: Float get() = tenths / 10f
    val label: String get() = String.format(Locale.ROOT, "%1.1fx", multiplier)

    fun next(): AudiobookSpeed = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromTenths(tenths: Int): AudiobookSpeed =
            requireNotNull(entries.firstOrNull { it.tenths == tenths }) { "Unsupported audiobook speed: $tenths" }
    }
}

/** One policy for every occurrence, including mixed queues and a profile change during playback. */
data class AudiobookAudioParameters(
    val speed: Float,
    val pitch: Float = 1.0f,
    val skipSilence: Boolean,
) {
    companion object {
        fun resolve(
            booksMode: Boolean,
            effectiveBook: Boolean,
            preference: AudiobookSpeed,
        ): AudiobookAudioParameters =
            if (booksMode && effectiveBook) {
                AudiobookAudioParameters(preference.multiplier, skipSilence = true)
            } else {
                AudiobookAudioParameters(1.0f, skipSilence = false)
            }
    }
}
