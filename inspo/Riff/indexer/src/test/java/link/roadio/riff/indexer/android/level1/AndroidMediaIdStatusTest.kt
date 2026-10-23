package link.roadio.riff.indexer.android.level1

import link.roadio.riff.indexer.core.media.MediaIdV32FullScanReason
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidMediaIdStatusTest {
    @Test
    fun statusUsesBoundedFilenameAndPublishedReason() {
        val longName = "x".repeat(100) + ".mp3"

        val status = fullMediaIdScanStatus("root/folder/$longName", MediaIdV32FullScanReason.OVERLAP)

        assertEquals("Full Media.ID scan (overlap): ${longName.take(88)}", status)
    }

    @Test
    fun statusAcceptsWindowsStyleRelativePaths() {
        assertEquals(
            "Full Media.ID scan (tail): track.m4a",
            fullMediaIdScanStatus("folder\\track.m4a", MediaIdV32FullScanReason.TAIL),
        )
    }
}
