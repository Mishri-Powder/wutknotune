package link.roadio.riff.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.settings.PlayerReplayGainTarget
import link.roadio.riff.player.library.PlayerReplayGainValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class ReplayGainAudioProcessorTest {
    @Test
    fun integerNeutralDelaysExactlyTransitionAndDrainsEveryFrame() {
        val samples = ShortArray(100) { (it * 100 - 5_000).toShort() }
        val fixture = fixture(ReplayGainOutputMode.INTEGER, gainQ8 = 0)

        fixture.processor.queueInput(pcm16(samples))
        val beforeEos = shorts(fixture.processor.output)
        assertEquals(25, beforeEos.size)
        fixture.processor.queueEndOfStream()
        val tail = shorts(fixture.processor.output)

        assertEquals(samples.toList(), (beforeEos + tail).toList())
        assertTrue(fixture.processor.isEnded)
    }

    @Test
    fun floatModeProducesNormalizedFloatSamples() {
        val fixture = fixture(ReplayGainOutputMode.FLOAT, gainQ8 = 0)
        fixture.processor.queueInput(pcm16(shortArrayOf(-16_000, -8_000, 0, 8_000, 16_000)))
        fixture.processor.queueEndOfStream()

        val output = floats(fixture.processor.output)
        assertEquals(5, output.size)
        assertEquals(-16_000 / 32_768f, output[0], 0.000_001f)
        assertEquals(-8_000 / 32_768f, output[1], 0.000_001f)
        assertEquals(0f, output[2], 0.000_001f)
        assertEquals(8_000 / 32_768f, output[3], 0.000_001f)
        assertEquals(16_000 / 32_768f, output[4], 0.000_001f)
    }

    @Test
    fun integerModeAppliesNegativeGainWithFixedPointArithmetic() {
        val fixture = fixture(ReplayGainOutputMode.INTEGER, gainQ8 = -1_541)
        fixture.processor.queueInput(pcm16(shortArrayOf(-20_000, -10_000, 10_000, 20_000)))
        fixture.processor.queueEndOfStream()

        val output = shorts(fixture.processor.output)
        assertEquals(4, output.size)
        output.zip(shortArrayOf(-10_000, -5_000, 5_000, 10_000).asIterable()).forEach { (actual, expected) ->
            assertTrue("actual=$actual expected=$expected", abs(actual - expected) <= 2)
        }
    }

    @Test
    fun limiterKeepsPositiveGainBelowConfiguredCeiling() {
        val fixture = fixture(ReplayGainOutputMode.INTEGER, gainQ8 = 1_541)
        val input = ShortArray(320) { if (it < 80) 1_000 else 30_000 }
        fixture.processor.queueInput(pcm16(input))
        val first = shorts(fixture.processor.output)
        fixture.processor.queueEndOfStream()
        val output = first + shorts(fixture.processor.output)

        assertEquals(input.size, output.size)
        assertTrue(output.maxOf { abs(it.toInt()) } <= 32_393)
        assertTrue(output.take(10).all { it > 1_000 })
        assertTrue(output.drop(100).any { it > 29_000 })
    }

    @Test
    fun streamRegistryAppliesCanonicalGainDirectlyAndDefaultsSafely() {
        val gains = ReplayGainStreamGains()
        gains.setTarget(PlayerReplayGainTarget.LUFS_18)
        gains.replace(mapOf(MEDIA_ID to PlayerReplayGainValue(trackGainQ8 = (-512).toShort())))
        gains.setQueue(listOf(MEDIA_ID))
        gains.register(OFFSET_US, MEDIA_ID)
        gains.register(OFFSET_US + 1L, null)

        gains.activate(OFFSET_US)
        assertEquals(-512, gains.active().gainQ8)
        gains.activate(OFFSET_US + 1L)
        assertEquals(0, gains.active().gainQ8)
        gains.activate(999L)
        assertEquals(0, gains.active().gainQ8)
    }

    @Test
    fun queueUsesAlbumGainOnlyWhenEveryOccurrenceHasSameValue() {
        val gains = ReplayGainStreamGains()
        gains.setTarget(PlayerReplayGainTarget.LUFS_18)
        gains.replace(
            mapOf(
                MediaId(1L) to PlayerReplayGainValue(100, albumGainQ8 = 300),
                MediaId(2L) to PlayerReplayGainValue(200, albumGainQ8 = 300),
            ),
        )
        gains.setQueue(listOf(1L, 1L, 2L).map(::MediaId))
        gains.register(1L, MediaId(1L))
        gains.register(2L, MediaId(2L))
        gains.activate(1L)
        assertEquals(300, gains.active().gainQ8)
        gains.activate(2L)
        assertEquals(300, gains.active().gainQ8)

        gains.setQueue(listOf(1L, 2L).map(::MediaId))
        gains.replace(
            mapOf(
                MediaId(1L) to PlayerReplayGainValue(100, albumGainQ8 = 300),
                MediaId(2L) to PlayerReplayGainValue(200, albumGainQ8 = 301),
            ),
        )
        gains.register(3L, MediaId(1L))
        gains.register(4L, MediaId(2L))
        gains.activate(3L)
        assertEquals(100, gains.active().gainQ8)
        gains.activate(4L)
        assertEquals(200, gains.active().gainQ8)
    }

    @Test
    fun missingAlbumFallsBackToTrackAndMissingTrackToNeutral() {
        val gains = ReplayGainStreamGains()
        gains.setTarget(PlayerReplayGainTarget.LUFS_18)
        gains.replace(mapOf(MediaId(1L) to PlayerReplayGainValue(100, albumGainQ8 = 300)))
        gains.setQueue(listOf(1L, 2L).map(::MediaId))
        gains.register(1L, MediaId(1L))
        gains.register(2L, MediaId(2L))

        gains.activate(1L)
        assertEquals(100, gains.active().gainQ8)
        gains.activate(2L)
        assertEquals(0, gains.active().gainQ8)
    }

    @Test
    fun processorUsesActivatedRendererOffsetWhenMedia3FlushMetadataIsDefault() {
        val gains = ReplayGainStreamGains()
        gains.setTarget(PlayerReplayGainTarget.LUFS_18)
        gains.replace(mapOf(MEDIA_ID to PlayerReplayGainValue(trackGainQ8 = (-512).toShort())))
        gains.setQueue(listOf(MEDIA_ID))
        gains.register(OFFSET_US, MEDIA_ID)
        gains.activate(OFFSET_US)
        val processor = ReplayGainAudioProcessor(ReplayGainOutputMode.INTEGER, gains)
        processor.configure(AudioFormat(SAMPLE_RATE, 1, C.ENCODING_PCM_16BIT))
        processor.flush(StreamMetadata.DEFAULT)

        processor.queueInput(pcm16(shortArrayOf(20_000)))
        processor.queueEndOfStream()

        assertEquals(15_887, shorts(processor.output).single().toInt())
    }

    @Test
    fun activeStreamBoundaryDrainsOldTailAndAppliesNewGainWithoutRamp() {
        val firstId = MediaId(1L)
        val secondId = MediaId(2L)
        val gains = ReplayGainStreamGains()
        gains.setTarget(PlayerReplayGainTarget.LUFS_18)
        gains.replace(
            mapOf(
                firstId to PlayerReplayGainValue(trackGainQ8 = (-1_541).toShort()),
                secondId to PlayerReplayGainValue(trackGainQ8 = 0),
            ),
        )
        gains.setQueue(listOf(firstId, secondId))
        gains.register(1L, firstId)
        gains.register(2L, secondId)
        gains.activate(1L)
        val processor = ReplayGainAudioProcessor(ReplayGainOutputMode.INTEGER, gains, transitionTimeUs = 1_000L)
        processor.configure(AudioFormat(SAMPLE_RATE, 1, C.ENCODING_PCM_16BIT))
        processor.flush(StreamMetadata.DEFAULT)

        processor.queueInput(pcm16(ShortArray(4) { 20_000 }))
        val firstOutput = shorts(processor.output)
        gains.activate(2L)
        processor.queueInput(pcm16(ShortArray(2) { 20_000 }))
        val boundaryOutput = shorts(processor.output)
        processor.queueEndOfStream()
        val tail = shorts(processor.output)

        assertEquals(3, firstOutput.size)
        assertTrue(firstOutput.all { abs(it.toInt() - 10_000) <= 2 })
        assertEquals(2, boundaryOutput.size)
        assertTrue(abs(boundaryOutput[0].toInt() - 10_000) <= 2)
        assertEquals(20_000, boundaryOutput[1].toInt())
        assertEquals(shortArrayOf(20_000).toList(), tail.toList())
    }

    @Test
    fun targetsAreDerivedFromCanonicalMinus18AndMissingGainStaysNeutral() {
        val storedZero = MediaId(1L)
        val missing = MediaId(2L)
        val gains = ReplayGainStreamGains()
        gains.replace(mapOf(storedZero to PlayerReplayGainValue(trackGainQ8 = 0)))
        gains.setQueue(listOf(storedZero, missing))
        gains.register(1L, storedZero)
        gains.register(2L, missing)

        gains.activate(1L)
        assertEquals(-5 * 256, gains.active().gainQ8)
        gains.setTarget(PlayerReplayGainTarget.LUFS_16)
        assertEquals(2 * 256, gains.active().gainQ8)
        gains.setTarget(PlayerReplayGainTarget.LUFS_18)
        assertEquals(0, gains.active().gainQ8)
        gains.setTarget(PlayerReplayGainTarget.ZERO)
        assertEquals(0, gains.active().gainQ8)

        gains.setTarget(PlayerReplayGainTarget.LUFS_16)
        gains.activate(2L)
        assertEquals(0, gains.active().gainQ8)
    }

    @Test
    fun targetOnlyChangeRampsAfterAlreadyAdmittedAudio() {
        val gains = ReplayGainStreamGains()
        gains.setTarget(PlayerReplayGainTarget.LUFS_18)
        gains.replace(mapOf(MEDIA_ID to PlayerReplayGainValue(trackGainQ8 = 0)))
        gains.setQueue(listOf(MEDIA_ID))
        gains.register(OFFSET_US, MEDIA_ID)
        gains.activate(OFFSET_US)
        val processor = ReplayGainAudioProcessor(ReplayGainOutputMode.INTEGER, gains, transitionTimeUs = 10_000L)
        processor.configure(AudioFormat(SAMPLE_RATE, 1, C.ENCODING_PCM_16BIT))
        processor.flush(StreamMetadata.DEFAULT)

        processor.queueInput(pcm16(ShortArray(15) { 1_000 }))
        assertEquals(List(5) { 1_000.toShort() }, shorts(processor.output).toList())

        gains.setTarget(PlayerReplayGainTarget.LUFS_16)
        processor.queueInput(pcm16(ShortArray(20) { 1_000 }))
        val duringRamp = shorts(processor.output)
        assertTrue(duringRamp.take(11).all { abs(it.toInt() - 1_000) <= 1 })
        assertTrue(duringRamp.last() in 1_220..1_240)

        processor.queueEndOfStream()
        val tail = shorts(processor.output)
        assertTrue(tail.first() in 1_255..1_265)
        assertTrue(tail.drop(1).all { it in 1_255..1_265 })
    }

    @Test
    fun zeroTargetStillRunsLimiter() {
        val gains = ReplayGainStreamGains()
        gains.setTarget(PlayerReplayGainTarget.ZERO)
        gains.replace(mapOf(MEDIA_ID to PlayerReplayGainValue(trackGainQ8 = 2_000)))
        gains.setQueue(listOf(MEDIA_ID))
        gains.register(OFFSET_US, MEDIA_ID)
        gains.activate(OFFSET_US)
        val processor = ReplayGainAudioProcessor(ReplayGainOutputMode.INTEGER, gains)
        processor.configure(AudioFormat(SAMPLE_RATE, 1, C.ENCODING_PCM_16BIT))
        processor.flush(StreamMetadata.DEFAULT)

        processor.queueInput(pcm16(ShortArray(320) { Short.MAX_VALUE }))
        val first = shorts(processor.output)
        processor.queueEndOfStream()
        val output = first + shorts(processor.output)

        assertEquals(320, output.size)
        assertTrue(output.maxOf { abs(it.toInt()) } <= 32_393)
    }

    private fun fixture(
        mode: ReplayGainOutputMode,
        gainQ8: Int,
    ): Fixture {
        val gains = ReplayGainStreamGains()
        gains.setTarget(PlayerReplayGainTarget.LUFS_18)
        gains.replace(mapOf(MEDIA_ID to PlayerReplayGainValue(gainQ8.toShort())))
        gains.setQueue(listOf(MEDIA_ID))
        gains.register(OFFSET_US, MEDIA_ID)
        gains.activate(OFFSET_US)
        val processor = ReplayGainAudioProcessor(mode, gains)
        processor.configure(AudioFormat(SAMPLE_RATE, 1, C.ENCODING_PCM_16BIT))
        processor.flush(StreamMetadata.DEFAULT)
        return Fixture(processor)
    }

    private fun pcm16(samples: ShortArray): ByteBuffer =
        ByteBuffer
            .allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                samples.forEach(::putShort)
                flip()
            }

    private fun shorts(buffer: ByteBuffer): ShortArray = ShortArray(buffer.remaining() / Short.SIZE_BYTES) { buffer.short }

    private fun floats(buffer: ByteBuffer): FloatArray = FloatArray(buffer.remaining() / Float.SIZE_BYTES) { buffer.float }

    private data class Fixture(
        val processor: ReplayGainAudioProcessor,
    )

    private companion object {
        const val SAMPLE_RATE = 1_000
        val MEDIA_ID = MediaId(42L)
        const val OFFSET_US = 123_000L
    }
}
