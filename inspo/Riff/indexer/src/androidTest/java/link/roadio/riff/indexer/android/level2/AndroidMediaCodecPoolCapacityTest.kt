package link.roadio.riff.indexer.android.level2

import android.content.ContentUris
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.os.Debug
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Measures configured-and-started audio decoder residency with real HU formats. */
@RunWith(AndroidJUnit4::class)
class AndroidMediaCodecPoolCapacityTest {
    @Test
    fun measureRepresentativeDecoderPools() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext
        val volume = configuredCorpusVolume(arguments, ARG_VOLUME)
        require(volume in MediaStore.getExternalVolumeNames(context))
        val scanLimit = arguments.getString(ARG_SCAN_LIMIT)?.toIntOrNull() ?: DEFAULT_SCAN_LIMIT
        val probeLimit = arguments.getString(ARG_PROBE_LIMIT)?.toIntOrNull() ?: DEFAULT_PROBE_LIMIT
        require(scanLimit > 0 && probeLimit >= MIXED_POOL_WIDTH)

        val inventory = queryAudio(context, volume)
        val observed = observeFormats(context, inventory, scanLimit)
        val representatives =
            TARGET_MIMES.mapNotNull { mime ->
                observed
                    .filter { it.key.mime == mime }
                    .maxWithOrNull(compareBy<ObservedFormat> { it.count }.thenBy { it.key.describe() })
            }
        require(representatives.isNotEmpty()) { "No supported representative audio formats found" }

        val baseline = memorySnapshot()
        val individual = representatives.map { measureIndividualCapacity(it, probeLimit) }
        val mixed = measureMixedPool(context, representatives)
        val result =
            JSONObject()
                .put("event", "media_codec_pool_capacity")
                .put("schema_version", 1)
                .put("media_store_volume", volume)
                .put("inventory_count", inventory.size)
                .put("scan_limit", scanLimit)
                .put("probe_limit", probeLimit)
                .put("baseline_memory", baseline.json())
                .put("observed_formats", JSONArray(observed.map(ObservedFormat::json)))
                .put("individual_capacity", JSONArray(individual.map(CapacityMeasurement::json)))
                .put("mixed_pool", mixed.json())

