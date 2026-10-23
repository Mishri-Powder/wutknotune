package link.roadio.riff.indexer.android.ipc

import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import android.os.RemoteException
import link.roadio.riff.indexer.core.ipc.INDEXER_STATUS_MAX_CHARS
import link.roadio.riff.indexer.core.ipc.IndexerArtifactKind
import link.roadio.riff.indexer.core.ipc.IndexerArtifactMetadata
import link.roadio.riff.indexer.core.ipc.reliable.ReliableAck
import link.roadio.riff.indexer.core.ipc.reliable.ReliablePacket
import link.roadio.riff.indexer.core.ipc.reliable.ReliableResult
import link.roadio.riff.ipc.indexer.IIndexerArtifactService
import link.roadio.riff.ipc.indexer.IndexerArtifactHandle
import link.roadio.riff.ipc.player.IPlayerArtifactCallback
import java.text.BreakIterator
import java.util.Locale
import java.util.UUID

data class ValidatedAndroidArtifact(
    val descriptor: ParcelFileDescriptor,
    val metadata: IndexerArtifactMetadata,
    val offset: Long = 0,
    val cohortId: String? = null,
)

fun interface AndroidIndexerArtifactCatalog {
    /** Opens and returns the exact read-only inode already validated against [metadata]. */
    fun openCurrent(
        kind: IndexerArtifactKind,
        volumeId: String,
    ): ValidatedAndroidArtifact?
}

