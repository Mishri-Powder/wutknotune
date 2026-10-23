package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId

/** Any own or ancestor ban on a non-playlist library path excludes that Media.id. */
internal fun effectiveAiBannedMediaIds(
    root: BrowserItem,
    reactions: Map<String, ReactionState>,
): Set<MediaId> {
    val result = mutableSetOf<MediaId>()

    fun visit(
        item: BrowserItem,
        inheritedBan: Boolean,
    ) {
        if (item.kind == BrowserKind.PLAYLIST) return
        val effectiveBan = inheritedBan || reactions[item.reactionKey]?.ownBanned == true
        if (item.kind == BrowserKind.TRACK && effectiveBan) {
            item.mediaId?.let(result::add)
        }
        item.children.forEach { child -> visit(child, effectiveBan) }
    }

    visit(root, inheritedBan = false)
    return result
}
