// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.random.Random

class AlbumV2Test {
    private val root = Node(NodeKind.DIRECTORY, MediaId(-100), source = SourceInfo("Music", provider = "memory", locator = "/"), life = Life(1))

    private fun folder(
        id: Long,
        name: String,
        parent: MediaId = root.id,
    ) = Node(NodeKind.DIRECTORY, MediaId(id), parent, SourceInfo(name), life = Life(1))

    private fun media(
        id: Long,
        parent: MediaId = root.id,
        vararg fields: Pair<String, String>,
    ) = Node(NodeKind.MEDIA, MediaId(id, 0), parent, SourceInfo("$id.flac"), attributes = fields.associate { it.first to listOf(it.second) }, life = Life(1))

    private fun group(
        nodes: List<Node>,
        seed: Int = 0,
    ): List<OrdinaryAlbumGroup> {
        val snapshot = Snapshot(1, "v2-test", nodes)
        val sources = nodes.indices.filter { nodes[it].baseIdentity }.shuffled(Random(seed))
        return DefaultAlbumGroupingPolicy.group(AlbumGroupingInput(snapshot, sources, Cancellation(), testVocabulary))
    }

    @Test fun scatteredPartialCompilationAndUnknownYearFormOneAlbum() {
        val dirs = (1L..7L).map { folder(-it, "Artist $it") }
        val tracks = dirs.mapIndexed { i, d -> media(100L + i, d.id, "album" to "Breakbeat Kaos", "artist" to "A$i", "trackNumber" to "" + (i + 1), "trackTotal" to if (i < 5) "16" else "18", "discNumber" to if (i < 5) "1" else "2", "discTotal" to "2", "date" to if (i == 6) "" else "2003") }
        val nodes = listOf(root) + dirs + tracks
        repeat(20) { assertEquals(7, group(nodes, it).single().sources.size) }
    }

    @Test fun unnumberedSoundtrackAbsorbsItsArtistSubgroupAcrossFolders() {
        val dirs = (1L..10L).map { folder(-it, "Artist $it") }
        val tracks = (0 until 12).map { i -> media(100L + i, dirs[i % 10].id, "album" to "Lost In Translation-OST", "artist" to if (i < 3) "Kevin Shields" else "A$i", "year" to "2003") }
        assertEquals(12, group(listOf(root) + dirs + tracks).single().sources.size)
    }

    @Test fun differentGreatestHitsArtistsStaySeparate() {
        val tracks = (0 until 5).flatMap { a -> (1..3).map { n -> media(100L + a * 10 + n, root.id, "album" to "Greatest Hits", "artist" to "Artist $a", "albumArtist" to "Artist $a", "trackNumber" to "$n") } }
        assertEquals(5, group(listOf(root) + tracks).size)
    }

    @Test fun knownYearGroupsAreNotBridgedByMissingYear() {
        val dirs = (1L..3L).map { folder(-it, "Folder $it") }
        val tracks =
            (0..1).flatMap { edition -> (1..2).map { n -> media(100L + edition * 10 + n, dirs[edition].id, "album" to "Compilation", "artist" to "A$n", "year" to "" + (2000 + edition)) } } +
                media(200, dirs[2].id, "album" to "Compilation")
        val result = group(listOf(root) + dirs + tracks)
        assertEquals(listOf(2, 2), result.map { it.sources.size }.sorted())
    }

    @Test fun mixedDiscSpellingsAndAlbumSuffixesAreCorroborated() {
        val dirs = listOf(folder(-1, "CD1"), folder(-2, "CD-2"), folder(-3, "[Disc 03]"), folder(-4, "Диск 4"))
        val tracks = dirs.mapIndexed { i, d -> media(100L + i, d.id, "album" to "Collection CD" + (i + 1), "artist" to "Band", "trackNumber" to "1", "discTotal" to "4") }
        val result = group(listOf(root) + dirs + tracks).single()
        assertEquals("Collection", result.title)
        assertEquals(tracks.map { it.id }, result.sources.map { (listOf(root) + dirs + tracks)[it].id })
        assertTrue(tracks.all { "discNumber" !in it.attributes })
    }

    @Test fun cdTrackCountAndMeaningfulDifferentAlbumTitlesDoNotNormalize() {
        assertNull(DiscFamilies.parse("CD 3T (CDMUTE251)", testVocabulary))
        assertNull(DiscFamilies.parse("myCD1", testVocabulary))
        val dirs = listOf(folder(-1, "CD1"), folder(-2, "CD2"))
        val tracks = listOf(media(100, dirs[0].id, "album" to "Original CD1", "artist" to "Band"), media(101, dirs[1].id, "album" to "Demos CD2", "artist" to "Band"))
        assertEquals(2, group(listOf(root) + dirs + tracks).size)
    }

    @Test fun rejectedEmbeddedDiscMarkersCannotReturnThroughNumericFallback() {
        val dirs = listOf(folder(-1, "myCD1"), folder(-2, "myCD2"))
        val tracks =
            dirs.mapIndexed { i, d ->
                media(100L + i, d.id, "album" to "Collection CD" + (i + 1), "trackNumber" to "1")
            }
        val nodes = listOf(root) + dirs + tracks
        val evidence = AlbumEvidence(Snapshot(1, "v2", nodes), tracks.indices.map { it + 3 }, Cancellation(), testVocabulary)
        assertNull(evidence.families.ordinal(3))
        assertNull(evidence.families.ordinal(4))
        assertEquals(2, group(nodes).size)
    }

