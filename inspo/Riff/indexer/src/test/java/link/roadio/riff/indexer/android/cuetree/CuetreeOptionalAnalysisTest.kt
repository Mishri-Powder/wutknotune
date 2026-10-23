package link.roadio.riff.indexer.android.cuetree

import link.roadio.riff.indexer.core.CancellationSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CuetreeOptionalAnalysisTest {
    private class Signal(
        var isCancelled: Boolean = false,
    ) : CancellationSignal {
        override val cancelled: Boolean get() = isCancelled

        override fun ensureAvailable() {
            if (cancelled) throw IllegalStateException("cancelled")
        }
    }

    @Test
    fun analysisFailureDoesNotFailTheFactualPass() {
        assertEquals(
            IllegalArgumentException::class.java,
            runOptionalCuetreeAnalysis(Signal()) { throw IllegalArgumentException("analysis") }?.javaClass,
        )
        assertNull(runOptionalCuetreeAnalysis(Signal()) {})
    }

    @Test(expected = IllegalStateException::class)
    fun cancellationDuringAnalysisStillStopsThePass() {
        val signal = Signal()
        runOptionalCuetreeAnalysis(signal) {
            signal.isCancelled = true
            throw IllegalArgumentException("analysis")
        }
    }
}
