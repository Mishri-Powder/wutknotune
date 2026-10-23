package link.roadio.riff.indexer.core.analysis

import link.roadio.cuetree.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CuetreeAnalysisCodecTest {
    @Test fun failedReanalysisClearsOnlyAiAndPreservesExternalAlbumGain() {
        val old =
            AnalysisRecord(
                MediaId(1),
                aiState = AnalysisState.AVAILABLE,
                arousal = 0.2f,
                arousalConfidence = 0.8f,
                valence = 0f,
                valenceConfidence = 1f,
                genres = listOf(AnalysisGenre(12, 0.6f)),
                albumGainQ8 = 0,
                albumSource = AnalysisGainSource.REPLAY_GAIN,
            )
        for (state in listOf(
            AnalysisState.PENDING,
            AnalysisState.RETRYABLE_FAILURE,
            AnalysisState.DETERMINISTIC_FAILURE,
            AnalysisState.COMPLETED_NO_RESULT,
        )) {
            val reset = old.withoutAi(state, "new-model").validate()
            assertEquals(null, reset.arousal)
            assertEquals(emptyList<AnalysisGenre>(), reset.genres)
            assertEquals(0.toShort(), reset.albumGainQ8)
        }
    }

    @Test fun decoderDependencyChangeReconsidersSuppressedOutcomes() {
        val completed =
            AnalysisRecord(
                MediaId(1),
                gainState = AnalysisState.COMPLETED_NO_RESULT,
                gainRevision = "gain",
                aiState = AnalysisState.DETERMINISTIC_FAILURE,
                modelRevision = "model",
                decoderHint = 1,
            )
        org.junit.Assert.assertFalse(completed.needsGain("gain"))
        org.junit.Assert.assertFalse(completed.needsAi("model"))
        org.junit.Assert.assertTrue(completed.needsGain("new-gain"))
        val changed = completed.withDecoderHint(2).validate()
        org.junit.Assert.assertTrue(changed.needsGain("gain"))
        org.junit.Assert.assertTrue(changed.needsAi("model"))
        assertEquals(completed, completed.withDecoderHint(1))
    }

    @Test fun exactFailureSuppressesStaleShortEvidence() {
        val root = MediaId(1)
        val full = MediaId(10, 2)
        val other = MediaId(10, 3)
        val snapshot =
            link.roadio.cuetree.Snapshot(
                1,
                "test",
                listOf(
                    link.roadio.cuetree.Node(
                        link.roadio.cuetree.NodeKind.DIRECTORY,
                        root,
                        source = link.roadio.cuetree.SourceInfo("Music", provider = "saf", locator = "content://music"),
                        life = link.roadio.cuetree.Life(1),
                    ),
                    link.roadio.cuetree.Node(
                        link.roadio.cuetree.NodeKind.MEDIA,
                        full,
                        root,
                        link.roadio.cuetree.SourceInfo("a.flac"),
                        life = link.roadio.cuetree.Life(1),
                        identityFlags = link.roadio.cuetree.Flags.SHRT,
                    ),
                    link.roadio.cuetree.Node(
                        link.roadio.cuetree.NodeKind.MEDIA,
                        other,
                        root,
                        link.roadio.cuetree.SourceInfo("b.flac"),
                        life = link.roadio.cuetree.Life(1),
                    ),
                ),
            )
        val alias = AnalysisRecord(MediaId(10), aiState = AnalysisState.AVAILABLE, genres = listOf(AnalysisGenre(12, 0.8f)))
        val exact = AnalysisRecord(full, aiState = AnalysisState.RETRYABLE_FAILURE)
        assertEquals(mapOf(full to exact), AnalysisSnapshot(1, listOf(alias, exact)).resolve(snapshot))
    }

    @Test fun shortAndExpandedKeysAndPresentZeroRoundTrip() {
        val snapshot =
            AnalysisSnapshot(
                7,
                listOf(
                    AnalysisRecord(
                        MediaId(10),
                        gainState = AnalysisState.AVAILABLE,
                        gainSource = AnalysisGainSource.REPLAY_GAIN,
                        trackGainQ8 = 0,
                        albumGainQ8 = 0,
                        albumSource = AnalysisGainSource.REPLAY_GAIN,
                        aiState = AnalysisState.AVAILABLE,
                        pte = 0f,
                        pteConfidence = 0f,
                        arousal = 0f,
                        arousalConfidence = 0f,
                        valence = 0f,
                        valenceConfidence = 0f,
                        genres = listOf(AnalysisGenre(-100001, 0.7f)),
                        decoderHint = 0L,
                    ),
                    AnalysisRecord(MediaId(10, 0), gainState = AnalysisState.RETRYABLE_FAILURE),
                ),
            )
        assertEquals(snapshot, CuetreeAnalysisCodec.decode(CuetreeAnalysisCodec.encode(snapshot)))
        assertThrows(IllegalArgumentException::class.java) {
            CuetreeAnalysisCodec.decode(CuetreeAnalysisCodec.encode(snapshot).clone().also { it[4] = 'X'.code.toByte() })
        }
    }
}
