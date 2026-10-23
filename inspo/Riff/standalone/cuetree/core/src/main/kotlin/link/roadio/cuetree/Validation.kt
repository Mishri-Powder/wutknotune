// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** The same semantic gate protects in-memory publication and decoded snapshots. */
fun validate(snapshot: Snapshot) {
    require(snapshot.generation >= 0 && snapshot.identityScheme.isNotBlank())

    fun life(life: Life) {
        require(life.generation in 0..snapshot.generation)
    }
    val folders = mutableSetOf<MediaId>()
    val roots = mutableSetOf<Pair<String, String>>()
    for ((index, n) in snapshot.nodes.withIndex()) {
        life(n.life)
        require(n.identityFlags and (Flags.RESL or Flags.SHRT or Flags.FAILEDL2).inv() == 0)
        if (n.isRoot) {
            val source = requireNotNull(n.source)
            require(!source.provider.isNullOrBlank() && !source.locator.isNullOrBlank())
            val rootIdentity = n.attributes[ROOT_IDENTITY_ATTRIBUTE]?.singleOrNull()
            require(roots.add(source.provider to (rootIdentity?.let { "identity:$it" } ?: "locator:${source.locator}")))
        } else {
            require(n.parentId != null)
            val parentIndex = requireNotNull(snapshot.parentOf(index))
            require(parentIndex != index)
            val parent = snapshot.nodes[parentIndex]
            require(if (n.kind == NodeKind.FRAGMENT) parent.baseIdentity else parent.kind == NodeKind.DIRECTORY)
            require(!n.life.available || parent.life.available)
            if (n.source != null) require(n.source.provider == null && n.source.locator == null && component(n.source.name))
        }
        when (n.kind) {
            NodeKind.DIRECTORY -> {
                require(n.id.lo == null && folders.add(n.id) && n.source != null && n.span == null && n.identityFlags == 0)
            }

            NodeKind.MEDIA -> {
                require(n.source != null && n.span == null)
            }

            NodeKind.METADATA -> {
                require(n.id.lo == null && folders.add(n.id) && n.source != null && n.span == null && n.identityFlags == 0)
            }

            NodeKind.FAILEDL1 -> {
                require(n.source != null && n.span == null && n.identityFlags and Flags.FAILEDL2 == 0)
            }

            NodeKind.FRAGMENT -> {
                require(n.source == null && n.span != null)
                val ordinal = n.attributes.first("fragment")?.toLongOrNull()
                val total = n.attributes.first("totalFragments")?.toLongOrNull()
                require(ordinal != null && total != null && ordinal in 1..total)
            }
        }
        require(n.tables.isEmpty() || n.kind == NodeKind.MEDIA || n.kind == NodeKind.METADATA || n.kind == NodeKind.FAILEDL1)
        require(n.sourceEvidence.isEmpty() || n.kind == NodeKind.MEDIA || (n.kind == NodeKind.FAILEDL1 && n.mediaId != null))
        n.tables.forEach { table ->
            require(table.sections.size > 1)
            table.sections.forEach { section -> require(section.reference == null || section.reference.isNotBlank()) }
        }
        if (n.mediaId != null) {
            if (n.id.lo == null) require(n.identityFlags and Flags.SHRT != 0)
        }
        require(n.identityFlags and Flags.FAILEDL2 == 0 || n.kind == NodeKind.MEDIA)
        var parent = snapshot.parentOf(index)
        val visited = mutableSetOf<Int>()
        while (parent != null) {
            require(visited.add(parent))
            parent = snapshot.parentOf(parent)
        }
    }
    val physical = snapshot.nodes.filter { it.kind != NodeKind.FRAGMENT && !it.isRoot }
    require(physical.map { it.parentId to it.name }.distinct().size == physical.size)
    require(
        snapshot.genres
            .map { it.id }
            .distinct()
            .size == snapshot.genres.size,
    )
    val attributeIds = mutableSetOf<MediaId>()
    snapshot.attributeCatalog.forEach {
        life(it.life)
        require(attributeIds.add(it.id))
        it.value.entry()
    }
    if (snapshot.attributeCatalog.isNotEmpty()) {
        val dictionary = snapshot.attributeCatalog.associateBy { it.id }
        val referenced = snapshot.nodes.flatMap { node -> node.attributes.typed() + node.sourceEvidence.typed() + node.tables.flatMap { table -> table.attributes.typed() + table.sections.flatMap { it.attributes.typed() } } } + snapshot.albums.flatMap { it.attributes.typed() } + snapshot.artists.flatMap { it.attributes.typed() } + snapshot.genres.flatMap { it.attributes.typed() }
        referenced.forEach { value -> require(dictionary[value.id]?.value == value) { "Missing or conflicting attribute" } }
    }
    val albumIds = snapshot.albums.map { it.id }.toSet()
    require(albumIds.size == snapshot.albums.size)
    require(snapshot.albums.count { it.kind == AlbumKind.SINGLES } <= 1)
    val personal = snapshot.albums.filter { it.kind == AlbumKind.ARTIST_SINGLES }
    require(personal.all { SinglesClassifier.credit(it.attributes["albumArtist"].orEmpty()) != null })
    require(personal.map { SinglesClassifier.credit(it.attributes["albumArtist"].orEmpty()) }.distinct().size == personal.size)
    require(snapshot.albums.filter { it.kind.isSingles }.all { album -> album.sources.all { it in snapshot.nodes.indices && snapshot.nodes[it].baseIdentity && snapshot.nodes[it].tables.isEmpty() } })
    snapshot.albums.forEach { a ->
        life(a.life)
        if (a.kind == AlbumKind.EXPLICIT) {
            require(a.sources.all { it in snapshot.nodes.indices && snapshot.nodes[it].kind == NodeKind.FRAGMENT })
            require(a.tracks == a.sources.map { snapshot.nodes[it].mediaId })
        }
        require(a.tracks == orderedAlbumTrackIds(snapshot.nodes, a.sources))
        require(a.tracks.all { snapshot.sources(it).isNotEmpty() })
        require(a.sources.distinct().size == a.sources.size && a.sources.all { it in snapshot.nodes.indices && snapshot.nodes[it].mediaId in a.tracks })
        require(a.life.available == ((a.kind == AlbumKind.SINGLES && a.tracks.isEmpty()) || a.tracks.any(snapshot::available)))
    }
    require(
        snapshot.artists
            .map { it.id }
            .distinct()
            .size == snapshot.artists.size,
    )
    require(
        snapshot.artists
            .map { it.key }
            .distinct()
            .size == snapshot.artists.size,
    )
    snapshot.artists.forEach { a ->
        life(a.life)
        require(a.albums.distinct().size == a.albums.size && a.albums.all { it in albumIds })
        require(a.life.available == snapshot.albums.any { it.id in a.albums && it.life.available })
    }
    require(
        snapshot.genres
            .map { it.ref }
            .distinct()
            .size == snapshot.genres.size,
    )
    snapshot.genres.forEach { g ->
        life(g.life)
        require(g.albums.distinct().size == g.albums.size && g.albums.all { it in albumIds })
        require(g.life.available == snapshot.albums.any { it.id in g.albums && it.life.available })
    }
}
