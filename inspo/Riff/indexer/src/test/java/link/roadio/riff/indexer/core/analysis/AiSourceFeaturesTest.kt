package link.roadio.riff.indexer.core.analysis

import link.roadio.cuetree.GenreRef
import link.roadio.cuetree.GenreResolver
import link.roadio.cuetree.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSourceFeaturesTest {
    private val resolver = GenreResolver { if (it == "Rock") GenreRef(dictionaryId = 10L) else GenreRef(text = it) }
    private val all =
        mapOf(
            "riffPte" to listOf("0.1"),
            "riffPteConfidence" to listOf("0.0"),
            "riffArousal" to listOf("0.2"),
            "riffArousalConfidence" to listOf("0.8"),
            "riffValence" to listOf("-0.3"),
            "riffValenceConfidence" to listOf("1.0"),
            "riffGenre" to listOf("Rock"),
            "riffGenreConfidence" to listOf("0.7"),
        )

    @Test fun completeTagsAndZeroTrackGainRequireNoAudioWork() {
        val features = AiSourceFeatures.read(all, resolver)
        val revision = features.revision("model") { 99 }
        val ready =
            features
                .reconcile(
                    AnalysisRecord(
                        MediaId(1),
                        gainState = AnalysisState.AVAILABLE,
                        gainSource = AnalysisGainSource.REPLAY_GAIN,
                        trackGainQ8 = 0,
                    ),
                    revision,
                ).validate()
        assertTrue(features.complete)
        assertFalse(ready.needsAi(revision))
        assertFalse(ready.needsGain("gain"))
        assertEquals(0f, ready.pteConfidence)
    }

    @Test fun missingConfidenceAndInvalidRangesRemainUnresolved() {
        val features =
            AiSourceFeatures.read(
                all - "riffPteConfidence" +
                    mapOf(
                        "riffArousal" to listOf("2"),
                        "riffValenceConfidence" to listOf("NaN"),
                        "riffGenreConfidence" to listOf("-1"),
                    ),
                resolver,
            )
        assertFalse(features.present)
        assertTrue(features.reconcile(AnalysisRecord(MediaId(1)), "model").needsAi("model"))
    }

    @Test fun inferenceFillsMissingOutputsAndRetryKeepsAcceptedPairs() {
        val partial = AiSourceFeatures.read(all - "riffArousal" - "riffValence" - "riffGenre", resolver)
        val revision = partial.revision("model") { 98 }
        val prepared = partial.reconcile(AnalysisRecord(MediaId(1)), revision)
        assertTrue(prepared.needsAi(revision))
        val inferred =
            prepared.copy(
                aiState = AnalysisState.AVAILABLE,
                pte = 0.9f,
                pteConfidence = 1f,
                arousal = 0.6f,
                arousalConfidence = 0.5f,
            )
        val merged = partial.finish(inferred, AnalysisState.AVAILABLE, revision)
        assertEquals(0.1f, merged.pte)
        assertEquals(0.6f, merged.arousal)
        assertFalse(merged.needsAi(revision))
        val retry = partial.finish(prepared.withoutAi(AnalysisState.RETRYABLE_FAILURE), AnalysisState.RETRYABLE_FAILURE, revision)
        assertEquals(0.1f, retry.pte)
        assertTrue(retry.needsAi(revision))
        val permanent =
            partial.finish(
                prepared.withoutAi(AnalysisState.DETERMINISTIC_FAILURE),
                AnalysisState.DETERMINISTIC_FAILURE,
                revision,
            )
        assertEquals(0.1f, permanent.pte)
        assertFalse(permanent.needsAi(revision))
    }

    @Test fun removingOrChangingTagsInvalidatesDependentResults() {
        val features = AiSourceFeatures.read(all, resolver)
        val first = features.reconcile(AnalysisRecord(MediaId(1)), features.revision("model") { 1 })
        val absent = AiSourceFeatures()
        val fresh = absent.reconcile(first, absent.revision("model") { error("No hash for no tags") })
        assertNull(fresh.pte)
        assertTrue(fresh.needsAi("model"))
        val changed = AiSourceFeatures.read(all + ("riffPte" to listOf("0.9")), resolver)
        val next = changed.reconcile(first, changed.revision("model") { 2 })
        assertEquals(0.9f, next.pte)
    }
}
