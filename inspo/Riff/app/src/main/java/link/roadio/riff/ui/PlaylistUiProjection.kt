package link.roadio.riff.ui

import link.roadio.riff.player.storage.PermanentPlaylist
import link.roadio.riff.player.storage.PlaylistKind

internal enum class RowActionKind {
    PLAY,
    ADD_TO_PLAYLIST,
    RENAME,
    DELETE,
    REMOVE_FROM_PLAYLIST,
    REMOVE_BOOKMARK,
}

/** Player-SQLite collections projected into the shared Cuetree hierarchy. */
internal fun projectPlaylists(
    libraryRoot: BrowserItem,
    playlists: List<PermanentPlaylist>,
    tracks: List<BrowserItem>,
    ignoreLeadingArticles: Boolean = false,
    contentMode: ContentMode = ContentMode.MUSIC,
): BrowserItem {
    val trackByMediaId =
        tracks.filter { it.mediaId != null }.groupBy { it.mediaId }.mapValues { (_, copies) ->
            copies.firstOrNull(BrowserItem::playable) ?: copies.first()
        }
    val visibleKind = if (contentMode == ContentMode.AUDIOBOOK) PlaylistKind.BOOK else PlaylistKind.MUSIC
    val playlistItems =
        playlists.filter { it.kind == visibleKind }.map { playlist ->
            val displayName =
                if (playlist.kind == PlaylistKind.BOOK) playlist.name.removePrefix("Bookmarks for ") else playlist.name
            val children =
                if (playlist.kind == PlaylistKind.BOOK) {
                    playlist.marks.sortedWith(compareBy({ it.ordinal }, { it.positionUs }, { it.markId })).mapNotNull { mark ->
                        val occurrence = playlist.occurrences.getOrNull(mark.ordinal) ?: return@mapNotNull null
                        val source = trackByMediaId[occurrence.mediaId]
                        BrowserItem(
                            id = bookmarkItemId(playlist.playlistId, mark.markId),
                            label = "${mark.label}, ${formatBookmarkTime(mark.positionUs)}",
                            kind = BrowserKind.TRACK,
                            playable = source?.playable == true,
                            enabled = source?.playable == true,
                            mediaId = occurrence.mediaId,
                            artist = source?.artist.orEmpty(),
                            album = displayName,
                            reactionKey = trackReactionKey(occurrence.mediaId),
                            sourceBindingId = source?.sourceBindingId,
                            playbackSource = source?.playbackSource,
                            audiobook = true,
                            aiReactionEligible = false,
                            playlistId = playlist.playlistId,
                            playlistRevisionId = playlist.revisionId,
                            bookmarks = true,
                            bookmarkMarkId = mark.markId,
                            bookmarkOrdinal = mark.ordinal,
                            bookmarkPositionUs = mark.positionUs,
                        )
                    }
                } else {
                    playlist.occurrences.map { occurrence ->
                        val source = trackByMediaId[occurrence.mediaId]
                        BrowserItem(
                            id = playlistOccurrenceItemId(playlist.playlistId, occurrence.occurrenceId),
                            label = source?.label ?: "Track",
                            kind = BrowserKind.TRACK,
                            playable = source?.playable == true,
                            enabled = source?.playable == true,
                            mediaId = occurrence.mediaId,
                            artist = source?.artist.orEmpty(),
                            album = source?.album.orEmpty(),
                            sourceFileName = source?.sourceFileName.orEmpty(),
                            discNumber = source?.discNumber,
                            trackNumber = source?.trackNumber,
                            reactionKey = trackReactionKey(occurrence.mediaId),
                            sourceBindingId = source?.sourceBindingId,
                            playbackSource = source?.playbackSource,
                            audiobook = source?.audiobook == true,
                            automaticAudiobook = source?.automaticAudiobook == true,
                            aiReactionEligible = source?.aiReactionEligible == true,
                            albumGroupId = source?.albumGroupId,
                            tocMarkersMs = source?.tocMarkersMs.orEmpty(),
                            playlistId = playlist.playlistId,
                            playlistRevisionId = playlist.revisionId,
                            playlistOccurrenceId = occurrence.occurrenceId,
                        )
                    }
                }
            BrowserItem(
                id = playlistItemId(playlist.playlistId),
                label = displayName,
                kind = BrowserKind.PLAYLIST,
                icon = "playlist",
                playable = playlist.kind != PlaylistKind.BOOK && children.any(BrowserItem::playable),
                bookmarks = playlist.kind == PlaylistKind.BOOK,
                playlistId = playlist.playlistId,
                playlistRevisionId = playlist.revisionId,
                children = children,
            )
        }
    return libraryRoot.copy(
        children =
            libraryRoot.children.map { category ->
                if (category.id == HierarchyRoot.PLAYLISTS.categoryId) {
                    category
                        .copy(
                            label = if (contentMode == ContentMode.AUDIOBOOK) "Bookmarks" else "Playlists",
                            enabled = playlistItems.isNotEmpty(),
                            children = playlistItems,
                        ).withBrowseNamePolicy(ignoreLeadingArticles)
                } else {
                    category
                }
            },
    )
}

