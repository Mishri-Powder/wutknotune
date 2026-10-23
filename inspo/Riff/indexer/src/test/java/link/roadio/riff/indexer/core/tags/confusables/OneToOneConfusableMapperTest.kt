package link.roadio.riff.indexer.core.tags.confusables

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OneToOneConfusableMapperTest {
    @Test
    fun loadsPinnedGeneratedUnicode17Table() {
        val mapper = OneToOneConfusableMapper.default

        assertEquals("17.0.0", mapper.revision.unicodeVersion)
        assertEquals(4_462, mapper.revision.entryCount)
        assertEquals("Rock", mapper.map("R\u043e\u0441k"))
        assertEquals("O", mapper.map("0"))
        assertEquals("m", mapper.map("m"))
        assertTrue(mapper.revision.sourceSha256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun rejectsUnsortedGeneratedData() {
        val bytes =
            ByteBuffer
                .allocate(24)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(0x52434631)
                .putInt(2)
                .putInt('b'.code)
                .putInt('a'.code)
                .putInt('a'.code)
                .putInt('a'.code)
                .array()
        val revision =
            ConfusableDataRevision(
                unicodeVersion = "test",
                sourceSha256 = "0".repeat(64),
                tableSha256 = "0".repeat(64),
                entryCount = 2,
            )

        assertThrows(IllegalArgumentException::class.java) {
            OneToOneConfusableMapper.fromBytes(bytes, revision)
        }
    }
}
