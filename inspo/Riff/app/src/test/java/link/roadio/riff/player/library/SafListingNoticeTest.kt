package link.roadio.riff.player.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafListingNoticeTest {
    private val active = SafListingNotice(1, listOf("Music"), 0, emptyList())

    @Test fun acknowledgementSurvivesLaterSubscribersAndRepeatedReports() {
        val session = SafListingNoticeSession()
        session.report(active)
        assertEquals(active, session.pending)
        session.acknowledge()
        session.report(active.copy(activeFolderLabels = listOf("Music")))
        assertNull(session.pending)
    }

    @Test fun healthyReportCannotDismissAnUnacknowledgedNoticeButNewEpisodeCanWarn() {
        val session = SafListingNoticeSession()
        session.report(active)
        session.report(null)
        assertEquals(active, session.pending)
        session.acknowledge()
        session.report(active)
        assertEquals(active, session.pending)
    }

    @Test fun uncertainFoldersDoNotClaimPlatformFailureOrSuggestReboot() {
        val uncertain = SafListingNotice(0, emptyList(), 1, listOf("Books"))
        assertEquals("Media folders unavailable", uncertain.title)
        assertFalse(uncertain.message.contains("Restart"))
        assertFalse(uncertain.message.contains("Android cannot"))
        assertTrue(uncertain.message.contains("other folders"))
    }
}
