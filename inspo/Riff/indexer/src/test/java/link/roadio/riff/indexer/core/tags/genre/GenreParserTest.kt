package link.roadio.riff.indexer.core.tags.genre

import link.roadio.riff.indexer.level1.GenreMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreParserTest {
    private val known = mapOf("r&b" to 1L, "soul" to 2L, "pop/rock" to 3L, "rock" to 4L)
    private val matcher = GenreMatcher { known[it.lowercase()]?.let(::setOf).orEmpty() }

    private fun texts(vararg values: String) = GenreParser.parse(values.toList(), matcher).map { it.text }

    @Test
    fun explicitDelimitersWinForTheWholeInputAndSeparateValuesStayIndependent() {
        assertEquals(listOf("R&B/Soul", "Ужасы", "Rock"), texts(" R&B/Soul; Ужасы,\n Rock\r\n"))
        assertEquals(listOf("R&B", "Soul", "Ужасы"), texts("R&B/Soul", "; Ужасы"))
        assertEquals(listOf("Rock & Pop", "Soul"), texts("Rock & Pop; Soul"))
    }

    @Test
    fun gnreProtectsWholeValuesAndLongestSpansWithoutRewritingDisplay() {
        assertEquals(listOf("Pop/Rock"), texts("Pop/Rock"))
        assertEquals(listOf("R & B", "Soul", "Pop / Rock", "Мистика"), texts(" R & B/Soul/Pop / Rock/Мистика "))
        assertEquals(listOf("R\u00a0&\u00a0B"), texts("R\u00a0&\u00a0B"))
        assertEquals(listOf("Ужасы", "Мистика", "Любовный роман", "Hip-Hop"), texts("Ужасы/Мистика&Любовный роман/Hip-Hop"))
        assertEquals(listOf("R", "B"), GenreParser.parse(listOf("R&B"), GenreMatcher.NONE).map { it.text })
    }

    @Test
    fun exactDedupKeepsCaseAndUnicodeVariantsWhileBothProjectionsAgree() {
        val parsed = GenreParser.parse(listOf("R&B/Soul", "R&B; Ужасы; ужасы; Ужасы; Ａ; A"), matcher)
        assertEquals(listOf("R&B", "Soul", "Ужасы", "ужасы", "Ａ", "A"), parsed.map { it.text })
        assertEquals(listOf(setOf(1L), setOf(2L), emptySet(), emptySet(), emptySet(), emptySet()), parsed.map { it.genreIds })
        assertTrue(texts("", " ", ";;,\n", "//&&").isEmpty())
    }

    @Test
    fun pathologicalWeakInputIsRetainedWholeButExplicitSplitsRemainAvailable() {
        val value = (1..66).joinToString("/") { "unknown" }
        assertEquals(listOf(value), texts(value))
        assertEquals(listOf(value, "Rock"), texts("$value;Rock"))
    }
}