class AndroidIndexerArtifactBinder(
    private val catalog: AndroidIndexerArtifactCatalog,
    private val requestLibraryRescan: () -> Unit = {},
    private val quiesceForSafGrantChange: () -> Boolean = { false },
    private val resumeAfterSafGrantChange: (Boolean) -> Boolean = { false },
    private val requestDatabaseRebuild: (Int) -> Unit = {},
    private val resolveGrantTreeUri: (Long) -> String? = { null },
    private val quiesceForStorageMigration: () -> Boolean = { false },
    private val activateStorageRootAfterMigration: (String, String, Boolean) -> Boolean = { _, _, _ -> false },
) : IIndexerArtifactService.Stub(),
    AutoCloseable {
    private val callbacks = RemoteCallbackList<IPlayerArtifactCallback>()
    val serverEpoch: String = UUID.randomUUID().toString()

    @Volatile private var reliableCallback: IPlayerArtifactCallback? = null
    val hasReliablePlayer: Boolean get() = reliableCallback != null
    private val reliablePeer = ReliableAndroidPeer(::applyReliable)
    private val longCommands =
        java.util.concurrent.Executors
            .newSingleThreadExecutor()

    override fun openReliableChannel(
        callback: IPlayerArtifactCallback,
        playerEpoch: String,
    ): String {
        require(playerEpoch.length in 1..160)
        reliableCallback = callback
        reliablePeer.connect(
            "$playerEpoch|$serverEpoch",
            object : ReliableWire {
                override fun sendPacket(packet: ReliablePacket) {
                    requireNotNull(
                        reliableCallback,
                    ).receiveReliablePacket(packet.session, packet.seq, packet.type, packet.fields.toTypedArray())
                }

                override fun sendAck(ack: ReliableAck) {
                    val copy = (ack.result.attachment as? ArtifactReply)?.handle?.let(::duplicateArtifact)
                    try {
                        requireNotNull(reliableCallback).receiveReliableAck(ack.session, ack.seq, ack.result.fields.toTypedArray(), copy)
                    } finally {
                        copy?.descriptor?.close()
                    }
                }
            },
        )
        reliablePeer.request(ReliableCommands.STATUS, listOf(currentStatus), "status")
        val (active, unverified) = currentSafListingIssues
        reliablePeer.request(ReliableCommands.SAF_LISTING, safFields(active, unverified), "saf")
        return serverEpoch
    }

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
    ) {
        reliablePeer.receiveAck(session, seq, fields)
    }

    private fun applyReliable(packet: ReliablePacket): ReliableResult {
        val args = packet.fields
        return when (packet.type) {
            ReliableCommands.READ -> {
                require(args.size == 2)
                val handle = getCurrentArtifact(args[0], args[1])
                ReliableResult(listOf(if (handle == null) "MISSING" else "OK"), handle?.let(::ArtifactReply))
            }

            ReliableCommands.RESCAN -> {
                require(args.isEmpty())
                requestLibraryRescan()
                ReliableResult(listOf("OK"))
            }

            ReliableCommands.QUIESCE_SAF -> {
                require(args.size == 1)
                completeLater(args[0]) { quiesceForSafGrantChange() }
                ReliableResult(listOf("ACCEPTED"))
            }

            ReliableCommands.RESUME_SAF -> {
                require(args.size == 1)
                ReliableResult(listOf(resumeAfterSafGrantChange(args[0].toBooleanStrict()).toString()))
            }

            ReliableCommands.REBUILD -> {
                require(args.size == 1)
                requestDatabaseRebuild(args[0].toInt())
                ReliableResult(listOf("OK"))
            }

            ReliableCommands.RESOLVE_GRANT -> {
                require(args.size == 1)
                ReliableResult(listOf(resolveGrantTreeUri(args[0].toLong()).orEmpty()))
            }

            ReliableCommands.QUIESCE_MIGRATION -> {
                require(args.size == 1)
                completeLater(args[0]) { quiesceForStorageMigration() }
                ReliableResult(listOf("ACCEPTED"))
            }

            ReliableCommands.ACTIVATE_STORAGE -> {
                require(args.size == 4)
                completeLater(args[0]) {
                    activateStorageRootAfterMigration(args[1], args[2], args[3].toBooleanStrict())
                }
                ReliableResult(listOf("ACCEPTED"))
            }

            else -> {
                throw IllegalArgumentException("Unsupported Player command")
            }
        }
    }

    private fun completeLater(
        reference: String,
        operation: () -> Boolean,
    ) {
        require(reference.length in 1..160)
        longCommands.execute {
            val result = runCatching(operation).getOrDefault(false)
            reliablePeer.request(ReliableCommands.RESULT, listOf(reference, result.toString()))
        }
    }

    fun requestReliable(
        type: Int,
        fields: List<String> = emptyList(),
    ): java.util.concurrent.CompletableFuture<ReliableResult> = reliablePeer.request(type, fields)

    private fun safFields(
        active: List<String>,
        unverified: List<String>,
    ): List<String> = listOf(active.size.toString()) + active.take(8) + listOf(unverified.size.toString()) + unverified.take(8)

    @Volatile
    private var currentStatus = ""

    @Volatile
    private var currentSafListingIssues = emptyList<String>() to emptyList<String>()

    override fun getCurrentArtifact(
        artifactKind: String,
        volumeId: String,
    ): IndexerArtifactHandle? {
        val kind = IndexerArtifactKind.valueOf(artifactKind)
        val artifact = catalog.openCurrent(kind, volumeId) ?: return null
        try {
            require(artifact.metadata.kind == kind) { "Artifact kind mismatch" }
            require(artifact.metadata.volumeId == volumeId) { "Artifact volume mismatch" }
            val descriptorSize = artifact.descriptor.statSize
            require(artifact.offset >= 0 && descriptorSize >= 0) { "Invalid descriptor range" }
            require(
                artifact.offset <= descriptorSize &&
                    artifact.metadata.length <= descriptorSize - artifact.offset,
            ) {
                "Artifact range is outside its descriptor"
            }
            return artifact.toHandle()
        } catch (error: Throwable) {
            artifact.descriptor.close()
            throw error
        }
    }

    override fun registerPlayerArtifactCallback(callback: IPlayerArtifactCallback) {
        callbacks.register(callback)
        deliver(callback) {
            it.onIndexerStatusChanged(currentStatus)
            val (active, unverified) = currentSafListingIssues
            it.onSafListingIssueDetected(active.size, active.take(8).toTypedArray(), unverified.size, unverified.take(8).toTypedArray())
        }
    }

    override fun unregisterPlayerArtifactCallback(callback: IPlayerArtifactCallback) {
        callbacks.unregister(callback)
    }

    override fun requestLibraryRescan() {
        // Indexer work stays off this thread.
        requestLibraryRescan.invoke()
    }

    override fun quiesceForSafGrantChange(): Boolean = quiesceForSafGrantChange.invoke()

    override fun resumeAfterSafGrantChange(requestFullPipeline: Boolean): Boolean = resumeAfterSafGrantChange.invoke(requestFullPipeline)

    override fun requestDatabaseRebuild(mode: Int) {
        // Reject unsupported rebuild masks before crossing the worker boundary.
        require(mode in 1..3) { "Unsupported rebuild mask" }
        requestDatabaseRebuild.invoke(mode)
    }

    override fun resolveGrantTreeUri(rootBindingId: Long): String? = resolveGrantTreeUri.invoke(rootBindingId)

    override fun quiesceForStorageMigration(): Boolean = quiesceForStorageMigration.invoke()

    override fun activateStorageRootAfterMigration(
        location: String,
        portableVolumeId: String,
        requestFullPipeline: Boolean,
    ): Boolean = activateStorageRootAfterMigration.invoke(location, portableVolumeId, requestFullPipeline)

    fun publishStatus(status: String) {
        require(status.length <= INDEXER_STATUS_MAX_CHARS) { "Indexer status is too long" }
        currentStatus = status
        if (reliableCallback != null) reliablePeer.request(ReliableCommands.STATUS, listOf(status), "status")
        broadcast { it.onIndexerStatusChanged(status) }
    }

    /**
     * Retained independently of progress/status so late UI consumers cannot miss admission failure.
     * Only bounded display labels cross IPC; no grant locator or source identity is exposed.
     * A completed scan normally clears status, while this failure still needs explicit user OK.
     * https://wiki.271.pics/Riff/LATEST/saf-empty-listing-workaround#scan-retention-and-warning
     */
    fun publishSafListingIssues(
        activeFolderLabels: List<String>,
        unverifiedFolderLabels: List<String>,
    ) {
        val active = activeFolderLabels.map(::boundedFolderLabel)
        val unverified = unverifiedFolderLabels.map(::boundedFolderLabel)
        currentSafListingIssues = active to unverified
        if (reliableCallback != null) reliablePeer.request(ReliableCommands.SAF_LISTING, safFields(active, unverified), "saf")
        broadcast {
            it.onSafListingIssueDetected(active.size, active.take(8).toTypedArray(), unverified.size, unverified.take(8).toTypedArray())
        }
    }

    private fun boundedFolderLabel(value: String): String {
        val text = value.filterNot(Char::isISOControl).ifBlank { "Media folder" }
        if (text.length <= 80) return text
        val boundary = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }.preceding(81)
        return text.substring(0, boundary.coerceAtLeast(0)).ifBlank { "Media folder" }
    }

    fun notifyArtifactAvailable(metadata: IndexerArtifactMetadata) {
        if (reliableCallback != null) {
            reliablePeer.request(
                ReliableCommands.ARTIFACT,
                listOf(metadata.kind.name, metadata.volumeId, metadata.generation.toString()),
            )
        }
        broadcast { it.onIndexerArtifactAvailable(metadata.kind.name, metadata.volumeId, metadata.generation) }
    }

    private fun broadcast(action: (IPlayerArtifactCallback) -> Unit) {
        // Snapshot under the broadcast lock, then release it before crossing Binder.
        // A reentrant or slow recipient must not hold the RemoteCallbackList broadcast open.
        val recipients =
            synchronized(callbacks) {
                val count = callbacks.beginBroadcast()
                try {
                    List(count, callbacks::getBroadcastItem)
                } finally {
                    callbacks.finishBroadcast()
                }
            }
        recipients.forEach { deliver(it, action) }
    }

    private fun deliver(
        callback: IPlayerArtifactCallback,
        action: (IPlayerArtifactCallback) -> Unit,
    ) {
        try {
            action(callback)
        } catch (_: RemoteException) {
            callbacks.unregister(callback)
        }
    }

    private fun ValidatedAndroidArtifact.toHandle(): IndexerArtifactHandle =
        IndexerArtifactHandle().also { handle ->
            handle.descriptor = descriptor
            handle.artifactKind = metadata.kind.name
            handle.volumeId = metadata.volumeId
            handle.generation = metadata.generation
            handle.offset = offset
            handle.length = metadata.length
            handle.flatBufferSchemaVersion = metadata.flatBufferSchemaVersion
            handle.semanticVersion = metadata.semanticVersion
            handle.xxh3 = metadata.xxh3
            handle.cohortId = cohortId
        }

    override fun close() {
        reliableCallback = null
        reliablePeer.close()
        longCommands.shutdownNow()
    }

    private companion object {
    }
}
