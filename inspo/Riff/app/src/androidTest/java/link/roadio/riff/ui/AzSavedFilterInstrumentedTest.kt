package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real local controls and navigation owner with a synthetic playback handoff, without media writes. */
class AzSavedFilterInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val navigation = PlayerNavigationState()
    private val rows =
        listOf("Ada", "Alpha", "Beta", "Delta", "Zulu").map {
            BrowserItem(it, it, BrowserKind.TRACK, playable = true)
        }
    private var state by mutableStateOf(initial())
    private var menuOpening: PlayerUiState? = null

    private fun initial(): PlayerUiState {
        val category =
            BrowserItem(HierarchyRoot.ALBUMS.categoryId, "Albums", BrowserKind.CATEGORY, icon = HierarchyRoot.ALBUMS.icon, children = rows)
        val ordinary =
            PlayerUiState(
                screen = PlayerScreen.HIERARCHY,
                root = BrowserItem("root", "Library", BrowserKind.ROOT, children = listOf(category)),
                hierarchyRoot = HierarchyRoot.ALBUMS,
                browserPath = listOf(category.id),
                azSteps = listOf(AzGroup.LATIN_DF),
                settings = PlayerSettingsUiState(autoSearch = false),
                nowPlaying = NowPlayingUiState(),
            )
        navigation.rememberHierarchy(ordinary)
        return ready(navigation.openAzSearch(ordinary))
    }

    private fun ready(value: PlayerUiState): PlayerUiState =
        value.copy(
            azBreadcrumbs =
                azBreadcrumbScopes(value.root, value.browserPath, value.azSteps) {
                    navigation.containers[it]?.azSteps.orEmpty()
                },
            azSnapshot = AzPreparedSection(value.currentNode().children).snapshot(value.azQuery()),
        )

    private fun dispatch(action: PlayerUiAction) {
        val next =
            when (action) {
                PlayerUiAction.AzBackspace -> {
                    navigation.backspaceAzSearch(state)
                }

                is PlayerUiAction.SelectAzBreadcrumb -> {
                    navigation.navigateAzBreadcrumb(state, action)
                }

                PlayerUiAction.OpenMain -> {
                    navigation.cancelAzSearch(state).openMain()
                }

                PlayerUiAction.TogglePlaybackSurface -> {
                    navigation.cancelAzSearch(state).copy(screen = PlayerScreen.PLAYBACK)
                }

                is PlayerUiAction.OpenCategory -> {
                    navigation.openAzSearch(navigation.openCategory(state, action.categoryId))
                }

                PlayerUiAction.OpenAzSearch -> {
                    if (state.screen ==
                        PlayerScreen.AZ_SEARCH
                    ) {
                        applyAzSearch(state)
                    } else {
                        navigation.openAzSearch(state)
                    }
                }

                is PlayerUiAction.OpenRowActions -> {
                    menuOpening = state
                    state.copy(screen = PlayerScreen.ROW_ACTIONS, actionsItemId = action.itemId)
                }

                is PlayerUiAction.Play, is PlayerUiAction.NavigateTo -> {
                    navigation.cancelAzSearch(navigation.rememberPlaybackFilter(state, menuOpening)).copy(screen = PlayerScreen.PLAYBACK)
                }

                is PlayerUiAction.RememberSelectorPosition -> {
                    state.copy(
                        pageSize = action.pageSize,
                        selectorIndex = action.index,
                        selectorOffset = action.offset,
                    )
                }

                is PlayerUiAction.RememberFocus -> {
                    state.copy(focusedItemId = action.itemId)
                }

                else -> {
                    state
                }
            }
        navigation.rememberHierarchy(next)
        state = ready(next)
    }

    private fun install(density: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = ::dispatch)
            }
        }
        compose.waitForIdle()
    }

    private fun homeAndReenter() {
        compose.onNodeWithContentDescription("Main menu").performClick()
        compose.onNodeWithTag("screen:MAIN").assertIsDisplayed()
        compose.onNodeWithContentDescription("Albums").performClick()
        compose.onNodeWithTag("screen:AZ_SEARCH").assertIsDisplayed()
    }

    @Test
    fun finalGroupPointerAndAccessibilityClicksSaveTheParentForHomeAndReentry() {
        install(1f)
        val draft = listOf(AzGroup.LATIN_AC, AzGroup.LATIN_GI)
        compose.runOnIdle { state = ready(state.copy(azDraftSteps = draft)) }
        compose
            .onNodeWithTag("az:search-crumb:0:2")
            .performScrollTo()
            .assertContentDescriptionEquals("Browse Albums; A-Z ABC", "GHI")
            .performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(PlayerScreen.AZ_SEARCH, state.screen)
            assertEquals(draft.take(1), state.azSteps)
            assertEquals(draft.take(1), state.azDraftSteps)
            assertEquals(draft.take(1), navigation.containers[state.browserPath]?.azSteps)
            assertFalse(state.azAutoApplyPending)
        }
        homeAndReenter()
        compose.runOnIdle { assertEquals(draft.take(1), state.azDraftSteps) }
        compose
            .onNodeWithTag("az:search-crumb:0:1")
            .performScrollTo()
            .assertContentDescriptionEquals("Browse Albums; no A-Z filter", "ABC")
            .performClick()
        compose.runOnIdle {
            assertTrue(state.azSteps.isEmpty())
            assertEquals(emptyList<AzGroup>(), state.azDraftSteps)
            assertEquals(PlayerScreen.AZ_SEARCH, state.screen)
        }
        homeAndReenter()
        compose.runOnIdle {
            assertTrue(state.azSteps.isEmpty())
            assertEquals(rows, state.visibleChildren())
        }
    }

    @Test
    fun finalGroupAfterClosingSearchClearsTheFilterAndSurvivesHome() {
        install(1f)
        compose.runOnIdle { state = ready(state.copy(azDraftSteps = listOf(AzGroup.LATIN_GI))) }
        compose.onNodeWithContentDescription("Apply and close A-Z search").performClick()
        compose.runOnIdle {
            assertEquals(PlayerScreen.HIERARCHY, state.screen)
            assertEquals(listOf(AzGroup.LATIN_GI), state.azSteps)
            assertEquals(null, state.azDraftSteps)
        }
        compose
            .onNodeWithTag("az:crumb:0:1")
            .performScrollTo()
            .assertContentDescriptionEquals("Browse Albums; no A-Z filter", "GHI")
            .performTouchInput { click() }
        compose.runOnIdle {
            assertTrue(state.azSteps.isEmpty())
            assertEquals(null, state.azDraftSteps)
            assertEquals(PlayerScreen.HIERARCHY, state.screen)
            assertEquals(emptyList<AzGroup>(), navigation.containers[state.browserPath]?.azSteps)
        }
        homeAndReenter()
        compose.runOnIdle {
            assertTrue(state.azSteps.isEmpty())
            assertEquals(rows, state.visibleChildren())
        }
    }

    @Test
    fun backspaceThenHomeKeepsTheEmptySavedFilter() {
        install(1f)
        compose.onNodeWithContentDescription("Remove last A-Z step").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(state.azSteps.isEmpty())
            assertEquals(emptyList<AzGroup>(), state.azDraftSteps)
        }
        homeAndReenter()
        compose.runOnIdle {
            assertTrue(state.azSteps.isEmpty())
            assertEquals(rows, state.visibleChildren())
        }
    }

    @Test
    fun narrowRootBreadcrumbThenHomeKeepsTheEmptySavedFilter() {
        install(narrowAzFixtureDensity())
        compose.onNodeWithTag("az:search-crumb:0:0").performScrollTo().performClick()
        homeAndReenter()
        compose.runOnIdle {
            assertTrue(state.azSteps.isEmpty())
            assertFalse(state.azAutoApplyPending)
        }
    }

    @Test
    fun visibleTrackPlayRetainsTheUnappliedDraftForReentry() {
        install(1f)
        compose.runOnIdle { state = ready(state.copy(azDraftSteps = listOf(AzGroup.LATIN_AC))) }
        compose.onNodeWithContentDescription("Ada; Albums;", substring = true).performClick()
        compose.onNodeWithTag("screen:PLAYBACK").assertIsDisplayed()
        compose.runOnIdle {
            state = ready(state.openMain())
        }
        compose.onNodeWithContentDescription("Albums").performClick()
        compose.runOnIdle { assertEquals(listOf(AzGroup.LATIN_AC), state.azSteps) }
    }

    @Test
    fun morePlayRetainsTheUnappliedDraftForReentry() {
        install(1f)
        compose.runOnIdle { state = ready(state.copy(azDraftSteps = listOf(AzGroup.LATIN_AC))) }
        compose.onNodeWithContentDescription("Actions for Ada").performClick()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithTag("screen:PLAYBACK").assertIsDisplayed()
        compose.runOnIdle { state = ready(state.openMain()) }
        compose.onNodeWithContentDescription("Albums").performClick()
        compose.runOnIdle { assertEquals(listOf(AzGroup.LATIN_AC), state.azSteps) }
    }
}
