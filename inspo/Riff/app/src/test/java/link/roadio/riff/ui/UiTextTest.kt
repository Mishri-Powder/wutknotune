package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {
    @Test
    fun boundsGraphemesWithoutCuttingCombiningMarksOrEmoji() {
        assertEquals("Á…", boundedUiText("ÁBC", 2))
        assertEquals("👩‍👩‍👧‍👦…", boundedUiText("👩‍👩‍👧‍👦BC", 2))
        assertEquals("ÁB", boundedUiText("ÁB", 2))
        assertEquals("…", boundedUiText("AB", 1))
        assertEquals("", boundedUiText("", 120))
    }
}
