package link.roadio.riff.indexer.android.level2

import android.content.ContentUris
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.max

/** Preserves the synchronous three-window experimental baseline; production uses callbacks. */
@RunWith(AndroidJUnit4::class)
class AndroidMediaCodecDecoderComparisonTest {
    @Test
    fun compareConfiguredAacDecoders() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext
        val volume = configuredCorpusVolume(arguments, ARG_VOLUME)
        val scanLimit = arguments.getString(ARG_SCAN_LIMIT)?.toIntOrNull() ?: DEFAULT_SCAN_LIMIT
        val sampleLimit = arguments.getString(ARG_SAMPLE_LIMIT)?.toIntOrNull() ?: DEFAULT_SAMPLE_LIMIT
        require(volume in MediaStore.getExternalVolumeNames(context))
        require(scanLimit > 0 && sampleLimit > 0)

        val candidates = codecCandidates()
        val inventory = queryAudio(context, volume)
        val scanStarted = SystemClock.elapsedRealtimeNanos()
        val cohorts = observeAacCohorts(context, inventory, scanLimit)
        val cohort = requireNotNull(cohorts.maxByOrNull { it.items.size })
        val samples =
            cohort.items
                .sortedWith(compareBy<CorpusItem> { selectionKey(it.id) }.thenBy { it.id })
                .take(sampleLimit)
        require(samples.size >= MINIMUM_SAMPLE_COUNT) {
            "Dominant AAC cohort has only ${samples.size} samples"
        }
        val scanNanos = SystemClock.elapsedRealtimeNanos() - scanStarted

