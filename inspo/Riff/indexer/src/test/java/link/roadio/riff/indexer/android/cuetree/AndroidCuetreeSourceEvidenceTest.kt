package link.roadio.riff.indexer.android.cuetree

import link.roadio.riff.indexer.core.replaygain.ReplayGainSource
import link.roadio.riff.indexer.level1.Level1ReplayGainMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidCuetreeSourceEvidenceTest {
    @Test fun externalGainAndDecoderHintSurviveAttributeEncoding() {
        val gain = Level1ReplayGainMetadata((-31).toShort(), 1.5f, 22, 0.9f, ReplayGainSource.R128)
        val encoded = AndroidCuetreeSourceEvidence.encode(gain, 0L)
        assertEquals(gain, AndroidCuetreeSourceEvidence.gain(encoded))
        assertEquals(0L, AndroidCuetreeSourceEvidence.hint(encoded))
    }
}
