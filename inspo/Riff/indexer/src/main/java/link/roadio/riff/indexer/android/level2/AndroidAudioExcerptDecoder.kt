package link.roadio.riff.indexer.android.level2

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import link.roadio.cuetree.Span
import link.roadio.riff.indexer.android.replaygain.ReplayGainNativeAnalyzer
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.indexer.module.BuildConfig
import java.io.Closeable
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal data class DecodedLevel2Audio(
    val pcm: FloatArray,
    val durationUs: Long,
    val decodeMillis: Long,
    val integratedLufs: Double? = null,
    val aiUsable: Boolean = true,
    val pcmProfile: PcmBufferProfile = PcmBufferProfile(),
)

internal data class PcmBufferProfile(
    val decodedPcmBytes: Long = 0L,
    val javaAllocatedBytes: Long = 0L,
    val peakReachableBytes: Long = 0L,
    val nativeAddCalls: Int = 0,
    val nativeAddNanos: Long = 0L,
    val nativeFinishNanos: Long = 0L,
    val aiResampleNanos: Long = 0L,
)

internal const val LEVEL2_AI_EXCERPT_US = 10L * 1_000_000L
internal const val LEVEL2_LOUDNESS_WINDOW_US = 20L * 1_000_000L

internal fun level2RequestedWindowUs(needsLoudness: Boolean): Long = if (needsLoudness) LEVEL2_LOUDNESS_WINDOW_US else LEVEL2_AI_EXCERPT_US

private fun decoderPcmBytesPerSample(encoding: Int): Int =
    when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_16BIT -> 2
        else -> throw IllegalArgumentException("Unsupported decoder PCM encoding: $encoding")
    }

internal fun level2BaseWindowStarts(durationUs: Long): List<Long> {
    require(durationUs > 0L)
    val latestStart = max(0L, durationUs - LEVEL2_AI_EXCERPT_US)
    return listOf(0.25, 0.50, 0.75).map { position ->
        ((durationUs * position).toLong() - LEVEL2_AI_EXCERPT_US / 2L).coerceIn(0L, latestStart)
    }
}

internal fun level2RetryWindowStarts(
    initialStartUs: Long,
    latestStartUs: Long,
): List<Long> {
    require(initialStartUs >= 0L && latestStartUs >= 0L)
    return (0 until 3)
        .map { attempt -> min(initialStartUs + attempt * LEVEL2_LOUDNESS_WINDOW_US, latestStartUs) }
        .distinct()
}

/**
 * Decodes the three shared L2 windows. Loudness consumes up to 20 seconds at the source format;
 * AI consumes the first 10 seconds of each accepted window after mono 32 kHz conversion.
 */
