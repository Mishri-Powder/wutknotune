package link.roadio.riff.indexer.android.level2

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.storage.hash.NativeXxh3Factory
import link.roadio.riff.storage.hash.Xxh3Stream
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal enum class DecoderBatchApi {
    SINGLE,
    LEGACY,
    PLATFORM,
}

internal data class AndroidDecoderPlan(
    val codecName: String,
    val canonicalName: String,
    val batchApi: DecoderBatchApi,
    val maxAccessUnits: Int,
    val maxEncodedBytes: Int = MAX_BATCH_ENCODED_BYTES,
) {
    val batchesInput: Boolean
        get() = batchApi != DecoderBatchApi.SINGLE && maxAccessUnits > 1
}

internal class AndroidDecoderAutotuner(
    private val context: Context,
) {
    private val plans = mutableMapOf<Long, AndroidDecoderPlan>()

    @Synchronized
    fun select(
        source: SourceMedia,
        decoderHint: Long,
        cancellation: CancellationSignal,
    ): AndroidDecoderPlan {
        plans[decoderHint]?.let { return it }
        val selected =
            runCatching { tune(source, decoderHint, cancellation) }
                .onFailure { error ->
                    Log.w(LOG_TAG, "decoder_autotune_failed hint=$decoderHint type=${error.javaClass.simpleName}")
                }.getOrElse { defaultPlan(source) }
        plans[decoderHint] = selected
        Log.i(
            LOG_TAG,
            "decoder_autotune_selected hint=$decoderHint codec=${selected.codecName} " +
                "canonical=${selected.canonicalName} api=${selected.batchApi} " +
                "batch_au=${selected.maxAccessUnits} batch_bytes=${selected.maxEncodedBytes}",
        )
        return selected
    }

    @Synchronized
    fun disableBatching(
        decoderHint: Long,
        plan: AndroidDecoderPlan,
        error: Throwable,
    ) {
        plans[decoderHint] = plan.copy(batchApi = DecoderBatchApi.SINGLE, maxAccessUnits = 1)
        Log.w(
            LOG_TAG,
            "decoder_batching_disabled hint=$decoderHint codec=${plan.codecName} " +
                "api=${plan.batchApi} type=${error.javaClass.simpleName}",
        )
    }

    private fun tune(
        source: SourceMedia,
        decoderHint: Long,
        cancellation: CancellationSignal,
    ): AndroidDecoderPlan {
        cancellation.ensureAvailable()
        val probe = readProbe(source)
        if (probe.mime !in TUNED_MIMES) return defaultPlan(probe.format)
        val batchApi = if (Build.VERSION.SDK_INT >= 35) DecoderBatchApi.PLATFORM else DecoderBatchApi.LEGACY
        val candidates = candidates(probe.mime, batchApi)
        if (candidates.isEmpty()) return defaultPlan(probe.format)

        val measured =
            candidates.mapNotNull { candidate ->
                cancellation.ensureAvailable()
                tuneCandidate(candidate, probe, batchApi, cancellation)
            }
        if (measured.isEmpty()) return defaultPlan(probe.format)
        val winner = measured.minBy(CandidateMeasurement::elapsedNanos)
        Log.i(
            LOG_TAG,
            "decoder_autotune_complete hint=$decoderHint mime=${probe.mime} candidates=${measured.size} " +
                "winner=${winner.codecName} batch_au=${winner.batchSize} elapsed_ms=${winner.elapsedNanos / 1_000_000L}",
        )
        return AndroidDecoderPlan(
            codecName = winner.codecName,
            canonicalName = winner.canonicalName,
            batchApi = batchApi,
            maxAccessUnits = winner.batchSize,
        )
    }

    private fun tuneCandidate(
        candidate: MediaCodecInfo,
        probe: ProbeSource,
        batchApi: DecoderBatchApi,
        cancellation: CancellationSignal,
    ): CandidateMeasurement? {
        val baseline =
            runCatching {
                decodeProbe(candidate.name, probe.format, probe.blocks.first(), batchApi, 1)
            }.getOrNull() ?: return null
        val passed = mutableListOf<Pair<Int, Long>>()
        for (batchSize in BATCH_SIZES) {
            cancellation.ensureAvailable()
            val result =
                try {
                    decodeProbe(candidate.name, probe.format, probe.blocks.first(), batchApi, batchSize)
                } catch (_: Throwable) {
                    break
                }
            if (!result.samePcmAs(baseline)) break
            passed += batchSize to result.elapsedNanos
        }
        if (passed.isEmpty()) return null

        val bestNanos = passed.minOf { it.second }
        val selectedBatch =
            passed
                .filter { (_, elapsed) -> elapsed.toDouble() <= bestNanos / MINIMUM_BEST_THROUGHPUT_RATIO }
                .minOf { it.first }
        cancellation.ensureAvailable()
        val elapsed =
            runCatching { benchmarkThree(candidate.name, probe, batchApi, selectedBatch) }
                .getOrNull() ?: return null
        Log.i(
            LOG_TAG,
            "decoder_autotune_candidate codec=${candidate.name} canonical=${candidate.canonicalName} " +
                "api=$batchApi passed=${passed.joinToString(",") { it.first.toString() }} selected=$selectedBatch " +
                "elapsed_ms=${elapsed / 1_000_000L}",
        )
        return CandidateMeasurement(candidate.name, candidate.canonicalName, selectedBatch, elapsed)
    }

    private fun benchmarkThree(
        codecName: String,
        probe: ProbeSource,
        batchApi: DecoderBatchApi,
        batchSize: Int,
    ): Long {
        val executor = Executors.newFixedThreadPool(probe.blocks.size)
        return try {
            val started = SystemClock.elapsedRealtimeNanos()
            val futures =
                probe.blocks.map { block ->
                    executor.submit<ProbeDecode> {
                        decodeProbe(codecName, probe.format, block, batchApi, batchSize)
                    }
                }
            futures.forEach { it.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            SystemClock.elapsedRealtimeNanos() - started
        } finally {
            executor.shutdownNow()
        }
    }

    private fun candidates(
        mime: String,
        batchApi: DecoderBatchApi,
    ): List<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.ALL_CODECS)
            .codecInfos
            .asSequence()
            .filterNot(MediaCodecInfo::isEncoder)
            .filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            .filter { info ->
                batchApi != DecoderBatchApi.PLATFORM ||
                    info.getCapabilitiesForType(mime).isFeatureSupported(
                        MediaCodecInfo.CodecCapabilities.FEATURE_MultipleFrames,
                    )
            }.groupBy(MediaCodecInfo::getCanonicalName)
            .values
            .map { aliases -> aliases.firstOrNull { !it.isAlias } ?: aliases.first() }

    private fun readProbe(source: SourceMedia): ProbeSource {
        val extractor = openExtractor(source)
        val format: MediaFormat
        val mime: String
        val durationUs: Long
        try {
            val track = extractor.audioTrack()
            format = MediaFormat(extractor.getTrackFormat(track))
            mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            durationUs = format.getLong(MediaFormat.KEY_DURATION)
        } finally {
            extractor.release()
        }
        val starts = level2BaseWindowStarts(durationUs)
        val blocks = starts.map { startUs -> readProbeBlock(source, startUs) }
        return ProbeSource(format, mime, blocks)
    }

    private fun readProbeBlock(
        source: SourceMedia,
        startUs: Long,
    ): ProbeBlock {
        val extractor = openExtractor(source)
        try {
            val track = extractor.audioTrack()
            extractor.selectTrack(track)
            extractor.seekTo(max(0L, startUs - PROBE_PRE_ROLL_US), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocateDirect(MAX_PROBE_ACCESS_UNIT_BYTES)
            val units = mutableListOf<CodecAccessUnit>()
            while (units.size < PROBE_PRE_ROLL_AU + PROBE_MEASURED_AU) {
                val timeUs = extractor.sampleTime
                if (timeUs < 0L) break
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size <= 0) break
                val bytes = ByteArray(size)
                buffer.position(0)
                buffer.get(bytes)
                units += CodecAccessUnit(bytes, timeUs, codecInputFlags(extractor.sampleFlags))
                if (!extractor.advance()) break
            }
            require(units.size >= PROBE_PRE_ROLL_AU + PROBE_MEASURED_AU) { "Too few probe access units" }
            return ProbeBlock(units)
        } finally {
            extractor.release()
        }
    }

    private fun defaultPlan(source: SourceMedia): AndroidDecoderPlan {
        val extractor = openExtractor(source)
        return try {
            defaultPlan(extractor.getTrackFormat(extractor.audioTrack()))
        } finally {
            extractor.release()
        }
    }

    private fun defaultPlan(format: MediaFormat): AndroidDecoderPlan {
        val codecName =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
                ?: throw IllegalArgumentException("No decoder supports the audio format")
        val info =
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == codecName }
        return AndroidDecoderPlan(
            codecName = codecName,
            canonicalName = info?.canonicalName ?: codecName,
            batchApi = DecoderBatchApi.SINGLE,
            maxAccessUnits = 1,
        )
    }

    private fun decodeProbe(
        codecName: String,
        inputFormat: MediaFormat,
        block: ProbeBlock,
        batchApi: DecoderBatchApi,
        batchSize: Int,
    ): ProbeDecode {
        val chunks = probeChunks(block, batchSize)
        val configured =
            configuredDecoderFormat(
                inputFormat,
                AndroidDecoderPlan(codecName, codecName, batchApi, batchSize),
            )
        return decodeProbeWithCallbacks(codecName, configured, chunks, batchApi)
    }

    private fun decodeProbeWithCallbacks(
        codecName: String,
        format: MediaFormat,
        chunks: List<CodecInputChunk>,
        batchApi: DecoderBatchApi,
    ): ProbeDecode {
        val session = CodecCallbackSession(codecName, format)
        val digest = NativeXxh3Factory.create()
        val digestScratch = ByteArray(XXH3_CHUNK_BYTES)
        try {
            val started = SystemClock.elapsedRealtimeNanos()
            var nextChunk = 0
            var pendingChunk: CodecInputChunk? = null
            var eosQueued = false
            var outputEnded = false
            var outputBytes = 0L
            var outputFormat: MediaFormat? = null
            var idle = 0
            while (!outputEnded) {
                var progressed = false
                if (!eosQueued) {
                    session.inputIndices.poll()?.let { index ->
                        if (pendingChunk != null || nextChunk < chunks.size) {
                            val chunk = pendingChunk ?: chunks[nextChunk++]
                            pendingChunk = session.queue(index, chunk, batchApi)
                        } else {
                            session.codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                        }
                        progressed = true
                    }
                }
                session.outputEvents.poll()?.let { event ->
                    when (event) {
                        is AsyncOutput.Format -> {
                            outputFormat = event.format
                        }

                        is AsyncOutput.Buffer -> {
                            digestOutput(session.codec, event.index, event.infos, digest, digestScratch)
                            outputBytes += event.infos.sumOf { it.size.toLong() }
                            outputEnded = event.infos.any { it.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 }
                            session.codec.releaseOutputBuffer(event.index, false)
                        }

                        is AsyncOutput.Error -> {
                            throw event.error
                        }
                    }
                    progressed = true
                }
                idle = if (progressed) 0 else idle + 1
                if (!progressed) Thread.sleep(1L)
                require(idle < MAX_PROBE_IDLE_POLLS) { "Callback decoder probe timed out" }
            }
            require(outputBytes > 0L)
            return ProbeDecode(
                elapsedNanos = SystemClock.elapsedRealtimeNanos() - started,
                outputBytes = outputBytes,
                xxh3 = digest.digest(),
                pcmFormat = pcmSignature(outputFormat ?: session.codec.outputFormat),
            )
        } finally {
            digest.close()
            session.close()
        }
    }

    private fun probeChunks(
        block: ProbeBlock,
        requestedBatchSize: Int,
    ): List<CodecInputChunk> {
        val chunks =
            block.units
                .take(PROBE_PRE_ROLL_AU)
                .map { CodecInputChunk(listOf(it)) }
                .toMutableList()
        val measured = block.units.drop(PROBE_PRE_ROLL_AU)
        var index = 0
        while (index < measured.size) {
            val units = mutableListOf<CodecAccessUnit>()
            var bytes = 0
            val firstTimeUs = measured[index].presentationTimeUs
            while (index < measured.size && units.size < requestedBatchSize) {
                val unit = measured[index]
                if (
                    units.isNotEmpty() &&
                    (
                        bytes + unit.data.size > MAX_BATCH_ENCODED_BYTES ||
                            unit.presentationTimeUs - firstTimeUs > MAX_BATCH_DURATION_US
                    )
                ) {
                    break
                }
                units += unit
                bytes += unit.data.size
                index += 1
            }
            chunks += CodecInputChunk(units)
        }
        return chunks
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

    private data class ProbeSource(
        val format: MediaFormat,
        val mime: String,
        val blocks: List<ProbeBlock>,
    )

    private data class ProbeBlock(
        val units: List<CodecAccessUnit>,
    )

    private data class ProbeDecode(
        val elapsedNanos: Long,
        val outputBytes: Long,
        val xxh3: Long,
        val pcmFormat: String,
    ) {
        fun samePcmAs(other: ProbeDecode): Boolean = outputBytes == other.outputBytes && xxh3 == other.xxh3 && pcmFormat == other.pcmFormat
    }

    private data class CandidateMeasurement(
        val codecName: String,
        val canonicalName: String,
        val batchSize: Int,
        val elapsedNanos: Long,
    )

    private companion object {
        const val LOG_TAG = "RiffDecoderTune"
        const val PROBE_PRE_ROLL_AU = 8
        const val PROBE_MEASURED_AU = 256
        const val PROBE_PRE_ROLL_US = 1_000_000L
        const val MAX_BATCH_SIZE = 64
        const val MAX_PROBE_ACCESS_UNIT_BYTES = 1 * 1024 * 1024
        const val MAX_PROBE_IDLE_POLLS = 5_000
        const val PROBE_TIMEOUT_SECONDS = 20L
        const val MINIMUM_BEST_THROUGHPUT_RATIO = 0.95
        const val XXH3_CHUNK_BYTES = 64 * 1024
        val BATCH_SIZES = listOf(2, 4, 8, 16, 32, 64)
        val TUNED_MIMES = setOf("audio/mpeg", "audio/mp4a-latm")
    }
}