        val pools = candidates.associateWith { DecoderPool(context, it.name, cohort.format) }
        try {
            val warmups = samples.take(WARMUP_FILES)
            candidates.forEach { candidate ->
                Log.i(TAG, "warmup_start codec=${candidate.name}")
                warmups.forEach { pools.getValue(candidate).decode(it) }
                Log.i(TAG, "warmup_complete codec=${candidate.name}")
            }

            val measurements = candidates.associateWith { mutableListOf<FileMeasurement>() }
            val runStarted = SystemClock.elapsedRealtimeNanos()
            samples.forEachIndexed { index, item ->
                val order = if (index % 2 == 0) candidates else candidates.asReversed()
                order.forEach { candidate ->
                    measurements.getValue(candidate) += pools.getValue(candidate).decode(item)
                }
                if ((index + 1) % PROGRESS_INTERVAL == 0 || index + 1 == samples.size) {
                    Log.i(TAG, "progress completed=${index + 1} total=${samples.size}")
                }
            }
            val result =
                JSONObject()
                    .put("event", "media_codec_decoder_comparison")
                    .put("schema_version", 1)
                    .put("media_store_volume", volume)
                    .put("inventory_count", inventory.size)
                    .put("scan_limit", scanLimit)
                    .put("scan_elapsed_ns", scanNanos)
                    .put("observed_aac_cohorts", JSONArray(cohorts.map(ObservedCohort::json)))
                    .put("selected_cohort", cohort.key.json())
                    .put("selected_cohort_count", cohort.items.size)
                    .put("sample_count", samples.size)
                    .put("warmup_files", warmups.size)
                    .put("window_positions", JSONArray(WINDOW_POSITIONS))
                    .put("window_duration_us", WINDOW_DURATION_US)
                    .put("pre_roll_us", PRE_ROLL_US)
                    .put("window_parallelism", WINDOW_WORKERS)
                    .put("pump", "production synchronous one-input/one-output")
                    .put("pair_order", "even AB, odd BA")
                    .put("run_elapsed_ns", SystemClock.elapsedRealtimeNanos() - runStarted)
                    .put("samples", JSONArray(samples.map(CorpusItem::json)))
                    .put(
                        "codecs",
                        JSONArray(
                            candidates.map { candidate ->
                                codecSummary(candidate, measurements.getValue(candidate))
                            },
                        ),
                    )
            val resultFile = File(context.filesDir, RESULT_FILE)
            resultFile.writeText(result.toString())
            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putString("riffMediaCodecDecoderComparison", resultFile.absolutePath)
                },
            )
        } finally {
            pools.values.forEach(DecoderPool::close)
        }
    }

    private fun codecCandidates(): List<CodecCandidate> {
        val infos =
            MediaCodecList(MediaCodecList.ALL_CODECS)
                .codecInfos
                .associateBy(MediaCodecInfo::getName)
        return CODEC_NAMES.map { name ->
            val info = requireNotNull(infos[name]) { "Codec is not registered: $name" }
            require(!info.isEncoder && AAC_MIME in info.supportedTypes)
            CodecCandidate(
                name = name,
                canonicalName = info.canonicalName,
                hardwareAccelerated = info.isHardwareAccelerated,
                softwareOnly = info.isSoftwareOnly,
                vendor = info.isVendor,
            )
        }
    }

    private fun observeAacCohorts(
        context: Context,
        inventory: List<CorpusItem>,
        scanLimit: Int,
    ): List<ObservedCohort> {
        val cohorts = linkedMapOf<SourceFormatKey, ObservedCohort>()
        inventory
            .sortedWith(compareBy<CorpusItem> { selectionKey(it.id) }.thenBy { it.id })
            .take(scanLimit)
            .forEach { item ->
                runCatching {
                    withExtractor(context, item.locator) { extractor ->
                        val format = extractor.getTrackFormat(extractor.audioTrack())
                        if (format.getString(MediaFormat.KEY_MIME) != AAC_MIME) return@withExtractor
                        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                        if (durationUs < MINIMUM_DURATION_US) return@withExtractor
                        val key = SourceFormatKey.from(format)
                        val measured = item.copy(durationUs = durationUs)
                        val existing = cohorts[key]
                        if (existing == null) {
                            cohorts[key] =
                                ObservedCohort(
                                    key = key,
                                    format = MediaFormat(format),
                                    items = mutableListOf(measured),
                                )
                        } else {
                            existing.items += measured
                        }
                    }
                }
            }
        return cohorts.values.sortedByDescending { it.items.size }
    }

    private fun queryAudio(
        context: Context,
        volume: String,
    ): List<CorpusItem> {
        val collection = MediaStore.Audio.Media.getContentUri(volume)
        return context.contentResolver
            .query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DURATION),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                buildList {
                    while (cursor.moveToNext()) {
                        val durationUs = cursor.getLong(durationColumn) * 1_000L
                        if (durationUs >= MINIMUM_DURATION_US) {
                            val id = cursor.getLong(idColumn)
                            add(
                                CorpusItem(
                                    id,
                                    ContentUris.withAppendedId(collection, id).toString(),
                                    durationUs,
                                ),
                            )
                        }
                    }
                }
            }.orEmpty()
    }

    private inner class DecoderPool(
        private val context: Context,
        codecName: String,
        format: MediaFormat,
    ) : AutoCloseable {
        private val executor = Executors.newFixedThreadPool(WINDOW_WORKERS)
        private val codecs =
            Array(WINDOW_WORKERS) {
                MediaCodec.createByCodecName(codecName).apply {
                    configure(MediaFormat(format), null, null, 0)
                    start()
                }
            }

        fun decode(item: CorpusItem): FileMeasurement {
            val started = SystemClock.elapsedRealtimeNanos()
            val windows =
                baseWindowStarts(item.durationUs)
                    .mapIndexed { index, startUs ->
                        executor.submit(Callable { decodeWindow(codecs[index], item.locator, startUs) })
                    }.map { it.get() }
            return FileMeasurement(
                mediaStoreId = item.id,
                elapsedNanos = SystemClock.elapsedRealtimeNanos() - started,
                windows = windows,
            )
        }

        private fun decodeWindow(
            codec: MediaCodec,
            locator: String,
            startUs: Long,
        ): WindowMeasurement =
            withExtractor(context, locator) { extractor ->
                val track = extractor.audioTrack()
                extractor.selectTrack(track)
                extractor.seekTo(max(0L, startUs - PRE_ROLL_US), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                codec.flush()
                val stopUs = startUs + WINDOW_DURATION_US + DECODE_TAIL_US
                val info = MediaCodec.BufferInfo()
                val started = SystemClock.elapsedRealtimeNanos()
                var inputEnded = false
                var outputEnded = false
                var inputAccessUnits = 0
                var inputBytes = 0L
                var outputBuffers = 0
                var outputBytes = 0L
                var idleInputDequeues = 0
                var idleOutputDequeues = 0
                var consecutiveIdle = 0
                while (!outputEnded) {
                    var progressed = false
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val input = requireNotNull(codec.getInputBuffer(inputIndex))
                            val sampleTime = extractor.sampleTime
                            if (sampleTime < 0L || sampleTime > stopUs) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    sampleTime.coerceAtLeast(0L),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEnded = true
                            } else {
                                input.clear()
                                val size = extractor.readSampleData(input, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        sampleTime.coerceAtLeast(0L),
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputEnded = true
                                } else {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        size,
                                        sampleTime,
                                        extractor.sampleFlags,
                                    )
                                    extractor.advance()
                                    inputAccessUnits += 1
                                    inputBytes += size
                                }
                            }
                            progressed = true
                        } else {
                            idleInputDequeues += 1
                        }
                    }
                    when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,
                        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                        -> {
                            progressed = true
                        }

                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            idleOutputDequeues += 1
                        }

                        else -> {
                            if (outputIndex >= 0) {
                                outputBuffers += 1
                                outputBytes += info.size
                                outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(outputIndex, false)
                                progressed = true
                            }
                        }
                    }
                    consecutiveIdle = if (progressed) 0 else consecutiveIdle + 1
                    require(consecutiveIdle < MAX_IDLE_DEQUEUES) { "Decoder made no progress" }
                }
                require(outputBytes > 0L)
                WindowMeasurement(
                    elapsedNanos = SystemClock.elapsedRealtimeNanos() - started,
                    inputAccessUnits = inputAccessUnits,
                    inputBytes = inputBytes,
                    outputBuffers = outputBuffers,
                    outputBytes = outputBytes,
                    idleInputDequeues = idleInputDequeues,
                    idleOutputDequeues = idleOutputDequeues,
                )
            }

        override fun close() {
            executor.shutdownNow()
            codecs.forEach { codec ->
                runCatching(codec::stop)
                codec.release()
            }
        }
    }

    private fun codecSummary(
        candidate: CodecCandidate,
        files: List<FileMeasurement>,
    ): JSONObject {
        val elapsed = files.map(FileMeasurement::elapsedNanos).sorted()
        val total = elapsed.sum()
        return candidate
            .json()
            .put("file_count", files.size)
            .put("files_per_second", files.size * NANOS_PER_SECOND.toDouble() / total)
            .put("mean_file_ms", total.toDouble() / files.size / NANOS_PER_MILLISECOND)
            .put("p50_file_ms", percentile(elapsed, 0.50) / NANOS_PER_MILLISECOND.toDouble())
            .put("p90_file_ms", percentile(elapsed, 0.90) / NANOS_PER_MILLISECOND.toDouble())
            .put("p95_file_ms", percentile(elapsed, 0.95) / NANOS_PER_MILLISECOND.toDouble())
            .put("input_access_units", files.sumOf { it.windows.sumOf(WindowMeasurement::inputAccessUnits) })
            .put("input_bytes", files.sumOf { it.windows.sumOf(WindowMeasurement::inputBytes) })
            .put("output_buffers", files.sumOf { it.windows.sumOf(WindowMeasurement::outputBuffers) })
            .put("output_bytes", files.sumOf { it.windows.sumOf(WindowMeasurement::outputBytes) })
            .put("idle_input_dequeues", files.sumOf { it.windows.sumOf(WindowMeasurement::idleInputDequeues) })
            .put("idle_output_dequeues", files.sumOf { it.windows.sumOf(WindowMeasurement::idleOutputDequeues) })
            .put("files", JSONArray(files.map(FileMeasurement::json)))
    }

    private fun <T> withExtractor(
        context: Context,
        locator: String,
        block: (MediaExtractor) -> T,
    ): T {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, android.net.Uri.parse(locator), emptyMap())
            return block(extractor)
        } finally {
            extractor.release()
        }
    }

    private fun MediaExtractor.audioTrack(): Int =
        (0 until trackCount).firstOrNull {
            getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("Media source has no audio track")

    private fun baseWindowStarts(durationUs: Long): List<Long> {
        val latestStart = max(0L, durationUs - 10_000_000L)
        return WINDOW_POSITIONS.map {
            ((durationUs * it).toLong() - 5_000_000L).coerceIn(0L, latestStart)
        }
    }

    private data class CorpusItem(
        val id: Long,
        val locator: String,
        val durationUs: Long,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("media_store_id", id)
                .put("locator", locator)
                .put("duration_us", durationUs)
    }

    private data class WindowMeasurement(
        val elapsedNanos: Long,
        val inputAccessUnits: Int,
        val inputBytes: Long,
        val outputBuffers: Int,
        val outputBytes: Long,
        val idleInputDequeues: Int,
        val idleOutputDequeues: Int,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("elapsed_ns", elapsedNanos)
                .put("input_access_units", inputAccessUnits)
                .put("input_bytes", inputBytes)
                .put("output_buffers", outputBuffers)
                .put("output_bytes", outputBytes)
                .put("idle_input_dequeues", idleInputDequeues)
                .put("idle_output_dequeues", idleOutputDequeues)
    }

    private data class FileMeasurement(
        val mediaStoreId: Long,
        val elapsedNanos: Long,
        val windows: List<WindowMeasurement>,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("media_store_id", mediaStoreId)
                .put("elapsed_ns", elapsedNanos)
                .put("windows", JSONArray(windows.map(WindowMeasurement::json)))
    }

    private data class CodecCandidate(
        val name: String,
        val canonicalName: String,
        val hardwareAccelerated: Boolean,
        val softwareOnly: Boolean,
        val vendor: Boolean,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("name", name)
                .put("canonical_name", canonicalName)
                .put("hardware_accelerated", hardwareAccelerated)
                .put("software_only", softwareOnly)
                .put("vendor", vendor)
    }

    private data class SourceFormatKey(
        val mime: String,
        val sampleRate: Int,
        val channelCount: Int,
        val channelMask: Int?,
        val profile: Int?,
        val isAdts: Int?,
        val csdSha256: List<String>,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("mime", mime)
                .put("sample_rate", sampleRate)
                .put("channel_count", channelCount)
                .put("channel_mask", channelMask ?: JSONObject.NULL)
                .put("profile", profile ?: JSONObject.NULL)
                .put("is_adts", isAdts ?: JSONObject.NULL)
                .put("csd_sha256", JSONArray(csdSha256))

        companion object {
            fun from(format: MediaFormat): SourceFormatKey =
                SourceFormatKey(
                    mime = requireNotNull(format.getString(MediaFormat.KEY_MIME)),
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    channelMask = format.optionalInteger(MediaFormat.KEY_CHANNEL_MASK),
                    profile = format.optionalInteger(MediaFormat.KEY_PROFILE),
                    isAdts = format.optionalInteger(MediaFormat.KEY_IS_ADTS),
                    csdSha256 =
                        (0..3).mapNotNull {
                            format.getByteBuffer("csd-$it")?.sha256()
                        },
                )
        }
    }

    private data class ObservedCohort(
        val key: SourceFormatKey,
        val format: MediaFormat,
        val items: MutableList<CorpusItem>,
    ) {
        fun json(): JSONObject = key.json().put("count", items.size)
    }

    private fun percentile(
        sorted: List<Long>,
        fraction: Double,
    ): Long = sorted[((sorted.size - 1) * fraction).toInt()]

    private fun selectionKey(id: Long): ULong {
        var value = (id xor SELECTION_SEED).toULong()
        value = (value xor (value shr 30)) * 0xbf58476d1ce4e5b9UL
        value = (value xor (value shr 27)) * 0x94d049bb133111ebUL
        return value xor (value shr 31)
    }

    private companion object {
        const val TAG = "RiffCodecComparison"
        const val RESULT_FILE = "media-codec-decoder-comparison-result.json"
        const val ARG_VOLUME = "riffMediaVolume"
        const val ARG_SCAN_LIMIT = "riffScanLimit"
        const val ARG_SAMPLE_LIMIT = "riffSampleLimit"
        const val DEFAULT_SCAN_LIMIT = 600
        const val DEFAULT_SAMPLE_LIMIT = 120
        const val MINIMUM_SAMPLE_COUNT = 30
        const val WARMUP_FILES = 3
        const val PROGRESS_INTERVAL = 10
        const val WINDOW_WORKERS = 3
        const val WINDOW_DURATION_US = 20_000_000L
        const val PRE_ROLL_US = 1_000_000L
        const val DECODE_TAIL_US = 500_000L
        const val MINIMUM_DURATION_US = 64_000_000L
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_IDLE_DEQUEUES = 1_000
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val SELECTION_SEED = 0x52494646434f4d50L
        const val AAC_MIME = "audio/mp4a-latm"
        val CODEC_NAMES = listOf("c2.android.aac.decoder", "OMX.ffmpeg.aac.decoder")
        val WINDOW_POSITIONS = listOf(0.25, 0.50, 0.75)
    }
}

private fun MediaFormat.optionalInteger(key: String): Int? = if (containsKey(key)) getInteger(key) else null

private fun ByteBuffer.sha256(): String {
    val copy = duplicate()
    val bytes = ByteArray(copy.remaining())
    copy.get(bytes)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
