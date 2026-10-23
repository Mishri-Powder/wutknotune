package link.roadio.riff.player.library

import com.google.flatbuffers.kotlin.ArrayReadWriteBuffer
import com.google.flatbuffers.kotlin.ReadWriteBuffer
import link.roadio.riff.indexer.android.tags.AndroidTagNormalization
import link.roadio.riff.indexer.core.tags.normalization.TagKind
import link.roadio.riff.indexer.fbs.Genre
import link.roadio.riff.indexer.fbs.GenreList
import link.roadio.riff.indexer.fbs.GenreRelationEnum
import link.roadio.riff.indexer.storage.gnre.GnreResource
import link.roadio.riff.indexer.storage.gnre.requireAudiobookGenre
import java.util.Locale

/** Player-side validated view of the immutable GNRE names used by hierarchy rows. */
class GnreDisplayNames private constructor(
    private val genres: GenreList,
) {
    val generation: Int = genres.generation

    fun displayName(
        genreId: Long,
        systemLocale: Locale,
    ): String {
        val genre = requireNotNull(genre(genreId)) { "MDIA references an unknown GNRE genre" }
        val language = systemLocale.language.lowercase(Locale.ROOT)
        // Authority: https://wiki.271.pics/Riff/LATEST/genre-locales
        // AI-INTENT: GNRE stores base languages only. Preserve vector order within each locale group.
        return (
            genre.firstName(language)
                ?: genre.firstName(ENGLISH_LANGUAGE)
                ?: requireNotNull(genre.names(0)?.name)
        )
    }

    fun visibleGenreId(evidenceId: Long): Long? {
        val evidence = genre(evidenceId) ?: return null
        if (evidenceId !in MTG_GENRE_ID_RANGE) return evidenceId
        val targets =
            List(evidence.relatedLength) { index -> requireNotNull(evidence.related(index)) }
                .filter { relation -> relation.kind == GenreRelationEnum.SameAs }
        if (targets.size > 1) return null
        val target = targets.singleOrNull() ?: return evidenceId
        val visible = genres.genres(target.genreIndex)?.id?.toLong() ?: return null
        return visible.takeUnless { it in MTG_GENRE_ID_RANGE }
    }

    /** Uses the same RIFF-TAG canonicalization and GNRE alias index as Indexer. */
    fun recognizes(text: String): Boolean {
        if (text.isBlank()) return false
        val id =
            runCatching {
                AndroidTagNormalization
                    .tags()
                    .normalize(TagKind.GENRE, text)
                    .id
                    .toULong()
            }.getOrElse {
                // Local JVM tests do not provide Android ICU; production always uses the indexed path.
                val normalized =
                    java.text.Normalizer
                        .normalize(text, java.text.Normalizer.Form.NFKC)
                        .lowercase(Locale.ROOT)
                return (0 until genres.genresLength).any { genreIndex ->
                    val genre = requireNotNull(genres.genres(genreIndex))
                    (0 until genre.namesLength).any { nameIndex ->
                        val name = requireNotNull(genre.names(nameIndex)).name
                        java.text.Normalizer
                            .normalize(requireNotNull(name), java.text.Normalizer.Form.NFKC)
                            .lowercase(Locale.ROOT) == normalized
                    }
                }
            }
        var low = 0
        var high = genres.indexesLength - 1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val candidate = requireNotNull(genres.indexes(middle)).id
            when {
                candidate < id -> low = middle + 1
                candidate > id -> high = middle - 1
                else -> return true
            }
        }
        return false
    }

    private fun genre(genreId: Long): Genre? = genres.genresByKey(genreId.toULong())

    private fun Genre.firstName(language: String): String? {
        repeat(namesLength) { index ->
            val value = requireNotNull(names(index))
            if (value.locale == language) return requireNotNull(value.name)
        }
        return null
    }

    companion object {
        private const val ENGLISH_LANGUAGE = "en"
        private val MTG_GENRE_ID_RANGE = -100_086L..-100_000L

        fun decode(bytes: ByteArray): GnreDisplayNames = decode(ArrayReadWriteBuffer(bytes))

        fun decode(buffer: ReadWriteBuffer): GnreDisplayNames {
            GnreResource.inspect(buffer)
            requireAudiobookGenre(buffer)
            return GnreDisplayNames(GenreList.asRoot(buffer))
        }
    }
}
