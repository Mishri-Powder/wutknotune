package link.roadio.riff.indexer.android.level2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CodecInputChunkTest {
    @Test
    fun `capacity split retains every access unit in order`() {
        val units = listOf(unit(3, 1L), unit(4, 2L), unit(2, 3L))

        val (accepted, remainder) = CodecInputChunk(units).splitForCapacity(7)

        assertEquals(listOf(1L, 2L), accepted.units.map(CodecAccessUnit::presentationTimeUs))
        assertArrayEquals(byteArrayOf(2, 2), requireNotNull(remainder).units.single().data)
    }

    @Test
    fun `capacity split returns no remainder when the batch fits`() {
        val units = listOf(unit(3, 1L), unit(4, 2L))

        val (accepted, remainder) = CodecInputChunk(units).splitForCapacity(7)

        assertEquals(units, accepted.units)
        assertNull(remainder)
    }

    @Test
    fun `capacity split rejects an access unit larger than the input buffer`() {
        assertThrows(IllegalArgumentException::class.java) {
            CodecInputChunk(listOf(unit(8, 1L))).splitForCapacity(7)
        }
    }

    private fun unit(
        size: Int,
        timeUs: Long,
    ) = CodecAccessUnit(ByteArray(size) { size.toByte() }, timeUs, 0)
}
