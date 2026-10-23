package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Test

class HierarchySubtreeProjectionTest {
    @Test
    fun directOrdinaryAlbumPlayAddAndRestorationPreserveDistinctEqualAudioPositions() {
        val first = track(1)
        val second = first.copy(id = "track:1:second-position", label = "Repeated position")
        val source = album("positions", first, second)
        assertEquals(listOf(first.id, second.id), materializedHierarchyTracks(source).map(BrowserItem::id))
        val play = requireNotNull(contextualPlayProjection(source, second.id))
        assertEquals(listOf(first.id, second.id), play.tracks.map(BrowserItem::id))
        assertEquals(1, play.selectedQueueIndex)
        val root = BrowserItem("root", "Library", BrowserKind.ROOT, children = listOf(source))
        assertEquals(second.id, queueTrackForOccurrence(root, source.reactionKey, 1, MediaId(1))?.id)
    }

    @Test
    fun artistAndGenreMaterializationUsesCompleteOrderedAlbums() {
        val duplicate = track(2).copy(id = "track:2:duplicate")
        val source =
            BrowserItem(
                id = "artist:test",
                label = "Artist",
                kind = BrowserKind.ARTIST,
                children =
                    listOf(
                        album("a", track(1), track(2)),
                        album("b", duplicate, track(3).copy(playable = false), track(4)),
                    ),
            )

        assertEquals(
            listOf(1L, 2L, 4L).map(::MediaId),
            materializedHierarchyTracks(source).map(BrowserItem::mediaId),
        )
    }

    @Test
    fun selectedArtistAndGenreExposeAlbumsContextOnlyAfterDescent() {
        val root = BrowserItem("root", "Library", BrowserKind.ROOT)

        assertEquals(
            HierarchyRoot.ARTISTS,
            state(root, HierarchyRoot.ARTISTS, listOf("category:artists")).visibleHierarchyRoot(),
        )
        assertEquals(
            HierarchyRoot.ALBUMS,
            state(root, HierarchyRoot.ARTISTS, listOf("category:artists", "artist:1")).visibleHierarchyRoot(),
        )
        assertEquals(
            HierarchyRoot.ALBUMS,
            state(root, HierarchyRoot.GENRES, listOf("category:genres", "genre:1")).visibleHierarchyRoot(),
        )
    }

    private fun album(
        id: String,
        vararg tracks: BrowserItem,
    ) = BrowserItem("album:$id", "Album $id", BrowserKind.ALBUM, children = tracks.toList())

    private fun track(id: Long) =
        BrowserItem(
            id = "track:$id",
            label = "Track $id",
            kind = BrowserKind.TRACK,
            playable = true,
            mediaId = MediaId(id),
        )

    private fun state(
        root: BrowserItem,
        hierarchyRoot: HierarchyRoot,
        path: List<String>,
    ) = PlayerUiState(
        screen = PlayerScreen.HIERARCHY,
        root = root,
        browserPath = path,
        hierarchyRoot = hierarchyRoot,
        nowPlaying = NowPlayingUiState(),
    )
}
