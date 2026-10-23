// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import com.google.flatbuffers.FlatBufferBuilder
import link.roadio.cuetree.fbs.Header
import link.roadio.cuetree.fbs.Mdia
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CompactFormatTest {
    @Test fun optionalScalarPresencePreservesZeroWithoutBooleanFields() {
        val life = Life(0)
        val root = Node(NodeKind.DIRECTORY, MediaId(0), source = SourceInfo("root", provider = "posix", locator = "/music"), life = life)
        val zero = Node(NodeKind.MEDIA, MediaId(1, 0), MediaId(0), SourceInfo("zero.wav", 0, 0), life = life)
        val absent = Node(NodeKind.MEDIA, MediaId(2), MediaId(0), SourceInfo("absent.wav"), life = life)
        val album = Album(0, AlbumKind.SINGLES, "[[SINGLES]]", listOf(zero.mediaId!!, absent.mediaId!!), listOf(1, 2), emptyMap(), life)
        val genres = listOf(Genre(GenreRef(dictionaryId = 0), "Zero", listOf(0), life, id = 1), Genre(GenreRef(text = "other"), "Other", listOf(0), life, id = 2))
        val snapshot = Snapshot(0, "test.zero", listOf(root, zero, absent), albums = listOf(album), genres = genres)
        val flat = FlatCatalogs.encode(snapshot)
        assertEquals(snapshot.withAttributes(snapshot, Long.MAX_VALUE).ordered(), flat.snapshot())
        val media = requireNotNull(flat.mdia().nodes(1))
        assertTrue(media.id()!!.hasLo())
        assertEquals(0L, media.id()!!.lo())
        assertTrue(media.source()!!.hasModified())
        assertEquals(0L, media.source()!!.modified())
        assertFalse(
            flat
                .mdia()
                .nodes(2)!!
                .id()!!
                .hasLo(),
        )
        assertFalse(
            flat
                .mdia()
                .nodes(2)!!
                .source()!!
                .hasModified(),
        )
        assertTrue(flat.gnrv().genres(0)!!.hasDictionaryId())
        assertEquals(0L, flat.gnrv().genres(0)!!.dictionaryId())
        assertFalse(flat.gnrv().genres(1)!!.hasDictionaryId())
        assertEquals(AlbumKind.SINGLES.ordinal, flat.albm().albums(0)!!.flags() ushr Flags.KIND_SHIFT)
    }

    @Test fun previousFormatVersionIsRejectedWithoutCompatibilityReader() {
        val snapshot = Snapshot.empty("test.version")
        val flat = FlatCatalogs.encode(snapshot)
        val b = FlatBufferBuilder()
        val header = Header.createHeader(b, FlatCatalogs.FORMAT_VERSION - 1, 0, b.createString(snapshot.identityScheme), 0)
        val empty = Mdia.createNodesVector(b, intArrayOf())
        b.finish(Mdia.createMdia(b, header, empty, empty, empty), "MDIA")
        val files = FlatCatalogs.names.associateWith { flat.bytes(it) }.toMutableMap()
        files["MDIA"] = b.sizedByteArray()
        val error = assertThrows(IOException::class.java) { FlatCatalogs.read(files) }
        assertEquals("Unsupported Cuetree format", error.cause!!.message)
    }
}
