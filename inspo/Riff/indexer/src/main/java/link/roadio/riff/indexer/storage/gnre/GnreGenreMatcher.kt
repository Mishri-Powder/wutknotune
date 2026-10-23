package link.roadio.riff.indexer.storage.gnre

import com.google.flatbuffers.kotlin.ArrayReadWriteBuffer
import com.google.flatbuffers.kotlin.ReadWriteBuffer
import link.roadio.riff.indexer.core.tags.genre.GenreParser
import link.roadio.riff.indexer.fbs.Genre
import link.roadio.riff.indexer.fbs.GenreList
import link.roadio.riff.indexer.fbs.GenreName
import link.roadio.riff.indexer.level1.GenreMatcher

/** GNRE lookup over the immutable, sorted FlatBuffer indexes. */
class GnreGenreMatcher private constructor(
    private val genres: GenreList,
    private val canonicalHash: (String) -> Long,
) : GenreMatcher {
    override val revision: String = "GNRE-${genres.generation}"

    private val compoundByHash: Map<Long, Set<Long>> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val compounds = mutableMapOf<Long, MutableSet<Long>>()
        val genre = Genre()
        val genreName = GenreName()
        repeat(genres.genresLength) { genreIndex ->
            val current = requireNotNull(genres.genres(genre, genreIndex))
            val genreId = current.id.toLong()
            repeat(current.namesLength) { nameIndex ->
                val name = requireNotNull(current.names(genreName, nameIndex)?.name)
                if ('/' in name || '&' in name) {
                    compounds.getOrPut(canonicalHash(GenreParser.comparisonText(name))) {
                        mutableSetOf()
                    } += genreId
                }
            }
        }
        compounds
    }

    override fun match(canonicalGenre: String): Set<Long> {
        val direct =
            genres
                .indexesByKey(canonicalHash(canonicalGenre).toULong())
                ?.let { index ->
                    require(index.genreIndex in 0 until genres.genresLength) {
                        "GNRE index is outside the genre vector"
                    }
                    setOf(requireNotNull(genres.genres(index.genreIndex)).id.toLong())
                }.orEmpty()
        if ('/' !in canonicalGenre && '&' !in canonicalGenre) return direct
        return direct +
            compoundByHash[
                canonicalHash(GenreParser.comparisonText(canonicalGenre)),
            ].orEmpty()
    }

    companion object {
        fun decode(
            bytes: ByteArray,
            canonicalHash: (String) -> Long,
        ): GnreGenreMatcher = decode(ArrayReadWriteBuffer(bytes), canonicalHash)

        fun decode(
            buffer: ReadWriteBuffer,
            canonicalHash: (String) -> Long,
        ): GnreGenreMatcher {
            GnreResource.inspect(buffer)
            return GnreGenreMatcher(GenreList.asRoot(buffer), canonicalHash)
        }
    }
}
