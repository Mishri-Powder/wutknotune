package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.MediaId
import link.roadio.cuetree.NodeKind
import link.roadio.riff.indexer.core.analysis.AnalysisGainSource
import link.roadio.riff.indexer.core.analysis.AnalysisRecord
import link.roadio.riff.indexer.core.analysis.AnalysisState
import link.roadio.riff.indexer.core.replaygain.ReplayGainSource
import link.roadio.riff.indexer.level1.Level1ReplayGainMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisSourceReconciliationTest {
    private val measured =
        AnalysisRecord(
            MediaId(1),
            gainState = AnalysisState.AVAILABLE,
            gainSource = AnalysisGainSource.MEASURED,
            trackGainQ8 = 25,
            gainRevision = "gain",
            aiState = AnalysisState.AVAILABLE,
            pte = 0.2f,
            pteConfidence = 1f,
            modelRevision = "model",
            decoderHint = 10,
        )

    @Test fun changedHintReopensCompletedWorkBeforeWorksetSelection() {
        val record = reconcileAnalysisSource(measured, NodeKind.MEDIA, null, false, 20, "gain").validate()
        assertTrue(record.needsGain("gain"))
        assertTrue(record.needsAi("model"))
        assertNull(record.trackGainQ8)
        assertNull(record.pte)
        assertEquals(20L, record.decoderHint)
    }

    @Test fun unchangedOrMissingObservationPreservesAcceptedOutcomes() {
        assertEquals(measured, reconcileAnalysisSource(measured, NodeKind.MEDIA, null, false, 10, "gain"))
        assertEquals(measured, reconcileAnalysisSource(measured, NodeKind.MEDIA, null, false, null, "gain"))
    }

    @Test fun changedHintPreservesFreshExternalGainButInvalidatesAi() {
        val external = Level1ReplayGainMetadata(30, 1f, 40, 1.1f, ReplayGainSource.REPLAY_GAIN)
        val record = reconcileAnalysisSource(measured, NodeKind.MEDIA, external, true, 20, "gain").validate()
        assertFalse(record.needsGain("gain"))
        assertTrue(record.needsAi("model"))
        assertEquals(external.trackGainQ8, record.trackGainQ8)
        assertEquals(external.albumGainQ8, record.albumGainQ8)
    }

    @Test fun fragmentCannotBorrowWholeFileTrackGain() {
        val external = Level1ReplayGainMetadata(30, 1f, 40, 1.1f, ReplayGainSource.REPLAY_GAIN)
        val record = reconcileAnalysisSource(measured, NodeKind.FRAGMENT, external, true, 20, "gain").validate()
        assertTrue(record.needsGain("gain"))
        assertNull(record.trackGainQ8)
        assertEquals(external.albumGainQ8, record.albumGainQ8)
        assertEquals(AnalysisGainSource.REPLAY_GAIN, record.albumSource)
    }

    @Test fun withdrawnExternalTagReopensGainWithoutDiscardingAcceptedAi() {
        val tagged = measured.copy(gainSource = AnalysisGainSource.REPLAY_GAIN)
        val record = reconcileAnalysisSource(tagged, NodeKind.MEDIA, null, true, 10, "gain").validate()
        assertTrue(record.needsGain("gain"))
        assertFalse(record.needsAi("model"))
    }

    @Test fun explicitFragmentGainSuppressesMeasurementAndRemovalReopensIt() {
        val clip = Level1ReplayGainMetadata(0, null, null, null, ReplayGainSource.R128)
        val complete = reconcileFragmentGain(measured, clip, "gain").validate()
        assertEquals(0.toShort(), complete.trackGainQ8)
        assertFalse(complete.needsGain("gain"))
        assertTrue(reconcileFragmentGain(complete, null, "gain").needsGain("gain"))
    }
}
