package link.roadio.riff.indexer.android.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import link.roadio.cuegrain.ced.CedEncodingDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class CedNativeTest {
    @Test
    fun detectsBoundedInputDeterministically() {
        val input = "François — música".toByteArray(StandardCharsets.UTF_8)

        val first = CedEncodingDetector().detect(input)
        val second = CedEncodingDetector().detect(input)

        assertEquals(first, second)
        assertTrue(first.bytesConsumed in 0..input.size)
    }

    @Test
    fun identifiesBothAdoptedRepairTargetsReliably() {
        val utf8 =
            listOf("João Gilberto", "Canção do Mar", "É")
                .joinToString("\n")
                .toByteArray(StandardCharsets.UTF_8)
        val windows1251 =
            listOf("Лебедовский вальс", "Оркестр", "Рок")
                .joinToString("\n")
                .toByteArray(Charset.forName("windows-1251"))

        val utf8Result = CedEncodingDetector().detect(utf8)
        val windows1251Result = CedEncodingDetector().detect(windows1251)

        assertEquals("UTF-8", utf8Result.charset)
        assertTrue(utf8Result.isReliable)
        assertEquals(utf8.size, utf8Result.bytesConsumed)
        assertEquals("windows-1251", windows1251Result.charset)
        assertTrue(windows1251Result.isReliable)
        assertEquals(windows1251.size, windows1251Result.bytesConsumed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInputAboveContractBound() {
        CedEncodingDetector().detect(ByteArray(65_536 + 1))
    }
}
