package link.roadio.riff.ui

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.MainActivity
import link.roadio.riff.player.core.settings.PlayerDimBacklightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the shipping Activity clock/window policy in the disposable application only. */
class MainActivityBacklightInstrumentedTest {
    @Test
    fun expiryInputLifecycleAndInstallationPreferenceWorkTogether() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            context
                .getSharedPreferences("riff-installation-settings", Context.MODE_PRIVATE)
                .edit()
                .remove("dim-backlight")
                .commit(),
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { controller(it) != null && it.hasWindowFocus() }
            scenario.onActivity { activity ->
                assertEquals(PlayerDimBacklightMode.AUTO, controller(activity)?.state?.settings?.dimBacklight)
                // Shorten only the fixture interval; production default and the scheduling path are retained.
                val timeout = MainActivity::class.java.getDeclaredField("idleTimeoutMillis").apply { isAccessible = true }
                assertEquals(30_000L, timeout.getLong(activity))
                timeout.setLong(activity, 500L)
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1))
                assertEquals(-1f, activity.window.attributes.screenBrightness, 0f)
            }
            await(scenario) { it.window.attributes.screenBrightness >= 0f }
            var baseline = 0
            scenario.onActivity { activity ->
                baseline = Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                assertEquals(baseline / 255f * 0.3f, activity.window.attributes.screenBrightness, 0.0001f)
                assertEquals(PlayerScreen.MAIN, controller(activity)?.state?.screen)
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1))
                assertEquals(-1f, activity.window.attributes.screenBrightness, 0f)
            }
            SystemClock.sleep(150)
            scenario.onActivity { assertEquals(-1f, it.window.attributes.screenBrightness, 0f) }
            await(scenario) { it.window.attributes.screenBrightness >= 0f }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { assertEquals(-1f, it.window.attributes.screenBrightness, 0f) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertEquals(-1f, activity.window.attributes.screenBrightness, 0f)
                assertEquals(baseline, Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
                MainActivity::class.java
                    .getDeclaredField("idleTimeoutMillis")
                    .apply { isAccessible = true }
                    .setLong(activity, 30_000L)
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1))
                val ui = requireNotNull(controller(activity))
                ui.dispatch(PlayerUiAction.OpenSettingsLongPress)
                ui.dispatch(PlayerUiAction.OpenMoreSettings)
                ui.dispatch(PlayerUiAction.SetDraftDimBacklight(PlayerDimBacklightMode.OFF))
                ui.dispatch(PlayerUiAction.SaveMoreSettings)
            }
            await(scenario) { controller(it)?.state?.settings?.dimBacklight == PlayerDimBacklightMode.OFF }
            scenario.onActivity { requireNotNull(controller(it)).dispatch(PlayerUiAction.CancelSettings) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { controller(it) != null }
            scenario.onActivity { activity ->
                val ui = requireNotNull(controller(activity))
                assertEquals(PlayerDimBacklightMode.OFF, ui.state.settings.dimBacklight)
                ui.dispatch(PlayerUiAction.OpenSettingsLongPress)
                ui.dispatch(PlayerUiAction.OpenMoreSettings)
                ui.dispatch(PlayerUiAction.SetDraftDimBacklight(PlayerDimBacklightMode.AUTO))
                ui.dispatch(PlayerUiAction.SaveMoreSettings)
            }
            await(scenario) { controller(it)?.state?.settings?.dimBacklight == PlayerDimBacklightMode.AUTO }
            scenario.onActivity { requireNotNull(controller(it)).dispatch(PlayerUiAction.CancelSettings) }
        }
    }

    private fun controller(activity: MainActivity): RiffPlayerUiController? =
        MainActivity::class.java
            .getDeclaredField("controller")
            .apply { isAccessible = true }
            .get(activity) as? RiffPlayerUiController

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { ready = condition(it) }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Activity condition timed out", ready)
    }
}
