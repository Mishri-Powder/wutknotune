package link.roadio.riff.player.core.selection

import link.roadio.riff.indexer.core.media.MediaId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

enum class AiPteBand {
    SLOW,
    MEDIUM,
    FAST,
}

enum class AiPreference {
    NEUTRAL,
    DISLIKED,
    LIKED,
}

data class AiHistoryCount(
    val generation: Long,
    val starts: Long,
) {
    init {
        require(generation >= 0L)
        require(starts > 0L)
    }
}

data class AiSelectionWeights(
    val arousalDistance: Double = 1.0,
    val valenceDistance: Double = 1.0,
    val pteDistance: Double = 1.0,
    val trackLikeDeficit: Double = 1.0,
    val trackDislikePenalty: Double = 1.0,
    val directoryLikeDeficit: Double = 0.5,
    val directoryDislikePenalty: Double = 0.5,
    val albumLikeDeficit: Double = 0.5,
    val albumDislikePenalty: Double = 0.5,
    val artistLikeDeficit: Double = 0.5,
    val artistDislikePenalty: Double = 0.5,
    val genreLikeDeficit: Double = 0.25,
    val genreDislikePenalty: Double = 0.25,
    val playHistoryPenalty: Double = 1.0,
) {
    init {
        all().forEach { require(it.isFinite() && it in 0.0..100.0) }
    }

    internal fun all(): List<Double> =
        listOf(
            arousalDistance,
            valenceDistance,
            pteDistance,
            trackLikeDeficit,
            trackDislikePenalty,
            directoryLikeDeficit,
            directoryDislikePenalty,
            albumLikeDeficit,
            albumDislikePenalty,
            artistLikeDeficit,
            artistDislikePenalty,
            genreLikeDeficit,
            genreDislikePenalty,
            playHistoryPenalty,
        )
}

data class AiSelectionModelConfig(
    val temperature: Double = 1.0,
    val axisMinimumSpan: Double = 0.10,
    val pteMinimumGroupSize: Int = 42,
    val directoryDecay: Double = 0.5,
    val historyDecay: Double = 0.5,
    val weights: AiSelectionWeights = AiSelectionWeights(),
) {
    init {
        require(temperature.isFinite() && temperature > 0.0)
        require(axisMinimumSpan.isFinite() && axisMinimumSpan > 0.0 && axisMinimumSpan <= 2.0)
        require(pteMinimumGroupSize in 1..10_000)
        require(directoryDecay.isFinite() && directoryDecay > 0.0 && directoryDecay < 1.0)
        require(historyDecay.isFinite() && historyDecay > 0.0 && historyDecay < 1.0)
    }
}

data class AiQueueCandidate(
    val mediaId: MediaId,
    val sourceBindingId: Long?,
    val playable: Boolean,
    val effectivelyBanned: Boolean,
    val pte: Float? = null,
    val pteConfidence: Float? = null,
    val arousal: Float? = null,
    val arousalConfidence: Float? = null,
    val valence: Float? = null,
    val valenceConfidence: Float? = null,
    val trackPreference: AiPreference = AiPreference.NEUTRAL,
    /** One nearest-first preference list for each distinct physical directory path. */
    val directoryPreferencePaths: List<List<AiPreference>> = emptyList(),
    val albumPreferences: List<AiPreference> = emptyList(),
    val artistPreferences: List<AiPreference> = emptyList(),
    val genrePreferences: List<AiPreference> = emptyList(),
    val playHistory: List<AiHistoryCount> = emptyList(),
)

internal data class AiPteProfile(
    val medians: Map<AiPteBand, Double>,
    val scale: Double,
)

/**
 * Draw from one frozen eligible population. Copies are not extra lottery tickets, and neither
 * incomplete analysis nor an undersized population permits relaxing eligibility to fill a queue.
 * The selected order is materialized once; later history and reactions affect later requests.
 * Authority: https://wiki.271.pics/Riff/LATEST/player-automatic-selection#automatic-selection
 */
