package link.roadio.riff.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** One production selector for immutable synthetic rows; never mutate working playlists or playback. */
class HierarchyRowsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun playlistsTitlesAndBookmarksShareOneRowAcrossResizeFontScaleAndRtl() {
        var state by mutableStateOf(fixture(compactRows(BrowserKind.ALBUM)))
        var width by mutableStateOf(640f)
        var fontScale by mutableStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        val config = UiConfig.defaults(context)
        val actions = mutableListOf<PlayerUiAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale), LocalLayoutDirection provides direction) {
                Box(Modifier.width(width.dp).height(800.dp)) {
                    PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = actions::add)
                }
            }
        }
        val cases =
            listOf(
                Triple(BrowserKind.ALBUM, false, false),
                Triple(BrowserKind.PLAYLIST, true, true),
                Triple(BrowserKind.TRACK, true, true),
                Triple(BrowserKind.PLAYLIST, false, true),
                Triple(BrowserKind.PLAYLIST, false, false),
            )
        for ((kind, bookmarks, reactionEligible) in cases) {
            for ((wide, enlarged) in listOf(false to false, true to true, false to true, true to false)) {
                compose.runOnIdle {
                    width = if (wide) 1000f else 640f
                    fontScale = if (enlarged) 2f else 1f
                    direction = if (enlarged) LayoutDirection.Rtl else LayoutDirection.Ltr
                    state = fixture(compactRows(kind, bookmarks, reactionEligible))
                }
                compose.waitForIdle()
                val first = bounds("selector:row:compact-0")
                val second = bounds("selector:row:compact-1")
                assertEquals(config.float("geometry.listRowHeight"), first.height, 1f)
                assertEquals(first.height + config.float("geometry.listRowGap"), second.top - first.top, 1f)
                val body = compose.onNodeWithContentDescription("Section 0; Library; page 1 of 1").fetchSemanticsNode().boundsInRoot
                val more = compose.onNodeWithContentDescription("Actions for Section 0").fetchSemanticsNode().boundsInRoot
                assertEquals(body.center.y, more.center.y, 1f)
                assertTrue("Complete body width reaches More", abs(body.width - (first.width - more.width - 24f)) <= 2f)
                assertTrue("Body and More do not overlap", body.right <= more.left || more.right <= body.left)
                assertTrue(body.height >= 76f && more.height >= 76f)
                for (reaction in listOf("Ban", "Like", "Dislike")) {
                    compose.onNodeWithContentDescription("$reaction Section 0").assertDoesNotExist()
                }
                actions.clear()
                compose.onNodeWithContentDescription("Section 0; Library; page 1 of 1").performClick()
                val expectedBodyAction =
                    when (kind) {
                        BrowserKind.TRACK -> PlayerUiAction.Play("compact-0")
                        else -> PlayerUiAction.NavigateTo("compact-0")
                    }
                assertTrue(actions.contains(expectedBodyAction))
                compose.onNodeWithContentDescription("Actions for Section 0").assertIsEnabled().performClick()
                assertTrue(actions.contains(PlayerUiAction.OpenRowActions("compact-0")))
                if (kind == BrowserKind.PLAYLIST) {
                    compose.onNodeWithText("Section 1").assertIsEnabled()
                } else {
                    compose.onNodeWithText("Section 1").assertIsNotEnabled()
                }
                if (kind == BrowserKind.ALBUM) {
                    compose.onNodeWithContentDescription("Actions for Section 1; unavailable").assertIsNotEnabled()
                } else {
                    compose.onNodeWithContentDescription("Actions for Section 1").assertIsEnabled()
                }
            }
        }
    }

    @Test
    fun customNarrowTargetRemainsFullSizeInSharedCompactRows() {
        val config =
            UiConfig.fromUserText(
                """{"skins":{"default":{"geometry":{"ratingTargetNarrow":128,"listRowHeightTwoTier":280}}}}""",
            )
        var state by mutableStateOf(fixture(compactRows(BrowserKind.PLAYLIST)))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.width(640.dp).height(800.dp)) {
                    PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = {})
                }
            }
        }
        for (bookmarks in listOf(false, true)) {
            compose.runOnIdle { state = fixture(compactRows(BrowserKind.PLAYLIST, bookmarks, reactionEligible = true)) }
            compose.waitForIdle()
            val row = bounds("selector:row:compact-0")
            val body = compose.onNodeWithContentDescription("Section 0; Library; page 1 of 1").fetchSemanticsNode().boundsInRoot
            val more = compose.onNodeWithContentDescription("Actions for Section 0").fetchSemanticsNode().boundsInRoot
            assertEquals(128f, row.height, 1f)
            assertEquals(128f, body.height, 1f)
            assertEquals(128f, more.height, 1f)
            assertEquals(body.center.y, more.center.y, 1f)
            assertEquals(24f, more.left - body.right, 1f)
            assertEquals(row.width - more.width - 24f, body.width, 2f)
        }
    }

    @Test
    fun eligibleMusicAlbumsAndPlaylistTracksRetainNarrowReactions() {
        var state by mutableStateOf(fixture(listOf(BrowserItem("music", "Music", BrowserKind.ALBUM))))
        val config = UiConfig.defaults(context)
        val actions = mutableListOf<PlayerUiAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.width(640.dp).height(800.dp)) {
                    PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = actions::add)
                }
            }
        }
        for (kind in listOf(BrowserKind.ALBUM, BrowserKind.TRACK)) {
            compose.runOnIdle {
                state =
                    fixture(
                        listOf(
                            BrowserItem(
                                "music",
                                "Music",
                                kind,
                                playable = true,
                                enabled = false,
                                playlistId = 1L.takeIf { kind == BrowserKind.TRACK },
                            ),
                        ),
                    )
            }
            compose.waitForIdle()
            assertEquals(config.float("geometry.listRowHeightTwoTier"), bounds("selector:row:music").height, 1f)
            val body = compose.onNodeWithText("Music").fetchSemanticsNode().boundsInRoot
            val moreDescription =
                when (kind) {
                    BrowserKind.TRACK -> "Actions for Music"
                    else -> "Actions for Music; unavailable"
                }
            val more = compose.onNodeWithContentDescription(moreDescription).fetchSemanticsNode().boundsInRoot
            assertTrue(more.top >= body.bottom + 24f - 1f)
            for (reaction in listOf("Ban", "Like", "Dislike")) {
                compose.onNodeWithContentDescription("$reaction Music").assertIsEnabled()
            }
            actions.clear()
            compose.onNodeWithContentDescription("Like Music").performClick()
            assertEquals(
                listOf(PlayerUiAction.SetReaction("music", Preference.LIKED, false)),
                actions.filterIsInstance<PlayerUiAction.SetReaction>(),
            )
        }
    }

    @Test
    fun azFilteringKeepsCompleteSourceAllocationAndCorrectViewportReport() {
        val source =
            listOf(
                BrowserItem("book", "Book", BrowserKind.ALBUM, aiReactionEligible = false),
                BrowserItem("music", "Music", BrowserKind.ALBUM),
                BrowserItem("playlist", "Playlist", BrowserKind.PLAYLIST),
            )
        var state by mutableStateOf(fixture(source))
        val config = UiConfig.defaults(context)
        val actions = mutableListOf<PlayerUiAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.width(640.dp).height(800.dp)) {
                    PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = actions::add)
                }
            }
        }
        assertEquals(config.float("geometry.listRowHeightTwoTier"), bounds("selector:row:book").height, 1f)
        compose.runOnIdle {
            val steps = listOf(AzGroup.LATIN_AC)
            val query = AzSearchQuery(source, steps, emptyList())
            state = state.copy(azSteps = steps, azSnapshot = AzPreparedSection(source).snapshot(query))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Music").assertDoesNotExist()
        assertEquals(config.float("geometry.listRowHeightTwoTier"), bounds("selector:row:book").height, 1f)
        compose.runOnIdle { state = fixture(source.take(1)) }
        compose.waitForIdle()
        assertEquals(config.float("geometry.listRowHeight"), bounds("selector:row:book").height, 1f)
        compose.runOnIdle {
            val steps = listOf(AzGroup.LATIN_PS)
            state =
                fixture(
                    source,
                ).copy(azSteps = steps, azSnapshot = AzPreparedSection(source).snapshot(AzSearchQuery(source, steps, emptyList())))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Music").assertDoesNotExist()
        assertEquals(config.float("geometry.listRowHeightTwoTier"), bounds("selector:row:playlist").height, 1f)
        compose.runOnIdle { state = fixture(source.takeLast(1)) }
        compose.waitForIdle()
        assertEquals(config.float("geometry.listRowHeight"), bounds("selector:row:playlist").height, 1f)
        assertEquals(
            config.float("geometry.listRowHeight").toInt(),
            actions.filterIsInstance<PlayerUiAction.ReportHierarchyViewport>().last().rowHeightPx,
        )
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun fixture(rows: List<BrowserItem>): PlayerUiState =
        PlayerUiState(
            screen = PlayerScreen.HIERARCHY,
            root = BrowserItem("fixture", "Fixture", BrowserKind.ROOT, children = rows),
            nowPlaying = NowPlayingUiState(),
        )

    private fun compactRows(
        kind: BrowserKind,
        bookmarks: Boolean = kind != BrowserKind.ALBUM,
        reactionEligible: Boolean = false,
    ): List<BrowserItem> =
        (0..1).map { index ->
            BrowserItem(
                "compact-$index",
                "Section $index",
                kind,
                playable = index == 0,
                enabled = index == 0 || kind == BrowserKind.PLAYLIST,
                bookmarks = bookmarks,
                aiReactionEligible = reactionEligible,
            )
        }
}
