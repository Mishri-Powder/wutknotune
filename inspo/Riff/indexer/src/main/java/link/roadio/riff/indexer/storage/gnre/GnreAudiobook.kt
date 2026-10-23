package link.roadio.riff.indexer.storage.gnre

import com.google.flatbuffers.kotlin.ReadWriteBuffer
import link.roadio.riff.indexer.core.library.AUDIOBOOK_GENRE_ID
import link.roadio.riff.indexer.fbs.GenreList

fun requireAudiobookGenre(buffer: ReadWriteBuffer) {
    val genres = GenreList.asRoot(buffer)
    requireNotNull(genres.genresByKey(AUDIOBOOK_GENRE_ID.toULong())) {
        "GNRE is missing audiobook Q106833"
    }
}
