package link.roadio.riff.indexer.core.replaygain

import kotlin.math.abs
import kotlin.math.floor

enum class ReplayGainSource { NONE, REPLAY_GAIN, R128, LAME, SOUND_CHECK, MEASURED }

object ReplayGainMath {
    fun q8(db: Double): Short? {
        if (!db.isFinite()) return null
        val scaled = db * 256.0
        val rounded = if (scaled >= 0) floor(scaled + 0.5) else -floor(abs(scaled) + 0.5)
        return rounded.takeIf { it in Short.MIN_VALUE.toDouble()..Short.MAX_VALUE.toDouble() }?.toInt()?.toShort()
    }

    fun atReference(
        sourceGainDb: Double,
        sourceReferenceLufs: Double,
    ): Short? {
        val referenceDeltaLu = TARGET_REFERENCE_LUFS - sourceReferenceLufs
        return q8(sourceGainDb + referenceDeltaLu)
    }

    private const val TARGET_REFERENCE_LUFS = -18.0
}
