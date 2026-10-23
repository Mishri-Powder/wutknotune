package link.roadio.riff.player.library

import link.roadio.cuetree.Album
import link.roadio.cuetree.AlbumKind
import link.roadio.cuetree.Flags
import link.roadio.cuetree.Genre
import link.roadio.cuetree.GenreRef
import link.roadio.cuetree.Life
import link.roadio.cuetree.MediaId
import link.roadio.cuetree.Node
import link.roadio.cuetree.NodeKind
import link.roadio.cuetree.ROOT_IDENTITY_ATTRIBUTE
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.SourceInfo
import link.roadio.cuetree.Span
import link.roadio.riff.indexer.core.analysis.AnalysisGainSource
import link.roadio.riff.indexer.core.analysis.AnalysisGenre
import link.roadio.riff.indexer.core.analysis.AnalysisRecord
import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot
import link.roadio.riff.indexer.core.analysis.AnalysisState
import link.roadio.riff.player.storage.StoredContentMode
import link.roadio.riff.ui.BrowserItem
import link.roadio.riff.ui.BrowserKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import link.roadio.riff.indexer.core.media.MediaId as PlayerMediaId

class CuetreeBrowserTreeTest {
    @Test fun singlesDisplayRetainsCatalogIdentityMembershipAndLiteralAlbumNames() {
        val root = MediaId(1)
        val media = MediaId(10)
        val albums =
            listOf(
                Album(30, AlbumKind.SINGLES, "[[SINGLES]]", listOf(media), listOf(1), emptyMap(), Life(1)),
                Album(
                    31,
                    AlbumKind.ARTIST_SINGLES,
                    "The Echo; Guest: singles",
                    listOf(media),
                    listOf(1),
                    mapOf("albumArtist" to listOf("The Echo", "Guest")),
                    Life(1),
                ),
                Album(32, AlbumKind.ORDINARY, "Artist: singles", listOf(media), listOf(1), emptyMap(), Life(1)),
                Album(33, AlbumKind.EXPLICIT, "[[SINGLES]]", listOf(media), listOf(1), emptyMap(), Life(1)),
            )
        val snapshot =
            Snapshot(
                1,
                "test",
                listOf(
                    Node(
                        NodeKind.DIRECTORY,
                        root,
                        source = SourceInfo("Music", provider = "saf", locator = "content://music"),
                        life = Life(1),
                    ),
                    Node(NodeKind.MEDIA, media, root, SourceInfo("track.flac"), life = Life(1)),
                ),
                albums = albums,
            )
        val model = CuetreeBrowserTree.build(snapshot, PlayerLibraryProjection(), emptySet(), hash = { 7L }).music
        val rows =
            model.root.children
                .single { it.id == "category:albums" }
                .children
                .associateBy { it.albumGroupId }
        val expected = listOf("◇ Singles", "The Echo; Guest ◇ Singles", "Artist: singles", "[[SINGLES]]")
        albums.zip(expected).forEach { (album, title) ->
            val row = rows.getValue(album.id)
            assertEquals(title, row.label)
            assertEquals(album.title, row.comparisonName)
            assertEquals("album:${album.id.toULong().toString(16)}", row.id)
            assertEquals(1, row.children.size)
            assertEquals(title, row.children.single().album)
            assertEquals(album.id, row.children.single().albumGroupId)
            assertEquals(PlayerMediaId(10, 0, false), row.children.single().mediaId)
        }
        assertEquals(listOf("[[SINGLES]]", "The Echo; Guest: singles", "Artist: singles", "[[SINGLES]]"), snapshot.albums.map { it.title })
    }

