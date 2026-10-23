package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AzSavedFilterNavigationTest {
    private val steps = listOf(AzGroup.LATIN_AC, AzGroup.LATIN_DF)
    private val rows =
        listOf("Ada", "Ado", "Alpha", "Beta", "Delta", "Zulu").map {
            BrowserItem(it, it, BrowserKind.FOLDER)
        }

    private fun fixture(kind: HierarchyRoot = HierarchyRoot.ALBUMS): PlayerUiState {
        val category = BrowserItem(kind.categoryId, kind.label, BrowserKind.CATEGORY, children = rows)
        val state =
            PlayerUiState(
                screen = PlayerScreen.HIERARCHY,
                root = BrowserItem("root", "Library", BrowserKind.ROOT, children = listOf(category)),
                hierarchyRoot = kind,
                browserPath = listOf(category.id),
                azSteps = steps,
                selectorIndex = 1,
                selectorOffset = 13,
                focusedItemId = "Ado",
                nowPlaying = NowPlayingUiState(),
            )
        return ready(state)
    }

    private fun ready(state: PlayerUiState): PlayerUiState =
        state.copy(azSnapshot = AzPreparedSection(state.currentNode().children).snapshot(state.azQuery()))

    private fun open(
        navigation: PlayerNavigationState,
        state: PlayerUiState,
    ): PlayerUiState {
        navigation.rememberHierarchy(state)
        return ready(navigation.openAzSearch(state))
    }

    @Test
    fun backspaceSavesEmptyAndNonemptyFiltersBeforePendingWorkOrHome() {
        HierarchyRoot.entries.forEach { kind ->
            val navigation = PlayerNavigationState()
            val opening = open(navigation, fixture(kind))
            val shortened = navigation.backspaceAzSearch(opening)
            assertEquals(steps.take(1), shortened.azSteps)
            assertEquals(shortened.azSteps, shortened.azDraftSteps)
            assertEquals(PlayerScreen.AZ_SEARCH, shortened.screen)
            assertFalse(shortened.azAutoApplyPending)
            assertFalse(shortened.azAutoEntryPending)
            assertNull(shortened.currentAzSnapshot())
            assertEquals(opening.visibleChildren(), shortened.visibleChildren())
            assertTrue(shortened.azAppliedReady())
            assertEquals(shortened.azSteps, navigation.containers[shortened.browserPath]?.azSteps)
            val cleared = navigation.backspaceAzSearch(shortened)
            assertTrue(cleared.azSteps.isEmpty())
            assertEquals(rows, cleared.visibleChildren())
            val main = navigation.cancelAzSearch(cleared).openMain()
            val returned = navigation.openCategory(main, kind.categoryId)
            assertTrue(returned.azSteps.isEmpty())
            assertEquals(0, returned.selectorIndex)
        }
    }

    @Test
    fun cancellationDiscardsOnlyChangesAfterTheLastBackspaceSave() {
        val navigation = PlayerNavigationState()
        val saved = ready(navigation.backspaceAzSearch(open(navigation, fixture())))
        val laterDraft = navigation.selectAzGroup(saved, AzGroup.LATIN_JL)
        assertEquals(steps.take(1), laterDraft.azSteps)
        assertEquals(steps.take(1) + AzGroup.LATIN_JL, laterDraft.azDraftSteps)
        val cancelled = navigation.cancelAzSearch(ready(laterDraft))
        assertEquals(steps.take(1), cancelled.azSteps)
        assertNull(cancelled.azDraftSteps)
        assertEquals(listOf("Ada", "Ado", "Alpha", "Beta"), cancelled.visibleChildren().map { it.label })
    }

    @Test
    fun currentBreadcrumbSavesAnEmptyPrefixAndSurvivesImmediateHome() {
        HierarchyRoot.entries.forEach { kind ->
            val navigation = PlayerNavigationState()
            val opening = open(navigation, fixture(kind))
            val selected =
                navigation.navigateAzBreadcrumb(
                    opening,
                    PlayerUiAction.SelectAzBreadcrumb(opening.browserPath, emptyList(), inSearch = true),
                )
            assertTrue(selected.azSteps.isEmpty())
            assertEquals(emptyList<AzGroup>(), selected.azDraftSteps)
            assertEquals(PlayerScreen.AZ_SEARCH, selected.screen)
            assertTrue(navigation.containers[selected.browserPath]?.azSteps?.isEmpty() == true)
            val main = navigation.cancelAzSearch(selected).openMain()
            assertTrue(navigation.openCategory(main, kind.categoryId).azSteps.isEmpty())
        }
    }

    @Test
    fun ancestorBreadcrumbSavesItsPrefixWithoutRewritingTheChildFilter() {
        val navigation = PlayerNavigationState()
        val initial = fixture()
        val child =
            initial
                .currentNode()
                .children
                .first()
                .copy(children = rows)
        val category = initial.currentNode().copy(children = listOf(child) + rows.drop(1))
        val root = ready(initial.copy(root = initial.root.copy(children = listOf(category))))
        navigation.rememberHierarchy(root)
        val childState = root.copy(browserPath = root.browserPath + child.id, azSteps = listOf(AzGroup.LATIN_WZ))
        val opening =
            open(navigation, childState).copy(
                azBreadcrumbs =
                    azBreadcrumbScopes(root.root, childState.browserPath, childState.azSteps) {
                        navigation.containers[it]?.azSteps.orEmpty()
                    },
            )
        val selected =
            navigation.navigateAzBreadcrumb(
                opening,
                PlayerUiAction.SelectAzBreadcrumb(root.browserPath, steps.take(1), inSearch = true),
            )
        assertEquals(root.browserPath, selected.browserPath)
        assertEquals(steps.take(1), navigation.containers[root.browserPath]?.azSteps)
        assertEquals(childState.azSteps, navigation.containers[childState.browserPath]?.azSteps)
        val cancelled = navigation.cancelAzSearch(selected)
        assertEquals(root.browserPath, cancelled.browserPath)
        assertEquals(steps.take(1), cancelled.azSteps)
    }

    @Test
    fun acceptedPlaybackSavesTheDraftAndPreviewAnchorForEveryRootAndContentMode() {
        HierarchyRoot.entries.forEach { kind ->
            ContentMode.entries.forEach { mode ->
                val navigation = PlayerNavigationState()
                val opening = open(navigation, fixture(kind).copy(contentMode = mode))
                val preview =
                    ready(
                        opening.copy(
                            azDraftSteps = listOf(AzGroup.LATIN_AC),
                            selectorIndex = 2,
                            selectorOffset = 21,
                            focusedItemId = "Alpha",
                        ),
                    )
                val accepted = navigation.rememberPlaybackFilter(preview)
                assertEquals(preview.azDraftSteps, accepted.azSteps)
                assertEquals(PlayerScreen.AZ_SEARCH, accepted.screen)
                assertFalse(accepted.azAutoApplyPending)
                val saved = navigation.containers[preview.browserPath]
                assertEquals(preview.azDraftSteps, saved?.azSteps)
                assertEquals(2, saved?.selectorIndex)
                assertEquals(21, saved?.selectorOffset)
                assertEquals("Alpha", saved?.focusedItemId)
                val playback = navigation.cancelAzSearch(accepted).copy(screen = PlayerScreen.PLAYBACK)
                val returned = navigation.openCategory(playback.openMain(), kind.categoryId)
                assertEquals(preview.azDraftSteps, returned.azSteps)
                assertEquals(2, returned.selectorIndex)
                assertEquals(21, returned.selectorOffset)
            }
        }
    }

    @Test
    fun acceptedPlayFromMoreSavesBrowserPositionAndFailureKeepsTheMenu() {
        val navigation = PlayerNavigationState()
        val preview =
            ready(
                open(navigation, fixture()).copy(
                    azDraftSteps = listOf(AzGroup.LATIN_AC),
                    selectorIndex = 2,
                    selectorOffset = 21,
                    focusedItemId = "Alpha",
                ),
            )
        val menu = preview.copy(screen = PlayerScreen.ROW_ACTIONS, selectorIndex = 0, selectorOffset = 0, actionsItemId = "Alpha")
        val accepted = navigation.rememberPlaybackFilter(menu, preview)
        assertEquals(PlayerScreen.ROW_ACTIONS, accepted.screen)
        assertEquals("Alpha", accepted.actionsItemId)
        assertEquals(0, accepted.selectorIndex)
        assertEquals(2, navigation.containers[preview.browserPath]?.selectorIndex)
        assertEquals(21, navigation.containers[preview.browserPath]?.selectorOffset)
        val cancelled = navigation.cancelAzSearch(accepted)
        assertEquals(preview.azDraftSteps, cancelled.azSteps)
        assertEquals(2, cancelled.selectorIndex)
        assertEquals(21, cancelled.selectorOffset)
    }

    @Test
    fun savedFilterRetainsPendingPreviewOnlyForTheExactSource() {
        val navigation = PlayerNavigationState()
        val pending = navigation.backspaceAzSearch(open(navigation, fixture()))
        assertTrue(pending.azAppliedReady())
        val replacement = pending.root.copy(children = listOf(pending.currentNode().copy(children = rows.toMutableList())))
        val changed = pending.copy(root = replacement)
        assertNull(changed.azDisplaySnapshot())
        assertFalse(changed.azAppliedReady())
        assertTrue(changed.visibleChildren().isEmpty())
    }

    @Test
    fun disabledBackspaceDoesNotSaveOrCloseAndAutoSearchOffStillSaves() {
        val navigation = PlayerNavigationState()
        val opening = open(navigation, fixture().copy(settings = PlayerSettingsUiState(autoSearch = false)))
        val saved = navigation.backspaceAzSearch(navigation.backspaceAzSearch(opening))
        assertSame(saved, navigation.backspaceAzSearch(saved))
        assertEquals(PlayerScreen.AZ_SEARCH, saved.screen)
        assertFalse(saved.settings.autoSearch)
        assertTrue(navigation.roots[HierarchyRoot.ALBUMS]?.azSteps?.isEmpty() == true)
    }
}