internal class AndroidAudioExcerptDecoder(
    private val context: Context,
    windowWorkerCount: Int = MAX_WINDOW_WORKERS,
) : Closeable {
    private val workerCount = windowWorkerCount.also { require(it in 1..MAX_WINDOW_WORKERS) }
    private val windowExecutor =
        Executors.newFixedThreadPool(
            workerCount,
        )
    private val codecSessions = arrayOfNulls<CodecCallbackSession>(MAX_WINDOW_WORKERS)
    private val pcmFormats = arrayOfNulls<DecoderPcmFormat>(MAX_WINDOW_WORKERS)
    private val autotuner = AndroidDecoderAutotuner(context)
    private var activeHint: Long? = null
    private var activePlan: AndroidDecoderPlan? = null

    fun decode(
        source: SourceMedia,
        decoderHint: Long,
        needsLoudness: Boolean,
        needsAi: Boolean,
        cancellation: CancellationSignal,
        span: Span? = null,
    ): DecodedLevel2Audio {
        require(needsLoudness || needsAi)
        val plan = autotuner.select(source, decoderHint, cancellation)
        activate(decoderHint, plan)
        val started = SystemClock.elapsedRealtime()
        val containerDurationUs = probeDuration(source)
        val beginUs = span?.beginUs ?: 0L
        val endUs = min(span?.endUs ?: containerDurationUs, containerDurationUs)
        val durationUs = endUs - beginUs
        require(durationUs > 0L) { "Audio duration is unavailable" }
        val latestStart = max(beginUs, endUs - LEVEL2_AI_EXCERPT_US)
        val starts = level2BaseWindowStarts(durationUs).map { it + beginUs }
        val pcm = if (needsAi) FloatArray(Level2AudioFeatures.PCM_FLOAT_COUNT) else FloatArray(0)
        val futures =
            starts.mapIndexed { index, startUs ->
                windowExecutor.submit<DecodedWindow> {
                    decodeLogicalWindow(
                        workerIndex = index,
                        plan = plan,
                        source = source,
                        initialStartUs = startUs,
                        latestStartUs = latestStart,
                        limitEndUs = endUs,
                        needsLoudness = needsLoudness,
                        aiDestination = pcm.takeIf { needsAi },
                        aiDestinationOffset = if (needsAi) index * Level2AudioFeatures.EXCERPT_SAMPLES else 0,
                        cancellation = cancellation,
                    )
                }
            }
        val windows = mutableListOf<DecodedWindow>()
        var firstFailure: Throwable? = null
        futures.forEach { future ->
            try {
                windows += future.get()
            } catch (error: ExecutionException) {
                if (firstFailure == null) firstFailure = error.cause ?: error
            } catch (error: Throwable) {
                if (firstFailure == null) firstFailure = error
            }
        }
        if (firstFailure != null) {
            windows.forEach(DecodedWindow::close)
            if (plan.batchesInput) {
                autotuner.disableBatching(decoderHint, plan, requireNotNull(firstFailure))
                releaseCodecs()
                return decode(source, decoderHint, needsLoudness, needsAi, cancellation, span)
            }
            throw requireNotNull(firstFailure)
        }
        try {
            val accepted = windows.filter { it.integratedLufs != null }
            val loudnessComplete = !needsLoudness || accepted.size == starts.size
            val aiComplete = !needsAi || windows.all(DecodedWindow::aiUsable)
            if (needsAi && !aiComplete) pcm.fill(0f)
            var nativeMergeNanos = 0L
            val integrated =
                if (needsLoudness && loudnessComplete) {
                    val mergeStarted = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
                    ReplayGainNativeAnalyzer.finishMultiple(accepted.map { requireNotNull(it.analyzer) }).also {
                        if (BuildConfig.DEBUG) {
                            nativeMergeNanos = SystemClock.elapsedRealtimeNanos() - mergeStarted
                        }
                    }
                } else {
                    null
                }
            val finalPcmBytes = pcm.size.toLong() * Float.SIZE_BYTES
            val windowProfiles = windows.map(DecodedWindow::profile)
            val pcmProfile =
                PcmBufferProfile(
                    decodedPcmBytes = windowProfiles.sumOf(PcmBufferProfile::decodedPcmBytes),
                    javaAllocatedBytes = windowProfiles.sumOf(PcmBufferProfile::javaAllocatedBytes) + finalPcmBytes,
                    peakReachableBytes = finalPcmBytes + windowProfiles.sumOf(PcmBufferProfile::peakReachableBytes),
                    nativeAddCalls = windowProfiles.sumOf(PcmBufferProfile::nativeAddCalls),
                    nativeAddNanos = windowProfiles.sumOf(PcmBufferProfile::nativeAddNanos),
                    nativeFinishNanos =
                        windowProfiles.sumOf(PcmBufferProfile::nativeFinishNanos) + nativeMergeNanos,
                    aiResampleNanos = windowProfiles.sumOf(PcmBufferProfile::aiResampleNanos),
                )
            return DecodedLevel2Audio(
                pcm = pcm,
                integratedLufs = integrated,
                durationUs = durationUs,
                decodeMillis = SystemClock.elapsedRealtime() - started,
                aiUsable = aiComplete,
                pcmProfile = pcmProfile,
            )
        } finally {
            windows.forEach(DecodedWindow::close)
        }
    }

    override fun close() {
        releaseCodecs()
        windowExecutor.shutdownNow()
        windowExecutor.awaitTermination(WINDOW_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun activate(
        decoderHint: Long,
        plan: AndroidDecoderPlan,
    ) {
        if (activeHint == decoderHint && activePlan == plan) return
        releaseCodecs()
        activeHint = decoderHint
        activePlan = plan
    }

    private fun releaseCodecs() {
        codecSessions.indices.forEach { index ->
            codecSessions[index]?.close()
            codecSessions[index] = null
            pcmFormats[index] = null
        }
        activeHint = null
        activePlan = null
    }

    private fun decodeLogicalWindow(
        workerIndex: Int,
        plan: AndroidDecoderPlan,
        source: SourceMedia,
        initialStartUs: Long,
        latestStartUs: Long,
        limitEndUs: Long,
        needsLoudness: Boolean,
        aiDestination: FloatArray?,
        aiDestinationOffset: Int,
        cancellation: CancellationSignal,
    ): DecodedWindow {
        val starts =
            if (needsLoudness) {
                level2RetryWindowStarts(initialStartUs, latestStartUs)
            } else {
                listOf(initialStartUs)
            }
        var last: DecodedWindow? = null
        starts.forEach { startUs ->
            last?.close()
            val decoded =
                decodeWindow(
                    workerIndex = workerIndex,
                    plan = plan,
                    source = source,
                    startUs = startUs,
                    limitEndUs = limitEndUs,
                    needsLoudness = needsLoudness,
                    aiDestination = aiDestination,
                    aiDestinationOffset = aiDestinationOffset,
                    cancellation = cancellation,
                )
            last = decoded
            if (!needsLoudness || decoded.integratedLufs != null) return decoded
        }
        return requireNotNull(last)
    }

    private fun probeDuration(source: SourceMedia): Long {
        val extractor = openExtractor(source)
        try {
            val format = extractor.getTrackFormat(extractor.audioTrack())
            return format.getLongOrNull(MediaFormat.KEY_DURATION)?.takeIf { it > 0L }
                ?: throw IllegalArgumentException("Audio duration is missing")
        } finally {
            extractor.release()
        }
    }

    private fun decodeWindow(
        workerIndex: Int,
        plan: AndroidDecoderPlan,
        source: SourceMedia,
        startUs: Long,
        limitEndUs: Long,
        needsLoudness: Boolean,
        aiDestination: FloatArray?,
        aiDestinationOffset: Int,
        cancellation: CancellationSignal,
    ): DecodedWindow {
        val extractor = openExtractor(source)
        val track = extractor.audioTrack()
        val inputFormat = extractor.getTrackFormat(track)
        extractor.selectTrack(track)
        extractor.seekTo(max(0L, startUs - PRE_ROLL_US), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val configuredFormat = configuredDecoderFormat(inputFormat, plan)
        val existing = codecSessions[workerIndex]
        val session = existing ?: CodecCallbackSession(plan.codecName, configuredFormat)
        try {
            if (existing == null) {
                codecSessions[workerIndex] = session
            } else {
                session.flush()
            }
            return drainWindow(
                extractor = extractor,
                session = session,
                plan = plan,
                workerIndex = workerIndex,
                startUs = startUs,
                limitEndUs = limitEndUs,
                firstAccessUnitUs = extractor.sampleTime,
                needsLoudness = needsLoudness,
                aiDestination = aiDestination,
                aiDestinationOffset = aiDestinationOffset,
                cancellation = cancellation,
            )
        } catch (error: Throwable) {
            if (codecSessions[workerIndex] === session) codecSessions[workerIndex] = null
            pcmFormats[workerIndex] = null
            session.close()
            throw error
        } finally {
            extractor.release()
        }
    }

    private fun drainWindow(
        extractor: MediaExtractor,
        session: CodecCallbackSession,
        plan: AndroidDecoderPlan,
        workerIndex: Int,
        startUs: Long,
        limitEndUs: Long,
        firstAccessUnitUs: Long,
        needsLoudness: Boolean,
        aiDestination: FloatArray?,
        aiDestinationOffset: Int,
        cancellation: CancellationSignal,
    ): DecodedWindow {
        val requestedDurationUs = min(level2RequestedWindowUs(needsLoudness), limitEndUs - startUs)
        require(requestedDurationUs > 0L)
        require(firstAccessUnitUs >= 0L) { "No access unit at the requested window" }
        val endUs = startUs + requestedDurationUs
        val codec = session.codec
        var sourceRate = 0
        var sourceChannels = 0
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var analyzer: ReplayGainNativeAnalyzer? = null
        var aiResampler: StreamingMonoResampler? = null
        var firstPresentationUs: Long? = null
        var requestedStartFrame: Long? = null
        var decodedFrames = 0L
        var selectedFrames = 0L
        var decodedPcmBytes = 0L
        var nativeAddCalls = 0
        var nativeAddNanos = 0L
        var aiResampleNanos = 0L
        var inputEnded = false
        var outputEnded = false

        fun applyPcmFormat(format: DecoderPcmFormat) {
            sourceRate = format.sampleRate
            sourceChannels = format.channelCount
            pcmEncoding = format.encoding
            analyzer?.close()
            analyzer = if (needsLoudness) ReplayGainNativeAnalyzer(sourceChannels, sourceRate) else null
            aiResampler =
                aiDestination?.let { destination ->
                    StreamingMonoResampler(
                        sourceRate = sourceRate,
                        channels = sourceChannels,
                        destination = destination,
                        destinationOffset = aiDestinationOffset,
                    )
                }
        }

        fun applyOutputFormat(format: MediaFormat) {
            require(selectedFrames == 0L) { "Decoder PCM format changed during a window" }
            val pcmFormat =
                DecoderPcmFormat(
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    encoding = format.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT,
                )
            pcmFormats[workerIndex] = pcmFormat
            applyPcmFormat(pcmFormat)
        }

        fun consumeOutput(
            outputIndex: Int,
            infos: List<MediaCodec.BufferInfo>,
        ): Boolean {
            val buffer = requireNotNull(codec.getOutputBuffer(outputIndex))
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            try {
                infos.forEach { info ->
                    if (info.size > 0) {
                        require(sourceRate > 0 && sourceChannels > 0)
                        val bytesPerSample = decoderPcmBytesPerSample(pcmEncoding)
                        val bytesPerFrame = bytesPerSample * sourceChannels
                        require(info.size % bytesPerFrame == 0) { "Decoder PCM buffer is not frame-aligned" }
                        val bufferFrames = info.size / bytesPerFrame
                        if (firstPresentationUs == null) {
                            firstPresentationUs = info.presentationTimeUs
                            // Some stock codecs misreport output PTS across parallel sessions.
                            // Our approximate windows follow the source AU timeline instead.
                            requestedStartFrame =
                                floor(
                                    (startUs - firstAccessUnitUs).toDouble() * sourceRate / MICROS_PER_SECOND,
                                ).toLong().coerceAtLeast(0L)
                        }
                        val requestedStart = requireNotNull(requestedStartFrame)
                        val requestedEnd = requestedStart + requestedDurationUs * sourceRate / MICROS_PER_SECOND
                        val bufferStart = decodedFrames
                        val bufferEnd = bufferStart + bufferFrames
                        val selectedStart = maxOf(bufferStart, requestedStart)
                        val selectedEnd = minOf(bufferEnd, requestedEnd)
                        if (selectedStart < selectedEnd) {
                            val localStartFrame = (selectedStart - bufferStart).toInt()
                            val frameCount = (selectedEnd - selectedStart).toInt()
                            val byteOffset = info.offset + localStartFrame * bytesPerFrame
                            val nativeAddStarted = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
                            analyzer?.let { currentAnalyzer ->
                                currentAnalyzer.addPcm(buffer, byteOffset, frameCount, pcmEncoding)
                                if (BuildConfig.DEBUG) {
                                    nativeAddNanos += SystemClock.elapsedRealtimeNanos() - nativeAddStarted
                                }
                                nativeAddCalls += 1
                            }
                            selectedFrames += frameCount
                            decodedPcmBytes += frameCount.toLong() * bytesPerFrame

                            val aiEnd = minOf(selectedEnd, requestedStart + AI_WINDOW_SECONDS * sourceRate)
                            val aiFrameCount = (aiEnd - selectedStart).coerceAtLeast(0L).toInt()
                            if (aiFrameCount > 0) {
                                val aiResampleStarted = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
                                aiResampler?.addPcm(
                                    buffer = buffer,
                                    byteOffset = byteOffset,
                                    frames = aiFrameCount,
                                    encoding = pcmEncoding,
                                )
                                if (BuildConfig.DEBUG) {
                                    aiResampleNanos += SystemClock.elapsedRealtimeNanos() - aiResampleStarted
                                }
                            }
                        }
                        decodedFrames += bufferFrames
                    }
                }
                return infos.any { it.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 }
            } finally {
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }
        try {
            pcmFormats[workerIndex]?.let(::applyPcmFormat)
            var lastProgressAt = SystemClock.elapsedRealtime()
            while (!outputEnded) {
                ensureWindowAvailable(cancellation)
                var progressed = false
                if (!inputEnded) {
                    val inputIndex = session.inputIndices.poll() ?: -1
                    if (inputIndex >= 0) {
                        inputEnded = queueExtractorInput(codec, inputIndex, extractor, endUs + DECODE_TAIL_US, plan)
                        progressed = true
                    }
                }
                val outputEvent = session.outputEvents.poll()
                when (outputEvent) {
                    is AsyncOutput.Format -> {
                        applyOutputFormat(outputEvent.format)
                        progressed = true
                    }

                    is AsyncOutput.Buffer -> {
                        outputEnded = consumeOutput(outputEvent.index, outputEvent.infos)
                        progressed = true
                    }

                    is AsyncOutput.Error -> {
                        throw outputEvent.error
                    }

                    null -> {
                        Unit
                    }
                }
                if (progressed) {
                    lastProgressAt = SystemClock.elapsedRealtime()
                } else {
                    if (SystemClock.elapsedRealtime() - lastProgressAt >= 30_000L) {
                        throw java.io.IOException("Decoder stopped producing buffers")
                    }
                    Thread.sleep(1L)
                }
            }
            require(sourceRate > 0 && sourceChannels > 0 && selectedFrames > 0L) {
                "Decoder produced no requested PCM: codec=${plan.codecName} rate=$sourceRate channels=$sourceChannels " +
                    "startUs=$startUs inputUs=$firstAccessUnitUs outputUs=$firstPresentationUs " +
                    "decodedFrames=$decodedFrames selectedFrames=$selectedFrames"
            }
            aiResampler?.finish()
            val nativeFinishStarted = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
            val loudness =
                analyzer?.let { currentAnalyzer ->
                    try {
                        currentAnalyzer.finish().takeIf(Double::isFinite)
                    } catch (error: IllegalArgumentException) {
                        if (error.message?.contains("Integrated loudness is unavailable") == true) {
                            null
                        } else {
                            throw error
                        }
                    }
                }
            val nativeFinishNanos =
                if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() - nativeFinishStarted else 0L
            if (needsLoudness && loudness == null) {
                analyzer?.close()
                analyzer = null
                return DecodedWindow(
                    analyzer = null,
                    integratedLufs = null,
                    aiUsable = aiDestination == null || selectedFrames > 0L,
                    profile =
                        PcmBufferProfile(
                            decodedPcmBytes = decodedPcmBytes,
                            nativeAddCalls = nativeAddCalls,
                            nativeAddNanos = nativeAddNanos,
                            nativeFinishNanos = nativeFinishNanos,
                            aiResampleNanos = aiResampleNanos,
                        ),
                )
            } else {
                return DecodedWindow(
                    analyzer = analyzer,
                    integratedLufs = loudness,
                    aiUsable = aiDestination == null || selectedFrames > 0L,
                    profile =
                        PcmBufferProfile(
                            decodedPcmBytes = decodedPcmBytes,
                            nativeAddCalls = nativeAddCalls,
                            nativeAddNanos = nativeAddNanos,
                            nativeFinishNanos = nativeFinishNanos,
                            aiResampleNanos = aiResampleNanos,
                        ),
                )
            }
        } catch (error: Throwable) {
            analyzer?.close()
            throw error
        }
    }

    private class StreamingMonoResampler(
        private val sourceRate: Int,
        private val channels: Int,
        private val destination: FloatArray,
        private val destinationOffset: Int,
    ) {
        val increment = sourceRate.toDouble() / Level2AudioFeatures.SAMPLE_RATE
        private var inputFrames = 0
        private var nextOutput = 0
        private var previous = 0f

        init {
            require(sourceRate > 0 && channels > 0)
            require(destinationOffset >= 0)
            require(destinationOffset + Level2AudioFeatures.EXCERPT_SAMPLES <= destination.size)
            destination.fill(
                element = 0f,
                fromIndex = destinationOffset,
                toIndex = destinationOffset + Level2AudioFeatures.EXCERPT_SAMPLES,
            )
        }

        fun addPcm(
            buffer: java.nio.ByteBuffer,
            byteOffset: Int,
            frames: Int,
            encoding: Int,
        ) {
            val bytesPerSample = decoderPcmBytesPerSample(encoding)
            val bytesPerFrame = bytesPerSample * channels
            repeat(frames) { frame ->
                val frameOffset = byteOffset + frame * bytesPerFrame
                var mixed = 0f
                repeat(channels) { channel ->
                    mixed += readSample(buffer, frameOffset + channel * bytesPerSample, encoding)
                }
                addFrame((mixed / channels).coerceIn(-1f, 1f))
            }
        }

        fun finish() {
            if (inputFrames == 0) return
            val lastFrame = inputFrames - 1
            var padded = 0
            while (nextOutput < Level2AudioFeatures.EXCERPT_SAMPLES) {
                val position = nextOutput * increment
                if (floor(position).toInt() != lastFrame) {
                    if (padded >= MAX_TRAILING_SAMPLES) break
                    padded += 1
                }
                destination[destinationOffset + nextOutput] = previous
                nextOutput += 1
            }
        }

        private fun addFrame(value: Float) {
            val currentFrame = inputFrames
            inputFrames += 1
            if (currentFrame == 0) {
                previous = value
                return
            }
            val leftFrame = currentFrame - 1
            while (nextOutput < Level2AudioFeatures.EXCERPT_SAMPLES) {
                val position = nextOutput * increment
                if (floor(position).toInt() != leftFrame) break
                val fraction = (position - leftFrame).toFloat()
                destination[destinationOffset + nextOutput] =
                    (previous + (value - previous) * fraction).coerceIn(-1f, 1f)
                nextOutput += 1
            }
            previous = value
        }

        private fun readSample(
            buffer: java.nio.ByteBuffer,
            offset: Int,
            encoding: Int,
        ): Float =
            when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    buffer.getFloat(offset)
                }

                AudioFormat.ENCODING_PCM_16BIT -> {
                    buffer.getShort(offset) / 32768f
                }

                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val sample =
                        (buffer.get(offset).toInt() and 0xff) or
                            ((buffer.get(offset + 1).toInt() and 0xff) shl 8) or
                            (buffer.get(offset + 2).toInt() shl 16)
                    sample / 8388608f
                }

                AudioFormat.ENCODING_PCM_32BIT -> {
                    buffer.getInt(offset) / 2147483648f
                }

                else -> {
                    error("unreachable")
                }
            }
    }

    private fun openExtractor(source: SourceMedia): MediaExtractor =
        MediaExtractor().also { extractor ->
            try {
                if (source.locator.contains("://") || source.locator.startsWith("content:")) {
                    extractor.setDataSource(context, Uri.parse(source.locator), emptyMap())
                } else {
                    extractor.setDataSource(source.locator)
                }
            } catch (error: Throwable) {
                extractor.release()
                throw error
            }
        }

    private fun MediaExtractor.audioTrack(): Int =
        (0 until trackCount).firstOrNull { index ->
            getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("Media source has no audio track")

    private fun ensureWindowAvailable(cancellation: CancellationSignal) {
        cancellation.ensureAvailable()
        if (Thread.currentThread().isInterrupted) throw CancellationException("Level 2 window decoder stopped")
    }

    private fun MediaFormat.getLongOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null

    private fun MediaFormat.getIntegerOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    private data class DecodedWindow(
        val analyzer: ReplayGainNativeAnalyzer?,
        val integratedLufs: Double?,
        val aiUsable: Boolean,
        val profile: PcmBufferProfile,
    ) : Closeable {
        override fun close() {
            analyzer?.close()
        }
    }

    private data class DecoderPcmFormat(
        val sampleRate: Int,
        val channelCount: Int,
        val encoding: Int,
    ) {
        init {
            require(sampleRate > 0 && channelCount > 0)
        }
    }

    private companion object {
        const val MAX_WINDOW_WORKERS = 3
        const val WINDOW_CLOSE_TIMEOUT_SECONDS = 30L
        const val AI_WINDOW_SECONDS = 10
        const val PRE_ROLL_US = 1_000_000L
        const val MICROS_PER_SECOND = 1_000_000L
        const val DECODE_TAIL_US = 500_000L
        const val MAX_TRAILING_SAMPLES = 2
    }
}
