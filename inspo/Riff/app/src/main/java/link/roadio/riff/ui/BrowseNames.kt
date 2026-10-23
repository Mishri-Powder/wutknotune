package link.roadio.riff.ui

import link.roadio.riff.config.ProjectLanguages
import java.text.Normalizer
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.CancellationException

/** Comparison only: never rewrite tags, grouping keys, filenames, or durable identities. */
internal fun browseComparisonName(
    name: String,
    ignoreLeadingArticles: Boolean,
): String {
    if (!ignoreLeadingArticles) return name
    val start = name.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return name
    var end = start
    while (end < name.length && !name[end].isWhitespace()) end++
    if (name.substring(start, end).lowercase(Locale.ROOT) !in ProjectLanguages.leadingArticles) return name
    while (end < name.length && name[end].isWhitespace()) end++
    return if (end < name.length) name.substring(end) else name
}

private val browseLabelOrder =
    compareBy<BrowserItem> { Normalizer.normalize(it.comparisonLabel, Normalizer.Form.NFKC).lowercase(Locale.ROOT) }
        .thenBy { it.comparisonName }
        .thenBy { it.genreId }
        .thenBy { it.id }

/** Immutable worker-built view. A replacement also invalidates A–Z's identity-keyed caches. */
internal fun BrowserItem.withBrowseNamePolicy(ignoreLeadingArticles: Boolean): BrowserItem {
    val projected = IdentityHashMap<BrowserItem, BrowserItem>()

    fun visit(item: BrowserItem): BrowserItem =
        projected.getOrPut(item) {
            if (Thread.currentThread().isInterrupted) throw CancellationException("Browse projection replaced")
            val children = item.children.map(::visit)
            val ordered =
                when {
                    item.kind == BrowserKind.FOLDER || item.id == HierarchyRoot.FOLDERS.categoryId -> {
                        children.filter { it.kind == BrowserKind.FOLDER }.sortedWith(browseLabelOrder) +
                            children.filter { it.kind != BrowserKind.FOLDER }
                    }

                    item.kind == BrowserKind.CATEGORY || item.kind == BrowserKind.ARTIST || item.kind == BrowserKind.GENRE -> {
                        children.sortedWith(browseLabelOrder)
                    }

                    // Album/file sequences, playlist occurrences, and fixed root categories retain their order.
                    else -> {
                        children
                    }
                }
            item.copy(children = ordered, ignoreLeadingArticles = ignoreLeadingArticles)
        }
    return visit(this)
}
