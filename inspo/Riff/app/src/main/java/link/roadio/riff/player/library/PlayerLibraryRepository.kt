package link.roadio.riff.player.library

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import link.roadio.cuetree.FlatCatalogs
import link.roadio.cuetree.ROOT_IDENTITY_ATTRIBUTE
import link.roadio.riff.indexer.android.cuetree.AndroidCuetreeGenres
import link.roadio.riff.indexer.android.ipc.ArtifactReply
import link.roadio.riff.indexer.android.ipc.ReliableAndroidPeer
import link.roadio.riff.indexer.android.ipc.ReliableCommands
import link.roadio.riff.indexer.android.ipc.ReliableWire
import link.roadio.riff.indexer.core.analysis.CuetreeAnalysisCodec
import link.roadio.riff.indexer.core.ipc.INDEXER_STATUS_MAX_CHARS
import link.roadio.riff.indexer.core.ipc.reliable.ReliableAck
import link.roadio.riff.indexer.core.ipc.reliable.ReliablePacket
import link.roadio.riff.indexer.core.ipc.reliable.ReliableResult
import link.roadio.riff.indexer.core.ipc.reliable.ScanAttempt
import link.roadio.riff.indexer.core.ipc.reliable.ScanAttemptJournal
import link.roadio.riff.indexer.core.ipc.reliable.ScanAttemptMode
import link.roadio.riff.ipc.indexer.IIndexerArtifactService
import link.roadio.riff.ipc.indexer.IndexerArtifactHandle
import link.roadio.riff.ipc.player.IPlayerArtifactCallback
import link.roadio.riff.storage.hash.nativeXxh3
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal data class PlayerLibraryObserver(
    val onLibrary: (PlayerLibraryModel) -> Unit,
    val onUnavailable: (String) -> Unit,
    val onIndexerStatus: (String) -> Unit,
    val onLoadingChanged: (Boolean) -> Unit,
    val projection: () -> PlayerLibraryProjection,
    val onSafListingBug: (SafListingNotice?) -> Unit,
)

/**
 * UI and playback subscribe to the same generation-coherent library rather than independently
 * binding, mapping and rebuilding it. Indexer retains mutable ownership; Player sees read-only
 * descriptors and keeps the last usable model while a replacement is prepared.
 *
 * Authority: https://wiki.271.pics/Riff/LATEST/cuetree-integration
 * AI-INTENT: The executor owns cohort loading; Binder callbacks only request work. Preserve the paired
 * MUSIC/AUDIOBOOK projections and generation checks when changing caching or reload scheduling.
 */
