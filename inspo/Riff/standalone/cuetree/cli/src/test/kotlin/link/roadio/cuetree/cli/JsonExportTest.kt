// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree.cli

import link.roadio.cuetree.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class JsonExportTest {
    @Test fun schemaJsonRoundTripsAllRootsIncludingNullZeroLongLimitsAndUnicode() {
        val life = Life(Long.MAX_VALUE)
        val root = Node(NodeKind.DIRECTORY, MediaId(10), source = SourceInfo("Музыка\u0018\u001d\n\"\\", provider = "posix", locator = "/music"), life = life)
        val media = Node(NodeKind.MEDIA, MediaId(Long.MIN_VALUE, 0), root.id, SourceInfo("zero.flac", 0, 0), life = life, tables = listOf(TableObservation("chapters", mapOf("title" to listOf("Структура")), listOf(Section(null, Span(0, 10), mapOf("title" to listOf("Глава 1"))), Section(null, Span(10, 20), mapOf("title" to listOf("Глава 2")))))), sourceEvidence = mapOf("decoder" to listOf("opaque-evidence")))
        val other = Node(NodeKind.MEDIA, MediaId(Long.MAX_VALUE), root.id, SourceInfo("absent.flac"), life = life)
        val fragments =
            listOf(Span(), Span(0, 10)).mapIndexed { i, span ->
                Node(NodeKind.FRAGMENT, MediaId(20L + i, 0), media.id, span = span, attributes = mapOf("title" to listOf("Глава $i"), "fragment" to listOf("${i + 1}"), "totalFragments" to listOf("2")), life = life)
            }
        val album = Album(30, AlbumKind.EXPLICIT, "Альбом", fragments.map { it.id }, listOf(3, 4), emptyMap(), life)
        val artist = Artist(40, "artist", "Исполнитель", listOf(30), life)
        val genre = Genre(GenreRef(dictionaryId = 0), "Жанр", listOf(30), life, 50)
        val flat = FlatCatalogs.encode(Snapshot(Long.MAX_VALUE, "test.json", listOf(root, media, other) + fragments, listOf(album), listOf(artist), listOf(genre), metadataRevision = "metadata:révision"))
        val exporter = JsonExport().also { it.checkVersion() }
        val json = exporter.convert(flat)
        assertTrue(json.getValue("MDIA").contains(Long.MIN_VALUE.toString()))
        assertTrue(json.getValue("MDIA").contains(Long.MAX_VALUE.toString()))
        assertTrue(json.getValue("ALBM").contains("Альбом"))
        assertTrue(json.getValue("MDIA").contains("\\u0018"))
        assertTrue(json.getValue("MDIA").contains("\\u001d"))
        val temp = Files.createTempDirectory("cuetree-json-roundtrip-")
        try {
            for (name in listOf("common") + FlatCatalogs.names.map { it.lowercase(Locale.ROOT) }) {
                javaClass.getResourceAsStream("/cuetree/fbs/$name.fbs")!!.use { Files.copy(it, temp.resolve("$name.fbs")) }
            }
            val rebuilt =
                FlatCatalogs.names.associateWith { name ->
                    val file = Files.writeString(temp.resolve("$name.json"), json.getValue(name))
                    val process = ProcessBuilder(Flatc.executable(), "--binary", "--strict-json", "-o", temp.toString(), temp.resolve("${name.lowercase(Locale.ROOT)}.fbs").toString(), file.toString()).redirectErrorStream(true).start()
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    assertEquals(output, 0, process.waitFor())
                    Files.readAllBytes(temp.resolve("$name.bin"))
                }
            assertEquals(flat.snapshot(), FlatCatalogs.read(rebuilt).snapshot())
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test fun cliJsonIsOptionalAndLivesBesideTheSamePublishedBuffers() {
        val base = Files.createTempDirectory("cuetree cli ")
        try {
            val music = Files.createDirectory(base.resolve("music root"))
            Files.write(music.resolve("test.wav"), byteArrayOf(1, 2, 3))
            val store = base.resolve("catalog")
            main(arrayOf(store.toString(), music.toString()))
            val first = cohort(store)
            assertTrue(FlatCatalogs.names.all { !Files.exists(first.resolve("$it.json")) })
            main(arrayOf(store.toString(), music.toString()))
            assertEquals(first, cohort(store))
            assertFalse(Files.exists(store.resolve("PREVIOUS")))
            main(arrayOf("--json", store.toString(), music.toString()))
            val second = cohort(store)
            assertNotEquals(first, second)
            val flat = SnapshotStore(store).readFlat()!!
            val expected = JsonExport().convert(flat)
            for (name in FlatCatalogs.names) {
                assertEquals(expected.getValue(name), Files.readString(second.resolve("$name.json")))
                assertArrayEquals(flat.bytes(name), Files.readAllBytes(second.resolve(name)))
            }
            main(arrayOf(store.toString(), music.toString()))
            assertEquals(second, cohort(store))
            val previous = Files.readString(store.resolve("PREVIOUS"))
            main(arrayOf("--json", store.toString(), music.toString()))
            assertEquals(second, cohort(store))
            assertEquals(previous, Files.readString(store.resolve("PREVIOUS")))
            Files.write(music.resolve("another.wav"), byteArrayOf(4, 5, 6))
            main(arrayOf(store.toString(), music.toString()))
            assertNotEquals(second, cohort(store))
            assertTrue(FlatCatalogs.names.all { !Files.exists(cohort(store).resolve("$it.json")) })
            assertTrue(FlatCatalogs.names.all { Files.exists(second.resolve("$it.json")) })
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test fun converterFailureOrIncompleteOutputDoesNotPublishANewCohort() {
        val base = Files.createTempDirectory("cuetree-json-failure-")
        try {
            val store = SnapshotStore(base)
            val first = Snapshot.empty("test.failure")
            store.write(first)
            val before = Files.readString(base.resolve("CURRENT"))
            val missing = JsonExport(base.resolve("missing-flatc").toString())
            assertThrows(IOException::class.java) { store.write(first.copy(generation = 1), json = missing::convert) }
            assertEquals(before, Files.readString(base.resolve("CURRENT")))
            assertThrows(IllegalArgumentException::class.java) { store.write(first.copy(generation = 1), json = { emptyMap() }) }
            assertEquals(before, Files.readString(base.resolve("CURRENT")))
            assertEquals(first, store.read())
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test fun cliResumesWhenThePreviousGenerationIsAheadOfTheHostClock() {
        val base = Files.createTempDirectory("cuetree-clock-regression-")
        try {
            val music = Files.createDirectory(base.resolve("music"))
            Files.write(music.resolve("test.wav"), byteArrayOf(1, 2, 3))
            val directory = base.resolve("catalog")
            main(arrayOf(directory.toString(), music.toString()))
            val store = SnapshotStore(directory)
            val future = System.currentTimeMillis() + 3_600_000L
            store.write(store.read()!!.copy(generation = future))
            main(arrayOf("--json", directory.toString(), music.toString()))
            assertEquals(future + 1, store.read()!!.generation)
            assertTrue(FlatCatalogs.names.all { Files.exists(cohort(directory).resolve("$it.json")) })
            val before = Files.readString(directory.resolve("CURRENT"))
            store.write(store.read()!!.copy(generation = Long.MAX_VALUE))
            val exhausted = Files.readString(directory.resolve("CURRENT"))
            assertNotEquals(before, exhausted)
            assertThrows(ArithmeticException::class.java) { main(arrayOf(directory.toString(), music.toString())) }
            assertEquals(exhausted, Files.readString(directory.resolve("CURRENT")))
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    private fun cohort(store: Path): Path = store.resolve(Files.readString(store.resolve("CURRENT")).trim())
}
