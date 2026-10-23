package link.roadio.riff.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class AzBreadcrumbInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private var lastAction: PlayerUiAction? = null

    private val filters = listOf(AzGroup.LATIN_AC, AzGroup.LATIN_DF)
    private var presented by mutableStateOf(fixture(HierarchyRoot.FOLDERS, false))

    private fun fixture(
        kind: HierarchyRoot,
        descendant: Boolean,
    ): PlayerUiState {
        val child = BrowserItem("child", "Ad synthetic object with a deliberately long name", BrowserKind.FOLDER)
        val category = BrowserItem(kind.categoryId, kind.label, BrowserKind.CATEGORY, icon = kind.icon, children = listOf(child))
        val root = BrowserItem("root", "Library", BrowserKind.ROOT, children = listOf(category))
        val path = if (descendant) listOf(category.id, child.id) else listOf(category.id)
        val steps = if (descendant) emptyList() else filters
        val state =
            PlayerUiState(
                screen = PlayerScreen.HIERARCHY,
                root = root,
                browserPath = path,
                hierarchyRoot = kind,
                azSteps = steps,
                azBreadcrumbs = azBreadcrumbScopes(root, path, steps) { filters },
                nowPlaying = NowPlayingUiState(),
            )
        return state.copy(azSnapshot = AzPreparedSection(state.currentNode().children).snapshot(state.azQuery()))
    }

    private fun install(
        density: Float = 1f,
        rtl: Boolean = false,
        immersive: Boolean = false,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        compose.setContent {
            if (immersive) {
                val view = LocalView.current
                val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().first()
                SideEffect { activity.window.applyRiffImmersiveMode() }
            }
            CompositionLocalProvider(
                LocalDensity provides Density(density),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                PlayerComposeUi(presented, config, CalibrationSessionState(), context, dispatch = { action ->
                    lastAction = action
                    if (action is PlayerUiAction.SelectAzBreadcrumb ||
                        action == PlayerUiAction.NavigateParent ||
                        action == PlayerUiAction.OpenAzSearch ||
                        action == PlayerUiAction.OpenMain ||
                        action == PlayerUiAction.TogglePlaybackSurface
                    ) {
                        val next =
                            when (action) {
                                is PlayerUiAction.SelectAzBreadcrumb -> {
                                    selectAzBreadcrumb(presented, action)
                                }

                                PlayerUiAction.NavigateParent -> {
                                    restoreHierarchyContainer(
                                        presented,
                                        presented.browserPath.dropLast(1),
                                        filters,
                                    )
                                }

                                PlayerUiAction.OpenAzSearch -> {
                                    if (presented.screen == PlayerScreen.AZ_SEARCH) {
                                        applyAzSearch(presented)
                                    } else {
                                        presented.copy(
                                            screen = PlayerScreen.AZ_SEARCH,
                                            azDraftSteps = presented.azSteps,
                                            azAutoEntryPending = false,
                                        )
                                    }
                                }

                                PlayerUiAction.OpenMain -> {
                                    presented.copy(screen = PlayerScreen.MAIN, azDraftSteps = null)
                                }

                                PlayerUiAction.TogglePlaybackSurface -> {
                                    presented.copy(screen = PlayerScreen.PLAYBACK, azDraftSteps = null)
                                }
                            }
                        presented =
                            next.copy(
                                azBreadcrumbs = azBreadcrumbScopes(next.root, next.browserPath, next.azSteps) { filters },
                                azSnapshot = AzPreparedSection(next.currentNode().children).snapshot(next.azQuery()),
                            )
                    }
                })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun foldersAndArtistsExposeAppliedPrefixesAfterClosingSearchAndDescent() {
        install()
        listOf(HierarchyRoot.FOLDERS, HierarchyRoot.ARTISTS).forEach { kind ->
            compose.runOnIdle { presented = fixture(kind, true) }
            compose.onNodeWithTag("az:crumb:0:1").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(listOf(kind.categoryId), presented.browserPath)
                assertEquals(filters.take(1), presented.azSteps)
                assertEquals(PlayerScreen.HIERARCHY, presented.screen)
                assertEquals(true, presented.azAutoEntryPending)
            }
            compose.onNodeWithTag("az:crumb:0:0").performScrollTo().performClick()
            compose.runOnIdle { assertTrue(presented.azSteps.isEmpty()) }
            compose.onNodeWithTag("az:crumb:0:0").assertIsDisplayed()
        }
    }

    @Test
    fun narrowFinalAndEarlierPrefixesHaveTheSameDestinationForEveryRoot() {
        val draft = listOf(AzGroup.LATIN_AC, AzGroup.LATIN_GI)
        install(narrowAzFixtureDensity())
        HierarchyRoot.entries.forEach { kind ->
            fun reset() {
                compose.runOnIdle {
                    val next = fixture(kind, false).copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = draft)
                    presented = next.copy(azSnapshot = AzPreparedSection(next.currentNode().children).snapshot(next.azQuery()))
                }
            }
            reset()
            compose.onNodeWithTag("az:search-crumb:0:1").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(draft.take(1), presented.azDraftSteps) }
            reset()
            compose.onNodeWithTag("az:search-crumb:0:2").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(PlayerScreen.AZ_SEARCH, presented.screen)
                assertEquals(listOf(kind.categoryId), presented.browserPath)
                assertEquals(draft.take(1), presented.azSteps)
                assertEquals(draft.take(1), presented.azDraftSteps)
                assertEquals(false, presented.azAutoApplyPending)
            }
            compose.onNodeWithTag("az:search-crumb:0:1").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(emptyList<AzGroup>(), presented.azSteps)
                assertEquals(emptyList<AzGroup>(), presented.azDraftSteps)
                assertEquals(PlayerScreen.AZ_SEARCH, presented.screen)
            }
            compose.onNodeWithTag("az:search-crumb:0:0").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(listOf(kind.categoryId), presented.browserPath) }
        }
    }

    @Test
    fun descendantFinalGroupPreservesTheIndependentAncestorFilter() {
        val draft = listOf(AzGroup.LATIN_AC, AzGroup.LATIN_GI)
        val initial = fixture(HierarchyRoot.FOLDERS, true).copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = draft)
        presented = initial.copy(azSnapshot = AzPreparedSection(initial.currentNode().children).snapshot(initial.azQuery()))
        install()
        compose.onNodeWithTag("az:search-crumb:1:2").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(initial.browserPath, presented.browserPath)
            assertEquals(draft.take(1), presented.azDraftSteps)
            assertEquals(filters, presented.azBreadcrumbs.first().steps)
        }
        compose.onNodeWithTag("az:search-crumb:0:2").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(initial.browserPath.take(1), presented.browserPath)
            assertEquals(filters, presented.azDraftSteps)
            assertEquals(filters, presented.azSteps)
            assertEquals(PlayerScreen.AZ_SEARCH, presented.screen)
        }
    }

    @Test
    fun closedSearchFinalGroupReturnsToTheParentPrefixForEveryRoot() {
        install(narrowAzFixtureDensity())
        HierarchyRoot.entries.forEach { kind ->
            compose.runOnIdle { presented = fixture(kind, false) }
            compose.onNodeWithTag("az:crumb:0:2").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(filters.take(1), presented.azSteps)
                assertEquals(null, presented.azDraftSteps)
                assertEquals(PlayerScreen.HIERARCHY, presented.screen)
                assertEquals(listOf(kind.categoryId), presented.browserPath)
                assertEquals(true, presented.azAutoEntryPending)
            }
            compose.onNodeWithTag("az:crumb:0:1").performScrollTo().performClick()
            compose.runOnIdle { assertTrue(presented.azSteps.isEmpty()) }
        }
    }

    private fun assertChromeAndPlateAlignment(density: Float) {
        val chrome = compose.onNodeWithTag("az:browser-chrome").fetchSemanticsNode().boundsInRoot
        val plates = compose.onNodeWithTag("az:browser-plates").fetchSemanticsNode().boundsInRoot
        val flip = compose.onNodeWithContentDescription("Switch to playback").fetchSemanticsNode().boundsInRoot
        val main = compose.onNodeWithContentDescription("Main menu").fetchSemanticsNode().boundsInRoot
        val end = compose.onNodeWithTag("az:crumb:0:2").fetchSemanticsNode().boundsInRoot
        assertEquals(chrome.left, flip.left, 1f)
        assertEquals(flip.right + 24f * density, main.left, 1f)
        assertEquals(chrome.left + 416f * density, plates.left, 1f)
        assertEquals(chrome.right, plates.right, 1f)
        assertEquals(plates.right, end.right, 1f)
    }

    @Test
    fun navigationStaysLeftWhileOnlyFilterPlatesAlignRight() {
        install()
        assertChromeAndPlateAlignment(1f)
        val original = compose.onNodeWithContentDescription("Main menu").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("az:crumb:0:0").performScrollTo().performClick()
        val root = compose.onNodeWithTag("az:crumb:0:0").fetchSemanticsNode().boundsInRoot
        val plates = compose.onNodeWithTag("az:browser-plates").fetchSemanticsNode().boundsInRoot
        assertEquals(plates.right, root.right, 1f)
        assertEquals(original, compose.onNodeWithContentDescription("Main menu").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun searchUsesNoPlaceholderAndSingleAzCloseControl() {
        val initial =
            fixture(HierarchyRoot.ARTISTS, true).copy(
                screen = PlayerScreen.AZ_SEARCH,
                azDraftSteps = emptyList(),
            )
        presented =
            initial.copy(
                azSnapshot = AzPreparedSection(initial.currentNode().children).snapshot(initial.azQuery()),
            )
        install()
        compose.onNodeWithTag("az:search-crumb:1:0").assertDoesNotExist()
        compose.onNodeWithTag("az:crumb:0:0").assertDoesNotExist()
        compose.onAllNodesWithText("Artists", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("az:browser-chrome").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Apply A-Z search").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Apply and close A-Z search").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Cancel A-Z search").assertCountEquals(0)
        compose.onNodeWithTag("az:group:EMPTY").assertDoesNotExist()
        compose.onNodeWithTag("az:group:BACKSPACE").assertExists()
        compose.onNodeWithTag("az:group:OTHER").assertDoesNotExist()
        compose.onNodeWithTag("az:group:END_NAME").assertDoesNotExist()
        compose.onNodeWithTag("az:search-crumb:0:1").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(PlayerScreen.AZ_SEARCH, presented.screen)
            assertEquals(listOf(HierarchyRoot.ARTISTS.categoryId), presented.browserPath)
            assertEquals(filters.take(1), presented.azSteps)
            assertEquals(filters.take(1), presented.azDraftSteps)
        }
        compose.onNodeWithTag("az:search-crumb:0:0").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(emptyList<AzGroup>(), presented.azDraftSteps)
            assertEquals(emptyList<AzGroup>(), presented.azSteps)
        }
        compose.runOnIdle {
            presented = presented.copy(selectorOffset = 19, focusedItemId = "child")
        }
        compose.onNodeWithContentDescription("Apply and close A-Z search").performClick()
        compose.runOnIdle {
            assertEquals(PlayerScreen.HIERARCHY, presented.screen)
            assertEquals(emptyList<AzGroup>(), presented.azSteps)
            assertEquals(19, presented.selectorOffset)
            assertEquals("child", presented.focusedItemId)
        }
    }

    @Test
    fun modalHasOneTopBreadcrumbAndKeepsChromePixelsUndimmed() {
        install(immersive = true)
        compose.waitUntil(timeoutMillis = 5000) {
            compose
                .onNodeWithTag("az:browser-chrome")
                .fetchSemanticsNode()
                .boundsInRoot.top == 24f
        }
        val beforeBounds = compose.onNodeWithTag("az:browser-chrome").fetchSemanticsNode().boundsInRoot
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation

        val expected =
            compose
                .onNodeWithTag("az:browser-chrome")
                .captureToImage()
                .toPixelMap()[0, 0]
                .toArgb()

        fun settledScreenshot(): android.graphics.Bitmap {
            var result: android.graphics.Bitmap? = null
            compose.waitUntil(timeoutMillis = 5000) {
                result?.recycle()
                result = automation.takeScreenshot()
                result?.getPixel(beforeBounds.left.roundToInt(), beforeBounds.top.roundToInt()) == expected
            }
            return requireNotNull(result)
        }
        val before = settledScreenshot()
        compose.runOnIdle {
            val next = presented.copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = presented.azSteps)
            presented = next.copy(azSnapshot = AzPreparedSection(next.currentNode().children).snapshot(next.azQuery()))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("az:crumb:0:0").assertDoesNotExist()
        compose.onAllNodesWithText("Folders", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("ABC", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("DEF", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("az:browser-chrome").assertCountEquals(1)
        val afterBounds = compose.onNodeWithTag("az:browser-chrome").fetchSemanticsNode().boundsInRoot
        assertEquals(beforeBounds, afterBounds)
        val after = settledScreenshot()

        fun region(
            bitmap: android.graphics.Bitmap,
            top: Int,
            bottom: Int,
        ): IntArray {
            val left = beforeBounds.left.roundToInt()
            val width = beforeBounds.width.roundToInt()
            return IntArray(width * (bottom - top)).also {
                bitmap.getPixels(it, 0, width, left, top, width, bottom - top)
            }
        }
        try {
            assertArrayEquals(
                "Both backdrop layers must leave the entire top chrome unchanged",
                region(before, beforeBounds.top.roundToInt(), beforeBounds.bottom.roundToInt()),
                region(after, afterBounds.top.roundToInt(), afterBounds.bottom.roundToInt()),
            )
            val gridTop =
                compose
                    .onNodeWithTag("az:grid")
                    .fetchSemanticsNode()
                    .boundsInRoot.top
                    .roundToInt()
            val top = beforeBounds.bottom.roundToInt() + 4
            assertTrue(gridTop > top)
            assertTrue(
                "The live list below chrome must still be dimmed",
                !region(before, top, gridTop).contentEquals(region(after, top, gridTop)),
            )
        } finally {
            before.recycle()
            after.recycle()
        }
        compose.onNodeWithContentDescription("Main menu").assertIsEnabled()
    }

    @Test
    fun narrowRtlSearchKeepsSingleTopStripAndBottomGrid() {
        val next = fixture(HierarchyRoot.FOLDERS, false).copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = filters)
        presented = next.copy(azSnapshot = AzPreparedSection(next.currentNode().children).snapshot(next.azQuery()))
        val density = narrowAzFixtureDensity()
        install(density, true)
        compose.onNodeWithTag("az:crumb:0:0").assertDoesNotExist()
        compose.onAllNodesWithTag("az:browser-chrome").assertCountEquals(1)
        val header = compose.onNodeWithTag("az:browser-chrome").fetchSemanticsNode().boundsInRoot
        assertEquals(24f * density, header.top, 1f)
        assertEquals(80f * density, header.height, 1f)
        val first = compose.onNodeWithTag("az:grid").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top >= header.bottom + 24f * density)
        listOf("0:0", "0:1", "0:2").forEach { id ->
            compose.onNodeWithTag("az:search-crumb:$id").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun foregroundUpRestoresParentAndAzAppliesWithoutLosingAnchor() {
        val next = fixture(HierarchyRoot.ARTISTS, true).copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = listOf(AzGroup.CYRILLIC_AG))
        presented = next.copy(azSnapshot = AzPreparedSection(next.currentNode().children).snapshot(next.azQuery()))
        install()
        compose.onNodeWithContentDescription("Parent").performClick()
        compose.runOnIdle {
            assertEquals(PlayerScreen.AZ_SEARCH, presented.screen)
            assertEquals(listOf(HierarchyRoot.ARTISTS.categoryId), presented.browserPath)
            assertEquals(filters, presented.azDraftSteps)
            assertEquals(filters, presented.azSteps)
            assertEquals(false, presented.azAutoApplyPending)
        }
        compose.onNodeWithContentDescription("Parent; unavailable").assertIsNotEnabled()
        compose.runOnIdle { presented = presented.copy(selectorOffset = 23, focusedItemId = "child") }
        compose.onNodeWithContentDescription("Apply and close A-Z search").performClick()
        compose.runOnIdle {
            assertEquals(PlayerScreen.HIERARCHY, presented.screen)
            assertEquals(filters, presented.azSteps)
            assertEquals(null, presented.azDraftSteps)
            assertEquals(23, presented.selectorOffset)
            assertEquals("child", presented.focusedItemId)
            assertEquals(false, presented.azAutoEntryPending)
        }
        compose.onNodeWithTag("az:group:EMPTY").assertDoesNotExist()
    }

    @Test
    fun foregroundHomeAndPlaybackSendTypedNavigation() {
        install()
        listOf(
            "Main menu" to PlayerUiAction.OpenMain,
            "Switch to playback" to PlayerUiAction.TogglePlaybackSurface,
        ).forEach { (label, control) ->
            compose.runOnIdle {
                val next = fixture(HierarchyRoot.FOLDERS, false).copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = filters)
                presented = next.copy(azSnapshot = AzPreparedSection(next.currentNode().children).snapshot(next.azQuery()))
            }
            compose.onNodeWithContentDescription(label).performClick()
            compose.runOnIdle {
                assertEquals(control, lastAction)
                assertEquals(null, presented.azDraftSteps)
                assertEquals(if (control == PlayerUiAction.OpenMain) PlayerScreen.MAIN else PlayerScreen.PLAYBACK, presented.screen)
            }
        }
    }

    @Test
    fun narrowRtlLongBreadcrumbScrollsWithoutShrinkingTargets() {
        presented = fixture(HierarchyRoot.FOLDERS, true)
        val density = narrowAzFixtureDensity()
        install(density, true)
        assertChromeAndPlateAlignment(density)
        compose.onNodeWithTag("az:crumb:1:0").assertDoesNotExist()
        listOf("0:0", "0:1", "0:2").forEach { id ->
            val node = compose.onNodeWithTag("az:crumb:$id").performScrollTo()
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$id visible bounds: $bounds", bounds.width >= 80f * density)
            assertEquals(80f * density, bounds.height, 1f)
        }
    }
}
