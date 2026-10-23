package link.roadio.riff.indexer.storage.gnre

import com.google.flatbuffers.kotlin.FlatBufferBuilder
import link.roadio.riff.indexer.fbs.Genre
import link.roadio.riff.indexer.fbs.GenreIndex
import link.roadio.riff.indexer.fbs.GenreIndexOffsetArray
import link.roadio.riff.indexer.fbs.GenreList
import link.roadio.riff.indexer.fbs.GenreName
import link.roadio.riff.indexer.fbs.GenreNameOffsetArray
import link.roadio.riff.indexer.fbs.GenreOffsetArray
import link.roadio.riff.indexer.fbs.GenreRelationOffsetArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GnreGenreMatcherTest {
    @Test
    fun `resolves canonical hash indexes to stable GNRE genre IDs`() {
        val builder = FlatBufferBuilder()
        val name =
            GenreName.createGenreName(
                builder,
                builder.createString("en"),
                builder.createString("Rock"),
            )
        val names = Genre.createNamesVector(builder, GenreNameOffsetArray(1) { name })
        val related = Genre.createRelatedVector(builder, GenreRelationOffsetArray(0) { error("empty") })
        val genre = Genre.createGenre(builder, 10334882uL, related, names)
        val index = GenreIndex.createGenreIndex(builder, 77uL, 0)
        val genres = GenreList.createGenresVector(builder, GenreOffsetArray(1) { genre })
        val indexes = GenreList.createIndexesVector(builder, GenreIndexOffsetArray(1) { index })
        val root = GenreList.createGenreList(builder, 1, genres, indexes)
        GenreList.finishGenreListBuffer(builder, root)

        val matcher = GnreGenreMatcher.decode(builder.sizedByteArray()) { if (it == "rock") 77L else 88L }

        assertEquals(setOf(10334882L), matcher.match("rock"))
        assertEquals(emptySet<Long>(), matcher.match("jazz"))
    }

    @Test
    fun `resolves ambiguous compound names omitted from the singular index`() {
        val builder = FlatBufferBuilder()
        val names =
            listOf("Rhythm & Blues", "R&B").map { value ->
                GenreName.createGenreName(
                    builder,
                    builder.createString("en"),
                    builder.createString(value),
                )
            }
        val firstNames = Genre.createNamesVector(builder, GenreNameOffsetArray(1) { names[1] })
        val secondNames = Genre.createNamesVector(builder, GenreNameOffsetArray(1) { names[0] })
        val related = Genre.createRelatedVector(builder, GenreRelationOffsetArray(0) { error("empty") })
        val firstGenre = Genre.createGenre(builder, 1uL, related, firstNames)
        val secondGenre = Genre.createGenre(builder, 2uL, related, secondNames)
        val genres =
            GenreList.createGenresVector(
                builder,
                GenreOffsetArray(2) { if (it == 0) firstGenre else secondGenre },
            )
        val unrelatedIndex = GenreIndex.createGenreIndex(builder, 99uL, 0)
        val indexes = GenreList.createIndexesVector(builder, GenreIndexOffsetArray(1) { unrelatedIndex })
        val root = GenreList.createGenreList(builder, 1, genres, indexes)
        GenreList.finishGenreListBuffer(builder, root)
        val matcher =
            GnreGenreMatcher.decode(builder.sizedByteArray()) { value ->
                if (value.lowercase().replace(" ", "") in setOf("r&b", "rhythm&blues")) 77L else 88L
            }

        assertEquals(setOf(1L, 2L), matcher.match("R&B"))
    }

    @Test
    fun `rejects a GNRE payload with a malformed file identifier`() {
        val bytes = validGnreBytes()
        bytes[4] = 'B'.code.toByte()

        assertThrows(IllegalArgumentException::class.java) {
            GnreGenreMatcher.decode(bytes) { 77L }
        }
    }

    private fun validGnreBytes(): ByteArray {
        val builder = FlatBufferBuilder()
        val name =
            GenreName.createGenreName(
                builder,
                builder.createString("en"),
                builder.createString("Rock"),
            )
        val names = Genre.createNamesVector(builder, GenreNameOffsetArray(1) { name })
        val related = Genre.createRelatedVector(builder, GenreRelationOffsetArray(0) { error("empty") })
        val genre = Genre.createGenre(builder, 10334882uL, related, names)
        val index = GenreIndex.createGenreIndex(builder, 77uL, 0)
        val genres = GenreList.createGenresVector(builder, GenreOffsetArray(1) { genre })
        val indexes = GenreList.createIndexesVector(builder, GenreIndexOffsetArray(1) { index })
        val root = GenreList.createGenreList(builder, 1, genres, indexes)
        GenreList.finishGenreListBuffer(builder, root)
        return builder.sizedByteArray()
    }
}
