package link.roadio.riff.player.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PlayerStateHeaderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun unreadableHeaderPropagatesAccessFailureInsteadOfReportingCorruption() {
        val absent = File(temporary.root, "removed.sqlite")
        assertThrows(IOException::class.java) { PlayerStateRecovery.hasSqliteHeader(absent) }
        assertFalse(absent.exists())
    }

    @Test
    fun readableInvalidOrTruncatedHeadersAreCorruptionEvidence() {
        val file = temporary.newFile()
        for (bytes in listOf(byteArrayOf(), "SQLite".toByteArray(), ByteArray(16))) {
            file.writeBytes(bytes)
            assertFalse(PlayerStateRecovery.hasSqliteHeader(file))
        }
        file.writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(32))
        assertTrue(PlayerStateRecovery.hasSqliteHeader(file))
    }
}
