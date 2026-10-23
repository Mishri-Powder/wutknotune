package link.roadio.riff.indexer.android.process

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.Flags
import link.roadio.cuetree.NodeKind
import link.roadio.riff.indexer.android.cuetree.AndroidCuetreeIdentity
import link.roadio.riff.indexer.android.cuetree.AndroidCuetreePipeline
import link.roadio.riff.indexer.android.cuetree.AndroidCuetreeStore
import link.roadio.riff.indexer.android.cuetree.AndroidSafProvider
import link.roadio.riff.indexer.android.cuetree.AndroidScanAcquisitionGuard
import link.roadio.riff.indexer.android.ipc.AndroidIndexerArtifactBinder
import link.roadio.riff.indexer.android.ipc.AndroidIndexerArtifactCatalog
import link.roadio.riff.indexer.android.ipc.ReliableCommands
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.storage.hash.nativeXxh3
import link.roadio.riff.storage.root.ApplicationDataLocation
import link.roadio.riff.storage.root.ApplicationDataSelection
import link.roadio.riff.storage.root.ApplicationDataStorage
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android process anchor for all Indexer work.
 *
 * Player binds through AIDL; it never scans or constructs Cuetree projections. A single executor
 * serializes scans and rebuilds, [workerGate] coalesces requests/cancellation, and [rootGate] makes repository
 * replacement atomic with descriptor acquisition during storage migration.
 */
