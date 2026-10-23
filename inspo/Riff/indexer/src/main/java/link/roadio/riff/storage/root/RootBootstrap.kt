package link.roadio.riff.storage.root

import android.content.Context
import android.util.AtomicFile
import link.roadio.riff.storage.publish.AndroidDirectorySync
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** Small device-protected admission record, never a copy of Player or Indexer state. */
class RootBootstrap(
    context: Context,
) {
    private val directory = File(context.createDeviceProtectedStorageContext().noBackupFilesDir, "riff-bootstrap")
    private val file = AtomicFile(File(directory, "root.json"))
    private val lock = synchronized(locks) { locks.getOrPut(directory.absolutePath) { LockState() } }

    /** Read under a process-shared file lock; SharedPreferences caches are not cross-process authority. */
    fun <T> transaction(block: () -> T): T =
        synchronized(lock) {
            if (lock.current != null) {
                block()
            } else {
                require(directory.mkdirs() || directory.isDirectory)
                RandomAccessFile(File(directory, "root.lock"), "rw").use { handle ->
                    handle.channel.lock().use {
                        lock.current = read()
                        try {
                            block()
                        } finally {
                            lock.current = null
                        }
                    }
                }
            }
        }

    val all: Map<String, Any>
        get() = transaction { state().keys().asSequence().associateWith { state().get(it) } }

    fun contains(key: String): Boolean = transaction { state().has(key) }

    fun getString(
        key: String,
        fallback: String?,
    ): String? = transaction { if (state().has(key)) state().getString(key) else fallback }

    fun getBoolean(
        key: String,
        fallback: Boolean,
    ): Boolean = transaction { if (state().has(key)) state().getBoolean(key) else fallback }

    fun edit(): Editor = Editor()

    inner class Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var clear = false

        fun putString(
            key: String,
            value: String?,
        ): Editor = apply { changes[key] = value }

        fun putBoolean(
            key: String,
            value: Boolean,
        ): Editor = apply { changes[key] = value }

        fun putInt(
            key: String,
            value: Int,
        ): Editor = apply { changes[key] = value }

        fun putLong(
            key: String,
            value: Long,
        ): Editor = apply { changes[key] = value }

        fun putFloat(
            key: String,
            value: Float,
        ): Editor = apply { changes[key] = value }

        fun remove(key: String): Editor = apply { changes[key] = null }

        fun clear(): Editor = apply { clear = true }

        fun commit(): Boolean =
            transaction {
                val next = if (clear) JSONObject() else JSONObject(state().toString())
                changes.forEach { (key, value) -> if (value == null) next.remove(key) else next.put(key, value) }
                persist(next)
                lock.current = next
                true
            }
    }

    private fun state(): JSONObject = requireNotNull(lock.current)

    private fun read(): JSONObject {
        if (file.baseFile.exists() || File(directory, "root.json.bak").exists()) {
            return JSONObject(file.openRead().bufferedReader().use { it.readText() })
        }
        // No released predecessor exists: absent current state means a new installation.
        return JSONObject().also(::persist)
    }

    private fun persist(value: JSONObject) {
        val output = file.startWrite()
        try {
            output.write(value.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
        AndroidDirectorySync.sync(directory.toPath())
        AndroidDirectorySync.sync(requireNotNull(directory.parentFile).toPath())
    }

    private class LockState {
        var current: JSONObject? = null
    }

    private companion object {
        val locks = mutableMapOf<String, LockState>()
    }
}
