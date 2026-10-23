package link.roadio.riff.player.library

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/** Lightweight consumer subscription to the process-scoped immutable Player library repository. */
class PlayerCuetreeClient(
    context: Context,
    onLibrary: (PlayerLibraryModel) -> Unit,
    onUnavailable: (String) -> Unit,
    onIndexerStatus: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit = {},
    excludedGenreIds: Set<Long> = emptySet(),
    projection: () -> PlayerLibraryProjection = { PlayerLibraryProjection() },
    onSafListingBug: (SafListingNotice?) -> Unit = {},
) : AutoCloseable {
    private val repository = PlayerLibraryRepository.get(context, excludedGenreIds)
    private val observer =
        PlayerLibraryObserver(
            onLibrary,
            onUnavailable,
            onIndexerStatus,
            onLoadingChanged,
            projection,
            onSafListingBug,
        )
    private val started = AtomicBoolean()
    private val closed = AtomicBoolean()

    fun start(): Boolean {
        if (closed.get()) return false
        if (!started.compareAndSet(false, true)) return true
        return repository.attach(observer)
    }

    fun acknowledgeSafListingBug() {
        if (!closed.get()) repository.acknowledgeSafListingBug()
    }

    fun requestReload() {
        if (!closed.get()) repository.requestReload(observer.stableProjection())
    }

    fun activateProjection(requested: PlayerLibraryProjection): Boolean =
        !closed.get() && repository.activateProjection(observer, requested)

    fun requestRescan(): Boolean = !closed.get() && repository.requestRescan(observer)

    fun resolveGrantTreeUri(rootBindingId: Long): String? = if (closed.get()) null else repository.resolveGrantTreeUri(rootBindingId)

    fun quiesceForSafGrantChange(): Boolean = !closed.get() && repository.quiesceForSafGrantChange()

    fun resumeAfterSafGrantChange(requestFullPipeline: Boolean): Boolean =
        !closed.get() && repository.resumeAfterSafGrantChange(requestFullPipeline)

    fun quiesceForStorageMigration(): Boolean = !closed.get() && repository.quiesceForStorageMigration(observer)

    fun activateStorageRootAfterMigration(
        location: String,
        portableVolumeId: String?,
        requestFullPipeline: Boolean,
    ): Boolean =
        !closed.get() &&
            repository.activateStorageRootAfterMigration(
                observer,
                location,
                portableVolumeId,
                requestFullPipeline,
            )

    fun requestDatabaseRebuild(mode: Int): Boolean = !closed.get() && repository.requestDatabaseRebuild(mode)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (started.get()) repository.detach(observer)
    }

    private fun PlayerLibraryObserver.stableProjection(): PlayerLibraryProjection {
        val current = projection()
        return current.copy(audiobookOverrides = current.audiobookOverrides.toMap())
    }
}
