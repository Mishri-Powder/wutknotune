package link.roadio.riff.player.core.selection

import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AiQueueGeneratorTest {
    @Test
    fun zeroRandomTicketNeverSelectsAnUnderflowedWeight() {
        val best = featuredCandidate(1L, arousal = -1f)
        val worst = featuredCandidate(2L, arousal = 1f)
        val random =
            object : Random() {
                override fun nextBits(bitCount: Int) = 0
            }
        val selected =
            AiQueueGenerator(
                { listOf(worst, best) },
                random,
                AiSelectionModelConfig(temperature = Double.MIN_VALUE, weights = onlyWeight(arousal = 1.0)),
            ).generate(10, emptySet(), -1f, 0f)
        assertEquals(best, selected.first())
    }

    @Test
    fun tinyPositiveTemperaturesKeepFiniteSamplingAcrossEveryDraw() {
        val candidates =
            (1L..20L).map {
                featuredCandidate(it, arousal = (it - 1) / 20f)
            }
        for (temperature in listOf(Double.MIN_VALUE, 1e-308, 1.0, Double.MAX_VALUE)) {
            val selected =
                AiQueueGenerator({ candidates }, Random(7), AiSelectionModelConfig(temperature = temperature))
                    .generate(20, emptySet(), 0f, 0f)
            assertEquals(candidates.map { it.mediaId }.toSet(), selected.map { it.mediaId }.toSet())
            if (temperature < 1e-300) assertEquals(candidates.map { it.mediaId }, selected.map { it.mediaId })
        }
    }

    @Test
    fun `weighted selector returns requested distinct eligible count reproducibly`() {
        val candidates =
            (1L..30L).map { mediaId ->
                AiQueueCandidate(
                    MediaId(mediaId),
                    sourceBindingId = mediaId + 100L,
                    playable = true,
                    effectivelyBanned = false,
                    arousal = 0f,
                    arousalConfidence = 1f,
                    valence = 0f,
                    valenceConfidence = 1f,
                    trackPreference = if (mediaId % 2L == 0L) AiPreference.LIKED else AiPreference.DISLIKED,
                )
            } +
                featuredCandidate(1L, sourceBindingId = 999L) +
                featuredCandidate(31L, sourceBindingId = 131L, playable = false) +
                featuredCandidate(32L, sourceBindingId = 132L, effectivelyBanned = true)

        fun generate() = AiQueueGenerator({ candidates }, Random(7)).generate(20, emptySet(), -0.25f, 0.75f)
        val result = generate()

        assertEquals(result, generate())
        assertEquals(20, result.size)
        assertEquals(20, result.map(AiQueueCandidate::mediaId).distinct().size)
        assertTrue(result.all { it.playable && !it.effectivelyBanned })
    }

    @Test
    fun `short library returns every eligible item`() {
        val candidates =
            listOf(
                featuredCandidate(1L, 11L),
                featuredCandidate(2L, 12L, effectivelyBanned = true),
                featuredCandidate(3L, 13L, playable = false),
                featuredCandidate(4L, 14L),
            )

        val result = AiQueueGenerator({ candidates }, Random(9)).generate(10, emptySet(), 0f, 0f)

        assertEquals(setOf(MediaId(1L), MediaId(4L)), result.map(AiQueueCandidate::mediaId).toSet())
    }

    @Test
    fun `PTE requires three groups of forty two and uses selected medians`() {
        val complete = pteCandidates(126)
        val incomplete = pteCandidates(125)
        val config = AiSelectionModelConfig(weights = onlyWeight(pte = 1.0))

        assertTrue(AiQueueGenerator.isPteAvailable(complete, config))
        assertFalse(AiQueueGenerator.isPteAvailable(incomplete, config))
        val profile = requireNotNull(AiQueueGenerator.pteProfile(complete, config))
        val lowMedian = profile.medians.getValue(AiPteBand.SLOW).toFloat()
        val low = featuredCandidate(1001, pte = lowMedian, pteConfidence = 1f)
        val high =
            featuredCandidate(
                1002,
                pte = profile.medians.getValue(AiPteBand.FAST).toFloat(),
                pteConfidence = 1f,
            )
        val candidates = complete + low + high
        val generator = AiQueueGenerator({ candidates }, Random(1), config)

        assertTrue(
            generator.scoreForTest(low, candidates, setOf(AiPteBand.SLOW), 0f, 0f) <
                generator.scoreForTest(high, candidates, setOf(AiPteBand.SLOW), 0f, 0f),
        )
        assertEquals(0.0, generator.scoreForTest(high, candidates, emptySet(), 0f, 0f), 0.0)
        assertFails { generator.generate(10, AiPteBand.entries.toSet(), 0f, 0f) }
    }

    @Test
    fun `confidence broadens A V distance and incomplete A V is ineligible`() {
        val config = AiSelectionModelConfig(weights = onlyWeight(arousal = 1.0))
        val full = featuredCandidate(1, arousal = 1f, arousalConfidence = 1f)
        val half = featuredCandidate(2, arousal = 1f, arousalConfidence = 0.5f)
        val missing = AiQueueCandidate(MediaId(3), 3, true, false)
        val far = featuredCandidate(4, arousal = -1f, arousalConfidence = 1f)
        val candidates = listOf(full, half, missing, far)
        val generator = AiQueueGenerator({ candidates }, Random(1), config)

        assertEquals(0.25, generator.scoreForTest(full, candidates, emptySet(), 0f, 0f), 1e-9)
        assertEquals(0.0625, generator.scoreForTest(half, candidates, emptySet(), 0f, 0f), 1e-9)
        assertFails { generator.scoreForTest(missing, candidates, emptySet(), 0f, 0f) }
        assertEquals(setOf(MediaId(1L), MediaId(2L), MediaId(4L)), generator.generate(10, emptySet(), 0f, 0f).map { it.mediaId }.toSet())
    }

    @Test
    fun `selected PTE excludes candidates without PTE`() {
        val withPte = List(10) { featuredCandidate(it.toLong() + 1L, pte = 0f, pteConfidence = 1f) }
        val missingPte = featuredCandidate(11L)

        val result =
            AiQueueGenerator({ withPte + missingPte }, Random(1))
                .generate(10, setOf(AiPteBand.MEDIUM), 0f, 0f)

        assertEquals(withPte.map { it.mediaId }.toSet(), result.map { it.mediaId }.toSet())
    }

    @Test
    fun `reaction directory and history coordinates accumulate with configured decay`() {
        val reactionConfig = AiSelectionModelConfig(weights = onlyWeight(directoryDislike = 1.0))
        val candidate =
            AiQueueCandidate(
                MediaId(1),
                1,
                true,
                false,
                arousal = 0f,
                arousalConfidence = 1f,
                valence = 0f,
                valenceConfidence = 1f,
                directoryPreferencePaths = listOf(listOf(AiPreference.DISLIKED, AiPreference.DISLIKED)),
            )
        assertEquals(
            0.75,
            AiQueueGenerator({ listOf(candidate) }, Random(1), reactionConfig)
                .scoreForTest(candidate, listOf(candidate), emptySet(), 0f, 0f),
            1e-9,
        )

        val historyConfig = AiSelectionModelConfig(weights = onlyWeight(history = 1.0))
        val history = candidate.copy(playHistory = listOf(AiHistoryCount(10, 1), AiHistoryCount(9, 1)))
        assertEquals(
            0.75,
            AiQueueGenerator({ listOf(history) }, Random(1), historyConfig, currentHistoryGeneration = 10)
                .scoreForTest(history, listOf(history), emptySet(), 0f, 0f),
            1e-9,
        )
    }

    @Test
    fun `entity memberships use a bounded mean rather than multiplying tickets`() {
        val config = AiSelectionModelConfig(weights = onlyWeight(artistLike = 1.0))
        val candidate =
            AiQueueCandidate(
                MediaId(1),
                1,
                true,
                false,
                arousal = 0f,
                arousalConfidence = 1f,
                valence = 0f,
                valenceConfidence = 1f,
                artistPreferences = listOf(AiPreference.LIKED, AiPreference.NEUTRAL),
            )
        assertEquals(
            0.5,
            AiQueueGenerator({ listOf(candidate) }, Random(1), config)
                .scoreForTest(candidate, listOf(candidate), emptySet(), 0f, 0f),
            1e-9,
        )
    }

    @Test
    fun `request validation rejects unsupported non finite and three-band input`() {
        val generator = AiQueueGenerator({ emptyList() }, Random(1))

        assertFails { generator.generate(12, emptySet(), 0f, 0f) }
        assertFails { generator.generate(10, emptySet(), Float.NaN, 0f) }
        assertFails { generator.generate(10, emptySet(), 0f, Float.POSITIVE_INFINITY) }
        assertFails { generator.generate(10, emptySet(), -1.01f, 0f) }
        assertFails { generator.generate(10, AiPteBand.entries.toSet(), 0f, 0f) }
    }

    private fun pteCandidates(count: Int): List<AiQueueCandidate> =
        List(count) { index ->
            AiQueueCandidate(
                mediaId = MediaId(index.toLong() + 1L),
                sourceBindingId = index.toLong() + 1L,
                playable = true,
                effectivelyBanned = false,
                arousal = 0f,
                arousalConfidence = 1f,
                valence = 0f,
                valenceConfidence = 1f,
                pte = -1f + 2f * index / (count - 1).coerceAtLeast(1),
                pteConfidence = 1f,
            )
        }

    private fun featuredCandidate(
        mediaId: Long,
        sourceBindingId: Long = mediaId,
        playable: Boolean = true,
        effectivelyBanned: Boolean = false,
        arousal: Float = 0f,
        arousalConfidence: Float = 1f,
        pte: Float? = null,
        pteConfidence: Float? = null,
    ) = AiQueueCandidate(
        mediaId = MediaId(mediaId),
        sourceBindingId = sourceBindingId,
        playable = playable,
        effectivelyBanned = effectivelyBanned,
        arousal = arousal,
        arousalConfidence = arousalConfidence,
        valence = 0f,
        valenceConfidence = 1f,
        pte = pte,
        pteConfidence = pteConfidence,
    )

    private fun onlyWeight(
        arousal: Double = 0.0,
        pte: Double = 0.0,
        directoryDislike: Double = 0.0,
        artistLike: Double = 0.0,
        history: Double = 0.0,
    ) = AiSelectionWeights(
        arousalDistance = arousal,
        valenceDistance = 0.0,
        pteDistance = pte,
        trackLikeDeficit = 0.0,
        trackDislikePenalty = 0.0,
        directoryLikeDeficit = 0.0,
        directoryDislikePenalty = directoryDislike,
        albumLikeDeficit = 0.0,
        albumDislikePenalty = 0.0,
        artistLikeDeficit = artistLike,
        artistDislikePenalty = 0.0,
        genreLikeDeficit = 0.0,
        genreDislikePenalty = 0.0,
        playHistoryPenalty = history,
    )

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
    }
}
