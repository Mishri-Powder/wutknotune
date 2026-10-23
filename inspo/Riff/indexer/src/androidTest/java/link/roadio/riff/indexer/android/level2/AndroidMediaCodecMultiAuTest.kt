package link.roadio.riff.indexer.android.level2

import android.content.ContentUris
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.storage.hash.NativeXxh3Factory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.max

/**
 * Tests whether HU decoders accept multiple complete access units in one legacy
 * queueInputBuffer call and produce the same PCM as N individual calls.
 */
@RunWith(AndroidJUnit4::class)
class AndroidMediaCodecMultiAuTest {
    @Test
    fun compareBatchedAndIndividualAccessUnits() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext
        val volume = configuredCorpusVolume(arguments, ARG_VOLUME)
        val scanLimit = arguments.getString(ARG_SCAN_LIMIT)?.toIntOrNull() ?: DEFAULT_SCAN_LIMIT
        val sampleLimit = arguments.getString(ARG_SAMPLE_LIMIT)?.toIntOrNull() ?: DEFAULT_SAMPLE_LIMIT
        require(scanLimit > 0 && sampleLimit > 0)
        require(volume in MediaStore.getExternalVolumeNames(context))

        val inventory = queryAudio(context, volume)
        val cohorts = observeCohorts(context, inventory, scanLimit)
        val selected =
            TARGET_MIMES.associateWith { mime ->
                requireNotNull(cohorts.filter { it.key.mime == mime }.maxByOrNull { it.items.size }) {
                    "No cohort for " + mime
                }
            }
        val blocks =
            selected.mapValues { (_, cohort) ->
                cohort.items
                    .sortedWith(compareBy<SourceItem> { selectionKey(it.id) }.thenBy { it.id })
                    .asSequence()
                    .mapNotNull { item -> runCatching { readBlock(context, item) }.getOrNull() }
                    .take(sampleLimit)
                    .toList()
                    .also {
                        require(it.size >= MINIMUM_SAMPLE_COUNT) {
                            "Too few readable samples for " + cohort.key.mime + ": " + it.size
                        }
                    }
            }

        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        val results =
            TARGET_MIMES.map { mime ->
                val cohort = selected.getValue(mime)
                val candidates =
                    codecInfos.filter { info ->
                        !info.isEncoder && mime in info.supportedTypes
                    }
                JSONObject()
                    .put("mime", mime)
                    .put("cohort", cohort.key.json())
                    .put("cohort_count", cohort.items.size)
                    .put("sample_count", blocks.getValue(mime).size)
                    .put(
                        "codecs",
                        JSONArray(
                            candidates.map { info ->
                                compareCodec(
                                    info = info,
                                    mime = mime,
                                    inputFormat = cohort.format,
                                    blocks = blocks.getValue(mime),
                                )
                            },
                        ),
                    )
            }

        val result =
            JSONObject()
                .put("event", "media_codec_multi_au")
                .put("schema_version", 2)
                .put("media_store_volume", volume)
                .put("inventory_count", inventory.size)
                .put("scan_limit", scanLimit)
                .put("sample_limit", sampleLimit)
                .put("batch_sizes", JSONArray(BATCH_SIZES))
                .put(
                    "comparison",
                    "$PRE_ROLL_ACCESS_UNITS individual pre-roll AU, then N individual AU calls " +
                        "versus the same pre-roll followed by one buffer containing the same N AU",
                ).put("larger_sizes_after_failure", "skipped per codec and MIME")
                .put("formats", JSONArray(results))

