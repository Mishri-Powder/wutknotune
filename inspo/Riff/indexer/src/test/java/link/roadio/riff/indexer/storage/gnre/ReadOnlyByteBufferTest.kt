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
import java.nio.ByteBuffer

class ReadOnlyByteBufferTest {
    @Test
    fun `validates GNRE directly from a non-array buffer`() {
        val builder = FlatBufferBuilder()
        val name =
            GenreName.createGenreName(
                builder,
                builder.createString("en"),
                builder.createString("rock"),
            )
        val names = Genre.createNamesVector(builder, GenreNameOffsetArray(1) { name })
        val relations = Genre.createRelatedVector(builder, GenreRelationOffsetArray(0) { error("empty") })
        val genre = Genre.createGenre(builder, 7uL, relations, names)
        val genres = GenreList.createGenresVector(builder, GenreOffsetArray(1) { genre })
        val index = GenreIndex.createGenreIndex(builder, 1uL, 0)
        val indexes = GenreList.createIndexesVector(builder, GenreIndexOffsetArray(1) { index })
        val root = GenreList.createGenreList(builder, 3, genres, indexes)
        GenreList.finishGenreListBuffer(builder, root)
        val bytes = builder.sizedByteArray()
        val direct = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes).flip() }
        val buffer = ReadOnlyByteBuffer(direct)

        assertEquals(3, GnreResource.validate(buffer).generation)
        assertThrows(UnsupportedOperationException::class.java) { buffer.data() }
    }
}
