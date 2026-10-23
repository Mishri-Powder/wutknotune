package link.roadio.riff.indexer.android.media

import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.media.MediaIdBenchmarkSample
import link.roadio.riff.indexer.core.model.SourceMedia
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class AndroidMediaIdBenchmarkTest {
    private val cancellation =
        object : CancellationSignal {
            override val cancelled = false

            override fun ensureAvailable() = Unit
        }

    @Test
    fun benchmarkConfiguredMedia() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val volume = arguments.getString("riffMediaVolume")
        val result =
            if (volume == null) {
                benchmarkSingle(arguments.getString("riffMediaLocator") ?: correctnessFixture(instrumentation.targetContext))
            } else {
                benchmarkCorpus(
                    instrumentation.targetContext,
                    volume,
                    arguments.getString("riffFileLimit")?.toIntOrNull() ?: 250,
                )
            }
        check(result.getInt("successes") > 0) { "Media.ID benchmark produced no successful samples" }
        val resultFile = File(instrumentation.targetContext.filesDir, RESULT_FILE)
        resultFile.writeText(result.toString())
        val summary = compactSummary(result)
        Log.i(DebugAndroidMediaIdBenchmarkRecorder.LOG_TAG, summary.toString())
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("riffMediaIdBenchmark", summary.toString())
                putString("riffMediaIdBenchmarkResult", resultFile.absolutePath)
            },
        )
    }

    private fun correctnessFixture(context: Context): String {
        // A valid one-second PCM WAV keeps default device tests independent of installed media.
        // https://wiki.271.pics/Riff/LATEST/engineering-verification
        // AI-INTENT: This is a correctness smoke input, never corpus or performance acceptance evidence.
        val sampleRate = 44100
        val payloadBytes = sampleRate * 2
        val bytes = ByteBuffer.allocate(44 + payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".encodeToByteArray()).putInt(36 + payloadBytes)
        bytes.put("WAVEfmt ".encodeToByteArray()).putInt(16)
        bytes
            .putShort(1)
            .putShort(1)
            .putInt(sampleRate)
            .putInt(sampleRate * 2)
        bytes.putShort(2).putShort(16)
        bytes.put("data".encodeToByteArray()).putInt(payloadBytes)
        val fixture = File(context.filesDir, "media-id-correctness-fixture.wav")
        fixture.writeBytes(bytes.array())
        return fixture.absolutePath
    }

    private fun benchmarkSingle(locator: String): JSONObject {
        val arguments = InstrumentationRegistry.getArguments()
        val iterations = arguments.getString("riffIterations")?.toIntOrNull() ?: 10
        val warmups = arguments.getString("riffWarmups")?.toIntOrNull() ?: 2
        require(iterations > 0 && warmups >= 0)
        val item =
            CorpusItem(
                locator = locator,
                relativePath = locator.substringAfterLast('/'),
                extension = locator.substringAfterLast('.', "").lowercase(),
                sizeBytes = File(locator).takeIf(File::exists)?.length(),
                devicePath = locator,
            )
        return benchmarkResult(List(iterations) { item }, warmups)
    }

    private fun benchmarkCorpus(
        context: Context,
        volume: String,
        limit: Int,
    ): JSONObject {
        require(limit > 0)
        val availableVolumes = MediaStore.getExternalVolumeNames(context)
        require(volume in availableVolumes) {
            "MediaStore volume $volume is unavailable; available=$availableVolumes"
        }
        val allItems = queryAudio(context, volume)
        require(allItems.isNotEmpty()) { "No MediaStore audio on volume $volume" }
        val selected = evenlySpaced(allItems.sortedWith(ITEM_ORDER), minOf(limit, allItems.size))
        return benchmarkResult(selected, warmups = 1)
            .put("mediaStoreVolume", volume)
            .put("availableMediaCount", allItems.size)
            .put("selectionOrder", "extension,size,relativePath; evenly spaced")
    }

    private fun benchmarkResult(
        items: List<CorpusItem>,
        warmups: Int,
    ): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val current =
            benchmarkVersion(items, warmups) { recorder ->
                val generator = debugAndroidMediaIdV32Generator(context, recorder)
                Generator(generator::generateHigh)
            }
        return resultJson(items, current)
            .put("event", "media_id.benchmark")
            .put("platform", "android")
            .put("requestedFiles", items.size)
            .put("warmupsPerVersion", warmups)
            .put("extensions", JSONObject(items.groupingBy(CorpusItem::extension).eachCount()))
            .put("identityVersion", "MDIA-ID-V3.2")
    }

    private fun benchmarkVersion(
        items: List<CorpusItem>,
        warmups: Int,
        factory: (DebugAndroidMediaIdBenchmarkRecorder) -> Generator,
    ): List<MediaIdBenchmarkSample> {
        val warmupRecorder = DebugAndroidMediaIdBenchmarkRecorder(maxOf(1, warmups))
        val warmupGenerator = factory(warmupRecorder)
        repeat(warmups) {
            val item = items[it % items.size]
            runCatching { warmupGenerator.generate(item.source(), cancellation) }
        }
        val recorder = DebugAndroidMediaIdBenchmarkRecorder(items.size)
        val generator = factory(recorder)
        items.forEach { item -> runCatching { generator.generate(item.source(), cancellation) } }
        return recorder.snapshot()
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
                MediaStore.Audio.Media.DATA,
            )
        return context.contentResolver
            .query(collection, projection, null, null, null)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn)
                        val extension = name.substringAfterLast('.', "").lowercase()
                        add(
                            CorpusItem(
                                locator = ContentUris.withAppendedId(collection, cursor.getLong(idColumn)).toString(),
                                relativePath = cursor.getString(pathColumn).orEmpty() + name,
                                extension = extension,
                                sizeBytes = cursor.getLong(sizeColumn),
                                devicePath = dataColumn.takeIf { it >= 0 }?.let(cursor::getString),
                            ),
                        )
                    }
                }
            }.orEmpty()
    }

    private fun resultJson(
        items: List<CorpusItem>,
        samples: List<MediaIdBenchmarkSample>,
    ): JSONObject {
        val successful = samples.filter(MediaIdBenchmarkSample::success)
        val byPath = items.associateBy(CorpusItem::relativePath)
        val phaseNames = successful.flatMap { it.phasesElapsedNanos.keys }.toSortedSet()
        val phases =
            JSONObject().apply {
                phaseNames.forEach { phase ->
                    put(phase, successful.sumOf { it.phasesElapsedNanos[phase] ?: 0L })
                }
            }
        val results =
            samples.map { sample ->
                val item = byPath[sample.source]
                JSONObject()
                    .put("source", sample.source)
                    .put("devicePath", item?.devicePath)
                    .put("extension", item?.extension)
                    .put("sizeBytes", item?.sizeBytes)
                    .put("success", sample.success)
                    .put("elapsedNanos", sample.elapsedNanos)
                    .put("sampleCount", sample.sampleCount?.toString())
                    .put("selectedOccurrences", sample.selectedOccurrences)
                    .put("selectedPayloadBytes", sample.selectedPayloadBytes)
                    .put("digest", sample.digest?.toUnsignedHex())
                    .put("errorCode", sample.errorCode)
                    .put("mode", sample.mode)
                    .put("terminationReason", sample.terminationReason)
                    .put("phasesElapsedNanos", JSONObject(sample.phasesElapsedNanos))
            }
        return JSONObject()
            .put("attempts", samples.size)
            .put("successes", successful.size)
            .put("failures", samples.count { !it.success })
            .put("totalElapsedNanos", successful.sumOf(MediaIdBenchmarkSample::elapsedNanos))
            .put("elapsedNanos", distribution(successful.map(MediaIdBenchmarkSample::elapsedNanos)))
            .put("phasesTotalNanos", phases)
            .put("files", JSONArray(results))
    }

    private fun compactSummary(result: JSONObject): JSONObject =
        JSONObject()
            .put("event", result.getString("event"))
            .put("platform", result.getString("platform"))
            .put("requestedFiles", result.getInt("requestedFiles"))
            .put("identityVersion", result.getString("identityVersion"))
            .put("successes", result.getInt("successes"))
            .put("failures", result.getInt("failures"))
            .put("totalElapsedNanos", result.getLong("totalElapsedNanos"))
            .put("elapsedNanos", result.getJSONObject("elapsedNanos"))

    private fun distribution(values: List<Long>): JSONObject {
        if (values.isEmpty()) return JSONObject()
        val sorted = values.sorted()

        fun percentile(percent: Double): Long = sorted[(sorted.lastIndex * percent).roundToInt()]
        return JSONObject()
            .put("minimum", sorted.first())
            .put("p50", percentile(0.50))
            .put("p90", percentile(0.90))
            .put("p95", percentile(0.95))
            .put("p99", percentile(0.99))
            .put("maximum", sorted.last())
            .put("mean", sorted.average())
            .put("total", sorted.sum())
    }

    private fun <T> evenlySpaced(
        values: List<T>,
        limit: Int,
    ): List<T> {
        if (values.size <= limit) return values
        if (limit == 1) return listOf(values.first())
        return List(limit) { index -> values[(index.toLong() * values.lastIndex / (limit - 1)).toInt()] }
    }

    private fun interface Generator {
        fun generate(
            source: SourceMedia,
            cancellation: CancellationSignal,
        ): Long
    }

    private data class CorpusItem(
        val locator: String,
        val relativePath: String,
        val extension: String,
        val sizeBytes: Long?,
        val devicePath: String?,
    ) {
        fun source() = SourceMedia(locator, relativePath)
    }

    private fun Long.toUnsignedHex(): String =
        java.lang.Long
            .toUnsignedString(this, 16)
            .padStart(16, '0')

    companion object {
        private const val RESULT_FILE = "media-id-benchmark-result.json"
        private val ITEM_ORDER =
            compareBy<CorpusItem>(
                CorpusItem::extension,
                { it.sizeBytes ?: -1L },
                CorpusItem::relativePath,
            )
    }
}
