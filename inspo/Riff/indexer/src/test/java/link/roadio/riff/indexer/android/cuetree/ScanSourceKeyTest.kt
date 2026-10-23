package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.Stamp
import link.roadio.riff.indexer.core.ipc.reliable.ScanAttempt
import link.roadio.riff.indexer.core.ipc.reliable.ScanAttemptJournal
import link.roadio.riff.indexer.core.ipc.reliable.ScanAttemptMode
import link.roadio.riff.indexer.core.ipc.reliable.SourceRecovery
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSourceKeyTest {
    @Test fun fullLongDocumentIsHashedAndBothStagesFitTheJournal() {
        val document = "content://provider/tree/root/document/" + "ф".repeat(255) + ".mp3"
        for (stage in listOf("L1", "L2")) {
            val key =
                scanSourceKey(document, Stamp(1, 10), stage, null) { bytes ->
                    assertEquals(document, bytes.toString(Charsets.UTF_8))
                    7L
                }
            assertEquals("0000000000000007", key.first())
            assertEquals(
                SourceRecovery.NORMAL,
                ScanAttemptJournal().begin(
                    ScanAttempt("owner", "attempt", key, ScanAttemptMode.NORMAL),
                ),
            )
        }
    }
}
