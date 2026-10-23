package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AzBreadcrumbTest {
    private val steps = listOf(AzGroup.LATIN_AC, AzGroup.LATIN_DF)
    private val childSteps = listOf(AzGroup.LATIN_WZ)

    private fun fixture(kind: HierarchyRoot = HierarchyRoot.FOLDERS): PlayerUiState {
        val child = BrowserItem("child", "Child", BrowserKind.FOLDER)
        val category = BrowserItem(kind.categoryId, kind.label, BrowserKind.CATEGORY, children = listOf(child))
        val root = BrowserItem("root", "Library", BrowserKind.ROOT, children = listOf(category))
        val path = listOf(category.id, child.id)
        return PlayerUiState(
            screen = PlayerScreen.HIERARCHY,
            root = root,
            hierarchyRoot = kind,
            browserPath = path,
            azSteps = childSteps,
            azBreadcrumbs = azBreadcrumbScopes(root, path, childSteps) { steps },
            azAutoEntryPending = true,
            selectorIndex = 12,
            selectorOffset = 40,
            nowPlaying = NowPlayingUiState(),
        )
    }

    @Test
    fun allRootsKeepIndependentAncestorAndChildFilters() {
        HierarchyRoot.entries.forEach { kind ->
            val state = fixture(kind)
            assertEquals(listOf(kind.label, "Child"), state.azBreadcrumbs.map { it.label })
            assertEquals(listOf(steps, childSteps), state.azBreadcrumbs.map { it.steps })
            assertEquals(childSteps, state.azSteps)
        }
    }

    @Test
    fun eachAncestorPrefixNavigatesDirectlyAndArmsOneAutoSearchAttempt() {
        HierarchyRoot.entries.forEach { kind ->
            val state = fixture(kind)
            (0..steps.size).forEach { length ->
                val prefix = steps.take(length)
                val next = selectAzBreadcrumb(state, PlayerUiAction.SelectAzBreadcrumb(listOf(kind.categoryId), prefix))
                assertEquals(listOf(kind.categoryId), next.browserPath)
                assertEquals(prefix, next.azSteps)
                assertEquals(0, next.selectorIndex)
                assertEquals(0, next.selectorOffset)
                assertEquals(PlayerScreen.HIERARCHY, next.screen)
                assertTrue(next.azAutoEntryPending)
                assertFalse(next.azAutoApplyPending)
                assertSame(state.nowPlaying, next.nowPlaying)
                assertEquals(kind, next.hierarchyRoot)
            }
        }
    }

    @Test
    fun artistsAndGenresRetainTypedAlbumContextOnDescent() {
        listOf(HierarchyRoot.ARTISTS, HierarchyRoot.GENRES).forEach { kind ->
            val state = fixture(kind)
            assertEquals(HierarchyRoot.ALBUMS, state.visibleHierarchyRoot())
            val next = selectAzBreadcrumb(state, PlayerUiAction.SelectAzBreadcrumb(listOf(kind.categoryId), steps))
            assertEquals(kind, next.visibleHierarchyRoot())
        }
    }

    @Test
    fun unrelatedPathsAndNonPrefixesCannotNavigate() {
        val state = fixture()
        val invalid =
            listOf(
                PlayerUiAction.SelectAzBreadcrumb(listOf("unrelated"), emptyList()),
                PlayerUiAction.SelectAzBreadcrumb(state.browserPath + "stale", emptyList()),
                PlayerUiAction.SelectAzBreadcrumb(emptyList(), emptyList()),
                PlayerUiAction.SelectAzBreadcrumb(state.browserPath.take(1), childSteps),
                PlayerUiAction.SelectAzBreadcrumb(state.browserPath.take(1), steps + steps),
            )
        invalid.forEach { assertSame(state, selectAzBreadcrumb(state, it)) }
    }

    @Test
    fun modalAndInvalidLibraryPathsRejectStaleActions() {
        val state = fixture()
        val action = PlayerUiAction.SelectAzBreadcrumb(state.browserPath.take(1), steps)
        val modal = state.copy(screen = PlayerScreen.AZ_SEARCH)
        assertSame(modal, selectAzBreadcrumb(modal, action))
        val removed = state.copy(root = state.root.copy(children = emptyList()))
        assertSame(removed, selectAzBreadcrumb(removed, action))
        assertEquals(emptyList<AzBreadcrumbScope>(), azBreadcrumbScopes(removed.root, removed.browserPath, steps) { steps })
    }

    @Test
    fun foregroundAncestorPrefixSavesFilterAndRejectsBackgroundActions() {
        val state = fixture().copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = childSteps + steps)
        val action = PlayerUiAction.SelectAzBreadcrumb(state.browserPath.take(1), steps.take(1), inSearch = true)
        val next = selectAzBreadcrumb(state, action)
        assertEquals(PlayerScreen.AZ_SEARCH, next.screen)
        assertEquals(state.browserPath.take(1), next.browserPath)
        assertEquals(steps.take(1), next.azSteps)
        assertEquals(steps.take(1), next.azDraftSteps)
        assertFalse(next.azAutoApplyPending)
        assertFalse(next.azAutoEntryPending)
        assertEquals(0, next.selectorIndex)
        assertSame(state, selectAzBreadcrumb(state, action.copy(inSearch = false)))
        val hierarchy = state.copy(screen = PlayerScreen.HIERARCHY)
        assertSame(hierarchy, selectAzBreadcrumb(hierarchy, action))
    }

    @Test
    fun foregroundCurrentPrefixUsesDraftRatherThanCommittedSteps() {
        val state = fixture().copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = steps)
        val next = selectAzBreadcrumb(state, PlayerUiAction.SelectAzBreadcrumb(state.browserPath, steps.take(1), true))
        assertEquals(steps.take(1), next.azSteps)
        assertEquals(steps.take(1), next.azDraftSteps)
        assertEquals(steps, state.azBreadcrumbScopesForDisplay().last().steps)
        assertEquals(steps, state.azBreadcrumbScopesForDisplay().first().steps)
    }

    @Test
    fun parentInSearchRestoresOwnFilterAndCannotLeaveCategoryRoot() {
        val state = fixture().copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = childSteps + childSteps)
        val next = restoreHierarchyContainer(state, state.browserPath.dropLast(1), steps)
        assertEquals(state.browserPath.dropLast(1), next.browserPath)
        assertEquals(steps, next.azSteps)
        assertEquals(steps, next.azDraftSteps)
        assertEquals(PlayerScreen.AZ_SEARCH, next.screen)
        assertFalse(next.azAutoApplyPending)
        val ordinary = restoreHierarchyContainer(fixture(), state.browserPath.dropLast(1), steps)
        assertEquals(PlayerScreen.HIERARCHY, ordinary.screen)
        assertEquals(null, ordinary.azDraftSteps)
    }

    @Test
    fun azClearClosesPendingOrEmptySearchWithoutDeletingAncestorFilter() {
        val state = fixture().copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = childSteps, azSnapshot = null)
        val next = clearAzSearch(state)
        assertEquals(state.browserPath, next.browserPath)
        assertEquals(emptyList<AzGroup>(), next.azSteps)
        assertEquals(null, next.azDraftSteps)
        assertEquals(PlayerScreen.HIERARCHY, next.screen)
        assertEquals(0, next.selectorIndex)
        assertEquals(steps, next.azBreadcrumbs.first().steps)
        assertFalse(next.azAutoEntryPending)
        assertSame(next, clearAzSearch(next))
    }

    @Test
    fun rememberedBreadcrumbDestinationRestoresItsFilterAnchorFocusAndAutoAttempt() {
        val state = fixture().copy(azAutoEntryPending = false)
        val destinationPath = state.browserPath.take(1)
        val selected = selectAzBreadcrumb(state, PlayerUiAction.SelectAzBreadcrumb(destinationPath, steps))
        val remembered =
            HierarchyNavigationSnapshot(
                browserPath = destinationPath,
                page = 3,
                pageSize = 4,
                selectorIndex = 13,
                selectorOffset = 27,
                focusedItemId = "remembered",
                azSteps = steps,
            )

        val next = restoreRememberedHierarchyContainer(selected, remembered)

        assertEquals(destinationPath, next.browserPath)
        assertEquals(steps, next.azSteps)
        assertEquals(3, next.page)
        assertEquals(4, next.pageSize)
        assertEquals(13, next.selectorIndex)
        assertEquals(27, next.selectorOffset)
        assertEquals("remembered", next.focusedItemId)
        assertTrue(next.azAutoEntryPending)
        assertEquals(null, next.azDraftSteps)
    }

    @Test
    fun currentContainerFilterBreadcrumbReArmsAutoSearch() {
        val committed = listOf(AzGroup.LATIN_MO, AzGroup.LATIN_TV)
        val initial = fixture()
        val state =
            initial.copy(
                azSteps = committed,
                azBreadcrumbs = azBreadcrumbScopes(initial.root, initial.browserPath, committed) { steps },
            )
        val prefix = committed.dropLast(1)
        val next = selectAzBreadcrumb(state, PlayerUiAction.SelectAzBreadcrumb(state.browserPath, prefix))
        assertEquals(state.browserPath, next.browserPath)
        assertEquals(prefix, next.azSteps)
        assertTrue(next.azAutoEntryPending)
        assertEquals(steps, state.azBreadcrumbs.first().steps)
        val rebuilt = azBreadcrumbScopes(next.root, next.browserPath, next.azSteps) { steps }
        assertEquals(steps, rebuilt.first().steps)
        assertEquals(prefix, rebuilt.last().steps)
    }
}
