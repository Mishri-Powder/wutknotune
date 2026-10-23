package link.roadio.riff.indexer.android.ipc

import link.roadio.riff.ipc.player.IPlayerArtifactCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafListingNoticeBinderInstrumentedTest {
    private class Callback : IPlayerArtifactCallback.Stub() {
        override fun receiveReliablePacket(
            session: String,
            seq: Long,
            type: Int,
            fields: Array<out String>?,
        ) = Unit

        override fun receiveReliableAck(
            session: String,
            seq: Long,
            fields: Array<out String>?,
            attachment: link.roadio.riff.ipc.indexer.IndexerArtifactHandle?,
        ) = Unit

        var status = "initial"
        var activeCount = -1
        var active = emptyList<String>()
        var unknownCount = -1
        var unknown = emptyList<String>()

        override fun onIndexerArtifactAvailable(
            kind: String,
            volume: String,
            generation: Long,
        ) = Unit

        override fun onIndexerStatusChanged(status: String) {
            this.status = status
        }

        override fun onSafListingIssueDetected(
            activeRootCount: Int,
            activeFolderLabels: Array<out String>,
            unverifiedRootCount: Int,
            unverifiedFolderLabels: Array<out String>,
        ) {
            activeCount = activeRootCount
            active = activeFolderLabels.toList()
            unknownCount = unverifiedRootCount
            unknown = unverifiedFolderLabels.toList()
        }
    }

    @Test fun lateSubscribersReceiveBoundedAdmissionFactsAfterProgressClears() {
        val binder = AndroidIndexerArtifactBinder(AndroidIndexerArtifactCatalog { _, _ -> null })
        binder.publishSafListingIssues(List(12) { "\u0000Music " + "\uD83D\uDE00".repeat(60) }, listOf("\n\t"))
        binder.publishStatus("")
        val callback = Callback()
        binder.registerPlayerArtifactCallback(callback)
        assertEquals("", callback.status)
        assertEquals(12, callback.activeCount)
        assertEquals(8, callback.active.size)
        assertTrue(callback.active.all { it.length <= 80 && !it.any(Char::isISOControl) })
        assertFalse(Character.isHighSurrogate(callback.active.first().last()))
        assertEquals(1, callback.unknownCount)
        assertEquals(listOf("Media folder"), callback.unknown)
        binder.publishSafListingIssues(emptyList(), emptyList())
        assertEquals(0, callback.activeCount)
        assertEquals(0, callback.unknownCount)
        binder.unregisterPlayerArtifactCallback(callback)
    }
}