internal const val MAX_BATCH_ENCODED_BYTES = 128 * 1024
internal const val MAX_BATCH_DURATION_US = 2_000_000L
private const val PLATFORM_MAX_OUTPUT_BYTES = 1 * 1024 * 1024
private const val PLATFORM_OUTPUT_THRESHOLD_BYTES = 256 * 1024

internal fun configuredDecoderFormat(
    input: MediaFormat,
    plan: AndroidDecoderPlan,
): MediaFormat =
    MediaFormat(input).apply {
        val existing = if (containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, max(existing, plan.maxEncodedBytes))
        if (plan.batchApi == DecoderBatchApi.PLATFORM && Build.VERSION.SDK_INT >= 35) {
            setInteger(MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE, PLATFORM_MAX_OUTPUT_BYTES)
            setInteger(MediaFormat.KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE, PLATFORM_OUTPUT_THRESHOLD_BYTES)
        }
    }

internal data class CodecAccessUnit(
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int,
)

internal data class CodecInputChunk(
    val units: List<CodecAccessUnit>,
) {
    init {
        require(units.isNotEmpty())
    }

    val size = units.sumOf { it.data.size }
}

internal fun CodecInputChunk.splitForCapacity(capacity: Int): Pair<CodecInputChunk, CodecInputChunk?> {
    require(capacity > 0)
    require(units.first().data.size <= capacity) { "Access unit exceeds decoder input capacity" }
    var byteCount = 0
    val acceptedCount =
        units
            .indexOfFirst { unit ->
                if (byteCount + unit.data.size > capacity) {
                    true
                } else {
                    byteCount += unit.data.size
                    false
                }
            }.let { index -> if (index < 0) units.size else index }
    val accepted = CodecInputChunk(units.take(acceptedCount))
    val remainder = units.drop(acceptedCount).takeIf(List<CodecAccessUnit>::isNotEmpty)?.let(::CodecInputChunk)
    return accepted to remainder
}

