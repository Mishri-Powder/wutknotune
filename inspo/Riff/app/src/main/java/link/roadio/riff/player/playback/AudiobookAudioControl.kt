@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import link.roadio.riff.player.core.playback.AudiobookAudioParameters

/** Apply both flags together on the player thread; never rebuild the queue or audio path. */
internal fun ExoPlayer.applyAudiobookAudioParameters(parameters: AudiobookAudioParameters) {
    playbackParameters = PlaybackParameters(parameters.speed, parameters.pitch)
    skipSilenceEnabled = parameters.skipSilence
}
