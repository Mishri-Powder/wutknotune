@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.MainActivity
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.playback.StockPlayback
import link.roadio.riff.player.core.playback.AudiobookAudioParameters
import link.roadio.riff.player.core.playback.AudiobookSpeed
import link.roadio.riff.player.core.settings.PlayerDataLocation
import link.roadio.riff.player.core.settings.PlayerReplayGainMode
import link.roadio.riff.player.library.PlayerLibraryModel
import link.roadio.riff.player.library.PlayerLibraryModels
import link.roadio.riff.player.library.PlayerLibraryProjection
import link.roadio.riff.player.library.PlayerLibraryRepository
import link.roadio.riff.player.storage.PlaybackQueueKind
import link.roadio.riff.player.storage.PlayerProcessStore
import link.roadio.riff.player.storage.StoredContentMode
import link.roadio.riff.storage.root.ApplicationDataStorage
import link.roadio.riff.ui.BrowserItem
import link.roadio.riff.ui.BrowserKind
import link.roadio.riff.ui.UiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.sin

/** Measures completed AudioTrack playout, rather than a controller's extrapolated position. */
class AudiobookRenderedPlaybackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun serviceSwitchesCachedProjectionAndUsesCurrentProfileWithoutRebuildingTheCatalog() {
        check(context.packageName.endsWith(".instrumented"))
        check(ApplicationDataStorage(context).prepareForStartup())
        val config = UiConfig.load(context)
        val store =
            requireNotNull(
                PlayerProcessStore.open(
                    context,
                    config.long("player.recentHistoryGenerations"),
                    config.long("player.bookmarkMinSpacingSeconds"),
                ),
            )
        val originalProfile = store.activeProfile()
        val modes = listOf(1, 2).associateWith(store::loadContentMode)
        val speeds = listOf(1, 2).associateWith(store::loadAudiobookSpeed)
        store.commitProfileSelection(1, store.loadSettingsForProfile(1, PlayerDataLocation.HU))
        store.setContentMode(StoredContentMode.MUSIC, 1)
        store.setContentMode(StoredContentMode.AUDIOBOOK, 2)
        store.saveAudiobookSpeed(1, AudiobookSpeed.FIFTEEN_TENTHS)
        store.saveAudiobookSpeed(2, AudiobookSpeed.SEVEN_TENTHS)
        val id = MediaId(981234L)
        val track = BrowserItem("tempo", "Tempo", BrowserKind.TRACK, playable = true, mediaId = id)
        val music =
            PlayerLibraryModel(
                BrowserItem("root", "Fixture", BrowserKind.ROOT),
                listOf(track),
                playbackSourcesByMediaId = emptyMap(),
                generation = 1,
                mediaIds = setOf(id),
                effectiveBookMediaIds = setOf(id),
            )
        val books = music.copy(projection = PlayerLibraryProjection(StoredContentMode.AUDIOBOOK))
        val repository = PlayerLibraryRepository.get(context, config.excludedGenreIds())
        val cache = repository.javaClass.getDeclaredField("cachedModels").apply { isAccessible = true }
        val originalCache = cache.get(repository)
        val bound = repository.javaClass.getDeclaredField("bound").apply { isAccessible = true }
        val originallyBound = bound.getBoolean(repository)
        // Keep the synthetic cached cohort fixed; a real empty Indexer must not replace this fixture.
        check(!originallyBound && field(repository, "service") == null)
        bound.setBoolean(repository, true)
        cache.set(repository, PlayerLibraryModels(music, books))
        // Android 15+ requires a foreground Activity or service when reacquiring audio focus.
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val future =
            MediaController
                .Builder(
                    context,
                    SessionToken(context, ComponentName(context, RiffPlaybackService::class.java)),
                ).buildAsync()
        val wav = toneFile()
        try {
            future.get(10, TimeUnit.SECONDS)
            await("service attached") { owner() != null }
            val service = requireNotNull(owner())
            PlayerPlaybackBridge.requestLibraryRefresh()
            await("initial Music projection") {
                field(service, "library") === music &&
                    field(service, "audiobookParametersReady") == true &&
                    field(service, "reloadPending") == false
            }
            val queue = store.materializeQueue(listOf(id), PlaybackQueueKind.MANUAL, false, audiobook = true)
            val occurrence = queue.occurrences.single()
            val extras =
                Bundle().apply {
                    putLong(PlaybackMetadata.EXTRA_QUEUE_REVISION_ID, queue.revisionId)
                    putLong(PlaybackMetadata.EXTRA_OCCURRENCE_ID, occurrence.occurrenceId)
                    putMediaId(id)
                }
            val item =
                MediaItem
                    .Builder()
                    .setUri(
                        Uri.fromFile(wav),
                    ).setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build())
                    .build()
            val player = field(service, "exoPlayer") as ExoPlayer
            onMain {
                player.volume = 0f
                player.setMediaItem(item)
                assertEquals(1f, player.playbackParameters.speed, 0f)
            }
            store.setContentMode(StoredContentMode.AUDIOBOOK, 1)
            PlayerPlaybackBridge.requestLibraryRefresh()
            await("Books projection on unchanged cached catalog") {
                field(service, "library") === books &&
                    player.playbackParameters.speed == 1.5f
            }
            onMain {
                assertEquals(1f, player.playbackParameters.pitch, 0f)
                assertTrue(player.skipSilenceEnabled)
                assertEquals(1, player.mediaItemCount)
            }
            val fastTime = render(player)
            assertElapsed("service 1.5x", fastTime, 2_300, 3_600)
            store.commitProfileSelection(2, store.loadSettingsForProfile(2, PlayerDataLocation.HU))
            PlayerPlaybackBridge.requestAudiobookParametersRefresh()
            await("current profile 0.7x") { player.playbackParameters.speed == 0.7f }
            val slowTime = render(player)
            assertElapsed("service 0.7x", slowTime, 5_000, 6_900)
            store.setContentMode(StoredContentMode.MUSIC, 2)
            PlayerPlaybackBridge.requestLibraryRefresh()
            await("Music restores unity") {
                field(service, "library") === music &&
                    player.playbackParameters.speed == 1f &&
                    !player.skipSilenceEnabled
            }
            onMain { assertEquals(item, player.currentMediaItem) }
        } finally {
            onMain { MediaController.releaseFuture(future) }
            activity.close()
            context.stopService(Intent(context, RiffPlaybackService::class.java))
            await("service stopped") { owner() == null }
            cache.set(repository, originalCache)
            bound.setBoolean(repository, originallyBound)
            modes.forEach { (profile, mode) -> store.setContentMode(mode, profile) }
            speeds.forEach { (profile, speed) -> store.saveAudiobookSpeed(profile, speed) }
            store.commitProfileSelection(originalProfile, store.loadSettingsForProfile(originalProfile, PlayerDataLocation.HU))
            wav.delete()
        }
    }

    @Test
    fun allAudioPathsRenderRangeEndpointsAndRemoveSilence() {
        check(context.packageName.endsWith(".instrumented"))
        val tone = toneFile()
        val silence = toneFile(silentMiddle = true)
        try {
            PlayerReplayGainMode.entries.forEach { mode ->
                var player: ExoPlayer? = null
                onMain {
                    val renderers =
                        if (mode ==
                            PlayerReplayGainMode.N
                        ) {
                            StockPlayback.renderers(context)
                        } else {
                            ReplayGainRenderersFactory(context, mode, ReplayGainStreamGains())
                        }
                    player = ExoPlayer.Builder(context, renderers).build().apply { volume = 0f }
                }
                val current = requireNotNull(player)
                try {
                    listOf(AudiobookSpeed.FIFTEEN_TENTHS, AudiobookSpeed.SEVEN_TENTHS).forEach { speed ->
                        onMain {
                            current.setMediaItem(MediaItem.fromUri(Uri.fromFile(tone)))
                            current.applyAudiobookAudioParameters(AudiobookAudioParameters.resolve(true, true, speed))
                        }
                        val elapsed = render(current)
                        if (speed ==
                            AudiobookSpeed.FIFTEEN_TENTHS
                        ) {
                            assertElapsed("$mode ${speed.label}", elapsed, 2_300, 3_600)
                        } else {
                            assertElapsed("$mode ${speed.label}", elapsed, 5_000, 6_900)
                        }
                    }
                    onMain {
                        current.setMediaItem(MediaItem.fromUri(Uri.fromFile(silence)))
                        current.applyAudiobookAudioParameters(AudiobookAudioParameters.resolve(true, true, AudiobookSpeed.NORMAL))
                    }
                    assertElapsed("$mode skip silence", render(current), 1_500, 3_000)
                } finally {
                    onMain { current.release() }
                }
            }
        } finally {
            tone.delete()
            silence.delete()
        }
    }

    private fun render(player: ExoPlayer): Long {
        onMain {
            player.seekTo(0)
            player.prepare()
            player.play()
        }
        await("audio started") {
            player.playerError?.let { throw AssertionError("Audio failed", it) }
            player.isPlaying
        }
        val start = SystemClock.elapsedRealtime()
        await("audio ended", 15_000) {
            player.playerError?.let { throw AssertionError("Audio failed", it) }
            player.playbackState == Player.STATE_ENDED
        }
        return SystemClock.elapsedRealtime() - start
    }

    private fun assertElapsed(
        label: String,
        elapsed: Long,
        minimum: Long,
        maximum: Long,
    ) {
        Log.i("RiffRenderedTempoTest", "$label elapsed_ms=$elapsed")
        assertTrue("$label: $elapsed ms outside $minimum..$maximum", elapsed in minimum..maximum)
    }

    private fun toneFile(silentMiddle: Boolean = false): File {
        val sampleRate = 48_000
        val sampleCount = sampleRate * 4
        val bytes = ByteBuffer.allocate(44 + sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes
            .put("RIFF".toByteArray())
            .putInt(36 + sampleCount * 2)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
        bytes
            .putShort(1)
            .putShort(1)
            .putInt(sampleRate)
            .putInt(sampleRate * 2)
            .putShort(2)
            .putShort(16)
        bytes.put("data".toByteArray()).putInt(sampleCount * 2)
        repeat(sampleCount) { index ->
            val silent = silentMiddle && index in sampleRate until sampleRate * 3
            bytes.putShort(if (silent) 0 else (8_000 * sin(2 * Math.PI * 440 * index / sampleRate)).toInt().toShort())
        }
        return File.createTempFile("rendered-tempo-", ".wav", context.cacheDir).apply { writeBytes(bytes.array()) }
    }

    private fun owner(): Any? = field(PlayerPlaybackBridge, "owner")

    private fun field(
        target: Any,
        name: String,
    ): Any? =
        target.javaClass
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(target)

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun await(
        label: String,
        timeoutMs: Long = 10_000,
        ready: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var done = false
            onMain { done = ready() }
            if (done) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out: $label")
    }
}
