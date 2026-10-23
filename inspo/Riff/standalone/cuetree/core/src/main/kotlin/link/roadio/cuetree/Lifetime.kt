// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/**
 * Age retained facts without source I/O. Availability alone preserves logical membership; actual
 * source expiry can change both Singles classification and qualification of an artist basket.
 * Retained chapter expansions must survive this path because it cannot reopen CUE/CHAP sources.
 * Authority: https://wiki.271.pics/Cuetree/catalogs#mdia-and-direct-folder-display
 */
fun Snapshot.advancePublication(
    generation: Long,
    ttl: Long,
    grouping: AlbumGroupingPolicy = DefaultAlbumGroupingPolicy,
    vocabulary: CatalogVocabulary = CatalogVocabulary.EMPTY,
): Snapshot {
    require(generation > this.generation && ttl >= 0)
    validate(this)
    val retained = nodes.map<Node, Node?> { it }.toMutableList()
    expireNodes(retained, generation, ttl)
    val mapping =
        retained.indices
            .filter { retained[it] != null }
            .withIndex()
            .associate { it.value to it.index }
    val factual = copy(generation = generation, nodes = retained.filterNotNull())
    val albums =
        albums.mapNotNull { album ->
            val sources = album.sources.mapNotNull(mapping::get)
            val tracks = orderedAlbumTrackIds(factual.nodes, sources)
            val singles = album.kind == AlbumKind.SINGLES
            val available = (singles && tracks.isEmpty()) || tracks.any(factual::available)
            val life = album.life.referenced(sources.isNotEmpty() || singles, available, generation)
            if (life.expired(generation, ttl)) null else album.copy(sources = sources, tracks = tracks, life = life)
        }
    val factualAlbums = albums.filter { it.sources.isNotEmpty() }.associateBy { it.id }

    fun catalogLife(
        old: Life,
        links: List<Long>,
    ) = old.referenced(links.isNotEmpty(), links.any { factualAlbums.getValue(it).life.available }, generation)
    val artists =
        artists.mapNotNull { artist ->
            val links = artist.albums.filter { it in factualAlbums }
            val life = catalogLife(artist.life, links)
            if (life.expired(generation, ttl)) null else artist.copy(albums = links, life = life)
        }
    val genres =
        genres.mapNotNull { genre ->
            val links = genre.albums.filter { it in factualAlbums }
            val life = catalogLife(genre.life, links)
            if (life.expired(generation, ttl)) null else genre.copy(albums = links, life = life)
        }
    val accepted = factual.copy(albums = albums, artists = artists, genres = genres)
    val classified =
        if (mapping.size == nodes.size) {
            accepted
        } else {
            val names = genres.associateBy { canonical(it.name) }
            CatalogBuilder(RandomIds, GenreResolver { value -> names[canonical(value)]?.ref ?: GenreRef(text = canonical(value)) }, grouping, ttl, Cancellation(), vocabulary)
                .build(accepted, accepted, reuseExpansions = true)
        }
    return classified.withAttributes(this, ttl).also(::validate).ordered()
}

/** Source removal and alias transfer are identical for scans and projection-only publications. */
internal fun expireNodes(
    nodes: MutableList<Node?>,
    generation: Long,
    ttl: Long,
) {
    val anchors = nodes.filterNotNull().filter { it.mediaId != null && it.id.lo != null && it.identityFlags and Flags.SHRT != 0 }
    nodes.indices.forEach { index ->
        if (nodes[index]?.life?.expired(generation, ttl) == true) nodes[index] = null
    }

    fun transferAliases() {
        anchors.forEach { anchor ->
            nodes.indices.firstOrNull { nodes[it]?.id == anchor.id && (nodes[it]?.kind == anchor.kind || (anchor.baseIdentity && nodes[it]?.baseIdentity == true)) }?.let { index ->
                val survivor = requireNotNull(nodes[index])
                nodes[index] = survivor.copy(identityFlags = survivor.identityFlags or Flags.SHRT)
            }
        }
    }
    var changed: Boolean
    do {
        transferAliases()
        changed = false
        val directories = nodes.filterNotNull().filter { it.kind == NodeKind.DIRECTORY }.mapTo(mutableSetOf()) { it.id }
        val bases = nodes.filterNotNull().filter { it.baseIdentity }
        nodes.indices.forEach { index ->
            val node = nodes[index] ?: return@forEach
            val parent = node.parentId ?: return@forEach
            val exists = if (node.kind == NodeKind.FRAGMENT) bases.any { IdOrder.matches(it.id, it.flags, parent) } else parent in directories
            if (!exists) {
                nodes[index] = null
                changed = true
            }
        }
    } while (changed)
    transferAliases()
}
