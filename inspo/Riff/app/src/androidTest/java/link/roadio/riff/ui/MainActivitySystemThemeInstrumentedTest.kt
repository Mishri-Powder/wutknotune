package link.roadio.riff.ui

import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Changes the real Android night configuration and restores the device's original policy. */
class MainActivitySystemThemeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {
            it.readBytes().toString(Charsets.UTF_8).trim()
        }

    @Test
    fun systemNightChangeRepaintsWithoutInputOrDomainMutation() {
        val original = shell("cmd uimode night").removePrefix("Night mode: ").trim()
        assertTrue(original in listOf("yes", "no", "auto", "custom"))
        try {
            shell("cmd uimode night no")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await(scenario) { controller(it) != null && it.hasWindowFocus() }
                scenario.onActivity { activity ->
                    val ui = requireNotNull(controller(activity))
                    ui.dispatch(PlayerUiAction.CancelSafGrantSelector)
                    ui.dispatch(PlayerUiAction.CancelSettings)
                    ui.dispatch(PlayerUiAction.OpenMain)
                    assertEquals(ColorScheme.SYSTEM, ui.state.settings.colorScheme)
                }
                assertBackground(false)
                for (dark in listOf(true, false, true)) {
                    shell("cmd uimode night ${if (dark) "yes" else "no"}")
                    await(scenario) { activity ->
                        val current = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        current == if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    assertBackground(dark)
                    scenario.onActivity { activity ->
                        val ui = requireNotNull(controller(activity))
                        assertEquals(ColorScheme.SYSTEM, ui.state.settings.colorScheme)
                        assertEquals(null, ui.state.settingsDraft)
                        assertEquals(PlayerScreen.MAIN, ui.state.screen)
                    }
                }
            }
        } finally {
            shell("cmd uimode night $original")
        }
    }

    private fun assertBackground(dark: Boolean) {
        val config = UiConfig.defaults(instrumentation.targetContext)
        val expected = (if (dark) UiPalette.night(config) else UiPalette.day(config)).selectBackground
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var observed = 0
        do {
            instrumentation.waitForIdleSync()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                observed = bitmap.getPixel(2, bitmap.height / 2)
            } finally {
                bitmap.recycle()
            }
            if (observed == expected) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        assertEquals("Rendered background for night=$dark", expected, observed)
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
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { ready = condition(it) }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Activity condition timed out", ready)
    }
}
