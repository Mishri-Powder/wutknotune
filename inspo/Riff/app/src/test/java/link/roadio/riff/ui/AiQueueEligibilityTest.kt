package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Test

class AiQueueEligibilityTest {
    @Test
    fun `own and ancestor bans exclude every affected media id`() {
        val first = track("first", 1L)
        val second = track("second", 2L)
        val third = track("third", 3L)
        val bannedFolder =
            BrowserItem(
                "folder:banned",
                "Banned",
                BrowserKind.FOLDER,
                reactionKey = "folder:banned",
                children = listOf(first),
            )
        val openFolder =
            BrowserItem(
                "folder:open",
                "Open",
                BrowserKind.FOLDER,
                reactionKey = "folder:open",
                children = listOf(second, third),
            )
        val playlistCopy = BrowserItem("playlist:1", "Playlist", BrowserKind.PLAYLIST, children = listOf(track("copy", 4L)))
        val root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(bannedFolder, openFolder, playlistCopy))
        val reactions =
            mapOf(
                "folder:banned" to ReactionState(ownBanned = true),
                third.reactionKey to ReactionState(ownBanned = true),
                "copy" to ReactionState(ownBanned = true),
            )

        assertEquals(setOf(MediaId(1L), MediaId(3L)), effectiveAiBannedMediaIds(root, reactions))
    }

    private fun track(
        id: String,
        mediaId: Long,
    ) = BrowserItem(
        id = id,
        label = id,
        kind = BrowserKind.TRACK,
        playable = true,
        mediaId = MediaId(mediaId),
        reactionKey = id,
    )
}