internal fun queueExtractorInput(
    codec: MediaCodec,
    inputIndex: Int,
    extractor: MediaExtractor,
    endUs: Long,
    plan: AndroidDecoderPlan,
): Boolean {
    val input = requireNotNull(codec.getInputBuffer(inputIndex))
    input.clear()
    val infos = ArrayDeque<MediaCodec.BufferInfo>(plan.maxAccessUnits)
    var offset = 0
    var firstTimeUs = -1L
    while (infos.size < plan.maxAccessUnits) {
        val sampleTimeUs = extractor.sampleTime
        if (sampleTimeUs < 0L || sampleTimeUs > endUs) break
        val announcedSize = extractor.sampleSize
        if (
            infos.isNotEmpty() &&
            (
                sampleTimeUs - firstTimeUs > MAX_BATCH_DURATION_US ||
                    announcedSize < 0L ||
                    offset + announcedSize > input.capacity() ||
                    offset + announcedSize > plan.maxEncodedBytes
            )
        ) {
            break
        }
        if (announcedSize > input.capacity() || announcedSize > plan.maxEncodedBytes) {
            throw IllegalArgumentException("Access unit exceeds decoder input capacity")
        }
        val size = extractor.readSampleData(input, offset)
        if (size <= 0) break
        if (firstTimeUs < 0L) firstTimeUs = sampleTimeUs
        infos +=
            MediaCodec.BufferInfo().apply {
                set(offset, size, sampleTimeUs, codecInputFlags(extractor.sampleFlags))
            }
        offset += size
        if (!extractor.advance()) break
        if (plan.maxAccessUnits == 1) break
    }
    if (infos.isEmpty()) {
        codec.queueInputBuffer(
            inputIndex,
            0,
            0,
            max(0L, extractor.sampleTime),
            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
        )
        return true
    }
    if (plan.batchApi == DecoderBatchApi.PLATFORM && infos.size > 1 && Build.VERSION.SDK_INT >= 35) {
        codec.queueInputBuffers(inputIndex, infos)
    } else {
        val first = infos.first
        codec.queueInputBuffer(inputIndex, 0, offset, first.presentationTimeUs, first.flags)
    }
    return false
}

