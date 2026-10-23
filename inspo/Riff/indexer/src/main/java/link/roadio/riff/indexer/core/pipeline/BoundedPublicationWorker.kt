package link.roadio.riff.indexer.core.pipeline

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * One immutable publication in flight; producer work can continue without queuing stale cohorts.
 * Closing is a resource fence: cancellation requests alone do not make the old root safe to move.
 * Authority: https://wiki.271.pics/Riff/LATEST/storage-roots#3-complete-migration
 */
internal class BoundedPublicationWorker(
    threadName: String,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, threadName) }
    private var inFlight: Future<*>? = null

    fun available(force: Boolean): Boolean {
        val pending = inFlight ?: return true
        if (!force && !pending.isDone) return false
        await()
        return true
    }

    fun submit(action: () -> Unit) {
        check(inFlight == null)
        inFlight = executor.submit(action)
    }

    fun await() {
        val pending = inFlight ?: return
        try {
            pending.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } finally {
            // An interrupted waiter does not own the writer's completion.
            if (pending.isDone) inFlight = null
        }
    }

    override fun close() {
        inFlight?.cancel(true)
        executor.shutdownNow()
        // AI-INTENT: The host may time out its relocation fence, but must never acknowledge
        // quiescence while this writer can still access the selected root.
        var interrupted = Thread.interrupted()
        try {
            while (!executor.isTerminated) {
                try {
                    executor.awaitTermination(30, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        } finally {
            inFlight = null
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}
