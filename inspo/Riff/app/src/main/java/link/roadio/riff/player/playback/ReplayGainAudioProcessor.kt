@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal enum class ReplayGainOutputMode {
    INTEGER,
    FLOAT,
}

/** One PCM16-input ReplayGain processor for both active output modes. */
internal class ReplayGainAudioProcessor(
    private val outputMode: ReplayGainOutputMode,
    private val streamGains: ReplayGainStreamGains,
    private val transitionTimeUs: Long = DEFAULT_TRANSITION_TIME_US,
) : BaseAudioProcessor() {
    private var lookAheadFrames = 0
    private var channelCount = 0
    private var ringCapacityFrames = 0
    private var ringSamples = ShortArray(0)
    private var ringReadFrame = 0
    private var ringFrameCount = 0
    private var inputFrameIndex = 0L
    private var outputFrameIndex = 0L
    private var streamRevision = Long.MIN_VALUE
    private var targetRevision = Long.MIN_VALUE
    private var configuredGainQ8 = 0
    private var baseInitialGainQ8 = 0
    private var detectorBlockStart = 0L
    private var detectorBlockPeak = 0
    private var detectorBlockEvent: LimiterEvent? = null
    private var previousDetectorBlockClipped = false
    private val targetTransitions = ArrayDeque<TargetTransition>()
    private val limiterEvents = ArrayDeque<LimiterEvent>()
    private var cachedGainQ8 = Int.MIN_VALUE
    private var cachedFloatGain = 1f
    private var cachedIntegerGainQ24 = INTEGER_SCALE

    init {
        require(transitionTimeUs > 0L)
        GainTables.preload()
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException("ReplayGain expects PCM16 input", inputAudioFormat)
        }
        return when (outputMode) {
            ReplayGainOutputMode.INTEGER -> {
                inputAudioFormat
            }

            ReplayGainOutputMode.FLOAT -> {
                AudioFormat(
                    inputAudioFormat.sampleRate,
                    inputAudioFormat.channelCount,
                    C.ENCODING_PCM_FLOAT,
                )
            }
        }
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        channelCount = inputAudioFormat.channelCount
        lookAheadFrames =
            ceil(inputAudioFormat.sampleRate.toDouble() * transitionTimeUs / MICROS_PER_SECOND)
                .toInt()
                .coerceAtLeast(1)
        ringCapacityFrames = lookAheadFrames + 1
        ringSamples = ShortArray(Math.multiplyExact(ringCapacityFrames, channelCount))
        applyActiveStream(streamGains.active())
        resetStreamState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerInputFrame = Math.multiplyExact(channelCount, Short.SIZE_BYTES)
        require(inputBuffer.remaining() % bytesPerInputFrame == 0) { "Queued an incomplete PCM16 frame" }
        val inputFrames = inputBuffer.remaining() / bytesPerInputFrame
        val activeStream = streamGains.active()
        val streamChanged = activeStream.revision != streamRevision
        val targetChanged = activeStream.targetRevision != targetRevision
        val delayedFrames = if (streamChanged) ringFrameCount else 0
        val retainedFrames = if (streamChanged) 0 else ringFrameCount
        val outputFrames = delayedFrames + (retainedFrames + inputFrames - lookAheadFrames).coerceAtLeast(0)
        val bytesPerOutputSample = if (outputMode == ReplayGainOutputMode.INTEGER) Short.SIZE_BYTES else Float.SIZE_BYTES
        val output = replaceOutputBuffer(Math.multiplyExact(Math.multiplyExact(outputFrames, channelCount), bytesPerOutputSample))

        if (streamChanged) {
            while (ringFrameCount > 0) emitFrame(output)
            applyActiveStream(activeStream)
            resetStreamState()
        } else if (targetChanged) {
            scheduleTargetTransition(activeStream)
        }
        repeat(inputFrames) {
            ingestFrame(inputBuffer)
            if (ringFrameCount > lookAheadFrames) emitFrame(output)
        }
        output.flip()
    }

    override fun onQueueEndOfStream() {
        if (ringFrameCount == 0) return
        val bytesPerOutputSample = if (outputMode == ReplayGainOutputMode.INTEGER) Short.SIZE_BYTES else Float.SIZE_BYTES
        val output =
            replaceOutputBuffer(
                Math.multiplyExact(Math.multiplyExact(ringFrameCount, channelCount), bytesPerOutputSample),
            )
        while (ringFrameCount > 0) emitFrame(output)
        output.flip()
    }

    override fun onReset() {
        lookAheadFrames = 0
        channelCount = 0
        ringCapacityFrames = 0
        ringSamples = ShortArray(0)
        streamRevision = Long.MIN_VALUE
        targetRevision = Long.MIN_VALUE
        configuredGainQ8 = 0
        baseInitialGainQ8 = 0
        resetStreamState()
    }

    private fun applyActiveStream(stream: ReplayGainActiveStream) {
        streamRevision = stream.revision
        targetRevision = stream.targetRevision
        configuredGainQ8 = stream.gainQ8
        baseInitialGainQ8 = stream.gainQ8
    }

    private fun scheduleTargetTransition(stream: ReplayGainActiveStream) {
        val startFrame = inputFrameIndex
        val fromGainQ8 = baseGainQ8(startFrame)
        targetRevision = stream.targetRevision
        configuredGainQ8 = stream.gainQ8
        targetTransitions.addLast(
            TargetTransition(
                startFrame = startFrame,
                endFrame = startFrame + lookAheadFrames,
                fromGainQ8 = fromGainQ8,
                toGainQ8 = stream.gainQ8,
            ),
        )
    }

    private fun resetStreamState() {
        ringReadFrame = 0
        ringFrameCount = 0
        inputFrameIndex = 0L
        outputFrameIndex = 0L
        detectorBlockStart = 0L
        detectorBlockPeak = 0
        detectorBlockEvent = null
        previousDetectorBlockClipped = false
        targetTransitions.clear()
        baseInitialGainQ8 = configuredGainQ8
        limiterEvents.clear()
        cachedGainQ8 = Int.MIN_VALUE
    }

    private fun ingestFrame(input: ByteBuffer) {
        val writeFrame = (ringReadFrame + ringFrameCount) % ringCapacityFrames
        val writeSample = writeFrame * channelCount
        var peak = 0
        repeat(channelCount) { channel ->
            val sample = input.short
            ringSamples[writeSample + channel] = sample
            peak = maxOf(peak, if (sample == Short.MIN_VALUE) 32_768 else kotlin.math.abs(sample.toInt()))
        }
        updateDetector(inputFrameIndex, peak)
        inputFrameIndex++
        ringFrameCount++
    }

    private fun updateDetector(
        frameIndex: Long,
        framePeak: Int,
    ) {
        val blockStart = frameIndex - frameIndex % DETECTOR_BLOCK_FRAMES
        if (blockStart != detectorBlockStart) {
            previousDetectorBlockClipped = detectorBlockEvent != null
            detectorBlockStart = blockStart
            detectorBlockPeak = 0
            detectorBlockEvent = null
        }
        detectorBlockPeak = maxOf(detectorBlockPeak, framePeak)
        val baseGainQ8 = baseGainQ8(frameIndex)
        val requiredGainQ8 = minOf(baseGainQ8, GainTables.safeGainQ8(detectorBlockPeak))
        if (requiredGainQ8 < baseGainQ8) {
            val event =
                detectorBlockEvent
                    ?: limiterEvents
                        .lastOrNull()
                        ?.takeIf { previousDetectorBlockClipped && it.endFrame == blockStart - 1L }
                    ?: LimiterEvent(blockStart, frameIndex, requiredGainQ8).also(limiterEvents::addLast)
            event.endFrame = frameIndex
            event.requiredGainQ8 = minOf(event.requiredGainQ8, requiredGainQ8)
            detectorBlockEvent = event
        } else {
            detectorBlockEvent?.endFrame = frameIndex
        }
    }

    private fun emitFrame(output: ByteBuffer) {
        pruneTargetTransitions(outputFrameIndex)
        while (limiterEvents.isNotEmpty() && limiterEvents.first().endFrame + lookAheadFrames < outputFrameIndex) {
            limiterEvents.removeFirst()
        }
        updateCachedGain(envelopeGainQ8(outputFrameIndex))
        val readSample = ringReadFrame * channelCount
        when (outputMode) {
            ReplayGainOutputMode.INTEGER -> {
                repeat(channelCount) { channel ->
                    val scaled = ringSamples[readSample + channel].toLong() * cachedIntegerGainQ24
                    val rounded =
                        if (scaled >= 0L) {
                            (scaled + INTEGER_HALF) / INTEGER_SCALE
                        } else {
                            (scaled - INTEGER_HALF) / INTEGER_SCALE
                        }
                    output.putShort(rounded.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort())
                }
            }

            ReplayGainOutputMode.FLOAT -> {
                repeat(channelCount) { channel ->
                    val value = ringSamples[readSample + channel] / 32_768f * cachedFloatGain
                    output.putFloat(value.coerceIn(-1f, 1f))
                }
            }
        }
        ringReadFrame = (ringReadFrame + 1) % ringCapacityFrames
        ringFrameCount--
        outputFrameIndex++
    }

    private fun envelopeGainQ8(frameIndex: Long): Int {
        val baseGainQ8 = baseGainQ8(frameIndex)
        var gainQ8 = baseGainQ8
        limiterEvents.forEach { event ->
            val constrained =
                when {
                    frameIndex < event.startFrame -> {
                        val distance = event.startFrame - frameIndex
                        if (distance > lookAheadFrames) {
                            baseGainQ8
                        } else {
                            interpolateQ8(baseGainQ8, event.requiredGainQ8, lookAheadFrames - distance)
                        }
                    }

                    frameIndex <= event.endFrame -> {
                        event.requiredGainQ8
                    }

                    else -> {
                        val distance = frameIndex - event.endFrame
                        if (distance > lookAheadFrames) {
                            baseGainQ8
                        } else {
                            interpolateQ8(event.requiredGainQ8, baseGainQ8, distance)
                        }
                    }
                }
            gainQ8 = minOf(gainQ8, constrained)
        }
        return gainQ8
    }

    private fun baseGainQ8(frameIndex: Long): Int {
        var active: TargetTransition? = null
        targetTransitions.forEach { transition ->
            if (transition.startFrame <= frameIndex) active = transition
        }
        val transition = active ?: return baseInitialGainQ8
        return if (frameIndex >= transition.endFrame) {
            transition.toGainQ8
        } else {
            interpolateQ8(
                transition.fromGainQ8,
                transition.toGainQ8,
                frameIndex - transition.startFrame,
            )
        }
    }

    private fun pruneTargetTransitions(frameIndex: Long) {
        while (targetTransitions.size > 1 && targetTransitions.elementAt(1).startFrame <= frameIndex) {
            baseInitialGainQ8 = targetTransitions.removeFirst().toGainQ8
        }
        if (targetTransitions.firstOrNull()?.endFrame?.let { it < frameIndex } == true) {
            baseInitialGainQ8 = targetTransitions.removeFirst().toGainQ8
        }
    }

    private fun interpolateQ8(
        from: Int,
        to: Int,
        elapsedFrames: Long,
    ): Int {
        val numerator = (to.toLong() - from) * elapsedFrames
        val rounded =
            if (numerator >= 0L) {
                (numerator + lookAheadFrames / 2L) / lookAheadFrames
            } else {
                (numerator - lookAheadFrames / 2L) / lookAheadFrames
            }
        return Math.addExact(from, rounded.toInt())
    }

    private fun updateCachedGain(gainQ8: Int) {
        if (gainQ8 == cachedGainQ8) return
        cachedGainQ8 = gainQ8
        cachedFloatGain = GainTables.factor(gainQ8)
        cachedIntegerGainQ24 =
            (cachedFloatGain.toDouble() * INTEGER_SCALE)
                .roundToLongSafe()
                .coerceIn(0L, MAX_INTEGER_GAIN_Q24)
    }

    private data class LimiterEvent(
        var startFrame: Long,
        var endFrame: Long,
        var requiredGainQ8: Int,
    )

    private data class TargetTransition(
        val startFrame: Long,
        val endFrame: Long,
        val fromGainQ8: Int,
        val toGainQ8: Int,
    )

    private object GainTables {
        private val factors =
            FloatArray(MAX_GAIN_Q8 - MIN_GAIN_Q8 + 1).also { table ->
                var factor = 10.0.pow(MIN_GAIN_Q8 / DB_FACTOR_DENOMINATOR)
                val step = 10.0.pow(1.0 / DB_FACTOR_DENOMINATOR)
                table.indices.forEach { index ->
                    table[index] = factor.toFloat()
                    factor *= step
                }
            }
        private val safeGainByPeak =
            IntArray(32_769) { peak ->
                if (peak == 0) {
                    MAX_GAIN_Q8
                } else {
                    floor((CEILING_DB + 20.0 * log10(32_768.0 / peak)) * Q8_SCALE).toInt()
                }
            }

        fun preload() {
            check(factors.isNotEmpty() && safeGainByPeak.isNotEmpty())
        }

        fun factor(gainQ8: Int): Float = factors[gainQ8.coerceIn(MIN_GAIN_Q8, MAX_GAIN_Q8) - MIN_GAIN_Q8]

        fun safeGainQ8(peak: Int): Int = safeGainByPeak[peak.coerceIn(0, 32_768)]
    }

    private companion object {
        const val DEFAULT_TRANSITION_TIME_US = 75_000L
        const val MICROS_PER_SECOND = 1_000_000.0
        const val DETECTOR_BLOCK_FRAMES = 32L
        const val Q8_SCALE = 256.0
        const val CEILING_DB = -0.1
        const val DB_FACTOR_DENOMINATOR = 20.0 * Q8_SCALE
        const val MIN_GAIN_Q8 = Short.MIN_VALUE.toInt() - 8 * 256
        const val MAX_GAIN_Q8 = Short.MAX_VALUE.toInt() + 18 * 256
        const val INTEGER_SCALE = 1L shl 24
        const val INTEGER_HALF = INTEGER_SCALE / 2L
        const val MAX_INTEGER_GAIN_Q24 = 32_768L * INTEGER_SCALE

        fun Double.roundToLongSafe(): Long =
            when {
                isNaN() || this <= 0.0 -> 0L
                this >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
                else -> kotlin.math.round(this).toLong()
            }
    }
}
