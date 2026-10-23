package link.roadio.riff.player.library

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class GnreDisplayNamesTest {
    @Test
    fun `uses base system language and first matching GNRE name`() {
        val names =
            testGnreDisplayNames(
                "en" to "Rock",
                "ru" to "Рок",
                "ru" to "Рок-музыка",
            )

        assertEquals("Рок", names.displayName(TEST_GENRE_ID, Locale.forLanguageTag("ru-RU")))
    }

    @Test
    fun `falls back to first English name`() {
        val names =
            testGnreDisplayNames(
                "es" to "Rock español",
                "en" to "Rock",
                "en" to "Rock music",
            )

        assertEquals("Rock", names.displayName(TEST_GENRE_ID, Locale.FRENCH))
    }

    @Test
    fun `falls back to first available name without English`() {
        val names =
            testGnreDisplayNames(
                "es" to "Rock español",
                "de" to "Rockmusik",
            )

        assertEquals("Rock español", names.displayName(TEST_GENRE_ID, Locale.ITALIAN))
    }

    @Test
    fun `MTG evidence resolves only through one direct SameAs target`() {
        val names =
            testGnreDisplayNamesById(
                listOf(7L to "Rock", -100_074L to "rock"),
                mapOf(-100_074L to 7L),
            )

        assertEquals(7L, names.visibleGenreId(-100_074L))
        assertEquals(7L, names.visibleGenreId(7L))
        assertEquals(null, names.visibleGenreId(-100_073L))
    }
}
