package link.roadio.riff.ui

import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
import android.util.Log
import link.roadio.riff.storage.publish.AndroidDirectorySync
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** All setting writes and durable lease I/O stay off the UI/playback threads. */
internal class AndroidSystemBrightnessWorkaround private constructor(
    private val context: Context,
    private val enabled: Boolean,
    file: File,
) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean()
    private val failureLogged = AtomicBoolean()
    private val lease = SystemBrightnessLease(AndroidAccess(context, file))

    init {
        // Constructed after selected-root admission on the startup worker, before Activity activation.
        runSafely { lease.restore() }
    }

    fun baseline(current: Int?): Int? = lease.baseline(current)

    fun canApply(): Boolean =
        runCatching {
            val manual =
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                ) == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            enabled && Settings.System.canWrite(context) && manual
        }.getOrDefault(false)

    fun apply(
        percent: Float?,
        onApplied: (Boolean) -> Unit = {},
    ) {
        if (closed.get()) return
        worker.execute {
            var applied = false
            runSafely {
                if (percent != null && canApply()) {
                    applied = lease.dim(percent)
                } else {
                    if (enabled && percent != null && !Settings.System.canWrite(context) && failureLogged.compareAndSet(false, true)) {
                        Log.w("RiffBacklight", "system_brightness_special_access_missing")
                    }
                    lease.restore()
                }
            }
            onApplied(applied)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.execute { runSafely { lease.restore() } }
        worker.shutdown()
    }

    private fun runSafely(action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            if (failureLogged.compareAndSet(false, true)) {
                Log.w("RiffBacklight", "system_brightness_workaround_failed type=${failure.javaClass.simpleName}")
            }
        }
    }

    companion object {
        fun create(
            context: Context,
            enabled: Boolean,
        ): AndroidSystemBrightnessWorkaround? {
            val file = File(context.createDeviceProtectedStorageContext().noBackupFilesDir, "riff-bootstrap/system-brightness-lease.json")
            if (!enabled && !file.exists() && !File(file.path + ".bak").exists()) return null
            return runCatching { AndroidSystemBrightnessWorkaround(context.applicationContext, enabled, file) }
                .getOrElse {
                    Log.w("RiffBacklight", "system_brightness_lease_unavailable type=${it.javaClass.simpleName}")
                    null
                }
        }
    }

    private class AndroidAccess(
        private val context: Context,
        path: File,
    ) : SystemBrightnessAccess {
        private val file = AtomicFile(path)

        override fun read(): Int? =
            runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()

        override fun canWrite(): Boolean = Settings.System.canWrite(context)

        override fun write(value: Int): Boolean = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)

        override fun savedLease(): BrightnessLease? {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
            val value = file.openRead().bufferedReader().use { JSONObject(it.readText()) }
            val baseline = value.getInt("baseline")
            val applied = value.getInt("applied")
            require(value.length() == 2 && baseline in 0..255 && applied in 0..255)
            return BrightnessLease(baseline, applied)
        }

        override fun saveLease(value: BrightnessLease) {
            val output = file.startWrite()
            try {
                output.write(
                    JSONObject()
                        .put("baseline", value.baseline)
                        .put("applied", value.applied)
                        .toString()
                        .toByteArray(Charsets.UTF_8),
                )
                file.finishWrite(output)
                AndroidDirectorySync.sync(file.baseFile.parentFile!!.toPath())
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
        }

        override fun clearLease() {
            file.delete()
            AndroidDirectorySync.sync(file.baseFile.parentFile!!.toPath())
        }
    }
}