    @Test fun shortDurableReferencesResolveAfterCollisionWithoutRewritingAnalysis() {
        val root = MediaId(1)
        val original = MediaId(10, 2)
        val contender = MediaId(10, 3)
        val snapshot =
            Snapshot(
                2,
                "test",
                listOf(
                    Node(
                        NodeKind.DIRECTORY,
                        root,
                        source = SourceInfo("Music", provider = "saf", locator = "content://tree"),
                        attributes =
                            mapOf(
                                ROOT_IDENTITY_ATTRIBUTE to
                                    listOf("00000000-0000-4000-8000-000000000000"),
                            ),
                        life = Life(2),
                    ),
                    Node(
                        NodeKind.MEDIA,
                        original,
                        root,
                        SourceInfo("original.flac"),
                        life = Life(2),
                        identityFlags = Flags.SHRT,
                    ),
                    Node(
                        NodeKind.MEDIA,
                        contender,
                        root,
                        SourceInfo("contender.flac"),
                        life = Life(2),
                    ),
                ),
            )
        val analysis =
            AnalysisSnapshot(
                1,
                listOf(
                    AnalysisRecord(
                        MediaId(10),
                        gainState = AnalysisState.AVAILABLE,
                        gainSource = AnalysisGainSource.REPLAY_GAIN,
                        trackGainQ8 = (-256).toShort(),
                        aiState = AnalysisState.AVAILABLE,
                        arousal = 0.2f,
                        arousalConfidence = 0.9f,
                        valence = -0.1f,
                        valenceConfidence = 0.8f,
                    ),
                ),
            )
        assertTrue(CuetreeBrowserTree.emptyRoot().children.any { it.id == "category:ai" })
        val model =
            CuetreeBrowserTree
                .build(
                    snapshot,
                    PlayerLibraryProjection(),
                    emptySet(),
                    analysis,
                    hash = { 7L },
                ).music
        assertTrue(model.root.children.any { it.id == "category:ai" })
        val short = PlayerMediaId(10, 0, false)
        val full = PlayerMediaId(10, 2, true)
        val other = PlayerMediaId(10, 3, true)
        assertTrue(short in model.mediaIds)
        assertEquals(model.playbackSourcesByMediaId[full], model.playbackSourcesByMediaId[short])
        assertEquals(model.tracksByMediaId[full], model.tracksByMediaId[short])
        assertEquals((-256).toShort(), model.replayGainByMediaId.getValue(full).trackGainQ8)
        assertTrue(full in model.aiFeaturesByMediaId)
        assertEquals(model.aiFeaturesByMediaId[full], model.aiFeaturesByMediaId[short])
        assertEquals(model.replayGainByMediaId[full], model.replayGainByMediaId[short])
        assertTrue(other !in model.aiFeaturesByMediaId)
    }

    @Test fun albumGainSurvivesUnavailableTrackMeasurement() {
        val root = MediaId(1)
        val track = MediaId(10)
        val snapshot =
            Snapshot(
                1,
                "test",
                listOf(
                    Node(
                        NodeKind.DIRECTORY,
                        root,
                        source = SourceInfo("Music", provider = "saf", locator = "content://music"),
                        life = Life(1),
                    ),
                    Node(NodeKind.MEDIA, track, root, SourceInfo("track.flac"), life = Life(1)),
                ),
            )
        val analysis =
            AnalysisSnapshot(
                1,
                listOf(
                    AnalysisRecord(
                        track,
                        gainState = AnalysisState.COMPLETED_NO_RESULT,
                        albumGainQ8 = 0,
                        albumSource = AnalysisGainSource.REPLAY_GAIN,
                    ),
                ),
            )
        val model = CuetreeBrowserTree.build(snapshot, PlayerLibraryProjection(), emptySet(), analysis, hash = { 7L }).music
        val gain = model.replayGainByMediaId.getValue(PlayerMediaId(10, 0, false))
        assertEquals(null, gain.trackGainQ8)
        assertEquals(0.toShort(), gain.albumGainQ8)
    }

