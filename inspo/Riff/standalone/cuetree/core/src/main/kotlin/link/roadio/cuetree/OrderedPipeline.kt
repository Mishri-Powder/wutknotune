// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import java.util.ArrayDeque
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Only the caller integrates facts, so acquisition cannot race catalog publication.
 * Depth includes the consumed result. Contract: https://wiki.271.pics/Cuetree/API/Indexing#ordered-acquisition
 */
fun <Input, Prepared> processOrderedPipeline(
    workset: List<Input>,
    maxInFlight: Int,
    cancelled: () -> Boolean,
    producerThreadName: String,
    prepare: (Input, () -> Boolean) -> Prepared,
    consume: (Prepared) -> Unit,
) {
    require(maxInFlight in 1..8)
    if (workset.isEmpty()) return

    fun check() {
        Cancellation(cancelled).check()
    }
    if (maxInFlight == 1 || workset.size == 1) {
        workset.forEach { item ->
            check()
            val prepared = prepare(item, cancelled)
            check()
            consume(prepared)
        }
        return
    }
    val stopped = AtomicBoolean(false)
    val producerCancelled = { cancelled() || stopped.get() || Thread.currentThread().isInterrupted }
    val executor = Executors.newSingleThreadExecutor { task -> Thread(task, producerThreadName) }
    val pending = ArrayDeque<Future<Prepared>>()
    var next = 0
    try {
        while (next < workset.size && pending.size < maxInFlight) {
            val item = workset[next++]
            pending.addLast(
                executor.submit<Prepared> {
                    Cancellation(producerCancelled).check()
                    prepare(item, producerCancelled)
                },
            )
        }
        workset.forEach {
            check()
            val prepared =
                try {
                    val future = pending.first()
                    var result: Prepared
                    while (true) {
                        check()
                        try {
                            result = future.get(50, TimeUnit.MILLISECONDS)
                            break
                        } catch (_: TimeoutException) {
                            // Cancellation must be observed even while acquisition waits for source I/O.
                        }
                    }
                    pending.removeFirst()
                    result
                } catch (error: ExecutionException) {
                    throw error.cause ?: error
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw CancellationException("Cuetree cancelled").apply { initCause(error) }
                }
            check()
            consume(prepared)
            if (next < workset.size) {
                val item = workset[next++]
                pending.addLast(
                    executor.submit<Prepared> {
                        Cancellation(producerCancelled).check()
                        prepare(item, producerCancelled)
                    },
                )
            }
        }
    } finally {
        stopped.set(true)
        pending.forEach { it.cancel(true) }
        executor.shutdownNow()
        // AI-INTENT: completion is a resource fence; hosts must not relocate storage with live acquisition.
        // A host may time out its barrier, but that does not make blocked source I/O quiescent.
        // https://wiki.271.pics/Cuetree/API/Indexing#ordered-acquisition
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
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}
