package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.ScanCheckpointKind
import link.roadio.cuetree.ScanProgress
import link.roadio.cuetree.ScanStage
import link.roadio.riff.indexer.android.process.IndexerProgressReporter
import link.roadio.riff.indexer.core.pipeline.PipelineProgressRates
import org.junit.Assert.assertTrue
import org.junit.Test

class CuetreeProgressTest {
    @Test fun stageControlsTextEvenAtDirectoryAndTableBoundaries() {
        val reporter = IndexerProgressReporter(PipelineProgressRates({ 0L }))
        val discovery = ScanProgress(0, 3, 0, ScanCheckpointKind.DIRECTORY, stage = ScanStage.DISCOVERY)
        assertTrue(reporter.text(reporter.observe(discovery)).startsWith("Checking media folders"))
        for (kind in ScanCheckpointKind.entries) {
            val acquisition = discovery.copy(kind = kind, stage = ScanStage.ACQUISITION, totalMedia = 5)
            val report = reporter.text(reporter.observe(acquisition))
            assertTrue(report.startsWith("Updating media index"))
            assertTrue(report.endsWith("0.0 media/s"))
        }
    }
}
