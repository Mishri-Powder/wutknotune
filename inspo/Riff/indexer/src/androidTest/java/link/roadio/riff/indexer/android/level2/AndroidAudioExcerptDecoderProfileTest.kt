package link.roadio.riff.indexer.android.level2

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class AndroidAudioExcerptDecoderProfileTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun threeWindowsRetainOnlyAiPcmInJavaHeap() {
        val source = File(context.cacheDir, "pcm-profile.wav")
        try {
            writeToneWav(source, durationSeconds = 70)
            val before = Debug.getRuntimeStats()
            val started = SystemClock.elapsedRealtime()
            val decoded =
                AndroidAudioExcerptDecoder(context).use { decoder ->
                    val first =
                        decoder.decode(
                            source = SourceMedia(source.absolutePath, source.name),
                            decoderHint = TEST_DECODER_HINT,
                            needsLoudness = true,
                            needsAi = true,
                            cancellation = Available,
                        )
                    repeat(10) {
                        val aiOnly =
                            decoder.decode(
                                source = SourceMedia(source.absolutePath, source.name),
                                decoderHint = TEST_DECODER_HINT,
                                needsLoudness = false,
                                needsAi = true,
                                cancellation = Available,
                            )
                        assertTrue(aiOnly.aiUsable)
                        assertTrue(aiOnly.integratedLufs == null)
                        assertTrue(aiOnly.pcmProfile.nativeAddCalls == 0)
                        assertTrue(aiOnly.pcm.any { it != 0f })
                        assertTone(aiOnly.pcm)
                    }
                    first
                }
            val elapsed = SystemClock.elapsedRealtime() - started
            val after = Debug.getRuntimeStats()
            val profile = decoded.pcmProfile
            Log.i(
                PROFILE_TAG,
                "decode_ms=$elapsed runtime_alloc_kb=${runtimeDelta(before, after, "art.gc.bytes-allocated") / 1024L} " +
                    "gc_count=${runtimeDelta(before, after, "art.gc.gc-count")} " +
                    "gc_ms=${runtimeDelta(before, after, "art.gc.gc-time")} " +
                    "source_kb=${profile.decodedPcmBytes / 1024L} " +
                    "java_alloc_kb=${profile.javaAllocatedBytes / 1024L} " +
                    "peak_reachable_kb=${profile.peakReachableBytes / 1024L} " +
                    "jni_calls=${profile.nativeAddCalls} " +
                    "rg_native_ms=${(profile.nativeAddNanos + profile.nativeFinishNanos) / 1_000_000L} " +
                    "ai_resample_ms=${profile.aiResampleNanos / 1_000_000L}",
            )

            assertTrue(decoded.aiUsable)
            assertTrue(decoded.integratedLufs?.isFinite() == true)
            assertTrue(profile.javaAllocatedBytes <= 4L * 1024L * 1024L)
            assertTrue(profile.peakReachableBytes <= 4L * 1024L * 1024L)
            assertTrue(profile.nativeAddCalls > 0)
            assertTone(decoded.pcm)
        } finally {
            source.delete()
        }
    }

    private fun assertTone(pcm: FloatArray) {
        // Approximate AU seeking can shift phase. Check the complete signal, not sample zero.
        repeat(Level2AudioFeatures.EXCERPT_COUNT) { excerpt ->
            var sine = 0.0
            var cosine = 0.0
            var energy = 0.0
            val count = Level2AudioFeatures.EXCERPT_SAMPLES
            repeat(count) { sample ->
                val value = pcm[excerpt * count + sample].toDouble()
                val angle = 2.0 * PI * 440.0 * sample / Level2AudioFeatures.SAMPLE_RATE
                sine += value * sin(angle)
                cosine += value * cos(angle)
                energy += value * value
            }
            val amplitude = 2.0 * sqrt(sine * sine + cosine * cosine) / count
            assertTrue("excerpt=$excerpt amplitude=$amplitude", abs(amplitude - 0.25) < 0.002)
            assertTrue("excerpt=$excerpt energy=$energy", abs(energy / count - 0.03125) < 0.001)
        }
    }

    private fun writeToneWav(
        target: File,
        durationSeconds: Int,
    ) {
        val sampleRate = 44_100
        val channels = 2
        val bytesPerSample = 2
        val frameCount = sampleRate * durationSeconds
        val dataBytes = frameCount * channels * bytesPerSample
        FileOutputStream(target).use { output ->
            output.write("RIFF".toByteArray())
            output.writeLeInt(36 + dataBytes)
            output.write("WAVEfmt ".toByteArray())
            output.writeLeInt(16)
            output.writeLeShort(1)
            output.writeLeShort(channels)
            output.writeLeInt(sampleRate)
            output.writeLeInt(sampleRate * channels * bytesPerSample)
            output.writeLeShort(channels * bytesPerSample)
            output.writeLeShort(bytesPerSample * 8)
            output.write("data".toByteArray())
            output.writeLeInt(dataBytes)
            val chunk = ByteBuffer.allocate(4096 * channels * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
            var frame = 0
            while (frame < frameCount) {
                chunk.clear()
                val frames = minOf(4096, frameCount - frame)
                repeat(frames) { offset ->
                    val value = (0.25 * sin(2.0 * PI * 440.0 * (frame + offset) / sampleRate) * Short.MAX_VALUE).roundToInt().toShort()
                    repeat(channels) { chunk.putShort(value) }
                }
                output.write(chunk.array(), 0, chunk.position())
                frame += frames
            }
        }
    }

    private fun FileOutputStream.writeLeInt(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
        write(value ushr 16 and 0xff)
        write(value ushr 24 and 0xff)
    }

    private fun FileOutputStream.writeLeShort(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }

    private fun runtimeDelta(
        before: Map<String, String>,
        after: Map<String, String>,
        key: String,
    ): Long = (after[key]?.toLongOrNull() ?: 0L) - (before[key]?.toLongOrNull() ?: 0L)

    private object Available : CancellationSignal {
        override val cancelled = false

        override fun ensureAvailable() = Unit
    }

    private companion object {
        const val PROFILE_TAG = "RiffPcmProfile"
        const val TEST_DECODER_HINT = 1L
    }
}
