// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Transient corroboration only; neither a folder ordinal nor a stripped Album becomes an MDIA tag. */
internal class DiscFamilies(
    snapshot: Snapshot,
    private val facts: Map<Int, AlbumFact>,
    cancellation: Cancellation,
    private val vocabulary: CatalogVocabulary,
) {
    private data class Member(
        val folder: MediaId,
        val parent: MediaId?,
        val template: String,
        val ordinal: Int,
    )

    private val family = mutableMapOf<MediaId, Int>()
    private val ordinals = mutableMapOf<MediaId, Int>()
    private val keys = mutableMapOf<Int, String>()
    private val titles = mutableMapOf<Int, String>()

    init {
        val children = facts.values.groupBy { it.node.parentId }
        val interpretations = mutableListOf<Member>()
        for (node in snapshot.nodes) {
            cancellation.check()
            if (node.kind != NodeKind.DIRECTORY || node.id !in children) continue
            val name = canonical(node.name)
            val marked = parse(name, vocabulary)
            val patterns =
                if (marked != null) {
                    listOf(marked.template to marked.ordinal)
                } else if (vocabulary.discWord.containsMatchIn(name)) {
                    emptyList()
                } else {
                    numericPatterns(name)
                }
            for ((template, ordinal) in patterns) {
                if (children.getValue(node.id).any { (it.disc != null && it.disc != ordinal) || (it.discs != null && ordinal > it.discs) }) continue
                interpretations += Member(node.id, node.parentId, template, ordinal)
            }
        }
        val eligible = interpretations.groupBy { it.parent to it.template }.values.filter { members -> members.size > 1 && members.map { it.ordinal }.distinct().size == members.size }
        val ambiguous =
            eligible
                .flatMap { it.map(Member::folder) }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        var serial = 0
        for (members in eligible) {
            if (members.any { it.folder in ambiguous }) continue
            val id = serial++
            for (m in members) {
                family[m.folder] = id
                ordinals[m.folder] = m.ordinal
            }
            val media = members.flatMap { children.getValue(it.folder) }.filter { it.album.isNotBlank() }
            val bases = media.map { f -> suffixBase(f, ordinals.getValue(f.node.parentId!!)) }
            if (bases.any { it == null } || bases.map { canonical(it!!) }.distinct().size != 1) continue
            val base = bases.filterNotNull().minWithOrNull(compareBy({ canonical(it) }, { it })) ?: continue
            if (base.isBlank()) continue
            for (f in media) {
                keys[f.index] = canonical(base)
                titles[f.index] = base
            }
        }
    }

    fun ordinal(index: Int) = ordinals[facts.getValue(index).node.parentId]

    fun same(
        a: Int,
        b: Int,
    ): Boolean {
        val x = family[facts.getValue(a).node.parentId]
        return x != null && x == family[facts.getValue(b).node.parentId]
    }

    fun albumKey(index: Int) = keys[index]

    fun albumTitle(index: Int) = titles[index]

    private fun suffixBase(
        fact: AlbumFact,
        ordinal: Int,
    ): String? {
        val text = fact.displayAlbum.trim()
        if (text.isEmpty()) return null
        val marked = parse(text, vocabulary)
        if (marked == null) return text.takeUnless { vocabulary.discWord.containsMatchIn(it) }
        if (marked.ordinal != ordinal || text.substring(marked.end).isNotBlank()) return null
        return text
            .substring(0, marked.begin)
            .trim()
            .trimEnd('-', '_', '.')
            .trim()
            .takeIf(String::isNotBlank)
    }

    companion object {
        internal data class DiscName(
            val template: String,
            val ordinal: Int,
            val begin: Int,
            val end: Int,
        )

        internal fun parse(
            text: String,
            vocabulary: CatalogVocabulary,
        ): DiscName? {
            val matches = vocabulary.markedDisc.findAll(text).toList()
            if (matches.size != 1) return null
            val match = matches.single()
            val ordinal = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            var begin = match.range.first
            var end = match.range.last + 1
            if (begin > 0 && end < text.length && ((text[begin - 1] == '(' && text[end] == ')') || (text[begin - 1] == '[' && text[end] == ']'))) {
                begin--
                end++
            }
            return DiscName(canonical(text.substring(0, begin) + "{disc}" + text.substring(end)), ordinal, begin, end)
        }

        private fun numericPatterns(text: String): List<Pair<String, Int>> {
            val digits = Regex("[0-9]+").findAll(text).toList()
            return digits.mapNotNull { match ->
                match.value
                    .toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { number -> (text.substring(0, match.range.first) + "{disc}" + text.substring(match.range.last + 1)) to number }
            }
        }
    }
}
