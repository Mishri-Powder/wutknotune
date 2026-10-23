@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.os.Bundle
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.artwork.ArtworkResolution
import link.roadio.riff.player.artwork.PlayerArtworkLoader
import link.roadio.riff.player.storage.ListeningEventType
import link.roadio.riff.player.storage.PlayerProcessStore
import link.roadio.riff.player.storage.PlayerStateStore
import link.roadio.riff.storage.root.ApplicationDataStorage
import link.roadio.riff.ui.ArtworkUiState
import link.roadio.riff.ui.UiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Drives the production service listener without audio hardware or timing-dependent media. */
class PlaybackHistoryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun field(name: String) = RiffPlaybackService::class.java.getDeclaredField(name).apply { isAccessible = true }

    private inner class Fixture : AutoCloseable {
        val store: PlayerStateStore
        val firstId = MediaId(System.nanoTime())
        val secondId = MediaId(firstId.high + 1)
        val first = item(1, firstId)
        val second = item(2, secondId)
        var current = first
        var position = 0L
        var playing = true
        lateinit var service: RiffPlaybackService
        lateinit var events: Player.Listener
        lateinit var player: ExoPlayer

        init {
            val context = instrumentation.targetContext
            check(context.packageName.endsWith(".instrumented"))
            check(ApplicationDataStorage(context).prepareForStartup())
            val config = UiConfig.load(context)
            store =
                requireNotNull(
                    PlayerProcessStore.open(
                        context,
                        config.long("player.recentHistoryGenerations"),
                        config.long("player.bookmarkMinSpacingSeconds"),
                    ),
                )
            main {
                service = RiffPlaybackService()
                player =
                    Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, _ ->
                        when (method.name) {
                            "getCurrentMediaItem" -> {
                                current
                            }

                            "getCurrentPosition" -> {
                                position
                            }

                            "getDuration" -> {
                                12_000L
                            }

                            "isPlaying", "getPlayWhenReady" -> {
                                playing
                            }

                            else -> {
                                when (method.returnType) {
                                    java.lang.Boolean.TYPE -> false
                                    java.lang.Integer.TYPE -> 0
                                    java.lang.Long.TYPE -> 0L
                                    java.lang.Float.TYPE -> 1f
                                    else -> null
                                }
                            }
                        }
                    } as ExoPlayer
                field("exoPlayer").set(service, player)
                field("artworkLoader").set(service, PlayerArtworkLoader({ _, _ -> ArtworkResolution(ArtworkUiState()) }))
                events = field("playerEvents").get(service) as Player.Listener
            }
        }

        fun frame() {
            main { events.onEvents(player, Player.Events(FlagSet.Builder().build())) }
            drain()
        }

        fun transition(
            next: MediaItem,
            reason: Int,
            oldPosition: Long,
        ) {
            val previous = current
            main {
                current = next
                position = 0
                events.onPositionDiscontinuity(info(previous, oldPosition), info(next, 0), reason)
                events.onMediaItemTransition(
                    next,
                    if (reason ==
                        Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                    ) {
                        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                    } else {
                        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                    },
                )
                events.onEvents(player, Player.Events(FlagSet.Builder().build()))
            }
            drain()
        }

        fun records() = store.listeningEvents().filter { it.mediaId in setOf(firstId, secondId) }

        fun command(name: String) {
            main {
                RiffPlaybackService::class.java
                    .getDeclaredMethod(name)
                    .apply { isAccessible = true }
                    .invoke(service)
            }
            drain()
        }

        private fun drain() {
            (field("stateExecutor").get(service) as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
        }

        override fun close() {
            drain()
            main { (field("artworkLoader").get(service) as PlayerArtworkLoader).close() }
            for (name in listOf("stateExecutor", "queueExecutor", "sourceExecutor")) {
                (field(name).get(service) as ExecutorService).shutdownNow()
            }
        }

        private fun item(
            occurrence: Long,
            id: MediaId,
        ): MediaItem {
            val extras =
                Bundle().apply {
                    putLong(PlaybackMetadata.EXTRA_QUEUE_REVISION_ID, firstId.high)
                    putLong(PlaybackMetadata.EXTRA_OCCURRENCE_ID, occurrence)
                    putMediaId(id)
                }
            return MediaItem
                .Builder()
                .setMediaId(
                    occurrence.toString(),
                ).setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build())
                .build()
        }

        private fun info(
            item: MediaItem,
            position: Long,
        ) = Player.PositionInfo(null, if (item == first) 0 else 1, item, null, 0, position, position, -1, -1)
    }

    @Test fun finalNextIsAUserSkipAndCensorUsesTheActualPosition() {
        Fixture().use { fixture ->
            fixture.frame()
            fixture.position = 3_250
            fixture.command("dispatchNext")
            val end = fixture.records().last()
            assertEquals(ListeningEventType.USER_SKIPPED, end.eventType)
            assertEquals(3_250L, end.positionMs)
        }
        Fixture().use { fixture ->
            fixture.frame()
            fixture.position = 6_789
            fixture.command("endCurrentAttemptAsCensored")
            val end = fixture.records().last()
            assertEquals(ListeningEventType.CENSORED_END, end.eventType)
            assertEquals(6_789L, end.positionMs)
        }
    }

    @Test fun seamlessTransitionRecordsTerminalPositionAndNextStartWithoutPlayingToggle() {
        Fixture().use { fixture ->
            fixture.frame()
            fixture.transition(fixture.second, Player.DISCONTINUITY_REASON_AUTO_TRANSITION, 12_000)
            val records = fixture.records()
            assertEquals(
                listOf(
                    ListeningEventType.PLAY_STARTED,
                    ListeningEventType.COMPLETED,
                    ListeningEventType.PLAY_STARTED,
                ),
                records.map {
                    it.eventType
                },
            )
            assertEquals(12_000L, records[1].positionMs)
            assertEquals(fixture.firstId, records[1].mediaId)
            assertEquals(fixture.secondId, records[2].mediaId)
        }
    }

    @Test fun repeatAllocatesAnotherAttemptButPauseResumeAndInFileSeekDoNot() {
        Fixture().use { fixture ->
            fixture.frame()
            fixture.playing = false
            fixture.frame()
            fixture.playing = true
            fixture.frame()
            fixture.transition(fixture.first, Player.DISCONTINUITY_REASON_SEEK, 4_000)
            assertEquals(1, fixture.records().size)
            fixture.transition(fixture.first, Player.DISCONTINUITY_REASON_AUTO_TRANSITION, 12_000)
            val starts = fixture.records().filter { it.eventType == ListeningEventType.PLAY_STARTED }
            assertEquals(2, starts.size)
            assertTrue(starts[0].playInstanceId != starts[1].playInstanceId)
        }
    }

    @Test fun crossOccurrenceUserSeekRecordsSkipButTechnicalSkipDoesNot() {
        Fixture().use { fixture ->
            fixture.frame()
            fixture.transition(fixture.second, Player.DISCONTINUITY_REASON_SEEK, 3_250)
            val skipped = fixture.records().single { it.eventType == ListeningEventType.USER_SKIPPED }
            assertEquals(3_250L, skipped.positionMs)
            fixture.transition(fixture.second, Player.DISCONTINUITY_REASON_SKIP, 5_000)
            assertEquals(1, fixture.records().count { it.eventType == ListeningEventType.USER_SKIPPED })
        }
    }
}
