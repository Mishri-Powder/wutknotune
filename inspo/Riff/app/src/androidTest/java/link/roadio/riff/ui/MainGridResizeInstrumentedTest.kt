package link.roadio.riff.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic local MAIN geometry; the working app's library, preferences and grants are untouched. */
class MainGridResizeInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun allSevenTilesFitAfterHeightWidthDensityAndDirectionChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        val children =
            HierarchyRoot.entries.map { BrowserItem(it.categoryId, it.label, BrowserKind.CATEGORY, it.icon) } +
                listOf(
                    BrowserItem("category:books", "Books", BrowserKind.CATEGORY, "audiobook"),
                    BrowserItem("category:ai", "AI", BrowserKind.AI, "ai"),
                )
        val state =
            PlayerUiState(
                screen = PlayerScreen.MAIN,
                root = BrowserItem("root", "Fixture", BrowserKind.ROOT, children = children),
                nowPlaying = NowPlayingUiState(),
                status = "Indexing media",
            )
        var width by mutableStateOf(1200f)
        var height by mutableStateOf(1000f)
        var density by mutableStateOf(1f)
        var fontScale by mutableStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        val actions = mutableListOf<PlayerUiAction>()
        var effectiveDensity = 1f
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Fit every synthetic viewport inside the real test window without clipping it.
                // This is fixture density, never a product or device-identity exception.
                val fit = minOf(1f, constraints.maxWidth / 1320f, constraints.maxHeight / 1650f)
                effectiveDensity = fit * density
                CompositionLocalProvider(
                    LocalDensity provides Density(effectiveDensity, fontScale),
                    LocalLayoutDirection provides direction,
                ) {
                    Box(Modifier.size(width.dp, height.dp)) {
                        PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = actions::add)
                    }
                }
            }
        }
        val labels = children.dropLast(1).map { it.label } + "AI mood selection"

        fun verifyTilesFit() {
            val viewport = compose.onNodeWithTag("main:grid-viewport").fetchSemanticsNode().boundsInRoot
            val tiles = labels.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode() }
            val bounds = tiles.map { it.boundsInRoot }
            tiles.forEach { tile ->
                val rect = tile.boundsInRoot
                val measured = tile.layoutInfo.coordinates.size
                assertEquals("Tile must be fully visible: $rect", measured.width.toFloat(), rect.width, 1f)
                assertEquals("Tile must be fully visible: $rect", measured.height.toFloat(), rect.height, 1f)
                assertEquals("Tile must remain square", rect.width, rect.height, 1f)
                assertTrue(rect.left >= viewport.left - 1f && rect.right <= viewport.right + 1f)
                assertTrue(
                    "Viewport $viewport tile $rect width=$width height=$height density=$effectiveDensity",
                    rect.top >= viewport.top - 1f && rect.bottom <= viewport.bottom + 1f,
                )
                assertTrue(
                    "128dp floor within one physical-pixel rounding: $rect density=$effectiveDensity",
                    rect.width >= 128f * effectiveDensity - 1f,
                )
            }
            bounds.forEachIndexed { index, rect ->
                bounds.drop(index + 1).forEach { other -> assertTrue("Tiles must not overlap", !rect.overlaps(other)) }
            }
            compose.onAllNodes(hasScrollAction() and hasAnyAncestor(hasTestTag("main:grid-viewport"))).assertCountEquals(0)
            assertTrue(actions.isEmpty())
        }

        verifyTilesFit()
        compose.runOnIdle { height = 763f }
        verifyTilesFit()
        compose.runOnIdle {
            width = 600f
            height = 1500f
        }
        verifyTilesFit()
        compose.runOnIdle { height = 1000f }
        verifyTilesFit()
        compose.runOnIdle {
            density = 1.1f
            fontScale = 2f
            direction = LayoutDirection.Rtl
        }
        verifyTilesFit()
    }
}
