// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Internal source evidence supplied by the caller's existing admission probe. */
const val SOURCE_DURATION_US = "cuetree.durationUs"
const val SOURCE_CONTENT_KIND = "cuetree.contentKind"

/**
 * Classify complete ordinary proposals after release/copy resolution. Availability is not a
 * track count: retained offline facts still protect album membership and artist qualification.
 * AI-INTENT: Title bases classify an already assembled release; they must never merge releases.
 * Contract: https://wiki.271.pics/Cuetree/singles
 */
internal class SinglesClassifier(
    private val snapshot: Snapshot,
    private val evidence: AlbumEvidence,
    private val genres: GenreResolver,
) {
    data class Basket(
        val key: List<String>,
        val display: List<String>,
        val sources: List<Int>,
    )

    data class Result(
        val ordinary: List<OrdinaryAlbumGroup>,
        val personal: List<Basket>,
        val global: List<Int>,
    )

    private data class Credit(
        val key: List<String>,
        val display: List<String>,
    )

    private data class Selection(
        val group: OrdinaryAlbumGroup,
        val owner: Credit?,
        val witnesses: Set<List<String>>,
    )

    private val vocabulary = evidence.vocabulary
    private val nodes = snapshot.nodes
    private val bookCache = mutableMapOf<Int, Boolean>()

    fun collect(
        groups: List<OrdinaryAlbumGroup>,
        unclaimed: List<Int>,
    ): Result {
        val candidates = groups.filter { candidate(it) }.toSet()
        val selected = candidates.map { Selection(it, owner(it.sources), common(it.sources)) }
        val ordinary = groups.filterNot { it in candidates }
        val anchors = ordinary.filter { substantial(it) }.mapNotNull { owner(it.sources)?.key }.toSet()
        val counts = selected.mapNotNull { it.owner?.key }.groupingBy { it }.eachCount()
        val qualified = anchors + counts.filterValues { it >= 3 }.keys
        val personal = mutableMapOf<List<String>, MutableList<List<Int>>>()
        val global = mutableListOf<List<Int>>()
        val displays = mutableMapOf<List<String>, MutableList<List<String>>>()

        fun route(
            sources: List<Int>,
            effective: Credit?,
            witnesses: Set<List<String>>,
        ) {
            evidence.cancellation.check()
            val matchingAnchors = witnesses.intersect(anchors)
            val matching = witnesses.intersect(qualified)
            val destination =
                when {
                    effective?.key in anchors -> effective?.key
                    matchingAnchors.size == 1 -> matchingAnchors.single()
                    matchingAnchors.size > 1 -> null
                    effective?.key in qualified -> effective?.key
                    matching.size == 1 -> matching.single()
                    else -> null
                }
            if (destination == null || sources.any(::book)) {
                global += sources
            } else {
                personal.getOrPut(destination, ::mutableListOf) += sources
                displays.getOrPut(destination, ::mutableListOf) += display(destination, sources)
            }
        }
        selected.forEach { route(it.group.sources, it.owner, it.witnesses) }
        unclaimed.forEach { route(listOf(it), owner(listOf(it)), common(listOf(it))) }

        fun ordered(parts: List<List<Int>>): List<Int> = parts.sortedWith(compareBy<List<Int>>({ canonical(nodes[it.first()].attributes.first("album").orEmpty()) }, { canonical(nodes[it.first()].attributes.first("title").orEmpty()) }, { nodes[it.first()].id.hi }, { nodes[it.first()].id.lo })).flatten()
        return Result(
            ordinary,
            personal.entries.sortedBy { it.key.joinToString("\u0000") }.map { (key, parts) ->
                val spelling = displays.getValue(key).minBy { it.joinToString("\u0000") }
                Basket(key, spelling, ordered(parts))
            },
            ordered(global),
        )
    }

    private fun candidate(group: OrdinaryAlbumGroup): Boolean {
        evidence.cancellation.check()
        val sources = group.sources
        if (sources.any { nodes[it].kind == NodeKind.FRAGMENT || nodes[it].tables.isNotEmpty() || book(it) }) return false
        if (sources.size in 1..3 && sources.all { duration(it)?.let { value -> value < 600_000_000L } == true }) return true
        val credited = owner(sources) ?: return false
        if (credited.key.isEmpty()) return false
        val album = canonical(group.title)
        val liveElectronic =
            sources.all { source ->
                val ids = genreIds(nodes[source])
                ids.any { it in vocabulary.liveGenreIds } && ids.any { it in vocabulary.electronicGenreIds }
            }
        if (vocabulary.setMarker.containsMatchIn(album) || liveElectronic) return true
        if (sources.size !in 4..16 || !complete(sources, singleDisc = true)) return false
        val titles = sources.map { nodes[it].attributes.first("title").orEmpty() }
        val bases = titles.map { versionBase(it, vocabulary) }
        return bases.all { (base, _) -> base.isNotBlank() && !vocabulary.genericTitle(base) } &&
            bases.any { it.second } && bases.map { it.first }.distinct().size <= 3 &&
            titles
                .map {
                    vocabulary.protected
                        .findAll(canonical(it))
                        .map { m -> m.value }
                        .toList()
                }.distinct()
                .size == 1
    }

    private fun substantial(group: OrdinaryAlbumGroup): Boolean {
        val sources = group.sources
        if (vocabulary.placeholderAlbum(group.title) || sources.any { nodes[it].kind == NodeKind.FRAGMENT || nodes[it].tables.isNotEmpty() || book(it) }) return false
        val duration = sources.map(::duration)
        val sizeable = sources.size >= 7 || (sources.size >= 4 && duration.all { it != null } && duration.sumOf { it?.toDouble() ?: 0.0 } >= 1_800_000_000.0)
        return sizeable && complete(sources, singleDisc = false)
    }

    private fun complete(
        sources: List<Int>,
        singleDisc: Boolean,
    ): Boolean {
        val facts = sources.map { evidence.facts[it] ?: return false }
        if (facts.any { it.boundsInvalid }) return false
        val discs = sources.map(evidence::disc)
        if (singleDisc && (discs.filterNotNull().distinct().size > 1 || facts.any { (it.discs ?: 1) > 1 })) return false
        if (facts.mapNotNull { it.discs }.distinct().size > 1) return false
        val declaredDiscs = facts.mapNotNull { it.discs }.distinct().singleOrNull()
        val knownDiscs = discs.filterNotNull().distinct().sorted()
        if (!singleDisc && knownDiscs.isNotEmpty()) {
            if (discs.any { it == null } || knownDiscs != (1..knownDiscs.size).toList()) return false
            if (declaredDiscs != null && declaredDiscs != knownDiscs.size) return false
        }
        if (knownDiscs.isEmpty() && (declaredDiscs ?: 1) > 1) return false
        for (disc in if (singleDisc) listOf(sources) else sources.groupBy(evidence::disc).values) {
            val numbered = disc.map { evidence.facts.getValue(it).track }
            if (numbered.sortedWith(nullsLast()) != (1..disc.size).toList()) return false
            if (disc.any {
                    evidence.facts
                        .getValue(it)
                        .total
                        ?.let { total -> total != disc.size } == true
                }
            ) {
                return false
            }
        }
        return true
    }

    private fun duration(source: Int): Long? =
        nodes[source]
            .sourceEvidence
            .first(SOURCE_DURATION_US)
            ?.toLongOrNull()
            ?.takeIf { it > 0 }

    private fun book(source: Int): Boolean =
        bookCache.getOrPut(source) {
            val node = nodes[source]
            val direct =
                node.attributes.first("mediaKind") == "2" || node.sourceEvidence.first(SOURCE_CONTENT_KIND) == "book" ||
                    node.name.endsWith(".m4b", true) ||
                    genreIds(node).any { it in vocabulary.spokenGenreIds }

            if (direct) {
                true
            } else {
                var cursor: Int? = source
                var result = false
                while (cursor != null) {
                    if (nodes[cursor].isRoot && nodes[cursor].attributes.first("mediaKind") == "2") result = true
                    cursor = snapshot.parentOf(cursor)
                }
                result
            }
        }

    private fun owner(sources: List<Int>): Credit? {
        for (field in listOf("albumArtist", "artist")) {
            val keys = sources.map { eligibleCredit(nodes[it].attributes[field].orEmpty()) }.distinct()
            if (keys.size == 1 && keys.single() != null) {
                val key = requireNotNull(keys.single())
                return Credit(key, display(key, sources))
            }
        }
        return null
    }

    private fun common(sources: List<Int>): Set<List<String>> =
        sources
            .map { source ->
                listOfNotNull(eligibleCredit(nodes[source].attributes["albumArtist"].orEmpty()), eligibleCredit(nodes[source].attributes["artist"].orEmpty())).toSet()
            }.reduceOrNull { a, b -> a.intersect(b) }
            .orEmpty()

    private fun display(
        key: List<String>,
        sources: List<Int>,
    ): List<String> =
        key.map { part ->
            sources
                .flatMap { nodes[it].attributes["albumArtist"].orEmpty() + nodes[it].attributes["artist"].orEmpty() }
                .filter { canonical(it) == part }
                .minOrNull() ?: part
        }

    private fun eligibleCredit(values: List<String>): List<String>? =
        credit(values)?.takeUnless {
            it.all { part -> part in vocabulary.genericArtists || part in vocabulary.placeholderArtists || part in vocabulary.placeholderAlbumArtists }
        }

    private fun genreIds(node: Node): Set<Long> =
        node.attributes["genreId"]
            .orEmpty()
            .mapNotNull(String::toLongOrNull)
            .toSet() +
            node.attributes["genre"].orEmpty().mapNotNull { genres.resolve(it).dictionaryId }

    companion object {
        private val BRACKET = Regex("\\s*[\\(\\[]([^()\\[\\]]+)[\\)\\]]\\s*$")
        private val DASH = Regex("\\s+[-–—]\\s+")

        fun credit(values: List<String>): List<String>? {
            val key =
                values
                    .map(::canonical)
                    .filter(String::isNotBlank)
                    .distinct()
                    .sorted()
            return key.takeIf { it.isNotEmpty() }
        }

        fun versionBase(
            title: String,
            vocabulary: CatalogVocabulary,
        ): Pair<String, Boolean> {
            var value = canonical(title)
            val original = value
            while (true) {
                // Malformed brackets are title text, not evidence of an authored version suffix.
                val bracket =
                    BRACKET.find(value)?.takeIf {
                        val suffix = it.value.trim()
                        (suffix.first() == '(' && suffix.last() == ')') ||
                            (suffix.first() == '[' && suffix.last() == ']')
                    }
                val dash = if (bracket == null) DASH.findAll(value).lastOrNull() else null
                val descriptor = bracket?.groupValues?.get(1) ?: dash?.let { value.substring(it.range.last + 1) } ?: break
                if (descriptor.any { it in "()[]" } || !vocabulary.version.containsMatchIn(descriptor) || vocabulary.protected.containsMatchIn(descriptor)) break
                val start = bracket?.range?.first ?: requireNotNull(dash).range.first
                value = value.substring(0, start).trimEnd()
            }
            return value to (value != original)
        }
    }
}
