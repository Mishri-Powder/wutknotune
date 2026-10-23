package link.roadio.riff.indexer.android.replaygain

import android.media.AudioFormat
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.cuetree.Span
import link.roadio.riff.indexer.android.level2.AndroidAudioExcerptDecoder
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class AndroidReplayGainAnalyzerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun sharedDecoderAndFullFallbackRespectIndependentFragmentSpans() {
        val rate = 48_000
        val frames = rate * 24
        val bytes = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + frames * 2).put("WAVEfmt ".toByteArray())
        bytes
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(rate)
            .putInt(rate * 2)
            .putShort(2)
            .putShort(16)
        bytes.put("data".toByteArray()).putInt(frames * 2)
        repeat(frames) { frame ->
            val amplitude = if (frame < rate * 12) 0.1 else 0.5
            bytes.putShort((amplitude * sin(2.0 * PI * 440.0 * frame / rate) * Short.MAX_VALUE).roundToInt().toShort())
        }
        val fixture = File(context.cacheDir, "cuetree-span-test.wav")
        try {
            fixture.writeBytes(bytes.array())
            val source = SourceMedia(fixture.absolutePath, fixture.name)
            val low = Span(0, 12_000_000)
            val high = Span(12_000_000, 24_000_000)
            val analyzer = AndroidFullTrackLoudnessAnalyzer(context)
            val fullLow = analyzer.analyze(source, Available, low)
            val fullHigh = analyzer.analyze(source, Available, high)
            assertTrue("Full-span separation", fullHigh - fullLow in 13.5..14.5)
            AndroidAudioExcerptDecoder(context).use { decoder ->
                val windowLow = requireNotNull(decoder.decode(source, 7, true, false, Available, low).integratedLufs)
                val windowHigh = requireNotNull(decoder.decode(source, 7, true, false, Available, high).integratedLufs)
                assertTrue("Window separation", windowHigh - windowLow in 13.5..14.5)
                assertTrue("Low fragment", abs(windowLow - fullLow) < 0.2)
                assertTrue("High fragment", abs(windowHigh - fullHigh) < 0.2)
            }
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun completeDecodedSilentTrackReportsNoGain() {
        val source = File(context.cacheDir, "replay-gain-fixture")
        try {
            instrumentation.context.assets.open("chapters/nero.m4a").use { input ->
                source.outputStream().use(input::copyTo)
            }
            val error =
                runCatching {
                    AndroidFullTrackLoudnessAnalyzer(context).analyze(
                        SourceMedia(source.absolutePath, source.name),
                        Available,
                    )
                }.exceptionOrNull()

            assertTrue(error.toString(), error is IllegalArgumentException)
            assertTrue(error.toString(), error?.message?.contains("Integrated loudness is unavailable") == true)
        } finally {
            source.delete()
        }
    }

    @Test
    fun nativeAnalyzerMeasuresIntegratedToneLoudness() {
        val sampleRate = 48_000
        val frames = sampleRate * 3
        val stereo =
            FloatArray(frames * 2) { sample ->
                val frame = sample / 2
                (0.5 * sin(2.0 * PI * 1_000.0 * frame / sampleRate)).toFloat()
            }
        val result =
            ReplayGainNativeAnalyzer(2, sampleRate).use { analyzer ->
                analyzer.add(stereo)
                analyzer.finish()
            }

        assertTrue(result.isFinite() && result in -20.0..0.0)
    }

    @Test
    fun nativeAnalyzerCombinesThreeWindowStates() {
        val sampleRate = 48_000
        val mono =
            FloatArray(sampleRate * 3) { frame ->
                (0.25 * sin(2.0 * PI * 440.0 * frame / sampleRate)).toFloat()
            }
        val analyzers = List(3) { ReplayGainNativeAnalyzer(1, sampleRate) }
        try {
            analyzers.forEach { it.add(mono) }
            val individual = analyzers.first().finish()
            val combined = ReplayGainNativeAnalyzer.finishMultiple(analyzers)

            assertTrue(combined.isFinite())
            assertTrue(abs(combined - individual) < 0.01)
        } finally {
            analyzers.forEach(ReplayGainNativeAnalyzer::close)
        }
    }

    @Test
    fun directDecoderPcmEncodingsMatchFloatInput() {
        val sampleRate = 48_000
        val channels = 2
        val frames = sampleRate * 3
        val samples =
            FloatArray(frames * channels) { sample ->
                val frame = sample / channels
                (0.25 * sin(2.0 * PI * 440.0 * frame / sampleRate)).toFloat()
            }
        val expected =
            ReplayGainNativeAnalyzer(channels, sampleRate).use { analyzer ->
                analyzer.add(samples)
                analyzer.finish()
            }

        listOf(
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            AudioFormat.ENCODING_PCM_32BIT,
        ).forEach { encoding ->
            val buffer = directPcm(samples, encoding)
            val actual =
                ReplayGainNativeAnalyzer(channels, sampleRate).use { analyzer ->
                    analyzer.addPcm(buffer, 0, frames, encoding)
                    analyzer.finish()
                }
            assertTrue("encoding=$encoding expected=$expected actual=$actual", abs(actual - expected) < 0.01)
        }
    }

    @Test
    fun profileDirectPcmJniChunking() {
        val sampleRate = 44_100
        val channels = 2
        val frames = sampleRate * 20
        val bytesPerFrame = channels * Short.SIZE_BYTES
        val buffer = ByteBuffer.allocateDirect(frames * bytesPerFrame).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { frame ->
            val value = (0.25 * sin(2.0 * PI * 440.0 * frame / sampleRate) * Short.MAX_VALUE).roundToInt().toShort()
            repeat(channels) { buffer.putShort(value) }
        }
        buffer.flip()

        measureNativeAdd(buffer, frames, bytesPerFrame, frames)
        measureNativeAdd(buffer, frames, bytesPerFrame, MEDIA_CODEC_CHUNK_FRAMES)
        val oneCall = LongArray(PROFILE_ITERATIONS)
        val chunked = LongArray(PROFILE_ITERATIONS)
        repeat(PROFILE_ITERATIONS) { iteration ->
            if (iteration % 2 == 0) {
                oneCall[iteration] = measureNativeAdd(buffer, frames, bytesPerFrame, frames)
                chunked[iteration] = measureNativeAdd(buffer, frames, bytesPerFrame, MEDIA_CODEC_CHUNK_FRAMES)
            } else {
                chunked[iteration] = measureNativeAdd(buffer, frames, bytesPerFrame, MEDIA_CODEC_CHUNK_FRAMES)
                oneCall[iteration] = measureNativeAdd(buffer, frames, bytesPerFrame, frames)
            }
        }
        val oneCallMedian = oneCall.sorted()[PROFILE_ITERATIONS / 2]
        val chunkedMedian = chunked.sorted()[PROFILE_ITERATIONS / 2]
        Log.i(
            PROFILE_TAG,
            "pcm20s_one_call_ms=${oneCallMedian / NANOS_PER_MILLISECOND} " +
                "pcm20s_chunked_ms=${chunkedMedian / NANOS_PER_MILLISECOND} " +
                "jni_chunk_overhead_ms=${(chunkedMedian - oneCallMedian) / NANOS_PER_MILLISECOND} " +
                "chunk_calls=${(frames + MEDIA_CODEC_CHUNK_FRAMES - 1) / MEDIA_CODEC_CHUNK_FRAMES}",
        )

        assertTrue(oneCallMedian > 0L && chunkedMedian > 0L)
    }

    private fun measureNativeAdd(
        buffer: ByteBuffer,
        frames: Int,
        bytesPerFrame: Int,
        chunkFrames: Int,
    ): Long =
        ReplayGainNativeAnalyzer(2, 44_100).use { analyzer ->
            val started = SystemClock.elapsedRealtimeNanos()
            var frame = 0
            while (frame < frames) {
                val count = minOf(chunkFrames, frames - frame)
                analyzer.addPcm(buffer, frame * bytesPerFrame, count, AudioFormat.ENCODING_PCM_16BIT)
                frame += count
            }
            SystemClock.elapsedRealtimeNanos() - started
        }

    private fun directPcm(
        samples: FloatArray,
        encoding: Int,
    ): ByteBuffer {
        val bytesPerSample = ReplayGainNativeAnalyzer.pcmBytesPerSample(encoding)
        return ByteBuffer.allocateDirect(samples.size * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN).also { buffer ->
            samples.forEach { value ->
                when (encoding) {
                    AudioFormat.ENCODING_PCM_FLOAT -> {
                        buffer.putFloat(value)
                    }

                    AudioFormat.ENCODING_PCM_16BIT -> {
                        buffer.putShort((value * Short.MAX_VALUE).roundToInt().toShort())
                    }

                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                        val sample = (value * 8_388_607).roundToInt()
                        buffer.put((sample and 0xff).toByte())
                        buffer.put((sample shr 8 and 0xff).toByte())
                        buffer.put((sample shr 16 and 0xff).toByte())
                    }

                    AudioFormat.ENCODING_PCM_32BIT -> {
                        buffer.putInt((value * Int.MAX_VALUE).roundToInt())
                    }
                }
            }
            buffer.flip()
        }
    }

    private object Available : CancellationSignal {
        override val cancelled = false

        override fun ensureAvailable() = Unit
    }

    private companion object {
        const val PROFILE_TAG = "RiffPcmProfile"
        const val PROFILE_ITERATIONS = 5
        const val MEDIA_CODEC_CHUNK_FRAMES = 1_024
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