    @Test fun fragmentAiGenreUsesPublishedCanonicalGenreLabel() {
        val rootId = MediaId(1)
        val baseId = MediaId(10)
        val fragmentId = MediaId(20)
        val snapshot =
            Snapshot(
                generation = 1,
                identityScheme = "test",
                nodes =
                    listOf(
                        Node(
                            NodeKind.DIRECTORY,
                            rootId,
                            source = SourceInfo("Music", provider = "saf", locator = "content://music"),
                            attributes = mapOf(ROOT_IDENTITY_ATTRIBUTE to listOf("00000000-0000-4000-8000-000000000000")),
                            life = Life(1),
                        ),
                        Node(NodeKind.MEDIA, baseId, rootId, SourceInfo("album.flac"), life = Life(1)),
                        Node(
                            NodeKind.FRAGMENT,
                            fragmentId,
                            baseId,
                            span = Span(0, 60_000_000),
                            attributes = mapOf("fragment" to listOf("1"), "totalFragments" to listOf("1")),
                            life = Life(1),
                        ),
                    ),
                albums = listOf(Album(30, AlbumKind.EXPLICIT, "Album", listOf(fragmentId), listOf(2), emptyMap(), Life(1))),
                genres = listOf(Genre(GenreRef(dictionaryId = 33), "Jazz", listOf(30), Life(1), 40)),
            )
        val analysis =
            AnalysisSnapshot(
                1,
                listOf(
                    AnalysisRecord(
                        fragmentId,
                        aiState = AnalysisState.AVAILABLE,
                        genres =
                            listOf(
                                AnalysisGenre(-100_086, 0.9f),
                            ),
                    ),
                ),
            )
        val key = PlayerMediaId(20, 0, false)
        val models =
            CuetreeBrowserTree.build(
                snapshot,
                PlayerLibraryProjection(),
                emptySet(),
                analysis,
                mapOf(-100_086L to (33L to "Jazz")),
                hash = { 7L },
            )
        val withoutGenreCatalog =
            CuetreeBrowserTree
                .build(
                    snapshot.copy(genres = emptyList()),
                    PlayerLibraryProjection(),
                    emptySet(),
                    analysis,
                    mapOf(-100_086L to (33L to "Jazz")),
                    hash = { 7L },
                ).music
        assertEquals("Jazz", withoutGenreCatalog.topInferredGenreByMediaId.getValue(key).label)
        val mapped = models.music.topInferredGenreByMediaId.getValue(key)
        assertEquals("Jazz", mapped.label)
        assertEquals(-100_086L, mapped.sourceGenreId)
        val excludedModels =
            CuetreeBrowserTree.build(
                snapshot,
                PlayerLibraryProjection(),
                setOf(33L),
                analysis,
                mapOf(-100_086L to (33L to "Jazz")),
                hash = { 7L },
            )
        assertTrue(key !in excludedModels.music.topInferredGenreByMediaId)
    }

    @Test fun exactFlacAndCueCopiesShowOneFragmentPerPhysicalFile() {
        val rootId = MediaId(1)
        val baseId = MediaId(10)
        val fragmentId = MediaId(20)
        val fragment =
            Node(
                NodeKind.FRAGMENT,
                fragmentId,
                baseId,
                span = Span(0, 2_000_000),
                attributes = mapOf("title" to listOf("Part")),
                life = Life(1),
            )
        val snapshot =
            Snapshot(
                generation = 1,
                identityScheme = "test",
                nodes =
                    listOf(
                        Node(
                            NodeKind.DIRECTORY,
                            rootId,
                            source = SourceInfo("Music", provider = "saf", locator = "content://tree"),
                            attributes = mapOf(ROOT_IDENTITY_ATTRIBUTE to listOf("00000000-0000-4000-8000-000000000000")),
                            life = Life(1),
                        ),
                        Node(NodeKind.MEDIA, baseId, rootId, SourceInfo("a.flac"), life = Life(1)),
                        Node(NodeKind.MEDIA, baseId, rootId, SourceInfo("b.flac"), life = Life(1)),
                        fragment,
                        fragment,
                    ),
            )
        val sourceOrderHash: (ByteArray) -> Long = { bytes ->
            if ("a.flac" in bytes.toString(Charsets.UTF_8)) 2L else 1L
        }
        val models = CuetreeBrowserTree.build(snapshot, PlayerLibraryProjection(), emptySet(), hash = sourceOrderHash)
        val model = models.music
        val physical =
            model.root.children
                .first { it.id == "category:folders" }
                .children
                .single()
                .children
        assertEquals(2, physical.size)
        val fragmentMediaId = PlayerMediaId(20, 0, false)
        val firstSource = model.playbackSourcesByMediaId.getValue(fragmentMediaId).first()
        assertEquals("a.flac", firstSource.segments.last().displayName)
        assertEquals(
            listOf("a.flac", "b.flac"),
            physical.map { container ->
                val fragments = container.children.filter(BrowserItem::fragment)
                assertEquals(1, fragments.size)
                fragments
                    .single()
                    .playbackSource!!
                    .segments
                    .last()
                    .displayName
            },
        )
    }

