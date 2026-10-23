package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.player.core.settings.SettingsEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic settings only: no actual profile, save, migration or media operation. */
class TopBarAnchoringInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(
        PlayerUiState(
            screen = PlayerScreen.MAIN,
            root = BrowserItem("root", "Fixture", BrowserKind.ROOT),
            nowPlaying = NowPlayingUiState(),
        ),
    )
    private var density by mutableStateOf(1f)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private val actions = mutableListOf<PlayerUiAction>()

    private fun install() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density),
                LocalLayoutDirection provides direction,
            ) {
                PlayerComposeUi(state, config, CalibrationSessionState(), context, dispatch = actions::add)
            }
        }
    }

    private fun bounds(label: String): Rect =
        compose
            .onNodeWithContentDescription(label)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

    private fun assertEdges(
        screen: String,
        leading: String,
        trailing: String,
    ) {
        val safe = compose.onNodeWithTag("screen:$screen").fetchSemanticsNode().boundsInRoot
        val start = bounds(leading)
        val end = bounds(trailing)
        if (direction == LayoutDirection.Ltr) {
            assertEquals(safe.left, start.left, 1f)
            assertEquals(safe.right, end.right, 1f)
            assertTrue(end.left - start.right >= 24f * density)
        } else {
            assertEquals(safe.right, start.right, 1f)
            assertEquals(safe.left, end.left, 1f)
            assertTrue(start.left - end.right >= 24f * density)
        }
        listOf(start, end).forEach {
            assertEquals(80f * density, it.width, 1f)
            assertEquals(80f * density, it.height, 1f)
            assertEquals(safe.top, it.top, 1f)
        }
    }

    @Test
    fun mainSettingsStaysAtSafeEndInNarrowAndRtl() {
        install()
        listOf(1f, 1.5f).forEach { scale ->
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layout ->
                compose.runOnIdle {
                    density = scale
                    direction = layout
                }
                assertEdges("MAIN", "Switch to playback", "Settings; hold to open")
                compose.onNodeWithContentDescription("Settings; hold to open").performTouchInput { longClick() }
                compose.runOnIdle { assertEquals(PlayerUiAction.OpenSettingsLongPress, actions.last()) }
            }
        }
    }

    @Test
    fun entryDecisionProvidesAccessibleHoldAndKeepsRestrictedActionsAbsent() {
        install()
        listOf(SettingsEntry.PRESS, SettingsEntry.LONG_PRESS, SettingsEntry.RECOVERY_HOLD, SettingsEntry.BLOCKED).forEach { entry ->
            compose.runOnIdle { state = state.copy(settingsEntry = entry) }
            val label =
                when (entry) {
                    SettingsEntry.PRESS -> "Settings"
                    SettingsEntry.LONG_PRESS -> "Settings; hold to open"
                    SettingsEntry.RECOVERY_HOLD -> "Settings; motion input unavailable; hold to open"
                    SettingsEntry.BLOCKED -> "Settings unavailable while restricted; unavailable"
                }
            val node = compose.onNodeWithContentDescription(label)
            if (entry == SettingsEntry.BLOCKED) {
                node.assertIsNotEnabled()
                assertTrue(!node.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
            } else if (entry == SettingsEntry.PRESS) {
                assertTrue(!node.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
                node.performClick()
                compose.runOnIdle { assertEquals(PlayerUiAction.OpenSettings, actions.last()) }
            } else {
                assertTrue(!node.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
                node.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
                compose.runOnIdle {
                    assertEquals(
                        if (entry ==
                            SettingsEntry.LONG_PRESS
                        ) {
                            PlayerUiAction.OpenSettingsLongPress
                        } else {
                            PlayerUiAction.OpenSettingsRecovery
                        },
                        actions.last(),
                    )
                }
            }
        }
    }

    @Test
    fun settingsSaveKeepsItsEdgeAcrossWidthAndDisabledState() {
        state = state.copy(screen = PlayerScreen.SETTINGS, settingsDraft = state.settings)
        install()
        listOf(0.75f, 1f, 1.5f).forEach { scale ->
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layout ->
                compose.runOnIdle {
                    density = scale
                    direction = layout
                    state = state.copy(selectedStorageAvailable = true)
                }
                assertEdges("SETTINGS", "Cancel settings", "Save settings")
                val enabled = bounds("Save settings")
                compose.onNodeWithContentDescription("Save settings").performClick()
                compose.runOnIdle {
                    assertEquals(PlayerUiAction.SaveSettings, actions.last())
                    state = state.copy(selectedStorageAvailable = false)
                }
                compose.onNodeWithContentDescription("Save settings; unavailable").assertIsNotEnabled()
                assertEquals(enabled, bounds("Save settings; unavailable"))
            }
        }
    }

    @Test
    fun middleOverflowDoesNotMoveCancelOrSave() {
        density = 2f
        state = state.copy(screen = PlayerScreen.SETTINGS, settingsDraft = state.settings)
        install()
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layout ->
            compose.runOnIdle { direction = layout }
            assertEdges("SETTINGS", "Cancel settings", "Save settings")
            val cancel = bounds("Cancel settings")
            val save = bounds("Save settings")
            compose.onNodeWithContentDescription("Next page").performScrollTo().assertIsDisplayed()
            assertEquals(cancel, bounds("Cancel settings"))
            assertEquals(save, bounds("Save settings"))
            compose.onNodeWithContentDescription("Previous page; unavailable").performScrollTo().assertIsDisplayed()
            assertEquals(cancel, bounds("Cancel settings"))
            assertEquals(save, bounds("Save settings"))
        }
    }

    @Test
    fun moreAndMediaFoldersReuseTheSettingsOkGeometry() {
        state =
            state.copy(
                screen = PlayerScreen.SETTINGS_MORE,
                settingsDraft = state.settings,
                moreSettingsDraft = state.settings,
            )
        install()

        assertEdges("SETTINGS_MORE", "Back to settings", "Save more settings")
        compose.onNodeWithContentDescription("Save more settings").performClick()
        compose.runOnIdle { assertEquals(PlayerUiAction.SaveMoreSettings, actions.last()) }

        compose.runOnIdle { state = state.copy(screen = PlayerScreen.SAF_GRANTS) }
        assertEdges("SAF_GRANTS", "Cancel media folder changes", "Save media folder changes")
        compose.onNodeWithContentDescription("Save media folder changes").performClick()
        compose.runOnIdle { assertEquals(PlayerUiAction.SaveSafGrantSelector, actions.last()) }
    }
}
