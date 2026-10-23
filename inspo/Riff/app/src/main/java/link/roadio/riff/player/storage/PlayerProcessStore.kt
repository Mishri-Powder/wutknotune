package link.roadio.riff.player.storage

import android.content.Context
import link.roadio.riff.storage.root.ApplicationDataStorage
import java.io.File

/** One process-lifetime Player SQLite owner shared by UI and Media3 service. */
object PlayerProcessStore {
    private var store: PlayerStateStore? = null
    private var rootIdentity: String? = null

    @Synchronized
    fun open(
        context: Context,
        recentHistoryGenerations: Long,
        bookmarkMinSpacingSeconds: Long,
    ): PlayerStateStore? {
        val appContext = context.applicationContext
        val storage = ApplicationDataStorage(appContext)
        val root = storage.activeRootOrNull()
        // Authority: https://wiki.271.pics/Riff/LATEST/storage-roots
        // AI-INTENT: Absence is a startup gate, never permission to open a replica or a fresh database.
        if (root == null) return null
        val identity = root.absolutePath
        if (identity == rootIdentity && store != null) return store

        closeForRootChange()
        val opened =
            runCatching {
                PlayerStateRecovery.open(File(root, PLAYER_DIRECTORY), recentHistoryGenerations, bookmarkMinSpacingSeconds).store
            }.getOrNull()
        opened?.beginPlaybackCycle()
        store = opened
        rootIdentity = identity.takeIf { opened != null }
        return opened
    }

    @Synchronized
    fun current(): PlayerStateStore? = store

    /** Storage migration/removal is the only normal reason to close the process store. */
    @Synchronized
    fun closeForRootChange() {
        // A failed close must keep migration from treating live SQLite/WAL state as a closed tree.
        // Keep ownership until close succeeds so the caller can retry or report the storage failure.
        store?.close()
        store = null
        rootIdentity = null
    }

    private const val PLAYER_DIRECTORY = "player"
}
