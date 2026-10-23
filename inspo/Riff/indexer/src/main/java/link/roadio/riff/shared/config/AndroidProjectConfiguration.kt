package link.roadio.riff.shared.config

import android.content.Context
import android.os.Looper
import android.util.Log
import link.roadio.riff.config.ConfigurationLoad
import link.roadio.riff.config.ConfigurationProblem
import link.roadio.riff.config.PackagedConfiguration
import link.roadio.riff.config.ProjectConfiguration
import link.roadio.riff.config.StartupConfiguration
import link.roadio.riff.storage.root.ApplicationDataStorage
import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.util.concurrent.atomic.AtomicBoolean

/** Process-local startup snapshot; no observer, retained-root search or hot reload. */
object AndroidProjectConfiguration {
    private val startup = StartupConfiguration()
    private val problemShown = AtomicBoolean()

    fun currentOrNull(): ConfigurationLoad? = startup.currentOrNull()

    fun takeStartupProblem(): ConfigurationProblem? = startup.currentOrNull()?.problem?.takeIf { problemShown.compareAndSet(false, true) }

    @Synchronized
    fun load(context: Context): ConfigurationLoad {
        startup.currentOrNull()?.let { return it }
        check(Looper.myLooper() != Looper.getMainLooper()) { "Load startup configuration on a worker" }
        val storage = ApplicationDataStorage(context)
        val root = storage.activeRootOrNull() ?: return ConfigurationLoad(PackagedConfiguration.snapshot)
        return startup
            .load {
                val file = File(root, "player/" + ProjectConfiguration.OVERRIDE_FILE_NAME)
                try {
                    Files.newBufferedReader(file.toPath()).use { it.readText() }
                } catch (_: NoSuchFileException) {
                    null
                }
            }.also { loaded ->
                storage.cacheApplicationMode(loaded.snapshot.applicationMode)
                loaded.problem?.let { Log.e("RiffConfig", "startup_config_rejected reason=" + it.name) }
            }
    }
}