class IndexerProcessService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val workerGate = Any()
    private val rootGate = Any()
    private val stopped = AtomicBoolean()
    private val migrationQuiesced = AtomicBoolean()
    private val safGrantQuiesced = AtomicBoolean()
    private val pipelineScheduled = AtomicBoolean()
    private val rerunRequested = AtomicBoolean()
    private val cancellationRevision = AtomicInteger()
    private val pendingRebuild = AtomicInteger()
    private val lastPipelineSucceeded = AtomicBoolean(true)
    private var migrationFence: Future<*>? = null
    private var safGrantFence: Future<*>? = null
    private var safGrantFenceRequiresSuccess = false

    @Volatile private var rootConfigured = false
    private lateinit var indexerRoot: File
    private lateinit var dataStorage: ApplicationDataStorage
    private var cuetreeStore: AndroidCuetreeStore? = null
    private lateinit var binder: AndroidIndexerArtifactBinder
    private lateinit var pipeline: AndroidCuetreePipeline

    override fun onCreate() {
        super.onCreate()
        check(Application.getProcessName().endsWith(":indexer")) {
            "IndexerProcessService must run in the :indexer process"
        }
        dataStorage = ApplicationDataStorage(this)
        if (!runCatching(dataStorage::prepareForStartup).getOrDefault(false)) {
            Log.i(LOG_TAG, "startup_waiting_for_selected_storage")
            stopSelf()
            return
        }
        binder =
            AndroidIndexerArtifactBinder(
                AndroidIndexerArtifactCatalog { kind, volumeId ->
                    synchronized(rootGate) {
                        if (migrationQuiesced.get() || !rootConfigured || volumeId != AndroidCuetreeStore.VOLUME) {
                            null
                        } else {
                            cuetreeStore?.currentArtifact(kind)
                        }
                    }
                },
                requestLibraryRescan = ::requestPipeline,
                quiesceForSafGrantChange = ::quiesceForSafGrantChange,
                resumeAfterSafGrantChange = ::resumeAfterSafGrantChange,
                requestDatabaseRebuild = ::requestRebuild,
                resolveGrantTreeUri = ::resolveGrantTreeUri,
                quiesceForStorageMigration = ::quiesceForStorageMigration,
                activateStorageRootAfterMigration = ::activateStorageRootAfterMigration,
            )
        executor.execute {
            runCatching { configureOrPlanRebuild(dataStorage.selection()) }
                .onSuccess {
                    // Existing Cuetree data is available before the queued scan starts.
                    cuetreeStore?.currentArtifact(link.roadio.riff.indexer.core.ipc.IndexerArtifactKind.CUMD)?.let { ready ->
                        ready.descriptor.close()
                        binder.notifyArtifactAvailable(ready.metadata)
                    }
                    requestPipeline()
                }.onFailure { error ->
                    lastPipelineSucceeded.set(false)
                    binder.publishStatus(
                        if (::indexerRoot.isInitialized && dataStorage.selectedRootAvailable()) {
                            "Music library update stopped after an error"
                        } else {
                            "Selected application data storage is unavailable"
                        },
                    )
                    Log.w(LOG_TAG, "selected_storage_unavailable type=${error.javaClass.simpleName}", error)
                }
        }
    }

    private fun resolveGrantTreeUri(rootBindingId: Long): String? =
        synchronized(rootGate) {
            if (migrationQuiesced.get() || !rootConfigured) return@synchronized null
            AndroidSafProvider(contentResolver)
                .roots(publishMarkers = false)
                .firstOrNull { root ->
                    root.identity?.let { nativeXxh3(it.toByteArray(Charsets.UTF_8)) } == rootBindingId
                }?.locator
        }

    private fun configureOrPlanRebuild(selection: ApplicationDataSelection) {
        try {
            configureRoot(selection)
        } catch (error: Exception) {
            if (!libraryRequiresRecreation(error) || !dataStorage.selectedRootAvailable()) throw error
            // AI-INTENT: Reuse the serialized full reset; preserve selected-root and IPC fences.
            // https://wiki.271.pics/Riff/LATEST/indexer-library-lifecycle#automatic-library-recreation
            pendingRebuild.getAndUpdate { it or DatabaseRebuild.AU_IA.mask }
            Log.i(LOG_TAG, "automatic_library_rebuild reason=" + error.javaClass.simpleName)
        }
    }

    private fun configureRoot(selection: ApplicationDataSelection) {
        synchronized(rootGate) {
            rootConfigured = false
            cuetreeStore = null
            indexerRoot = File(dataStorage.rootFor(selection), "indexer")
            val store = AndroidCuetreeStore(File(indexerRoot, "cuetree"), applicationContext)
            cuetreeStore = store
            val previous = store.read()
            if (previous != null &&
                (
                    previous.identityScheme != AndroidCuetreeIdentity(applicationContext).scheme ||
                        previous.metadataRevision != link.roadio.riff.indexer.android.cuetree.AndroidCuetreeMetadataRevision.current
                )
            ) {
                throw IncompatibleLibraryException()
            }
            store.publicationListener = binder::notifyArtifactAvailable
            pipeline =
                AndroidCuetreePipeline(
                    applicationContext,
                    store,
                    binder::publishStatus,
                    safListingIssues = binder::publishSafListingIssues,
                    acquisitionGuard = AndroidScanAcquisitionGuard(binder),
                )
            rootConfigured = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (::binder.isInitialized && runCatching(dataStorage::prepareForStartup).getOrDefault(false)) binder else null

    override fun onDestroy() {
        stopped.set(true)
        migrationQuiesced.set(true)
        safGrantQuiesced.set(true)
        cancellationRevision.incrementAndGet()
        if (::binder.isInitialized) {
            binder.publishStatus("")
            binder.close()
        }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun requestPipeline() {
        synchronized(workerGate) {
            if (stopped.get() || migrationQuiesced.get() || safGrantQuiesced.get() || (!rootConfigured && pendingRebuild.get() == 0)) return
            rerunRequested.set(true)
            if (!pipelineScheduled.compareAndSet(false, true)) return
        }
        executor.execute {
            try {
                do {
                    if (safGrantQuiesced.get()) break
                    rerunRequested.set(false)
                    val rebuild = pendingRebuild.getAndSet(0).takeIf { it != 0 }?.let(DatabaseRebuild::fromMask)
                    val revision = cancellationRevision.get()
                    val cancellation = cancellationFor(revision)
                    runCatching {
                        rebuild?.let(::resetDatabase)
                        pipeline.run(cancellation)
                    }.onSuccess {
                        lastPipelineSucceeded.set(true)
                        binder.publishStatus("")
                    }.onFailure { error ->
                        lastPipelineSucceeded.set(false)
                        if (!cancellation.cancelled) {
                            binder.publishStatus("Music library update stopped after an error")
                            Log.e(LOG_TAG, "indexer_pipeline_failed type=" + error.javaClass.simpleName, error)
                        }
                    }
                } while (
                    rerunRequested.get() &&
                    !stopped.get() &&
                    !migrationQuiesced.get() &&
                    !safGrantQuiesced.get()
                )
            } finally {
                pipelineScheduled.set(false)
                // Authority: https://wiki.271.pics/Riff/LATEST/runtime-architecture
                // AI-INTENT: Close the race where a Binder request arrives as this worker exits.
                if (
                    rerunRequested.get() &&
                    !stopped.get() &&
                    !migrationQuiesced.get() &&
                    !safGrantQuiesced.get()
                ) {
                    requestPipeline()
                }
            }
        }
    }

    private fun requestRebuild(mode: Int) {
        synchronized(workerGate) {
            check(!migrationQuiesced.get()) { "Indexer is quiesced for storage migration" }
            check(!safGrantQuiesced.get()) { "Indexer is quiesced for media-folder change" }
            check(::indexerRoot.isInitialized && dataStorage.selectedRootAvailable()) {
                "Selected application data storage is unavailable"
            }
            val rebuild = DatabaseRebuild.fromMask(mode)
            check(rootConfigured || rebuild != DatabaseRebuild.AU) {
                "The unavailable Cuetree index requires an IA rebuild"
            }
            pendingRebuild.getAndUpdate { it or rebuild.mask }
            cancellationRevision.incrementAndGet()
            binder.publishStatus("Preparing the selected library rebuild…")
            requestPipeline()
        }
    }

    private fun quiesceForSafGrantChange(): Boolean {
        // SAF grant barrier: drain the active old-grant pipeline without cancelling it.
        val fence =
            synchronized(workerGate) {
                if (stopped.get() || migrationQuiesced.get() || !rootConfigured) return false
                if (safGrantQuiesced.compareAndSet(false, true)) {
                    safGrantFenceRequiresSuccess = pipelineScheduled.get()
                    rerunRequested.set(false)
                    executor.submit {}.also { safGrantFence = it }
                } else {
                    safGrantFence ?: return false
                }
            }
        return runCatching {
            fence.get(SAF_GRANT_QUIESCE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            check(!pipelineScheduled.get()) { "Indexer worker did not become idle" }
            check(!safGrantFenceRequiresSuccess || lastPipelineSucceeded.get()) {
                "Active Indexer pipeline did not complete successfully"
            }
            true
        }.getOrElse { error ->
            Log.e(LOG_TAG, "saf_grant_quiesce_failed", error)
            false
        }
    }

    private fun resumeAfterSafGrantChange(requestFullPipeline: Boolean): Boolean {
        val resumed =
            synchronized(workerGate) {
                if (stopped.get() || migrationQuiesced.get() || !rootConfigured) return false
                if (!safGrantQuiesced.compareAndSet(true, false)) return false
                safGrantFence = null
                safGrantFenceRequiresSuccess = false
                true
            }
        if (resumed && requestFullPipeline) requestPipeline()
        return resumed
    }

    private fun quiesceForStorageMigration(): Boolean {
        // AI-INTENT: ACK only after the single worker queue has crossed the cancellation fence.
        val fence =
            synchronized(workerGate) {
                if (stopped.get() || safGrantQuiesced.get()) return false
                if (migrationQuiesced.compareAndSet(false, true)) {
                    rerunRequested.set(false)
                    cancellationRevision.incrementAndGet()
                    executor.submit {}.also { migrationFence = it }
                } else {
                    migrationFence ?: return false
                }
            }
        binder.publishStatus("")
        return runCatching {
            // Also fence read-only Binder work that already captured the old repository.
            synchronized(rootGate) {}
            fence.get(MIGRATION_QUIESCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            check(!pipelineScheduled.get()) { "Indexer worker did not become idle" }
            true
        }.getOrElse { error ->
            Log.e(LOG_TAG, "storage_migration_quiesce_failed", error)
            false
        }
    }

    private fun activateStorageRootAfterMigration(
        location: String,
        portableVolumeId: String,
        requestFullPipeline: Boolean,
    ): Boolean {
        // Activate copied generations before the optional source scan.
        if (!migrationQuiesced.get() || stopped.get() || pipelineScheduled.get()) return false
        return runCatching {
            val parsedLocation = ApplicationDataLocation.valueOf(location)
            val selection =
                ApplicationDataSelection(
                    parsedLocation,
                    portableVolumeId.takeIf { parsedLocation == ApplicationDataLocation.SD },
                )
            require(parsedLocation != ApplicationDataLocation.SD || portableVolumeId.isNotBlank()) {
                "SD storage activation requires a portable volume ID"
            }
            configureOrPlanRebuild(selection)
            synchronized(workerGate) {
                check(!pipelineScheduled.get()) { "Indexer worker resumed during root activation" }
                migrationFence = null
                migrationQuiesced.set(false)
            }
            binder.publishStatus("")
            if (requestFullPipeline || pendingRebuild.get() != 0) requestPipeline()
            true
        }.getOrElse { error ->
            Log.e(LOG_TAG, "storage_root_activation_failed", error)
            false
        }
    }

    private fun cancellationFor(revision: Int): CancellationSignal =
        object : CancellationSignal {
            override val cancelled: Boolean
                get() =
                    stopped.get() ||
                        Thread.currentThread().isInterrupted ||
                        cancellationRevision.get() != revision

            override fun ensureAvailable() {
                check(!cancelled) { "Indexer process work was cancelled" }
            }
        }

    private fun resetDatabase(rebuild: DatabaseRebuild) {
        val clearFields = if (rebuild == DatabaseRebuild.AU) listOf("L2") else emptyList()
        val cleared = binder.requestReliable(ReliableCommands.CLEAR_SCAN, clearFields).get()
        try {
            check(cleared.fields == listOf("OK")) { "Player attempt journal did not acknowledge rebuild" }
        } finally {
            cleared.close()
        }
        synchronized(rootGate) {
            check(dataStorage.selectedRootAvailable()) { "Selected application data storage is unavailable" }
            rootConfigured = false
            binder.publishStatus("Clearing selected library data…")
            val cuetree = File(indexerRoot, "cuetree")
            // Automatic and explicit rebuilds share this scope. Never decode an older format
            // to recover a generation; retain the independent existing watermarks.
            // https://wiki.271.pics/Riff/LATEST/indexer-library-lifecycle#automatic-library-recreation
            cuetreeStore?.rememberGenerations()
            cuetreeStore = null
            if (rebuild == DatabaseRebuild.AU || rebuild == DatabaseRebuild.AU_IA) {
                File(cuetree, "analysis")
                    .listFiles()
                    .orEmpty()
                    .filter { it.name != "LAST_GENERATION" }
                    .forEach(::deleteTree)
            }
            if (rebuild == DatabaseRebuild.IA || rebuild == DatabaseRebuild.AU_IA) {
                cuetree
                    .listFiles()
                    .orEmpty()
                    .filter { it.name != "analysis" && it.name != "LAST_GENERATION" }
                    .forEach(::deleteTree)
            }
            val store = AndroidCuetreeStore(cuetree, applicationContext)
            cuetreeStore = store
            store.publicationListener = binder::notifyArtifactAvailable
            if (rebuild == DatabaseRebuild.AU) {
                store.read()?.let { prior ->
                    val cleared =
                        prior.copy(
                            nodes =
                                prior.nodes.map { node ->
                                    if (node.kind == NodeKind.MEDIA && node.identityFlags and Flags.FAILEDL2 != 0) {
                                        node.copy(identityFlags = node.identityFlags and Flags.FAILEDL2.inv())
                                    } else {
                                        node
                                    }
                                },
                        )
                    if (!cleared.hasSameContentAs(prior)) {
                        store.publish(cleared.copy(generation = store.nextLibraryGeneration()), Cancellation())
                    }
                }
            }
            pipeline =
                AndroidCuetreePipeline(
                    applicationContext,
                    store,
                    binder::publishStatus,
                    safListingIssues = binder::publishSafListingIssues,
                    acquisitionGuard = AndroidScanAcquisitionGuard(binder),
                )
            rootConfigured = true
        }
    }

    private fun deleteTree(file: File) {
        val path = file.toPath()
        // Indexer cleanup may unlink a stale link, but must never walk its external target.
        if (Files.isDirectory(path, NOFOLLOW_LINKS)) file.listFiles().orEmpty().forEach(::deleteTree)
        Files.deleteIfExists(path)
    }

    companion object {
        private const val LOG_TAG = "RiffIndexer"
        private const val MIGRATION_QUIESCE_TIMEOUT_SECONDS = 30L
        private const val SAF_GRANT_QUIESCE_TIMEOUT_MINUTES = 30L
    }
}
