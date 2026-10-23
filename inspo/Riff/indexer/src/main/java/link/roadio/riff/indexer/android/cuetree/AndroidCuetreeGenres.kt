package link.roadio.riff.indexer.android.cuetree

import android.content.Context
import link.roadio.cuetree.GenreRef
import link.roadio.cuetree.GenreResolver
import link.roadio.riff.indexer.android.level1.AndroidGenreMatcher
import link.roadio.riff.indexer.android.library.AndroidGnreRepository
import link.roadio.riff.indexer.android.tags.AndroidTagNormalization
import link.roadio.riff.indexer.core.tags.genre.GenreParser
import link.roadio.riff.indexer.core.tags.normalization.TagKind
import link.roadio.riff.indexer.fbs.GenreList
import link.roadio.riff.indexer.fbs.GenreRelationEnum

/** Injects Riff's bundled genre taxonomy without making Cuetree depend on Android or GNRE. */
object AndroidCuetreeGenres {
    /** Exact GNRE SameAs may merge classifier evidence with a metadata genre. */
    fun displayMap(context: Context): Map<Long, Pair<Long, String>> {
        val list = GenreList.asRoot(AndroidGnreRepository(context).installBundled().buffer)
        return (0 until list.genresLength).associate { index ->
            val genre = requireNotNull(list.genres(index))
            val sameAs =
                (0 until genre.relatedLength)
                    .mapNotNull { genre.related(it) }
                    .filter { it.kind == GenreRelationEnum.SameAs }
            val visible =
                if (sameAs.size == 1) {
                    requireNotNull(list.genres(sameAs.single().genreIndex))
                } else {
                    genre
                }
            val name =
                (0 until visible.namesLength)
                    .mapNotNull { visible.names(it) }
                    .firstOrNull { it.locale == "en" }
                    ?.name
                    ?: requireNotNull(visible.names(0)?.name)
            genre.id.toLong() to (visible.id.toLong() to name)
        }
    }

    fun load(context: Context): GenreResolver {
        val matcher = AndroidGenreMatcher.load(context, AndroidGnreRepository(context).installBundled().buffer)
        val tags = AndroidTagNormalization.tags()
        return GenreResolver { text ->
            val canonical = tags.normalize(TagKind.GENRE, text).canonical
            val comparison = GenreParser.comparisonText(canonical)
            val matches = matcher.match(canonical) + if (comparison == canonical) emptySet() else matcher.match(comparison)
            if (matches.size == 1) GenreRef(dictionaryId = matches.single()) else GenreRef(text = canonical)
        }
    }
}
