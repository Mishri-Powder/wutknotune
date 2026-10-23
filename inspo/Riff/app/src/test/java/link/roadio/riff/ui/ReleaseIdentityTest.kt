package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseIdentityTest {
    @Test
    fun nonFinalChannelsExposeExactImmutableLabels() {
        val date = LocalDate.of(2026, 9, 12)
        val labels =
            ReleaseChannel.entries.associateWith { channel ->
                ReleaseIdentity(channel, date).announcementLabel
            }

        assertEquals("Debug: 260912", labels.getValue(ReleaseChannel.DEBUG))
        assertEquals("Test: 260912", labels.getValue(ReleaseChannel.TEST))
        assertEquals("Alpha: 260912", labels.getValue(ReleaseChannel.ALPHA))
        assertEquals("Beta: 260912", labels.getValue(ReleaseChannel.BETA))
        assertEquals("RC: 260912", labels.getValue(ReleaseChannel.RC))
        assertNull(labels.getValue(ReleaseChannel.FINAL))
    }

    @Test
    fun preRcReleaseWarnsAtStartOfThirdCalendarMonthBoundary() {
        val identity = ReleaseIdentity(ReleaseChannel.BETA, LocalDate.of(2026, 11, 30))

        assertFalse(identity.requiresAgeWarning(LocalDate.of(2027, 2, 27)))
        assertTrue(identity.requiresAgeWarning(LocalDate.of(2027, 2, 28)))
        assertEquals("Old Beta build", identity.ageWarningTitle)
        assertEquals(
            "This Beta build is more than three months old. It may be outdated.",
            identity.ageWarningMessage,
        )
    }

    @Test
    fun rcAndFinalNeverShowAgeWarning() {
        val releaseDate = LocalDate.of(2000, 1, 1)

        assertFalse(ReleaseIdentity(ReleaseChannel.RC, releaseDate).requiresAgeWarning(LocalDate.of(9999, 12, 31)))
        assertFalse(ReleaseIdentity(ReleaseChannel.FINAL, releaseDate).requiresAgeWarning(LocalDate.of(9999, 12, 31)))
    }

    @Test
    fun warningSessionIsAcknowledgedOnlyForCurrentProcessLifetime() {
        val session = ReleaseAgeWarningSession()
        val identity = ReleaseIdentity(ReleaseChannel.ALPHA, LocalDate.of(2026, 1, 1))

        assertTrue(session.shouldShow(identity, LocalDate.of(2026, 4, 1)))
        session.acknowledge()
        assertFalse(session.shouldShow(identity, LocalDate.of(2026, 4, 1)))
        assertTrue(ReleaseAgeWarningSession().shouldShow(identity, LocalDate.of(2026, 4, 1)))
        assertFalse(shouldHandleBackInApp(PlayerScreen.MAIN))
        assertTrue(shouldHandleBackInApp(PlayerScreen.SETTINGS))
    }
}
