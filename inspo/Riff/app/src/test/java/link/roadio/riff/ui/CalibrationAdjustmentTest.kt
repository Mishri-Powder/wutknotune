package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationAdjustmentTest {
    @Test fun `drag activates only at raw movement threshold`() {
        assertEquals(false, CalibrationAdjustment.hasReachedActivationDistance(5f, 5f, 8f))
        assertEquals(true, CalibrationAdjustment.hasReachedActivationDistance(6f, 8f, 10f))
    }

    @Test fun `candidate can expand beyond conservative startup size`() {
        assertEquals(840f, CalibrationAdjustment.apply(790f, 50f, 790f, 912f, 0.2f), 0f)
    }

    @Test fun `candidate remains bounded by raw canvas and minimum`() {
        assertEquals(912f, CalibrationAdjustment.apply(900f, 50f, 790f, 912f, 0.2f), 0f)
        assertEquals(158f, CalibrationAdjustment.apply(200f, -100f, 790f, 912f, 0.2f), 0f)
    }
}