class AiQueueGenerator(
    private val candidateProvider: () -> List<AiQueueCandidate>,
    private val random: Random = Random.Default,
    private val config: AiSelectionModelConfig = AiSelectionModelConfig(),
    private val currentHistoryGeneration: Long = 0L,
) {
    init {
        require(currentHistoryGeneration >= 0L)
    }

    fun generate(
        size: Int,
        pteBands: Set<AiPteBand>,
        arousal: Float,
        valence: Float,
    ): List<AiQueueCandidate> {
        validateRequest(size, pteBands, arousal, valence)
        val candidates = eligibleCandidates(candidateProvider(), requirePte = pteBands.isNotEmpty())
        if (candidates.isEmpty()) return emptyList()

        val arousalScale = observedScale(candidates.mapNotNull { it.arousal?.toDouble() })
        val valenceScale = observedScale(candidates.mapNotNull { it.valence?.toDouble() })
        val pteProfile = pteProfile(candidates, config)
        val remaining =
            candidates.mapTo(ArrayList(candidates.size)) { candidate ->
                ScoredCandidate(
                    candidate,
                    -score(
                        candidate = candidate,
                        arousalTarget = arousal.toDouble(),
                        valenceTarget = valence.toDouble(),
                        arousalScale = arousalScale,
                        valenceScale = valenceScale,
                        pteBands = pteBands,
                        pteProfile = pteProfile,
                    ),
                )
            }
        val selected = ArrayList<AiQueueCandidate>(minOf(size, remaining.size))
        while (selected.size < size && remaining.isNotEmpty()) {
            val index = drawIndex(remaining)
            selected += remaining.removeAt(index).candidate
        }
        return selected
    }

    internal fun scoreForTest(
        candidate: AiQueueCandidate,
        candidates: List<AiQueueCandidate>,
        pteBands: Set<AiPteBand>,
        arousal: Float,
        valence: Float,
    ): Double {
        validateRequest(10, pteBands, arousal, valence)
        val eligible = eligibleCandidates(candidates, requirePte = pteBands.isNotEmpty())
        require(candidate in eligible) {
            "Candidate is not AI-eligible for this request"
        }
        return score(
            candidate,
            arousal.toDouble(),
            valence.toDouble(),
            observedScale(eligible.mapNotNull { it.arousal?.toDouble() }),
            observedScale(eligible.mapNotNull { it.valence?.toDouble() }),
            pteBands,
            pteProfile(eligible, config),
        )
    }

    private fun validateRequest(
        size: Int,
        pteBands: Set<AiPteBand>,
        arousal: Float,
        valence: Float,
    ) {
        require(size in ALLOWED_SIZES) { "AI queue size must be 10, 20, or 50" }
        require(pteBands.size <= 2) { "At most two PTE bands may be selected" }
        require(arousal.isFinite() && arousal in -1f..1f) { "Arousal must be finite and within [-1, 1]" }
        require(valence.isFinite() && valence in -1f..1f) { "Valence must be finite and within [-1, 1]" }
    }

    private fun eligibleCandidates(
        source: List<AiQueueCandidate>,
        requirePte: Boolean,
    ): List<AiQueueCandidate> =
        source
            .asSequence()
            .filter(AiQueueCandidate::playable)
            .filterNot(AiQueueCandidate::effectivelyBanned)
            .filter(::hasCompleteAv)
            .filter { !requirePte || hasCompletePte(it) }
            .distinctBy(AiQueueCandidate::mediaId)
            .toList()

    private fun score(
        candidate: AiQueueCandidate,
        arousalTarget: Double,
        valenceTarget: Double,
        arousalScale: Double,
        valenceScale: Double,
        pteBands: Set<AiPteBand>,
        pteProfile: AiPteProfile?,
    ): Double {
        val weights = config.weights
        val arousalDistance = scalarDistance(candidate.arousal, candidate.arousalConfidence, arousalTarget, arousalScale)
        val valenceDistance = scalarDistance(candidate.valence, candidate.valenceConfidence, valenceTarget, valenceScale)
        val pteDistance =
            if (pteBands.isEmpty() || pteProfile == null || candidate.pte == null || candidate.pteConfidence == null) {
                0.0
            } else {
                require(candidate.pteConfidence.isFinite() && candidate.pteConfidence in 0f..1f)
                val nearest = pteBands.minOf { abs(candidate.pte.toDouble() - pteProfile.medians.getValue(it)) }
                (nearest / pteProfile.scale).coerceIn(0.0, 1.0) * candidate.pteConfidence
            }
        val trackLike = if (candidate.trackPreference == AiPreference.LIKED) 0.0 else 1.0
        val trackDislike = if (candidate.trackPreference == AiPreference.DISLIKED) 1.0 else 0.0
        val directory = directoryCoordinates(candidate.directoryPreferencePaths)
        val album = entityCoordinates(candidate.albumPreferences)
        val artist = entityCoordinates(candidate.artistPreferences)
        val genre = entityCoordinates(candidate.genrePreferences)
        val history = historyCoordinate(candidate.playHistory)
        val result =
            weights.arousalDistance * arousalDistance.squared() +
                weights.valenceDistance * valenceDistance.squared() +
                weights.pteDistance * pteDistance.squared() +
                weights.trackLikeDeficit * trackLike.squared() +
                weights.trackDislikePenalty * trackDislike.squared() +
                weights.directoryLikeDeficit * directory.first.squared() +
                weights.directoryDislikePenalty * directory.second.squared() +
                weights.albumLikeDeficit * album.first.squared() +
                weights.albumDislikePenalty * album.second.squared() +
                weights.artistLikeDeficit * artist.first.squared() +
                weights.artistDislikePenalty * artist.second.squared() +
                weights.genreLikeDeficit * genre.first.squared() +
                weights.genreDislikePenalty * genre.second.squared() +
                weights.playHistoryPenalty * history.squared()
        check(result.isFinite() && result >= 0.0) { "AI selection score is not finite" }
        return result
    }

    private fun scalarDistance(
        value: Float?,
        confidence: Float?,
        target: Double,
        scale: Double,
    ): Double {
        if (value == null || confidence == null) return 0.0
        require(value.isFinite() && value in -1f..1f)
        require(confidence.isFinite() && confidence in 0f..1f)
        return (abs(value.toDouble() - target) / scale).coerceIn(0.0, 1.0) * confidence
    }

    private fun observedScale(values: List<Double>): Double =
        if (values.isEmpty()) config.axisMinimumSpan else ((values.max() - values.min()).coerceAtLeast(config.axisMinimumSpan))

    private fun directoryCoordinates(paths: List<List<AiPreference>>): Pair<Double, Double> {
        if (paths.isEmpty()) return 1.0 to 0.0
        val values =
            paths.map { path ->
                var alpha = 1.0 - config.directoryDecay
                var likes = 0.0
                var dislikes = 0.0
                path.forEach { preference ->
                    if (preference == AiPreference.LIKED) likes += alpha
                    if (preference == AiPreference.DISLIKED) dislikes += alpha
                    alpha *= config.directoryDecay
                }
                (1.0 - likes.coerceIn(0.0, 1.0)) to dislikes.coerceIn(0.0, 1.0)
            }
        return sqrt(values.map { it.first }.average()) to sqrt(values.map { it.second }.average())
    }

    private fun entityCoordinates(preferences: List<AiPreference>): Pair<Double, Double> {
        if (preferences.isEmpty()) return 1.0 to 0.0
        val size = preferences.size.toDouble()
        val liked = preferences.count { it == AiPreference.LIKED } / size
        val disliked = preferences.count { it == AiPreference.DISLIKED } / size
        return sqrt(1.0 - liked) to sqrt(disliked)
    }

    private fun historyCoordinate(history: List<AiHistoryCount>): Double {
        val accumulated =
            history.sumOf { count ->
                require(count.generation <= currentHistoryGeneration)
                val age = (currentHistoryGeneration - count.generation).toDouble()
                (1.0 - config.historyDecay) * config.historyDecay.pow(age) * count.starts.toDouble()
            }
        check(accumulated.isFinite() && accumulated >= 0.0)
        return sqrt(accumulated)
    }

    private fun drawIndex(candidates: List<ScoredCandidate>): Int {
        val maximum = candidates.maxOf(ScoredCandidate::logWeight)
        val total = candidates.sumOf { exp((it.logWeight - maximum) / config.temperature) }
        check(total.isFinite() && total > 0.0)
        var ticket = random.nextDouble() * total
        candidates.forEachIndexed { index, candidate ->
            ticket -= exp((candidate.logWeight - maximum) / config.temperature)
            if (ticket < 0.0) return index
        }
        return candidates.lastIndex
    }

    private data class ScoredCandidate(
        val candidate: AiQueueCandidate,
        val logWeight: Double,
    )

    private fun Double.squared(): Double = this * this

    companion object {
        private val ALLOWED_SIZES = setOf(10, 20, 50)

        internal fun pteProfile(
            candidates: List<AiQueueCandidate>,
            config: AiSelectionModelConfig,
        ): AiPteProfile? {
            val values =
                candidates
                    .asSequence()
                    .filter(AiQueueCandidate::playable)
                    .filterNot(AiQueueCandidate::effectivelyBanned)
                    .filter(::hasCompleteAv)
                    .distinctBy(AiQueueCandidate::mediaId)
                    .filter(::hasCompletePte)
                    .filter { requireNotNull(it.pteConfidence) > 0f }
                    .sortedWith(compareBy<AiQueueCandidate> { requireNotNull(it.pte) }.thenBy(AiQueueCandidate::mediaId))
                    .toList()
            val base = values.size / 3
            val remainder = values.size % 3
            val sizes = IntArray(3) { index -> base + if (index < remainder) 1 else 0 }
            if (sizes.any { it < config.pteMinimumGroupSize }) return null
            var offset = 0
            val medians = linkedMapOf<AiPteBand, Double>()
            AiPteBand.entries.forEachIndexed { index, band ->
                val group = values.subList(offset, offset + sizes[index])
                medians[band] = median(group.map { requireNotNull(it.pte).toDouble() })
                offset += sizes[index]
            }
            val scale =
                ((values.last().pte!!.toDouble() - values.first().pte!!.toDouble()).coerceAtLeast(config.axisMinimumSpan))
            return AiPteProfile(medians, scale)
        }

        fun isPteAvailable(
            candidates: List<AiQueueCandidate>,
            config: AiSelectionModelConfig,
        ): Boolean = pteProfile(candidates, config) != null

        private fun hasCompleteAv(candidate: AiQueueCandidate): Boolean =
            candidate.arousal != null &&
                candidate.arousalConfidence != null &&
                candidate.valence != null &&
                candidate.valenceConfidence != null

        private fun hasCompletePte(candidate: AiQueueCandidate): Boolean = candidate.pte != null && candidate.pteConfidence != null

        private fun median(sortedValues: List<Double>): Double {
            require(sortedValues.isNotEmpty())
            val middle = sortedValues.size / 2
            return if (sortedValues.size % 2 == 1) sortedValues[middle] else (sortedValues[middle - 1] + sortedValues[middle]) / 2.0
        }
    }
}
