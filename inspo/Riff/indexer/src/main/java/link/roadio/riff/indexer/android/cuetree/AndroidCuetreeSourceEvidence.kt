package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.Attributes
import link.roadio.riff.indexer.core.replaygain.ReplayGainSource
import link.roadio.riff.indexer.level1.Level1ReplayGainMetadata

/**
 * Retained admission/gain evidence lets unchanged metadata feed analysis without reopening sources.
 * Contract: https://wiki.271.pics/Riff/LATEST/cuetree-integration#source-evidence
 */
internal object AndroidCuetreeSourceEvidence {
    private const val PREFIX = "riff.analysis."
    private const val SOURCE = PREFIX + "gainSource"
    private const val TRACK_GAIN = PREFIX + "trackGainQ8"
    private const val TRACK_PEAK = PREFIX + "trackPeak"
    private const val ALBUM_GAIN = PREFIX + "albumGainQ8"
    private const val ALBUM_PEAK = PREFIX + "albumPeak"
    private const val DECODER_HINT = PREFIX + "decoderHint"

    fun encode(
        gain: Level1ReplayGainMetadata,
        hint: Long?,
    ): Attributes =
        buildMap {
            put(SOURCE, listOf(gain.source.name))
            gain.trackGainQ8?.let { put(TRACK_GAIN, listOf(it.toString())) }
            gain.trackPeak?.let { put(TRACK_PEAK, listOf(it.toString())) }
            gain.albumGainQ8?.let { put(ALBUM_GAIN, listOf(it.toString())) }
            gain.albumPeak?.let { put(ALBUM_PEAK, listOf(it.toString())) }
            hint?.let { put(DECODER_HINT, listOf(it.toString())) }
        }

    fun gain(values: Attributes): Level1ReplayGainMetadata? {
        val source = values[SOURCE]?.singleOrNull()?.let { runCatching { ReplayGainSource.valueOf(it) }.getOrNull() } ?: return null
        return Level1ReplayGainMetadata(
            trackGainQ8 = values[TRACK_GAIN]?.singleOrNull()?.toShortOrNull(),
            trackPeak = values[TRACK_PEAK]?.singleOrNull()?.toFloatOrNull(),
            albumGainQ8 = values[ALBUM_GAIN]?.singleOrNull()?.toShortOrNull(),
            albumPeak = values[ALBUM_PEAK]?.singleOrNull()?.toFloatOrNull(),
            source = source,
        )
    }

    fun hint(values: Attributes): Long? = values[DECODER_HINT]?.singleOrNull()?.toLongOrNull()
}
