package link.roadio.riff.player.artwork

import android.os.CancellationSignal
import link.roadio.riff.ui.ArtworkUiState
import link.roadio.riff.ui.PlaybackSourceRef
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal data class ArtworkKey(
    val queueRevisionId: Long,
    val occurrenceId: Long,
    val bindingId: Long,
    val sourceGeneration: Long,
)

internal data class ArtworkResolution(
    val artwork: ArtworkUiState,
    val retryable: Boolean = false,
)

/**
 * The service's bounded owner for current and prefetched artwork. UI reads these same results.
 * AI-INTENT: An old attempt may finish after cancellation; entry identity and attempt number fence it.
 * https://wiki.271.pics/Riff/LATEST/ui-foundation#physical-ui-and-artwork
 */
internal class PlayerArtworkLoader(
    private val resolve: (PlaybackSourceRef, CancellationSignal) -> ArtworkResolution,
    private val timeoutMs: Long = 5_000L,
    private val retryDelayMs: Long = 1_000L,
) : AutoCloseable {
    private class Entry(
        val key: ArtworkKey,
        val source: PlaybackSourceRef,
    ) {
        var artwork = ArtworkUiState(source.bindingId)
        var attempt = 0
        var finished = false
        var started = false
        var cancellationQueued = false
        var cancellation = CancellationSignal()
        var job: Future<*>? = null
        var timer: ScheduledFuture<*>? = null
    }

    private val lock = Any()
    private val entries = LinkedHashMap<ArtworkKey, Entry>(4, 0.75f, true)
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(3))

    // Only the one running resolver can need provider cancellation. Obsolete queued jobs never start.
    private val canceller =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(3),
            { task -> Thread(task, "RiffArtworkCancel").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardOldestPolicy(),
        )
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var current: Pair<Long, Long>? = null
    private var generation: Long? = null
    private var activeQueue: Long? = null
    private var closed = false

    fun select(
        queueRevisionId: Long?,
        occurrenceId: Long?,
    ) = synchronized(lock) {
        current = if (queueRevisionId != null && occurrenceId != null) queueRevisionId to occurrenceId else null
    }

    fun invalidate(
        sourceGeneration: Long,
        queueRevisionId: Long? = null,
    ) = synchronized(lock) {
        val obsolete =
            entries.values.filter {
                it.key.sourceGeneration != sourceGeneration || (queueRevisionId != null && it.key.queueRevisionId != queueRevisionId)
            }
        generation = sourceGeneration
        if (queueRevisionId != null) activeQueue = queueRevisionId
        obsolete.forEach {
            entries.remove(it.key)
            cancel(it)
        }
    }

    /** Returns the current placeholder/cache immediately; provider work belongs to the worker. */
    fun request(
        key: ArtworkKey,
        source: PlaybackSourceRef,
    ): ArtworkUiState {
        entry(key, source) ?: return ArtworkUiState(source.bindingId)
        return synchronized(lock) { entries[key]?.artwork ?: ArtworkUiState(source.bindingId) }
    }

    fun artworkFor(key: ArtworkKey): ArtworkUiState? = synchronized(lock) { entries[key]?.artwork }

    private fun entry(
        key: ArtworkKey,
        source: PlaybackSourceRef,
    ): Entry? =
        synchronized(lock) {
            if (closed ||
                (generation != null && key.sourceGeneration != generation) ||
                (activeQueue != null && key.queueRevisionId != activeQueue)
            ) {
                return@synchronized null
            }
            entries[key]?.let { return@synchronized it }
            while (entries.size >= MAX_ENTRIES) {
                val victim =
                    entries.values.firstOrNull { it.key.queueRevisionId to it.key.occurrenceId != current }
                        ?: entries.values.first()
                entries.remove(victim.key)
                cancel(victim)
            }
            Entry(key, source).also {
                entries[key] = it
                start(it)
            }
        }

    /** Called only under lock. Provider work and waiting never hold it. */
    private fun start(entry: Entry) {
        val attempt = ++entry.attempt
        entry.finished = false
        entry.started = false
        entry.cancellationQueued = false
        val cancellation = CancellationSignal()
        entry.cancellation = cancellation
        entry.timer =
            scheduler.schedule({
                finish(entry, attempt, ArtworkResolution(ArtworkUiState(entry.source.bindingId), true), timedOut = true)
            }, timeoutMs, TimeUnit.MILLISECONDS)
        entry.job =
            worker.submit {
                synchronized(lock) {
                    if (closed || entries[entry.key] !== entry || entry.attempt != attempt || entry.finished) return@submit
                    entry.started = true
                }
                val result =
                    runCatching { resolve(entry.source, cancellation) }
                        .getOrElse { ArtworkResolution(ArtworkUiState(entry.source.bindingId), true) }
                finish(entry, attempt, result, timedOut = false)
            }
    }

    private fun finish(
        entry: Entry,
        attempt: Int,
        result: ArtworkResolution,
        timedOut: Boolean,
    ) = synchronized(lock) {
        if (closed || entries[entry.key] !== entry || entry.attempt != attempt || entry.finished) return@synchronized
        entry.finished = true
        entry.timer?.cancel(false)
        if (timedOut) cancelJob(entry) else entry.started = false
        // No failed/late lookup can demote a successful result for this exact observation.
        if (entry.artwork.bitmap == null || result.artwork.bitmap != null) entry.artwork = result.artwork
        if (result.retryable && entry.artwork.bitmap == null && attempt < MAX_ATTEMPTS) {
            entry.timer =
                scheduler.schedule({
                    synchronized(lock) {
                        if (!closed && entries[entry.key] === entry && entry.attempt == attempt) start(entry)
                    }
                }, retryDelayMs * attempt, TimeUnit.MILLISECONDS)
        }
    }

    private fun cancelJob(entry: Entry) {
        entry.job?.cancel(true)
        if (entry.started && !entry.cancellationQueued) {
            entry.cancellationQueued = true
            val signal = entry.cancellation
            canceller.execute { runCatching { signal.cancel() } }
        }
        (entry.job as? Runnable)?.let(worker::remove)
    }

    private fun cancel(entry: Entry) {
        entry.timer?.cancel(false)
        cancelJob(entry)
    }

    override fun close() =
        synchronized(lock) {
            closed = true
            entries.values.forEach(::cancel)
            entries.clear()
            scheduler.shutdownNow()
            worker.shutdownNow()
            canceller.shutdown()
            Unit
        }

    private companion object {
        const val MAX_ENTRIES = 3
        const val MAX_ATTEMPTS = 3
    }
}
