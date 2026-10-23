package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.AlbumKind
import link.roadio.cuetree.Genre
import link.roadio.cuetree.GenreRef
import link.roadio.cuetree.GenreResolver
import link.roadio.cuetree.Life
import link.roadio.cuetree.NodeKind
import link.roadio.cuetree.RandomIds
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.withCompleteAttributeCatalog
import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot
import link.roadio.riff.indexer.core.analysis.AnalysisState
import link.roadio.riff.indexer.core.analysis.resolve

/** Publishes available catalog containers with their complete retained member rows. */
internal object CuetreePlayerProjection {
    private const val AUDIOBOOK_GENRE_ID = 106833L
    private const val AUTOMATIC_BOOK = "automaticBook"

    fun build(
        snapshot: Snapshot,
        genres: GenreResolver,
        analysis: AnalysisSnapshot? = null,
        displayGenres: Map<Long, Pair<Long, String>> = emptyMap(),
        retainedGenres: List<Genre> = emptyList(),
        isAudiobooksRoot: (String) -> Boolean,
    ): Snapshot {
        val nodes = snapshot.nodes
        val preferredOccurrences =
            nodes.indices
                .filter { nodes[it].kind == NodeKind.MEDIA || nodes[it].kind == NodeKind.FRAGMENT }
                .groupBy { nodes[it].id }
                .mapValues { (_, copies) -> copies.firstOrNull { nodes[it].life.available } ?: copies.first() }

        fun rootLocator(index: Int): String? {
            var cursor = index
            while (true) {
                val parent = snapshot.parentOf(cursor) ?: return nodes[cursor].source?.locator
                cursor = parent
            }
        }

        val automaticBookIds =
            preferredOccurrences
                .mapNotNull { (id, index) ->
                    val node = nodes[index]
                    val genre =
                        node.attributes["genre"]
                            .orEmpty()
                            .any { genres.resolve(it).dictionaryId == AUDIOBOOK_GENRE_ID }
                    val mediaKind = node.attributes["mediaKind"]?.singleOrNull() == "2"
                    val physical = if (node.kind == NodeKind.FRAGMENT) snapshot.parentOf(index)?.let(nodes::get) else node
                    val m4b = physical?.source?.name?.endsWith(".m4b", ignoreCase = true) == true
                    val root = rootLocator(index)?.let(isAudiobooksRoot) == true
                    id.takeIf { genre || mediaKind || m4b || root }
                }.toSet()

        fun availableOccurrence(index: Int): Int? {
            val node = nodes[index]
            if (node.life.available) return index
            return snapshot.sources(node.id).firstOrNull { candidate ->
                val alternate = nodes[candidate]
                alternate.life.available &&
                    alternate.kind == node.kind &&
                    alternate.span == node.span &&
                    alternate.attributes == node.attributes
            }
        }
        val albumRows =
            snapshot.albums.mapNotNull { album ->
                if (!album.life.available) return@mapNotNull null
                val retained =
                    album.sources.mapNotNull { source ->
                        // Preserve the declared FILF occurrence and its own metadata; audio binding resolves alternatives.
                        // https://wiki.271.pics/Cuetree/catalogs#albm
                        if (nodes[source].kind == NodeKind.FRAGMENT) {
                            source
                        } else {
                            availableOccurrence(source) ?: source
                        }
                    }
                if (retained.isEmpty()) return@mapNotNull null
                val ids = retained.map { nodes[it].id }
                val automatic = !album.kind.isSingles && ids.any { it in automaticBookIds }
                album.copy(
                    sources = retained,
                    tracks = ids,
                    attributes = if (automatic) album.attributes + (AUTOMATIC_BOOK to listOf("1")) else album.attributes,
                )
            }
        val albumIds = albumRows.mapTo(mutableSetOf()) { it.id }
        val artistRows =
            snapshot.artists.mapNotNull { artist ->
                if (!artist.life.available) return@mapNotNull null
                artist.copy(albums = artist.albums.filter { it in albumIds }).takeIf { it.albums.isNotEmpty() }
            }
        val genreRows =
            snapshot.genres
                .mapNotNull { genre ->
                    if (!genre.life.available) return@mapNotNull null
                    genre.copy(albums = genre.albums.filter { it in albumIds }).takeIf { it.albums.isNotEmpty() }
                }.toMutableList()
        // MDRG republishes repeatedly; index catalog membership once instead of searching every album for every result.
        val albumPositionsBySource = mutableMapOf<Int, MutableList<Int>>()
        albumRows.forEachIndexed { position, album ->
            if (!album.attributes.containsKey(AUTOMATIC_BOOK)) {
                album.sources.distinct().forEach { source ->
                    albumPositionsBySource.getOrPut(source, ::mutableListOf).add(position)
                }
            }
        }
        val inferred = mutableMapOf<Long, MutableSet<Long>>()
        analysis?.resolve(snapshot)?.filterValues { it.aiState == AnalysisState.AVAILABLE }?.forEach { (mediaId, record) ->
            val matchingAlbums =
                snapshot
                    .sources(mediaId)
                    .flatMap { albumPositionsBySource[it].orEmpty() }
                    .distinct()
                    .sorted()
                    .map { albumRows[it].id }
            record.genres.forEach { evidence ->
                val visible = displayGenres[evidence.genreId]?.first ?: return@forEach
                inferred.getOrPut(visible, ::mutableSetOf).addAll(matchingAlbums)
            }
        }
        inferred.forEach { (genreId, addedAlbums) ->
            if (addedAlbums.isEmpty()) return@forEach
            val position = genreRows.indexOfFirst { it.ref.dictionaryId == genreId }
            if (position >= 0) {
                val old = genreRows[position]
                genreRows[position] =
                    old.copy(
                        albums = (old.albums + addedAlbums).distinct(),
                        life = Life(snapshot.generation),
                    )
            } else {
                val name =
                    displayGenres[genreId]?.second
                        ?: displayGenres.values.firstOrNull { it.first == genreId }?.second
                        ?: return@forEach
                val used = (snapshot.genres + retainedGenres + genreRows).mapTo(mutableSetOf()) { it.id }
                val id =
                    retainedGenres.firstOrNull { it.ref.dictionaryId == genreId }?.id
                        ?: generateSequence(RandomIds::next).take(100).first { it !in used }
                genreRows +=
                    Genre(
                        ref = GenreRef(dictionaryId = genreId),
                        name = name,
                        albums = addedAlbums.toList().sorted(),
                        life = Life(snapshot.generation),
                        id = id,
                    )
            }
        }
        val playerNodes =
            nodes.map { node ->
                if (node.kind != NodeKind.DIRECTORY && node.id in automaticBookIds) {
                    node.copy(attributes = node.attributes + (AUTOMATIC_BOOK to listOf("1")), sourceEvidence = emptyMap())
                } else {
                    node.copy(sourceEvidence = emptyMap())
                }
            }
        val projected =
            snapshot.copy(
                nodes = playerNodes,
                albums = albumRows,
                artists = artistRows,
                genres = genreRows,
            )
        val retained = projected.nodes.indices.filter { projected.nodes[it].kind != NodeKind.METADATA }
        val indices = retained.withIndex().associate { (new, old) -> old to new }
        return projected
            .copy(
                nodes = retained.map { projected.nodes[it] },
                albums =
                    projected.albums.map { album ->
                        album.copy(sources = album.sources.map(indices::getValue))
                    },
            ).withCompleteAttributeCatalog()
    }
}