        val resultFile = File(context.filesDir, RESULT_FILE)
        resultFile.writeText(result.toString())
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("riffMediaCodecPoolCapacity", resultFile.absolutePath)
            },
        )
    }

    private fun measureIndividualCapacity(
        observed: ObservedFormat,
        probeLimit: Int,
    ): CapacityMeasurement {
        collectGarbage()
        val before = memorySnapshot()
        val codecs = mutableListOf<MediaCodec>()
        val snapshots = mutableListOf<CountedMemory>()
        var failure: Failure? = null
        try {
            while (codecs.size < probeLimit && failure == null) {
                val codec = MediaCodec.createByCodecName(observed.key.codecName)
                try {
                    codec.configure(MediaFormat(observed.format), null, null, 0)
                    codec.start()
                    codecs += codec
                    if (codecs.size in MEMORY_CHECKPOINTS || codecs.size == probeLimit) {
                        snapshots += CountedMemory(codecs.size, memorySnapshot())
                    }
                } catch (error: Throwable) {
                    codec.release()
                    failure = Failure.from(error)
                }
            }
            return CapacityMeasurement(
                format = observed,
                reportedMaximum = observed.maxSupportedInstances,
                started = codecs.size,
                probeLimit = probeLimit,
                before = before,
                checkpoints = snapshots,
                failure = failure,
            )
        } finally {
            codecs.asReversed().forEach(::releaseCodec)
            collectGarbage()
        }
    }

    private fun measureMixedPool(
        context: Context,
        representatives: List<ObservedFormat>,
    ): MixedMeasurement {
        collectGarbage()
        val before = memorySnapshot()
        val codecs = mutableListOf<MediaCodec>()
        var failure: Failure? = null
        var decodedOutputBytes = 0L
        var completedDecodeCycles = 0
        try {
            representatives.forEach { observed ->
                repeat(PER_FORMAT_POOL_WIDTH) { instance ->
                    if (failure == null) {
                        val codec = MediaCodec.createByCodecName(observed.key.codecName)
                        try {
                            codec.configure(MediaFormat(observed.format), null, null, 0)
                            codec.start()
                            codecs += codec
                            repeat(DECODE_CYCLES) { cycle ->
                                decodedOutputBytes +=
                                    decodeWindowAndFlush(
                                        context = context,
                                        codec = codec,
                                        locator = observed.locators[(instance + cycle) % observed.locators.size],
                                        position = DECODE_POSITIONS[cycle],
                                    )
                                completedDecodeCycles += 1
                            }
                        } catch (error: Throwable) {
                            if (codec !in codecs) codec.release()
                            failure = Failure.from(error)
                        }
                    }
                }
            }
            return MixedMeasurement(
                requestedPerFormat = PER_FORMAT_POOL_WIDTH,
                formatCount = representatives.size,
                started = codecs.size,
                completedDecodeCycles = completedDecodeCycles,
                decodedOutputBytes = decodedOutputBytes,
                before = before,
                after = memorySnapshot(),
                failure = failure,
            )
        } finally {
            codecs.asReversed().forEach(::releaseCodec)
            collectGarbage()
        }
    }

    private fun decodeWindowAndFlush(
        context: Context,
        codec: MediaCodec,
        locator: String,
        position: Double,
    ): Long =
        withExtractor(context, locator) { extractor ->
            val track = extractor.audioTrack()
            val durationUs = extractor.getTrackFormat(track).getLong(MediaFormat.KEY_DURATION)
            val startUs = (durationUs * position).toLong().coerceAtMost((durationUs - DECODE_WINDOW_US).coerceAtLeast(0L))
            val stopUs = startUs + DECODE_WINDOW_US
            extractor.selectTrack(track)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val information = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputBytes = 0L
            var idleDequeues = 0
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = requireNotNull(codec.getInputBuffer(inputIndex))
                        val sampleTime = extractor.sampleTime
                        if (sampleTime < 0L || sampleTime > stopUs + DECODE_TAIL_US) {
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
                                codec.queueInputBuffer(inputIndex, 0, size, sampleTime, extractor.sampleFlags)
                                extractor.advance()
                            }
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(information, CODEC_TIMEOUT_US)
                if (outputIndex >= 0) {
                    outputBytes += information.size
                    outputEnded = information.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    idleDequeues = 0
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    idleDequeues += 1
                    require(idleDequeues < MAX_IDLE_DEQUEUES) { "Decoder made no progress" }
                }
            }
            require(outputBytes > 0L) { "Decoder returned no PCM" }
            codec.flush()
            outputBytes
        }

    private fun observeFormats(
        context: Context,
        inventory: List<CorpusItem>,
        scanLimit: Int,
    ): List<ObservedFormat> {
        val observed = linkedMapOf<DecoderKey, ObservedFormat>()
        inventory
            .sortedWith(compareBy<CorpusItem> { selectionKey(it.id) }.thenBy { it.id })
            .take(scanLimit)
            .forEach { item ->
                runCatching {
                    withExtractor(context, item.locator) { extractor ->
                        val format = extractor.getTrackFormat(extractor.audioTrack())
                        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime !in TARGET_MIMES) return@withExtractor
                        val codecName =
                            requireNotNull(MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)) {
                                "No decoder for $mime"
                            }
                        val codecInfo =
                            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.first { it.name == codecName }
                        val key = DecoderKey.from(codecName, format)
                        val existing = observed[key]
                        if (existing == null) {
                            observed[key] =
                                ObservedFormat(
                                    key = key,
                                    format = MediaFormat(format),
                                    count = 1,
                                    locators = listOf(item.locator),
                                    maxSupportedInstances = codecInfo.getCapabilitiesForType(mime).maxSupportedInstances,
                                )
                        } else {
                            observed[key] =
                                existing.copy(
                                    count = existing.count + 1,
                                    locators = (existing.locators + item.locator).distinct().take(DECODE_CYCLES),
                                )
                        }
                    }
                }
            }
        return observed.values.sortedWith(compareBy<ObservedFormat> { it.key.mime }.thenByDescending { it.count })
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
                        val durationMillis = cursor.getLong(durationColumn)
                        if (durationMillis >= MINIMUM_DURATION_MILLIS) {
                            val id = cursor.getLong(idColumn)
                            add(CorpusItem(id, ContentUris.withAppendedId(collection, id).toString()))
                        }
                    }
                }
            }.orEmpty()
    }

    private fun memorySnapshot(): MemorySnapshot {
        val memory = Debug.MemoryInfo()
        Debug.getMemoryInfo(memory)
        return MemorySnapshot(
            totalPssKb = memory.totalPss,
            nativePssKb = memory.nativePss,
            dalvikPssKb = memory.dalvikPss,
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
            javaHeapUsedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
        )
    }

    private fun collectGarbage() {
        repeat(2) {
            Runtime.getRuntime().gc()
            System.runFinalization()
        }
    }

    private fun releaseCodec(codec: MediaCodec) {
        runCatching(codec::stop)
        codec.release()
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
        (0 until trackCount).firstOrNull { index ->
            getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("Media source has no audio track")

    private data class CorpusItem(
        val id: Long,
        val locator: String,
    )

    private data class DecoderKey(
        val codecName: String,
        val mime: String,
        val sampleRate: Int,
        val channelCount: Int,
        val channelMask: Int?,
        val profile: Int?,
        val isAdts: Int?,
        val csdSha256: List<String>,
    ) {
        fun describe(): String =
            listOfNotNull(
                codecName,
                mime,
                "${sampleRate}Hz",
                "${channelCount}ch",
                channelMask?.let { "mask=$it" },
                profile?.let { "profile=$it" },
                isAdts?.let { "adts=$it" },
                csdSha256.takeIf(List<String>::isNotEmpty)?.joinToString(prefix = "csd=", separator = ","),
            ).joinToString("|")

        fun json(): JSONObject =
            JSONObject()
                .put("codec_name", codecName)
                .put("mime", mime)
                .put("sample_rate", sampleRate)
                .put("channel_count", channelCount)
                .put("channel_mask", channelMask ?: JSONObject.NULL)
                .put("profile", profile ?: JSONObject.NULL)
                .put("is_adts", isAdts ?: JSONObject.NULL)
                .put("csd_sha256", JSONArray(csdSha256))

        companion object {
            fun from(
                codecName: String,
                format: MediaFormat,
            ): DecoderKey =
                DecoderKey(
                    codecName = codecName,
                    mime = requireNotNull(format.getString(MediaFormat.KEY_MIME)),
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    channelMask = format.optionalInteger(MediaFormat.KEY_CHANNEL_MASK),
                    profile = format.optionalInteger(MediaFormat.KEY_PROFILE),
                    isAdts = format.optionalInteger(MediaFormat.KEY_IS_ADTS),
                    csdSha256 =
                        (0..3).mapNotNull { index ->
                            format.getByteBuffer("csd-$index")?.sha256()
                        },
                )
        }
    }

    private data class ObservedFormat(
        val key: DecoderKey,
        val format: MediaFormat,
        val count: Int,
        val locators: List<String>,
        val maxSupportedInstances: Int,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("key", key.json())
                .put("sample_count", count)
                .put("reported_max_supported_instances", maxSupportedInstances)
    }

    private data class MemorySnapshot(
        val totalPssKb: Int,
        val nativePssKb: Int,
        val dalvikPssKb: Int,
        val nativeHeapAllocatedBytes: Long,
        val javaHeapUsedBytes: Long,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("total_pss_kb", totalPssKb)
                .put("native_pss_kb", nativePssKb)
                .put("dalvik_pss_kb", dalvikPssKb)
                .put("native_heap_allocated_bytes", nativeHeapAllocatedBytes)
                .put("java_heap_used_bytes", javaHeapUsedBytes)
    }

    private data class CountedMemory(
        val count: Int,
        val memory: MemorySnapshot,
    ) {
        fun json(): JSONObject = JSONObject().put("count", count).put("memory", memory.json())
    }

    private data class Failure(
        val type: String,
        val message: String,
        val codecErrorCode: Int?,
        val recoverable: Boolean?,
        val transient: Boolean?,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("type", type)
                .put("message", message)
                .put("codec_error_code", codecErrorCode ?: JSONObject.NULL)
                .put("recoverable", recoverable ?: JSONObject.NULL)
                .put("transient", transient ?: JSONObject.NULL)

        companion object {
            fun from(error: Throwable): Failure {
                val codecError = error as? MediaCodec.CodecException
                return Failure(
                    type = error.javaClass.name,
                    message = error.message.orEmpty().take(MAX_ERROR_LENGTH),
                    codecErrorCode = codecError?.errorCode,
                    recoverable = codecError?.isRecoverable,
                    transient = codecError?.isTransient,
                )
            }
        }
    }

    private data class CapacityMeasurement(
        val format: ObservedFormat,
        val reportedMaximum: Int,
        val started: Int,
        val probeLimit: Int,
        val before: MemorySnapshot,
        val checkpoints: List<CountedMemory>,
        val failure: Failure?,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("key", format.key.json())
                .put("sample_count", format.count)
                .put("reported_max_supported_instances", reportedMaximum)
                .put("started", started)
                .put("probe_limit", probeLimit)
                .put("reached_probe_limit", started == probeLimit)
                .put("before", before.json())
                .put("checkpoints", JSONArray(checkpoints.map(CountedMemory::json)))
                .put("failure", failure?.json() ?: JSONObject.NULL)
    }

    private data class MixedMeasurement(
        val requestedPerFormat: Int,
        val formatCount: Int,
        val started: Int,
        val completedDecodeCycles: Int,
        val decodedOutputBytes: Long,
        val before: MemorySnapshot,
        val after: MemorySnapshot,
        val failure: Failure?,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("requested_per_format", requestedPerFormat)
                .put("format_count", formatCount)
                .put("requested_total", requestedPerFormat * formatCount)
                .put("started", started)
                .put("completed_decode_cycles", completedDecodeCycles)
                .put("decoded_output_bytes", decodedOutputBytes)
                .put("before", before.json())
                .put("after", after.json())
                .put("failure", failure?.json() ?: JSONObject.NULL)
    }

    private companion object {
        const val RESULT_FILE = "media-codec-pool-capacity-result.json"
        const val ARG_VOLUME = "riffMediaVolume"
        const val ARG_SCAN_LIMIT = "riffScanLimit"
        const val ARG_PROBE_LIMIT = "riffProbeLimit"
        const val DEFAULT_SCAN_LIMIT = 300
        const val DEFAULT_PROBE_LIMIT = 33
        const val PER_FORMAT_POOL_WIDTH = 3
        const val MIXED_POOL_WIDTH = 6
        const val MINIMUM_DURATION_MILLIS = 64_000L
        const val MAX_ERROR_LENGTH = 300
        const val DECODE_WINDOW_US = 21_000_000L
        const val DECODE_TAIL_US = 500_000L
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_IDLE_DEQUEUES = 500
        const val DECODE_CYCLES = 2
        const val SELECTION_SEED = 0x52494646434f4445L
        val TARGET_MIMES = setOf("audio/mpeg", "audio/mp4a-latm")
        val MEMORY_CHECKPOINTS = setOf(1, 3, 6, 12, 24, 32)
        val DECODE_POSITIONS = listOf(0.25, 0.50)
    }
}

private fun MediaFormat.optionalInteger(key: String): Int? = if (containsKey(key)) getInteger(key) else null

private fun ByteBuffer.sha256(): String {
    val copy = duplicate()
    val bytes = ByteArray(copy.remaining())
    copy.get(bytes)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

private fun selectionKey(id: Long): ULong {
    var value = (id xor 0x52494646434f4445L).toULong()
    value = (value xor (value shr 30)) * 0xbf58476d1ce4e5b9UL
    value = (value xor (value shr 27)) * 0x94d049bb133111ebUL
    return value xor (value shr 31)
}
