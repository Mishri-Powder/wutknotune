package link.roadio.riff.indexer.core.analysis

import link.roadio.cuetree.MediaId

enum class AnalysisState { PENDING, AVAILABLE, COMPLETED_NO_RESULT, RETRYABLE_FAILURE, DETERMINISTIC_FAILURE }

enum class AnalysisGainSource { NONE, REPLAY_GAIN, R128, LAME, SOUND_CHECK, MEASURED }

data class AnalysisGenre(
    val genreId: Long,
    val confidence: Float,
) {
    init {
        require(confidence.isFinite() && confidence in 0f..1f)
    }
}

data class AnalysisRecord(
    val mediaId: MediaId,
    val gainState: AnalysisState = AnalysisState.PENDING,
    val gainSource: AnalysisGainSource = AnalysisGainSource.NONE,
    val trackGainQ8: Short? = null,
    val trackPeak: Float? = null,
    val albumGainQ8: Short? = null,
    val albumPeak: Float? = null,
    val albumSource: AnalysisGainSource = AnalysisGainSource.NONE,
    val aiState: AnalysisState = AnalysisState.PENDING,
    val pte: Float? = null,
    val pteConfidence: Float? = null,
    val arousal: Float? = null,
    val arousalConfidence: Float? = null,
    val valence: Float? = null,
    val valenceConfidence: Float? = null,
    val genres: List<AnalysisGenre> = emptyList(),
    val modelRevision: String? = null,
    val gainRevision: String? = null,
    val decoderHint: Long? = null,
) {
    fun needsGain(revision: String): Boolean =
        gainState in setOf(AnalysisState.PENDING, AnalysisState.RETRYABLE_FAILURE) ||
            (
                gainRevision != revision &&
                    gainSource in setOf(AnalysisGainSource.NONE, AnalysisGainSource.MEASURED)
            )

    fun needsAi(revision: String): Boolean =
        aiState in setOf(AnalysisState.PENDING, AnalysisState.RETRYABLE_FAILURE) || modelRevision != revision

    fun withoutAi(
        state: AnalysisState,
        revision: String? = modelRevision,
    ): AnalysisRecord =
        copy(
            aiState = state,
            pte = null,
            pteConfidence = null,
            arousal = null,
            arousalConfidence = null,
            valence = null,
            valenceConfidence = null,
            genres = emptyList(),
            modelRevision = revision,
        )

    fun withDecoderHint(hint: Long): AnalysisRecord {
        if (decoderHint == null || decoderHint == hint) return copy(decoderHint = hint)
        val updated = withoutAi(AnalysisState.PENDING, null).copy(decoderHint = hint)
        return if (gainSource == AnalysisGainSource.NONE || gainSource == AnalysisGainSource.MEASURED) {
            updated.copy(
                gainState = AnalysisState.PENDING,
                gainSource = AnalysisGainSource.NONE,
                trackGainQ8 = null,
                trackPeak = null,
                gainRevision = null,
            )
        } else {
            updated
        }
    }

    fun validate(): AnalysisRecord {
        require((trackGainQ8 == null) == (gainSource == AnalysisGainSource.NONE))
        require((albumGainQ8 == null) == (albumSource == AnalysisGainSource.NONE))
        require(albumSource != AnalysisGainSource.MEASURED)
        require((gainState == AnalysisState.AVAILABLE) == (trackGainQ8 != null))
        require(trackPeak == null || (trackGainQ8 != null && gainSource != AnalysisGainSource.MEASURED))
        require(albumPeak == null || albumGainQ8 != null)
        listOf(trackPeak, albumPeak).forEach { require(it == null || (it.isFinite() && it >= 0f)) }
        listOf(pte to pteConfidence, arousal to arousalConfidence, valence to valenceConfidence).forEach { (value, confidence) ->
            require((value == null) == (confidence == null))
            require(value == null || (value.isFinite() && value in -1f..1f))
            require(confidence == null || (confidence.isFinite() && confidence in 0f..1f))
        }
        require(aiState == AnalysisState.AVAILABLE || (pte == null && arousal == null && valence == null && genres.isEmpty()))
        require(genres.map(AnalysisGenre::genreId).distinct().size == genres.size)
        require(genres.zipWithNext().all { (a, b) -> a.genreId < b.genreId })
        return this
    }
}

data class AnalysisSnapshot(
    val generation: Long,
    val records: List<AnalysisRecord>,
) {
    fun validate(): AnalysisSnapshot {
        require(generation >= 0)
        require(records.map(AnalysisRecord::mediaId).distinct().size == records.size)
        require(
            records.zipWithNext().all { (a, b) ->
                a.mediaId.hi < b.mediaId.hi ||
                    (
                        a.mediaId.hi == b.mediaId.hi &&
                            compareValues(a.mediaId.lo, b.mediaId.lo) < 0
                    )
            },
        )
        records.forEach(AnalysisRecord::validate)
        return this
    }
}

/**
 * Exact records win as a whole, even when their current outcome is pending or unsuccessful.
 * Resolution preserves the captured MDRG key; aliases join it to the current factual identity.
 * Contract: https://wiki.271.pics/Riff/LATEST/cuetree-analysis-integration
 */
fun AnalysisSnapshot.resolve(snapshot: link.roadio.cuetree.Snapshot): Map<MediaId, AnalysisRecord> {
    val result = linkedMapOf<MediaId, AnalysisRecord>()
    records.forEach { record ->
        snapshot.sources(record.mediaId).forEach { index ->
            val id = snapshot.nodes[index].id
            if (id == record.mediaId || result[id]?.mediaId != id) result[id] = record
        }
    }
    return result
}