    @Test fun sameSpanFromDifferentCuesKeepsTwoAlbumTitles() {
        val rootId = MediaId(1)
        val baseId = MediaId(10)
        val fragmentId = MediaId(20)
        val snapshot =
            Snapshot(
                generation = 1,
                identityScheme = "test",
                nodes =
                    listOf(
                        Node(
                            NodeKind.DIRECTORY,
                            rootId,
                            source = SourceInfo("Music", provider = "saf", locator = "content://tree"),
                            attributes = mapOf(ROOT_IDENTITY_ATTRIBUTE to listOf("00000000-0000-4000-8000-000000000000")),
                            life = Life(1),
                        ),
                        Node(NodeKind.MEDIA, baseId, rootId, SourceInfo("image.flac"), life = Life(1)),
                        Node(
                            NodeKind.FRAGMENT,
                            fragmentId,
                            baseId,
                            span = Span(0, 2_000_000),
                            attributes = mapOf("title" to listOf("A")),
                            life = Life(1),
                        ),
                        Node(
                            NodeKind.FRAGMENT,
                            fragmentId,
                            baseId,
                            span = Span(0, 2_000_000),
                            attributes = mapOf("title" to listOf("B")),
                            life = Life(1),
                        ),
                    ),
                albums =
                    listOf(
                        Album(30, AlbumKind.EXPLICIT, "Album A", listOf(fragmentId), listOf(2), emptyMap(), Life(1)),
                        Album(31, AlbumKind.EXPLICIT, "Album B", listOf(fragmentId), listOf(3), emptyMap(), Life(1)),
                    ),
            )
        val model = CuetreeBrowserTree.build(snapshot, PlayerLibraryProjection(), emptySet(), hash = { it.size.toLong() }).music
        val albums =
            model.root.children
                .first { it.id == "category:albums" }
                .children
        assertEquals(listOf("Album A", "Album B"), albums.map(BrowserItem::label))
        assertEquals(listOf("A", "B"), albums.map { it.children.single().label })
        assertEquals(listOf("Album A", "Album B"), albums.map { it.children.single().album })
    }

    @Test fun fragmentUsesBaseSourceAndKeepsOwnMetadataAndSpan() {
        val rootId = MediaId(1)
        val baseId = MediaId(10)
        val fragmentId = MediaId(20)
        val snapshot =
            Snapshot(
                generation = 2,
                identityScheme = "test",
                nodes =
                    listOf(
                        Node(
                            NodeKind.DIRECTORY,
                            rootId,
                            source = SourceInfo("Music", provider = "saf", locator = "content://tree"),
                            attributes = mapOf(ROOT_IDENTITY_ATTRIBUTE to listOf("00000000-0000-4000-8000-000000000000")),
                            life = Life(2),
                        ),
                        Node(NodeKind.MEDIA, baseId, rootId, SourceInfo("whole.flac"), life = Life(2)),
                        Node(
                            NodeKind.FRAGMENT,
                            fragmentId,
                            baseId,
                            span = Span(1_250_000, 2_750_000),
                            attributes =
                                mapOf(
                                    "title" to listOf("Chapter 2"),
                                ),
                            life = Life(2),
                        ),
                    ),
                albums = listOf(Album(30, AlbumKind.EXPLICIT, "Book", listOf(fragmentId), listOf(2), emptyMap(), Life(2))),
            )
        val models =
            CuetreeBrowserTree.build(snapshot, PlayerLibraryProjection(StoredContentMode.MUSIC), emptySet(), hash = {
                it.fold(0L) { sum, byte ->
                    sum *
                        31 +
                        byte
                }
            })
        val fragment =
            models.music.root.children
                .first { it.id == "category:folders" }
                .children
                .single()
                .children
                .single()
                .children
                .single { it.fragment }
        assertEquals("Chapter 2", fragment.label)
        assertEquals(BrowserKind.TRACK, fragment.kind)
        assertTrue(fragment.playable)
        assertEquals(
            "whole.flac",
            fragment.playbackSource!!
                .segments
                .single()
                .displayName,
        )
        assertEquals(1_250_000L, fragment.playbackSource!!.beginUs)
        assertEquals(2_750_000L, fragment.playbackSource!!.endUs)
        assertEquals(
            "Book",
            models.music.root.children
                .first { it.id == "category:albums" }
                .children
                .single()
                .label,
        )
    }

