package link.roadio.riff.indexer.core.analysis

import link.roadio.cuetree.Attributes
import link.roadio.cuetree.GenreResolver

/** One selected scope supplies pairs atomically; no default confidence or cross-source repair. */
internal data class AiSourceFeatures(
    val pte: Pair<Float, Float>? = null,
    val arousal: Pair<Float, Float>? = null,
    val valence: Pair<Float, Float>? = null,
    val genres: List<AnalysisGenre> = emptyList(),
) {
    val present: Boolean get() = pte != null || arousal != null || valence != null || genres.isNotEmpty()
    val complete: Boolean get() = pte != null && arousal != null && valence != null && genres.isNotEmpty()

    fun revision(
        model: String,
        hash: (ByteArray) -> Long,
    ): String {
        if (!present) return model
        val evidence =
            listOf(pte, arousal, valence).joinToString("|") { it?.let { pair -> pair.first.toString() + ":" + pair.second } ?: "-" } +
                "|" + genres.joinToString("|") { it.genreId.toString() + ":" + it.confidence }
        return model + "/SOURCE-" + java.lang.Long.toUnsignedString(hash(evidence.toByteArray(Charsets.UTF_8)), 16)
    }

    fun reconcile(
        previous: AnalysisRecord,
        revision: String,
    ): AnalysisRecord {
        val retained = if (previous.modelRevision == revision) previous else previous.withoutAi(AnalysisState.PENDING, null)
        return overlay(retained).let { record ->
            if (!present) {
                record
            } else {
                record.copy(
                    aiState = AnalysisState.AVAILABLE,
                    modelRevision = if (complete || previous.modelRevision == revision) revision else null,
                )
            }
        }
    }

    fun finish(
        record: AnalysisRecord,
        state: AnalysisState,
        revision: String,
    ): AnalysisRecord =
        overlay(record)
            .let { result ->
                result.copy(
                    aiState = if (present) AnalysisState.AVAILABLE else state,
                    modelRevision = if (state in setOf(AnalysisState.PENDING, AnalysisState.RETRYABLE_FAILURE)) null else revision,
                )
            }.validate()

    private fun overlay(record: AnalysisRecord): AnalysisRecord =
        record.copy(
            pte = pte?.first ?: record.pte,
            pteConfidence = pte?.second ?: record.pteConfidence,
            arousal = arousal?.first ?: record.arousal,
            arousalConfidence = arousal?.second ?: record.arousalConfidence,
            valence = valence?.first ?: record.valence,
            valenceConfidence = valence?.second ?: record.valenceConfidence,
            genres = if (genres.isNotEmpty()) genres else record.genres,
        )

    companion object {
        fun read(
            attributes: Attributes,
            resolver: GenreResolver,
        ): AiSourceFeatures {
            fun metric(key: String): Pair<Float, Float>? {
                val value = attributes[key]?.singleOrNull()?.toFloatOrNull() ?: return null
                val confidence = attributes[key + "Confidence"]?.singleOrNull()?.toFloatOrNull() ?: return null
                if (!value.isFinite() || value !in -1f..1f || !confidence.isFinite() || confidence !in 0f..1f) return null
                return value to confidence
            }
            val names = attributes["riffGenre"].orEmpty()
            val confidences = attributes["riffGenreConfidence"].orEmpty()
            val genres =
                names
                    .mapIndexedNotNull { index, name ->
                        val confidence = confidences.getOrNull(index)?.toFloatOrNull() ?: return@mapIndexedNotNull null
                        if (!confidence.isFinite() || confidence !in 0f..1f) return@mapIndexedNotNull null
                        resolver.resolve(name).dictionaryId?.let { AnalysisGenre(it, confidence) }
                    }.groupBy { it.genreId }
                    .map { (_, values) -> values.maxBy { it.confidence } }
                    .sortedBy { it.genreId }
            return AiSourceFeatures(metric("riffPte"), metric("riffArousal"), metric("riffValence"), genres)
        }
    }
}
