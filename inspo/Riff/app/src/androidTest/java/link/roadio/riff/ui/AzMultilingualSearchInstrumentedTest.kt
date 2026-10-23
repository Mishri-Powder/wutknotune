package link.roadio.riff.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AzMultilingualSearchInstrumentedTest {
    @Test
    fun platformUnicodeClassificationRetainsTheFixedKeypad() {
        assertTrue(azNameMatches("Қайрат", listOf(AzGroup.CYRILLIC_IL)))
        assertTrue(azNameMatches("Өркен", listOf(AzGroup.CYRILLIC_MP)))
        assertTrue(azNameMatches("Әлия", listOf(AzGroup.CYRILLIC_AG)))
        assertTrue(azNameMatches("Łódź", listOf(AzGroup.LATIN_JL)))
        assertTrue(azNameMatches("Đorđe", listOf(AzGroup.LATIN_DF)))
        assertFalse(azNameMatches("Қайрат", listOf(AzGroup.SYMBOL)))
        assertEquals(
            listOf("ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"),
            AZ_LATIN_GROUPS.map(AzGroup::label),
        )
        assertEquals(
            listOf("АБВГ", "ДЕЖЗ", "ИЙКЛ", "МНОП", "РСТУ", "ФХЦЧ", "ШЩЪЫ", "ЬЭЮЯ"),
            AZ_CYRILLIC_GROUPS.map(AzGroup::label),
        )
    }
}
