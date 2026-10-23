package link.roadio.riff.indexer.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmSpanTest {
    @Test fun clipsBothFragmentBoundsOnCountedFrames() {
        assertEquals(48_000 until 72_000, pcmSpanFrames(1_000_000, 2_000_000, 2_500_000, 48_000, 0, 96_000))
        assertTrue(pcmSpanFrames(1_000_000, 2_000_000, 2_500_000, 48_000, 72_000, 1_000).isEmpty())
        assertEquals(0 until 24_000, pcmSpanFrames(1_000_000, 2_000_000, 2_500_000, 48_000, 48_000, 48_000))
    }

    @Test fun nullableEndAndPrerollDoNotConsumeOutsideTheSpan() {
        assertEquals(48_000 until 96_000, pcmSpanFrames(1_000_000, 2_000_000, null, 48_000, 0, 96_000))
        assertEquals(0 until 1_000, pcmSpanFrames(0, 0, null, 48_000, 999_999, 1_000))
    }
}