internal fun MediaCodec.BufferInfo.copy(): MediaCodec.BufferInfo =
    MediaCodec.BufferInfo().also { copy ->
        copy.set(offset, size, presentationTimeUs, flags)
    }

internal sealed interface AsyncOutput {
    data class Format(
        val format: MediaFormat,
    ) : AsyncOutput

    data class Buffer(
        val index: Int,
        val infos: List<MediaCodec.BufferInfo>,
    ) : AsyncOutput

    data class Error(
        val error: Throwable,
    ) : AsyncOutput
}

internal class CodecCallbackSession(
    codecName: String,
    format: MediaFormat,
) : AutoCloseable {
    val codec: MediaCodec = MediaCodec.createByCodecName(codecName)
    val inputIndices = LinkedBlockingQueue<Int>()
    val outputEvents = LinkedBlockingQueue<AsyncOutput>()
    private val callbackThread = HandlerThread("RiffCodec-${codecName.takeLast(18)}").apply { start() }
    private val callbackHandler = Handler(callbackThread.looper)

    init {
        try {
            codec.setCallback(
                object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                    ) {
                        inputIndices.offer(index)
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo,
                    ) {
                        outputEvents.offer(AsyncOutput.Buffer(index, listOf(info.copy())))
                    }

                    override fun onOutputBuffersAvailable(
                        codec: MediaCodec,
                        index: Int,
                        infos: ArrayDeque<MediaCodec.BufferInfo>,
                    ) {
                        outputEvents.offer(AsyncOutput.Buffer(index, infos.map(MediaCodec.BufferInfo::copy)))
                    }

                    override fun onOutputFormatChanged(
                        codec: MediaCodec,
                        format: MediaFormat,
                    ) {
                        outputEvents.offer(AsyncOutput.Format(MediaFormat(format)))
                    }

                    override fun onError(
                        codec: MediaCodec,
                        error: MediaCodec.CodecException,
                    ) {
                        outputEvents.offer(AsyncOutput.Error(error))
                    }
                },
                callbackHandler,
            )
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (error: Throwable) {
            runCatching { close() }
            throw error
        }
    }

    fun queue(
        index: Int,
        chunk: CodecInputChunk,
        batchApi: DecoderBatchApi,
    ): CodecInputChunk? {
        val input = requireNotNull(codec.getInputBuffer(index))
        input.clear()
        val (accepted, remainder) = chunk.splitForCapacity(input.capacity())
        val infos = ArrayDeque<MediaCodec.BufferInfo>(accepted.units.size)
        var offset = 0
        accepted.units.forEach { unit ->
            input.put(unit.data)
            infos +=
                MediaCodec.BufferInfo().apply {
                    set(offset, unit.data.size, unit.presentationTimeUs, unit.flags)
                }
            offset += unit.data.size
        }
        if (batchApi == DecoderBatchApi.PLATFORM && infos.size > 1) {
            if (Build.VERSION.SDK_INT >= 35) {
                codec.queueInputBuffers(index, infos)
            } else {
                error("Platform multiple-frame input requires API 35")
            }
        } else {
            val info = infos.first
            codec.queueInputBuffer(index, 0, offset, info.presentationTimeUs, info.flags)
        }
        return remainder
    }

    fun flush() {
        codec.flush()
        // flush() invalidates buffers but may leave callbacks queued on this handler.
        // Drain those callbacks before clearing their events and admitting a new generation.
        val restart =
            FutureTask {
                inputIndices.clear()
                outputEvents.clear()
                codec.start()
            }
        check(callbackHandler.post(restart)) { "Decoder callback handler has stopped" }
        restart.get(10L, TimeUnit.SECONDS)
    }

    override fun close() {
        runCatching(codec::stop)
        try {
            codec.release()
        } finally {
            callbackThread.quitSafely()
            callbackThread.join(2_000L)
        }
    }
}

private fun digestOutput(
    codec: MediaCodec,
    index: Int,
    infos: List<MediaCodec.BufferInfo>,
    digest: Xxh3Stream,
    scratch: ByteArray,
) {
    val source = requireNotNull(codec.getOutputBuffer(index))
    infos.filter { it.size > 0 }.forEach { info ->
        val buffer = source.duplicate()
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        while (buffer.hasRemaining()) {
            val count = minOf(buffer.remaining(), scratch.size)
            buffer.get(scratch, 0, count)
            digest.update(scratch, 0, count)
        }
    }
}

private fun pcmSignature(format: MediaFormat): String =
    listOf(
        format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        },
    ).joinToString(":")

private fun codecInputFlags(sampleFlags: Int): Int {
    var result = 0
    if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
        result = result or MediaCodec.BUFFER_FLAG_KEY_FRAME
    }
    if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
        result = result or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
    }
    return result
}
