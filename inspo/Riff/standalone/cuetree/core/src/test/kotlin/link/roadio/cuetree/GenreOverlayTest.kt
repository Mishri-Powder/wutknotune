package link.roadio.cuetree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GenreOverlayTest {
    @Test fun sectionUnknownGenreDoesNotInheritNumericGenreFromBaseOrTable() {
        val base = mapOf("genre" to listOf("Dance"), "genreId" to listOf("1"), "album" to listOf("Album"))
        val table = mapOf("genre" to listOf("Electronic"), "genreId" to listOf("3"))
        val section = mapOf("genre" to listOf("Unknown literary genre"))
        val attributes = base.overlaidBy(table).overlaidBy(section)
        assertEquals(listOf("Unknown literary genre"), attributes["genre"])
        assertFalse("genreId" in attributes)
        assertEquals(listOf("Album"), attributes["album"])
    }
}
