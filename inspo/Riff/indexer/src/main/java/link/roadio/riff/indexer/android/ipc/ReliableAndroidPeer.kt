package link.roadio.riff.indexer.android.ipc

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import link.roadio.riff.indexer.core.ipc.reliable.ReliableAck
import link.roadio.riff.indexer.core.ipc.reliable.ReliableDelivery
import link.roadio.riff.indexer.core.ipc.reliable.ReliablePacket
import link.roadio.riff.indexer.core.ipc.reliable.ReliableResult
import link.roadio.riff.ipc.indexer.IndexerArtifactHandle
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Parcelable attachment ownership is transferred by duplicating the cached descriptor for each ACK. */
class ArtifactReply(
    val handle: IndexerArtifactHandle,
) : Closeable {
    override fun close() {
        handle.descriptor?.close()
    }
}

interface ReliableWire {
    fun sendPacket(packet: ReliablePacket)

    fun sendAck(ack: ReliableAck)
}

/** Binder entry points never wait for an ACK or hold the command worker's locks. */
class ReliableAndroidPeer(
    private val apply: (ReliablePacket) -> ReliableResult,
) : Closeable {
    private val worker = Executors.newSingleThreadExecutor()
    private val dispatch = Executors.newFixedThreadPool(2)
    private val timer = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var wire: ReliableWire? = null
    private val delivery =
        ReliableDelivery(
            clock = SystemClock::elapsedRealtime,
            sendPacket = { packet -> wire?.sendPacket(packet) ?: throw IOException("IPC peer disconnected") },
            sendAck = { ack -> wire?.sendAck(ack) ?: throw IOException("IPC peer disconnected") },
            apply = { packet -> CompletableFuture.supplyAsync({ apply(packet) }, worker) },
        )

    init {
        timer.scheduleWithFixedDelay(delivery::tick, 100, 100, TimeUnit.MILLISECONDS)
    }

    fun connect(
        session: String,
        wire: ReliableWire,
    ) {
        this.wire = wire
        delivery.connect(session)
    }

    fun disconnect() {
        wire = null
    }

    fun request(
        type: Int,
        fields: List<String> = emptyList(),
        coalesceGroup: String? = null,
    ): CompletableFuture<ReliableResult> = delivery.submit(type, fields, coalesceGroup)

    fun receivePacket(
        session: String,
        seq: Long,
        type: Int,
        fields: Array<out String>?,
    ) {
        runCatching {
            dispatch.execute { delivery.receive(ReliablePacket(session, seq, type, fields.orEmpty().toList())) }
        }
    }

    fun receiveAck(
        session: String,
        seq: Long,
        fields: Array<out String>?,
        attachment: IndexerArtifactHandle? = null,
    ) {
        val ack = ReliableAck(session, seq, ReliableResult(fields.orEmpty().toList(), attachment?.let(::ArtifactReply)))
        runCatching { dispatch.execute { delivery.acknowledge(ack) } }
            .onFailure { ack.result.close() }
    }

    override fun close() {
        wire = null
        delivery.close()
        timer.shutdownNow()
        dispatch.shutdownNow()
        worker.shutdownNow()
    }
}

fun duplicateArtifact(handle: IndexerArtifactHandle): IndexerArtifactHandle =
    IndexerArtifactHandle().also { copy ->
        copy.descriptor = ParcelFileDescriptor.dup(requireNotNull(handle.descriptor).fileDescriptor)
        copy.artifactKind = handle.artifactKind
        copy.volumeId = handle.volumeId
        copy.generation = handle.generation
        copy.offset = handle.offset
        copy.length = handle.length
        copy.flatBufferSchemaVersion = handle.flatBufferSchemaVersion
        copy.semanticVersion = handle.semanticVersion
        copy.xxh3 = handle.xxh3
        copy.cohortId = handle.cohortId
    }
