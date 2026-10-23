package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HierarchyNavigationTest {
    private fun fixture() =
        PlayerUiState(
            root = BrowserItem("root", "Fixture", BrowserKind.FOLDER),
            nowPlaying = NowPlayingUiState(),
            screen = PlayerScreen.AZ_SEARCH,
            browserPath = listOf("category:folders"),
            azSteps = listOf(AzGroup.LATIN_AC),
            azDraftSteps = listOf(AzGroup.LATIN_AC, AzGroup.LATIN_DF),
            selectorIndex = 7,
            selectorOffset = 13,
            focusedItemId = "row",
        )

    @Test
    fun childAndParentUseDestinationFilterAndRetainSearch() {
        val source = fixture()
        val child = restoreHierarchyContainer(source, source.browserPath + "child", listOf(AzGroup.LATIN_WZ))
        assertEquals(PlayerScreen.AZ_SEARCH, child.screen)
        assertEquals(listOf(AzGroup.LATIN_WZ), child.azDraftSteps)
        assertFalse(child.azAutoEntryPending)
        assertFalse(child.azAutoApplyPending)
        val parent = restoreHierarchyContainer(child, source.browserPath, source.azSteps, index = 7, offset = 13)
        assertEquals(source.azSteps, parent.azDraftSteps)
        assertEquals(7, parent.selectorIndex)
        assertEquals(13, parent.selectorOffset)
    }

    @Test
    fun ordinaryNavigationUsesSameRestorationWithoutStartingSearch() {
        val next = restoreHierarchyContainer(fixture().copy(azDraftSteps = null), listOf("child"), emptyList())
        assertEquals(PlayerScreen.HIERARCHY, next.screen)
        assertNull(next.azDraftSteps)
        assertTrue(next.azAutoEntryPending)
    }

    @Test
    fun autoSearchThresholdUsesCompleteSourceBeforeRestoredFilter() {
        val rows = (0 until 12).map { BrowserItem("row:$it", "Row $it", BrowserKind.TRACK) }
        val category = BrowserItem("category:folders", "Folders", BrowserKind.CATEGORY, children = rows)
        val state =
            fixture().copy(
                screen = PlayerScreen.HIERARCHY,
                root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(category)),
                browserPath = listOf(category.id),
                azDraftSteps = null,
                azSteps = listOf(AzGroup.LATIN_AC),
                azSnapshot =
                    AzSearchSnapshot(
                        AzSearchQuery(rows, listOf(AzGroup.LATIN_AC), null),
                        visibleItems = rows.take(1),
                        draftItems = rows.take(1),
                        resultCounts = emptyMap(),
                    ),
            )

        assertEquals(1, state.visibleChildren().size)
        assertEquals(12, autoSearchSourceItemCount(state))
    }

    @Test
    fun azControlCloseAppliesDraftAndPreservesPreviewAnchor() {
        val rows = (0 until 5).map { BrowserItem("row:$it", "Alpha $it", BrowserKind.TRACK) }
        val draft = listOf(AzGroup.LATIN_AC)
        val state =
            fixture().copy(
                screen = PlayerScreen.AZ_SEARCH,
                root = BrowserItem("root", "Root", BrowserKind.ROOT, children = rows),
                browserPath = emptyList(),
                azSteps = emptyList(),
                azDraftSteps = draft,
                selectorIndex = 2,
                selectorOffset = 19,
                focusedItemId = "row:2",
                azSnapshot = AzPreparedSection(rows).snapshot(AzSearchQuery(rows, emptyList(), draft)),
            )

        val next = applyAzSearch(state)

        assertEquals(PlayerScreen.HIERARCHY, next.screen)
        assertEquals(draft, next.azSteps)
        assertEquals(null, next.azDraftSteps)
        assertEquals(2, next.selectorIndex)
        assertEquals(19, next.selectorOffset)
        assertEquals("row:2", next.focusedItemId)
        assertFalse(next.azAutoEntryPending)
    }

    @Test
    fun contentModeLabelsAreAvailableBeforeLibraryProjectionLoads() {
        val music = fixture().copy(hierarchyRoot = HierarchyRoot.ALBUMS, contentMode = ContentMode.MUSIC)
        assertEquals("Albums", music.visibleHierarchyLabel())
        assertEquals("Titles", music.copy(contentMode = ContentMode.AUDIOBOOK).visibleHierarchyLabel())
        assertEquals(
            "Authors",
            music
                .copy(
                    hierarchyRoot = HierarchyRoot.ARTISTS,
                    contentMode = ContentMode.AUDIOBOOK,
                ).visibleHierarchyLabel(),
        )
        assertEquals(
            "Folders",
            music
                .copy(
                    hierarchyRoot = HierarchyRoot.FOLDERS,
                    contentMode = ContentMode.AUDIOBOOK,
                ).visibleHierarchyLabel(),
        )
    }

    @Test
    fun moreAndNestedSelectorRestorePreviewPositionWithoutCommittingDraft() {
        val source = fixture()
        val menu = source.copy(screen = PlayerScreen.ROW_ACTIONS, selectorIndex = 0, selectorOffset = 0)
        val next = resumeHierarchyAfterMenu(menu, source)
        assertEquals(PlayerScreen.AZ_SEARCH, next.screen)
        assertEquals(source.azDraftSteps, next.azDraftSteps)
        assertEquals(source.azSteps, next.azSteps)
        assertEquals(7, next.selectorIndex)
        assertEquals(13, next.selectorOffset)
        assertEquals("row", next.focusedItemId)
        assertFalse(next.azAutoApplyPending)
    }

    @Test
    fun staleMenuPositionDoesNotOverrideRepairedLibraryPosition() {
        val source = fixture()
        val current = source.copy(libraryGeneration = (source.libraryGeneration ?: 0L) + 1, selectorIndex = 2)
        assertEquals(2, resumeHierarchyAfterMenu(current, source).selectorIndex)
    }

    @Test
    fun everyNormalRowActionIsAcceptedDuringSearch() {
        listOf(
            PlayerUiAction.NavigateTo("row"),
            PlayerUiAction.Play("row"),
            PlayerUiAction.SetReaction("row", Preference.LIKED),
            PlayerUiAction.SetReaction("row", Preference.DISLIKED),
            PlayerUiAction.SetReaction("row", toggleBan = true),
            PlayerUiAction.OpenRowActions("row"),
            PlayerUiAction.RememberSelectorPosition(1, 5, 2),
        ).forEach { assertTrue(it.allowedDuringAzOverlay()) }
    }

    @Test
    fun playlistSuccessUsesDestinationFilterAndRetainsSearchSession() {
        val playlist = BrowserItem(playlistItemId(7), "Playlist", BrowserKind.PLAYLIST)
        val root =
            BrowserItem(
                "root",
                "Root",
                BrowserKind.FOLDER,
                children =
                    listOf(
                        BrowserItem(HierarchyRoot.PLAYLISTS.categoryId, "Playlists", BrowserKind.PLAYLIST, children = listOf(playlist)),
                    ),
            )
        val next = openCommittedPlaylist(fixture(), root, 7, "Added", listOf(AzGroup.LATIN_WZ))
        assertEquals(PlayerScreen.AZ_SEARCH, next.screen)
        assertEquals(HierarchyRoot.PLAYLISTS, next.hierarchyRoot)
        assertEquals(listOf(AzGroup.LATIN_WZ), next.azDraftSteps)
        assertEquals(next.azSteps, next.azDraftSteps)
        assertEquals(0, next.selectorIndex)
        assertEquals(0, next.selectorOffset)
    }

    @Test
    fun sharedLookupKeepsPreorderAndReturnsNullForMissingRows() {
        val leaf = BrowserItem("leaf", "Leaf", BrowserKind.TRACK)
        val root = BrowserItem("root", "Root", BrowserKind.FOLDER, children = listOf(leaf))
        assertEquals(root, findBrowserItem(root, "root"))
        assertEquals(leaf, findBrowserItem(root, "leaf"))
        assertNull(findBrowserItem(root, "missing"))
    }

    @Test
    fun constrainedPanelLeavesOneStandardActionVisibleAndScrollsItsOwnRows() {
        assertEquals(348f, selectorBottomLayerHeight(634f, 100f, 3, true), 0f)
        assertEquals(76f, selectorBottomLayerHeight(192f, 176f, 6, false), 0f)
        assertEquals(100f, selectorBottomLayerHeight(634f, 100f, 1, true), 0f)
        assertEquals(0f, selectorBottomLayerHeight(80f, 176f, 6, false), 0f)
    }
}
