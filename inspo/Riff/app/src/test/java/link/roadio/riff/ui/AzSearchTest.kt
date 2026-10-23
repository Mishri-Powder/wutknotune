package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzSearchTest {
    private fun item(label: String) = BrowserItem(label, label, BrowserKind.ALBUM)

    @Test
    fun `shared filter preserves source order for every media root`() {
        HierarchyRoot.entries.forEach { root ->
            val items = listOf(item("Zulu"), item("Alpha"), item("Beta"))
            assertEquals(
                listOf("Alpha", "Beta"),
                filterAzItems(items, listOf(AzGroup.LATIN_AC)).map(BrowserItem::label),
            )
            assertTrue(root.categoryId.startsWith("category:"))
        }
    }

    @Test
    fun `matching is case insensitive NFC grapheme aware and includes adopted alphabets`() {
        val items =
            listOf(
                item("a\u0301lbum"),
                item("ßeta"),
                item("ёж"),
                item("Я"),
            )

        assertEquals(listOf("a\u0301lbum"), filterAzItems(items, listOf(AzGroup.LATIN_AC)).map { it.label })
        assertEquals(listOf("ßeta"), filterAzItems(items, listOf(AzGroup.LATIN_PS)).map { it.label })
        assertEquals(listOf("ёж"), filterAzItems(items, listOf(AzGroup.CYRILLIC_DZ)).map { it.label })
        assertEquals(listOf("Я"), filterAzItems(items, listOf(AzGroup.CYRILLIC_SOFT_YA)).map { it.label })
    }

    @Test
    fun `symbols and residual Unicode share hash without becoming a wildcard`() {
        val items = listOf(item("🎵 Mix"), item("-Dash"), item("東京"), item("١ song"), item("Alpha"), item("2"), item("Ё"))
        assertEquals(
            listOf("🎵 Mix", "-Dash", "東京", "١ song"),
            filterAzItems(items, listOf(AzGroup.SYMBOL)).map(BrowserItem::label),
        )
    }

    @Test
    fun `finished names remain results without a terminal control`() {
        val items = listOf(item("A"), item("Ab"), item("9"), item("#tag"))
        val first = listOf(AzGroup.LATIN_AC)
        assertEquals(setOf(AzGroup.LATIN_AC), availableAzGroups(items, first))
        assertEquals(listOf("A", "Ab"), filterAzItems(items, first).map(BrowserItem::label))
        assertTrue(availableAzGroups(items, first + AzGroup.LATIN_AC).isEmpty())
        assertFalse(azNameMatches("A", first + AzGroup.LATIN_AC))
    }

    @Test
    fun `auto entry uses exact three viewport inclusive threshold`() {
        assertTrue(shouldAutoOpenAzSearch(12, 100, 24, 472))
        assertFalse(shouldAutoOpenAzSearch(11, 100, 24, 472))
        assertFalse(shouldAutoOpenAzSearch(12, 0, 24, 472))
    }

    @Test
    fun `auto apply requires explicit nonempty one-screen result`() {
        assertTrue(shouldAutoApplyAzSearch(true, 5, 5))
        assertFalse(shouldAutoApplyAzSearch(false, 5, 5))
        assertFalse(shouldAutoApplyAzSearch(true, 0, 5))
        assertFalse(shouldAutoApplyAzSearch(true, 6, 5))
    }
}
