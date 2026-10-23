package link.roadio.riff.player.playback

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.config.ProjectConfiguration
import link.roadio.riff.shared.config.AndroidProjectConfiguration
import link.roadio.riff.storage.root.ApplicationDataStorage
import link.roadio.riff.ui.UiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Opt-in cold-process probe: the cached configuration intentionally cannot be reset within a process. */
class ConfigurationStartupInstrumentedTest {
    @Test
    fun headlessStartupAppliesUserConfigOnceBeforeOpeningTheQueue() {
        assumeTrue(
            "Run this class alone with riffConfigStartupProbe=true in the disposable target.",
            InstrumentationRegistry.getArguments().getString("riffConfigStartupProbe") == "true",
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertNull(AndroidProjectConfiguration.currentOrNull())
        val storage = ApplicationDataStorage(context)
        assertTrue(storage.prepareForStartup())
        val file = File(storage.activeRoot(), "player/" + ProjectConfiguration.OVERRIDE_FILE_NAME)
        assertTrue("Never overwrite an existing fixture", !file.exists())
        file.parentFile!!.mkdirs()
        file.writeText(
            """{"schemaVersion":3,"activeSkin":"startup","player":{"rewindPreviousThresholdSeconds":{"music":31}},
            "skins":{"startup":{"behavior":{"idleTimeoutSeconds":45}}}}""",
        )
        val token = SessionToken(context, ComponentName(context, RiffPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        try {
            val controller = future.get(10, TimeUnit.SECONDS)
            val threshold = AtomicLong()
            val deadline = SystemClock.elapsedRealtime() + 10_000L
            do {
                instrumentation.runOnMainSync { threshold.set(controller.maxSeekToPreviousPosition) }
                if (threshold.get() == 31_000L) break
                SystemClock.sleep(10)
            } while (SystemClock.elapsedRealtime() < deadline)
            assertEquals(31_000L, threshold.get())
            val initial = UiConfig.load(context)
            assertEquals("startup", initial.activeSkin)
            assertEquals(45L, initial.long("behavior.idleTimeoutSeconds"))
            file.writeText("""{"schemaVersion":1}""")
            assertEquals("startup", UiConfig.load(context).activeSkin)
            assertNull(AndroidProjectConfiguration.currentOrNull()!!.problem)
        } finally {
            instrumentation.runOnMainSync { MediaController.releaseFuture(future) }
            context.stopService(Intent(context, RiffPlaybackService::class.java))
            assertTrue(file.delete())
        }
    }
}
