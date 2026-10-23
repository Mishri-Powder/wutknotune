// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class OrderedPipelineTest {
    @Test fun cancelledPreparedResultIsNeverConsumed() {
        for (depth in 1..3) {
            val cancelled = AtomicBoolean()
            val consumed = mutableListOf<Int>()
            assertThrows(CancellationException::class.java) {
                processOrderedPipeline(
                    listOf(1, 2),
                    depth,
                    cancelled::get,
                    "cancel-prepared",
                    prepare = { item, _ ->
                        cancelled.set(true)
                        item
                    },
                    consume = consumed::add,
                )
            }
            assertTrue(consumed.isEmpty())
        }
    }

    @Test fun cancellationInterruptsBlockedAcquisitionBeforeConsumption() {
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val canceller =
            Thread {
                assertTrue(started.await(2, TimeUnit.SECONDS))
                cancelled.set(true)
            }
        cancelled.set(false)
        canceller.start()
        try {
            assertThrows(CancellationException::class.java) {
                processOrderedPipeline(
                    listOf(1, 2),
                    2,
                    cancelled::get,
                    "cancel-blocked",
                    prepare = { item, _ ->
                        started.countDown()
                        try {
                            CountDownLatch(1).await()
                        } finally {
                            stopped.countDown()
                        }
                        item
                    },
                    consume = { error("Cancelled result reached consumer") },
                )
            }
            assertTrue(stopped.await(2, TimeUnit.SECONDS))
        } finally {
            cancelled.set(true)
            canceller.join(2000)
        }
    }

    @Test fun preparationFailurePreservesPreviouslyConsumedPrefix() {
        val consumed = mutableListOf<Int>()
        assertThrows(IllegalStateException::class.java) {
            processOrderedPipeline(
                listOf(1, 2, 3),
                3,
                { false },
                "failed-prepare",
                prepare = { item, _ ->
                    check(item != 2)
                    item
                },
                consume = consumed::add,
            )
        }
        assertEquals(listOf(1), consumed)
    }

    @Test fun ownerInterruptionCannotCompleteBeforeAcquisitionStops() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ownerFinished = CountDownLatch(1)
        val stopped = AtomicBoolean()
        val cancel = AtomicBoolean()
        val failure = AtomicReference<Throwable>()
        val owner =
            Thread {
                try {
                    processOrderedPipeline(
                        listOf(1, 2),
                        2,
                        cancel::get,
                        "fenced-prepare",
                        prepare = { item, _ ->
                            entered.countDown()
                            try {
                                while (release.count > 0L) {
                                    try {
                                        release.await()
                                    } catch (_: InterruptedException) {
                                    }
                                }
                                item
                            } finally {
                                stopped.set(true)
                            }
                        },
                        consume = { error("Cancelled input was consumed") },
                    )
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    ownerFinished.countDown()
                }
            }
        owner.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            cancel.set(true)
            owner.interrupt()
            assertFalse("Owner returned with a live acquisition worker", ownerFinished.await(100, TimeUnit.MILLISECONDS))
        } finally {
            release.countDown()
            owner.join(2000)
        }
        assertTrue(stopped.get())
        assertTrue(ownerFinished.await(2, TimeUnit.SECONDS))
        assertTrue(failure.get() is CancellationException)
        assertTrue(owner.isInterrupted)
    }

    private val cancelled = AtomicBoolean()
}
