package link.roadio.riff.player.storage

import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.settings.PlayerDataLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProfileCommandInstrumentedTest {
    @Test fun delayedReactionBelongsToGestureProfile() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "audit-profile-" + System.nanoTime())
        val worker = Executors.newSingleThreadExecutor()
        val release = CountDownLatch(1)
        try {
            PlayerStateStore(directory).use { store ->
                assertEquals(1, store.activeProfile())
                worker.submit { release.await(5, TimeUnit.SECONDS) }
                val profile = store.activeProfile()
                // A delayed command carries its acceptance-time profile into the transaction.
                val reaction = worker.submit { store.updateReaction("track:audit", StoredPreference.LIKED, false, profile) }
                store.commitProfileSelection(2, store.loadSettingsForProfile(2, PlayerDataLocation.HU))
                release.countDown()
                reaction.get(3, TimeUnit.SECONDS)
                assertEquals(emptyMap<String, StoredReaction>(), store.loadReactions(2))
                assertThrows(IllegalStateException::class.java) {
                    store.materializeQueue(
                        listOf(MediaId(17)),
                        PlaybackQueueKind.MANUAL,
                        true,
                        profileId = profile,
                        requireCurrentProfile = true,
                    )
                }
                assertNull(store.loadActiveQueue())
                assertEquals(store.loadAiPlaybackHistory(1), store.loadAiPlaybackHistory(2))
                assertEquals(
                    "Gesture made in profile 1 must not modify profile 2",
                    StoredPreference.LIKED,
                    store.loadReactions(1)["track:audit"]?.preference,
                )
            }
        } finally {
            release.countDown()
            worker.shutdownNow()
            directory.deleteRecursively()
        }
    }
}
