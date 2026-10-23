package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCommandContextTest {
    @Test fun switchedProfileStoreAndReturnToOriginalProfileFenceCompletions() {
        val store = Any()
        val command = ProfileCommandContext(store, 1, 7)
        assertTrue(command.isCurrent(store, 1, 7))
        assertFalse(command.isCurrent(store, 2, 7))
        assertFalse(command.isCurrent(Any(), 1, 7))
        assertFalse(command.isCurrent(store, 1, 8))
        assertSame(store, command.store)
        assertEquals(1, command.profileId)
    }
}
