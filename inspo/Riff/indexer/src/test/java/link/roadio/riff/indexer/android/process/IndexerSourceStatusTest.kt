package link.roadio.riff.indexer.android.process

import link.roadio.riff.indexer.core.ipc.INDEXER_STATUS_MAX_CHARS
import link.roadio.riff.indexer.core.pipeline.PipelineProgressRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexerSourceStatusTest {
    @Test fun suspectRetainsLiveCountersUntilItsAttemptEnds() {
        var now = 0L
        val reporter = IndexerProgressReporter(PipelineProgressRates({ now }))
        reporter.observe(IndexerProgressStage.ACQUISITION, 10, 100)
        val events = mutableListOf<String>()
        val status = IndexerSourceStatus(reporter, events::add)
        now = 1000
        status.beginSuspect(IndexerProgressStage.ACQUISITION, "folder/crash.mp3")
        assertEquals("Updating media index: 10 / 100 checked | 10.0 media/s | Suspect file: crash.mp3", events.single())
        reporter.observe(IndexerProgressStage.ACQUISITION, 11, 100)
        assertTrue(status.text(IndexerProgressStage.ACQUISITION).endsWith("Suspect file: crash.mp3"))
        status.endSuspect(IndexerProgressStage.ACQUISITION, "folder/crash.mp3")
        assertFalse(status.text(IndexerProgressStage.ACQUISITION).contains("Suspect"))
        assertTrue(events.none { it.contains("FERR") })
    }

    @Test fun acceptedFailureSupersedesSuspectAndKeepsTheNextConsumedCount() {
        var now = 0L
        val reporter = IndexerProgressReporter(PipelineProgressRates({ now }))
        reporter.observe(IndexerProgressStage.ANALYSIS, 5, 20)
        val events = mutableListOf<String>()
        val status = IndexerSourceStatus(reporter, events::add)
        now = 1000
        status.beginSuspect(IndexerProgressStage.ANALYSIS, "bad.mp3")
        status.failed(IndexerProgressStage.ANALYSIS, "bad.mp3")
        reporter.observe(IndexerProgressStage.ANALYSIS, 6, 20)
        assertEquals("Analyzing audio: 6 / 20 tracks | 6.0 tracks/s | FERR file: bad.mp3", status.text(IndexerProgressStage.ANALYSIS))
        status.endSuspect(IndexerProgressStage.ANALYSIS, "bad.mp3")
        assertEquals("Analyzing audio: 6 / 20 tracks | 6.0 tracks/s", status.text(IndexerProgressStage.ANALYSIS))
    }

    @Test fun everyDiagnosticUsesOnlyABoundedSafeFilename() {
        assertEquals("FERR file: bad name.mp3", sourceFileStatus("FERR file", "content://provider/root/bad\n|name.mp3"))
        val emoji = "\uD83D\uDE00"
        assertEquals("Suspect file: " + emoji.repeat(88), sourceFileStatus("Suspect file", "folder\\" + emoji.repeat(100)))
        assertEquals("Full loudness analysis: Current file", sourceFileStatus("Full loudness analysis", "\n"))
    }

    @Test fun longUnicodeFilenameCannotOverflowBinderOrRemoveCountsAndRate() {
        val reporter = IndexerProgressReporter(PipelineProgressRates({ 1000L }, 0L))
        reporter.observe(IndexerProgressStage.ACQUISITION, 100, 200)
        val events = mutableListOf<String>()
        val status = IndexerSourceStatus(reporter, events::add)
        val emoji = "\uD83D\uDE00"
        status.failed(IndexerProgressStage.ACQUISITION, emoji.repeat(100))
        val line = events.single()
        assertTrue(line.length <= INDEXER_STATUS_MAX_CHARS)
        assertTrue(line.startsWith(reporter.text(IndexerProgressStage.ACQUISITION) + " | FERR file: "))
        assertTrue(line.endsWith("…"))
        assertFalse(Character.isHighSurrogate(line[line.lastIndex - 1]))
    }
}
