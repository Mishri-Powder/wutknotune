package link.roadio.riff.player.storage

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlayerSafGrantFlagsTest {
    private val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    @Test fun newWritableGrantRetainsBothOfferedCapabilities() {
        assertEquals(read or write, flagsToPersist(read or write, 0))
        assertEquals(read, flagsToPersist(read, 0))
    }

    @Test fun reselectionCanUpgradeReadOnlyGrantWithoutReplacingIt() {
        assertEquals(write, flagsToPersist(read or write, read))
        assertEquals(0, flagsToPersist(read, read))
        assertEquals(0, flagsToPersist(read or write, read or write))
    }

    @Test fun resultWithoutReadCapabilityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { flagsToPersist(write, 0) }
    }
}
