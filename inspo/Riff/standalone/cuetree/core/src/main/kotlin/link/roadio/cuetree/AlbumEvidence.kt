// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Parsed once per immutable grouping input; no provider or metadata reader is involved. */
internal class AlbumEvidence(
    val snapshot: Snapshot,
    sources: List<Int>,
    val cancellation: Cancellation,
    val vocabulary: CatalogVocabulary = CatalogVocabulary.EMPTY,
) {
    private val filenameOrdinals = FilenameOrdinals.derive(snapshot)
    val facts =
        sources.associateWith { index ->
            cancellation.check()
            AlbumFact(index, snapshot.nodes[index], filenameOrdinals[index], vocabulary)
        }
    val families = DiscFamilies(snapshot, facts, cancellation, vocabulary)
    val order = compareBy<Int>({ facts.getValue(it).disc ?: families.ordinal(it) ?: Int.MAX_VALUE }, { facts.getValue(it).track ?: Int.MAX_VALUE }, { canonical(snapshot.nodes[it].name) }, { snapshot.nodes[it].parentId?.hi }, { snapshot.nodes[it].name }, { snapshot.nodes[it].id.hi }, { snapshot.nodes[it].id.lo })
    val stable = compareBy<Int>({ !snapshot.nodes[it].life.available }, { snapshot.nodes[it].parentId?.hi }, { canonical(snapshot.nodes[it].name) }, { snapshot.nodes[it].name }, { snapshot.nodes[it].id.hi }, { snapshot.nodes[it].id.lo })

    fun disc(index: Int) = facts.getValue(index).disc ?: families.ordinal(index)

    fun key(index: Int) = families.albumKey(index) ?: facts.getValue(index).album

    fun title(group: List<Int>): String =
        group
            .map { families.albumTitle(it) ?: facts.getValue(it).displayAlbum }
            .filter(String::isNotBlank)
            .minWithOrNull(compareBy({ canonical(it) }, { it }))
            .orEmpty()

    fun agreed(group: List<Int>): Attributes {
        val attrs = group.map { snapshot.nodes[it].attributes }
        return attrs.first().filter { (key, values) -> key != "title" && key !in POSITION_KEYS && attrs.all { it[key] == values } } + ("album" to listOf(title(group)))
    }

    companion object {
        val POSITION_KEYS = setOf("trackNumber", "trackTotal", "discNumber", "discTotal", "fragment", "totalFragments")
    }
}

internal data class AlbumFact(
    val index: Int,
    val node: Node,
    val filenameOrdinal: Int? = null,
    private val vocabulary: CatalogVocabulary = CatalogVocabulary.EMPTY,
) {
    val attrs = node.attributes
    val displayAlbum = attrs.first("album").orEmpty()
    val album = canonical(displayAlbum)
    val title = canonical(attrs.first("title").orEmpty())
    val artist = components("artist").takeUnless { it.isNotEmpty() && it.all(vocabulary.placeholderArtists::contains) }.orEmpty()
    val rawAa = components("albumArtist").takeUnless { it.isNotEmpty() && it.all(vocabulary.placeholderAlbumArtists::contains) }.orEmpty()
    val aa = rawAa.takeUnless { it.isNotEmpty() && it.all(vocabulary.genericArtists::contains) }.orEmpty()
    val genre = components("genre")
    val comment = components("comment")
    val year =
        (attrs.first("year")?.takeIf { it.matches(Regex("[0-9]{4}")) } ?: attrs.first("date")?.take(4))
            ?.take(4)
            ?.takeIf { it.matches(Regex("[0-9]{4}")) }
            ?.toInt()
            ?.takeIf { it > 0 }
    val track = number("trackNumber") ?: filenameOrdinal
    val total = number("trackTotal") ?: number("trackNumber", 1)
    val disc = number("discNumber")
    val discs = number("discTotal") ?: number("discNumber", 1)
    val boundsInvalid = (track != null && total != null && track > total) || (disc != null && discs != null && disc > discs)
    val placeholderAlbum = vocabulary.placeholderAlbum(album)

    private fun number(
        key: String,
        part: Int = 0,
    ) = attrs
        .first(key)
        ?.split('/')
        ?.getOrNull(part)
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

    private fun components(key: String) =
        attrs[key]
            .orEmpty()
            .map(::canonical)
            .filter(String::isNotBlank)
            .toSortedSet()
}
