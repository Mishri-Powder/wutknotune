package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.NodeKind
import link.roadio.riff.indexer.core.analysis.AnalysisGainSource
import link.roadio.riff.indexer.core.analysis.AnalysisRecord
import link.roadio.riff.indexer.core.analysis.AnalysisState
import link.roadio.riff.indexer.core.replaygain.ReplayGainSource
import link.roadio.riff.indexer.level1.Level1ReplayGainMetadata

/**
 * AI-INTENT: invalidate decoder-dependent outcomes before deciding whether the identity needs work.
 * Tagged gain survives decoder changes; a FILF may inherit Album Gain but never whole-file Track Gain.
 * https://wiki.271.pics/Riff/LATEST/replaygain-analysis#rg-5-measurement-and-dependencies
 * https://wiki.271.pics/Riff/LATEST/cuetree-analysis-integration
 */
internal fun reconcileAnalysisSource(
    previous: AnalysisRecord,
    kind: NodeKind,
    gain: Level1ReplayGainMetadata?,
    gainObserved: Boolean,
    decoderHint: Long?,
    gainRevision: String,
): AnalysisRecord {
    var record = decoderHint?.let(previous::withDecoderHint) ?: previous
    if (!gainObserved) return record
    record =
        record.copy(
            albumGainQ8 = gain?.albumGainQ8,
            albumPeak = if (gain?.albumGainQ8 != null) gain.albumPeak else null,
            albumSource = if (gain?.albumGainQ8 != null) gain.source.analysisSource() else AnalysisGainSource.NONE,
        )
    if (kind != NodeKind.MEDIA) return record
    return if (gain?.trackGainQ8 != null) {
        record.copy(
            gainState = AnalysisState.AVAILABLE,
            gainSource = gain.source.analysisSource(),
            trackGainQ8 = gain.trackGainQ8,
            trackPeak = gain.trackPeak,
            gainRevision = gainRevision,
        )
    } else if (record.gainSource !in setOf(AnalysisGainSource.MEASURED, AnalysisGainSource.NONE)) {
        record.copy(
            gainState = AnalysisState.PENDING,
            gainSource = AnalysisGainSource.NONE,
            trackGainQ8 = null,
            trackPeak = null,
            gainRevision = null,
        )
    } else {
        record
    }
}

internal fun ReplayGainSource.analysisSource() =
    when (this) {
        ReplayGainSource.NONE -> AnalysisGainSource.NONE
        ReplayGainSource.REPLAY_GAIN -> AnalysisGainSource.REPLAY_GAIN
        ReplayGainSource.R128 -> AnalysisGainSource.R128
        ReplayGainSource.LAME -> AnalysisGainSource.LAME
        ReplayGainSource.SOUND_CHECK -> AnalysisGainSource.SOUND_CHECK
        ReplayGainSource.MEASURED -> AnalysisGainSource.MEASURED
    }

/** Only explicit clip evidence can suppress its own Track Gain measurement. */
internal fun reconcileFragmentGain(
    previous: AnalysisRecord,
    gain: Level1ReplayGainMetadata?,
    revision: String,
): AnalysisRecord {
    var record = previous
    if (gain?.albumGainQ8 != null) {
        record =
            record.copy(
                albumGainQ8 = gain.albumGainQ8,
                albumPeak = gain.albumPeak,
                albumSource = gain.source.analysisSource(),
            )
    }
    return if (gain?.trackGainQ8 != null) {
        record.copy(
            gainState = AnalysisState.AVAILABLE,
            gainSource = gain.source.analysisSource(),
            trackGainQ8 = gain.trackGainQ8,
            trackPeak = gain.trackPeak,
            gainRevision = revision,
        )
    } else if (record.gainSource !in setOf(AnalysisGainSource.NONE, AnalysisGainSource.MEASURED)) {
        record.copy(
            gainState = AnalysisState.PENDING,
            gainSource = AnalysisGainSource.NONE,
            trackGainQ8 = null,
            trackPeak = null,
            gainRevision = null,
        )
    } else {
        record
    }
}
