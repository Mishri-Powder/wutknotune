package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.selection.AiFeaturePoint
import link.roadio.riff.player.core.selection.AiPreference
import link.roadio.riff.player.library.PlayerPteFeature
import link.roadio.riff.player.storage.AiPlaybackHistorySnapshot
import link.roadio.riff.player.storage.PlaybackStartCount
import org.junit.Assert.assertEquals
import org.junit.Test

class AiSelectionSnapshotTest {
    @Test fun retainedShortHistoryFollowsOnlyItsFullIdentity() {
        val short = MediaId(1, 0, false)
        val full = MediaId(1, 2, true)
        val other = MediaId(1, 3, true)
        val tracks =
            listOf(full, other).mapIndexed { index, id ->
                BrowserItem("track:$index", "Track", BrowserKind.TRACK, playable = true, mediaId = id, sourceBindingId = index.toLong())
            }
        val input =
            AiSelectionCandidateInput(
                tracks,
                BrowserItem("root", "Library", BrowserKind.ROOT),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptySet(),
                emptySet(),
                mapOf(short to full),
            )
        val history = AiPlaybackHistorySnapshot(7, listOf(PlaybackStartCount(short, 6, 2), PlaybackStartCount(full, 7, 1)))
        val candidates = buildAiQueueCandidates(input, history).associateBy { it.mediaId }
        assertEquals(3L, candidates.getValue(full).playHistory.sumOf { it.starts })
        assertEquals(emptyList<Any>(), candidates.getValue(other).playHistory)
        assertEquals(short, history.starts.first().mediaId)
    }

    @Test
    fun `snapshot projects direct entities nearest-first folders features and history`() {
        val track = BrowserItem("track:1", "Track", BrowserKind.TRACK, playable = true, mediaId = MediaId(1), sourceBindingId = 11)
        val folderTrack = track.copy(id = "folder-track:1")
        val album = BrowserItem("album:1", "Album", BrowserKind.ALBUM, reactionKey = "album:1", children = listOf(track))
        val artist = BrowserItem("artist:1", "Artist", BrowserKind.ARTIST, reactionKey = "artist:1", children = listOf(album))
        val genre = BrowserItem("genre:1", "Genre", BrowserKind.GENRE, reactionKey = "genre:1", children = listOf(album))
        val childFolder = BrowserItem("folder:b", "B", BrowserKind.FOLDER, children = listOf(folderTrack))
        val parentFolder = BrowserItem("folder:a", "A", BrowserKind.FOLDER, children = listOf(childFolder))
        val root =
            BrowserItem(
                "root",
                "Library",
                BrowserKind.ROOT,
                children =
                    listOf(
                        BrowserItem("category:albums", "Albums", BrowserKind.CATEGORY, children = listOf(album)),
                        BrowserItem("category:artists", "Artists", BrowserKind.CATEGORY, children = listOf(artist)),
                        BrowserItem("category:genres", "Genres", BrowserKind.CATEGORY, children = listOf(genre)),
                        BrowserItem("category:folders", "Folders", BrowserKind.CATEGORY, children = listOf(parentFolder)),
                    ),
            )
        val reactions =
            mapOf(
                "track:1" to ReactionState(Preference.LIKED),
                "album:1" to ReactionState(Preference.DISLIKED),
                "artist:1" to ReactionState(Preference.LIKED),
                "genre:1" to ReactionState(Preference.DISLIKED),
                "folder:a" to ReactionState(Preference.DISLIKED),
                "folder:b" to ReactionState(Preference.LIKED),
            )
        val history = AiPlaybackHistorySnapshot(7, listOf(PlaybackStartCount(MediaId(1), 6, 2)))

        val candidate =
            buildAiQueueCandidates(
                AiSelectionCandidateInput(
                    tracks = listOf(track),
                    root = root,
                    reactions = reactions,
                    featuresByMediaId = mapOf(MediaId(1L) to AiFeaturePoint(MediaId(1), -0.2f, 0.3f, 0.8f, 0.9f)),
                    pteByMediaId = mapOf(MediaId(1L) to PlayerPteFeature(0.4f, 0.7f)),
                    audiobookMediaIds = emptySet(),
                    effectiveBookMediaIds = emptySet(),
                ),
                history,
            ).single()

        assertEquals(AiPreference.LIKED, candidate.trackPreference)
        assertEquals(listOf(listOf(AiPreference.LIKED, AiPreference.DISLIKED)), candidate.directoryPreferencePaths)
        assertEquals(listOf(AiPreference.DISLIKED), candidate.albumPreferences)
        assertEquals(listOf(AiPreference.LIKED), candidate.artistPreferences)
        assertEquals(listOf(AiPreference.DISLIKED), candidate.genrePreferences)
        assertEquals(0.8f, candidate.arousalConfidence)
        assertEquals(0.7f, candidate.pteConfidence)
        assertEquals(2L, candidate.playHistory.single().starts)
    }
}
