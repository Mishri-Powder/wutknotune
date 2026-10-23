package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Test

class HierarchyQueueProjectionTest {
    @Test
    fun albumQueueRetainsCanonicalOrderAndSelectsTrack() {
        val album =
            BrowserItem(
                id = "album:test",
                label = "Test",
                kind = BrowserKind.ALBUM,
                children = (1L..5L).map(::track),
            )

        assertEquals(
            listOf(1L, 2L, 3L, 4L, 5L).map(::MediaId),
            contextualPlayProjection(album, "track:2")?.tracks?.map(BrowserItem::mediaId),
        )
        assertEquals(1, contextualPlayProjection(album, "track:2")?.selectedQueueIndex)
    }

    @Test
    fun albumQueueKeepsRepeatedFragmentIdAndResolvesEachOccurrence() {
        val first = track(7).copy(id = "fragment:first", label = "Chapter A", sourceBindingId = 101, fragment = true)
        val second = track(7).copy(id = "fragment:second", label = "Chapter B", sourceBindingId = 102, fragment = true)
        val album = BrowserItem("album:book", "Book", BrowserKind.ALBUM, children = listOf(first, second))
        val root = BrowserItem("root", "Library", BrowserKind.ROOT, children = listOf(album))
        val queue = contextualPlayProjection(album, second.id)

        assertEquals(listOf(first.mediaId, second.mediaId), queue?.tracks?.map(BrowserItem::mediaId))
        assertEquals(1, queue?.selectedQueueIndex)
        assertEquals(first, queueTrackForOccurrence(root, album.id, 0, requireNotNull(first.mediaId)))
        assertEquals(second, queueTrackForOccurrence(root, album.id, 1, requireNotNull(second.mediaId)))
        assertEquals(listOf(first, second), materializedHierarchyTracks(album))
        val artist = BrowserItem("artist", "Artist", BrowserKind.ARTIST, children = listOf(album))
        assertEquals(listOf(first), materializedHierarchyTracks(artist))
    }

    @Test
    fun folderQueueRecursesAndFiltersInCanonicalOrder() {
        val duplicate = track(2).copy(id = "track:2:duplicate")
        val folder =
            BrowserItem(
                id = "folder:test",
                label = "Test",
                kind = BrowserKind.FOLDER,
                children =
                    listOf(
                        BrowserItem("folder:child", "Child", BrowserKind.FOLDER, children = listOf(track(9))),
                        track(1),
                        track(2),
                        track(3).copy(playable = false),
                        duplicate,
                    ),
            )

        assertEquals(
            listOf(9L, 1L, 2L).map(::MediaId),
            contextualPlayProjection(folder, "track:2")?.tracks?.map(BrowserItem::mediaId),
        )
        assertEquals(2, contextualPlayProjection(folder, "track:2")?.selectedQueueIndex)
        assertEquals(2, contextualPlayProjection(folder, duplicate.id)?.selectedQueueIndex)
    }

    @Test
    fun openedChildFolderDoesNotIncludeParentOrSiblingTracks() {
        val child =
            BrowserItem(
                id = "folder:child",
                label = "Child",
                kind = BrowserKind.FOLDER,
                children = listOf(track(2), track(3)),
            )

        assertEquals(
            listOf(2L, 3L).map(::MediaId),
            contextualPlayProjection(child, "track:3")?.tracks?.map(BrowserItem::mediaId),
        )
        assertEquals(1, contextualPlayProjection(child, "track:3")?.selectedQueueIndex)
    }

    @Test
    fun playlistRotatesExactOccurrenceAndPreservesDuplicates() {
        val playlist =
            BrowserItem(
                id = "playlist:1",
                label = "Test",
                kind = BrowserKind.PLAYLIST,
                children =
                    listOf(
                        track(1).copy(id = "occurrence:10"),
                        track(2).copy(id = "occurrence:11"),
                        track(1).copy(id = "occurrence:12"),
                        track(3).copy(id = "occurrence:13"),
                    ),
            )

        val projection = contextualPlayProjection(playlist, "occurrence:12")

        assertEquals(true, projection?.permanentPlaylist)
        assertEquals(listOf(1L, 3L, 1L, 2L).map(::MediaId), projection?.tracks?.map(BrowserItem::mediaId))
        assertEquals(0, projection?.selectedQueueIndex)
    }

    @Test
    fun containerPlayMaterializesItsCompleteSubtree() {
        val artist =
            BrowserItem(
                id = "artist:1",
                label = "Artist",
                kind = BrowserKind.ARTIST,
                children =
                    listOf(
                        BrowserItem("album:1", "One", BrowserKind.ALBUM, children = listOf(track(1))),
                        BrowserItem("album:2", "Two", BrowserKind.ALBUM, children = listOf(track(2))),
                    ),
            )
        val category = BrowserItem("category:artists", "Artists", BrowserKind.CATEGORY, children = listOf(artist))

        assertEquals(
            listOf(1L, 2L).map(::MediaId),
            contextualPlayProjection(category, artist.id)?.tracks?.map(BrowserItem::mediaId),
        )
        assertEquals(0, contextualPlayProjection(category, artist.id)?.selectedQueueIndex)
    }

    @Test
    fun foldersAlwaysIncludeTheirCompleteMixedContentSubtree() {
        val book =
            BrowserItem(
                id = "folder:book",
                label = "Book",
                kind = BrowserKind.FOLDER,
                playable = true,
                children =
                    listOf(
                        track(2).copy(audiobook = true),
                        BrowserItem(
                            "folder:chapters",
                            "Chapters",
                            BrowserKind.FOLDER,
                            children = listOf(track(3)),
                        ),
                    ),
            )
        val ordinary =
            BrowserItem(
                id = "folder:ordinary",
                label = "Ordinary",
                kind = BrowserKind.FOLDER,
                playable = true,
                children = listOf(track(1), book),
            )
        val category =
            BrowserItem(
                "category:folders",
                "Folders",
                BrowserKind.CATEGORY,
                children = listOf(ordinary, book),
            )

        assertEquals(
            listOf(1L, 2L, 3L).map(::MediaId),
            contextualPlayProjection(category, ordinary.id)?.tracks?.map(BrowserItem::mediaId),
        )
        assertEquals(
            listOf(2L, 3L).map(::MediaId),
            contextualPlayProjection(category, book.id)?.tracks?.map(BrowserItem::mediaId),
        )
    }

    @Test
    fun permanentPlaylistRetainsExplicitAudiobookOccurrence() {
        val playlist =
            BrowserItem(
                id = "playlist:books",
                label = "Mixed",
                kind = BrowserKind.PLAYLIST,
                children = listOf(track(1), track(2).copy(audiobook = true)),
            )

        assertEquals(
            listOf(2L, 1L).map(::MediaId),
            contextualPlayProjection(playlist, "track:2")?.tracks?.map(BrowserItem::mediaId),
        )
    }

    private fun track(id: Long) =
        BrowserItem(
            id = "track:$id",
            label = "Track $id",
            kind = BrowserKind.TRACK,
            playable = true,
            mediaId = MediaId(id),
        )
}
