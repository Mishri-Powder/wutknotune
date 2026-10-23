package link.roadio.riff.player.playback

import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.library.PlayerLibraryModel
import link.roadio.riff.player.storage.PlaybackCheckpoint
import link.roadio.riff.player.storage.PlaybackQueueKind
import link.roadio.riff.player.storage.PlaybackQueueOccurrence
import link.roadio.riff.player.storage.PlaybackQueueSnapshot
import link.roadio.riff.ui.BrowserItem
import link.roadio.riff.ui.BrowserKind
import link.roadio.riff.ui.PlaybackSourceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueuePreparationInstrumentedTest {
    @Test fun largeQueueKeepsExactMetadataAndCheckpointBindingPreference() {
        val tracks =
            (0 until 20_000).map {
                BrowserItem(
                    "track:$it",
                    "Track $it",
                    BrowserKind.TRACK,
                    playable = true,
                    mediaId = MediaId(it.toLong()),
                    sourceBindingId = it.toLong(),
                )
            }
        val album = BrowserItem("album", "Album", BrowserKind.ALBUM, children = tracks)
        val sources =
            tracks.associate {
                requireNotNull(it.mediaId) to
                    listOf(
                        PlaybackSourceRef(requireNotNull(it.sourceBindingId), 1, emptyList()),
                        PlaybackSourceRef(100_000 + requireNotNull(it.sourceBindingId), 2, emptyList()),
                    )
            }
        val model = PlayerLibraryModel(album, tracks, playbackSourcesByMediaId = sources, generation = 1, mediaIds = sources.keys)
        val queue =
            PlaybackQueueSnapshot(
                revisionId = 1,
                kind = PlaybackQueueKind.MANUAL,
                sourceProfileId = 1,
                temporary = false,
                occurrences =
                    tracks.mapIndexed {
                        i,
                        track,
                        ->
                        PlaybackQueueOccurrence(i.toLong() + 1, i, tracks.lastIndex - i, requireNotNull(track.mediaId))
                    },
                checkpoint = PlaybackCheckpoint(occurrenceId = 1001, chosenBindingId = 101_000),
                contextKey = album.reactionKey,
            )
        val start = System.nanoTime()
        val prepared = prepareQueue(queue, model)
        println("QUEUE_PREPARATION items=20000 millis=${(System.nanoTime() - start) / 1_000_000}")
        assertEquals(20_000, prepared.items.size)
        assertEquals(1000, prepared.startIndex)
        assertEquals("Track 1000", prepared.items[1000].mediaMetadata.title)
        assertEquals(
            101_000L,
            prepared.plan.occurrences
                .getValue(1001)
                .sources
                .first()
                .bindingId,
        )
        assertEquals(19_999, prepared.shuffleOrder.first())
        assertEquals(0, prepared.shuffleOrder.last())
        assertTrue(
            prepared.items
                .map { it.mediaId }
                .toSet()
                .size == 20_000,
        )
    }
}
