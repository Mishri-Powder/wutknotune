package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuegrain.ByteArrayInput
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.CuegrainMetadataReader
import link.roadio.cuetree.MediaId
import link.roadio.riff.indexer.core.analysis.AnalysisRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FragmentGainTagsTest {
    @Test fun cueTrackGainIsDecodedOnceAndRetainedWithItsSection() {
        val text =
            """
            FILE "image.mp3" WAVE
              TRACK 01 AUDIO
                REM REPLAYGAIN_TRACK_GAIN "0 dB"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                REM REPLAYGAIN_TRACK_GAIN "-3 dB"
                INDEX 01 00:10:00
            """.trimIndent()
        val reader = AdmittedMetadataReader(CuegrainMetadataReader()) { _, _ -> error("CUE is not audio") }
        val sections =
            reader
                .read(ByteArrayInput(text.toByteArray(), "disc.cue"), "cue", Cancellation())
                .tables
                .single()
                .sections
        val zero = AndroidCuetreeSourceEvidence.gain(sections[0].attributes)
        assertEquals(0.toShort(), zero!!.trackGainQ8)
        assertFalse(reconcileFragmentGain(AnalysisRecord(MediaId(1)), zero, "gain").needsGain("gain"))
        assertEquals((-3 * 256).toShort(), AndroidCuetreeSourceEvidence.gain(sections[1].attributes)!!.trackGainQ8)
    }
}
