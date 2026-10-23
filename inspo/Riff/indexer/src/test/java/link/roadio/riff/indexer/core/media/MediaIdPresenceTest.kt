package link.roadio.riff.indexer.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaIdPresenceTest {
    @Test
    fun shortAndPresentZeroLowWordsHaveDifferentKeys() {
        val short = MediaId(0x1234, 0, lowPresent = false)
        val fullZero = MediaId(0x1234, 0, lowPresent = true)
        assertNotEquals(short, fullZero)
        assertNotEquals(short.hex(), fullZero.hex())
        assertNotEquals(0, short.compareTo(fullZero))
        assertEquals(16, short.hex().length)
        assertEquals(32, fullZero.hex().length)
    }
}
