package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.storage.BookmarkMark
import link.roadio.riff.player.storage.PermanentPlaylist
import link.roadio.riff.player.storage.PlaylistKind
import link.roadio.riff.player.storage.PlaylistOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistUiProjectionTest {
    @Test
    fun `Music Playlists launcher follows active profile playlist existence`() {
        fun enabled(playlists: List<PermanentPlaylist>): Boolean =
            projectPlaylists(libraryRoot(), playlists, emptyList())
                .children
                .single { it.id == HierarchyRoot.PLAYLISTS.categoryId }
                .enabled

        assertFalse(enabled(emptyList()))
        assertTrue(enabled(listOf(playlist(2L, "Saved", 20L))))
        assertFalse(enabled(emptyList()))
    }

    @Test
    fun `selector starts with New Playlist and preserves canonical store order`() {
        val root =
            projectPlaylists(
                libraryRoot(),
                listOf(
                    playlist(2L, "Alpha", 20L),
                    playlist(7L, "Road", 70L),
                ),
                emptyList(),
            )

        assertEquals(
            listOf(NEW_PLAYLIST_LABEL, "Alpha", "Road"),
            playlistSelectorItems(root).map(PlaylistSelectorUiItem::label),
        )
        assertTrue(
            root.children
                .single { it.id == HierarchyRoot.PLAYLISTS.categoryId }
                .children
                .none(BrowserItem::playable),
        )
    }

    @Test
    fun `projection preserves duplicate occurrences and marks missing media unavailable`() {
        val available =
            BrowserItem(
                id = "track:7",
                label = "Seven",
                kind = BrowserKind.TRACK,
                playable = true,
                mediaId = MediaId(7L),
                sourceBindingId = 700L,
            )
        val projected =
            projectPlaylists(
                libraryRoot(),
                listOf(
                    PermanentPlaylist(
                        playlistId = 4L,
                        name = "Road",
                        revisionId = 9L,
                        occurrences =
                            listOf(
                                PlaylistOccurrence(101L, 0, MediaId(7L)),
                                PlaylistOccurrence(102L, 1, MediaId(7L)),
                                PlaylistOccurrence(103L, 2, MediaId(-1L)),
                            ),
                    ),
                ),
                listOf(available, BrowserItem("track:missing", "Missing", BrowserKind.TRACK, playable = false, mediaId = MediaId(-1L))),
            )
        val playlist =
            projected.children
                .single { it.id == HierarchyRoot.PLAYLISTS.categoryId }
                .children
                .single()

        assertEquals(listOf(MediaId(7L), MediaId(7L), MediaId(-1L)), playlist.children.map(BrowserItem::mediaId))
        assertEquals(
            3,
            playlist.children
                .map(BrowserItem::id)
                .distinct()
                .size,
        )
        assertTrue(playlist.children[0].playable)
        assertFalse(playlist.children[2].playable)
        assertEquals("Missing", playlist.children[2].label)
    }

    @Test
    fun `Book mode displays only marks in frozen order and disables unavailable mark play`() {
        val book =
            PermanentPlaylist(
                8L,
                "Bookmarks for Pilot",
                30L,
                listOf(PlaylistOccurrence(1L, 0, MediaId(7L)), PlaylistOccurrence(2L, 1, MediaId(8L))),
                PlaylistKind.BOOK,
                "album:8",
                listOf(BookmarkMark(11L, 1, 3_210_000L, "2. Voyage"), BookmarkMark(12L, 0, 15_000_000L, "1. Start")),
            )
        val root =
            projectPlaylists(
                libraryRoot(),
                listOf(book),
                listOf(
                    BrowserItem("track:7", "Start", BrowserKind.TRACK, playable = true, mediaId = MediaId(7L)),
                    BrowserItem("track:8", "Voyage", BrowserKind.TRACK, playable = false, mediaId = MediaId(8L)),
                ),
                contentMode = ContentMode.AUDIOBOOK,
            )
        val category = root.children.single { it.id == HierarchyRoot.PLAYLISTS.categoryId }
        assertEquals("Bookmarks", category.label)
        assertTrue(category.enabled)
        val collection = category.children.single()
        assertEquals("Pilot", collection.label)
        assertTrue(collection.enabled)
        assertFalse(collection.playable)
        assertEquals(listOf(RowActionKind.PLAY, RowActionKind.DELETE), rowActionKinds(collection))
        val marks = collection.children
        assertEquals(listOf("1. Start, 0:15", "2. Voyage, 0:03"), marks.map(BrowserItem::label))
        assertTrue(marks.first().playable)
        assertFalse(marks.last().playable)
        assertEquals(listOf(RowActionKind.PLAY, RowActionKind.REMOVE_BOOKMARK), rowActionKinds(marks.last()))
        assertEquals(listOf(NEW_PLAYLIST_LABEL), playlistSelectorItems(root).map(PlaylistSelectorUiItem::label))
    }

    @Test
    fun `Book collection Play stays disabled even when an unmarked part is available`() {
        val book =
            PermanentPlaylist(
                8L,
                "Bookmarks for Pilot",
                30L,
                listOf(PlaylistOccurrence(1L, 0, MediaId(7L)), PlaylistOccurrence(2L, 1, MediaId(8L))),
                PlaylistKind.BOOK,
                "album:8",
                listOf(BookmarkMark(11L, 0, 15_000_000L, "1. Start")),
            )
        val root =
            projectPlaylists(
                libraryRoot(),
                listOf(book),
                listOf(
                    BrowserItem("track:7", "Start", BrowserKind.TRACK, playable = false, mediaId = MediaId(7L)),
                    BrowserItem("track:8", "Voyage", BrowserKind.TRACK, playable = true, mediaId = MediaId(8L)),
                ),
                contentMode = ContentMode.AUDIOBOOK,
            )
        val collection =
            root.children
                .single { it.id == HierarchyRoot.PLAYLISTS.categoryId }
                .children
                .single()
        assertTrue(collection.enabled)
        assertFalse(collection.playable)
        assertFalse(collection.children.single().playable)
    }

    @Test
    fun `row action sets match each playlist scope exactly`() {
        val ordinary = BrowserItem("album:1", "Album", BrowserKind.ALBUM, playable = true)
        val playlist = BrowserItem("playlist:1", "Road", BrowserKind.PLAYLIST, playable = true, playlistId = 1L)
        val occurrence =
            BrowserItem(
                "playlist:1:occurrence:2",
                "Track",
                BrowserKind.TRACK,
                playable = true,
                mediaId = MediaId(7L),
                playlistId = 1L,
                playlistOccurrenceId = 2L,
            )

        assertEquals(
            listOf(RowActionKind.PLAY, RowActionKind.ADD_TO_PLAYLIST),
            rowActionKinds(ordinary),
        )
        assertEquals(
            listOf(RowActionKind.PLAY, RowActionKind.RENAME, RowActionKind.DELETE),
            rowActionKinds(playlist),
        )
        assertEquals(
            listOf(RowActionKind.PLAY, RowActionKind.ADD_TO_PLAYLIST, RowActionKind.REMOVE_FROM_PLAYLIST),
            rowActionKinds(occurrence),
        )
    }

    @Test
    fun `successful Add opens committed destination playlist on first page`() {
        val projected =
            projectPlaylists(
                libraryRoot(),
                listOf(
                    PermanentPlaylist(
                        playlistId = 7L,
                        name = "Road",
                        revisionId = 20L,
                        occurrences = listOf(PlaylistOccurrence(101L, 0, MediaId(42L))),
                    ),
                ),
                listOf(
                    BrowserItem(
                        id = "track:42",
                        label = "Forty Two",
                        kind = BrowserKind.TRACK,
                        playable = true,
                        mediaId = MediaId(42L),
                    ),
                ),
            )
        val old =
            PlayerUiState(
                screen = PlayerScreen.PLAYLIST_SELECTOR,
                root = libraryRoot(),
                browserPath = listOf(HierarchyRoot.ALBUMS.categoryId, "album:1"),
                hierarchyRoot = HierarchyRoot.ALBUMS,
                page = 3,
                focusedItemId = "album:1",
                nowPlaying = NowPlayingUiState(),
                actionsItemId = "album:1",
                playlistSourceItemId = "album:1",
                playlistNameRequest = PlaylistNameUiRequest(PlaylistNameOperation.CREATE),
                playlistNameDraft = "Road",
                playlistMutationInProgress = true,
            )

        val result = openCommittedPlaylist(old, projected, 7L, "Added")

        assertEquals(PlayerScreen.HIERARCHY, result.screen)
        assertEquals(
            listOf(HierarchyRoot.PLAYLISTS.categoryId, playlistItemId(7L)),
            result.browserPath,
        )
        assertEquals(HierarchyRoot.PLAYLISTS, result.hierarchyRoot)
        assertEquals(0, result.page)
        assertEquals(playlistOccurrenceItemId(7L, 101L), result.focusedItemId)
        assertEquals("Road", result.currentNode().label)
        assertEquals("Added", result.status)
        assertEquals(null, result.actionsItemId)
        assertEquals(null, result.playlistSourceItemId)
        assertEquals(null, result.playlistNameRequest)
        assertEquals("", result.playlistNameDraft)
        assertFalse(result.playlistMutationInProgress)
    }

    private fun playlist(
        id: Long,
        name: String,
        revision: Long,
    ) = PermanentPlaylist(id, name, revision, listOf(PlaylistOccurrence(id + 1L, 0, MediaId(id))))

    private fun libraryRoot(): BrowserItem =
        BrowserItem(
            id = "root",
            label = "Library",
            kind = BrowserKind.ROOT,
            children =
                HierarchyRoot.entries.map { hierarchy ->
                    BrowserItem(
                        id = hierarchy.categoryId,
                        label = hierarchy.label,
                        kind = BrowserKind.CATEGORY,
                    )
                },
        )
}
