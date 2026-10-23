package link.roadio.riff.player.playback

import android.os.DeadObjectException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.android.ipc.AndroidIndexerArtifactBinder
import link.roadio.riff.indexer.android.ipc.AndroidIndexerArtifactCatalog
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.ipc.player.IPlayerArtifactCallback
import link.roadio.riff.player.core.settings.PlayerDataLocation
import link.roadio.riff.player.storage.ListeningEventType
import link.roadio.riff.player.storage.PlayerStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReleaseBoundaryInstrumentedTest {
    @Test fun sessionRejectsQueueReplacementCommand() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val token =
            androidx.media3.session.SessionToken(
                context,
                android.content.ComponentName(context, link.roadio.riff.player.playback.RiffPlaybackService::class.java),
            )
        val future =
            androidx.media3.session.MediaController
                .Builder(context, token)
                .buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                val allowed = controller.isCommandAvailable(androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM)
                println("AUDIT controller_set_media_item_allowed=$allowed")
                assertFalse("MediaSession must not allow replacement outside the durable READY queue", allowed)
            }
        } finally {
            instrumentation.runOnMainSync {
                androidx.media3.session.MediaController
                    .releaseFuture(future)
            }
        }
    }

    @Test fun completionRetainsAttemptProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "link.roadio.riff.instrumented")
        val directory = File(context.cacheDir, "release-audit-${System.nanoTime()}")
        try {
            PlayerStateStore(directory).use { store ->
                val media = MediaId(1001)
                val attempt = store.recordPlaybackStart(media, 0, 10000, audiobook = true)
                store.commitProfileSelection(2, store.loadSettingsForProfile(2, PlayerDataLocation.HU))
                store.recordListeningEvent(media, attempt, ListeningEventType.COMPLETED, 10000, 10000)
                val misplaced = store.listeningEvents().filter { it.playInstanceId == attempt }
                println("AUDIT profile2_events=$misplaced")
                assertTrue("Profile 2 must not receive the completion of profile 1's attempt", misplaced.isEmpty())
                store.commitProfileSelection(1, store.loadSettingsForProfile(1, PlayerDataLocation.HU))
                assertEquals(
                    listOf(ListeningEventType.PLAY_STARTED, ListeningEventType.COMPLETED),
                    store.listeningEvents().filter { it.playInstanceId == attempt }.map { it.eventType },
                )
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun deadRecipientCannotPreventDeliveryToHealthyRecipients() {
        val binder = AndroidIndexerArtifactBinder(AndroidIndexerArtifactCatalog { _, _ -> null })
        val received = mutableListOf<String?>()

        fun callback(dead: Boolean) =
            object : IPlayerArtifactCallback.Stub() {
                override fun receiveReliablePacket(
                    session: String,
                    seq: Long,
                    type: Int,
                    fields: Array<out String>?,
                ) = Unit

                override fun receiveReliableAck(
                    session: String,
                    seq: Long,
                    fields: Array<out String>?,
                    attachment: link.roadio.riff.ipc.indexer.IndexerArtifactHandle?,
                ) = Unit

                override fun onIndexerArtifactAvailable(
                    kind: String?,
                    volume: String?,
                    generation: Long,
                ) = Unit

                override fun onSafListingIssueDetected(
                    a: Int,
                    b: Array<out String>?,
                    c: Int,
                    d: Array<out String>?,
                ) = Unit

                override fun onIndexerStatusChanged(status: String?) {
                    if (dead && status == "publication") throw DeadObjectException()
                    if (!dead) received += status
                }
            }
        binder.registerPlayerArtifactCallback(callback(true))
        binder.registerPlayerArtifactCallback(callback(false))
        binder.publishStatus("publication")
        binder.publishStatus("next")
        assertTrue(received.containsAll(listOf("publication", "next")))
    }

    @Test fun broadcastsCanOverlapWithoutAbortingIndexer() {
        val binder = AndroidIndexerArtifactBinder(AndroidIndexerArtifactCatalog { _, _ -> null })
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstFailure = AtomicReference<Throwable?>()
        binder.registerPlayerArtifactCallback(
            object : IPlayerArtifactCallback.Stub() {
                override fun receiveReliablePacket(
                    session: String,
                    seq: Long,
                    type: Int,
                    fields: Array<out String>?,
                ) = Unit

                override fun receiveReliableAck(
                    session: String,
                    seq: Long,
                    fields: Array<out String>?,
                    attachment: link.roadio.riff.ipc.indexer.IndexerArtifactHandle?,
                ) = Unit

                override fun onIndexerArtifactAvailable(
                    kind: String?,
                    volume: String?,
                    generation: Long,
                ) = Unit

                override fun onSafListingIssueDetected(
                    a: Int,
                    b: Array<out String>?,
                    c: Int,
                    d: Array<out String>?,
                ) = Unit

                override fun onIndexerStatusChanged(status: String?) {
                    if (status == "hold") {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                    }
                }
            },
        )
        val first =
            Thread {
                try {
                    binder.publishStatus("hold")
                } catch (failure: Throwable) {
                    firstFailure.set(failure)
                }
            }
        first.start()
        var concurrentFailure: Throwable? = null
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            try {
                binder.publishStatus("other worker")
            } catch (failure: Throwable) {
                concurrentFailure = failure
            }
        } finally {
            release.countDown()
            first.join(5000)
        }
        assertNull(firstFailure.get())
        println("AUDIT concurrent_broadcast_failure=$concurrentFailure")
        assertNull("Concurrent progress/publication must not abort Indexer", concurrentFailure)
    }
}
