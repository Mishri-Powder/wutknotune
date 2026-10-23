package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic-only UI fixtures: no library, playback, profile, or real media data. */
@RunWith(AndroidJUnit4::class)
class AzLivePreviewInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val rows =
        listOf("Zulu fixture", "Alpha fixture", "Beta fixture", "Яблоко fixture", "Альфа fixture", "# fixture").map {
            BrowserItem(it, it, BrowserKind.ALBUM)
        }
    private var presented by mutableStateOf(
        PlayerUiState(
            screen = PlayerScreen.AZ_SEARCH,
            root = BrowserItem("root", "Fixture", BrowserKind.ALBUM, children = rows),
            hierarchyRoot = HierarchyRoot.ALBUMS,
            nowPlaying = NowPlayingUiState(),
            azDraftSteps = emptyList(),
            azSnapshot = AzPreparedSection(rows).snapshot(AzSearchQuery(rows, emptyList(), emptyList())),
        ),
    )

    private fun install(density: Float = 1f) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        val calibration = CalibrationSessionState()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                PlayerComposeUi(presented, config, calibration, context, dispatch = {})
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(name: String): Rect =
        compose.onNodeWithTag("az:group:$name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun verifyRow(
        names: List<String>,
        density: Float,
        heightDp: Int,
    ) {
        val cells = names.map(::bounds)
        val backdrop = compose.onNodeWithTag("screen:AZ_SEARCH", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(backdrop.left, cells.first().left, 1f)
        assertEquals(backdrop.right, cells.last().right, 1f)
        cells.forEach {
            assertEquals(heightDp * density, it.height, 1f)
            assertTrue(it.width >= it.height)
            assertEquals(cells.first().width, it.width, 1f)
            assertEquals(cells.first().top, it.top, 1f)
        }
        cells.zipWithNext().forEach { (left, right) ->
            assertEquals(24 * density, right.left - left.right, 1f)
        }
    }

    @Test
    fun unavailableButtonsCompositeAtTwentyPercentWithoutHatchingInBothThemes() {
        // Isolate the plate's alpha from live-list text beneath the transparent keypad.
        val content = listOf(BrowserItem("alpha", "Alpha fixture", BrowserKind.ALBUM))
        val next = presented.copy(root = presented.root.copy(children = content))
        presented = next.copy(azSnapshot = AzPreparedSection(content).snapshot(next.azQuery()))
        install()
        listOf(ColorScheme.DAY, ColorScheme.NIGHT).forEach { scheme ->
            compose.runOnIdle { presented = presented.copy(settings = presented.settings.copy(colorScheme = scheme)) }
            val screen = compose.onNodeWithTag("screen:AZ_SEARCH")
            val origin = screen.fetchSemanticsNode().boundsInRoot.topLeft
            val pixels = screen.captureToImage().toPixelMap()
            val inactive = bounds("BACKSPACE").translate(-origin)
            val active = bounds("LATIN_AC").translate(-origin)
            val background = pixels[(inactive.left - 12).toInt(), (inactive.top + 5).toInt()]
            val plate = pixels[(active.left + 20).toInt(), (active.top + 5).toInt()]
            val expected = plate.copy(alpha = 0.2f).compositeOver(background)
            val strip =
                (inactive.left.toInt() + 20 until inactive.right.toInt() - 20).map { x ->
                    pixels[x, (inactive.top + 5).toInt()]
                }
            assertEquals("Inactive plate must have no stripes in $scheme", 1, strip.toSet().size)
            val actual = strip.first()
            assertEquals("Red alpha composition in $scheme", expected.red, actual.red, 3f / 255f)
            assertEquals("Green alpha composition in $scheme", expected.green, actual.green, 3f / 255f)
            assertEquals("Blue alpha composition in $scheme", expected.blue, actual.blue, 3f / 255f)
            assertTrue("Inactive plate must differ from an opaque plate", actual != plate)
        }
    }

    @Test
    fun responsiveRowsReachBothEdgesWithFixedGapsAndFixedHeight() {
        install()
        val wide =
            compose
                .onNodeWithTag("screen:AZ_SEARCH")
                .fetchSemanticsNode()
                .boundsInRoot.width >= 800f
        val height = if (wide) 100 else 76
        verifyNumericRows(wide)
        AZ_LATIN_GROUPS.chunked(if (wide) 8 else 4).forEach { verifyRow(it.map { group -> group.name }, 1f, height) }
        AZ_CYRILLIC_GROUPS.chunked(if (wide) 8 else 4).forEach { verifyRow(it.map { group -> group.name }, 1f, height) }
        assertEquals(bounds("SYMBOL").width * 2f + 24f, bounds("BACKSPACE").width, 1f)
        val backdrop = compose.onNodeWithTag("screen:AZ_SEARCH", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("az:browser-chrome", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bottomKey = "BACKSPACE"
        assertEquals(backdrop.bottom, bounds(bottomKey).bottom, 1f)
        assertEquals(backdrop.top, header.top, 1f)
        assertTrue(bounds(AZ_CYRILLIC_GROUPS.first().name).top >= header.bottom + 24f)
        assertTrue(bounds(AZ_LATIN_GROUPS.first().name).top > bounds(AZ_CYRILLIC_GROUPS.last().name).bottom)
        assertTrue(bounds("SYMBOL").top > bounds(AZ_LATIN_GROUPS.last().name).bottom)
    }

    @Test
    fun pendingRetainsPhysicalRowsAndCompletedResultsUpdateTheirAvailability() {
        install()

        val wide =
            compose
                .onNodeWithTag("screen:AZ_SEARCH")
                .fetchSemanticsNode()
                .boundsInRoot.width >= 800f

        fun check(
            latinRows: Set<Int>,
            cyrillicRows: Set<Int>,
            upperNumericRow: Boolean = false,
        ) {
            fun checkAlphabet(
                groups: List<AzGroup>,
                shownRows: Set<Int>,
            ) {
                groups.forEachIndexed { index, group ->
                    val shown = if (wide) shownRows.isNotEmpty() else index / 4 in shownRows
                    val node = compose.onNodeWithTag("az:group:" + group.name)
                    if (shown) node.assertExists() else node.assertDoesNotExist()
                }
            }
            checkAlphabet(AZ_LATIN_GROUPS, latinRows)
            checkAlphabet(AZ_CYRILLIC_GROUPS, cyrillicRows)
            if (!wide && !upperNumericRow) {
                listOf("SYMBOL", "DIGIT_01", "DIGIT_23", "DIGIT_45").forEach {
                    compose.onNodeWithTag("az:group:$it").assertDoesNotExist()
                }
            }
            verifyNumericRows(wide, upperNumericRow = upperNumericRow)
        }
        val initial = presented
        listOf(ColorScheme.DAY, ColorScheme.NIGHT).forEach { scheme ->
            compose.runOnIdle { presented = initial.copy(settings = initial.settings.copy(colorScheme = scheme)) }
            check(setOf(0, 1), setOf(0, 1), upperNumericRow = true)
            val names = AzGroup.entries.map { it.name } + "BACKSPACE"
            val originalBounds = names.associateWith(::bounds)
            val pixelsBefore = compose.onNodeWithTag("az:group:LATIN_AC").captureToImage()
            val inactiveBefore = compose.onNodeWithTag("az:group:DIGIT_89").captureToImage()
            compose.runOnIdle { presented = presented.copy(azDraftSteps = listOf(AzGroup.LATIN_AC)) }
            compose.onAllNodesWithText("Zulu fixture", useUnmergedTree = true).assertCountEquals(1)
            assertSamePixels(pixelsBefore, compose.onNodeWithTag("az:group:LATIN_AC").captureToImage())
            assertSamePixels(inactiveBefore, compose.onNodeWithTag("az:group:DIGIT_89").captureToImage())
            assertEquals(originalBounds, names.associateWith(::bounds))
            compose.runOnIdle { presented = presented.copy(azSnapshot = AzPreparedSection(rows).snapshot(presented.azQuery())) }
            check(setOf(0), emptySet())
            compose.onAllNodesWithText("Zulu fixture", useUnmergedTree = true).assertCountEquals(0)
            compose.onAllNodesWithText("Alpha fixture", useUnmergedTree = true).assertCountEquals(1)
            compose.runOnIdle {
                val next = presented.copy(azDraftSteps = listOf(AzGroup.LATIN_AC, AzGroup.LATIN_WZ))
                presented = next.copy(azSnapshot = AzPreparedSection(rows).snapshot(next.azQuery()))
            }
            check(emptySet(), emptySet())
            compose.onAllNodesWithText("Alpha fixture", useUnmergedTree = true).assertCountEquals(0)
            compose.runOnIdle {
                val next = presented.copy(azDraftSteps = listOf(AzGroup.CYRILLIC_SOFT_YA))
                presented = next.copy(azSnapshot = AzPreparedSection(rows).snapshot(next.azQuery()))
            }
            check(emptySet(), setOf(0))
            compose.runOnIdle { presented = presented.copy(azDraftSteps = emptyList()) }
            check(emptySet(), setOf(0)) // Pending Backspace retains last-complete physical rows.
            compose.onAllNodesWithText("Zulu fixture", useUnmergedTree = true).assertCountEquals(1)
            compose.runOnIdle { presented = presented.copy(azSnapshot = AzPreparedSection(rows).snapshot(presented.azQuery())) }
            check(setOf(0, 1), setOf(0, 1), upperNumericRow = true)
            assertEquals(originalBounds, names.associateWith(::bounds))
            compose.runOnIdle { presented = presented.copy(screen = PlayerScreen.HIERARCHY, azDraftSteps = null) }
            compose.onAllNodesWithText("Zulu fixture", useUnmergedTree = true).assertCountEquals(1)
        }
    }

    @Test
    fun narrowHidesEachUnavailablePhysicalRowAndKeepsItsNeighborsIntact() {
        val density = narrowAzFixtureDensity()
        install(density)
        compose.runOnIdle {
            val next = presented.copy(azDraftSteps = listOf(AzGroup.LATIN_AC))
            presented = next.copy(azSnapshot = AzPreparedSection(rows).snapshot(next.azQuery()))
        }
        AZ_CYRILLIC_GROUPS.forEach { compose.onNodeWithTag("az:group:" + it.name).assertDoesNotExist() }
        AZ_LATIN_GROUPS.take(4).forEach { compose.onNodeWithTag("az:group:" + it.name).assertExists() }
        AZ_LATIN_GROUPS.drop(4).forEach { compose.onNodeWithTag("az:group:" + it.name).assertDoesNotExist() }
        listOf("SYMBOL", "DIGIT_01", "DIGIT_23", "DIGIT_45").forEach {
            compose.onNodeWithTag("az:group:$it").assertDoesNotExist()
        }
        verifyRow(AZ_LATIN_GROUPS.take(4).map { it.name }, density, 76)
        // The key's closest scroll ancestor is its horizontal row; scroll the vertical grid explicitly.
        compose.onNodeWithTag("az:grid").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1000f) }
        verifyNumericRows(wide = false, density = density, upperNumericRow = false)
    }

    @Test
    fun emptyNarrowSearchKeepsTheDoubleWidthBackspaceRowEvenWhenEveryKeyIsDisabled() {
        compose.runOnIdle {
            val next = presented.copy(root = presented.root.copy(children = emptyList()))
            presented = next.copy(azSnapshot = AzPreparedSection(emptyList()).snapshot(next.azQuery()))
        }
        val density = narrowAzFixtureDensity()
        install(density)
        (AZ_CYRILLIC_GROUPS + AZ_LATIN_GROUPS + listOf(AzGroup.SYMBOL, AzGroup.DIGIT_01, AzGroup.DIGIT_23, AzGroup.DIGIT_45))
            .forEach { compose.onNodeWithTag("az:group:" + it.name).assertDoesNotExist() }
        listOf("DIGIT_67", "DIGIT_89", "BACKSPACE").forEach {
            compose.onNodeWithTag("az:group:$it").assertExists().assertIsNotEnabled()
        }
        verifyNumericRows(wide = false, density = density, upperNumericRow = false)
        val backdrop = compose.onNodeWithTag("screen:AZ_SEARCH").fetchSemanticsNode().boundsInRoot
        assertEquals(backdrop.bottom, bounds("BACKSPACE").bottom, 1f)
    }

    private fun verifyNumericRows(
        wide: Boolean,
        density: Float = 1f,
        upperNumericRow: Boolean = true,
    ) {
        val rows =
            if (wide) {
                listOf(listOf("SYMBOL", "DIGIT_01", "DIGIT_23", "DIGIT_45", "DIGIT_67", "DIGIT_89", "BACKSPACE"))
            } else {
                buildList {
                    if (upperNumericRow) add(listOf("SYMBOL", "DIGIT_01", "DIGIT_23", "DIGIT_45"))
                    add(listOf("DIGIT_67", "DIGIT_89", "BACKSPACE"))
                }
            }
        val backdrop = compose.onNodeWithTag("screen:AZ_SEARCH", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rows.forEach { names ->
            val cells = names.map(::bounds)
            assertEquals(backdrop.left, cells.first().left, 1f)
            assertEquals(backdrop.right, cells.last().right, 1f)
            cells.forEach {
                assertEquals((if (wide) 100f else 76f) * density, it.height, 1f)
                assertTrue(it.width >= it.height)
                assertEquals(cells.first().top, it.top, 1f)
            }
            cells.zipWithNext().forEach { (left, right) ->
                assertEquals(24f * density, right.left - left.right, 1f)
            }
            val regular = names.zip(cells).filter { (name, _) -> name != "BACKSPACE" }.map { it.second }
            regular.zipWithNext().forEach { (left, right) -> assertEquals(left.width, right.width, 1f) }
            val backspace = names.indexOf("BACKSPACE").takeIf { it >= 0 }?.let(cells::get)
            if (backspace != null) {
                assertEquals(regular.first().width * 2f + 24f * density, backspace.width, 1f)
            }
        }
    }

    private fun assertSamePixels(
        first: ImageBitmap,
        second: ImageBitmap,
    ) {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        repeat(first.height) { y ->
            repeat(first.width) { x -> assertEquals(a[x, y], b[x, y]) }
        }
    }
}
