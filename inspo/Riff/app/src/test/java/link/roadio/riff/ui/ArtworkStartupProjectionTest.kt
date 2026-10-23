package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.storage.PlaybackCheckpoint
import link.roadio.riff.player.storage.PlaybackQueueKind
import link.roadio.riff.player.storage.PlaybackQueueOccurrence
import link.roadio.riff.player.storage.PlaybackQueueSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ArtworkStartupProjectionTest {
    @Test
    fun coldStartProjectionRetainsSelectedBindingForArtwork() {
        val queue =
            PlaybackQueueSnapshot(
                revisionId = 17L,
                kind = PlaybackQueueKind.MANUAL,
                sourceProfileId = 1,
                temporary = false,
                occurrences =
                    listOf(
                        PlaybackQueueOccurrence(
                            occurrenceId = 23L,
                            ordinal = 0,
                            shuffleOrdinal = 0,
                            mediaId = MediaId(41L),
                        ),
                    ),
                checkpoint =
                    PlaybackCheckpoint(
                        occurrenceId = 23L,
                        chosenBindingId = 59L,
                    ),
            )

        val nowPlaying = queue.toInitialNowPlaying()

        assertEquals(MediaId(41L), nowPlaying.mediaId)
        assertEquals(59L, nowPlaying.artwork.sourceBindingId)
        assertNull(nowPlaying.artwork.bitmap)
    }

    @Test
    fun unchangedTrackAndBindingRetainResolvedArtworkState() {
        val resolved = ArtworkUiState(sourceBindingId = 59L, origin = ArtworkOrigin.EMBEDDED)

        val projected =
            artworkForProjection(
                oldMediaId = MediaId(41L),
                oldArtwork = resolved,
                newMediaId = MediaId(41L),
                newBindingId = 59L,
            )

        assertSame(resolved, projected)
    }

    @Test
    fun unresolvedNewBindingCannotRetainThePreviousSourcesArtwork() {
        val resolved = ArtworkUiState(sourceBindingId = 59L, origin = ArtworkOrigin.EMBEDDED)
        assertEquals(
            ArtworkUiState(),
            artworkForProjection(MediaId(41L), resolved, MediaId(41L), null),
        )
    }

    @Test
    fun changedBindingGetsAnUnresolvedPlaceholder() {
        val resolved = ArtworkUiState(sourceBindingId = 59L, origin = ArtworkOrigin.EMBEDDED)

        val projected =
            artworkForProjection(
                oldMediaId = MediaId(41L),
                oldArtwork = resolved,
                newMediaId = MediaId(41L),
                newBindingId = 61L,
            )

        assertEquals(ArtworkUiState(sourceBindingId = 61L), projected)
    }
}
