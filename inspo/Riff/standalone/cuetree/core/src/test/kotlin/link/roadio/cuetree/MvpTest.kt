// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.MediaInput
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MvpTest {
    private class Identity : MediaIdGenerator {
        override val scheme = "test.mvp"
        var lows = 0
        var spanHighs = 0

        override fun high(
            input: MediaInput,
            span: Span?,
            cancellation: Cancellation,
        ) = span?.let {
            spanHighs++
            1000 + (it.beginUs ?: 0)
        } ?: 101L

        override fun low(
            input: MediaInput,
            span: Span?,
            cancellation: Cancellation,
        ): Long {
            cancellation.check()
            lows++
            return input.read(0, 1)[0].toLong()
        }
    }

    private fun chapters(count: Int = 2) = (0 until count).map { Section(null, Span(it * 10L, (it + 1) * 10L), mapOf("title" to listOf("Chapter ${it + 1}"))) }

    private val embedded =
        MetadataReader { input, _, _ ->
            Observation(mapOf("comment" to listOf(input.id)), if (input.id == "image.mp3") listOf(TableObservation("toc", emptyMap(), chapters())) else emptyList(), emptyList())
        }

    @Test fun fragmentKeepsItsMetadataAndSpanAcrossAlternativeSourceAndOriginalExpiry() {
        val p = MemoryProvider()
        val original = p.directory("original")
        p.file("image.mp3", byteArrayOf(7), original)
        val copy = p.file("copy.mp3", byteArrayOf(7))
        val identity = Identity()
        val reader = MetadataReader { _, _, _ -> Observation(mapOf("comment" to listOf("image.mp3")), listOf(TableObservation("toc", emptyMap(), chapters())), emptyList()) }
        val indexer = Indexer(identity, 10, listOf(p), reader)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        val initialLows = identity.lows
        val chapter = first.nodes.indexOfFirst { it.kind == NodeKind.FRAGMENT && it.span!!.beginUs == 10L }
        val fragment = first.nodes[chapter]
        assertEquals(MediaId(101, 7), fragment.parentId)
        assertEquals("image.mp3", fragment.attributes.first("comment"))
        p.denied += copy
        val resolved = SourceResolver(listOf(p)).resolve(first, chapter)
        assertEquals(Span(10, 20), resolved.span)
        resolved.open().use { assertEquals("image.mp3", it.id) }
        p.denied.clear()
        p.denied += original
        val lost = indexer.scan(listOf(p.rootSpec()), first, 5)
        val expired = indexer.scan(listOf(p.rootSpec()), FlatCatalogs.encode(lost).snapshot(), 11)
        assertTrue(expired.nodes.none { it.name == "original" || it.name == "image.mp3" })
        val surviving = expired.nodes.single { it.mediaId == fragment.mediaId }
        assertTrue(surviving.life.available)
        assertEquals(fragment.attributes, surviving.attributes)
        assertEquals(fragment.span, surviving.span)
        assertEquals("copy.mp3", expired.nodes[requireNotNull(expired.parentOf(expired.nodes.indexOf(surviving)))].name)
        assertEquals(initialLows, identity.lows)
        assertEquals(expired.copy(diagnostics = emptyList()), FlatCatalogs.encode(expired).snapshot())
    }

    @Test fun fragmentCannotBorrowSourceWithDifferentLow() {
        val p = MemoryProvider()
        val original = p.file("image.mp3", byteArrayOf(7))
        p.file("copy.mp3", byteArrayOf(9))
        val indexer = Indexer(Identity(), 10, listOf(p), embedded)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        p.denied += original
        val lost = indexer.scan(listOf(p.rootSpec()), first, 2)
        val fragments = lost.nodes.filter { it.kind == NodeKind.FRAGMENT }
        assertEquals(2, fragments.size)
        assertTrue(fragments.all { !it.life.available && it.parentId == MediaId(101, 7) && it.life.generation == 1L })
        assertThrows(IOException::class.java) { SourceResolver(listOf(p)).resolve(lost, lost.nodes.indexOf(fragments.first())) }
    }

    @Test fun removedChapterDoesNotReviveAndEmbeddedOwnerIsReobserved() {
        val p = MemoryProvider()
        val image = p.file("image.mp3", byteArrayOf(7))
        p.file("copy.mp3", byteArrayOf(7))
        var sections = chapters(3)
        val reader =
            MetadataReader { input, _, _ ->
                Observation(emptyMap(), if (input.id == "image.mp3") listOf(TableObservation("toc", emptyMap(), sections)) else emptyList(), emptyList())
            }
        val indexer = Indexer(Identity(), 10, listOf(p), reader)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        sections = chapters(2)
        p.items.getValue(image).modified++
        val changed = indexer.scan(listOf(p.rootSpec()), first, 2)
        val removed = changed.nodes.single { it.span?.beginUs == 20L }
        assertFalse(removed.life.available)
        assertEquals(1L, removed.life.generation)
        val again = indexer.scan(listOf(p.rootSpec()), FlatCatalogs.encode(changed).snapshot(), 3)
        assertFalse(
            again.nodes
                .single { it.span?.beginUs == 20L }
                .life.available,
        )
        val expired = indexer.scan(listOf(p.rootSpec()), again, 11)
        assertEquals(
            listOf("1", "2"),
            expired.albums
                .single { it.kind == AlbumKind.EXPLICIT && it.sources.isNotEmpty() }
                .sources
                .map { expired.nodes[it].attributes.first("fragment") },
        )
        assertTrue(expired.nodes.filter { it.kind == NodeKind.FRAGMENT }.all { it.attributes.first("totalFragments") == "2" })
    }

    @Test fun externalCueBecomesReadyBeforeUnrelatedFolderIsScanned() {
        val p = MemoryProvider()
        val album = p.directory("A Album")
        p.file("disc.cue", parent = album)
        p.file("image.mp3", parent = album)
        val unrelated = p.directory("Z Unrelated")
        p.file("later.mp3", parent = unrelated)
        val reader =
            MetadataReader { _, hint, _ ->
                Observation(emptyMap(), if (hint == "cue") listOf(TableObservation("cue", emptyMap(), chapters().map { it.copy(reference = "image.mp3") })) else emptyList(), emptyList())
            }
        val events = mutableListOf<ScanProgress>()
        val identity = Identity()
        var early: Snapshot? = null
        var later: Snapshot? = null
        val result =
            Indexer(identity, 10, listOf(p), reader).scan(
                listOf(p.rootSpec()),
                generation = 1,
                onCheckpoint = { progress, offer ->
                    events += progress
                    if (progress.kind == ScanCheckpointKind.EXPLICIT_TABLE && early == null) early = offer()
                    if (progress.kind == ScanCheckpointKind.MEDIA && early != null && later == null) later = offer()
                    false
                },
            )
        val checkpoint = requireNotNull(early)
        assertEquals(2, checkpoint.nodes.count { it.kind == NodeKind.FRAGMENT })
        assertTrue(checkpoint.nodes.none { it.name == "later.mp3" })
        assertEquals(2, result.nodes.count { it.kind == NodeKind.FRAGMENT })
        assertEquals(2, requireNotNull(later).nodes.count { it.kind == NodeKind.FRAGMENT })
        assertEquals(2, identity.spanHighs)
        assertEquals(2L, events.last().completedMedia)
        assertEquals(3L, events.last().completedDirectories)
        assertEquals(1L, events.last().completedCueSheets)
    }

    @Test fun failedCueReadIsTerminalUntilSourceStatChanges() {
        val p = MemoryProvider()
        p.file("image.mp3")
        val cue = p.file("disc.cue")
        var fail = false
        var cueReads = 0
        val reader =
            MetadataReader { _, hint, _ ->
                if (hint == "cue") {
                    cueReads++
                    if (fail) throw IOException("CUE read failed")
                }
                Observation(emptyMap(), if (hint == "cue") listOf(TableObservation("cue", emptyMap(), chapters().map { it.copy(reference = "image.mp3") })) else emptyList(), emptyList())
            }
        val indexer = Indexer(Identity(), 10, listOf(p), reader)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        fail = true
        p.items.getValue(cue).modified++
        val failed = indexer.scan(listOf(p.rootSpec()), first, 2)
        assertEquals(NodeKind.FAILEDL1, failed.nodes.single { it.name == "disc.cue" }.kind)
        assertEquals(2, cueReads)
        fail = false
        val skipped = indexer.scan(listOf(p.rootSpec()), failed, 3)
        assertEquals(2, cueReads)
        assertEquals(NodeKind.FAILEDL1, skipped.nodes.single { it.name == "disc.cue" }.kind)
        p.items.getValue(cue).modified++
        val recovered = indexer.scan(listOf(p.rootSpec()), skipped, 4)
        assertEquals(3, cueReads)
        assertEquals(NodeKind.METADATA, recovered.nodes.single { it.name == "disc.cue" }.kind)
    }

    @Test fun typedAttributesInternByKindAndValueAndPreserveNumericZeroAndExtensions() {
        val p = MemoryProvider()
        p.file("a.mp3")
        p.file("b.mp3")
        val attrs = mapOf("title" to listOf("Same"), "artist" to listOf("Same"), "year" to listOf("0"), "trackNumber" to listOf("01/02"), "custom:ключ" to listOf("x:y", ""))
        val snapshot = Indexer(MockMediaID(), 10, listOf(p), metadataByName(mapOf("a.mp3" to attrs, "b.mp3" to attrs))).scan(listOf(p.rootSpec()), generation = 1)
        val flat = FlatCatalogs.encode(snapshot)
        val title = snapshot.attributeCatalog.single { it.value.kind == AttributeKind.TITLE }
        val artist = snapshot.attributeCatalog.single { it.value.kind == AttributeKind.ARTIST }
        val year = snapshot.attributeCatalog.single { it.value.kind == AttributeKind.YEAR }
        assertNotEquals(title.id, artist.id)
        assertEquals(0L, year.value.number)
        assertTrue(flat.attribute(year.id)!!.hasValueLong())
        assertEquals(0L, flat.attribute(year.id)!!.valueLong())
        assertEquals(snapshot, flat.snapshot())
        assertTrue(snapshot.nodes.filter { it.kind == NodeKind.MEDIA }.all { it.attributes.first("trackNumber") == "1" && it.attributes.first("trackTotal") == "2" && it.attributes["custom:ключ"] == listOf("x:y", "") })
        assertThrows(IllegalArgumentException::class.java) { FlatCatalogs.encode(snapshot.copy(attributeCatalog = snapshot.attributeCatalog.filter { it.id != title.id })) }
    }

    @Test fun sortedCommonIdsRemapOccurrencesAndKeepTrackOrderAndNullableLowDistinct() {
        val life = Life(0)
        val root = Node(NodeKind.DIRECTORY, MediaId(0), source = SourceInfo("root", provider = "posix", locator = "/music"), life = life)
        val media = listOf(MediaId(Long.MAX_VALUE), MediaId(0, 0), MediaId(Long.MIN_VALUE), MediaId(0))
        val nodes = listOf(root) + media.mapIndexed { index, id -> Node(NodeKind.MEDIA, id, MediaId(0), SourceInfo("$index.mp3"), life = life) }
        val album = Album(Long.MAX_VALUE, AlbumKind.SINGLES, "[[SINGLES]]", media, listOf(1, 2, 3, 4), emptyMap(), life)
        val input = Snapshot(0, "test.sorted", nodes, albums = listOf(album))
        val flat = FlatCatalogs.encode(input)
        val result = flat.snapshot()
        assertEquals(result.nodes.map { it.id }.sortedWith(IdOrder), result.nodes.map { it.id })
        assertEquals(
            media,
            result.albums
                .single()
                .sources
                .map { result.nodes[it].id },
        )
        assertEquals(
            listOf("0.mp3", "1.mp3", "2.mp3", "3.mp3"),
            result.albums
                .single()
                .sources
                .map { result.nodes[it].name },
        )
        for (id in media) assertEquals(result.sources(id), flat.sources(id))
        assertEquals(1, flat.sources(MediaId(0)).size)
        assertEquals(1, flat.sources(MediaId(0, 0)).size)
        assertNotNull(flat.album(MediaId(Long.MAX_VALUE)))
        assertNull(flat.album(MediaId(Long.MAX_VALUE, 0)))
        assertEquals(4, flat.childrenOf(flat.roots().first()).size)
    }

    @Test fun moveIsNewOccurrenceAndOldMissingLocationExpires() {
        val p = MemoryProvider()
        val file = p.file("before.mp3", byteArrayOf(7))
        val identity = Identity()
        val indexer = Indexer(identity, 10, listOf(p), emptyMetadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        p.items.remove(file)
        p.file("after.mp3", byteArrayOf(7))
        val moved = indexer.scan(listOf(p.rootSpec()), first, 2)
        assertEquals(2, moved.nodes.count { it.kind == NodeKind.MEDIA })
        assertFalse(
            moved.nodes
                .single { it.name == "before.mp3" }
                .life.available,
        )
        assertTrue(
            moved.nodes
                .single { it.name == "after.mp3" }
                .life.available,
        )
        val expired = indexer.scan(listOf(p.rootSpec()), moved, 11)
        assertEquals(listOf("after.mp3"), expired.nodes.filter { it.kind == NodeKind.MEDIA }.map { it.name })
    }

    @Test fun fragmentParentMustResolveToMediaNotDirectoryOrFragment() {
        val p = MemoryProvider()
        p.file("image.mp3", byteArrayOf(7))
        val first = Indexer(Identity(), 10, listOf(p), embedded).scan(listOf(p.rootSpec()), generation = 1)
        val root = first.nodes.single { it.isRoot }.id
        val fragment = first.nodes.first { it.kind == NodeKind.FRAGMENT }.id
        for (badParent in listOf(root, fragment, MediaId(99999), null)) {
            val bad = first.copy(nodes = first.nodes.map { if (it.kind == NodeKind.FRAGMENT) it.copy(parentId = badParent) else it }, albums = emptyList())
            assertThrows(IllegalArgumentException::class.java) { FlatCatalogs.encode(bad) }
        }
    }

    @Test fun contradictoryCatalogAvailabilityIsRejected() {
        val p = MemoryProvider()
        p.file("a.mp3")
        val first = Indexer(MockMediaID(), 10, listOf(p), metadataByName(mapOf("a.mp3" to mapOf("artist" to listOf("Artist"), "genre" to listOf("Rock"))))).scan(listOf(p.rootSpec()), generation = 1)
        assertThrows(IllegalArgumentException::class.java) { FlatCatalogs.encode(first.copy(artists = first.artists.map { it.copy(life = Life(1, Flags.ACSS)) })) }
        assertThrows(IllegalArgumentException::class.java) { FlatCatalogs.encode(first.copy(genres = first.genres.map { it.copy(life = Life(1, Flags.ACSS)) })) }
    }
}
