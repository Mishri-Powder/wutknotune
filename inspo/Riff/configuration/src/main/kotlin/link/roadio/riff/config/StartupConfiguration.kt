package link.roadio.riff.config

import java.io.IOException

enum class ConfigurationProblem { UNREADABLE, INVALID }

data class ConfigurationLoad(
    val snapshot: ConfigurationSnapshot,
    val problem: ConfigurationProblem? = null,
)

/**
 * One attempt per owner process. Even absent/invalid files are not reread until restart.
 * Storage admission must precede this call; waiting for a missing root must not seal defaults.
 * AI-INTENT: UI resolution and Indexer consumers use this immutable result instead of adding
 * reduced parsers or runtime reload paths. Each process owns its own admitted startup snapshot.
 * Authority: https://wiki.271.pics/Riff/LATEST/project-configuration#configuration-registry
 */
class StartupConfiguration {
    @Volatile
    private var loaded: ConfigurationLoad? = null

    @Synchronized
    fun load(readUserFile: () -> String?): ConfigurationLoad {
        loaded?.let { return it }
        val result =
            try {
                val text = readUserFile()
                if (text == null) {
                    ConfigurationLoad(PackagedConfiguration.snapshot)
                } else {
                    ConfigurationLoad(ProjectConfiguration.parse(text))
                }
            } catch (_: IOException) {
                ConfigurationLoad(PackagedConfiguration.snapshot, ConfigurationProblem.UNREADABLE)
            } catch (_: IllegalArgumentException) {
                ConfigurationLoad(PackagedConfiguration.snapshot, ConfigurationProblem.INVALID)
            } catch (_: IllegalStateException) {
                ConfigurationLoad(PackagedConfiguration.snapshot, ConfigurationProblem.INVALID)
            } catch (_: NoSuchElementException) {
                ConfigurationLoad(PackagedConfiguration.snapshot, ConfigurationProblem.INVALID)
            } catch (_: SecurityException) {
                ConfigurationLoad(PackagedConfiguration.snapshot, ConfigurationProblem.UNREADABLE)
            }
        loaded = result
        return result
    }

    fun currentOrNull(): ConfigurationLoad? = loaded
}