internal class PlayerLibraryRepository private constructor(
    private val context: Context,
    private val excludedGenreIds: Set<Long>,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val ipcExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val aiGenreDisplay by lazy(LazyThreadSafetyMode.NONE) {
        runCatching { AndroidCuetreeGenres.displayMap(context) }.getOrDefault(emptyMap())
    }
    private val observers = linkedSetOf<PlayerLibraryObserver>()
    private val reloadPending = AtomicBoolean()
    private val reloadRequested = AtomicBoolean()
    private val pendingRebuild = AtomicInteger()
    private val cohortRetries = AtomicInteger()

    @Volatile
    private var service: IIndexerArtifactService? = null

    @Volatile
    private var cachedModels: PlayerLibraryModels? = null

    @Volatile
    private var acceptedCohort: PreparedCohort? = null

    @Volatile
    private var grantUris: Map<Long, String> = emptyMap()

    @Volatile
    private var requestedProjection = PlayerLibraryProjection()

    private var bound = false
    private var loading = false
    private val playerEpoch = UUID.randomUUID().toString()

    @Volatile private var indexerEpoch: String? = null
    private val scanJournal = ScanAttemptJournal()
    private val longResults = ConcurrentHashMap<String, CompletableFuture<Boolean>>()
    private val reliablePeer = ReliableAndroidPeer(::applyReliable)

    private fun failPendingLongCommands() {
        longResults.values.forEach { it.completeExceptionally(IOException("Indexer connection changed")) }
        longResults.clear()
    }

    private fun reliableResult(
        type: Int,
        fields: List<String> = emptyList(),
    ): ReliableResult {
        val result = reliablePeer.request(type, fields).get()
        if (result.fields.firstOrNull() == "ERROR") {
            result.close()
            throw IOException("Indexer command failed: " + result.fields.drop(1).joinToString(","))
        }
        return result
    }

    private fun reliableBoolean(
        type: Int,
        fields: List<String> = emptyList(),
    ): Boolean = reliableResult(type, fields).fields.single().toBooleanStrict()

    private fun reliableLongBoolean(
        type: Int,
        fields: List<String> = emptyList(),
    ): Boolean {
        val reference = UUID.randomUUID().toString()
        val completion = CompletableFuture<Boolean>()
        longResults[reference] = completion
        try {
            check(reliableResult(type, listOf(reference) + fields).fields == listOf("ACCEPTED"))
            return completion.get()
        } finally {
            longResults.remove(reference)
        }
    }

    private fun reliableArtifact(kind: String): IndexerArtifactHandle? {
        val response = reliableResult(ReliableCommands.READ, listOf(kind, CUETREE_VOLUME_ID))
        if (response.fields.single() == "MISSING") return null
        check(response.fields.single() == "OK")
        return (response.attachment as ArtifactReply).handle
    }

    private fun applyReliable(packet: ReliablePacket): ReliableResult {
        val fields = packet.fields
        when (packet.type) {
            ReliableCommands.STATUS -> {
                require(fields.size == 1)
                callback.onIndexerStatusChanged(fields[0])
            }

            ReliableCommands.ARTIFACT -> {
                require(fields.size == 3)
                callback.onIndexerArtifactAvailable(fields[0], fields[1], fields[2].toLong())
            }

            ReliableCommands.RESULT -> {
                require(fields.size == 2)
                longResults[fields[0]]?.complete(fields[1].toBooleanStrict())
            }

            ReliableCommands.SAF_LISTING -> {
                val count = fields.first().toInt()
                require(count in 0..100_000 && fields.size >= 2 + minOf(count, 8))
                val active = fields.drop(1).take(minOf(count, 8))
                val offset = 1 + active.size
                val unverifiedCount = fields[offset].toInt()
                val unverified = fields.drop(offset + 1).take(minOf(unverifiedCount, 8))
                callback.onSafListingIssueDetected(count, active.toTypedArray(), unverifiedCount, unverified.toTypedArray())
            }

            ReliableCommands.BEGIN_SCAN -> {
                require(fields.size == 9 && fields[0] == indexerEpoch)
                val attempt = ScanAttempt(fields[0], fields[1], fields.subList(2, 8), ScanAttemptMode.valueOf(fields[8]))
                return ReliableResult(listOf(scanJournal.begin(attempt).name))
            }

            ReliableCommands.END_SCAN -> {
                require(fields.size == 2 && fields[0] == indexerEpoch)
                scanJournal.finish(fields[0], fields[1])
            }

            ReliableCommands.RECOVER_SCAN -> {
                require(fields.size == 1)
                val offset = fields[0].toInt()
                val all = scanJournal.recover()
                require(offset in 0..all.size)
                val batch = all.drop(offset).take(12)
                return ReliableResult(
                    listOf(all.size.toString()) + batch.flatMap { listOf(it.owner, it.reference) + it.source + it.mode.name },
                )
            }

            ReliableCommands.CLEAR_SCAN -> {
                require(fields.size <= 1)
                scanJournal.clear(fields.singleOrNull())
            }

            else -> {
                throw IllegalArgumentException("Unsupported Indexer command")
            }
        }
        return ReliableResult(listOf("OK"))
    }

    private val safListingNotice = SafListingNoticeSession()

    private val callback =
        object : IPlayerArtifactCallback.Stub() {
            override fun receiveReliablePacket(
                session: String,
                seq: Long,
                type: Int,
                fields: Array<out String>?,
            ) {
                reliablePeer.receivePacket(session, seq, type, fields)
            }

            override fun receiveReliableAck(
                session: String,
                seq: Long,
                fields: Array<out String>?,
                attachment: IndexerArtifactHandle?,
            ) {
                reliablePeer.receiveAck(session, seq, fields, attachment)
            }

            override fun onIndexerArtifactAvailable(
                artifactKind: String,
                volumeId: String,
                generation: Long,
            ) {
                if (volumeId == CUETREE_VOLUME_ID && (artifactKind in CUETREE_KINDS || artifactKind == "MDRG")) {
                    requestReload()
                }
            }

            override fun onIndexerStatusChanged(status: String) {
                observersSnapshot().forEach { it.onIndexerStatus(status.take(INDEXER_STATUS_MAX_CHARS)) }
            }

            override fun onSafListingIssueDetected(
                activeRootCount: Int,
                activeFolderLabels: Array<out String>?,
                unverifiedRootCount: Int,
                unverifiedFolderLabels: Array<out String>?,
            ) {
                execute {
                    val notice =
                        if (activeRootCount > 0 || unverifiedRootCount > 0) {
                            SafListingNotice(
                                activeRootCount,
                                activeFolderLabels.orEmpty().take(8),
                                unverifiedRootCount,
                                unverifiedFolderLabels.orEmpty().take(8),
                            )
                        } else {
                            null
                        }
                    synchronized(this@PlayerLibraryRepository) { safListingNotice.report(notice) }
                    notifySafListingBug()
                }
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                service = IIndexerArtifactService.Stub.asInterface(binder)
                runCatching {
                    val remote = requireNotNull(service)
                    val nextEpoch = remote.openReliableChannel(callback, playerEpoch)
                    indexerEpoch?.takeIf { it != nextEpoch }?.let {
                        scanJournal.ownerDied(it)
                        failPendingLongCommands()
                    }
                    indexerEpoch = nextEpoch
                    reliablePeer.connect(
                        "$playerEpoch|$nextEpoch",
                        object : ReliableWire {
                            override fun sendPacket(packet: ReliablePacket) {
                                requireNotNull(
                                    service,
                                ).receiveReliablePacket(packet.session, packet.seq, packet.type, packet.fields.toTypedArray())
                            }

                            override fun sendAck(ack: ReliableAck) {
                                requireNotNull(service).receiveReliableAck(ack.session, ack.seq, ack.result.fields.toTypedArray())
                            }
                        },
                    )
                }.onFailure { notifyUnavailable("Library callback registration failed") }
                Log.i(TAG, "repository_connected subscribers=${observerCount()}")
                requestReload()
                ipcExecutor.execute(::drainPendingRebuild)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                reliablePeer.disconnect()
                failPendingLongCommands()
                service = null
                setLoading(false)
                observersSnapshot().forEach { it.onIndexerStatus("") }
                notifyUnavailable("Indexer is restarting; the last library remains active")
            }
        }

    fun attach(observer: PlayerLibraryObserver): Boolean {
        val model: PlayerLibraryModel?
        synchronized(this) {
            observers += observer
            requestedProjection = observer.stableProjection()
            model = cachedModel(observer)
        }
        model?.let { execute { if (isAttached(observer)) observer.onLibrary(it) } }
        val accepted = bindOnce()
        if (loading) observer.onLoadingChanged(true)
        execute { if (isAttached(observer)) observer.onSafListingBug(synchronized(this) { safListingNotice.pending }) }
        Log.i(TAG, "subscriber_attached subscribers=${observerCount()} bound=$accepted")
        return accepted
    }

    fun acknowledgeSafListingBug() {
        execute {
            synchronized(this) {
                safListingNotice.acknowledge()
            }
            notifySafListingBug()
        }
    }

    private fun notifySafListingBug() {
        val notice = synchronized(this) { safListingNotice.pending }
        observersSnapshot().forEach { it.onSafListingBug(notice) }
    }

    fun detach(observer: PlayerLibraryObserver) {
        synchronized(this) { observers -= observer }
        Log.i(TAG, "subscriber_detached subscribers=${observerCount()}")
    }

    fun requestReload(projection: PlayerLibraryProjection? = null) {
        projection?.let { requestedProjection = it.stable() }
        reloadRequested.set(true)
        if (reloadPending.compareAndSet(false, true)) beginReload()
    }

    private fun beginReload() {
        if (!reloadRequested.getAndSet(false)) {
            finishReload()
            return
        }
        val projection = requestedProjection.stable()
        runCatching {
            ipcExecutor.execute {
                val handles = runCatching(::fetchArtifactHandles)
                execute {
                    handles.fold(
                        onSuccess = { acquired ->
                            if (acquired == null) {
                                notifyUnavailable("Preparing music library…")
                                finishReload()
                            } else {
                                parseArtifactHandles(acquired, projection)
                            }
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Cuetree descriptor acquisition failed", error)
                            notifyUnavailable("Library reload failed: " + error.javaClass.simpleName)
                            finishReload()
                        },
                    )
                }
            }
        }.onFailure {
            reloadPending.set(false)
            notifyUnavailable("Library reload scheduling failed")
        }
    }

    private fun finishReload() {
        setLoading(false)
        reloadPending.set(false)
        if (reloadRequested.get()) requestReload()
    }

    private fun fetchArtifactHandles(): List<IndexerArtifactHandle>? {
        if (service == null) return null
        val handles = mutableListOf<IndexerArtifactHandle>()
        var transferred = false
        try {
            for (kind in CUETREE_KINDS) {
                val handle = reliableArtifact(kind) ?: return null
                handles += handle
            }
            runCatching { reliableArtifact("MDRG") }
                .onFailure { Log.w(TAG, "Optional MDRG descriptor unavailable") }
                .getOrNull()
                ?.let(handles::add)
            transferred = true
            return handles.toList()
        } finally {
            // Ownership passes to parseArtifactHandles only for a complete cohort.
            if (!transferred) {
                handles.forEach { runCatching { it.descriptor?.close() } }
            }
        }
    }

    private data class PreparedCohort(
        val key: CohortKey,
        val models: PlayerLibraryModels,
        val roots: List<Long>,
        val generation: Long,
    )

    private fun parseArtifactHandles(
        handles: List<IndexerArtifactHandle>,
        projection: PlayerLibraryProjection,
    ) {
        val prepared =
            try {
                val cohort = handles.take(CUETREE_KINDS.size)
                val cohortIds = cohort.map { it.cohortId }.toSet()
                if (cohortIds.size != 1 ||
                    cohortIds.single().isNullOrBlank() ||
                    cohort.map { it.generation }.toSet().size != 1
                ) {
                    if (cohortRetries.incrementAndGet() <= 3) {
                        reloadRequested.set(true)
                    } else {
                        notifyUnavailable("Cuetree cohort changed during loading")
                        cohortRetries.set(0)
                    }
                    null
                } else {
                    cohortRetries.set(0)
                    setLoading(true)
                    val analysisHandle = handles.getOrNull(CUETREE_KINDS.size)
                    val key =
                        CohortKey(
                            cohort.map { it.key() } + listOfNotNull(analysisHandle?.key()),
                            requireNotNull(cohort.first().cohortId),
                            projection.audiobookOverrides,
                        )
                    acceptedCohort?.takeIf { it.key == key } ?: run {
                        val files = FlatCatalogs.names.zip(cohort).associate { (name, handle) -> name to readCuetreeMember(handle) }
                        val snapshot = FlatCatalogs.read(files).snapshot()
                        require(snapshot.generation == cohort.first().generation) { "Cuetree generation mismatch" }
                        val analysis =
                            readOptionalCuetreeAnalysis {
                                analysisHandle?.let { handle ->
                                    require(handle.artifactKind == "MDRG" && handle.volumeId == CUETREE_VOLUME_ID)
                                    require(handle.flatBufferSchemaVersion == CuetreeAnalysisCodec.SCHEMA_VERSION)
                                    require(handle.semanticVersion == CuetreeAnalysisCodec.SEMANTIC_VERSION)
                                    CuetreeAnalysisCodec.decode(readValidatedMember(handle)).also {
                                        require(it.generation == handle.generation)
                                    }
                                }
                            }
                        val models = CuetreeBrowserTree.build(snapshot, projection, excludedGenreIds, analysis, aiGenreDisplay)
                        val roots =
                            snapshot.nodes.filter { it.isRoot }.mapNotNull { root ->
                                root.attributes[ROOT_IDENTITY_ATTRIBUTE]?.singleOrNull()?.let { identity ->
                                    nativeXxh3(identity.toByteArray(Charsets.UTF_8))
                                }
                            }
                        PreparedCohort(key, models, roots, snapshot.generation)
                    }
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Cuetree cohort reload failed", error)
                notifyUnavailable("Library reload failed: " + error.javaClass.simpleName)
                null
            } finally {
                handles.forEach { handle -> runCatching { handle.descriptor?.close() } }
            }
        if (prepared == null) {
            finishReload()
            return
        }
        ipcExecutor.execute {
            val resolved =
                runCatching {
                    prepared.roots
                        .mapNotNull { key ->
                            val uri = reliableResult(ReliableCommands.RESOLVE_GRANT, listOf(key.toString())).fields.single()
                            uri.takeIf(String::isNotEmpty)?.let { key to it }
                        }.toMap()
                }
            execute {
                resolved.fold(
                    onSuccess = { uris ->
                        grantUris = uris
                        acceptedCohort = prepared
                        cachedModels = prepared.models
                        broadcast(prepared.models)
                        Log.i(TAG, "cuetree_cohort_load generation=" + prepared.generation + " subscribers=" + observerCount())
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Cuetree grant acquisition failed", error)
                        notifyUnavailable("Library reload failed: " + error.javaClass.simpleName)
                    },
                )
                finishReload()
            }
        }
    }

    fun activateProjection(
        observer: PlayerLibraryObserver,
        requested: PlayerLibraryProjection,
    ): Boolean {
        val stable = requested.stable()
        requestedProjection = stable
        val model =
            synchronized(this) {
                cachedModels
                    ?.takeIf { it.music.projection.audiobookOverrides == stable.audiobookOverrides }
                    ?.forMode(stable.contentMode)
            } ?: return false
        observer.onLibrary(model)
        return true
    }

    fun requestRescan(observer: PlayerLibraryObserver): Boolean =
        runCatching {
            requireNotNull(service) { "Indexer service is unavailable" }
            reliablePeer.request(ReliableCommands.RESCAN).whenComplete { result, error ->
                if (error != null || result?.fields?.firstOrNull() == "ERROR") notifyUnavailable("Indexer rescan request failed")
            }
            true
        }.getOrElse {
            observer.onUnavailable("Indexer rescan request failed")
            false
        }

    fun resolveGrantTreeUri(rootBindingId: Long): String? = grantUris[rootBindingId]

    fun quiesceForSafGrantChange(): Boolean = runCatching { reliableLongBoolean(ReliableCommands.QUIESCE_SAF) }.getOrDefault(false)

    fun resumeAfterSafGrantChange(requestFullPipeline: Boolean): Boolean =
        runCatching {
            reliableBoolean(ReliableCommands.RESUME_SAF, listOf(requestFullPipeline.toString()))
        }.getOrDefault(false)

    fun quiesceForStorageMigration(observer: PlayerLibraryObserver): Boolean =
        runCatching { reliableLongBoolean(ReliableCommands.QUIESCE_MIGRATION) }.getOrElse {
            observer.onUnavailable("Indexer could not stop for data move")
            false
        }

    fun activateStorageRootAfterMigration(
        observer: PlayerLibraryObserver,
        location: String,
        portableVolumeId: String?,
        requestFullPipeline: Boolean,
    ): Boolean =
        runCatching {
            reliableLongBoolean(
                ReliableCommands.ACTIVATE_STORAGE,
                listOf(location, portableVolumeId.orEmpty(), requestFullPipeline.toString()),
            )
        }.getOrElse {
            observer.onUnavailable("Indexer could not activate the moved data")
            false
        }

    fun requestDatabaseRebuild(mode: Int): Boolean {
        if (!isCuetreeRebuildMask(mode)) return false
        pendingRebuild.getAndUpdate { it or mode }
        return runCatching {
            ipcExecutor.execute(::drainPendingRebuild)
            true
        }.getOrDefault(false)
    }

    private fun drainPendingRebuild() {
        val current = service ?: return
        val mode = pendingRebuild.getAndSet(0)
        if (mode == 0) return
        runCatching { reliableResult(ReliableCommands.REBUILD, listOf(mode.toString())) }
            .onFailure {
                pendingRebuild.getAndUpdate { it or mode }
                notifyUnavailable("Indexer database rebuild request failed")
            }
    }

    private fun readCuetreeMember(handle: IndexerArtifactHandle): ByteArray {
        require(handle.artifactKind in CUETREE_KINDS)
        require(handle.volumeId == CUETREE_VOLUME_ID)
        require(handle.flatBufferSchemaVersion == FlatCatalogs.FORMAT_VERSION.toInt())
        require(handle.semanticVersion == 1)
        return readValidatedMember(handle)
    }

    private fun readValidatedMember(handle: IndexerArtifactHandle): ByteArray {
        require(handle.offset >= 0 && handle.length in 12..Int.MAX_VALUE.toLong())
        val descriptor = requireNotNull(handle.descriptor)
        val size = descriptor.statSize
        require(size >= 0 && handle.offset <= size && handle.length <= size - handle.offset)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val mapped = input.channel.map(FileChannel.MapMode.READ_ONLY, handle.offset, handle.length)
            require(nativeXxh3(mapped) == handle.xxh3) { "Cuetree checksum mismatch" }
            ByteArray(handle.length.toInt()).also(mapped::get)
        }
    }

    private fun broadcast(models: PlayerLibraryModels) {
        observersSnapshot().forEach { observer ->
            val projection = observer.stableProjection()
            if (projection.audiobookOverrides == models.music.projection.audiobookOverrides) {
                observer.onLibrary(models.forMode(projection.contentMode))
            } else {
                requestedProjection = projection
                reloadRequested.set(true)
            }
        }
    }

    private fun setLoading(value: Boolean) {
        val changed =
            synchronized(this) {
                if (loading == value) {
                    false
                } else {
                    loading = value
                    true
                }
            }
        if (changed) observersSnapshot().forEach { it.onLoadingChanged(value) }
    }

    private fun notifyUnavailable(message: String) {
        observersSnapshot().forEach { it.onUnavailable(message) }
    }

    private fun bindOnce(): Boolean =
        synchronized(this) {
            if (bound) return@synchronized true
            val intent =
                Intent().setComponent(
                    ComponentName(
                        context.packageName,
                        "link.roadio.riff.indexer.android.process.IndexerProcessService",
                    ),
                )
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Log.i(TAG, "repository_bind_requested package=${context.packageName} accepted=$bound")
            if (!bound) notifyUnavailable("Indexer service is unavailable")
            bound
        }

    private fun execute(block: () -> Unit): Boolean =
        runCatching {
            executor.execute(block)
            true
        }.getOrDefault(false)

    private fun observersSnapshot(): List<PlayerLibraryObserver> = synchronized(this) { observers.toList() }

    private fun observerCount(): Int = synchronized(this) { observers.size }

    private fun isAttached(observer: PlayerLibraryObserver): Boolean = synchronized(this) { observer in observers }

    private fun cachedModel(observer: PlayerLibraryObserver): PlayerLibraryModel? {
        val projection = observer.stableProjection()
        return cachedModels
            ?.takeIf { it.music.projection.audiobookOverrides == projection.audiobookOverrides }
            ?.forMode(projection.contentMode)
    }

    private fun PlayerLibraryObserver.stableProjection(): PlayerLibraryProjection = projection().stable()

    private fun PlayerLibraryProjection.stable(): PlayerLibraryProjection = copy(audiobookOverrides = audiobookOverrides.toMap())

    private fun IndexerArtifactHandle.key(): ArtifactKey =
        ArtifactKey(
            artifactKind,
            volumeId,
            generation,
            flatBufferSchemaVersion,
            semanticVersion,
            offset,
            length,
            xxh3,
        )

    private data class ArtifactKey(
        val kind: String,
        val volumeId: String,
        val generation: Long,
        val schemaVersion: Int,
        val semanticVersion: Int,
        val offset: Long,
        val length: Long,
        val checksum: Long,
    )

    private data class CohortKey(
        val members: List<ArtifactKey>,
        val cohortId: String,
        val audiobookOverrides: Map<String, link.roadio.riff.player.storage.StoredAudiobookOverride>,
    )

    companion object {
        @Volatile
        private var instance: PlayerLibraryRepository? = null

        fun get(
            context: Context,
            excludedGenreIds: Set<Long>,
        ): PlayerLibraryRepository =
            instance ?: synchronized(this) {
                instance ?: PlayerLibraryRepository(context.applicationContext, excludedGenreIds.toSet())
                    .also { instance = it }
            }

        private val CUETREE_KINDS = listOf("CUMD", "CUAL", "CUAR", "CUGR")
        private const val CUETREE_VOLUME_ID = "cuetree"
        private const val TAG = "RiffLibraryRepo"
    }
}
