package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuegrain.ByteArrayInput
import link.roadio.cuegrain.repair.MojibakeEncoding
import link.roadio.cuegrain.repair.MojibakeEncodingDetection
import link.roadio.cuegrain.repair.MojibakeEncodingDetector
import link.roadio.cuetree.Album
import link.roadio.cuetree.AlbumKind
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.CuegrainMetadataReader
import link.roadio.cuetree.FlatCatalogs
import link.roadio.cuetree.GenreRef
import link.roadio.cuetree.GenreResolver
import link.roadio.cuetree.Life
import link.roadio.cuetree.MediaId
import link.roadio.cuetree.Node
import link.roadio.cuetree.NodeKind
import link.roadio.cuetree.Observation
import link.roadio.cuetree.Section
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.SourceInfo
import link.roadio.cuetree.Span
import link.roadio.cuetree.TableObservation
import link.roadio.riff.indexer.level1.GenreMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CuetreeTagProjectionTest {
    private val known = mapOf("dance" to 1L, "edm" to 2L, "electronic" to 3L, "audiobook" to 106833L)
    private val matcher = GenreMatcher { known[it.lowercase()]?.let(::setOf).orEmpty() }
    private val detector =
        object : MojibakeEncodingDetector {
            override val revision = "TEST"

            override fun detect(input: ByteArray) = MojibakeEncodingDetection(MojibakeEncoding.OTHER, false, input.size)
        }
    private val projection = CuetreeTagProjection(matcher, detector)

    @Test fun blankTconDisappearsAndIndependentGenresFeedTextAndNumericAttributes() {
        val observation =
            Observation(
                attributes =
                    mapOf(
                        "title" to listOf("Song"),
                        "genre" to listOf(" ", "Dance; EDM; Electronic", " EDM "),
                        "artist" to listOf("First; Second|Third"),
                    ),
                tables = emptyList(),
                diagnostics = emptyList(),
            )
        val attributes = projection.project(observation).attributes
        assertEquals(listOf("Dance", "EDM", "Electronic"), attributes["genre"])
        assertEquals(listOf("1", "2", "3"), attributes["genreId"])
        assertEquals(listOf("First", "Second", "Third"), attributes["artist"])

        val rootId = MediaId(1)
        val trackId = MediaId(10)
        val snapshot =
            Snapshot(
                2,
                "test",
                listOf(
                    Node(NodeKind.DIRECTORY, rootId, source = SourceInfo("music", provider = "saf", locator = "music"), life = Life(2)),
                    Node(NodeKind.MEDIA, trackId, rootId, SourceInfo("track.mp3"), attributes = attributes, life = Life(2)),
                ),
                albums = listOf(Album(20, AlbumKind.ORDINARY, "Album", listOf(trackId), listOf(1), emptyMap(), Life(2))),
            )
        val resolver = GenreResolver { text -> known[text.lowercase()]?.let { GenreRef(dictionaryId = it) } ?: GenreRef(text = text) }
        val projected = CuetreePlayerProjection.build(snapshot, resolver) { false }
        assertFalse(
            projected.albums
                .single()
                .attributes
                .containsKey("automaticBook"),
        )
        FlatCatalogs.encode(projected).snapshot()
    }

    @Test fun id3TconFlowsThroughCuegrainBeforeRiffGenreParsing() {
        fun frame(value: String): ByteArray {
            val data = byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1)
            val size = ByteArray(4) { index -> (data.size ushr (24 - index * 8)).toByte() }
            return "TCON".toByteArray() + size + byteArrayOf(0, 0) + data
        }

        val body = frame(" ") + frame("Dance; EDM; Electronic")
        val size = ByteArray(4) { index -> ((body.size ushr (21 - index * 7)) and 127).toByte() }
        val bytes = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + size + body
        val observed = CuegrainMetadataReader().read(ByteArrayInput(bytes, "track.mp3"), "mp3", Cancellation())
        assertEquals(listOf(" ", "Dance; EDM; Electronic"), observed.attributes["genre"])
        val parsed = projection.project(observed)
        assertEquals(listOf("Dance", "EDM", "Electronic"), parsed.attributes["genre"])
        assertEquals(listOf("1", "2", "3"), parsed.attributes["genreId"])
    }

    @Test fun blankAndCompoundGenresAreParsedInTableAndFragmentMetadata() {
        val observation =
            Observation(
                attributes = mapOf("genre" to listOf(" ")),
                tables =
                    listOf(
                        TableObservation(
                            "cue:0",
                            mapOf("genre" to listOf("Dance; EDM")),
                            listOf(
                                Section(null, Span(0, null), mapOf("genre" to listOf(" "), "artist" to listOf("A; B"))),
                                Section(null, Span(1_000_000, null), mapOf("genre" to listOf("Electronic; Dance"))),
                            ),
                        ),
                    ),
                diagnostics = emptyList(),
            )
        val result = projection.project(observation)
        assertFalse("genre" in result.attributes)
        assertEquals(listOf("Dance", "EDM"), result.tables.single().attributes["genre"])
        assertEquals(listOf("1", "2"), result.tables.single().attributes["genreId"])
        assertFalse(
            "genre" in
                result.tables
                    .single()
                    .sections
                    .first()
                    .attributes,
        )
        assertEquals(
            listOf("A", "B"),
            result.tables
                .single()
                .sections
                .first()
                .attributes["artist"],
        )
        assertEquals(
            listOf("Electronic", "Dance"),
            result.tables
                .single()
                .sections
                .last()
                .attributes["genre"],
        )
    }
}
