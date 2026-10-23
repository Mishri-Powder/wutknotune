@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * The service owns one focus request and the user's play intent separately from a temporary pause.
 * Platform duck callbacks are requested for both categories so one boundary restores the exact
 * volume and seeks a book before audio resumes. Media3's automatic focus must remain disabled.
 *
 * Authority: https://wiki.271.pics/Riff/LATEST/player-audio-focus
 * AI-INTENT: Use the playing occurrence's classification, never the browser's Books selection.
 */
internal class NavigationAudioFocus(
    context: Context,
    private val player: Player,
    private val handler: Handler,
    private val isBook: () -> Boolean,
) : Player.Listener,
    AutoCloseable {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val focusListener = AudioManager.OnAudioFocusChangeListener(::onFocusChange)
    private var request: AudioFocusRequest? = null

    private fun acquireFocus(): Boolean {
        val next =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(if (isBook()) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusListener, handler)
                .build()
        request = next
        return manager.requestAudioFocus(next) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private var changing = false
    private var interruptedItem: String? = null
    private var rewindOnResume = false
    private var unduckedVolume: Float? = null
    var playRequested: Boolean = player.playWhenReady
        private set

    init {
        player.addListener(this)
    }

    fun setPlayWhenReady(requested: Boolean) {
        if (!requested) {
            cancelResume()
        } else {
            playRequested = acquireFocus()
            interruptedItem = null
            rewindOnResume = false
            restoreVolume()
        }
        change { player.playWhenReady = playRequested }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        if (!changing) setPlayWhenReady(playWhenReady)
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (interruptedItem != null && interruptedItem != mediaItem?.mediaId) {
            playRequested = false
            abandon()
        }
        refreshContentType()
    }

    fun refreshContentType() {
        // A mixed queue may reach a book while music is still ducked by navigation.
        if (unduckedVolume != null && isBook()) pauseForFocus()
        player.setAudioAttributes(
            androidx.media3.common.AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(if (isBook()) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            false,
        )
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        playRequested = false
        abandon()
    }

    // Explicit Pause/Stop during an already focus-paused interval emits no Media3 state change.
    fun cancelResume() {
        playRequested = false
        abandon()
    }

    private fun onFocusChange(focus: Int) {
        if (request == null) return
        when (focus) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                restoreVolume()
                val resume = playRequested && interruptedItem != null && interruptedItem == player.currentMediaItem?.mediaId
                val rewind = rewindOnResume
                interruptedItem = null
                rewindOnResume = false
                if (resume) {
                    change {
                        if (rewind) player.seekTo((player.currentPosition - 2_000L).coerceAtLeast(0L))
                        player.play()
                    }
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (playRequested && player.playWhenReady && !isBook()) {
                    if (unduckedVolume == null) unduckedVolume = player.volume
                    player.volume = requireNotNull(unduckedVolume) * 0.2f
                } else {
                    pauseForFocus()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pauseForFocus()
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                playRequested = false
                abandon()
                change { player.pause() }
            }
        }
    }

    private fun pauseForFocus() {
        if (!playRequested || interruptedItem != null) return
        interruptedItem = player.currentMediaItem?.mediaId
        rewindOnResume = isBook() && player.playWhenReady
        change { player.pause() }
    }

    private fun restoreVolume() {
        unduckedVolume?.let { player.volume = it }
        unduckedVolume = null
    }

    private fun abandon() {
        interruptedItem = null
        rewindOnResume = false
        restoreVolume()
        val previous = request
        request = null
        previous?.let(manager::abandonAudioFocusRequest)
    }

    private inline fun change(action: () -> Unit) {
        changing = true
        try {
            action()
        } finally {
            changing = false
        }
    }

    override fun close() {
        player.removeListener(this)
        abandon()
    }
}
