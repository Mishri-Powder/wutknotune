package link.roadio.riff.indexer.core.ipc.reliable

import java.io.Closeable
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture

data class ReliablePacket(
    val session: String,
    val seq: Long,
    val type: Int,
    val fields: List<String>,
)

data class ReliableResult(
    val fields: List<String> = emptyList(),
    val attachment: Closeable? = null,
) : Closeable {
    override fun close() {
        attachment?.close()
    }
}

data class ReliableAck(
    val session: String,
    val seq: Long,
    val result: ReliableResult,
)

class IpcSessionChanged : IOException("IPC endpoint session changed")

/**
 * Stop-and-wait applies a command once and repeats its original reply after ACK loss.
 * Session state is RAM-only. Attachments belong to the cached receiver reply or accepted sender.
 * Contract: https://wiki.271.pics/Riff/LATEST/reliable-player-indexer-ipc
 */
class ReliableDelivery(
    private val clock: () -> Long,
    private val sendPacket: (ReliablePacket) -> Unit,
    private val sendAck: (ReliableAck) -> Unit,
    private val apply: (ReliablePacket) -> CompletableFuture<ReliableResult>,
    private val queueLimit: Int = 16,
    private val initialRetryMillis: Long = 250L,
    private val maximumRetryMillis: Long = 2_000L,
) : Closeable {
    private data class Request(
        val type: Int,
        val fields: List<String>,
        val result: CompletableFuture<ReliableResult>,
        val coalesceGroup: String? = null,
    )

    private data class Pending(
        val packet: ReliablePacket,
        val result: CompletableFuture<ReliableResult>,
        var retryAt: Long,
        var delay: Long,
    )

    private val gate = Any()
    private val queued = ArrayDeque<Request>()
    private var session: String? = null
    private var sendSequence = 1L
    private var receiveSequence = 1L
    private var pending: Pending? = null
    private var receiving: ReliablePacket? = null
    private var lastPacket: ReliablePacket? = null
    private var lastAck: ReliableAck? = null
    private var closed = false

    init {
        require(queueLimit > 0 && initialRetryMillis > 0 && maximumRetryMillis >= initialRetryMillis)
    }

    fun connect(next: String) {
        require(next.isNotBlank() && next.length <= 160)
        var stale: ReliableResult? = null
        val abandoned = mutableListOf<CompletableFuture<ReliableResult>>()
        synchronized(gate) {
            check(!closed)
            if (session == next) return
            if (session != null) {
                pending?.let { abandoned += it.result }
                queued.forEach { abandoned += it.result }
                queued.clear()
                stale = lastAck?.result
            }
            session = next
            sendSequence = 1L
            receiveSequence = 1L
            pending = null
            receiving = null
            lastPacket = null
            lastAck = null
        }
        stale?.close()
        abandoned.forEach { it.completeExceptionally(IpcSessionChanged()) }
        pump()
    }

    fun submit(
        type: Int,
        fields: List<String> = emptyList(),
        coalesceGroup: String? = null,
    ): CompletableFuture<ReliableResult> {
        require(type >= 0 && fields.size <= 128 && fields.all { it.length <= 4096 })
        val future = CompletableFuture<ReliableResult>()
        var superseded: Request? = null
        synchronized(gate) {
            if (coalesceGroup != null) {
                superseded = queued.firstOrNull { it.coalesceGroup == coalesceGroup }
                superseded?.let(queued::remove)
            }
            if (closed) {
                future.completeExceptionally(IOException("IPC delivery is closed"))
            } else if (queued.size + (if (pending == null) 0 else 1) >= queueLimit) {
                future.completeExceptionally(IOException("IPC control queue is full"))
            } else {
                queued.addLast(Request(type, fields.toList(), future, coalesceGroup))
            }
        }
        superseded?.result?.completeExceptionally(IOException("Superseded before SEQ assignment"))
        pump()
        return future
    }

    private fun pump() {
        val packet =
            synchronized(gate) {
                val current = session
                if (closed || current == null || pending != null || queued.isEmpty()) {
                    null
                } else {
                    val request = queued.removeFirst()
                    val packet = ReliablePacket(current, sendSequence, request.type, request.fields)
                    sendSequence = Math.addExact(sendSequence, 1L)
                    pending = Pending(packet, request.result, clock() + initialRetryMillis, initialRetryMillis)
                    packet
                }
            }
        packet?.let(::transmit)
    }

    fun tick() {
        val packet =
            synchronized(gate) {
                val current = pending
                if (closed || current == null || clock() < current.retryAt) {
                    null
                } else {
                    current.delay = (current.delay * 2).coerceAtMost(maximumRetryMillis)
                    current.retryAt = clock() + current.delay
                    current.packet
                }
            }
        packet?.let(::transmit)
    }

    private fun transmit(packet: ReliablePacket) {
        // A failed send retains the packet. Transport failure is never a source error.
        runCatching { sendPacket(packet) }
    }

    fun acknowledge(ack: ReliableAck) {
        val target =
            synchronized(gate) {
                val current = pending
                if (closed || ack.session != session || current == null || ack.seq != current.packet.seq) {
                    null
                } else {
                    pending = null
                    current.result
                }
            }
        if (target == null || !target.complete(ack.result)) ack.result.close()
        pump()
    }

    fun receive(packet: ReliablePacket) {
        // AI-INTENT: Async apply and duplicate ACKs must retain the accepted payload, even if
        // a transport adapter reuses its input lists. The protocol owns these snapshots.
        require(packet.fields.size <= 128)
        val command = packet.copy(fields = packet.fields.toList())
        require(command.seq > 0 && command.type >= 0 && command.fields.size <= 128 && command.fields.all { it.length <= 4096 })
        var replay: ReliableAck? = null
        val accepted =
            synchronized(gate) {
                when {
                    closed || command.session != session -> {
                        false
                    }

                    command.seq < receiveSequence -> {
                        if (lastPacket?.seq == command.seq) require(lastPacket == command) { "SEQ reused with different content" }
                        replay = lastAck
                        false
                    }

                    command.seq > receiveSequence -> {
                        false
                    }

                    receiving != null -> {
                        require(receiving == command) { "SEQ reused with different content" }
                        false
                    }

                    else -> {
                        receiving = command
                        true
                    }
                }
            }
        replay?.let(::transmitAck)
        if (!accepted) return
        val operation =
            try {
                apply(command)
            } catch (error: Exception) {
                CompletableFuture.completedFuture(ReliableResult(listOf("ERROR", error.javaClass.simpleName)))
            }
        operation.whenComplete { result, error ->
            val reply =
                result?.copy(fields = result.fields.toList()) ?: ReliableResult(listOf("ERROR", error?.javaClass?.simpleName ?: "FAILED"))
            var stale: ReliableResult? = null
            val ack =
                synchronized(gate) {
                    if (closed || session != command.session || receiving != command) {
                        null
                    } else {
                        stale = lastAck?.result
                        receiving = null
                        receiveSequence = Math.addExact(receiveSequence, 1L)
                        ReliableAck(command.session, command.seq, reply).also {
                            lastPacket = command
                            lastAck = it
                        }
                    }
                }
            stale?.close()
            if (ack == null) reply.close() else transmitAck(ack)
        }
    }

    private fun transmitAck(ack: ReliableAck) {
        runCatching { sendAck(ack) }
    }

    override fun close() {
        val abandoned = mutableListOf<CompletableFuture<ReliableResult>>()
        val reply =
            synchronized(gate) {
                if (closed) return
                closed = true
                pending?.let { abandoned += it.result }
                queued.forEach { abandoned += it.result }
                pending = null
                queued.clear()
                receiving = null
                lastAck?.result.also { lastAck = null }
            }
        reply?.close()
        abandoned.forEach { it.completeExceptionally(IOException("IPC delivery closed")) }
    }
}
