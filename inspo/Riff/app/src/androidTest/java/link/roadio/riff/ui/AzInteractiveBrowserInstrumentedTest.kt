package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Exercises the real pointer path with synthetic rows; never changes user data. */
class AzInteractiveBrowserInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val rows =
        (0 until 30).map {
            BrowserItem("fixture:$it", "Fixture $it", BrowserKind.FOLDER)
        }
    private var state by mutableStateOf(
        PlayerUiState(
            screen = PlayerScreen.AZ_SEARCH,
            root = BrowserItem("root", "Fixture", BrowserKind.FOLDER, children = rows),
            nowPlaying = NowPlayingUiState(),
            azDraftSteps = emptyList(),
            azSnapshot = AzPreparedSection(rows).snapshot(AzSearchQuery(rows, emptyList(), emptyList())),
        ),
    )
    private val actions = mutableListOf<PlayerUiAction>()
    private var menuOpening: PlayerUiState? = null

    private fun install(
        density: Float? = null,
        configOverride: UiConfig? = null,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = configOverride ?: UiConfig.defaults(context)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides (density?.let { Density(it) } ?: LocalDensity.current)) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context) { action ->
                    actions += action
                    state =
                        when (action) {
                            is PlayerUiAction.RememberSelectorPosition -> {
                                state.copy(
                                    selectorIndex = action.index,
                                    selectorOffset = action.offset,
                                    pageSize = action.pageSize,
                                )
                            }

                            is PlayerUiAction.OpenRowActions -> {
                                menuOpening = state
                                state.copy(screen = PlayerScreen.ROW_ACTIONS, actionsItemId = action.itemId)
                            }

                            PlayerUiAction.CloseRowActions -> {
                                resumeHierarchyAfterMenu(state, menuOpening)
                            }

                            else -> {
                                state
                            }
                        }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun adjustmentCoversWholeRowsAndClearsOnCloseWithoutChangingChromeOrKeys() {
        verifyWholeRowAdjustment(UiConfig.defaults(InstrumentationRegistry.getInstrumentation().targetContext), 32)
    }

    @Test
    fun fileOnlyOverrideChangesTheActualWholeRowAdjustment() {
        verifyWholeRowAdjustment(UiConfig.fromUserText("""{"skins":{"default":{"behavior":{"azRgbSubtract":16}}}}"""), 16)
    }

    private fun verifyWholeRowAdjustment(
        config: UiConfig,
        subtract: Int,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(subtract, config.int("behavior.azRgbSubtract"))
        val captures = File(context.getExternalFilesDir(null), "az-rgb-config/$subtract").apply { mkdirs() }
        state = state.copy(screen = PlayerScreen.HIERARCHY, azDraftSteps = null)
        install(configOverride = config)
        listOf(ColorScheme.DAY, ColorScheme.NIGHT).forEach { scheme ->
            compose.runOnIdle {
                state =
                    state.copy(
                        screen = PlayerScreen.HIERARCHY,
                        azDraftSteps = null,
                        settings = state.settings.copy(colorScheme = scheme),
                    )
            }
            val baselineNode = compose.onNodeWithTag("screen:HIERARCHY")
            val origin = baselineNode.fetchSemanticsNode().boundsInRoot.topLeft
            val ordinary = baselineNode.captureToImage()
            File(captures, "${scheme.name.lowercase()}-ordinary.png").outputStream().use {
                ordinary.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val before = ordinary.toPixelMap()
            val row =
                compose
                    .onNodeWithTag("selector:row:fixture:0")
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .translate(-origin)
            val chrome =
                compose
                    .onNodeWithTag("az:browser-chrome")
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .translate(-origin)
            compose.runOnIdle { state = state.copy(screen = PlayerScreen.AZ_SEARCH, azDraftSteps = emptyList()) }
            val searchNode = compose.onNodeWithTag("screen:AZ_SEARCH")
            val adjusted = searchNode.captureToImage()
            File(captures, "${scheme.name.lowercase()}-adjusted.png").outputStream().use {
                adjusted.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val after = adjusted.toPixelMap()
            val resolved = config.themed(scheme == ColorScheme.NIGHT)
            val palette = if (resolved.night) UiPalette.night(resolved) else UiPalette.day(resolved)
            var maximumError = 0f
            var changed = 0
            for (y in row.top.toInt() until row.bottom.toInt()) {
                for (x in row.left.toInt() until row.right.toInt()) {
                    val original = before[x, y]
                    val expected =
                        Color(
                            (original.red - subtract / 255f).coerceAtLeast(0f),
                            (original.green - subtract / 255f).coerceAtLeast(0f),
                            (original.blue - subtract / 255f).coerceAtLeast(0f),
                            original.alpha,
                        )
                    val actual = after[x, y]
                    maximumError =
                        maxOf(
                            maximumError,
                            abs(expected.red - actual.red),
                            abs(expected.green - actual.green),
                            abs(expected.blue - actual.blue),
                        )
                    if (before[x, y] != actual) changed++
                }
            }
            assertTrue("Whole row must receive one fixed RGB subtraction in $scheme: $maximumError", maximumError <= 3f / 255f)
            assertTrue("Search must visibly adjust the row in $scheme", changed > 100)
            for (y in chrome.top.toInt() until chrome.bottom.toInt()) {
                for (x in chrome.left.toInt() until chrome.right.toInt()) {
                    assertEquals("Top chrome must stay unchanged in $scheme", before[x, y], after[x, y])
                }
            }
            val active =
                compose
                    .onNodeWithTag("az:group:LATIN_DF")
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .translate(-origin)
            val keyPlate = after[(active.left + active.width * 0.2f).toInt(), (active.top + active.height * 0.08f).toInt()]
            val expectedPlate = Color(palette.surface)
            assertEquals(expectedPlate.red, keyPlate.red, 3f / 255f)
            assertEquals(expectedPlate.green, keyPlate.green, 3f / 255f)
            assertEquals(expectedPlate.blue, keyPlate.blue, 3f / 255f)
            compose.runOnIdle { state = state.copy(screen = PlayerScreen.HIERARCHY, azDraftSteps = null) }
            val closed = compose.onNodeWithTag("screen:HIERARCHY").captureToImage().toPixelMap()
            for (y in row.top.toInt() until row.bottom.toInt()) {
                for (x in row.left.toInt() until row.right.toInt()) {
                    assertEquals("Closing search must restore the row in $scheme", before[x, y], closed[x, y])
                }
            }
        }
    }

    @Test
    fun rowsAcceptReactionPointersAndMoreResumesSameSearch() {
        install()
        listOf("Ban", "Like", "Dislike").forEach { label ->
            compose.onNodeWithContentDescription("$label Fixture 0").performTouchInput { click() }
            compose.runOnIdle {
                assertTrue(actions.any { it is PlayerUiAction.SetReaction && it.itemId == rows[0].reactionKey })
                actions.clear()
            }
        }
        compose.onNodeWithTag("selector:list").performScrollToIndex(8)
        compose.waitForIdle()
        val index = state.selectorIndex
        val offset = state.selectorOffset
        compose.onNodeWithContentDescription("Actions for Fixture 8").performTouchInput { click() }
        compose.onNodeWithTag("screen:ROW_ACTIONS").assertExists()
        compose.runOnIdle { state = resumeHierarchyAfterMenu(state, menuOpening) }
        compose.onNodeWithTag("screen:AZ_SEARCH").assertExists()
        compose.onAllNodesWithTag("selector:list").assertCountEquals(1)
        compose.runOnIdle {
            assertEquals(index, state.selectorIndex)
            assertEquals(offset, state.selectorOffset)
            assertEquals(emptyList<AzGroup>(), state.azDraftSteps)
        }
    }

    @Test
    fun dimDoesNotInterceptSwipeAndLastRowActionsAreReachableAboveKeypad() {
        install()
        val listBounds = compose.onNodeWithTag("selector:list").fetchSemanticsNode().boundsInRoot
        val layerBounds = compose.onNodeWithTag("az:gap-shield").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("selector:list").performTouchInput {
            swipe(Offset(width / 2f, layerBounds.top - listBounds.top - 30f), Offset(width / 2f, 20f))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(state.selectorIndex > 0 || state.selectorOffset > 0) }
        compose.onNodeWithTag("selector:list").performScrollToIndex(29)
        val action = compose.onNodeWithContentDescription("Actions for Fixture 29")
        val bounds = action.fetchSemanticsNode().boundsInRoot
        val keypad = compose.onNodeWithTag("az:layer").fetchSemanticsNode().boundsInRoot
        val minimum =
            if (compose
                    .onNodeWithTag("az:group:BACKSPACE")
                    .fetchSemanticsNode()
                    .boundsInRoot.height >= 100f
            ) {
                100f
            } else {
                76f
            }
        assertTrue(bounds.height >= minimum)
        assertTrue("Last action $bounds overlaps keypad $keypad or its gap", bounds.bottom <= keypad.top - 24f)
        action.performTouchInput { click() }
        compose.onNodeWithTag("screen:ROW_ACTIONS").assertExists()
    }

    @Test
    fun dragPublishesOnlyTheSettledSelectorPosition() {
        install()
        val listBounds = compose.onNodeWithTag("selector:list").fetchSemanticsNode().boundsInRoot
        val layerBounds = compose.onNodeWithTag("az:gap-shield").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { actions.clear() }

        compose.onNodeWithTag("selector:list").performTouchInput {
            swipe(Offset(width / 2f, layerBounds.top - listBounds.top - 30f), Offset(width / 2f, 20f))
        }
        compose.waitForIdle()

        compose.runOnIdle {
            val reports = actions.filterIsInstance<PlayerUiAction.RememberSelectorPosition>()
            assertEquals(1, reports.size)
            assertTrue(reports.single().index > 0 || reports.single().offset > 0)
        }
    }

    @Test
    fun gapTapLongPressAndSwipeDoNothingButKeyClicksStillWork() {
        install()

        fun bounds(name: String) = compose.onNodeWithTag("az:group:$name").fetchSemanticsNode().boundsInRoot
        val wide =
            compose
                .onNodeWithTag("screen:AZ_SEARCH")
                .fetchSemanticsNode()
                .boundsInRoot.width >= 800f
        val firstName = if (wide) "SYMBOL" else "DIGIT_67"
        val first = bounds(firstName)
        val neighbor = bounds(if (wide) "DIGIT_01" else "DIGIT_89")
        val adjacentRow = bounds(AZ_LATIN_GROUPS.first().name)
        val screen = compose.onNodeWithTag("screen:AZ_SEARCH")
        val origin = screen.fetchSemanticsNode().boundsInRoot.topLeft
        val gaps =
            listOf(
                Offset((first.right + neighbor.left) / 2, first.center.y),
                Offset(first.center.x, (adjacentRow.bottom + first.top) / 2),
                Offset(first.center.x, first.top - 12f),
            )
        val anchor = state.selectorIndex to state.selectorOffset
        val keyOrigin = first.topLeft
        gaps.forEach { absolute ->
            compose.runOnIdle { actions.clear() }
            val point = absolute - origin
            screen.performTouchInput { click(point) }
            screen.performTouchInput {
                down(point)
                advanceEventTime(800)
                up()
            }
            screen.performTouchInput { swipe(point, point + Offset(60f, -90f)) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue("Gap dispatched $actions", actions.isEmpty())
                assertEquals(anchor, state.selectorIndex to state.selectorOffset)
            }
            assertEquals(keyOrigin, bounds(firstName).topLeft)
        }
        compose.onNodeWithTag("az:gap-shield").assertHasNoClickAction()
        compose.onNodeWithTag("az:group:LATIN_DF").performTouchInput { click() }
        compose.runOnIdle { assertTrue(actions.contains(PlayerUiAction.SelectAzGroup(AzGroup.LATIN_DF))) }
    }

    @Test
    fun underlyingRowsShowThroughGapsAndInactiveKeysButNotActiveKeys() {
        fun replaceLabels(label: String) {
            val content = rows.map { it.copy(label = label) }
            val next = state.copy(root = state.root.copy(children = content))
            state = next.copy(azSnapshot = AzPreparedSection(content).snapshot(next.azQuery()))
        }
        replaceLabels("M".repeat(80))
        install()
        val screen = compose.onNodeWithTag("screen:AZ_SEARCH")
        val origin = screen.fetchSemanticsNode().boundsInRoot.topLeft
        val activeNode = compose.onNodeWithTag("az:group:LATIN_MO")
        val wide = screen.fetchSemanticsNode().boundsInRoot.width >= 800f
        val visibleKeys =
            if (wide) {
                listOf("SYMBOL", "DIGIT_01", "DIGIT_23", "DIGIT_45", "DIGIT_67", "DIGIT_89", "BACKSPACE") +
                    AZ_LATIN_GROUPS.map { it.name }
            } else {
                listOf("DIGIT_67", "DIGIT_89", "BACKSPACE") + AZ_LATIN_GROUPS.drop(4).map { it.name }
            }
        val keys =
            visibleKeys.associateWith { name ->
                compose.onNodeWithTag("az:group:$name").fetchSemanticsNode().boundsInRoot
            }
        val inactive = keys.filterKeys { it != "LATIN_MO" }.values.map { it.deflate(20f) }
        val layer = compose.onNodeWithTag("az:layer").fetchSemanticsNode().boundsInRoot
        val before = screen.captureToImage().toPixelMap()
        val plateBefore = activeNode.captureToImage().toPixelMap()
        compose.runOnIdle { replaceLabels("M") }
        val after = screen.captureToImage().toPixelMap()
        val plateAfter = activeNode.captureToImage().toPixelMap()
        var changed = 0
        var inactiveChanged = 0
        for (x in (layer.left - origin.x).toInt() until (layer.right - origin.x).toInt()) {
            for (y in (layer.top - origin.y).toInt() until (layer.bottom - origin.y).toInt()) {
                if (before[x, y] != after[x, y]) {
                    val point = Offset(x + origin.x, y + origin.y)
                    if (keys.values.none { it.contains(point) }) changed++
                    if (inactive.any { it.contains(point) }) inactiveChanged++
                }
            }
        }
        assertTrue("Gaps do not show the changing underlying row text", changed > 10)
        assertTrue("Inactive keys hide the changing underlying row text", inactiveChanged > 10)
        for (x in 20 until plateBefore.width - 20) {
            for (y in 20 until plateBefore.height - 20) {
                assertEquals(plateBefore[x, y], plateAfter[x, y])
            }
        }
    }

    @Test
    fun translucentUnavailableKeysBlockPointerAndAccessibilityActions() {
        install()
        val wide =
            compose
                .onNodeWithTag("screen:AZ_SEARCH")
                .fetchSemanticsNode()
                .boundsInRoot.width >= 800f
        val inactive = if (wide) listOf("SYMBOL", "DIGIT_01", "BACKSPACE") else listOf("DIGIT_67", "DIGIT_89", "BACKSPACE")
        inactive.forEach { name ->
            val key = compose.onNodeWithTag("az:group:$name").assertIsNotEnabled()
            val anchor = state.selectorIndex to state.selectorOffset
            compose.runOnIdle { actions.clear() }
            key.performTouchInput { click() }
            key.performTouchInput {
                down(center)
                advanceEventTime(800)
                up()
            }
            key.performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.runOnIdle {
                assertTrue("Unavailable key dispatched $actions", actions.isEmpty())
                assertEquals(anchor, state.selectorIndex to state.selectorOffset)
            }
        }
    }

    @Test
    fun narrowGapSwipeIsInertButKeyOriginSwipeScrollsTheKeypad() {
        val content = rows + listOf("Zulu", "Альфа", "Яблоко", "#").map { BrowserItem(it, it, BrowserKind.FOLDER) }
        val next = state.copy(root = state.root.copy(children = content))
        state = next.copy(azSnapshot = AzPreparedSection(content).snapshot(next.azQuery()))
        install(narrowAzFixtureDensity())
        val grid = compose.onNodeWithTag("az:grid")
        val first = compose.onNodeWithTag("az:group:CYRILLIC_AG").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("az:group:CYRILLIC_DZ").fetchSemanticsNode().boundsInRoot
        val origin = grid.fetchSemanticsNode().boundsInRoot.topLeft
        val gap = Offset((first.right + second.left) / 2, first.center.y) - origin
        compose.runOnIdle { actions.clear() }
        grid.performTouchInput { swipe(gap, gap - Offset(0f, 60f)) }
        compose.waitForIdle()
        assertEquals(first, compose.onNodeWithTag("az:group:CYRILLIC_AG").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        grid.performTouchInput {
            swipe(first.center - origin, first.center - origin - Offset(0f, 100f))
        }
        compose.waitForIdle()
        val moved = compose.onNodeWithTag("az:group:CYRILLIC_AG").fetchSemanticsNode().boundsInRoot
        assertTrue("Key-origin drag must keep the constrained keypad scrollable", moved != first)
    }

    @Test
    fun keyboardFocusBringsCoveredRowActionAboveKeypad() {
        install()
        val action = compose.onNodeWithContentDescription("Actions for Fixture 3")
        val layer = compose.onNodeWithTag("az:layer").fetchSemanticsNode().boundsInRoot
        assertTrue(action.fetchSemanticsNode().boundsInRoot.bottom > layer.top)
        action.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        assertTrue(action.fetchSemanticsNode().boundsInRoot.bottom <= layer.top - 24f)
    }

    @Test
    fun autoOkCapacityUsesThePostSearchListNotThePreview() {
        install()
        val searching = actions.filterIsInstance<PlayerUiAction.ReportHierarchyViewport>().last()
        val previewCapacity = azVisibleCapacity(searching.rowHeightPx, searching.rowGapPx, searching.viewportHeightPx)
        // Derive the discriminating result size from this window instead of assuming a
        // particular device can show exactly three rows after the keypad closes.
        val resultCount = previewCapacity + 1
        assertTrue("The keypad must reduce the fixture's row capacity", resultCount <= searching.visibleCapacity)
        assertTrue(
            "Results fitting the ordinary destination should auto-apply",
            shouldAutoApplyAzSearch(true, resultCount, searching.visibleCapacity),
        )
        compose.runOnIdle {
            actions.clear()
            state = state.copy(screen = PlayerScreen.HIERARCHY, azDraftSteps = null)
        }
        compose.waitForIdle()
        val ordinary = actions.filterIsInstance<PlayerUiAction.ReportHierarchyViewport>().last()
        assertEquals(ordinary.visibleCapacity, searching.visibleCapacity)
        assertTrue(ordinary.viewportHeightPx > searching.viewportHeightPx)
    }
}
