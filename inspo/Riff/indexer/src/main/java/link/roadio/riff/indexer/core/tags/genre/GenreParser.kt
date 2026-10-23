package link.roadio.riff.indexer.core.tags.genre

import link.roadio.riff.indexer.core.tags.list.TagValueListParser
import link.roadio.riff.indexer.level1.GenreMatcher

/** GP-2..GP-5: accepts whole, already repaired display values; never repairs fragments. */
object GenreParser {
    const val REVISION = "GENRE-PROCESSING-V2/" + TagValueListParser.REVISION

    data class Component(
        val text: String,
        val genreIds: Set<Long>,
    )

    /** A comparison copy only; whitespace between words and source display text are retained. */
    fun comparisonText(value: String): String =
        buildString {
            val whitespace = StringBuilder()
            value.trim().forEach { character ->
                if (character.isWhitespace()) {
                    whitespace.append(character)
                } else {
                    if (character != '/' && character != '&' && lastOrNull() != '/' && lastOrNull() != '&') {
                        append(whitespace)
                    }
                    whitespace.setLength(0)
                    append(character)
                }
            }
        }

    fun parse(
        values: List<String>,
        matcher: GenreMatcher,
    ): List<Component> {
        val matches = mutableMapOf<String, Set<Long>>()

        fun match(value: String): Set<Long> =
            matches.getOrPut(value) {
                val comparison = comparisonText(value)
                val exact = matcher.match(value)
                if (comparison == value) exact else exact + matcher.match(comparison)
            }

        return TagValueListParser
            .split(values, TagValueListParser.DelimiterPolicy.GENRE) { match(it).isNotEmpty() }
            .map { value -> Component(value, match(value)) }
    }
}