        val resultFile = File(context.filesDir, RESULT_FILE)
        resultFile.writeText(result.toString())
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("riffMediaCodecMultiAu", resultFile.absolutePath)
            },
        )
    }

    private fun compareCodec(
        info: MediaCodecInfo,
        mime: String,
        inputFormat: MediaFormat,
        blocks: List<SourceBlock>,
    ): JSONObject {
        val capabilities = info.getCapabilitiesForType(mime)
        val cases = JSONArray()
        var stopped = false
        BATCH_SIZES.forEach { batchSize ->
            if (stopped) {
                cases.put(
                    JSONObject()
                        .put("batch_size", batchSize)
                        .put("status", "skipped_after_smaller_failure"),
                )
            } else {
                val measured = compareBatchSize(info.name, inputFormat, blocks, batchSize)
                cases.put(measured)
                if (measured.getString("status") != "exact_match") stopped = true
            }
        }
        return JSONObject()
            .put("name", info.name)
            .put("canonical_name", info.canonicalName)
            .put("alias", info.isAlias)
            .put("hardware_accelerated", info.isHardwareAccelerated)
            .put("software_only", info.isSoftwareOnly)
            .put("vendor", info.isVendor)
            .put("max_supported_instances", capabilities.maxSupportedInstances)
            .put(
                "feature_multiple_frames",
                capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_MultipleFrames),
            ).put(
                "feature_frame_parsing",
                capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_FrameParsing),
            ).put("cases", cases)
    }

    private fun compareBatchSize(
        codecName: String,
        inputFormat: MediaFormat,
        blocks: List<SourceBlock>,
        batchSize: Int,
    ): JSONObject {
        val comparisons = mutableListOf<JSONObject>()
        var individualNanos = 0L
        var batchedNanos = 0L
        var exactMatches = 0
        blocks.forEachIndexed { index, block ->
            val preRoll = block.units.take(PRE_ROLL_ACCESS_UNITS)
            val units = block.units.drop(PRE_ROLL_ACCESS_UNITS).take(batchSize)
            val firstBatched = index % 2 == 1
            val comparison =
                runCatching {
                    comparePair(
                        codecName = codecName,
                        inputFormat = inputFormat,
                        preRoll = preRoll,
                        units = units,
                        firstBatched = firstBatched,
                    )
                }.fold(
                    onSuccess = { pair ->
                        individualNanos += pair.individual.elapsedNanos
                        batchedNanos += pair.batched.elapsedNanos
                        if (pair.exact) exactMatches += 1
                        pair.json(block.item.id, firstBatched)
                    },
                    onFailure = { error ->
                        JSONObject()
                            .put("media_store_id", block.item.id)
                            .put("exact", false)
                            .put("error_type", error.javaClass.name)
                            .put("error_message", error.message.orEmpty().take(MAX_ERROR_LENGTH))
                    },
                )
            comparisons += comparison
        }
        val status = if (exactMatches == blocks.size) "exact_match" else "failed"
        return JSONObject()
            .put("batch_size", batchSize)
            .put("status", status)
            .put("files", blocks.size)
            .put("exact_matches", exactMatches)
            .put(
                "individual_mean_ms",
                if (individualNanos > 0L) {
                    individualNanos.toDouble() / blocks.size / NANOS_PER_MILLISECOND
                } else {
                    JSONObject.NULL
                },
            ).put(
                "batched_mean_ms",
                if (batchedNanos > 0L) {
                    batchedNanos.toDouble() / blocks.size / NANOS_PER_MILLISECOND
                } else {
                    JSONObject.NULL
                },
            ).put("comparisons", JSONArray(comparisons))
    }

    private fun comparePair(
        codecName: String,
        inputFormat: MediaFormat,
        preRoll: List<AccessUnit>,
        units: List<AccessUnit>,
        firstBatched: Boolean,
    ): PairMeasurement {
        val configuredFormat =
            MediaFormat(inputFormat).apply {
                setInteger(
                    MediaFormat.KEY_MAX_INPUT_SIZE,
                    max(
                        multiAuOptionalInteger(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 0,
                        REQUESTED_INPUT_CAPACITY,
                    ),
                )
            }
        val individualChunks = preRoll.map(AccessUnit::chunk) + units.map(AccessUnit::chunk)
        val batchedChunks = preRoll.map(AccessUnit::chunk) + batch(units)
        val first =
            if (firstBatched) {
                decodeOnce(codecName, configuredFormat, batchedChunks)
            } else {
                decodeOnce(codecName, configuredFormat, individualChunks)
            }
        val second =
            if (firstBatched) {
                decodeOnce(codecName, configuredFormat, individualChunks)
            } else {
                decodeOnce(codecName, configuredFormat, batchedChunks)
            }
        val individual = if (firstBatched) second else first
        val batched = if (firstBatched) first else second
        return PairMeasurement(individual, batched)
    }

    private fun decodeOnce(
        codecName: String,
        configuredFormat: MediaFormat,
        chunks: List<InputChunk>,
    ): DecodeMeasurement {
        val codec = MediaCodec.createByCodecName(codecName)
        try {
            codec.configure(configuredFormat, null, null, 0)
            codec.start()
            return decode(codec, chunks)
        } finally {
            runCatching(codec::stop)
            codec.release()
        }
    }

    private fun decode(
        codec: MediaCodec,
        chunks: List<InputChunk>,
    ): DecodeMeasurement =
        NativeXxh3Factory.create().use { digest ->
            val digestScratch = ByteArray(64 * 1024)
            val info = MediaCodec.BufferInfo()
            val started = SystemClock.elapsedRealtimeNanos()
            var nextChunk = 0
            var eosQueued = false
            var outputEnded = false
            var outputBytes = 0L
            var outputBuffers = 0
            var inputCapacity = 0
            var idleIterations = 0
            var outputFormat: MediaFormat? = null
            while (!outputEnded) {
                var progressed = false
                if (!eosQueued) {
                    val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = requireNotNull(codec.getInputBuffer(inputIndex))
                        inputCapacity = max(inputCapacity, input.capacity())
                        if (nextChunk < chunks.size) {
                            val chunk = chunks[nextChunk]
                            require(chunk.data.size <= input.capacity()) {
                                "Input batch " + chunk.data.size + " exceeds capacity " + input.capacity()
                            }
                            input.clear()
                            input.put(chunk.data)
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                chunk.data.size,
                                chunk.presentationTimeUs,
                                chunk.flags,
                            )
                            nextChunk += 1
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                chunks.last().presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            eosQueued = true
                        }
                        progressed = true
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = MediaFormat(codec.outputFormat)
                        progressed = true
                    }

                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        progressed = true
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        Unit
                    }

                    else -> {
                        if (outputIndex >= 0) {
                            try {
                                if (info.size > 0) {
                                    val output = requireNotNull(codec.getOutputBuffer(outputIndex)).duplicate()
                                    output.position(info.offset)
                                    output.limit(info.offset + info.size)
                                    while (output.hasRemaining()) {
                                        val count = minOf(output.remaining(), digestScratch.size)
                                        output.get(digestScratch, 0, count)
                                        digest.update(digestScratch, 0, count)
                                    }
                                    outputBytes += info.size
                                    outputBuffers += 1
                                }
                                outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            } finally {
                                codec.releaseOutputBuffer(outputIndex, false)
                            }
                            progressed = true
                        }
                    }
                }
                idleIterations = if (progressed) 0 else idleIterations + 1
                require(idleIterations < MAX_IDLE_ITERATIONS) { "Decoder made no progress" }
            }
            require(outputBytes > 0L) { "Decoder returned no PCM" }
            val format = outputFormat ?: MediaFormat(codec.outputFormat)
            DecodeMeasurement(
                elapsedNanos = SystemClock.elapsedRealtimeNanos() - started,
                outputBytes = outputBytes,
                outputBuffers = outputBuffers,
                xxh3 =
                    digest
                        .digest()
                        .toULong()
                        .toString(16)
                        .padStart(16, '0'),
                format = pcmSignature(format),
                inputCapacity = inputCapacity,
            )
        }

    private fun batch(units: List<AccessUnit>): InputChunk {
        val bytes = ByteArray(units.sumOf { it.data.size })
        var offset = 0
        units.forEach { unit ->
            unit.data.copyInto(bytes, offset)
            offset += unit.data.size
        }
        return InputChunk(
            data = bytes,
            presentationTimeUs = units.first().presentationTimeUs,
            flags = units.first().flags,
        )
    }

    private fun readBlock(
        context: Context,
        item: SourceItem,
    ): SourceBlock =
        withExtractor(context, item.locator) { extractor ->
            val track = extractor.audioTrack()
            extractor.selectTrack(track)
            val logicalStartUs =
                ((item.durationUs * 0.25).toLong() - 5_000_000L)
                    .coerceIn(0L, max(0L, item.durationUs - 10_000_000L))
            extractor.seekTo(
                max(0L, logicalStartUs - PRE_ROLL_US),
                MediaExtractor.SEEK_TO_PREVIOUS_SYNC,
            )
            val buffer = ByteBuffer.allocateDirect(MAX_ACCESS_UNIT_BYTES)
            val units =
                buildList {
                    while (size < PRE_ROLL_ACCESS_UNITS + MAX_BATCH_SIZE) {
                        val timestamp = extractor.sampleTime
                        require(timestamp >= 0L) { "Unexpected end of source" }
                        buffer.clear()
                        val byteCount = extractor.readSampleData(buffer, 0)
                        require(byteCount in 1..MAX_ACCESS_UNIT_BYTES) {
                            "Invalid AU byte count: " + byteCount
                        }
                        val bytes = ByteArray(byteCount)
                        buffer.position(0)
                        buffer.get(bytes)
                        add(
                            AccessUnit(
                                data = bytes,
                                presentationTimeUs = timestamp,
                                flags = extractor.sampleFlags,
                            ),
                        )
                        require(extractor.advance() || size == PRE_ROLL_ACCESS_UNITS + MAX_BATCH_SIZE) {
                            "Unexpected end of source"
                        }
                    }
                }
            SourceBlock(item, units)
        }

    private fun observeCohorts(
        context: Context,
        inventory: List<SourceItem>,
        scanLimit: Int,
    ): List<ObservedCohort> {
        val observed = linkedMapOf<SourceFormatKey, ObservedCohort>()
        inventory
            .sortedWith(compareBy<SourceItem> { selectionKey(it.id) }.thenBy { it.id })
            .take(scanLimit)
            .forEach { item ->
                runCatching {
                    withExtractor(context, item.locator) { extractor ->
                        val format = extractor.getTrackFormat(extractor.audioTrack())
                        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime !in TARGET_MIMES) return@withExtractor
                        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                        if (durationUs < MINIMUM_DURATION_US) return@withExtractor
                        val key = SourceFormatKey.from(format)
                        val measured = item.copy(durationUs = durationUs)
                        val existing = observed[key]
                        if (existing == null) {
                            observed[key] =
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
        return observed.values.toList()
    }

    private fun queryAudio(
        context: Context,
        volume: String,
    ): List<SourceItem> {
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
                                SourceItem(
                                    id = id,
                                    locator = ContentUris.withAppendedId(collection, id).toString(),
                                    durationUs = durationUs,
                                ),
                            )
                        }
                    }
                }
            }.orEmpty()
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

    private fun pcmSignature(format: MediaFormat): String =
        listOf(
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            format.multiAuOptionalInteger(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT,
        ).joinToString(":")

    private data class PairMeasurement(
        val individual: DecodeMeasurement,
        val batched: DecodeMeasurement,
    ) {
        val exact: Boolean
            get() =
                individual.outputBytes == batched.outputBytes &&
                    individual.xxh3 == batched.xxh3 &&
                    individual.format == batched.format

        fun json(
            mediaStoreId: Long,
            firstBatched: Boolean,
        ): JSONObject =
            JSONObject()
                .put("media_store_id", mediaStoreId)
                .put("first_batched", firstBatched)
                .put("exact", exact)
                .put("individual", individual.json())
                .put("batched", batched.json())
    }

    private data class DecodeMeasurement(
        val elapsedNanos: Long,
        val outputBytes: Long,
        val outputBuffers: Int,
        val xxh3: String,
        val format: String,
        val inputCapacity: Int,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("elapsed_ns", elapsedNanos)
                .put("output_bytes", outputBytes)
                .put("output_buffers", outputBuffers)
                .put("xxh3", xxh3)
                .put("pcm_format", format)
                .put("input_capacity", inputCapacity)
    }

    private data class AccessUnit(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
    ) {
        fun chunk(): InputChunk = InputChunk(data, presentationTimeUs, flags)
    }

    private data class InputChunk(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
    )

    private data class SourceItem(
        val id: Long,
        val locator: String,
        val durationUs: Long,
    )

    private data class SourceBlock(
        val item: SourceItem,
        val units: List<AccessUnit>,
    )

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
                    channelMask = format.multiAuOptionalInteger(MediaFormat.KEY_CHANNEL_MASK),
                    profile = format.multiAuOptionalInteger(MediaFormat.KEY_PROFILE),
                    isAdts = format.multiAuOptionalInteger(MediaFormat.KEY_IS_ADTS),
                    csdSha256 =
                        (0..3).mapNotNull { index ->
                            format.getByteBuffer("csd-" + index)?.multiAuSha256()
                        },
                )
        }
    }

    private data class ObservedCohort(
        val key: SourceFormatKey,
        val format: MediaFormat,
        val items: MutableList<SourceItem>,
    )

    private fun selectionKey(id: Long): ULong {
        var value = (id xor SELECTION_SEED).toULong()
        value = (value xor (value shr 30)) * 0xbf58476d1ce4e5b9UL
        value = (value xor (value shr 27)) * 0x94d049bb133111ebUL
        return value xor (value shr 31)
    }

    private companion object {
        const val RESULT_FILE = "media-codec-multi-au-result.json"
        const val ARG_VOLUME = "riffMediaVolume"
        const val ARG_SCAN_LIMIT = "riffScanLimit"
        const val ARG_SAMPLE_LIMIT = "riffSampleLimit"
        const val DEFAULT_SCAN_LIMIT = 500
        const val DEFAULT_SAMPLE_LIMIT = 24
        const val MINIMUM_SAMPLE_COUNT = 8
        const val MINIMUM_DURATION_US = 64_000_000L
        const val PRE_ROLL_US = 1_000_000L
        const val MAX_ACCESS_UNIT_BYTES = 1 * 1024 * 1024
        const val REQUESTED_INPUT_CAPACITY = 256 * 1024
        const val INPUT_TIMEOUT_US = 1_000L
        const val OUTPUT_TIMEOUT_US = 5_000L
        const val MAX_IDLE_ITERATIONS = 1_000
        const val MAX_ERROR_LENGTH = 400
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val SELECTION_SEED = 0x524946464d41554cL
        const val MAX_BATCH_SIZE = 16
        const val PRE_ROLL_ACCESS_UNITS = 8
        val BATCH_SIZES = listOf(2, 4, 8, 16)
        val TARGET_MIMES = listOf("audio/mpeg", "audio/mp4a-latm")
    }
}

private fun MediaFormat.multiAuOptionalInteger(key: String): Int? = if (containsKey(key)) getInteger(key) else null

private fun ByteBuffer.multiAuSha256(): String {
    val copy = duplicate()
    val bytes = ByteArray(copy.remaining())
    copy.get(bytes)
    return MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
