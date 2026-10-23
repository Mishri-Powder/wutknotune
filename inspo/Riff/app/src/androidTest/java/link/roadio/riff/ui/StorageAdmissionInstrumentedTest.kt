@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.ui

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import link.roadio.riff.MainActivity
import link.roadio.riff.player.playback.RiffPlaybackService
import link.roadio.riff.player.storage.PlayerProcessStore
import link.roadio.riff.storage.root.RootBootstrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Verifies production launcher and service admission in the disposable instrumented package. */
class StorageAdmissionInstrumentedTest {
    @Test fun missingRootBlocksLauncherPlaybackAndIndexerBeforeStateOpens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = RootBootstrap(context)
        val original = preferences.all.toMap()
        val playback = Intent(context, RiffPlaybackService::class.java)
        val indexer = Intent().setClassName(context, "link.roadio.riff.indexer.android.process.IndexerProcessService")
        context.stopService(playback)
        context.stopService(indexer)
        Thread.sleep(500)
        PlayerProcessStore.closeForRootChange()
        preferences
            .edit()
            .putString("location", "SD")
            .putString("portable_volume_id", "missing-test-volume")
            .commit()
        // Earlier tests intentionally retain a process-wide library binding. Simulate a cold
        // Indexer here, not an already admitted runtime session with cached Binder handles.
        context
            .getSystemService(ActivityManager::class.java)
            .runningAppProcesses
            .filter { it.processName == "${context.packageName}:indexer" }
            .forEach { Process.killProcess(it.pid) }
        Thread.sleep(500)
        val admitted = CountDownLatch(1)
        var unexpectedBinder = false
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName,
                    service: IBinder,
                ) {
                    unexpectedBinder = true
                    admitted.countDown()
                }

                override fun onNullBinding(name: ComponentName) {
                    admitted.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
        var bound = false
        var controllerFuture: ListenableFuture<MediaController>? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                Thread.sleep(InstrumentationRegistry.getArguments().getString("storageGateWaitMillis")?.toLongOrNull() ?: 2500)
                scenario.onActivity { activity ->
                    val field = MainActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
                    assertNull(field.get(activity))
                }
                assertNull(PlayerProcessStore.current())
                assertEquals("SD", preferences.getString("location", null))
            }
            instrumentation.runOnMainSync {
                controllerFuture =
                    MediaController
                        .Builder(context, SessionToken(context, ComponentName(context, RiffPlaybackService::class.java)))
                        .buildAsync()
            }
            assertTrue("Playback must reject a blocked cold start", runCatching { controllerFuture!!.get(5, TimeUnit.SECONDS) }.isFailure)
            bound = context.bindService(indexer, connection, Context.BIND_AUTO_CREATE)
            assertTrue("Indexer must return a null-binding callback", bound && admitted.await(5, TimeUnit.SECONDS))
            assertTrue("Indexer must not expose its data binder", !unexpectedBinder)
            assertNull(PlayerProcessStore.current())
            assertEquals("missing-test-volume", preferences.getString("portable_volume_id", null))
        } finally {
            if (bound) context.unbindService(connection)
            controllerFuture?.let { future -> instrumentation.runOnMainSync { MediaController.releaseFuture(future) } }
            context.stopService(playback)
            context.stopService(indexer)
            val editor = preferences.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            editor.commit()
        }
    }
}
