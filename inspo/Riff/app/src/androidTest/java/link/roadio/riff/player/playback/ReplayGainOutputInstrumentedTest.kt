@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.media.AudioAttributes
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.settings.PlayerReplayGainMode
import link.roadio.riff.player.core.settings.PlayerReplayGainTarget
import link.roadio.riff.player.library.PlayerReplayGainValue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin

/** Manual physical-output probe. It is skipped unless the oscilloscope harness explicitly opts in. */
@RunWith(AndroidJUnit4::class)
class ReplayGainOutputInstrumentedTest {
    @Test
    fun emitsCalibratedToneThroughProductionAudioPath() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Requires the external ReplayGain oscilloscope harness",
            arguments.getString(ARG_SCOPE_ENABLED) == "true",
        )
        val mode = PlayerReplayGainMode.valueOf(arguments.getString(ARG_MODE) ?: "N")
        val targetValue = arguments.getString(ARG_TARGET)?.toIntOrNull() ?: 18
        val target = PlayerReplayGainTarget.entries.single { it.displayValue == targetValue }
        val gainQ8 = arguments.getString(ARG_GAIN_Q8)?.toIntOrNull() ?: DEFAULT_GAIN_Q8
        val holdMillis = arguments.getString(ARG_HOLD_MILLIS)?.toLongOrNull() ?: DEFAULT_HOLD_MILLIS
        val context = instrumentation.targetContext
        val tone = File(context.cacheDir, "replaygain-output-probe.wav")
        writeTone(tone)

        val mediaId = MediaId(42L)
        val streamGains = ReplayGainStreamGains()
        streamGains.setTarget(target)
        streamGains.replace(mapOf(mediaId to PlayerReplayGainValue(trackGainQ8 = gainQ8.toShort())))
        streamGains.setQueue(listOf(mediaId))
        val playerReference = AtomicReference<ExoPlayer>()
        val playing = CountDownLatch(1)

        try {
            instrumentation.runOnMainSync {
                val renderersFactory =
                    if (mode == PlayerReplayGainMode.N) {
                        DefaultRenderersFactory(context)
                    } else {
                        ReplayGainRenderersFactory(context, mode, streamGains)
                    }
                val extras = Bundle().apply { putMediaId(mediaId) }
                val mediaItem =
                    MediaItem
                        .Builder()
                        .setUri(Uri.fromFile(tone))
                        .setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build())
                        .build()
                val player = ExoPlayer.Builder(context, renderersFactory).build()
                playerReference.set(player)
                player.addListener(
                    object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) playing.countDown()
                        }
                    },
                )
                player.setAudioAttributes(
                    androidx.media3.common.AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }
            check(playing.await(10L, TimeUnit.SECONDS)) { "Test tone did not start" }
            Thread.sleep(holdMillis)
        } finally {
            instrumentation.runOnMainSync { playerReference.getAndSet(null)?.release() }
            tone.delete()
        }
    }

    private fun writeTone(file: File) {
        val frameCount = SAMPLE_RATE * DURATION_SECONDS
        BufferedOutputStream(FileOutputStream(file)).use { output ->
            val dataBytes = frameCount * CHANNEL_COUNT * Short.SIZE_BYTES
            output.write(wavHeader(dataBytes))
            val buffer =
                ByteBuffer
                    .allocate(SAMPLE_RATE * CHANNEL_COUNT * Short.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
            repeat(frameCount) { frame ->
                val sample =
                    (AMPLITUDE * sin(2.0 * PI * FREQUENCY_HZ * frame / SAMPLE_RATE))
                        .toInt()
                        .toShort()
                repeat(CHANNEL_COUNT) { buffer.putShort(sample) }
                if (!buffer.hasRemaining()) {
                    output.write(buffer.array())
                    buffer.clear()
                }
            }
            if (buffer.position() > 0) output.write(buffer.array(), 0, buffer.position())
        }
    }

    private fun wavHeader(dataBytes: Int): ByteArray =
        ByteBuffer
            .allocate(44)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + dataBytes)
                put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(CHANNEL_COUNT.toShort())
                putInt(SAMPLE_RATE)
                putInt(SAMPLE_RATE * CHANNEL_COUNT * Short.SIZE_BYTES)
                putShort((CHANNEL_COUNT * Short.SIZE_BYTES).toShort())
                putShort(Short.SIZE_BITS.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataBytes)
            }.array()

    private companion object {
        const val ARG_SCOPE_ENABLED = "riffReplayGainScope"
        const val ARG_MODE = "riffRgMode"
        const val ARG_TARGET = "riffRgTarget"
        const val ARG_GAIN_Q8 = "riffRgGainQ8"
        const val ARG_HOLD_MILLIS = "riffRgHoldMillis"
        const val DEFAULT_GAIN_Q8 = -3_072
        const val DEFAULT_HOLD_MILLIS = 15_000L
        const val SAMPLE_RATE = 48_000
        const val CHANNEL_COUNT = 2
        const val DURATION_SECONDS = 30
        const val FREQUENCY_HZ = 1_000.0
        const val AMPLITUDE = 4_096.0
    }
}
