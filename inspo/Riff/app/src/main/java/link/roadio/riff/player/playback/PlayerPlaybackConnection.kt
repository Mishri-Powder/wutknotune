package link.roadio.riff.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import link.roadio.riff.player.core.settings.PlayerReplayGainMode
import link.roadio.riff.player.core.settings.PlayerReplayGainTarget
import java.util.concurrent.CopyOnWriteArrayList

/**
 * UI-side transport proxy for the service-owned MediaSession.
 *
 * This class publishes a disposable playback projection only. Queue mutations cross the explicit
 * PlayerPlaybackBridge after SQLite commits a READY revision; MediaController commands remain transport-only.
 */
class PlayerPlaybackConnection(
    context: Context,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val observers = CopyOnWriteArrayList<(PlaybackEngineState) -> Unit>()
    private var controller: MediaController? = null
    private var closed = false
    private val controllerFuture: ListenableFuture<MediaController>
    private val playerListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) = publish(player)
        }
    private val progressTicker =
        object : Runnable {
            override fun run() {
                controller?.let(::publish)
                if (!closed) main.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, RiffPlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture.addListener(
            {
                if (closed) return@addListener
                runCatching { controllerFuture.get() }
                    .onSuccess { connected ->
                        controller = connected
                        connected.addListener(playerListener)
                        publish(connected)
                        main.post(progressTicker)
                    }.onFailure { error ->
                        Log.w(TAG, "media_controller_connection_failed", error)
                        publishError("Playback service is unavailable")
                    }
            },
            appContext.mainExecutor,
        )
    }

    fun observe(observer: (PlaybackEngineState) -> Unit): AutoCloseable {
        observers += observer
        controller?.let { observer(snapshot(it)) }
        return AutoCloseable { observers -= observer }
    }

    fun currentState(): PlaybackEngineState? = controller?.let(::snapshot)

    fun activateReadyQueue() = PlayerPlaybackBridge.requestQueueReload()

    fun refreshLibraryClassification() = PlayerPlaybackBridge.requestLibraryRefresh()

    fun setReplayGainSettings(
        mode: PlayerReplayGainMode,
        target: PlayerReplayGainTarget,
    ) = PlayerPlaybackBridge.requestReplayGainSettings(mode, target)

    fun quiesceForStorageChange(): Boolean = PlayerPlaybackBridge.quiesceForStorageChange()

    fun togglePlayback() {
        controller?.let { player ->
            if (player.playWhenReady) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(player.currentMediaItemIndex.coerceAtLeast(0), 0L)
                }
                player.play()
            }
        }
    }

    fun previous() = PlayerPlaybackBridge.requestPrevious()

    fun next() = PlayerPlaybackBridge.requestNext()

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
        }
    }

    fun seek(fraction: Float) {
        controller?.let { player ->
            val duration = player.duration
            if (duration > 0L && duration != C.TIME_UNSET) {
                player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
            }
        }
    }

    override fun close() {
        closed = true
        main.removeCallbacks(progressTicker)
        controller?.removeListener(playerListener)
        controller = null
        observers.clear()
        MediaController.releaseFuture(controllerFuture)
    }

    private fun publish(player: Player) {
        val state = snapshot(player)
        observers.forEach { it(state) }
    }

    private fun publishError(message: String) {
        val state = PlaybackEngineState(error = message)
        observers.forEach { it(state) }
    }

    private fun snapshot(player: Player): PlaybackEngineState {
        val metadata = player.currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
        val extras = metadata.extras
        val queueRevisionId = extras.longOrNull(PlaybackMetadata.EXTRA_QUEUE_REVISION_ID)
        val occurrenceId = extras.longOrNull(PlaybackMetadata.EXTRA_OCCURRENCE_ID)
        val bindingId = extras.longOrNull(PlaybackMetadata.EXTRA_BINDING_ID)
        val duration = player.duration.takeIf { it >= 0L && it != C.TIME_UNSET } ?: 0L
        return PlaybackEngineState(
            queueRevisionId = queueRevisionId,
            occurrenceId = occurrenceId,
            mediaId = extras.mediaIdOrNull(),
            chosenBindingId = bindingId,
            title = metadata.title?.toString() ?: "Nothing played yet",
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode == Player.REPEAT_MODE_ALL,
            playbackState = player.playbackState,
            artwork = PlayerPlaybackBridge.artworkFor(queueRevisionId, occurrenceId, bindingId),
            error = player.playerError?.errorCodeName,
        )
    }

    private fun android.os.Bundle?.longOrNull(key: String): Long? = this?.takeIf { it.containsKey(key) }?.getLong(key)

    private companion object {
        const val TAG = "RiffMedia3Client"
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
