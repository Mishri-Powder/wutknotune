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

/** Run alone after clearing ONLY the disposable target, with riffSystemBrightnessProbe=true. */
class SystemBrightnessWorkaroundInstrumentedTest {
    @Test
    fun partialHuConfigControlsGlobalDimmingAndLifecycleRestoration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("riffSystemBrightnessProbe") == "true")
        val context = instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".instrumented"))
        assertTrue(Settings.System.canWrite(context))
        assertNull(AndroidProjectConfiguration.currentOrNull())
        val root = ApplicationDataStorage(context).activeRoot()
        val file = File(root, "player/" + ProjectConfiguration.OVERRIDE_FILE_NAME)
        assertFalse(file.exists())
        file.writeText("""{"skins":{"default":{"behavior":{"systemBrightnessWorkaroundEnabled":true}}}}""")
        val original = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        val originalMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 102)
            SystemClock.sleep(250)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await(scenario) {
                    MainActivity::class.java
                        .getDeclaredField("controller")
                        .apply { isAccessible = true }
                        .get(it) != null &&
                        it.hasWindowFocus()
                }
                scenario.onActivity { activity ->
                    val config = requireNotNull(UiConfig.currentOrNull())
                    assertTrue(config.boolean("behavior.systemBrightnessWorkaroundEnabled"))
                    assertEquals(30L, config.long("behavior.idleTimeoutSeconds"))
                    assertEquals(3, config.int("indexer.pipelineDepth"))
                    assertEquals("default", config.activeSkin)
                    MainActivity::class.java
                        .getDeclaredField("idleTimeoutMillis")
                        .apply { isAccessible = true }
                        .setLong(activity, 600L)
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1))
                }
                await(scenario) { brightness(context) == 31 }
                SystemClock.sleep(1_000)
                assertEquals(31, brightness(context))
                assertEquals(0, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE))
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 180)
                await(scenario) { brightness(context) == 54 }
                scenario.onActivity { it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1)) }
                await(scenario) { brightness(context) == 180 }
                await(scenario) { brightness(context) == 54 }
                scenario.moveToState(Lifecycle.State.CREATED)
                await(scenario) { brightness(context) == 180 }
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

    @Test
    fun missingAccessOrAutomaticModeKeepsTheWindowFallback() {
        val fixture = InstrumentationRegistry.getArguments().getString("riffSystemBrightnessFallback")
        assumeTrue(fixture == "denied" || fixture == "auto")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".instrumented"))
        assertEquals(fixture != "denied", Settings.System.canWrite(context))
        assertEquals(
            if (fixture ==
                "auto"
            ) {
                1
            } else {
                0
            },
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE),
        )
        val lease = File(context.createDeviceProtectedStorageContext().noBackupFilesDir, "riff-bootstrap/system-brightness-lease.json")
        assertFalse(lease.exists())
        val original = brightness(context)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) {
                MainActivity::class.java
                    .getDeclaredField("controller")
                    .apply { isAccessible = true }
                    .get(it) != null &&
                    it.hasWindowFocus()
            }
            scenario.onActivity { activity ->
                assertTrue(requireNotNull(UiConfig.currentOrNull()).boolean("behavior.systemBrightnessWorkaroundEnabled"))
                MainActivity::class.java
                    .getDeclaredField("idleTimeoutMillis")
                    .apply { isAccessible = true }
                    .setLong(activity, 600L)
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1))
            }
            await(scenario) { it.window.attributes.screenBrightness >= 0f }
            SystemClock.sleep(300)
            assertFalse(lease.exists())
            if (fixture == "denied") assertEquals(original, brightness(context))
        }
        assertEquals(
            if (fixture ==
                "auto"
            ) {
                1
            } else {
                0
            },
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE),
        )
    }

    private fun brightness(context: Context): Int = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)

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
