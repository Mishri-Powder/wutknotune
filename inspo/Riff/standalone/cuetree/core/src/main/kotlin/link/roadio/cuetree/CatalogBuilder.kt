// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

internal data class ExplicitAlbum(
    val sources: List<Int>,
    val title: String,
    val attributes: Attributes,
)

internal data class ChapterExpansion(
    val base: Int,
    val sources: List<Int>,
    val title: String,
    val attributes: Attributes,
)

/**
 * Facts own liveness; catalogs own presentation and stable IDs. Explicit tables claim logical
 * bases before ordinary grouping, and chapter expansion precedes Singles classification.
 * AI-INTENT: A physical copy may supply audio without adding a position. Never admit a base
 * container to another album or basket after its FILF positions have claimed that identity.
 * Authority: https://wiki.271.pics/Cuetree/catalogs#albm
 */
internal class CatalogBuilder(
    val ids: IdAllocator,
    val genres: GenreResolver,
    val grouping: AlbumGroupingPolicy,
    val ttl: Long,
    val cancel: Cancellation,
    val vocabulary: CatalogVocabulary = CatalogVocabulary.EMPTY,
) {
    fun build(
        snapshot: Snapshot,
        previous: Snapshot,
        observed: List<ExplicitAlbum> = emptyList(),
        observedExpansions: List<ChapterExpansion> = emptyList(),
        reuseExpansions: Boolean = false,
    ): Snapshot {
        val nodes = snapshot.nodes
        val old = previous.albums.filter { !it.life.expired(snapshot.generation, ttl) }

        fun signature(
            title: String,
            attrs: Attributes,
            sources: List<Int>,
        ) = Triple(title, attrs, sources.map { nodes[it].id to nodes[it].attributes })

        fun refreshed(sources: List<Int>) =
            sources.map { i ->
                val n = nodes[i]
                snapshot.sources(n.id).firstOrNull { nodes[it].kind == n.kind && nodes[it].attributes == n.attributes && nodes[it].life.available } ?: i
            }

        fun members(sources: List<Int>) = sources.map { nodes[it].id to nodes[it].attributes }
        val observedBaseIds = observedExpansions.map { nodes[it.base].id }.toSet()
        val retainedExpansions =
            old
                .filter { it.kind == AlbumKind.ORDINARY }
                .flatMap { album ->
                    album.sources
                        .filter { nodes[it].kind == NodeKind.FRAGMENT }
                        .groupBy { nodes[it].parentId }
                        .mapNotNull { (parentId, sources) ->
                            if (parentId == null || parentId in observedBaseIds) return@mapNotNull null
                            val base =
                                nodes.indices.firstOrNull { nodes[it].baseIdentity && IdOrder.matches(nodes[it].id, nodes[it].flags, parentId) }
                                    ?: return@mapNotNull null
                            if (!reuseExpansions && snapshot.available(parentId)) return@mapNotNull null
                            val attrs = nodes[base].attributes
                            val title = attrs.first("album") ?: attrs.first("title") ?: "[[UNTITLED]]"
                            ChapterExpansion(base, sources, title, mapOf("album" to listOf(title)))
                        }
                }
        val expansions = (observedExpansions + retainedExpansions).map { it.copy(sources = refreshed(it.sources)) }
        val byBase = expansions.groupBy { nodes[it.base].id }
        val unambiguous = mutableMapOf<MediaId, ChapterExpansion>()
        val competing = mutableListOf<ExplicitAlbum>()
        for ((baseId, copies) in byBase) {
            val variants = copies.distinctBy { Triple(it.title, it.attributes, members(it.sources)) }
            if (variants.size == 1) {
                unambiguous[baseId] = variants.single()
            } else {
                competing += variants.map { ExplicitAlbum(it.sources, it.title, it.attributes) }
            }
        }
        val current = (observed + competing).map { it.copy(sources = refreshed(it.sources)) }
        val superseded =
            old
                .filter { prior ->
                    prior.kind == AlbumKind.EXPLICIT &&
                        prior.sources.isNotEmpty() &&
                        unambiguous.values.any { expansion ->
                            val samePositions = members(refreshed(prior.sources)) == members(expansion.sources)
                            samePositions &&
                                (
                                    (prior.title == expansion.title && prior.attributes == expansion.attributes) ||
                                        (prior.title == "[[UNTITLED]]" && prior.attributes.first("album") == null && prior.attributes.first("title") == null)
                                )
                        }
                }.mapTo(mutableSetOf()) { it.id }
        // Parsed explicit structures already contain grouping; never reconstruct it from a path.
        val explicit =
            (current + old.filter { it.kind == AlbumKind.EXPLICIT && it.sources.isNotEmpty() && it.id !in superseded }.map { ExplicitAlbum(it.sources, it.title, it.attributes) })
                .map { it.copy(sources = refreshed(it.sources)) }
                .distinctBy { signature(it.title, it.attributes, it.sources) }
        val explicitBaseIds = explicit.flatMap { album -> album.sources.mapNotNull { nodes[it].parentId } }.toSet()
        val expandable = unambiguous.filterKeys { baseId -> explicitBaseIds.none { IdOrder.matches(baseId, 0, it) } }
        val preferredExpandableBase =
            nodes.indices
                .filter { nodes[it].baseIdentity && nodes[it].id in expandable }
                .sortedWith(compareBy({ nodes[it].parentId?.hi }, { nodes[it].name }, { nodes[it].id.hi }))
                .groupBy { nodes[it].id }
                .mapValues { (_, copies) -> copies.firstOrNull { nodes[it].playable } ?: copies.first() }
        val ordinary =
            nodes.indices
                .filter { i ->
                    val n = nodes[i]
                    n.baseIdentity &&
                        explicitBaseIds.none { IdOrder.matches(n.id, n.flags, it) } &&
                        (n.id !in expandable || preferredExpandableBase[n.id] == i)
                }.sortedWith(compareBy({ nodes[it].parentId?.hi }, { nodes[it].name }, { nodes[it].id.hi }, { nodes[it].id.lo }))
        val groups = explicit.map { Candidate(AlbumKind.EXPLICIT, it.sources, it.title, it.attributes) }.toMutableList()
        val positions = LogicalAlbumPositions(AlbumEvidence(snapshot, ordinary, cancel, vocabulary))
        val eligible = ordinary.toSet()
        val assignedSources = mutableSetOf<Int>()
        val assignedBases = mutableSetOf<MediaId>()
        cancel.check()
        val proposals =
            if (grouping === DefaultAlbumGroupingPolicy) {
                AlbumGrouping(positions.evidence, positions).groups()
            } else {
                grouping.group(AlbumGroupingInput(snapshot, positions.representatives, cancel, vocabulary))
            }
        for (proposal in proposals) {
            cancel.check()
            require(proposal.sources.isNotEmpty()) { "Ordinary album group cannot be empty" }
            require(proposal.title.isNotBlank()) { "Ordinary album title cannot be blank" }
            val expanded = mutableListOf<Int>()
            val expandedBases = mutableSetOf<MediaId>()
            for (source in proposal.sources) {
                require(source in eligible && assignedSources.add(source)) { "Ordinary album source is ineligible or assigned more than once: $source" }
                assignedSources.addAll(positions.claimed(source))
                val baseId = nodes[source].id
                val chapter = expandable[baseId]
                if (chapter == null) {
                    expanded += source
                } else if (expandedBases.add(baseId)) {
                    expanded += chapter.sources
                }
                if (chapter != null) assignedBases += baseId
            }
            groups += Candidate(AlbumKind.ORDINARY, expanded, proposal.title, proposal.attributes)
        }
        for ((baseId, chapter) in expandable) {
            if (baseId !in assignedBases) {
                groups += Candidate(AlbumKind.EXPLICIT, chapter.sources, chapter.title, chapter.attributes)
                assignedBases += baseId
            }
        }
        val unclaimed = positions.representatives.filter { it !in assignedSources && nodes[it].id !in assignedBases }
        val classified =
            SinglesClassifier(snapshot, positions.evidence, genres).collect(
                groups.filter { it.kind == AlbumKind.ORDINARY }.map { OrdinaryAlbumGroup(it.sources, it.title, it.attributes) },
                unclaimed,
            )
        groups.removeAll { it.kind == AlbumKind.ORDINARY }
        groups += classified.ordinary.map { Candidate(AlbumKind.ORDINARY, it.sources, it.title, it.attributes) }
        groups +=
            classified.personal.map { basket ->
                val title = basket.display.joinToString("; ") + ": singles"
                Candidate(AlbumKind.ARTIST_SINGLES, basket.sources, title, mapOf("albumArtist" to basket.display))
            }
        groups += Candidate(AlbumKind.SINGLES, classified.global, "[[SINGLES]]", emptyMap())
        val assigned = mutableSetOf<Long>()
        val used = previous.albums.map { it.id }.toMutableSet()
        val oldSingles = old.filter { it.kind == AlbumKind.SINGLES }.singleOrNull()
        val oldPersonal = old.filter { it.kind == AlbumKind.ARTIST_SINGLES }.associateBy { SinglesClassifier.credit(it.attributes["albumArtist"].orEmpty()) }
        val oldExplicit =
            old
                .filter { it.kind == AlbumKind.EXPLICIT }
                .groupBy { signature(it.title, it.attributes, it.sources) }
        val oldById = old.associateBy { it.id }
        val oldMembership = mutableMapOf<Int, MutableList<Long>>()
        old.filter { it.kind == AlbumKind.ORDINARY }.forEach { album ->
            album.sources.distinct().forEach { source -> oldMembership.getOrPut(source) { mutableListOf() }.add(album.id) }
        }
        val choices =
            groups.associateWith { group ->
                when (group.kind) {
                    AlbumKind.SINGLES -> {
                        oldSingles
                    }

                    AlbumKind.ARTIST_SINGLES -> {
                        oldPersonal[SinglesClassifier.credit(group.attributes["albumArtist"].orEmpty())]
                    }

                    AlbumKind.EXPLICIT -> {
                        oldExplicit[signature(group.title, group.attributes, group.sources)]?.firstOrNull()
                    }

                    AlbumKind.ORDINARY -> {
                        val scores =
                            group.sources
                                .distinct()
                                .flatMap { oldMembership[it].orEmpty() }
                                .groupingBy { it }
                                .eachCount()
                        val max = scores.values.maxOrNull() ?: 0
                        scores
                            .filterValues { it == max && it > 0 }
                            .keys
                            .singleOrNull()
                            ?.let(oldById::getValue)
                    }
                }
            }
        val choiceCounts =
            choices.values
                .filterNotNull()
                .groupingBy { it.id }
                .eachCount()
        val albums =
            groups
                .map { group ->
                    cancel.check()
                    val choice = choices[group]?.takeIf { a -> choiceCounts[a.id] == 1 && assigned.add(a.id) }
                    val members = group.sources
                    val tracks = orderedAlbumTrackIds(nodes, members)
                    val available = (group.kind == AlbumKind.SINGLES && tracks.isEmpty()) || tracks.any(snapshot::available)
                    Album(
                        choice?.id ?: allocate(used),
                        group.kind,
                        group.title,
                        tracks,
                        members,
                        group.attributes,
                        (choice?.life ?: Life(snapshot.generation)).referenced(members.isNotEmpty() || group.kind == AlbumKind.SINGLES, available, snapshot.generation),
                    )
                }.toMutableList()
        val currentAlbumIds = albums.mapTo(mutableSetOf()) { it.id }
        old.filter { prior -> prior.id !in superseded && prior.id !in currentAlbumIds }.forEach { prior ->
            val life = prior.life.observe(false, snapshot.generation)
            if (!life.expired(snapshot.generation, ttl)) albums += prior.copy(tracks = emptyList(), sources = emptyList(), life = life)
        }
        val artistLinks = linkedMapOf<String, Pair<String, MutableSet<Long>>>()
        val genreLinks = linkedMapOf<GenreRef, Pair<String, MutableSet<Long>>>()
        for (album in albums.filter { it.sources.isNotEmpty() }) {
            val attrs = listOf(album.attributes) + album.sources.map { nodes[it].attributes }
            for (a in attrs) {
                for (name in (a["artist"].orEmpty() + a["albumArtist"].orEmpty()).filter { it.isNotBlank() }) {
                    artistLinks.getOrPut(canonical(name)) { name to linkedSetOf() }.second += album.id
                }
                for (text in a["genre"].orEmpty().filter { it.isNotBlank() }) {
                    genreLinks.getOrPut(genres.resolve(text)) { text to linkedSetOf() }.second += album.id
                }
            }
        }
        val byAlbum = albums.associateBy { it.id }
        val artistIds = previous.artists.map { it.id }.toMutableSet()
        val oldArtists = previous.artists.filter { !it.life.expired(snapshot.generation, ttl) }.groupBy { it.key }
        val artists =
            artistLinks
                .map { (key, pair) ->
                    val before = oldArtists[key]?.singleOrNull()
                    Artist(
                        before?.id ?: allocate(artistIds),
                        key,
                        pair.first,
                        pair.second.toList(),
                        (before?.life ?: Life(snapshot.generation)).referenced(true, pair.second.any { byAlbum.getValue(it).life.available }, snapshot.generation),
                    )
                }.toMutableList()
        previous.artists.filter { it.key !in artistLinks }.forEach { artist ->
            val life = artist.life.observe(false, snapshot.generation)
            if (!life.expired(snapshot.generation, ttl)) artists += artist.copy(albums = emptyList(), life = life)
        }
        val genreIds = previous.genres.map { it.id }.toMutableSet()
        val oldGenres = previous.genres.filter { !it.life.expired(snapshot.generation, ttl) }.groupBy { it.ref }
        val foundGenres =
            genreLinks
                .map { (ref, pair) ->
                    val before = oldGenres[ref]?.singleOrNull()
                    Genre(
                        ref,
                        pair.first,
                        pair.second.toList(),
                        (before?.life ?: Life(snapshot.generation)).referenced(true, pair.second.any { byAlbum.getValue(it).life.available }, snapshot.generation),
                        before?.id ?: allocate(genreIds),
                    )
                }.toMutableList()
        previous.genres.filter { it.ref !in genreLinks }.forEach { genre ->
            val life = genre.life.observe(false, snapshot.generation)
            if (!life.expired(snapshot.generation, ttl)) foundGenres += genre.copy(albums = emptyList(), life = life)
        }
        return snapshot.copy(albums = albums, artists = artists, genres = foundGenres)
    }

    private fun allocate(used: MutableSet<Long>): Long {
        repeat(100) {
            val id = ids.next()
            if (used.add(id)) return id
        }
        error("ID allocator repeatedly returned existing catalog IDs")
    }

    private data class Candidate(
        val kind: AlbumKind,
        val sources: List<Int>,
        val title: String,
        val attributes: Attributes,
    )
}
