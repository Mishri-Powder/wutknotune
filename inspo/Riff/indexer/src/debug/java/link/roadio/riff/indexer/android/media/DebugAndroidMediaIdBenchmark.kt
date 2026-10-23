package link.roadio.riff.indexer.android.media

import android.content.Context
import android.util.Log
import link.roadio.riff.indexer.core.media.MediaIdBenchmarkObserver
import link.roadio.riff.indexer.core.media.MediaIdBenchmarkSample
import org.json.JSONObject

class DebugAndroidMediaIdBenchmarkRecorder(
    private val maximumSamples: Int = 1_000,
) : MediaIdBenchmarkObserver {
    private val samples = ArrayDeque<MediaIdBenchmarkSample>()

    init {
        require(maximumSamples > 0)
    }

    @Synchronized
    override fun record(sample: MediaIdBenchmarkSample) {
        if (samples.size == maximumSamples) samples.removeFirst()
        samples.addLast(sample)
        Log.i(LOG_TAG, sample.toJson().toString())
    }

    @Synchronized
    fun snapshot(): List<MediaIdBenchmarkSample> = samples.toList()

    companion object {
        const val LOG_TAG = "RiffMediaIdBenchmark"
    }
}

fun debugAndroidMediaIdV32Generator(
    context: Context,
    recorder: DebugAndroidMediaIdBenchmarkRecorder,
): AndroidMediaIdV32Generator = AndroidMediaIdV32Generator(context, recorder)

internal fun MediaIdBenchmarkSample.toJson(): JSONObject =
    JSONObject()
        .put("event", "media_id.benchmark")
        .put("source", source)
        .put("platform", platform)
        .put("implementation", implementation)
        .put("identityVersion", identityVersion)
        .put("mode", mode)
        .put("terminationReason", terminationReason)
        .put("success", success)
        .put("elapsedNanos", elapsedNanos)
        .put("sampleCount", sampleCount?.toString())
        .put("selectedOccurrences", selectedOccurrences)
        .put("selectedPayloadBytes", selectedPayloadBytes)
        .put(
            "digest",
            digest?.let {
                java.lang.Long
                    .toUnsignedString(it, 16)
                    .padStart(16, '0')
            },
        ).put("errorCode", errorCode)
        .put("phasesElapsedNanos", JSONObject(phasesElapsedNanos))
