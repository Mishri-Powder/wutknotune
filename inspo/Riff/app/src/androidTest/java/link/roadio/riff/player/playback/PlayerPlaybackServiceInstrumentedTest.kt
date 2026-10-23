package link.roadio.riff.player.playback

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class PlayerPlaybackServiceInstrumentedTest {
    @Test
    fun servicePublishesLargestPackagedThresholdWhileDispatcherChoosesCategory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        @Suppress("DEPRECATION")
        val requested =
            context.packageManager
                .getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS,
                ).requestedPermissions
                .orEmpty()
                .toSet()
        assertEquals(
            setOf(
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                "android.permission.WAKE_LOCK",
                "android.permission.WRITE_SETTINGS",
            ),
            requested,
        )
        val token = SessionToken(context, ComponentName(context, RiffPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        val thresholdMs = AtomicLong()

        try {
            instrumentation.runOnMainSync {
                thresholdMs.set(controller.maxSeekToPreviousPosition)
                assertFalse(controller.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
                assertFalse(controller.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
            }
            assertEquals(15_000L, thresholdMs.get())
        } finally {
            instrumentation.runOnMainSync {
                MediaController.releaseFuture(future)
            }
        }
    }
}
