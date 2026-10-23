// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/**
 * Host-selected language evidence. No language is implicit in the independent library.
 * AI-INTENT: Use the same immutable input for scans and publication-only expiry. Genre roles are
 * dictionary identities; translated genre strings belong to the injected resolver, not this policy.
 * Contract: https://wiki.271.pics/Cuetree/singles#configured-language-evidence
 */
class CatalogVocabulary(
    genericArtists: Set<String> = emptySet(),
    placeholderArtists: Set<String> = emptySet(),
    placeholderAlbumArtists: Set<String> = emptySet(),
    placeholderAlbums: Set<String> = emptySet(),
    genericTitles: Set<String> = emptySet(),
    versionWords: Set<String> = emptySet(),
    protectedWords: Set<String> = emptySet(),
    setMarkers: Set<String> = emptySet(),
    discWords: Set<String> = emptySet(),
    spokenGenreIds: Set<Long> = emptySet(),
    liveGenreIds: Set<Long> = emptySet(),
    electronicGenreIds: Set<Long> = emptySet(),
) {
    internal val genericArtists = genericArtists.map(::canonical).toSet()
    internal val placeholderArtists = placeholderArtists.map(::canonical).toSet()
    internal val placeholderAlbumArtists = placeholderAlbumArtists.map(::canonical).toSet()
    private val placeholderAlbums = placeholderAlbums.map(::canonical).toSet()
    internal val spokenGenreIds = spokenGenreIds.toSet()
    internal val liveGenreIds = liveGenreIds.toSet()
    internal val electronicGenreIds = electronicGenreIds.toSet()
    private val genericTitle = Regex("(?:${alternatives(genericTitles)})\\s*[0-9]*")
    internal val version = Regex("(?<![\\p{L}\\p{N}_])(?:${alternatives(versionWords)})(?![\\p{L}\\p{N}_])")
    internal val protected = Regex("(?<![\\p{L}\\p{N}_])(?:${alternatives(protectedWords)})\\.?\\s*([0-9]+|[ivx]+)(?![\\p{L}\\p{N}_])")
    internal val setMarker = Regex("(?<![\\p{L}\\p{N}_])(?:${alternatives(setMarkers, flexibleSpace = true)})(?![\\p{L}\\p{N}_])")

    internal val discWord = Regex("(?i)(?:${alternatives(discWords)})[\\s._-]*[0-9]+")
    internal val markedDisc = Regex("(?i)(?<![\\p{L}\\p{N}])(?:${alternatives(discWords)})[\\s._-]*([0-9]+)(?![\\p{L}\\p{N}])")

    internal fun genericTitle(title: String) = genericTitle.matches(title)

    internal fun placeholderAlbum(title: String): Boolean {
        val value = canonical(title)
        return value in placeholderAlbums || placeholderAlbums.any { value.startsWith("$it (") } || value.matches(Regex("#[0-9]+"))
    }

    companion object {
        val EMPTY = CatalogVocabulary()

        private fun alternatives(
            values: Set<String>,
            flexibleSpace: Boolean = false,
        ): String =
            values
                .map(::canonical)
                .filter(String::isNotBlank)
                .distinct()
                .sortedByDescending(String::length)
                .joinToString("|") {
                    if (flexibleSpace) it.split(' ').joinToString("[ -]+", transform = Regex::escape) else Regex.escape(it)
                }.ifEmpty { "(?!)" }
    }
}
