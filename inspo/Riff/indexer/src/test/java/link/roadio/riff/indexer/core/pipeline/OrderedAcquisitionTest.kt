package link.roadio.riff.indexer.core.pipeline

import link.roadio.riff.indexer.core.CancellationSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OrderedAcquisitionTest {
    @Test
    fun overlapsOnlyTheNextItemAndPreservesOrder() {
        val secondPrepared = CountDownLatch(1)
        val thirdPrepared = CountDownLatch(1)
        val consumed = mutableListOf<Int>()

        processBoundedPipeline(
            maxInFlight = 2,
            workset = listOf(1, 2, 3),
            cancellation = AVAILABLE,
            producerThreadName = "pipeline-test",
            prepare = { item, _ ->
                when (item) {
                    2 -> secondPrepared.countDown()
                    3 -> thirdPrepared.countDown()
                }
                item
            },
            consume = { item ->
                if (item == 1) {
                    assertTrue(secondPrepared.await(2, TimeUnit.SECONDS))
                    assertFalse(thirdPrepared.await(100, TimeUnit.MILLISECONDS))
                }
                consumed += item
            },
        )

        assertEquals(listOf(1, 2, 3), consumed)
        assertEquals(0L, thirdPrepared.count)
    }

    @Test
    fun consumerFailureInterruptsPendingPreparation() {
        val secondStarted = CountDownLatch(1)
        val secondStopped = CountDownLatch(1)
        var failure: Throwable? = null

        try {
            processBoundedPipeline(
                maxInFlight = 2,
                workset = listOf(1, 2),
                cancellation = AVAILABLE,
                producerThreadName = "pipeline-cancellation-test",
                prepare = { item, _ ->
                    if (item == 2) {
                        secondStarted.countDown()
                        try {
                            CountDownLatch(1).await()
                        } finally {
                            secondStopped.countDown()
                        }
                    }
                    item
                },
                consume = { item ->
                    if (item == 1) {
                        assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
                        error("consumer failed")
                    }
                },
            )
        } catch (error: IllegalStateException) {
            failure = error
        }

        assertEquals("consumer failed", failure?.message)
        assertTrue(secondStopped.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun boundedDepthThreeOverlapsPreparationWithoutReordering() {
        val thirdPrepared = CountDownLatch(1)
        val fourthPrepared = CountDownLatch(1)
        val consumed = mutableListOf<Int>()
        processBoundedPipeline(
            workset = listOf(1, 2, 3, 4),
            maxInFlight = 3,
            cancellation = AVAILABLE,
            producerThreadName = "bounded-pipeline-test",
            prepare = { item, _ ->
                if (item == 3) thirdPrepared.countDown()
                if (item == 4) fourthPrepared.countDown()
                item
            },
            consume = { item ->
                if (item == 1) {
                    assertTrue(thirdPrepared.await(2, TimeUnit.SECONDS))
                    assertFalse(fourthPrepared.await(100, TimeUnit.MILLISECONDS))
                }
                consumed += item
            },
        )
        assertEquals(listOf(1, 2, 3, 4), consumed)
        assertEquals(0L, fourthPrepared.count)
    }

    @Test
    fun boundedConsumerFailureInterruptsQueuedPreparation() {
        val secondStarted = CountDownLatch(1)
        val secondStopped = CountDownLatch(1)
        val thirdStarted = CountDownLatch(1)
        val failure =
            org.junit.Assert.assertThrows(IllegalStateException::class.java) {
                processBoundedPipeline(
                    workset = listOf(1, 2, 3, 4),
                    maxInFlight = 3,
                    cancellation = AVAILABLE,
                    producerThreadName = "bounded-cancellation-test",
                    prepare = { item, _ ->
                        if (item == 2) {
                            secondStarted.countDown()
                            try {
                                CountDownLatch(1).await()
                            } finally {
                                secondStopped.countDown()
                            }
                        }
                        if (item == 3) thirdStarted.countDown()
                        item
                    },
                    consume = { item ->
                        if (item == 1) {
                            assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
                            error("consumer failed")
                        }
                    },
                )
            }
        assertEquals("consumer failed", failure.message)
        assertTrue(secondStopped.await(2, TimeUnit.SECONDS))
        assertEquals(1L, thirdStarted.count)
    }

    @Test
    fun boundedDepthOneRunsSynchronouslyAndRejectsUnboundedValues() {
        val prepareThreads = mutableListOf<Thread>()
        val consumingThread = Thread.currentThread()
        processBoundedPipeline(
            workset = listOf(1, 2),
            maxInFlight = 1,
            cancellation = AVAILABLE,
            producerThreadName = "unused",
            prepare = { item, _ ->
                prepareThreads += Thread.currentThread()
                item
            },
            consume = {},
        )
        assertEquals(listOf(consumingThread, consumingThread), prepareThreads)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            processBoundedPipeline<Int, Int>(
                workset = emptyList(),
                maxInFlight = 9,
                cancellation = AVAILABLE,
                producerThreadName = "unused",
                prepare = { item, _ -> item },
                consume = {},
            )
        }
    }

    private companion object {
        val AVAILABLE =
            object : CancellationSignal {
                override val cancelled = false

                override fun ensureAvailable() = Unit
            }
    }
}
