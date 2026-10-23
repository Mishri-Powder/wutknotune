package link.roadio.riff.indexer.core.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BoundedPublicationWorkerTest {
    @Test(timeout = 5_000)
    fun activePublicationCannotAccumulateASecondCandidate() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        BoundedPublicationWorker("publication-test").use { worker ->
            worker.submit {
                started.countDown()
                release.await()
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            assertFalse(worker.available(false))
            release.countDown()
            worker.await()
            assertTrue(worker.available(false))
            worker.submit {}
            worker.await()
        }
    }

    @Test(timeout = 5_000)
    fun publicationFailureReachesTheProducer() {
        BoundedPublicationWorker("publication-test").use { worker ->
            worker.submit { throw IllegalStateException("write failed") }
            assertThrows(IllegalStateException::class.java) { worker.await() }
        }
    }

    @Test(timeout = 5_000)
    fun closeWaitsForWriterCleanupEvenWhenInterrupted() {
        val started = CountDownLatch(1)
        val cleanup = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val interruptPreserved =
            java.util.concurrent.atomic
                .AtomicBoolean()
        val worker = BoundedPublicationWorker("publication-fence-test")
        worker.submit {
            started.countDown()
            try {
                CountDownLatch(1).await()
            } catch (_: InterruptedException) {
                cleanup.countDown()
                // Cleanup may defer repeated cancellation while it releases root-bound resources.
                while (release.count > 0) {
                    try {
                        release.await()
                    } catch (_: InterruptedException) {
                        // Both task cancellation and executor shutdown may interrupt this thread.
                    }
                }
            }
        }
        assertTrue(started.await(1, TimeUnit.SECONDS))
        val closer =
            Thread {
                Thread.currentThread().interrupt()
                worker.close()
                interruptPreserved.set(Thread.currentThread().isInterrupted)
                closed.countDown()
            }
        try {
            closer.start()
            assertTrue(cleanup.await(1, TimeUnit.SECONDS))
            assertFalse("Close must not acknowledge a live writer", closed.await(100, TimeUnit.MILLISECONDS))
        } finally {
            release.countDown()
            closer.join(1_000)
            worker.close()
        }
        assertTrue(closed.await(1, TimeUnit.SECONDS))
        assertTrue(interruptPreserved.get())
    }

    @Test(timeout = 5_000)
    fun interruptedAwaitDoesNotForgetTheActivePublication() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        BoundedPublicationWorker("publication-await-test").use { worker ->
            worker.submit {
                started.countDown()
                release.await()
            }
            try {
                assertTrue(started.await(1, TimeUnit.SECONDS))
                Thread.currentThread().interrupt()
                assertThrows(InterruptedException::class.java) { worker.await() }
                assertFalse(worker.available(false))
                assertThrows(IllegalStateException::class.java) { worker.submit {} }
            } finally {
                Thread.interrupted()
                release.countDown()
            }
            worker.await()
        }
    }
}
