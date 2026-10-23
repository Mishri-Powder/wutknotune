@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.playback.StockPlayback
import link.roadio.riff.player.core.playback.AudiobookAudioParameters
import link.roadio.riff.player.core.playback.AudiobookSpeed
import link.roadio.riff.player.core.settings.PlayerReplayGainMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookAudioControlInstrumentedTest {
    @Test
    fun realPlayersKeepUnityPitchAndRestoreMusicAcrossAllAudioPaths() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".instrumented"))
        instrumentation.runOnMainSync {
            PlayerReplayGainMode.entries.forEach { mode ->
                val renderers =
                    if (mode ==
                        PlayerReplayGainMode.N
                    ) {
                        StockPlayback.renderers(context)
                    } else {
                        ReplayGainRenderersFactory(context, mode, ReplayGainStreamGains())
                    }
                val player = ExoPlayer.Builder(context, renderers).build()
                try {
                    player.setMediaItems(listOf(MediaItem.fromUri("file:///book"), MediaItem.fromUri("file:///music")))
                    AudiobookSpeed.entries.forEach { speed ->
                        player.applyAudiobookAudioParameters(AudiobookAudioParameters.resolve(true, true, speed))
                        assertEquals(speed.multiplier, player.playbackParameters.speed, 0f)
                        assertEquals(1f, player.playbackParameters.pitch, 0f)
                        assertEquals(true, player.skipSilenceEnabled)
                        player.applyAudiobookAudioParameters(AudiobookAudioParameters.resolve(true, false, speed))
                        assertEquals(1f, player.playbackParameters.speed, 0f)
                        assertEquals(1f, player.playbackParameters.pitch, 0f)
                        assertEquals(false, player.skipSilenceEnabled)
                        player.applyAudiobookAudioParameters(AudiobookAudioParameters.resolve(false, true, speed))
                        assertEquals(1f, player.playbackParameters.speed, 0f)
                        assertEquals(false, player.skipSilenceEnabled)
                        assertEquals(2, player.mediaItemCount)
                        assertEquals(0, player.currentMediaItemIndex)
                        assertEquals(0L, player.currentPosition)
                    }
                } finally {
                    player.release()
                }
            }
        }
    }
}
