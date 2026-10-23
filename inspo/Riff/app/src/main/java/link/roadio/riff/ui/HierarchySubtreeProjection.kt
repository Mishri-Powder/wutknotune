package link.roadio.riff.ui

/**
 * AI-INTENT: Play, Add and restored occurrence lookup share the same source-specific ordering policy.
 * Album/Title keeps all declared ALBM positions; aggregate sources collapse identities after technical filtering.
 * https://wiki.271.pics/Riff/LATEST/player-queues#queues-playlists-and-history
 */
internal fun orderedHierarchyTracks(
    source: BrowserItem,
    playableOnly: Boolean = true,
): List<BrowserItem> {
    val identities = mutableSetOf<link.roadio.riff.indexer.core.media.MediaId>()
    return collectHierarchyTracks(source)
        .filter { it.mediaId != null && (!playableOnly || it.playable) }
        .filter {
            source.kind == BrowserKind.PLAYLIST ||
                source.kind == BrowserKind.ALBUM ||
                identities.add(requireNotNull(it.mediaId))
        }
}

internal fun materializedHierarchyTracks(source: BrowserItem): List<BrowserItem> = orderedHierarchyTracks(source)

internal fun collectHierarchyTracks(source: BrowserItem): List<BrowserItem> = collectAllHierarchyTracks(source)

private fun collectAllHierarchyTracks(source: BrowserItem): List<BrowserItem> {
    if (source.kind == BrowserKind.TRACK) return listOf(source)
    return source.children.flatMap(::collectAllHierarchyTracks)
}
