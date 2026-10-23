package link.roadio.riff.ui

import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.player.core.settings.SettingsAccess
import link.roadio.riff.player.library.SafListingNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Shared production presentation only; all mutation callbacks use disposable synthetic state. */
class ModalDialogsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val config =
        UiConfig.fromUserText(
            """{"skins":{"default":{"typography":{"topBarFontFamily":"serif","topBarFontWeight":500}}}}""",
        )
    private val base = PlayerUiState(PlayerScreen.MAIN, BrowserItem("root", "Library", BrowserKind.ROOT), nowPlaying = NowPlayingUiState())
    private var current by mutableStateOf(base)
    private var ageWarning by mutableStateOf(false)
    private var density by mutableStateOf(1f)
    private var fontScale by mutableStateOf(1f)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private val actions = mutableListOf<PlayerUiAction>()

    private fun install() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale), LocalLayoutDirection provides direction) {
                PlayerComposeUi(
                    current,
                    config,
                    CalibrationSessionState(),
                    context,
                    releaseIdentity = ReleaseIdentity(ReleaseChannel.DEBUG, LocalDate.of(2026, 1, 1)),
                    releaseAgeWarningVisible = ageWarning,
                    onAcknowledgeReleaseAge = { ageWarning = false },
                ) { action ->
                    actions += action
                    current = current.copy(pendingSafGrantRemoval = null, safListingBugWarning = null)
                }
            }
        }
    }

    private fun assertTypography(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(boundedUiText(text, config.int("behavior.maxStringLength")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val style = layouts.single().layoutInput.style
        assertEquals(32.sp, style.fontSize)
        assertEquals(FontFamily.Serif, style.fontFamily)
        assertEquals(FontWeight(500), style.fontWeight)
    }

    private fun shell(command: String) {
        ParcelFileDescriptor
            .AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
        compose.waitForIdle()
    }

    private fun awaitActiveDialog(title: String) {
        compose.onNodeWithText(title).assertIsDisplayed()

        // Compose idle can precede native focus after immediately reopening a Dialog window.
        fun containsTitle(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == title || node.contentDescription?.toString() == title) return true
            return (0 until node.childCount).any { containsTitle(node.getChild(it)) }
        }
        compose.waitUntil(5_000) {
            containsTitle(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
        }
    }

    @Test fun folderNoticeVariantsAndReleaseAgeUseCommonTypographyAndIconActions() {
        val notices =
            listOf(
                SafListingNotice(1, listOf("Music"), 0, emptyList()),
                SafListingNotice(0, emptyList(), 1, listOf("Books")),
                SafListingNotice(1, listOf("Music"), 1, listOf("Books")),
            )
        install()
        for (theme in listOf(ColorScheme.DAY, ColorScheme.NIGHT)) {
            for (rtl in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (variant in 0..5) {
                    compose.runOnIdle {
                        density = if (rtl == LayoutDirection.Rtl) 1.5f else 0.75f
                        fontScale = if (theme == ColorScheme.NIGHT) 2f else 1f
                        direction = rtl
                        ageWarning = variant == 5
                        current =
                            base.copy(
                                settings = base.settings.copy(colorScheme = theme),
                                pendingSafGrantRemoval = if (variant < 2) SafGrantUiItem("fixture", "Fixture folder") else null,
                                selectedStorageAvailable = variant != 1,
                                safListingBugWarning = if (variant in 2..4) notices[variant - 2] else null,
                            )
                    }
                    val title =
                        when (variant) {
                            0, 1 -> "Remove media folder?"
                            5 -> "Old Debug build"
                            else -> notices[variant - 2].title
                        }
                    val message =
                        when (variant) {
                            0 -> {
                                "Stop using “Fixture folder” after you press OK?"
                            }

                            1 -> {
                                "Stop using “Fixture folder” after you press OK? Application data storage is unavailable, " +
                                    "so indexing cannot run."
                            }

                            5 -> {
                                "This Debug build is more than three months old. It may be outdated."
                            }

                            else -> {
                                notices[variant - 2].message
                            }
                        }
                    assertTypography(title)
                    assertTypography(message)
                    val positive =
                        compose
                            .onNodeWithContentDescription(
                                if (variant <
                                    2
                                ) {
                                    "Remove"
                                } else {
                                    "OK"
                                },
                            ).assertIsDisplayed()
                            .fetchSemanticsNode()
                            .boundsInRoot
                    val dialog = compose.onNodeWithTag("riff:dialog").fetchSemanticsNode().boundsInRoot
                    assertTrue(dialog.width <= 420f * density + 1f)
                    assertEquals(80f * density, positive.width, 1f)
                    assertEquals(80f * density, positive.height, 1f)
                    if (variant < 2) {
                        val cancel =
                            compose
                                .onNodeWithContentDescription("Cancel")
                                .assertIsDisplayed()
                                .fetchSemanticsNode()
                                .boundsInRoot
                        assertTrue(cancel.left - positive.right >= 24f * density - 1f)
                        assertEquals(positive.top, cancel.top, 1f)
                    } else {
                        compose.onNodeWithContentDescription("Cancel").assertDoesNotExist()
                    }
                    compose.onNodeWithText(if (variant < 2) "Remove" else "OK").assertDoesNotExist()
                }
            }
        }
    }

    @Test fun folderCancellationAndConfirmationKeepTypedActionsAndReleaseRequiresOk() {
        fun reopen() {
            compose.runOnIdle { current = base.copy(pendingSafGrantRemoval = SafGrantUiItem("fixture", "Fixture folder")) }
            awaitActiveDialog("Remove media folder?")
        }
        current = base.copy(pendingSafGrantRemoval = SafGrantUiItem("fixture", "Fixture folder"))
        install()
        compose.onNodeWithContentDescription("Cancel").performClick()
        reopen()
        shell("input keyevent KEYCODE_BACK")
        compose.onNodeWithTag("riff:dialog").assertDoesNotExist()
        reopen()
        shell("input tap 1 ${context.resources.displayMetrics.heightPixels / 2}")
        compose.onNodeWithTag("riff:dialog").assertDoesNotExist()
        reopen()
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    PlayerUiAction.CancelSafGrantRemoval,
                    PlayerUiAction.CancelSafGrantRemoval,
                    PlayerUiAction.CancelSafGrantRemoval,
                    PlayerUiAction.ConfirmSafGrantRemoval,
                ),
                actions,
            )
            ageWarning = true
        }
        awaitActiveDialog("Old Debug build")
        shell("input keyevent KEYCODE_BACK")
        shell("input tap 1 ${context.resources.displayMetrics.heightPixels / 2}")
        compose.onNodeWithTag("riff:dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("OK").performClick()
        compose.onNodeWithTag("riff:dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(4, actions.size) }
    }

    @Test fun bootstrapTypographyErrorsIconsAndRecoveryAccessibilityUseTheSameShell() {
        var waiting by mutableStateOf(
            StorageWaitingState("Fixture removable storage", SettingsAccess.RECOVERY_REQUIRED, error = "Fixture storage error"),
        )
        var night by mutableStateOf(false)
        val startupActions = mutableListOf<StorageStartupAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.25f, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                val themed = config.themed(night)
                StorageWaitingDialog(waiting, themed, if (night) UiPalette.night(themed) else UiPalette.day(themed), startupActions::add)
            }
        }
        for (theme in listOf(false, true)) {
            for (confirmation in listOf(false, true)) {
                compose.runOnIdle {
                    night = theme
                    waiting = waiting.copy(confirmation = confirmation, busy = false)
                }
                assertTypography(if (confirmation) "Reset to Internal?" else "Waiting for USB/SD storage…")
                assertTypography("Fixture storage error")
                compose.onNodeWithText("Fixture storage error").performScrollTo().assertIsDisplayed()
                val positiveLabel = if (confirmation) "Reset" else "Forget and reset to Internal"
                val positive =
                    compose
                        .onNodeWithContentDescription(positiveLabel)
                        .assertIsDisplayed()
                        .fetchSemanticsNode()
                        .boundsInRoot
                assertEquals(100f, positive.height, 1f)
                if (confirmation) {
                    val cancel =
                        compose
                            .onNodeWithContentDescription("Cancel")
                            .assertIsDisplayed()
                            .fetchSemanticsNode()
                            .boundsInRoot
                    assertTrue(cancel.left > positive.right)
                } else {
                    compose
                        .onNodeWithContentDescription(positiveLabel)
                        .performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
                    compose.runOnIdle { assertEquals(StorageStartupAction.AUTHORIZE_RECOVERY, startupActions.last()) }
                }
                shell("input keyevent KEYCODE_BACK")
                shell("input tap 1 ${context.resources.displayMetrics.heightPixels / 2}")
                compose.onNodeWithTag("storage:waiting-dialog").assertIsDisplayed()
                val count = startupActions.size
                compose.runOnIdle { waiting = waiting.copy(busy = true) }
                compose.onNodeWithContentDescription("$positiveLabel; unavailable").assertIsNotEnabled().performClick()
                if (confirmation) compose.onNodeWithContentDescription("Cancel; unavailable").assertIsNotEnabled().performClick()
                compose.runOnIdle { assertEquals(count, startupActions.size) }
            }
        }
    }
}