    @Test fun readyBookAlbumUsesPublishedEvidenceWhileFoldersHideUnavailableVolume() {
        val rootId = MediaId(1)
        val lostRootId = MediaId(2)
        val bookId = MediaId(10)
        val lostId = MediaId(11)
        val snapshot =
            Snapshot(
                generation = 2,
                identityScheme = "test",
                nodes =
                    listOf(
                        Node(
                            NodeKind.DIRECTORY,
                            rootId,
                            source = SourceInfo("Music", provider = "saf", locator = "content://music"),
                            attributes = mapOf(ROOT_IDENTITY_ATTRIBUTE to listOf("00000000-0000-4000-8000-000000000000")),
                            life = Life(2),
                        ),
                        Node(
                            NodeKind.MEDIA,
                            bookId,
                            rootId,
                            SourceInfo("book.m4b"),
                            attributes = mapOf("automaticBook" to listOf("1")),
                            life = Life(2),
                        ),
                        Node(
                            NodeKind.DIRECTORY,
                            lostRootId,
                            source = SourceInfo("Lost", provider = "saf", locator = "content://lost"),
                            attributes = mapOf(ROOT_IDENTITY_ATTRIBUTE to listOf("00000000-0000-4000-8000-000000000000")),
                            life = Life(1, Flags.ACSS),
                        ),
                        Node(NodeKind.MEDIA, lostId, lostRootId, SourceInfo("lost.flac"), life = Life(1, Flags.ACSS)),
                    ),
                albums =
                    listOf(
                        Album(
                            30,
                            AlbumKind.ORDINARY,
                            "Lem",
                            listOf(bookId, lostId),
                            listOf(1, 3),
                            mapOf("automaticBook" to listOf("1")),
                            Life(2),
                        ),
                    ),
            )
        val models = CuetreeBrowserTree.build(snapshot, PlayerLibraryProjection(), emptySet(), hash = { it.size.toLong() })
        assertEquals(
            emptyList<String>(),
            models.music.root.children
                .first { it.id == "category:albums" }
                .children
                .map(BrowserItem::label),
        )
        assertEquals(
            listOf("Lem"),
            models.audiobook.root.children
                .first { it.id == "category:albums" }
                .children
                .map(BrowserItem::label),
        )
        assertEquals(
            listOf("Music"),
            models.audiobook.root.children
                .first { it.id == "category:folders" }
                .children
                .map(BrowserItem::label),
        )
        assertEquals(listOf(PlayerMediaId(10, 0, false)), models.audiobook.tracks.map(BrowserItem::mediaId))
        val title =
            models.audiobook.root.children
                .first { it.id == "category:albums" }
                .children
                .single()
        assertEquals(listOf("book.m4b", "lost.flac"), title.children.map(BrowserItem::label))
        assertTrue(title.children.first().playable)
        assertTrue(!title.children.last().playable)
        assertEquals(
            setOf(PlayerMediaId(10, 0, false), PlayerMediaId(11, 0, false)),
            models.audiobook.retainedTracks
                .mapNotNull(BrowserItem::mediaId)
                .toSet(),
        )
    }
}