internal fun formatBookmarkTime(positionUs: Long): String {
    val total = positionUs.coerceAtLeast(0L) / 1_000_000L
    val seconds = total % 60L
    val minutes = total / 60L % 60L
    val hours = total / 3_600L
    return if (hours ==
        0L
    ) {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    } else {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

internal fun bookmarkItemId(
    playlistId: Long,
    markId: Long,
): String = "${playlistItemId(playlistId)}:mark:${markId.toULong().toString(16)}"

internal fun playlistSelectorItems(root: BrowserItem): List<PlaylistSelectorUiItem> =
    listOf(PlaylistSelectorUiItem(null, NEW_PLAYLIST_LABEL)) +
        root.children
            .firstOrNull { it.id == HierarchyRoot.PLAYLISTS.categoryId }
            ?.children
            .orEmpty()
            .filterNot(BrowserItem::bookmarks)
            .mapNotNull { item -> item.playlistId?.let { PlaylistSelectorUiItem(it, item.label) } }

internal fun rowActionKinds(item: BrowserItem): List<RowActionKind> =
    when {
        item.kind == BrowserKind.PLAYLIST && item.bookmarks -> {
            listOf(RowActionKind.PLAY, RowActionKind.DELETE)
        }

        item.kind == BrowserKind.PLAYLIST -> {
            listOf(RowActionKind.PLAY, RowActionKind.RENAME, RowActionKind.DELETE)
        }

        item.bookmarkMarkId != null -> {
            listOf(RowActionKind.PLAY, RowActionKind.REMOVE_BOOKMARK)
        }

        item.playlistOccurrenceId != null -> {
            listOf(RowActionKind.PLAY, RowActionKind.ADD_TO_PLAYLIST, RowActionKind.REMOVE_FROM_PLAYLIST)
        }

        else -> {
            listOf(RowActionKind.PLAY, RowActionKind.ADD_TO_PLAYLIST)
        }
    }

internal fun playlistItemId(playlistId: Long): String = "playlist:${playlistId.toULong().toString(16)}"

internal fun playlistOccurrenceItemId(
    playlistId: Long,
    occurrenceId: Long,
): String = "${playlistItemId(playlistId)}:occurrence:${occurrenceId.toULong().toString(16)}"

/**
 * Common success destination for Add to playlist, including New Playlist.
 *
 * Authority: https://wiki.271.pics/Riff/LATEST/player-queues
 * AI-INTENT: Both Add paths publish the same durable destination before navigation leaves ROW_ACTIONS.
 */
internal fun openCommittedPlaylist(
    state: PlayerUiState,
    projectedRoot: BrowserItem,
    playlistId: Long,
    status: String,
    steps: List<AzGroup> = emptyList(),
): PlayerUiState {
    val destinationId = playlistItemId(playlistId)
    val destination =
        projectedRoot.children
            .firstOrNull { it.id == HierarchyRoot.PLAYLISTS.categoryId }
            ?.children
            ?.firstOrNull { it.id == destinationId }
    checkNotNull(destination) { "Committed playlist $playlistId is missing from projection" }
    return restoreHierarchyContainer(
        state,
        listOf(HierarchyRoot.PLAYLISTS.categoryId, destinationId),
        steps,
        focus = destination.children.firstOrNull()?.id,
    ).copy(
        root = projectedRoot,
        hierarchyRoot = HierarchyRoot.PLAYLISTS,
        page = 0,
        focusedItemId = destination.children.firstOrNull()?.id,
        actionsItemId = null,
        playlistSourceItemId = null,
        playlistNameRequest = null,
        playlistNameDraft = "",
        pendingPlaylistDelete = null,
        playlistMutationInProgress = false,
        status = status,
    )
}
