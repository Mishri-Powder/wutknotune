package link.roadio.riff.indexer.android.level2

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.math.abs

/** Exercises the actual APK model and CPU interpreter against its immutable producer reference. */
class Level2RuntimeParityInstrumentedTest {
    @Test
    fun packagedCpuInterpreterRetainsReferenceOutputs() {
        val context = InstrumentationRegistry.getInstrumentation().context
        assertEquals("link.roadio.riff.indexer.module.test", context.packageName)
        val vectors = mutableMapOf<String, FloatArray>()
        ZipInputStream(context.assets.open("level2/reference-vector.npz")).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                require(bytes.size >= 10 && bytes.copyOfRange(1, 6).decodeToString() == "NUMPY")
                require(bytes[6].toInt() == 1)
                val headerLength =
                    ByteBuffer
                        .wrap(bytes, 8, 2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .short
                        .toInt() and 0xffff
                val header = bytes.copyOfRange(10, 10 + headerLength).decodeToString()
                require("'descr': '<f4'" in header && "'fortran_order': False" in header)
                val payload = ByteBuffer.wrap(bytes, 10 + headerLength, bytes.size - 10 - headerLength).order(ByteOrder.LITTLE_ENDIAN)
                require(payload.remaining() % Float.SIZE_BYTES == 0)
                vectors[entry.name] = FloatArray(payload.remaining() / Float.SIZE_BYTES) { payload.float }
            }
        }
        // AI-INTENT: Read producer-owned expected values; never regenerate goldens with the runtime under test.
        // https://wiki.271.pics/Riff/LATEST/indexer-ai-package#package-and-delivery
        Level2ModelPackage.load(context).use { model ->
            val actual = model.infer(vectors.getValue("logmel.npy"))
            compare("value", actual.values, vectors.getValue("value.npy"))
            compare("log_variance", actual.logVariances, vectors.getValue("log_variance.npy"))
            compare("genre_logits", actual.genreLogits, vectors.getValue("genre_logits.npy"))
            Log.i("RiffRuntimeParity", "model_bytes=${model.modelBytes} inference_ms=${actual.inferenceMillis}")
        }
    }

    private fun compare(
        name: String,
        matrix: Array<FloatArray>,
        expected: FloatArray,
    ) {
        val actual = matrix.flatMap { it.toList() }
        assertEquals(expected.size, actual.size)
        val maximumError = actual.indices.maxOf { abs(actual[it] - expected[it]) }
        Log.i("RiffRuntimeParity", "$name maximum_absolute_error=$maximumError")
        for (index in actual.indices) {
            val tolerance = 1e-4f + 1e-4f * abs(expected[index])
            assertTrue("$name[$index]: ${actual[index]} != ${expected[index]}", abs(actual[index] - expected[index]) <= tolerance)
        }
    }
}
