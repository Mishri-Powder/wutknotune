package link.roadio.riff.ui

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.MainActivity
import link.roadio.riff.config.ProjectConfiguration
import link.roadio.riff.shared.config.AndroidProjectConfiguration
import link.roadio.riff.storage.root.ApplicationDataStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/** Run alone in a cleared disposable target with explicit fixture access and manual brightness. */
class DimBacklightPercentInstrumentedTest {
    @Test
    fun themeTargetsScaleTheIndependentBaselineOnlyAfterExpiry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("riffDimPercentProbe") == "true")
        val global = args.getString("riffDimPercentGlobal") == "true"
        val context = instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".instrumented"))
        assertTrue(Settings.System.canWrite(context))
        assertNull(AndroidProjectConfiguration.currentOrNull())
        val file = File(ApplicationDataStorage(context).activeRoot(), "player/" + ProjectConfiguration.OVERRIDE_FILE_NAME)
        assertFalse(file.exists())
        file.parentFile!!.mkdirs()
        file.writeText(
            """{"skins":{"default":{"behavior":{"systemBrightnessWorkaroundEnabled":$global,"dimBacklightPercent":{"day":15,"night":1}}}}}""",
        )
        val original = brightness(context)
        val originalMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await(scenario) { controller(it) != null && it.hasWindowFocus() }
                scenario.onActivity { activity ->
                    val config = requireNotNull(UiConfig.currentOrNull())
                    assertEquals(global, config.boolean("behavior.systemBrightnessWorkaroundEnabled"))
                    assertEquals(global, config.themed(true).boolean("behavior.systemBrightnessWorkaroundEnabled"))
                    assertEquals(15f, config.themed(false).float("behavior.dimBacklightPercent"), 0f)
                    assertEquals(1f, config.themed(true).float("behavior.dimBacklightPercent"), 0f)
                    assertEquals(30L, config.long("behavior.idleTimeoutSeconds"))
                    assertEquals(3, config.int("indexer.pipelineDepth"))
                    val ui = requireNotNull(controller(activity))
                    ui.dispatch(PlayerUiAction.OpenSettingsLongPress)
                    ui.dispatch(PlayerUiAction.SetDraftTheme(ColorScheme.DAY))
                    ui.dispatch(PlayerUiAction.SaveSettings)
                }
                await(scenario) {
                    controller(it)?.state?.settings?.colorScheme == ColorScheme.DAY &&
                        controller(it)?.state?.settingsDraft == null
                }
                scenario.onActivity { activity ->
                    MainActivity::class.java
                        .getDeclaredField("idleTimeoutMillis")
                        .apply { isAccessible = true }
                        .setLong(activity, 700L)
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1))
                    assertEquals(-1f, activity.window.attributes.screenBrightness, 0f)
                }
                assertEquals(128, brightness(context))
                awaitTarget(scenario, context, 128, 15f, global)
                SystemClock.sleep(400)
                awaitTarget(scenario, context, 128, 15f, global)
                // AI-INTENT: Typed preview commands exercise theme re-evaluation
                // without user input resetting idle.
                // https://wiki.271.pics/Riff/LATEST/player-inactivity#window-brightness-policy
                scenario.onActivity { activity ->
                    val ui = requireNotNull(controller(activity))
                    ui.dispatch(PlayerUiAction.OpenSettingsLongPress)
                    ui.dispatch(PlayerUiAction.SetDraftTheme(ColorScheme.NIGHT))
                }
                awaitTarget(scenario, context, 128, 1f, global)
                SystemClock.sleep(2_000)
                awaitTarget(scenario, context, 128, 1f, global)
                scenario.onActivity { requireNotNull(controller(it)).dispatch(PlayerUiAction.SetDraftTheme(ColorScheme.DAY)) }
                awaitTarget(scenario, context, 128, 15f, global)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 180)
                awaitTarget(scenario, context, 180, 15f, global)
                scenario.onActivity { it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1)) }
                await(scenario) { it.window.attributes.screenBrightness == -1f && brightness(context) == 180 }
                SystemClock.sleep(150)
                scenario.onActivity { assertEquals(-1f, it.window.attributes.screenBrightness, 0f) }
                awaitTarget(scenario, context, 180, 15f, global)
                scenario.moveToState(Lifecycle.State.CREATED)
                await(scenario) { it.window.attributes.screenBrightness == -1f && brightness(context) == 180 }
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { assertEquals(-1f, it.window.attributes.screenBrightness, 0f) }
            }
            awaitBrightness(context, 180)
            assertFalse(
                File(
                    context.createDeviceProtectedStorageContext().noBackupFilesDir,
                    "riff-bootstrap/system-brightness-lease.json",
                ).exists(),
            )
        } finally {
            file.delete()
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, original)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, originalMode)
        }
    }

    private fun controller(activity: MainActivity): RiffPlayerUiController? =
        MainActivity::class.java
            .getDeclaredField("controller")
            .apply { isAccessible = true }
            .get(activity) as? RiffPlayerUiController

    private fun brightness(context: Context): Int = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)

    private fun awaitTarget(
        scenario: ActivityScenario<MainActivity>,
        context: Context,
        baseline: Int,
        percent: Float,
        global: Boolean,
    ) {
        val window = if (global) -1f else baseline * percent / 100f / 255f
        val system = if (global) (baseline * percent / 100f).roundToInt() else baseline
        await(scenario) { kotlin.math.abs(it.window.attributes.screenBrightness - window) < 0.0001f && brightness(context) == system }
    }

    private fun awaitBrightness(
        context: Context,
        value: Int,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (brightness(context) != value && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertEquals(value, brightness(context))
    }

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
