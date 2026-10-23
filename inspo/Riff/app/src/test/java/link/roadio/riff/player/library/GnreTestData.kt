package link.roadio.riff.player.library

import com.google.flatbuffers.kotlin.FlatBufferBuilder
import link.roadio.riff.indexer.fbs.Genre
import link.roadio.riff.indexer.fbs.GenreIndex
import link.roadio.riff.indexer.fbs.GenreIndexOffsetArray
import link.roadio.riff.indexer.fbs.GenreList
import link.roadio.riff.indexer.fbs.GenreName
import link.roadio.riff.indexer.fbs.GenreNameOffsetArray
import link.roadio.riff.indexer.fbs.GenreOffsetArray
import link.roadio.riff.indexer.fbs.GenreRelation
import link.roadio.riff.indexer.fbs.GenreRelationEnum
import link.roadio.riff.indexer.fbs.GenreRelationOffsetArray

internal const val TEST_GENRE_ID = 7L
private const val AUDIOBOOK_GENRE_ID = 106833L
private val AUDIOBOOK_NAMES =
    listOf(
        "en" to "audiobook",
        "en" to "audio book",
        "en" to "talking book",
        "en" to "audiobooks",
        "ru" to "аудиокнига",
        "ru" to "аудио-книга",
        "ru" to "аудиокниги",
    )

internal fun testGnreDisplayNames(vararg names: Pair<String, String>): GnreDisplayNames {
    require(names.isNotEmpty())
    val builder = FlatBufferBuilder()
    val values = listOf(TEST_GENRE_ID to names.toList(), AUDIOBOOK_GENRE_ID to AUDIOBOOK_NAMES)
    val genreOffsets =
        values.map { (genreId, genreNames) ->
            val nameOffsets =
                genreNames.map { (locale, name) ->
                    GenreName.createGenreName(
                        builder,
                        builder.createString(locale),
                        builder.createString(name),
                    )
                }
            val nameVector =
                Genre.createNamesVector(
                    builder,
                    GenreNameOffsetArray(nameOffsets.size) { nameOffsets[it] },
                )
            val related = Genre.createRelatedVector(builder, GenreRelationOffsetArray(0) { error("empty") })
            Genre.createGenre(builder, genreId.toULong(), related, nameVector)
        }
    val index = GenreIndex.createGenreIndex(builder, 1uL, 0)
    val genres = GenreList.createGenresVector(builder, GenreOffsetArray(genreOffsets.size) { genreOffsets[it] })
    val indexes = GenreList.createIndexesVector(builder, GenreIndexOffsetArray(1) { index })
    val root = GenreList.createGenreList(builder, 1, genres, indexes)
    GenreList.finishGenreListBuffer(builder, root)
    return GnreDisplayNames.decode(builder.sizedByteArray())
}

internal fun testGnreDisplayNamesById(
    values: List<Pair<Long, String>>,
    sameAs: Map<Long, Long> = emptyMap(),
): GnreDisplayNames {
    require(values.isNotEmpty())
    val builder = FlatBufferBuilder()
    val ordered =
        (
            values.map { (genreId, name) -> genreId to listOf("en" to name) } +
                if (values.any { it.first == AUDIOBOOK_GENRE_ID }) {
                    emptyList()
                } else {
                    listOf(AUDIOBOOK_GENRE_ID to AUDIOBOOK_NAMES)
                }
        ).sortedBy { (genreId, _) -> genreId.toULong() }
    val positions = ordered.mapIndexed { index, (genreId, _) -> genreId to index }.toMap()
    val genreOffsets =
        ordered
            .map { (genreId, localizedNames) ->
                val nameOffsets =
                    localizedNames.map { (locale, name) ->
                        GenreName.createGenreName(
                            builder,
                            builder.createString(locale),
                            builder.createString(name),
                        )
                    }
                val names =
                    Genre.createNamesVector(
                        builder,
                        GenreNameOffsetArray(nameOffsets.size) { nameOffsets[it] },
                    )
                val target = sameAs[genreId]
                val relationOffsets =
                    target
                        ?.let {
                            listOf(
                                GenreRelation.createGenreRelation(
                                    builder,
                                    GenreRelationEnum.SameAs,
                                    requireNotNull(positions[it]),
                                ),
                            )
                        }.orEmpty()
                val related =
                    Genre.createRelatedVector(
                        builder,
                        GenreRelationOffsetArray(relationOffsets.size) { relationOffsets[it] },
                    )
                Genre.createGenre(builder, genreId.toULong(), related, names)
            }
    val index = GenreIndex.createGenreIndex(builder, 1uL, 0)
    val genres = GenreList.createGenresVector(builder, GenreOffsetArray(genreOffsets.size) { genreOffsets[it] })
    val indexes = GenreList.createIndexesVector(builder, GenreIndexOffsetArray(1) { index })
    val root = GenreList.createGenreList(builder, 1, genres, indexes)
    GenreList.finishGenreListBuffer(builder, root)
    return GnreDisplayNames.decode(builder.sizedByteArray())
}
