package link.roadio.riff.player.artwork

import link.roadio.riff.ui.ArtworkUiState
import link.roadio.riff.ui.PlaybackSourceRef
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ArtworkCancellationInstrumentedTest {
    @Test fun nextAudioSourceRequestDoesNotWaitForProviderCancellation() {
        val cancellationEntered = CountDownLatch(1)
        val releaseProvider = CountDownLatch(1)
        val loader =
            PlayerArtworkLoader({ source, cancellation ->
                cancellation.setOnCancelListener {
                    cancellationEntered.countDown()
                    releaseProvider.await(5, TimeUnit.SECONDS)
                }
                releaseProvider.await(5, TimeUnit.SECONDS)
                ArtworkResolution(ArtworkUiState(source.bindingId))
            }, timeoutMs = 250, retryDelayMs = 10_000)
        val caller = Executors.newSingleThreadExecutor()
        try {
            loader.request(ArtworkKey(1, 1, 1, 1), PlaybackSourceRef(1, 1, emptyList()))
            assertTrue("Provider cancellation did not begin", cancellationEntered.await(2, TimeUnit.SECONDS))
            val next =
                caller.submit<ArtworkUiState> {
                    loader.request(ArtworkKey(1, 2, 2, 1), PlaybackSourceRef(2, 1, emptyList()))
                }
            val returned =
                try {
                    next.get(400, TimeUnit.MILLISECONDS)
                    true
                } catch (_: TimeoutException) {
                    false
                }
            assertTrue("Audio-source artwork request blocked over 400 ms behind cancellation under the loader lock", returned)
        } finally {
            releaseProvider.countDown()
            caller.shutdownNow()
            loader.close()
        }
    }
}
