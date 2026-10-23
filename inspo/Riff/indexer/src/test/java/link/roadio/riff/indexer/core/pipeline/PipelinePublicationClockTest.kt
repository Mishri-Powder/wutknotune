package link.roadio.riff.indexer.core.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelinePublicationClockTest {
    @Test fun oneDeadlineSpansEveryStageAndOnlySuccessfulBatchesAdvanceIt() {
        var now = 100L
        val clock = PipelinePublicationClock({ now })
        now = 30_099L
        assertFalse(clock.due())
        now++ // L0 -> L1 has no reset.
        assertTrue(clock.due())
        now += 4_000 // Unchanged or failed assessments do not consume the deadline.
        assertTrue(clock.due())
        clock.committed()
        now += 59_999 // L1 -> L2 has no reset.
        assertFalse(clock.due())
        now++
        assertTrue(clock.due())
        clock.committed()
        assertFalse(clock.due())
    }
}
