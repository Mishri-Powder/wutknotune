package link.roadio.riff.indexer.core.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class PipelineProgressRatesTest {
    @Test fun aSlowNewPeriodDoesNotReuseTheFastLifetimeAverageOrResetTheTotal() {
        var now = 0L
        val rates = PipelineProgressRates({ now })
        val publication = PipelinePublicationClock({ now }, onCommitted = { rates.committed(it) })
        rates.record(PipelineProgressUnit.MEDIA, 1800)
        now = 30_000
        assertEquals(60.0, rates.sample(PipelineProgressUnit.MEDIA).perSecond, 0.0)
        publication.committed()
        rates.record(PipelineProgressUnit.MEDIA, 1913)
        now = 35_000
        val sample = rates.sample(PipelineProgressUnit.MEDIA)
        assertEquals(1913, sample.completed)
        assertEquals(113, sample.periodCompleted)
        assertEquals(5000, sample.elapsedMillis)
        assertEquals(22.6, sample.perSecond, 0.0)
    }

    @Test fun stagesShareThePublicationBoundaryAndSamplesNeverAdvanceIt() {
        var now = 100L
        val rates = PipelineProgressRates({ now })
        rates.record(PipelineProgressUnit.FOLDERS, 60)
        now += 30_000
        rates.committed(now)
        now += 15_000
        rates.record(PipelineProgressUnit.FOLDERS, 75)
        rates.record(PipelineProgressUnit.MEDIA, 150)
        assertEquals(1.0, rates.sample(PipelineProgressUnit.FOLDERS).perSecond, 0.0)
        assertEquals(10.0, rates.sample(PipelineProgressUnit.MEDIA).perSecond, 0.0)
        rates.record(PipelineProgressUnit.TRACKS, 15)
        assertEquals(1.0, rates.sample(PipelineProgressUnit.TRACKS).perSecond, 0.0)
        assertEquals(15_000, rates.sample(PipelineProgressUnit.MEDIA).elapsedMillis)
        now += 15_000
        assertEquals(5.0, rates.sample(PipelineProgressUnit.MEDIA).perSecond, 0.0)
    }

    @Test fun zeroDurationIsFiniteAndCumulativeCountersCannotGoBackwards() {
        val rates = PipelineProgressRates({ 10L })
        rates.record(PipelineProgressUnit.MEDIA, 100)
        assertEquals(0.0, rates.sample(PipelineProgressUnit.MEDIA).perSecond, 0.0)
        assertTrue(rates.sample(PipelineProgressUnit.MEDIA).perSecond.isFinite())
        rates.committed(10)
        assertEquals(100, rates.sample(PipelineProgressUnit.MEDIA).completed)
        assertThrows(IllegalArgumentException::class.java) { rates.record(PipelineProgressUnit.MEDIA, 99) }
    }

    @Test(timeout = 5000)
    fun asynchronousPublicationResetsAtCommitUsingLiveCompletedWork() {
        val now = AtomicLong(0)
        val rates = PipelineProgressRates(now::get)
        val publication = PipelinePublicationClock(now::get, onCommitted = { rates.committed(it) })
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        rates.record(PipelineProgressUnit.TRACKS, 10)
        now.set(30_000)
        BoundedPublicationWorker("period-test").use { writer ->
            writer.submit {
                started.countDown()
                assertTrue(release.await(1, TimeUnit.SECONDS))
                publication.committed()
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            rates.record(PipelineProgressUnit.TRACKS, 15)
            now.set(40_000)
            assertTrue(publication.due())
            assertEquals(15, rates.sample(PipelineProgressUnit.TRACKS).periodCompleted)
            release.countDown()
            writer.await()
            assertFalse(publication.due())
            rates.record(PipelineProgressUnit.TRACKS, 25)
            now.set(50_000)
            assertEquals(10, rates.sample(PipelineProgressUnit.TRACKS).periodCompleted)
            assertEquals(1.0, rates.sample(PipelineProgressUnit.TRACKS).perSecond, 0.0)
        }
    }

    @Test(timeout = 5000)
    fun failedAndUnchangedOffersLeaveTheCurrentPeriodIntact() {
        var now = 0L
        val rates = PipelineProgressRates({ now })
        val publication = PipelinePublicationClock({ now }, onCommitted = { rates.committed(it) })
        rates.record(PipelineProgressUnit.MEDIA, 100)
        now = 30_000
        assertTrue(publication.due()) // An unchanged offer does not call committed().
        BoundedPublicationWorker("failed-period-test").use { writer ->
            writer.submit { throw IOException("publication failed") }
            assertThrows(IOException::class.java) { writer.await() }
        }
        now = 60_000
        assertTrue(publication.due())
        assertEquals(100, rates.sample(PipelineProgressUnit.MEDIA).periodCompleted)
        assertEquals(60_000, rates.sample(PipelineProgressUnit.MEDIA).elapsedMillis)
        assertEquals(100.0 / 60, rates.sample(PipelineProgressUnit.MEDIA).perSecond, 0.0)
    }
}
