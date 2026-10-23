package link.roadio.riff.indexer.android.level2

import android.content.Context
import android.os.SystemClock
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.indexer.level2.AugmentationGenre
import link.roadio.riff.indexer.level2.AugmentationRecord
import link.roadio.riff.storage.hash.nativeXxh3
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

internal data class Level2RawOutput(
    val values: Array<FloatArray>,
    val logVariances: Array<FloatArray>,
    val genreLogits: Array<FloatArray>,
    val inferenceMillis: Long,
)

internal data class Level2ModelResult(
    val record: AugmentationRecord,
    val postprocessMillis: Long,
)

/**
 * Validated deployment package around one shared Level 2 backbone and its three output heads.
 *
 * Model bytes are mmap-backed from the APK. Manifest shapes/order and checksums are verified before the
 * interpreter becomes usable; calibration and thresholds are post-processing data, not trainable Android
 * state. One package instance is confined to the ordered Level 2 consumer thread.
 */
internal class Level2ModelPackage private constructor(
    private val interpreter: Interpreter,
    private val scalarCalibrators: Map<String, LinearCalibrator>,
    private val genreCalibrators: List<GenreCalibrator>,
    private val logVarianceMinimum: Double,
    private val logVarianceMaximum: Double,
    val modelBytes: Long,
) : Closeable {
    private val input =
        ByteBuffer
            .allocateDirect(Level2AudioFeatures.LOG_MEL_FLOAT_COUNT * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

    fun infer(logMel: FloatArray): Level2RawOutput {
        require(logMel.size == Level2AudioFeatures.LOG_MEL_FLOAT_COUNT)
        require(logMel.all(Float::isFinite))
        input.clear()
        input.asFloatBuffer().put(logMel)
        input.position(0)
        val values = Array(FEATURE_COUNT) { FloatArray(MEMBER_COUNT) }
        val logVariances = Array(FEATURE_COUNT) { FloatArray(MEMBER_COUNT) }
        val genreLogits = Array(MEMBER_COUNT) { FloatArray(GENRE_COUNT) }
        val started = SystemClock.elapsedRealtimeNanos()
        interpreter.runForMultipleInputsOutputs(
            arrayOf(input),
            mapOf(
                VALUE_OUTPUT to values,
                LOG_VARIANCE_OUTPUT to logVariances,
                GENRE_OUTPUT to genreLogits,
            ),
        )
        val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L
        require(values.all { row -> row.all(Float::isFinite) })
        require(logVariances.all { row -> row.all(Float::isFinite) })
        require(genreLogits.all { row -> row.all(Float::isFinite) })
        return Level2RawOutput(values, logVariances, genreLogits, elapsed)
    }

    fun postprocess(
        mediaId: MediaId,
        output: Level2RawOutput,
    ): Level2ModelResult {
        // Ensemble disagreement becomes calibrated confidence; it is not a softmax probability.
        val started = SystemClock.elapsedRealtimeNanos()
        val scalar =
            FEATURE_NAMES.indices.map { feature ->
                scalarValue(
                    output.values[feature],
                    output.logVariances[feature],
                    scalarCalibrators.getValue(FEATURE_NAMES[feature]),
                )
            }
        val genres =
            genreCalibrators.mapIndexedNotNull { index, genre ->
                val meanProbability =
                    output.genreLogits
                        .map { member -> sigmoid(member[index].toDouble()) }
                        .average()
                val confidence = genre.calibrator.interpolate(meanProbability)
                if (confidence >= genre.threshold) {
                    AugmentationGenre(
                        id = genre.genreId,
                        genreId = genre.genreId,
                        confidence = confidence.toFloat(),
                    )
                } else {
                    null
                }
            }
        val record =
            AugmentationRecord(
                mediaId = mediaId,
                pte = scalar[0].first,
                pteConfidence = scalar[0].second,
                arousal = scalar[1].first,
                arousalConfidence = scalar[1].second,
                valence = scalar[2].first,
                valenceConfidence = scalar[2].second,
                genres = genres.sortedBy(AugmentationGenre::id),
            ).validate()
        return Level2ModelResult(
            record = record,
            postprocessMillis =
                (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L,
        )
    }

    private fun scalarValue(
        values: FloatArray,
        logVariances: FloatArray,
        calibrator: LinearCalibrator,
    ): Pair<Float, Float> {
        require(values.size == MEMBER_COUNT && logVariances.size == MEMBER_COUNT)
        val mean = values.map(Float::toDouble).average()
        val secondMoment =
            values.indices
                .map { index ->
                    val variance =
                        exp(
                            logVariances[index]
                                .toDouble()
                                .coerceIn(logVarianceMinimum, logVarianceMaximum),
                        )
                    variance + values[index] * values[index]
                }.average()
        val sigma = sqrt(max(0.0, secondMoment - mean * mean))
        val expectedAbsoluteError = calibrator.interpolate(sigma)
        val confidence = (1.0 - expectedAbsoluteError / 2.0).coerceIn(0.0, 1.0)
        return mean.toFloat().coerceIn(-1.0f, 1.0f) to confidence.toFloat()
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_VERSION = "riff-level2-efficientat-eval-v2"
        private const val ASSET_ROOT = "level2"
        private const val MODEL_ASSET = "$ASSET_ROOT/$MODEL_VERSION.tflite"
        private const val MEMBER_COUNT = 3
        private const val FEATURE_COUNT = 3
        private const val GENRE_COUNT = 87
        private const val VALUE_OUTPUT = 0
        private const val LOG_VARIANCE_OUTPUT = 1
        private const val GENRE_OUTPUT = 2
        private val FEATURE_NAMES = listOf("pte", "arousal", "valence")

        fun load(context: Context): Level2ModelPackage {
            val manifest = context.assets.json("$ASSET_ROOT/manifest.json")
            require(manifest.getString("schema_version") == "RIFF-L2-MODEL-PACKAGE-V1")
            require(manifest.getString("model_version") == MODEL_VERSION)
            require(manifest.getString("deployment_class") == "public-beta")
            require(manifest.getBoolean("distribution_allowed"))
            require(manifest.getJSONArray("feature_order").strings() == FEATURE_NAMES)
            require(manifest.getJSONArray("genre_ids").length() == GENRE_COUNT)

            val inventory = context.assets.json("$ASSET_ROOT/XXH3SUMS.json")
            val expectedHash =
                inventory
                    .getJSONObject("$MODEL_VERSION.tflite")
                    .getString("xxh3")
            val model = context.assets.mapAndValidate(MODEL_ASSET, expectedHash)
            val options =
                Interpreter
                    .Options()
                    .setNumThreads(CPU_THREAD_COUNT)
                    .setUseXNNPACK(true)
                    .setCancellable(true)
            val interpreter = Interpreter(model, options)
            require(interpreter.inputTensorCount == 1)
            require(
                interpreter
                    .getInputTensor(0)
                    .shape()
                    .contentEquals(
                        intArrayOf(
                            Level2AudioFeatures.EXCERPT_COUNT,
                            Level2AudioFeatures.FRAMES,
                            Level2AudioFeatures.MEL_BINS,
                            1,
                        ),
                    ),
            )
            require(interpreter.outputTensorCount == 3)
            require(interpreter.getOutputTensor(VALUE_OUTPUT).shape().contentEquals(intArrayOf(3, 3)))
            require(interpreter.getOutputTensor(LOG_VARIANCE_OUTPUT).shape().contentEquals(intArrayOf(3, 3)))
            require(interpreter.getOutputTensor(GENRE_OUTPUT).shape().contentEquals(intArrayOf(3, 87)))

            val calibration = context.assets.json("$ASSET_ROOT/calibrators.json")
            val bounds = calibration.getJSONArray("log_variance_bounds")
            val scalarObject = calibration.getJSONObject("scalar")
            val scalar =
                FEATURE_NAMES.associateWith { name ->
                    val value = scalarObject.getJSONObject(name)
                    LinearCalibrator(
                        x = value.getJSONArray("sigma").doubles(),
                        y = value.getJSONArray("expected_absolute_error").doubles(),
                    )
                }
            val genre =
                calibration.getJSONArray("genre").objects().map { value ->
                    val calibrator = value.getJSONObject("calibrator")
                    GenreCalibrator(
                        genreId = value.getLong("genre_id"),
                        calibrator =
                            LinearCalibrator(
                                x = calibrator.getJSONArray("probability").doubles(),
                                y = calibrator.getJSONArray("calibrated").doubles(),
                            ),
                        threshold = value.getDouble("threshold"),
                    )
                }
            require(
                genre.map(GenreCalibrator::genreId) ==
                    manifest.getJSONArray("genre_ids").longs(),
            )
            return Level2ModelPackage(
                interpreter = interpreter,
                scalarCalibrators = scalar,
                genreCalibrators = genre,
                logVarianceMinimum = bounds.getDouble(0),
                logVarianceMaximum = bounds.getDouble(1),
                modelBytes = model.capacity().toLong(),
            )
        }

        const val CPU_THREAD_COUNT = 4
    }
}

private data class GenreCalibrator(
    val genreId: Long,
    val calibrator: LinearCalibrator,
    val threshold: Double,
)

private data class LinearCalibrator(
    val x: List<Double>,
    val y: List<Double>,
) {
    init {
        require(x.size == y.size && x.size >= 2)
        require(x.zipWithNext().all { (left, right) -> left <= right })
        require(y.all(Double::isFinite))
    }

    fun interpolate(value: Double): Double {
        if (value <= x.first()) return y.first()
        for (index in 1 until x.size) {
            if (value <= x[index]) {
                val width = x[index] - x[index - 1]
                if (width <= 0.0) return y[index]
                val fraction = (value - x[index - 1]) / width
                return y[index - 1] + fraction * (y[index] - y[index - 1])
            }
        }
        return y.last()
    }
}

private fun android.content.res.AssetManager.json(path: String): JSONObject =
    open(path).bufferedReader().use { reader -> JSONObject(reader.readText()) }

/** Maps an uncompressed APK asset and validates its exact bytes before TFLite sees it. */
private fun android.content.res.AssetManager.mapAndValidate(
    path: String,
    expectedXxh3: String,
): MappedByteBuffer {
    val mapped =
        openFd(path).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                )
            }
        }
    require(expectedXxh3.matches(Regex("[0-9a-f]{16}"))) { "Invalid Level2 model checksum" }
    val actualHash = nativeXxh3(mapped)
    val unsignedHex = java.lang.Long.toUnsignedString(actualHash, 16)
    val actual = unsignedHex.padStart(16, '0')
    require(actual == expectedXxh3) { "Level2 model checksum mismatch" }
    mapped.position(0)
    return mapped
}

private fun JSONArray.doubles(): List<Double> = (0 until length()).map(::getDouble)

private fun JSONArray.longs(): List<Long> = (0 until length()).map(::getLong)

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

private fun sigmoid(value: Double): Double =
    if (value >= 0.0) {
        1.0 / (1.0 + exp(-value))
    } else {
        val exponential = exp(value)
        exponential / (1.0 + exponential)
    }
