package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId

internal data class HierarchyQueueProjection(
    val selected: BrowserItem,
    val tracks: List<BrowserItem>,
    val sourceTracks: List<BrowserItem>,
    val titleName: String,
    val selectedQueueIndex: Int,
    val permanentPlaylist: Boolean,
    val contextKey: String,
    val albumContext: Boolean,
    val audiobook: Boolean,
    val resume: Boolean,
)

/**
 * Projects every hierarchy Play trigger from the selected row and its open container.
 *
 * AI-INTENT: Gesture type must never enter this function. Album and Folder retain canonical,
 * finite non-playlist queue order; Folder recursion follows the projected child order.
 * Playlist rotation uses the exact row ID so equal Media IDs remain distinct.
 * https://wiki.271.pics/Riff/LATEST/player-queues#queues-playlists-and-history
 */
internal fun contextualPlayProjection(
    container: BrowserItem,
    selectedItemId: String,
): HierarchyQueueProjection? {
    val selected = container.children.firstOrNull { it.id == selectedItemId } ?: return null
    val permanentPlaylist = selected.kind == BrowserKind.PLAYLIST || container.kind == BrowserKind.PLAYLIST
    val context = if (selected.kind == BrowserKind.TRACK) container else selected
    val projected = orderedHierarchyTracks(context)
    val selectedIndex =
        if (selected.kind == BrowserKind.TRACK) {
            projected.indexOfFirst { it.id == selectedItemId }.takeIf { it >= 0 }
                ?: projected.indexOfFirst { it.mediaId == selected.mediaId }
        } else {
            0
        }
    if (selectedIndex < 0) return null
    val queueTracks =
        if (permanentPlaylist && selected.kind == BrowserKind.TRACK) {
            projected.drop(selectedIndex) + projected.take(selectedIndex)
        } else {
            projected
        }
    val selectedQueueIndex = if (permanentPlaylist) 0 else selectedIndex
    return HierarchyQueueProjection(
        selected = selected,
        tracks = queueTracks,
        sourceTracks = orderedHierarchyTracks(context, playableOnly = false),
        titleName = context.label,
        selectedQueueIndex = selectedQueueIndex,
        permanentPlaylist = permanentPlaylist,
        contextKey = context.reactionKey,
        albumContext = context.kind == BrowserKind.ALBUM,
        audiobook = context.audiobook,
        resume = selected.kind != BrowserKind.TRACK,
    )
}

/** Resolve metadata and the preferred binding for an exact hierarchy occurrence. */
internal fun queueTrackForOccurrence(
    root: BrowserItem,
    contextKey: String?,
    ordinal: Int,
    mediaId: MediaId,
): BrowserItem? {
    if (ordinal < 0) return null
    return queueContextTracks(root, contextKey).getOrNull(ordinal)?.takeIf { it.mediaId == mediaId }
}

/** Materialize a context once before resolving all occurrences of a queue. */
internal fun queueContextTracks(
    root: BrowserItem,
    contextKey: String?,
): List<BrowserItem> {
    if (contextKey == null) return emptyList()

    fun find(item: BrowserItem): BrowserItem? {
        if (item.reactionKey == contextKey) return item
        return item.children.firstNotNullOfOrNull(::find)
    }
    return find(root)?.let(::orderedHierarchyTracks).orEmpty()
}
