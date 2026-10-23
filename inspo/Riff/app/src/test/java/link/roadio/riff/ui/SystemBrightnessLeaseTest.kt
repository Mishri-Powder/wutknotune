package link.roadio.riff.ui

import link.roadio.riff.player.core.settings.ApplicationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBrightnessLeaseTest {
    @Test
    fun onlyExplicitLocalOptInCanUseTheWorkaround() {
        for (mode in ApplicationMode.entries) {
            assertFalse(systemBrightnessWorkaroundEnabled(false, mode))
            assertEquals(mode != ApplicationMode.AAOS, systemBrightnessWorkaroundEnabled(true, mode))
        }
    }

    @Test
    fun ownWritesDoNotCompoundAndReleaseRestoresBaseline() {
        val access = Access()
        val policy = SystemBrightnessLease(access)
        repeat(10) {
            policy.dim(30f)
            assertEquals(31, access.current)
            assertEquals(102, policy.baseline(access.current))
        }
        assertEquals(listOf(31), access.writes)
        policy.restore()
        assertEquals(102, access.current)
        assertNull(access.saved)
        policy.restore()
        assertEquals(listOf(31, 102), access.writes)
    }

    @Test
    fun externalBrightnessWinsAndCanBecomeANewDimBaseline() {
        val access = Access()
        val policy = SystemBrightnessLease(access)
        policy.dim(30f)
        access.current = 180
        assertEquals(180, policy.baseline(access.current))
        policy.dim(30f)
        assertEquals(54, access.current)
        policy.restore()
        assertEquals(180, access.current)
        policy.dim(30f)
        access.current = 200
        policy.restore()
        assertEquals(200, access.current)
        assertNull(access.saved)
    }

    @Test
    fun interruptionRecoveryChecksOwnershipAndDoesNotNeedTheEnableFlag() {
        val access = Access()
        SystemBrightnessLease(access).dim(30f)
        SystemBrightnessLease(access).restore()
        assertEquals(102, access.current)
        assertNull(access.saved)
        SystemBrightnessLease(access).dim(30f)
        access.current = 170
        SystemBrightnessLease(access).restore()
        assertEquals(170, access.current)
        assertNull(access.saved)
    }

    @Test
    fun changingThePercentWhileDimmedRetainsTheIndependentBaseline() {
        val access = Access()
        access.current = 128
        val policy = SystemBrightnessLease(access)
        policy.dim(10f)
        assertEquals(13, access.current)
        assertEquals(128, policy.baseline(access.current))
        policy.dim(15f)
        assertEquals(19, access.current)
        repeat(3) { policy.dim(0f) }
        assertEquals(0, access.current)
        assertEquals(128, policy.baseline(0))
        policy.dim(15f)
        assertEquals(19, access.current)
        access.current = 180
        policy.dim(15f)
        assertEquals(27, access.current)
        policy.restore()
        assertEquals(180, access.current)
        assertNull(access.saved)
    }

    @Test
    fun fullPercentAndZeroBaselineNeedNoGlobalWrite() {
        val access = Access()
        val policy = SystemBrightnessLease(access)
        assertTrue(policy.dim(100f))
        assertTrue(access.writes.isEmpty())
        assertNull(access.saved)
        access.current = 0
        policy.dim(0f)
        assertTrue(access.writes.isEmpty())
        assertNull(access.saved)
    }

    @Test
    fun missingPermissionOrUnknownBaselineCannotAcquireLease() {
        val access = Access()
        access.allowed = false
        assertFalse(SystemBrightnessLease(access).dim(30f))
        assertTrue(access.writes.isEmpty())
        assertNull(access.saved)
        access.allowed = true
        access.current = null
        assertFalse(SystemBrightnessLease(access).dim(30f))
        assertNull(access.saved)
        assertTrue(access.writes.isEmpty())
    }

    @Test
    fun revokedPermissionKeepsRecoveryRecordUntilItCanBeRestored() {
        val access = Access()
        val policy = SystemBrightnessLease(access)
        policy.dim(30f)
        access.allowed = false
        policy.restore()
        assertEquals(31, access.current)
        assertEquals(BrightnessLease(102, 31), access.saved)
        access.allowed = true
        policy.restore()
        assertEquals(102, access.current)
        assertNull(access.saved)
    }

    @Test
    fun persistenceFailurePreventsWriteAndWriteFailureKeepsRecoveryEvidence() {
        val access = Access()
        val policy = SystemBrightnessLease(access)
        access.failSave = true
        assertTrue(runCatching { policy.dim(30f) }.isFailure)
        assertTrue(access.writes.isEmpty())
        access.failSave = false
        access.failWrite = true
        assertTrue(runCatching { policy.dim(30f) }.isFailure)
        assertEquals(102, access.current)
        assertEquals(BrightnessLease(102, 31), access.saved)
        policy.restore()
        assertNull(access.saved)
        assertFalse(access.writes.any { it == 102 })
    }

    private class Access : SystemBrightnessAccess {
        var current: Int? = 102
        var allowed = true
        var saved: BrightnessLease? = null
        var failSave = false
        var failWrite = false
        val writes = mutableListOf<Int>()

        override fun read(): Int? = current

        override fun canWrite(): Boolean = allowed

        override fun write(value: Int): Boolean {
            if (value != saved?.baseline) assertEquals(value, saved?.applied)
            if (failWrite) return false
            writes += value
            current = value
            return true
        }

        override fun savedLease(): BrightnessLease? = saved

        override fun saveLease(value: BrightnessLease) {
            check(!failSave)
            saved = value
        }

        override fun clearLease() {
            saved = null
        }
    }
}
