package link.roadio.riff.player.artwork

import android.graphics.Bitmap
import link.roadio.riff.ui.ArtworkOrigin
import link.roadio.riff.ui.ArtworkUiState
import link.roadio.riff.ui.PlaybackSourceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PlayerArtworkLoaderInstrumentedTest {
    private fun source(binding: Long) = PlaybackSourceRef(binding, 1L, emptyList())

    private fun key(
        occurrence: Long,
        binding: Long = occurrence,
        generation: Long = 1,
        queue: Long = 1,
    ) = ArtworkKey(queue, occurrence, binding, generation)

    private fun await(ready: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!ready() && System.nanoTime() < until) Thread.sleep(10)
        assertTrue("Artwork did not settle", ready())
    }

    @Test fun transientDefaultRecoversAndRecreationReadsTheSameSuccessfulCache() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val attempts = AtomicInteger()
        val allowRecovery = CountDownLatch(1)
        PlayerArtworkLoader({ source, _ ->
            if (attempts.incrementAndGet() == 1) {
                ArtworkResolution(ArtworkUiState(source.bindingId), true)
            } else {
                check(allowRecovery.await(5, TimeUnit.SECONDS))
                ArtworkResolution(ArtworkUiState(source.bindingId, bitmap, ArtworkOrigin.EMBEDDED))
            }
        }, retryDelayMs = 10).use { loader ->
            assertNull(loader.request(key(1), source(1)).bitmap)
            allowRecovery.countDown()
            await { loader.artworkFor(key(1))?.bitmap === bitmap }
            assertSame(bitmap, loader.request(key(1), source(1)).bitmap)
            assertEquals(2, attempts.get())
        }
        bitmap.recycle()
    }

    @Test fun confirmedAbsenceDoesNotRetryAndPrefetchCannotReplaceCurrentArtwork() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val attempts = AtomicInteger()
        PlayerArtworkLoader({ source, _ ->
            attempts.incrementAndGet()
            ArtworkResolution(ArtworkUiState(source.bindingId, bitmap.takeIf { source.bindingId == 1L }))
        }, retryDelayMs = 10).use { loader ->
            loader.select(1, 1)
            loader.request(key(1), source(1))
            await { loader.artworkFor(key(1))?.bitmap === bitmap }
            for (id in 2L..8L) {
                loader.request(key(id), source(id))
                await { attempts.get() >= id.toInt() }
            }
            Thread.sleep(80)
            assertSame(bitmap, loader.artworkFor(key(1))?.bitmap)
            assertNull(loader.artworkFor(key(8))?.bitmap)
            assertNull(loader.artworkFor(key(2)))
            assertEquals(8, attempts.get())
        }
        bitmap.recycle()
    }

    @Test fun obsoleteUncooperativeLookupCannotOverwriteNewBindingOrSourceGeneration() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        PlayerArtworkLoader({ source, _ ->
            if (source.bindingId == 1L) {
                entered.countDown()
                while (release.count > 0) {
                    try {
                        release.await()
                    } catch (_: InterruptedException) {
                        // Deliberately emulate an uncooperative provider.
                    }
                }
            }
            ArtworkResolution(ArtworkUiState(source.bindingId, bitmap.takeIf { source.bindingId == 2L }))
        }, timeoutMs = 500, retryDelayMs = 10).use { loader ->
            loader.invalidate(1, 1)
            loader.request(key(1), source(1))
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            loader.invalidate(2, 2)
            loader.request(key(1, binding = 2, generation = 2, queue = 2), source(2))
            release.countDown()
            await { loader.artworkFor(key(1, binding = 2, generation = 2, queue = 2))?.bitmap === bitmap }
            assertNull(loader.artworkFor(key(1)))
            loader.request(key(1), source(1))
            assertNull(loader.artworkFor(key(1)))
        }
        bitmap.recycle()
    }

    @Test fun slowProviderReachesDefaultByDeadlineAndCanRecoverOnRetry() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val attempts = AtomicInteger()
        val interrupted = CountDownLatch(1)
        PlayerArtworkLoader({ source, _ ->
            if (attempts.incrementAndGet() == 1) {
                try {
                    CountDownLatch(1).await()
                } catch (_: InterruptedException) {
                    interrupted.countDown()
                }
                ArtworkResolution(ArtworkUiState(source.bindingId), true)
            } else {
                ArtworkResolution(ArtworkUiState(source.bindingId, bitmap))
            }
        }, timeoutMs = 100, retryDelayMs = 100).use { loader ->
            val started = System.nanoTime()
            assertNull(loader.request(key(1), source(1)).bitmap)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000)
            assertTrue(interrupted.await(2, TimeUnit.SECONDS))
            await { loader.artworkFor(key(1))?.bitmap === bitmap }
            assertEquals(2, attempts.get())
        }
        bitmap.recycle()
    }

    @Test fun retryCountAndReadinessStayBoundedWhenEveryAttemptFails() {
        val attempts = AtomicInteger()
        PlayerArtworkLoader({ source, _ ->
            attempts.incrementAndGet()
            ArtworkResolution(ArtworkUiState(source.bindingId), true)
        }, timeoutMs = 50, retryDelayMs = 10).use { loader ->
            loader.request(key(1), source(1))
            await { attempts.get() == 3 }
            Thread.sleep(80)
            assertEquals(3, attempts.get())
            assertNull(loader.artworkFor(key(1))?.bitmap)
        }
    }
}
