package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.Album
import link.roadio.cuetree.AlbumKind
import link.roadio.cuetree.Artist
import link.roadio.cuetree.AttributeKind
import link.roadio.cuetree.Flags
import link.roadio.cuetree.FlatCatalogs
import link.roadio.cuetree.Genre
import link.roadio.cuetree.GenreRef
import link.roadio.cuetree.GenreResolver
import link.roadio.cuetree.Life
import link.roadio.cuetree.MediaId
import link.roadio.cuetree.Node
import link.roadio.cuetree.NodeKind
import link.roadio.cuetree.ROOT_IDENTITY_ATTRIBUTE
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.SourceInfo
import link.roadio.cuetree.Span
import link.roadio.riff.indexer.core.analysis.AnalysisGenre
import link.roadio.riff.indexer.core.analysis.AnalysisRecord
import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot
import link.roadio.riff.indexer.core.analysis.AnalysisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuetreePlayerProjectionTest {
    private val genres = GenreResolver { text -> if (text == "Book genre") GenreRef(dictionaryId = 106833) else GenreRef(text = text) }

    @Test fun ordinaryAlbumRetainsEqualAudioAtDifferentDeclaredPhysicalPositions() {
        val rootId = MediaId(1)
        val audio = MediaId(10, 0)
        val nodes =
            listOf(
                root(rootId, "music"),
                Node(
                    NodeKind.MEDIA,
                    audio,
                    rootId,
                    SourceInfo("first.flac"),
                    attributes = mapOf("trackNumber" to listOf("1")),
                    life = Life(2),
                ),
                Node(
                    NodeKind.MEDIA,
                    audio,
                    rootId,
                    SourceInfo("second.flac"),
                    attributes = mapOf("trackNumber" to listOf("2")),
                    life = Life(2),
                ),
            )
        val album = Album(20, AlbumKind.ORDINARY, "Positions", listOf(audio, audio), listOf(1, 2), emptyMap(), Life(2))
        val snapshot = Snapshot(2, "test", nodes, albums = listOf(album))
        val player = CuetreePlayerProjection.build(snapshot, genres) { false }
        assertEquals(listOf(audio, audio), player.albums.single().tracks)
        assertEquals(
            listOf("1", "2"),
            player.albums
                .single()
                .sources
                .map { player.nodes[it].attributes["trackNumber"]!!.single() },
        )
        assertEquals(player, FlatCatalogs.encode(player).snapshot())
    }

    @Test fun ordinaryTitleRetainsDeclaredRepeatedFragmentPositions() {
        val rootId = MediaId(1)
        val baseId = MediaId(10)
        val fragmentId = MediaId(11, 12)
        val snapshot =
            Snapshot(
                2,
                "test",
                listOf(
                    root(rootId, "music"),
                    Node(NodeKind.MEDIA, baseId, rootId, SourceInfo("book.m4b"), life = Life(2)),
                    Node(
                        NodeKind.FRAGMENT,
                        fragmentId,
                        baseId,
                        span = Span(0, 10),
                        attributes =
                            mapOf(
                                "title" to listOf("A"),
                                "fragment" to listOf("1"),
                                "totalFragments" to listOf("2"),
                            ),
                        life = Life(2),
                    ),
                    Node(
                        NodeKind.FRAGMENT,
                        fragmentId,
                        baseId,
                        span = Span(10, 20),
                        attributes =
                            mapOf(
                                "title" to listOf("B"),
                                "fragment" to listOf("2"),
                                "totalFragments" to listOf("2"),
                            ),
                        life = Life(2),
                    ),
                ),
                albums =
                    listOf(
                        Album(20, AlbumKind.ORDINARY, "Book", listOf(fragmentId, fragmentId), listOf(2, 3), emptyMap(), Life(2)),
                    ),
            )
        val projected = CuetreePlayerProjection.build(snapshot, genres) { false }
        assertEquals(listOf(fragmentId, fragmentId), projected.albums.single().tracks)
        val decoded = FlatCatalogs.encode(projected).snapshot()
        assertEquals(
            listOf("A", "B"),
            decoded.albums
                .single()
                .sources
                .map { decoded.nodes[it].name },
        )
    }

    @Test fun hiddenCueMetadataIsAbsentFromPlayerCohortAndSourcesAreRemapped() {
        val rootId = MediaId(1)
        val song = MediaId(10)
        val snapshot =
            Snapshot(
                2,
                "test",
                listOf(
                    root(rootId, "music"),
                    Node(NodeKind.METADATA, MediaId(2), rootId, SourceInfo("disc.cue"), life = Life(2)),
                    Node(
                        NodeKind.MEDIA,
                        song,
                        rootId,
                        SourceInfo("song.flac"),
                        life = Life(2),
                        sourceEvidence =
                            mapOf(
                                "riff.analysis.gainSource" to listOf("R128"),
                            ),
                    ),
                ),
                albums = listOf(Album(20, AlbumKind.ORDINARY, "Album", listOf(song), listOf(2), emptyMap(), Life(2))),
            )
        val player = CuetreePlayerProjection.build(snapshot, genres) { false }
        assertEquals(listOf(NodeKind.DIRECTORY, NodeKind.MEDIA), player.nodes.map { it.kind })
        assertEquals(listOf(1), player.albums.single().sources)
        assertEquals(
            2,
            FlatCatalogs
                .encode(player)
                .snapshot()
                .nodes.size,
        )
    }

    @Test fun temporaryLossOfBookMemberDoesNotReclassifyTheRetainedTitle() {
        val rootId = MediaId(1)
        val book = MediaId(10)
        val companion = MediaId(11)
        val snapshot =
            Snapshot(
                2,
                "test",
                listOf(
                    root(rootId, "music"),
                    Node(NodeKind.MEDIA, book, rootId, SourceInfo("part-one.m4b"), life = Life(1, Flags.ACSS)),
                    Node(NodeKind.MEDIA, companion, rootId, SourceInfo("part-two.m4a"), life = Life(2)),
                ),
                albums = listOf(Album(20, AlbumKind.ORDINARY, "Book", listOf(book, companion), listOf(1, 2), emptyMap(), Life(2))),
            )
        val projected = CuetreePlayerProjection.build(snapshot, genres) { false }
        assertEquals(listOf("1"), projected.albums.single().attributes["automaticBook"])
        assertEquals(listOf("1"), projected.nodes[1].attributes["automaticBook"])
        assertFalse(projected.nodes[1].life.available)
    }

    @Test fun unavailableVolumeIsRetainedInMdiaButAbsentFromReadyCatalogs() {
        val rootA = MediaId(1)
        val rootB = MediaId(2)
        val trackA = MediaId(10)
        val trackB = MediaId(11)
        val snapshot =
            Snapshot(
                2,
                "test",
                listOf(
                    root(rootA, "a"),
                    Node(NodeKind.MEDIA, trackA, rootA, SourceInfo("a.flac"), life = Life(2)),
                    root(rootB, "b", available = false),
                    Node(NodeKind.MEDIA, trackB, rootB, SourceInfo("b.flac"), life = Life(1, Flags.ACSS)),
                ),
                albums =
                    listOf(
                        Album(20, AlbumKind.ORDINARY, "A", listOf(trackA, trackB), listOf(1, 3), emptyMap(), Life(2)),
                        Album(21, AlbumKind.ORDINARY, "B", listOf(trackB), listOf(3), emptyMap(), Life(1, Flags.ACSS)),
                    ),
                artists = listOf(Artist(30, "author", "Author", listOf(20, 21), Life(2))),
                genres = listOf(Genre(GenreRef(text = "Other"), "Other", listOf(20, 21), Life(2), 40)),
            )
        val player = CuetreePlayerProjection.build(snapshot, genres) { false }
        assertEquals(4, player.nodes.size)
        assertFalse(player.nodes[2].life.available)
        assertEquals(listOf("A"), player.albums.map(Album::title))
        assertEquals(listOf(1, 3), player.albums.single().sources)
        assertEquals(listOf(trackA, trackB), player.albums.single().tracks)
        assertEquals(listOf(20L), player.artists.single().albums)
        assertEquals(listOf(20L), player.genres.single().albums)
        assertEquals(2, snapshot.albums.size)
        val decoded = FlatCatalogs.encode(player).snapshot()
        assertEquals(listOf("A"), decoded.albums.map(Album::title))
        assertEquals(4, decoded.nodes.size)
        assertEquals(
            "a",
            decoded.nodes
                .first()
                .attributes[ROOT_IDENTITY_ATTRIBUTE]
                ?.single(),
        )
    }

    @Test fun exactAvailableCopyKeepsRetainedAlbumPlayable() {
        val oldRoot = MediaId(1)
        val newRoot = MediaId(2)
        val track = MediaId(10)
        val snapshot =
            Snapshot(
                2,
                "test",
                listOf(
                    root(oldRoot, "lost", available = false),
                    Node(NodeKind.MEDIA, track, oldRoot, SourceInfo("old.flac"), life = Life(1, Flags.ACSS)),
                    root(newRoot, "current"),
                    Node(NodeKind.MEDIA, track, newRoot, SourceInfo("copy.flac"), life = Life(2)),
                ),
                albums = listOf(Album(20, AlbumKind.ORDINARY, "Album", listOf(track), listOf(1), emptyMap(), Life(2))),
            )
        val player = CuetreePlayerProjection.build(snapshot, genres) { false }
        assertEquals(listOf(3), player.albums.single().sources)
        assertEquals("old.flac", snapshot.nodes[1].source?.name)
        assertFalse(player.nodes[1].life.available)
    }

    @Test fun automaticBookIsInternedInExistingAttributeCatalog() {
        val rootId = MediaId(1)
        val bookId = MediaId(10)
        val source =
            Snapshot(
                2,
                "test",
                listOf(
                    root(rootId, "music"),
                    Node(
                        NodeKind.MEDIA,
                        bookId,
                        rootId,
                        SourceInfo("book.m4b"),
                        attributes = mapOf("mediaKind" to listOf("2"), "genre" to listOf("Novel")),
                        life = Life(2),
                    ),
                ),
                albums = listOf(Album(20, AlbumKind.ORDINARY, "Book", listOf(bookId), listOf(1), emptyMap(), Life(2))),
            )
        val full = FlatCatalogs.encode(source).snapshot()
        assertTrue(full.attributeCatalog.isNotEmpty())
        val projected = CuetreePlayerProjection.build(full, genres) { false }
        val decoded = FlatCatalogs.encode(projected).snapshot()
        assertEquals(listOf("1"), decoded.nodes.single { it.kind == NodeKind.MEDIA }.attributes["automaticBook"])
        assertEquals(listOf("1"), decoded.albums.single().attributes["automaticBook"])
        assertTrue(
            decoded.attributeCatalog.any {
                it.value.kind == AttributeKind.EXTENSION && it.value.text == "13:automaticBook1"
            },
        )
    }

    @Test fun firstAvailableCopyAndThreePositiveBookSources() {
        val root = MediaId(1)
        val music = MediaId(10)
        val byGenre = MediaId(11)
        val byKind = MediaId(12)
        val byRoot = MediaId(13)
        val audiobookRoot = MediaId(2)
        val snapshot =
            Snapshot(
                1,
                "test",
                listOf(
                    root(root, "music"),
                    Node(NodeKind.MEDIA, music, root, SourceInfo("first.flac"), life = Life(1)),
                    Node(
                        NodeKind.MEDIA,
                        music,
                        root,
                        SourceInfo("second.flac"),
                        attributes = mapOf("genre" to listOf("Book genre")),
                        life = Life(1),
                    ),
                    Node(
                        NodeKind.MEDIA,
                        byGenre,
                        root,
                        SourceInfo("genre.flac"),
                        attributes = mapOf("genre" to listOf("Book genre")),
                        life = Life(1),
                    ),
                    Node(
                        NodeKind.MEDIA,
                        byKind,
                        root,
                        SourceInfo("kind.m4b"),
                        attributes = mapOf("mediaKind" to listOf("2")),
                        life = Life(1),
                    ),
                    root(audiobookRoot, "books"),
                    Node(NodeKind.MEDIA, byRoot, audiobookRoot, SourceInfo("root.flac"), life = Life(1)),
                ),
                albums = listOf(Album(20, AlbumKind.ORDINARY, "Book", listOf(byGenre, music), listOf(3, 1), emptyMap(), Life(1))),
            )
        val player = CuetreePlayerProjection.build(snapshot, genres) { it == "books" }
        assertFalse(player.nodes[1].attributes.containsKey("automaticBook"))
        assertFalse(player.nodes[2].attributes.containsKey("automaticBook"))
        assertTrue(player.nodes[3].attributes.containsKey("automaticBook"))
        assertTrue(player.nodes[4].attributes.containsKey("automaticBook"))
        assertTrue(player.nodes[6].attributes.containsKey("automaticBook"))
        assertEquals(listOf("1"), player.albums.single().attributes["automaticBook"])
    }

    @Test fun m4bFilenameClassifiesMediaAndItsFragmentsWithoutMediaKind() {
        val rootId = MediaId(1)
        val baseId = MediaId(10)
        val fragmentId = MediaId(11)
        val otherId = MediaId(12)
        val source =
            Snapshot(
                2,
                "test",
                listOf(
                    root(rootId, "folder.m4b"),
                    Node(NodeKind.MEDIA, baseId, rootId, SourceInfo("story.M4B"), life = Life(2)),
                    Node(
                        NodeKind.FRAGMENT,
                        fragmentId,
                        baseId,
                        span = Span(0, 10_000_000),
                        attributes =
                            mapOf(
                                "title" to listOf("Chapter"),
                                "fragment" to listOf("1"),
                                "totalFragments" to listOf("1"),
                            ),
                        life = Life(2),
                    ),
                    Node(NodeKind.MEDIA, otherId, rootId, SourceInfo("story.m4a"), life = Life(2)),
                ),
                albums = listOf(Album(20, AlbumKind.EXPLICIT, "Story", listOf(fragmentId), listOf(2), emptyMap(), Life(2))),
            )
        val full = FlatCatalogs.encode(source).snapshot()
        val player = CuetreePlayerProjection.build(full, genres) { false }
        val decoded = FlatCatalogs.encode(player).snapshot()
        assertEquals(listOf("1"), decoded.nodes[1].attributes["automaticBook"])
        assertEquals(listOf("1"), decoded.nodes[2].attributes["automaticBook"])
        assertFalse(decoded.nodes[3].attributes.containsKey("automaticBook"))
        assertEquals(listOf("1"), decoded.albums.single().attributes["automaticBook"])
    }

    @Test fun acceptedShortKeyAiGenreEntersIndexerProjectionAfterExpansion() {
        val folder = MediaId(1)
        val expanded = MediaId(10, 2)
        val source =
            Snapshot(
                5,
                "test",
                listOf(
                    root(folder, "music"),
                    Node(
                        NodeKind.MEDIA,
                        expanded,
                        folder,
                        SourceInfo("track.flac"),
                        life = Life(5),
                        identityFlags = Flags.SHRT,
                    ),
                ),
                albums =
                    listOf(
                        Album(
                            20,
                            AlbumKind.ORDINARY,
                            "Album",
                            listOf(expanded),
                            listOf(1),
                            emptyMap(),
                            Life(5),
                        ),
                    ),
            )
        val full = FlatCatalogs.encode(source).snapshot()
        val analysis =
            AnalysisSnapshot(
                3,
                listOf(
                    AnalysisRecord(
                        MediaId(10),
                        aiState = AnalysisState.AVAILABLE,
                        genres = listOf(AnalysisGenre(-100000, 0.9f)),
                    ),
                ),
            )
        val projected =
            CuetreePlayerProjection.build(
                full,
                genres,
                analysis,
                mapOf(-100000L to (33L to "Jazz"), 33L to (33L to "Jazz")),
            ) { false }
        val inferred = projected.genres.single { it.ref.dictionaryId == 33L }
        assertEquals("Jazz", inferred.name)
        assertEquals(listOf(20L), inferred.albums)
        assertEquals(
            inferred,
            FlatCatalogs
                .encode(projected)
                .snapshot()
                .genres
                .single(),
        )
    }

    @Test fun fragmentAiGenreEntersExplicitAlbumProjection() {
        val folder = MediaId(1)
        val base = MediaId(10)
        val fragment = MediaId(20)
        val source =
            Snapshot(
                5,
                "test",
                listOf(
                    root(folder, "music"),
                    Node(NodeKind.MEDIA, base, folder, SourceInfo("album.flac"), life = Life(5)),
                    Node(
                        NodeKind.FRAGMENT,
                        fragment,
                        base,
                        span = Span(0, 60_000_000),
                        attributes = mapOf("fragment" to listOf("1"), "totalFragments" to listOf("1")),
                        life = Life(5),
                    ),
                ),
                albums = listOf(Album(30, AlbumKind.EXPLICIT, "Album", listOf(fragment), listOf(2), emptyMap(), Life(5))),
            )
        val full = FlatCatalogs.encode(source).snapshot()
        val analysis =
            AnalysisSnapshot(
                3,
                listOf(
                    AnalysisRecord(
                        fragment,
                        aiState = AnalysisState.AVAILABLE,
                        genres = listOf(AnalysisGenre(-100_086, 0.9f)),
                    ),
                ),
            )
        val projected =
            CuetreePlayerProjection.build(
                full,
                genres,
                analysis,
                mapOf(-100_086L to (33L to "Jazz")),
            ) { false }
        val inferred = projected.genres.single()
        assertEquals(listOf(30L), inferred.albums)
        assertEquals(33L, inferred.ref.dictionaryId)
        FlatCatalogs.encode(projected).snapshot()
    }

    @Test fun inferredGenreUsesEverySourceCopyWithoutBookAlbums() {
        val root = MediaId(1)
        val shared = MediaId(10)
        val other = MediaId(11)
        val book = MediaId(12)
        val snapshot =
            Snapshot(
                5,
                "test",
                listOf(
                    root(root, "music"),
                    Node(NodeKind.MEDIA, shared, root, SourceInfo("first.flac"), life = Life(5)),
                    Node(NodeKind.MEDIA, shared, root, SourceInfo("copy.flac"), life = Life(5)),
                    Node(NodeKind.MEDIA, other, root, SourceInfo("other.flac"), life = Life(5)),
                    Node(NodeKind.MEDIA, book, root, SourceInfo("book.m4b"), life = Life(5)),
                ),
                albums =
                    listOf(
                        Album(21, AlbumKind.ORDINARY, "Copy", listOf(shared), listOf(2), emptyMap(), Life(5)),
                        Album(20, AlbumKind.ORDINARY, "Original", listOf(shared, other), listOf(1, 3), emptyMap(), Life(5)),
                        Album(22, AlbumKind.ORDINARY, "Book", listOf(book), listOf(4), emptyMap(), Life(5)),
                    ),
            )
        val analysis =
            AnalysisSnapshot(
                7,
                listOf(shared, other, book).map { id ->
                    AnalysisRecord(id, aiState = AnalysisState.AVAILABLE, genres = listOf(AnalysisGenre(33, 0.9f)))
                },
            )
        val projected =
            CuetreePlayerProjection.build(
                snapshot,
                genres,
                analysis,
                mapOf(33L to (33L to "Jazz")),
            ) { false }
        assertEquals(listOf(20L, 21L), projected.genres.single { it.ref.dictionaryId == 33L }.albums)
        FlatCatalogs.encode(projected).snapshot()
    }

    private fun root(
        id: MediaId,
        locator: String,
        available: Boolean = true,
    ) = Node(
        NodeKind.DIRECTORY,
        id,
        source = SourceInfo(locator, provider = "saf", locator = locator),
        attributes = mapOf(ROOT_IDENTITY_ATTRIBUTE to listOf(locator)),
        life = Life(if (available) 2 else 1, if (available) 0 else Flags.ACSS),
    )
}
