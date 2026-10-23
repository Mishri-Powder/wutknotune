package link.roadio.riff.storage.hash

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeXxh3Test {
    @Test
    fun matchesOfficialXxh3VectorsAcrossChunkBoundaries() {
        assertEquals("2d06800538d394c2", digestHex(byteArrayOf(), 1))
        assertEquals("78af5f94892f3950", digestHex("abc".toByteArray(), 1))

        val sequence = ByteArray(2048) { index -> index.toByte() }
        assertEquals("dd420471ff96bd00", digestHex(sequence, 37))
    }

    @Test
    fun oneShotMatchesOfficialXxh3Vectors() {
        assertEquals("2d06800538d394c2", nativeXxh3(byteArrayOf()).hex())
        assertEquals("78af5f94892f3950", nativeXxh3("abc".encodeToByteArray()).hex())
    }

    private fun digestHex(
        bytes: ByteArray,
        chunkSize: Int,
    ): String =
        NativeXxh3Factory.create().use { stream ->
            var offset = 0
            while (offset < bytes.size) {
                val length = minOf(chunkSize, bytes.size - offset)
                stream.update(bytes, offset, length)
                offset += length
            }
            java.lang.Long
                .toUnsignedString(stream.digest(), 16)
                .padStart(16, '0')
        }

    private fun Long.hex(): String =
        java.lang.Long
            .toUnsignedString(this, 16)
            .padStart(16, '0')
}
