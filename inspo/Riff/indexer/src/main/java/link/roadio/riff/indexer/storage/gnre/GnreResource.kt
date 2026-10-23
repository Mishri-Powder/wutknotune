package link.roadio.riff.indexer.storage.gnre

import com.google.flatbuffers.kotlin.ArrayReadWriteBuffer
import com.google.flatbuffers.kotlin.ReadWriteBuffer
import link.roadio.riff.indexer.fbs.GenreList

data class GnreResourceMetadata(
    val generation: Int,
    val genreCount: Int,
    val indexCount: Int,
)

object GnreResource {
    fun validate(bytes: ByteArray): GnreResourceMetadata = validate(ArrayReadWriteBuffer(bytes))

    fun inspect(buffer: ReadWriteBuffer): GnreResourceMetadata {
        require(GenreList.GenreListBufferHasIdentifier(buffer)) { "Invalid GNRE file identifier" }
        val list = GenreList.asRoot(buffer)
        require(list.generation > 0) { "GNRE generation must be positive" }
        require(list.genresLength > 0) { "GNRE genre vector must not be empty" }
        require(list.indexesLength > 0) { "GNRE index vector must not be empty" }
        return GnreResourceMetadata(list.generation, list.genresLength, list.indexesLength)
    }

    fun validate(buffer: ReadWriteBuffer): GnreResourceMetadata {
        val metadata = inspect(buffer)
        val list = GenreList.asRoot(buffer)

        var previousGenreId: ULong? = null
        repeat(list.genresLength) { position ->
            val genre = requireNotNull(list.genres(position))
            previousGenreId?.let { require(it < genre.id) { "GNRE genres are not sorted and unique" } }
            previousGenreId = genre.id
            require(genre.namesLength > 0) { "GNRE genre has no names" }
            repeat(genre.namesLength) { namePosition ->
                val name = requireNotNull(genre.names(namePosition))
                require(!name.locale.isNullOrBlank()) { "GNRE genre locale is absent" }
                require(!name.name.isNullOrBlank()) { "GNRE genre name is absent" }
            }
            repeat(genre.relatedLength) { relationPosition ->
                val relation = requireNotNull(genre.related(relationPosition))
                require(relation.genreIndex in 0 until list.genresLength) {
                    "GNRE relation is outside the genre vector"
                }
            }
        }

        var previousIndexId: ULong? = null
        repeat(list.indexesLength) { position ->
            val index = requireNotNull(list.indexes(position))
            previousIndexId?.let { require(it < index.id) { "GNRE indexes are not sorted and unique" } }
            previousIndexId = index.id
            require(index.genreIndex in 0 until list.genresLength) {
                "GNRE index is outside the genre vector"
            }
        }
        return metadata
    }
}
