// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class BoundaryTest {
    @Test fun failedFirstReadDoesNotPublishMediaWithoutIdentity() {
        val p = MemoryProvider()
        val f = p.file("bad.mp3")
        p.denied += f
        val snapshot = Indexer(MockMediaID(), 1000, listOf(p), emptyMetadata).scan(listOf(p.rootSpec()), generation = 1)
        assertEquals(1, snapshot.nodes.size)
        validate(snapshot)
    }

    @Test fun metadataReadFailureRetainsOldAttributes() {
        val p = MemoryProvider()
        p.file("a.mp3")
        var fail = false
        val reader =
            MetadataReader { _, _, _ ->
                if (fail) throw java.io.IOException("Unavailable")
                Observation(mapOf("title" to listOf("Before")), emptyList(), emptyList())
            }
        val indexer = Indexer(MockMediaID(), 1000, listOf(p), reader)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        fail = true
        val next = indexer.scan(listOf(p.rootSpec()), first, 2, ScanOptions(forceRefresh = true))
        assertEquals(
            "Before",
            next.nodes
                .single { it.name == "a.mp3" && it.kind == NodeKind.FAILEDL1 }
                .attributes
                .first("title"),
        )
        assertFalse(
            next.nodes
                .single { it.name == "a.mp3" && it.kind == NodeKind.FAILEDL1 }
                .playable,
        )
    }

    @Test fun fileDirectoryReplacementRemovesIncompatibleOldSubtree() {
        val p = MemoryProvider()
        val dir = p.directory("name.mp3")
        val file = p.file("child.mp3", parent = dir)
        val indexer = Indexer(MockMediaID(), 1000, listOf(p), emptyMetadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        p.items.remove(file)
        p.items.getValue(dir).bytes = byteArrayOf(1, 2)
        val second = indexer.scan(listOf(p.rootSpec()), first, 2)
        assertEquals(2, second.nodes.size)
        assertEquals(NodeKind.MEDIA, second.nodes.single { it.name == "name.mp3" }.kind)
    }

    @Test fun discFolderNumberSuppliesStructureWithoutInventingDiscTag() {
        val p = MemoryProvider()
        val nine = p.directory("Set CD9")
        val ten = p.directory("Set CD10")
        p.file("a.mp3", parent = nine)
        p.file("b.mp3", parent = ten)
        val attrs = mapOf("album" to listOf("Set"), "trackNumber" to listOf("1/1"))
        val result =
            Indexer(MockMediaID(), 1000, listOf(p), metadataByName(mapOf("a.mp3" to attrs, "b.mp3" to attrs)))
                .scan(listOf(p.rootSpec()), generation = 1)
        val album = result.albums.single { it.kind == AlbumKind.ORDINARY }
        assertEquals(listOf("a.mp3", "b.mp3"), album.sources.map { result.nodes[it].name })
        assertTrue(album.sources.all { "discNumber" !in result.nodes[it].attributes })
    }

    @Test fun id3OrderedTocDefinesAlbumThroughCuegrain() {
        fun frame(
            id: String,
            payload: ByteArray,
        ): ByteArray = id.toByteArray() + ByteBuffer.allocate(4).putInt(payload.size).array() + byteArrayOf(0, 0) + payload

        fun chapter(
            id: String,
            start: Int,
            end: Int,
        ) = frame(
            "CHAP",
            id.toByteArray() + byteArrayOf(0) +
                ByteBuffer
                    .allocate(16)
                    .putInt(start)
                    .putInt(end)
                    .putInt(-1)
                    .putInt(-1)
                    .array(),
        )
        val body =
            chapter("one", 0, 1000) + chapter("two", 1000, 2000) +
                frame("CTOC", "root".toByteArray() + byteArrayOf(0, 3, 2) + "one\u0000two\u0000".toByteArray())
        val size = ByteArray(4) { i -> ((body.size ushr ((3 - i) * 7)) and 127).toByte() }
        val bytes = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + size + body + ByteArray(8)
        val p = MemoryProvider()
        p.file("chapters.mp3", bytes)
        val result = Indexer(MockMediaID(), 1000, listOf(p)).scan(listOf(p.rootSpec()), generation = 1)
        assertEquals(2, result.nodes.count { it.kind == NodeKind.FRAGMENT })
        val album = result.albums.single { it.kind == AlbumKind.EXPLICIT }
        assertEquals(listOf(0L, 1_000_000L), album.sources.map { result.nodes[it].span!!.beginUs })
        assertEquals(result.copy(diagnostics = emptyList()), FlatCatalogs.encode(result).snapshot())
    }

    @Test fun standaloneAndFragmentMayShareFullIdentity() {
        val p = MemoryProvider()
        p.file("image.mp3")
        p.file("single.mp3")
        val sections = listOf(Section(null, Span(0, 10), emptyMap()), Section(null, Span(10, 20), emptyMap()))
        val reader =
            MetadataReader { input, _, _ ->
                Observation(
                    emptyMap(),
                    if (input.id == "image.mp3") listOf(TableObservation("toc", emptyMap(), sections)) else emptyList(),
                    emptyList(),
                )
            }
        val identity =
            object : MediaIdGenerator {
                override val scheme = "test.shared.fragment"

                override fun high(
                    input: link.roadio.cuegrain.MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = if (span == null && input.id == "image.mp3") 999L else 123L

                override fun low(
                    input: link.roadio.cuegrain.MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = 0L
            }
        val result = Indexer(identity, 1000, listOf(p), reader).scan(listOf(p.rootSpec()), generation = 1)
        assertEquals(3, result.sources(MediaId(123, 0)).size)
        assertEquals(
            2,
            result.albums
                .single { it.kind == AlbumKind.EXPLICIT }
                .tracks.size,
        )
        assertEquals(
            1,
            result.albums
                .single { it.kind == AlbumKind.SINGLES }
                .tracks.size,
        )
    }

    @Test fun repeatedLossDoesNotExtendFragmentTtl() {
        val p = MemoryProvider()
        p.file("image.mp3")
        val sheet = p.file("tracks.cue", "FILE \"image.mp3\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nTRACK 02 AUDIO\nINDEX 01 00:01:00".toByteArray())
        val indexer = Indexer(MockMediaID(), 5, listOf(p))
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        p.denied += sheet
        val lost = indexer.scan(listOf(p.rootSpec()), first, 2)
        val again = indexer.scan(listOf(p.rootSpec()), lost, 3)
        assertTrue(again.nodes.filter { it.kind == NodeKind.FRAGMENT }.all { !it.life.available && it.life.generation == 1L })
        val expired = indexer.scan(listOf(p.rootSpec()), again, 6)
        assertTrue(expired.nodes.none { it.kind == NodeKind.FRAGMENT })
        assertTrue(expired.albums.single { it.kind == AlbumKind.EXPLICIT }.let { !it.life.available && it.sources.isEmpty() })
        assertEquals(
            1,
            expired.albums
                .single { it.kind == AlbumKind.SINGLES }
                .tracks.size,
        )
    }

    @Test fun unchangedFileSkipsMetadataAndBothHashes() {
        val p = MemoryProvider()
        p.file("a.mp3")
        var reads = 0
        val reader =
            MetadataReader { _, _, _ ->
                reads++
                Observation(emptyMap(), emptyList(), emptyList())
            }
        val identity = CountingIdentity()
        val indexer = Indexer(identity, 1000, listOf(p), reader)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        indexer.scan(listOf(p.rootSpec()), first, 2)
        assertEquals(1, reads)
        assertEquals(1, identity.highs)
        assertEquals(0, identity.lows)
    }
}
