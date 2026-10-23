package link.roadio.riff.indexer.android.level2

import android.content.ContentUris
import android.content.Context
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
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Reads compressed access units without creating a decoder.
 *
 * Every logical window uses an independent extractor, matching production Level 2
 * window sessions. Files are sequential and up to three windows within one file are
 * concurrent. Plans run once in fixed order without explicit cache invalidation.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAccessUnitReadBenchmarkTest {
    @Test
    fun benchmarkConfiguredMediaStoreVolume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext
        val volume = configuredCorpusVolume(arguments, ARG_VOLUME)
        val limit = arguments.getString(ARG_LIMIT)?.toIntOrNull() ?: DEFAULT_SAMPLE_LIMIT
        val windowParallelism =
            arguments.getString(ARG_WINDOW_PARALLELISM)?.toIntOrNull() ?: DEFAULT_WINDOW_WORKERS
        require(limit > 0)
        require(windowParallelism in 1..MAXIMUM_WINDOW_WORKERS)

        val availableVolumes = MediaStore.getExternalVolumeNames(context)
        require(volume in availableVolumes) {
            "MediaStore volume is unavailable: " + volume + "; available=" + availableVolumes
        }
        val inventoryStarted = SystemClock.elapsedRealtimeNanos()
        val inventory = queryAudio(context, volume)
        val eligible = inventory.filter { it.durationMillis >= MINIMUM_DURATION_MILLIS }
        require(eligible.size >= limit) {
            "Too few eligible media items: " + eligible.size + "; requested=" + limit
        }
        val samples =
            eligible
                .sortedWith(compareBy<CorpusItem> { selectionKey(it.id) }.thenBy { it.id })
                .take(limit)
        val inventoryNanos = SystemClock.elapsedRealtimeNanos() - inventoryStarted

        val passes =
            PLANS.mapIndexed { index, plan ->
                Log.i(
                    TAG,
                    "pass_start index=" + (index + 1) +
                        " name=" + plan.name +
                        " files=" + samples.size,
                )
                measurePass(context, plan, samples, windowParallelism).also { measured ->
                    Log.i(
                        TAG,
                        "pass_complete index=" + (index + 1) +
                            " name=" + plan.name +
                            " wall_ms=" + measured.wallNanos / NANOS_PER_MILLISECOND +
                            " success=" + measured.successes +
                            " failure=" + measured.failures +
                            " files_per_second=" + measured.attemptedFilesPerSecond,
                    )
                }
            }

        val result =
            JSONObject()
                .put("event", "access_unit_read_benchmark")
                .put("schema_version", 1)
                .put("media_store_volume", volume)
                .put("available_volumes", JSONArray(availableVolumes.sorted()))
                .put("inventory_count", inventory.size)
                .put("eligible_count", eligible.size)
                .put("sample_count", samples.size)
                .put("minimum_duration_ms", MINIMUM_DURATION_MILLIS)
                .put("selection", "splitmix64(MediaStore._ID xor fixed seed), first N")
                .put("selection_seed_hex", SELECTION_SEED.toULong().toString(16))
                .put("inventory_elapsed_ns", inventoryNanos)
                .put("cache_invalidation", "none")
                .put("pass_order", JSONArray(PLANS.map(Plan::name)))
                .put("window_parallelism", windowParallelism)
                .put("extractor_session", "independent per logical window")
                .put("decoder_created", false)
                .put("read_tail_us", READ_TAIL_US)
                .put("samples", JSONArray(samples.map(CorpusItem::json)))
                .put("passes", JSONArray(passes.map(MeasuredPass::json)))

        val resultFile = File(context.filesDir, RESULT_FILE)
        resultFile.writeText(result.toString())
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("riffAccessUnitBenchmark", compactSummary(result).toString())
                putString("riffAccessUnitBenchmarkResult", resultFile.absolutePath)
            },
        )
    }

    private fun measurePass(
        context: Context,
        plan: Plan,
        samples: List<CorpusItem>,
        windowParallelism: Int,
    ): MeasuredPass {
        val executor = Executors.newFixedThreadPool(windowParallelism)
        val started = SystemClock.elapsedRealtimeNanos()
        val items =
            try {
                samples.mapIndexed { index, item ->
                    if (index % PROGRESS_INTERVAL == 0) {
                        Log.i(
                            TAG,
                            "pass_progress name=" + plan.name +
                                " completed=" + index +
                                " total=" + samples.size,
                        )
                    }
                    measureFile(context, executor, plan, item)
                }
            } finally {
                executor.shutdownNow()
            }
        return MeasuredPass(plan, SystemClock.elapsedRealtimeNanos() - started, items)
    }

    private fun measureFile(
        context: Context,
        executor: ExecutorService,
        plan: Plan,
        item: CorpusItem,
    ): FileMeasurement {
        val started = SystemClock.elapsedRealtimeNanos()
        return runCatching {
            val durationUs = probeDurationUs(context, item.locator)
            require(durationUs >= MINIMUM_DURATION_MILLIS * MICROS_PER_MILLISECOND) {
                "Extractor duration is too short: " + durationUs
            }
            val starts = plan.positions.map { position -> logicalStartUs(durationUs, position) }
            val futures =
                starts.mapIndexed { index, startUs ->
                    executor.submit(
                        Callable {
                            readWindow(context, item.locator, index, startUs, plan.windowUs)
                        },
                    )
                }
            val windows = futures.map { it.get() }
            FileMeasurement(
                item = item,
                elapsedNanos = SystemClock.elapsedRealtimeNanos() - started,
                extractorDurationUs = durationUs,
                windows = windows,
            )
        }.getOrElse { error ->
            FileMeasurement(
                item = item,
                elapsedNanos = SystemClock.elapsedRealtimeNanos() - started,
                errorType = error.javaClass.simpleName,
                errorMessage = error.message.orEmpty().take(MAX_ERROR_LENGTH),
            )
        }
    }

    private fun probeDurationUs(
        context: Context,
        locator: String,
    ): Long =
        withExtractor(context, locator) { extractor ->
            extractor.getTrackFormat(extractor.audioTrack()).getLong(MediaFormat.KEY_DURATION)
        }

    private fun readWindow(
        context: Context,
        locator: String,
        index: Int,
        startUs: Long,
        windowUs: Long,
    ): WindowMeasurement {
        val extractor = MediaExtractor()
        val dataSourceStarted = SystemClock.elapsedRealtimeNanos()
        try {
            extractor.setDataSource(context, android.net.Uri.parse(locator), emptyMap())
            val dataSourceElapsedNanos = SystemClock.elapsedRealtimeNanos() - dataSourceStarted
            val started = SystemClock.elapsedRealtimeNanos()
            val track = extractor.audioTrack()
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val bufferSize =
                format
                    .getIntegerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                    ?.coerceAtLeast(MINIMUM_BUFFER_BYTES)
                    ?: DEFAULT_BUFFER_BYTES
            require(bufferSize <= MAXIMUM_BUFFER_BYTES) {
                "Declared input buffer is too large: " + bufferSize
            }
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            extractor.selectTrack(track)
            val seekStarted = SystemClock.elapsedRealtimeNanos()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val seekElapsedNanos = SystemClock.elapsedRealtimeNanos() - seekStarted
            val stopUs = startUs + windowUs + READ_TAIL_US
            var firstPresentationUs: Long? = null
            var lastPresentationUs: Long? = null
            var accessUnits = 0
            var bytes = 0L
            var maximumAccessUnitBytes = 0
            var reachedEndOfStream = false
            val readStarted = SystemClock.elapsedRealtimeNanos()
            while (true) {
                val presentationUs = extractor.sampleTime
                if (presentationUs < 0L) {
                    reachedEndOfStream = true
                    break
                }
                if (presentationUs > stopUs) break
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    reachedEndOfStream = true
                    break
                }
                if (firstPresentationUs == null) firstPresentationUs = presentationUs
                lastPresentationUs = presentationUs
                accessUnits += 1
                bytes += size
                maximumAccessUnitBytes = max(maximumAccessUnitBytes, size)
                if (!extractor.advance()) {
                    reachedEndOfStream = true
                    break
                }
            }
            val readElapsedNanos = SystemClock.elapsedRealtimeNanos() - readStarted
            val first = requireNotNull(firstPresentationUs) { "Extractor returned no access units" }
            val last = requireNotNull(lastPresentationUs)
            require(reachedEndOfStream || last >= startUs + windowUs) {
                "Access units did not cover the interval; last=" +
                    last +
                    " target=" +
                    (startUs + windowUs)
            }
            return WindowMeasurement(
                index = index,
                requestedStartUs = startUs,
                requestedWindowUs = windowUs,
                firstPresentationUs = first,
                lastPresentationUs = last,
                accessUnits = accessUnits,
                bytes = bytes,
                maximumAccessUnitBytes = maximumAccessUnitBytes,
                mimeType = mime,
                reachedEndOfStream = reachedEndOfStream,
                dataSourceElapsedNanos = dataSourceElapsedNanos,
                seekElapsedNanos = seekElapsedNanos,
                readElapsedNanos = readElapsedNanos,
                elapsedNanos = SystemClock.elapsedRealtimeNanos() - started,
            )
        } finally {
            extractor.release()
        }
    }

    private fun queryAudio(
        context: Context,
        volume: String,
    ): List<CorpusItem> {
        val collection = MediaStore.Audio.Media.getContentUri(volume)
        val projection =
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
            )
        return context.contentResolver
            .query(collection, projection, null, null, null)
            ?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val path = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                buildList {
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(id)
                        val displayName = cursor.getString(name).orEmpty()
                        add(
                            CorpusItem(
                                id = mediaId,
                                locator = ContentUris.withAppendedId(collection, mediaId).toString(),
                                relativePath = cursor.getString(path).orEmpty() + displayName,
                                extension = displayName.substringAfterLast('.', "").lowercase(),
                                sizeBytes = cursor.getLong(size),
                                durationMillis = cursor.getLong(duration),
                                mimeType = cursor.getString(mime).orEmpty(),
                            ),
                        )
                    }
                }
            }.orEmpty()
    }

    private fun logicalStartUs(
        durationUs: Long,
        position: Double,
    ): Long {
        val latestStart = max(0L, durationUs - AI_EXCERPT_US)
        return (durationUs * position - AI_EXCERPT_US / 2.0)
            .roundToLong()
            .coerceIn(0L, latestStart)
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

    private fun MediaFormat.getIntegerOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    private fun compactSummary(result: JSONObject): JSONObject =
        JSONObject()
            .put("event", result.getString("event"))
            .put("sample_count", result.getInt("sample_count"))
            .put("cache_invalidation", result.getString("cache_invalidation"))
            .put(
                "passes",
                JSONArray(
                    (0 until result.getJSONArray("passes").length()).map { index ->
                        val pass = result.getJSONArray("passes").getJSONObject(index)
                        JSONObject()
                            .put("name", pass.getString("name"))
                            .put(
                                "files_per_second",
                                pass.getDouble("attempted_files_per_second"),
                            ).put("successes", pass.getInt("successes"))
                            .put("failures", pass.getInt("failures"))
                    },
                ),
            )

    private data class Plan(
        val name: String,
        val positions: List<Double>,
        val windowUs: Long,
    )

    private data class CorpusItem(
        val id: Long,
        val locator: String,
        val relativePath: String,
        val extension: String,
        val sizeBytes: Long,
        val durationMillis: Long,
        val mimeType: String,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("media_store_id", id)
                .put("relative_path", relativePath)
                .put("extension", extension)
                .put("size_bytes", sizeBytes)
                .put("duration_ms", durationMillis)
                .put("mime_type", mimeType)
    }

    private data class WindowMeasurement(
        val index: Int,
        val requestedStartUs: Long,
        val requestedWindowUs: Long,
        val firstPresentationUs: Long,
        val lastPresentationUs: Long,
        val accessUnits: Int,
        val bytes: Long,
        val maximumAccessUnitBytes: Int,
        val mimeType: String,
        val reachedEndOfStream: Boolean,
        val dataSourceElapsedNanos: Long,
        val seekElapsedNanos: Long,
        val readElapsedNanos: Long,
        val elapsedNanos: Long,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("index", index)
                .put("requested_start_us", requestedStartUs)
                .put("requested_window_us", requestedWindowUs)
                .put("first_presentation_us", firstPresentationUs)
                .put("last_presentation_us", lastPresentationUs)
                .put("access_units", accessUnits)
                .put("bytes", bytes)
                .put("maximum_access_unit_bytes", maximumAccessUnitBytes)
                .put("mime_type", mimeType)
                .put("reached_end_of_stream", reachedEndOfStream)
                .put("data_source_elapsed_ns", dataSourceElapsedNanos)
                .put("seek_elapsed_ns", seekElapsedNanos)
                .put("read_elapsed_ns", readElapsedNanos)
                .put("elapsed_ns", elapsedNanos)
    }

    private data class FileMeasurement(
        val item: CorpusItem,
        val elapsedNanos: Long,
        val extractorDurationUs: Long? = null,
        val windows: List<WindowMeasurement> = emptyList(),
        val errorType: String? = null,
        val errorMessage: String? = null,
    ) {
        val successful: Boolean
            get() = errorType == null && windows.isNotEmpty()

        fun json(): JSONObject =
            JSONObject()
                .put("media_store_id", item.id)
                .put("relative_path", item.relativePath)
                .put("elapsed_ns", elapsedNanos)
                .put("extractor_duration_us", extractorDurationUs ?: JSONObject.NULL)
                .put("access_units", windows.sumOf(WindowMeasurement::accessUnits))
                .put("bytes", windows.sumOf(WindowMeasurement::bytes))
                .put("windows", JSONArray(windows.map(WindowMeasurement::json)))
                .put("error_type", errorType ?: JSONObject.NULL)
                .put("error_message", errorMessage ?: JSONObject.NULL)
    }

    private data class MeasuredPass(
        val plan: Plan,
        val wallNanos: Long,
        val items: List<FileMeasurement>,
    ) {
        val successes: Int
            get() = items.count(FileMeasurement::successful)
        val failures: Int
            get() = items.size - successes
        val attemptedFilesPerSecond: Double
            get() = items.size * NANOS_PER_SECOND.toDouble() / wallNanos

        fun json(): JSONObject {
            val successful = items.filter(FileMeasurement::successful)
            return JSONObject()
                .put("name", plan.name)
                .put("positions", JSONArray(plan.positions))
                .put("window_us", plan.windowUs)
                .put("wall_ns", wallNanos)
                .put("attempts", items.size)
                .put("successes", successes)
                .put("failures", failures)
                .put("attempted_files_per_second", attemptedFilesPerSecond)
                .put(
                    "successful_files_per_second",
                    successes * NANOS_PER_SECOND.toDouble() / wallNanos,
                ).put(
                    "total_access_units",
                    successful.sumOf { file ->
                        file.windows.sumOf(WindowMeasurement::accessUnits)
                    },
                ).put(
                    "total_bytes",
                    successful.sumOf { file -> file.windows.sumOf(WindowMeasurement::bytes) },
                ).put(
                    "file_elapsed_ns",
                    distribution(successful.map(FileMeasurement::elapsedNanos)),
                ).put("items", JSONArray(items.map(FileMeasurement::json)))
        }
    }

    private companion object {
        const val TAG = "RiffAuReadBench"
        const val RESULT_FILE = "access-unit-read-benchmark-result.json"
        const val ARG_VOLUME = "riffMediaVolume"
        const val ARG_LIMIT = "riffFileLimit"
        const val ARG_WINDOW_PARALLELISM = "riffWindowParallelism"
        const val DEFAULT_SAMPLE_LIMIT = 300
        const val PROGRESS_INTERVAL = 25
        const val DEFAULT_WINDOW_WORKERS = 3
        const val MAXIMUM_WINDOW_WORKERS = 3
        const val MAX_ERROR_LENGTH = 240
        const val MINIMUM_DURATION_MILLIS = 64_000L
        const val AI_EXCERPT_US = 10_000_000L
        const val READ_TAIL_US = 500_000L
        const val MICROS_PER_MILLISECOND = 1_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val MINIMUM_BUFFER_BYTES = 64 * 1024
        const val DEFAULT_BUFFER_BYTES = 1024 * 1024
        const val MAXIMUM_BUFFER_BYTES = 16 * 1024 * 1024
        const val SELECTION_SEED = 0x5249464641553231L
        val PLANS =
            listOf(
                Plan("baseline_3x10s_25_50_75", listOf(0.25, 0.50, 0.75), 10_000_000L),
                Plan("three_3x21s_25_50_75", listOf(0.25, 0.50, 0.75), 21_000_000L),
                Plan("two_2x21s_33_66", listOf(1.0 / 3.0, 2.0 / 3.0), 21_000_000L),
            )
    }
}

private fun selectionKey(id: Long): ULong {
    var value = (id xor 0x5249464641553231L).toULong()
    value = (value xor (value shr 30)) * 0xbf58476d1ce4e5b9UL
    value = (value xor (value shr 27)) * 0x94d049bb133111ebUL
    return value xor (value shr 31)
}

private fun distribution(values: List<Long>): JSONObject {
    if (values.isEmpty()) return JSONObject()
    val sorted = values.sorted()

    fun percentile(fraction: Double): Long = sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)]

    return JSONObject()
        .put("minimum", sorted.first())
        .put("p50", percentile(0.50))
        .put("p90", percentile(0.90))
        .put("p95", percentile(0.95))
        .put("p99", percentile(0.99))
        .put("maximum", sorted.last())
        .put("mean", sorted.average())
}
