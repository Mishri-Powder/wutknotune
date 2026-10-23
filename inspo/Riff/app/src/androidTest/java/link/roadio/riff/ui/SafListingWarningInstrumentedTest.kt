package link.roadio.riff.ui

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.player.library.SafListingNotice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SafListingWarningInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun state(notice: SafListingNotice) =
        PlayerUiState(
            screen = PlayerScreen.MAIN,
            root = BrowserItem("root", "Library", BrowserKind.ROOT),
            nowPlaying = NowPlayingUiState(),
            safListingBugWarning = notice,
        )

    @Test fun onlyOkAcknowledgesWhileStatusBackAndOutsideCannotDismiss() {
        val context = instrumentation.targetContext
        val notice = SafListingNotice(1, listOf("Music"), 0, emptyList())
        var current by mutableStateOf(state(notice))
        val actions = mutableListOf<PlayerUiAction>()
        compose.setContent {
            PlayerComposeUi(current, UiConfig.defaults(context), CalibrationSessionState(), context) { action ->
                actions += action
                if (action == PlayerUiAction.AcknowledgeSafListingBug) current = current.copy(safListingBugWarning = null)
            }
        }
        compose.onNodeWithText(notice.title).assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.runOnIdle { current = current.copy(indexerStatus = "") }
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), true)
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK), true)
        val time = SystemClock.uptimeMillis()
        val outsideY = context.resources.displayMetrics.heightPixels / 2f
        for ((eventTime, action) in listOf(time to MotionEvent.ACTION_DOWN, time + 10 to MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, eventTime, action, 1f, outsideY, 0)
            try {
                instrumentation.uiAutomation.injectInputEvent(event, true)
            } finally {
                event.recycle()
            }
        }
        compose.onNodeWithContentDescription("OK").assertIsDisplayed().performClick()
        compose.onNodeWithTag("riff:dialog").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(PlayerUiAction.AcknowledgeSafListingBug), actions)
            assertEquals(PlayerScreen.MAIN, current.screen)
            assertEquals(emptyList<BrowserItem>(), current.root.children)
        }
    }

    @Test fun longRtlNoticeKeepsOkVisibleAtLargeFontScale() {
        val context = instrumentation.targetContext
        val notice = SafListingNotice(12, List(8) { "موسيقى " + "Long folder name ".repeat(4) }, 3, List(3) { "Unavailable folder" })
        val current = state(notice)
        var acknowledged = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                PlayerComposeUi(current, UiConfig.defaults(context), CalibrationSessionState(), context) { action ->
                    acknowledged = action == PlayerUiAction.AcknowledgeSafListingBug
                }
            }
        }
        compose.onNodeWithContentDescription("OK").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, acknowledged) }
    }
}
