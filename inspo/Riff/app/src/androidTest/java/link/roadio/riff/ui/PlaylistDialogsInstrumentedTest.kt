package link.roadio.riff.ui

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic New/Rename/Delete names only; shared production dialog, no working-package mutations. */
class PlaylistDialogsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var current by mutableStateOf(
        PlayerUiState(
            screen = PlayerScreen.HIERARCHY,
            root =
                BrowserItem(
                    "root",
                    "Fixture",
                    BrowserKind.ROOT,
                    children = listOf(BrowserItem(HierarchyRoot.PLAYLISTS.categoryId, "Playlists", BrowserKind.CATEGORY)),
                ),
            browserPath = listOf(HierarchyRoot.PLAYLISTS.categoryId),
            nowPlaying = NowPlayingUiState(),
            playlistNameRequest = PlaylistNameUiRequest(PlaylistNameOperation.CREATE),
            playlistNameDraft = "Fixture name",
        ),
    )
    private var density by mutableStateOf(1f)
    private var fontScale by mutableStateOf(1f)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private val actions = mutableListOf<PlayerUiAction>()

    private fun install() {
        val config = UiConfig.fromUserText("""{"skins":{"default":{"typography":{"topBarFontFamily":"serif","topBarFontWeight":500}}}}""")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale), LocalLayoutDirection provides direction) {
                PlayerComposeUi(current, config, CalibrationSessionState(), context) {
                    actions += it
                    if (it is PlayerUiAction.UpdatePlaylistName) current = current.copy(playlistNameDraft = it.value)
                    if (it == PlayerUiAction.CancelPlaylistName) current = current.copy(playlistNameRequest = null)
                    if (it == PlayerUiAction.CancelPlaylistDelete) current = current.copy(pendingPlaylistDelete = null)
                    if (it == PlayerUiAction.ConfirmPlaylistDelete) current = current.copy(playlistMutationInProgress = true)
                }
            }
        }
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    @Test
    fun acceptedNameAndDeleteWritesIgnoreCancelBackAndOutside() {
        current = current.copy(playlistMutationInProgress = true)
        install()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(false, true).forEach { deleting ->
            compose.runOnIdle {
                current =
                    current.copy(
                        playlistNameRequest = if (deleting) null else PlaylistNameUiRequest(PlaylistNameOperation.CREATE),
                        pendingPlaylistDelete = if (deleting) PlaylistDeleteUiRequest(7L, "Busy") else null,
                    )
            }
            compose.onNodeWithContentDescription("OK; unavailable").assertIsNotEnabled().performClick()
            compose.onNodeWithContentDescription("Cancel; unavailable").assertIsNotEnabled().performClick()
            repeat(2) {
                ParcelFileDescriptor
                    .AutoCloseInputStream(
                        automation.executeShellCommand("input keyevent KEYCODE_BACK"),
                    ).use { it.readBytes() }
                compose.waitForIdle()
            }
            ParcelFileDescriptor
                .AutoCloseInputStream(
                    automation.executeShellCommand("input tap 1 ${context.resources.displayMetrics.heightPixels / 2}"),
                ).use {
                    it.readBytes()
                }
            compose.waitForIdle()
            compose.onNodeWithTag(if (deleting) "playlist:delete-dialog" else "playlist:name-dialog").assertIsDisplayed()
            compose.runOnIdle {
                val dialogActions =
                    setOf(
                        PlayerUiAction.ConfirmPlaylistName,
                        PlayerUiAction.CancelPlaylistName,
                        PlayerUiAction.ConfirmPlaylistDelete,
                        PlayerUiAction.CancelPlaylistDelete,
                    )
                assertTrue(actions.toString(), actions.none { it in dialogActions })
            }
        }
    }

    @Test
    fun inlineErrorUsesSharedTypographyAndAccessibleLiveRegion() {
        val error = "A playlist with this name already exists"
        current = current.copy(playlistMutationError = error)
        install()
        val node =
            compose
                .onNodeWithTag(
                    "riff:dialog-error",
                    useUnmergedTree = true,
                ).performScrollTo()
                .assertIsDisplayed()
                .fetchSemanticsNode()
        assertEquals(error, node.config[SemanticsProperties.Error])
        assertEquals(LiveRegionMode.Polite, node.config[SemanticsProperties.LiveRegion])
        assertEquals(layout("Playlists").layoutInput.style.fontSize, layout(error).layoutInput.style.fontSize)
        compose.onNodeWithContentDescription("OK").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel").assertIsDisplayed()
    }

    @Test
    fun newAndRenameSharePlateTypographyAndPhysicalIconOrderAcrossRtlAndFontScale() {
        install()
        listOf(PlaylistNameOperation.CREATE, PlaylistNameOperation.RENAME).forEach { operation ->
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { rtl ->
                listOf(0.75f to 1f, 1.5f to 2f).forEach { (d, f) ->
                    compose.runOnIdle {
                        density = d
                        fontScale = f
                        direction = rtl
                        current = current.copy(playlistNameRequest = PlaylistNameUiRequest(operation), playlistNameDraft = "Fixture name")
                    }
                    val title = if (operation == PlaylistNameOperation.CREATE) "New playlist" else "Rename playlist"
                    val plateStyle = layout("Playlists").layoutInput.style
                    val titleStyle = layout(title).layoutInput.style
                    val fieldStyle = layout("Fixture name").layoutInput.style
                    listOf(titleStyle, fieldStyle).forEach {
                        assertEquals(plateStyle.fontSize, it.fontSize)
                        assertEquals(plateStyle.fontFamily, it.fontFamily)
                        assertEquals(plateStyle.fontWeight, it.fontWeight)
                    }
                    val ok =
                        compose
                            .onNodeWithContentDescription("OK")
                            .assertIsDisplayed()
                            .fetchSemanticsNode()
                            .boundsInRoot
                    val cancel =
                        compose
                            .onNodeWithContentDescription("Cancel")
                            .assertIsDisplayed()
                            .fetchSemanticsNode()
                            .boundsInRoot
                    val dialog = compose.onNodeWithTag("playlist:name-dialog").fetchSemanticsNode().boundsInRoot
                    assertTrue("Dialog width ${dialog.width} at density $d", dialog.width <= 420f * d + 1f)
                    assertTrue(cancel.left - ok.right >= 24f * d - 1f)
                    assertEquals(ok.top, cancel.top, 1f)
                    listOf(ok, cancel).forEach {
                        assertEquals(80f * d, it.width, 1f)
                        assertEquals(80f * d, it.height, 1f)
                    }
                    compose.onNodeWithText("OK").assertDoesNotExist()
                    compose.onNodeWithText("Cancel").assertDoesNotExist()
                    compose.onNodeWithContentDescription("OK").performClick()
                    compose.runOnIdle { assertEquals(PlayerUiAction.ConfirmPlaylistName, actions.last()) }
                }
            }
        }
    }

    @Test
    fun deleteUsesTheSamePlateTypographyAndIconsWithCompleteLongNameAndScrollableBody() {
        val name = "Long playlist name ".repeat(6).padEnd(120, 'Z')
        current = current.copy(playlistNameRequest = null, pendingPlaylistDelete = PlaylistDeleteUiRequest(7L, name))
        install()
        listOf(ColorScheme.DAY, ColorScheme.NIGHT).forEach { theme ->
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { rtl ->
                listOf(0.75f to 1f, 1.5f to 2f).forEach { (d, f) ->
                    compose.runOnIdle {
                        density = d
                        fontScale = f
                        direction = rtl
                        current = current.copy(settings = current.settings.copy(colorScheme = theme))
                    }
                    val plateStyle = layout("Playlists").layoutInput.style
                    listOf(layout("Delete playlist?"), layout(name), layout("The active playback queue will not change.")).forEach {
                        assertEquals(plateStyle.fontSize, it.layoutInput.style.fontSize)
                        assertEquals(plateStyle.fontFamily, it.layoutInput.style.fontFamily)
                        assertEquals(plateStyle.fontWeight, it.layoutInput.style.fontWeight)
                    }
                    compose.onNodeWithContentDescription(name, useUnmergedTree = true).assertExists()
                    compose.onNodeWithTag("playlist:name-input").assertDoesNotExist()
                    val ok =
                        compose
                            .onNodeWithContentDescription("OK")
                            .assertIsDisplayed()
                            .fetchSemanticsNode()
                            .boundsInRoot
                    val cancel =
                        compose
                            .onNodeWithContentDescription("Cancel")
                            .assertIsDisplayed()
                            .fetchSemanticsNode()
                            .boundsInRoot
                    val dialog = compose.onNodeWithTag("playlist:delete-dialog").fetchSemanticsNode().boundsInRoot
                    assertTrue(dialog.width <= 420f * d + 1f)
                    assertTrue(cancel.left - ok.right >= 24f * d - 1f)
                    assertEquals(ok.top, cancel.top, 1f)
                    listOf(ok, cancel).forEach {
                        assertEquals(80f * d, it.width, 1f)
                        assertEquals(80f * d, it.height, 1f)
                    }
                    compose.onNodeWithText("OK").assertDoesNotExist()
                    compose.onNodeWithText("Cancel").assertDoesNotExist()
                    compose.onNodeWithText("Delete").assertDoesNotExist()
                    compose.onNodeWithText("The active playback queue will not change.").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithContentDescription("OK").assertIsDisplayed()
                    compose.onNodeWithContentDescription("Cancel").assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun deleteCancelAndBusyConfirmationEmitOnlyTheirTypedActions() {
        current = current.copy(playlistNameRequest = null, pendingPlaylistDelete = PlaylistDeleteUiRequest(7L, "Fixture delete"))
        install()
        compose.onNodeWithText("Fixture delete").performTouchInput { click() }
        compose.onNodeWithTag("playlist:delete-dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithTag("playlist:delete-dialog").assertDoesNotExist()
        compose.runOnIdle { current = current.copy(pendingPlaylistDelete = PlaylistDeleteUiRequest(7L, "Fixture delete")) }
        compose.onNodeWithContentDescription("OK").performClick()
        compose.onNodeWithContentDescription("OK; unavailable").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(PlayerUiAction.CancelPlaylistDelete, PlayerUiAction.ConfirmPlaylistDelete),
                actions.filter {
                    it == PlayerUiAction.CancelPlaylistDelete || it == PlayerUiAction.ConfirmPlaylistDelete
                },
            )
        }
    }

    @Test
    fun deleteSystemBackAndOutsideCancelWithoutConfirmation() {
        current = current.copy(playlistNameRequest = null, pendingPlaylistDelete = PlaylistDeleteUiRequest(7L, "Fixture delete"))
        install()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent KEYCODE_BACK")).use { it.readBytes() }
        compose.waitForIdle()
        compose.onNodeWithTag("playlist:delete-dialog").assertDoesNotExist()
        compose.runOnIdle { current = current.copy(pendingPlaylistDelete = PlaylistDeleteUiRequest(7L, "Fixture delete")) }
        compose.onNodeWithTag("playlist:delete-dialog").assertIsDisplayed()
        ParcelFileDescriptor
            .AutoCloseInputStream(
                automation.executeShellCommand("input tap 1 ${context.resources.displayMetrics.heightPixels / 2}"),
            ).use {
                it.readBytes()
            }
        compose.waitForIdle()
        compose.onNodeWithTag("playlist:delete-dialog").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(
                listOf(PlayerUiAction.CancelPlaylistDelete, PlayerUiAction.CancelPlaylistDelete),
                actions.filter {
                    it == PlayerUiAction.CancelPlaylistDelete || it == PlayerUiAction.ConfirmPlaylistDelete
                },
            )
        }
    }

    @Test
    fun blankAndBusyPreventConfirmAndCancelDiscardsDraft() {
        current = current.copy(playlistNameDraft = "")
        install()
        compose.onNodeWithContentDescription("OK; unavailable").assertIsNotEnabled()
        assertEquals(layout("Playlists").layoutInput.style.fontSize, layout("Playlist name").layoutInput.style.fontSize)
        compose.onNodeWithTag("playlist:name-input").performTextReplacement("Corrected name")
        compose.onNodeWithContentDescription("OK").performClick()
        compose.runOnIdle { current = current.copy(playlistMutationInProgress = true) }
        compose.onNodeWithContentDescription("OK; unavailable").assertIsNotEnabled()
        compose.onNodeWithTag("playlist:name-input").assertIsNotEnabled()
        compose.runOnIdle { current = current.copy(playlistMutationInProgress = false) }
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithTag("playlist:name-dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(PlayerUiAction.CancelPlaylistName, actions.last()) }
    }

    @Test
    fun backDismissesNameAfterKeyboardCloses() {
        install()
        compose.onNodeWithTag("playlist:name-input").performTextReplacement("Uncommitted name")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // The first Back may belong to the IME; the next belongs to the shared name dialog.
        repeat(3) {
            var dismissed = false
            compose.runOnIdle { dismissed = current.playlistNameRequest == null }
            if (!dismissed) {
                ParcelFileDescriptor
                    .AutoCloseInputStream(
                        automation.executeShellCommand("input keyevent KEYCODE_BACK"),
                    ).use { it.readBytes() }
                SystemClock.sleep(350)
                compose.waitForIdle()
            }
        }
        compose.onNodeWithTag("playlist:name-dialog").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(
                listOf(PlayerUiAction.UpdatePlaylistName("Uncommitted name"), PlayerUiAction.CancelPlaylistName),
                actions.filter {
                    it is PlayerUiAction.UpdatePlaylistName ||
                        it == PlayerUiAction.CancelPlaylistName
                },
            )
        }
    }
}
