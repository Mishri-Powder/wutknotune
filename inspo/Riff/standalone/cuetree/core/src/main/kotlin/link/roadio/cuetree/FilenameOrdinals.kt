// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/**
 * A complete listing can corroborate missing ordinals without becoming a release boundary.
 * Unsupported audio names still count, so decoder admission cannot manufacture completeness.
 * AI-INTENT: Keep this evidence separate from authored Track/Total Tracks and preserve offline
 * members until source expiry; an accessible subset is not a newly complete directory.
 * Contract: https://wiki.271.pics/Cuetree/filename-evidence
 */
internal object FilenameOrdinals {
    const val SIZE = "cuetree.filenameSequenceSize"
    private val prefix = Regex("^(?:0[ \t]+)?([0-9]+)(?=[ \t._()\\-–—])")
    private val audio = setOf("mp3", "mp2", "flac", "m4a", "m4b", "mp4", "aac", "wma", "wav", "wave", "aif", "aiff", "ape", "wv", "ogg", "opus", "alac")
    private val neutral = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "txt", "log", "nfo", "pdf", "m3u", "m3u8", "pls", "ini", "db")

    fun ordinal(name: String): Int? =
        prefix
            .find(name)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

    fun listedSize(
        entries: List<Pair<String, SourceStat>>,
        complete: Boolean,
    ): Int? {
        if (!complete) return null
        val names = mutableListOf<String>()
        for ((name, stat) in entries) {
            if (name.startsWith('.')) continue
            if (stat.directory) return null
            val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            when {
                extension == "cue" || extension == "toc" -> return null
                extension in audio || stat.mediaCandidate == true -> names += name
                extension !in neutral -> return null
            }
        }
        val n = names.size
        return n.takeIf { n >= 2 && names.map(::ordinal).sortedWith(nullsLast()).let { it == (1..n).toList() } }
    }

    fun derive(snapshot: Snapshot): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val children = snapshot.nodes.indices.groupBy { snapshot.nodes[it].parentId }
        for (directory in snapshot.nodes.filter { it.kind == NodeKind.DIRECTORY }) {
            val observed =
                directory.attributes
                    .first(SIZE)
                    ?.toIntOrNull()
                    ?.takeIf { it >= 2 } ?: continue
            val entries = children[directory.id].orEmpty()
            if (entries.any { snapshot.nodes[it].kind == NodeKind.DIRECTORY || snapshot.nodes[it].kind == NodeKind.METADATA || snapshot.nodes[it].tables.isNotEmpty() }) continue
            val media = entries.filter { snapshot.nodes[it].source != null && snapshot.nodes[it].kind != NodeKind.FRAGMENT }
            val n = media.size
            // Retained missing members count; new unacquired children cannot manufacture completeness.
            if (n < observed || n < 2) continue
            val ordinals = media.map { ordinal(snapshot.nodes[it].name) }
            if (ordinals.sortedWith(nullsLast()) != (1..n).toList()) continue
            val attrs = media.map { snapshot.nodes[it].attributes }
            val albums = attrs.mapNotNull { it.first("album")?.let(::canonical)?.takeIf { value -> value.isNotBlank() && value !in setOf("album", "unknown album", "<unknown>") } }.distinct()
            if (albums.size > 1) continue
            if (media.indices.any { offset -> positive(attrs[offset], "trackNumber")?.let { it != ordinals[offset] } == true }) continue
            if (attrs.any { a ->
                    (
                        positive(a, "trackTotal") ?: a
                            .first("trackNumber")
                            ?.substringAfter('/', "")
                            ?.trim()
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 }
                    )?.let { it != n } == true
                }
            ) {
                continue
            }
            media.indices.forEach { result[media[it]] = requireNotNull(ordinals[it]) }
        }
        return result
    }

    private fun positive(
        attrs: Attributes,
        key: String,
    ): Int? =
        attrs
            .first(key)
            ?.substringBefore('/')
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
}
