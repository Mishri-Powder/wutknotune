package link.roadio.riff.indexer.android.level1

import link.roadio.cuegrain.Gain
import link.roadio.cuegrain.Source
import link.roadio.riff.indexer.core.replaygain.ReplayGainMath
import link.roadio.riff.indexer.core.replaygain.ReplayGainSource
import link.roadio.riff.indexer.level1.Level1ReplayGainMetadata

/**
 * Cuegrain owns representation decoding. Riff owns reference normalization and whole-candidate
 * selection: a complete lower-priority source may win, but missing fields never cross source IDs.
 * Keeping policy here lets new readers reuse the same rules without copying tag syntax into Riff.
 */
internal object AndroidReplayGainMetadata {
    fun select(sources: List<Source>): Level1ReplayGainMetadata {
        val candidates =
            listOf("replaygain", "lame", "r128", "soundcheck").flatMap { kind ->
                sources.map { candidate(it, kind) }
            }
        return (candidates.firstOrNull(Candidate::complete) ?: candidates.firstOrNull { it.trackGainQ8 != null })
            ?.metadata() ?: Level1ReplayGainMetadata()
    }

    private fun candidate(
        source: Source,
        kind: String,
    ): Candidate {
        val gains = source.gains.filter { it.kind == kind }
        val reference =
            when (kind) {
                "replaygain" -> reference(source, kind, -18.0)
                "r128" -> reference(source, kind, -23.0)
                "soundcheck" -> -16.0
                else -> -18.0
            }

        fun gain(scope: String): Short? {
            if (reference == null) return null
            val values =
                gains
                    .filter { it.scope == scope && it.unit == "dB" }
                    .filter { kind != "lame" || source.attributes[it.key + "Originator"] == "3" }
            return unique(values.map(Gain::value))?.let { ReplayGainMath.atReference(it, reference) }
        }

        fun peak(scope: String): Float? =
            unique(gains.filter { it.scope == scope && it.unit == "linear" }.map(Gain::value))
                ?.toFloat()
                ?.takeIf { it.isFinite() && it in 0f..<512f }
        val track = gain("track")
        val album = gain("album")
        return Candidate(
            source =
                when (kind) {
                    "replaygain" -> ReplayGainSource.REPLAY_GAIN
                    "lame" -> ReplayGainSource.LAME
                    "r128" -> ReplayGainSource.R128
                    else -> ReplayGainSource.SOUND_CHECK
                },
            trackGainQ8 = track,
            albumGainQ8 = album,
            trackPeak = if (kind in setOf("replaygain", "lame") && (kind != "lame" || track != null)) peak("track") else null,
            albumPeak = if (kind in setOf("replaygain", "lame") && (kind != "lame" || album != null)) peak("album") else null,
            completeWhenPeaksPresent = kind != "r128",
        )
    }

    private fun reference(
        source: Source,
        kind: String,
        absent: Double,
    ): Double? {
        val key = if (kind == "r128") "R128_REFERENCE_LOUDNESS" else "REPLAYGAIN_REFERENCE_LOUDNESS"
        if (source.tags.none { it.key.equals(key, ignoreCase = true) }) return absent
        return unique(
            source.gains
                .filter {
                    it.kind == "reference" &&
                        it.scope == kind &&
                        (it.unit.equals("LUFS", true) || it.unit == "unspecified")
                }.map(Gain::value),
        )
    }

    private fun <T> unique(values: List<T>): T? = values.distinct().singleOrNull()

    private data class Candidate(
        val source: ReplayGainSource,
        val trackGainQ8: Short? = null,
        val trackPeak: Float? = null,
        val albumGainQ8: Short? = null,
        val albumPeak: Float? = null,
        val completeWhenPeaksPresent: Boolean,
    ) {
        fun complete(): Boolean =
            trackGainQ8 != null &&
                albumGainQ8 != null &&
                (!completeWhenPeaksPresent || (trackPeak != null && albumPeak != null))

        fun metadata() = Level1ReplayGainMetadata(trackGainQ8, trackPeak, albumGainQ8, albumPeak, source)
    }
}