    @Test fun oneBadSlotLeavesCoherentRemainderInsteadOfSingletonAlbums() {
        val tracks = (1..20).map { n -> media(100L + n, root.id, "album" to "Trip", "artist" to "A$n", "year" to "2016", "trackNumber" to if (n == 20) "3" else "$n") }
        repeat(10) { assertEquals(18, group(listOf(root) + tracks, it).single().sources.size) }
    }

    @Test fun independentLiveConcertsRemainSeparateAboveResidualThreshold() {
        val dirs = listOf(folder(-1, "Fox"), folder(-2, "Cervantes"))
        val tracks = dirs.mapIndexed { i, d -> media(100L + i, d.id, "album" to "Live Album", "albumArtist" to "Pretty Lights", "artist" to "Pretty Lights", "year" to "2008", "trackNumber" to "1", "trackTotal" to "1", "title" to "Show $i") }
        assertEquals(2, group(listOf(root) + dirs + tracks).size)
    }

    @Test fun unknownDiscDoesNotTurnOverlappingEditionsIntoDifferentDiscs() {
        val dirs = listOf(folder(-1, "Original"), folder(-2, "Reissue"))
        val tracks = dirs.flatMapIndexed { i, d -> (1..4).map { n -> media(100L + i * 10 + n, d.id, "album" to "Release", "artist" to "Band", "trackNumber" to "$n", "trackTotal" to "4", "discNumber" to if (i == 0) "1" else "") } }
        assertEquals(listOf(4, 4), group(listOf(root) + dirs + tracks).map { it.sources.size }.sorted())
    }

    @Test fun optionalDiscTotalContradictionDoesNotOverrideIndependentEvidence() {
        val tracks = (1..17).map { n -> media(100L + n, root.id, "album" to "Tribute", "genre" to "Rock", "artist" to "A$n", "genre" to "Rock", "year" to "2006", "trackNumber" to "$n", "trackTotal" to "17", "discNumber" to "1", "discTotal" to if (n == 17) "1" else "2") }
        assertEquals(17, group(listOf(root) + tracks).single().sources.size)
    }

    @Test fun placeholderArtistDoesNotSupplyGlobalGroupingProof() {
        val dirs = listOf(folder(-1, "A"), folder(-2, "B"))
        val tracks = dirs.mapIndexed { i, d -> media(100L + i, d.id, "album" to "Album", "artist" to "Artist", "trackNumber" to "1") }
        assertTrue(group(listOf(root) + dirs + tracks).isEmpty())
    }

    @Test fun equivalentCopiesCollapseButDifferentDeclaredPositionsSurvive() {
        val first = media(100, root.id, "album" to "Positions", "artist" to "Band", "title" to "Song", "trackNumber" to "1")
        val copy = first.copy(source = SourceInfo("copy.flac"))
        val repeat = first.copy(source = SourceInfo("repeat.flac"), attributes = first.attributes + ("trackNumber" to listOf("2")))
        val nodes = listOf(root, first, copy, repeat)
        val built = CatalogBuilder(RandomIds, GenreResolver { GenreRef(text = it) }, DefaultAlbumGroupingPolicy, 100, Cancellation()).build(Snapshot(1, "v2", nodes), Snapshot.empty("v2"))
        val album = built.albums.single { it.kind == AlbumKind.ORDINARY }
        assertEquals(listOf(first.id, first.id), album.tracks)
        assertTrue(
            built.albums
                .single { it.kind == AlbumKind.SINGLES }
                .sources
                .isEmpty(),
        )
        assertEquals(built.ordered().withCompleteAttributeCatalog(), FlatCatalogs.encode(built).snapshot())
    }

    @Test fun differentIdsAreNeverDeletedBecauseTagsMatch() {
        val tracks = (1..4).map { n -> media(100L + n, root.id, "album" to "Variants", "artist" to "Band", "title" to "Song", "trackNumber" to "1") }
        assertEquals(4, group(listOf(root) + tracks).single().sources.size)
    }

    @Test fun largeStructuralCandidateIsLinearAndCancellationIsObserved() {
        val tracks = (1..125001).map { n -> media(100L + n, root.id, "album" to "Large", "trackNumber" to "$n", "trackTotal" to "125001") }
        assertEquals(125001, group(listOf(root) + tracks).single().sources.size)
        val snapshot = Snapshot(1, "large", listOf(root) + tracks)
        var checks = 0
        assertThrows(CancellationException::class.java) {
            DefaultAlbumGroupingPolicy.group(AlbumGroupingInput(snapshot, tracks.indices.map { it + 1 }, Cancellation { ++checks > 100 }))
        }
    }

    @Test fun largeResidualFolderHasBoundedWorkingMemory() {
        val tracks = (1..10000).map { n -> media(100L + n, root.id, "album" to "Folder", "title" to "Song $n") }
        assertEquals(10000, group(listOf(root) + tracks).single().sources.size)
    }
}
