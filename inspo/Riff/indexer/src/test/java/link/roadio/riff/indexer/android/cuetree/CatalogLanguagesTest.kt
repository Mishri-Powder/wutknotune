package link.roadio.riff.indexer.android.cuetree

import com.dynatrace.hash4j.hashing.Hashing
import link.roadio.cuetree.AlbumKind
import link.roadio.cuetree.GenreRef
import link.roadio.cuetree.GenreResolver
import link.roadio.cuetree.Indexer
import link.roadio.cuetree.MetadataReader
import link.roadio.cuetree.MockMediaID
import link.roadio.cuetree.Observation
import link.roadio.cuetree.RootSpec
import link.roadio.cuetree.SOURCE_DURATION_US
import link.roadio.cuetree.Snapshot
import link.roadio.riff.config.ProjectCatalogVocabulary
import link.roadio.riff.indexer.core.tags.normalization.TagKind
import link.roadio.riff.indexer.core.tags.normalization.TagNormalizer
import link.roadio.riff.indexer.core.tags.normalization.TagUnicodePlatform
import link.roadio.riff.indexer.core.tags.normalization.TagXxh3
import link.roadio.riff.indexer.storage.gnre.GnreGenreMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.text.Normalizer
import java.util.Locale

class CatalogLanguagesTest {
    private val tags =
        TagNormalizer(
            object : TagUnicodePlatform {
                override val revision = "test-jvm"

                override fun nfkc(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)

                override fun caseFold(value: String) = value.lowercase(Locale.ROOT)
            },
            TagXxh3 { Hashing.xxh3_64().hashBytesToLong(it) },
        )
    private val matcher =
        GnreGenreMatcher.decode(File("src/main/assets/gnre/gnre.fb").readBytes()) {
            tags.normalize(TagKind.GENRE, it).id
        }
    private val resolver =
        GenreResolver { text ->
            val ids = matcher.match(tags.normalize(TagKind.GENRE, text).canonical)
            if (ids.size == 1) GenreRef(dictionaryId = ids.single()) else GenreRef(text = text)
        }

    private fun scan(
        genreNames: List<String>,
        artist: String = "Band",
        count: Int = 8,
    ): Snapshot {
        val root = Files.createTempDirectory("catalog-languages-")
        try {
            (1..count).forEach { Files.writeString(root.resolve("$it.flac"), "unique recording $it") }
            return Indexer(
                MockMediaID(),
                10,
                metadata =
                    MetadataReader { input, _, _ ->
                        val number = input.id.substringBefore('.').toInt()
                        val ids = genreNames.mapNotNull { resolver.resolve(it).dictionaryId }.map(Long::toString)
                        Observation(
                            mapOf(
                                "title" to listOf("Position $number"),
                                "album" to listOf("Event"),
                                "artist" to listOf(artist),
                                "albumArtist" to listOf(artist),
                                "trackNumber" to listOf("$number"),
                                "trackTotal" to listOf("$count"),
                                "genre" to genreNames,
                                "genreId" to ids,
                            ),
                            emptyList(),
                            emptyList(),
                            sourceEvidence = mapOf(SOURCE_DURATION_US to listOf("180000000")),
                        )
                    },
                genres = resolver,
                vocabulary = AndroidCatalogVocabulary.value,
            ).scan(listOf(RootSpec("posix", root.toString(), "Music")), generation = 1)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun localizedLiveAndElectronicGenresUseThePackagedDictionary() {
        for ((live, electronic) in listOf(
            "En directo" to "electrónica",
            "Ao vivo" to "música eletrônica",
            "Живое выступление" to "электронная музыка",
            "Liveaufnahme" to "elektronische Musik",
        )) {
            assertTrue(live, resolver.resolve(live).dictionaryId in ProjectCatalogVocabulary.liveGenreIds)
            assertTrue(electronic, resolver.resolve(electronic).dictionaryId in ProjectCatalogVocabulary.electronicGenreIds)
            assertEquals(live, AlbumKind.SINGLES, scan(listOf(live, electronic)).albums.single { it.sources.isNotEmpty() }.kind)
        }
    }

    @Test fun localizedAudiobooksRemainProtectedEvenWhenShort() {
        for (name in listOf("Libros y voz", "Livros e Discursos", "Hörbuch", "аудиокниги")) {
            assertEquals(name, AlbumKind.ORDINARY, scan(listOf(name), count = 2).albums.single { it.sources.isNotEmpty() }.kind)
        }
    }

    @Test fun liveAloneDoesNotTurnAnOrdinaryConcertIntoSingles() {
        assertEquals(AlbumKind.ORDINARY, scan(listOf("Ao vivo")).albums.single { it.sources.isNotEmpty() }.kind)
    }

    @Test fun ambiguousGenreAliasRemainsTextAndDoesNotInventAnElectronicRole() {
        assertEquals(null, resolver.resolve("música electrónica").dictionaryId)
        assertEquals(AlbumKind.ORDINARY, scan(listOf("En directo", "música electrónica")).albums.single { it.sources.isNotEmpty() }.kind)
    }
}
