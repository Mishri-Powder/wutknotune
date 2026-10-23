package link.roadio.riff.indexer.android.process

import link.roadio.riff.indexer.core.pipeline.PipelineProgressRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexerProgressStatusTest {
    @Test fun cumulativeKnownCountsKeepTheirValuesWhileSpeedUsesTheNewPeriod() {
        var now = 0L
        val rates = PipelineProgressRates({ now })
        val reporter = IndexerProgressReporter(rates)
        reporter.observe(IndexerProgressStage.ACQUISITION, 1800, 10414)
        now = 30_000
        rates.committed(now)
        reporter.observe(IndexerProgressStage.ACQUISITION, 1913, 10414)
        now = 35_000
        assertEquals("Updating media index: 1913 / 10414 checked | 22.6 media/s", reporter.text(IndexerProgressStage.ACQUISITION))
        assertEquals(
            "Updating media index: 1913 / 10414 checked | 22.6 media/s | Full scan",
            reporter.text(IndexerProgressStage.ACQUISITION, "Full scan"),
        )
    }

    @Test fun longWorkDetailKeepsCurrentAnalysisCountsAndRateVisible() {
        var now = 0L
        val rates = PipelineProgressRates({ now })
        val reporter = IndexerProgressReporter(rates)
        reporter.observe(IndexerProgressStage.ANALYSIS, 10, 100)
        now = 30_000
        rates.committed(now)
        reporter.observe(IndexerProgressStage.ANALYSIS, 15, 100)
        now = 35_000
        val detail = "Full loudness analysis: track.mp3"
        assertEquals("Analyzing audio: 15 / 100 tracks | 1.0 tracks/s | " + detail, reporter.text(IndexerProgressStage.ANALYSIS, detail))
        reporter.observe(IndexerProgressStage.ANALYSIS, 20, 100)
        now = 40_000
        assertEquals("Analyzing audio: 20 / 100 tracks | 1.0 tracks/s | " + detail, reporter.text(IndexerProgressStage.ANALYSIS, detail))
    }

    @Test fun unknownDiscoveryTotalIsOmittedAndEveryStageIncludesItsRate() {
        var now = 0L
        val reporter = IndexerProgressReporter(PipelineProgressRates({ now }))
        reporter.observe(IndexerProgressStage.DISCOVERY, 3)
        now = 1000
        assertEquals("Checking media folders: 3 checked | 3.0 folders/s", reporter.text(IndexerProgressStage.DISCOVERY))
        assertFalse(reporter.text(IndexerProgressStage.DISCOVERY).contains(" / "))
        reporter.observe(IndexerProgressStage.ACQUISITION, 20, 50)
        reporter.observe(IndexerProgressStage.ANALYSIS, 2, 10)
        assertTrue(reporter.text(IndexerProgressStage.ACQUISITION).endsWith("20.0 media/s"))
        assertTrue(reporter.text(IndexerProgressStage.ANALYSIS).endsWith("2.0 tracks/s"))
    }
}
