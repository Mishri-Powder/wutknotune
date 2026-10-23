package link.roadio.riff.player.library

import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class TopInferredGenreProjectionTest {
    private val names =
        testGnreDisplayNamesById(
            listOf(
                7L to "Rock",
                8L to "Disco",
                -100_074L to "rock",
                -100_022L to "disco",
            ),
            sameAs = mapOf(-100_074L to 7L, -100_022L to 8L),
        )

    @Test
    fun `highest confidence displayable inference wins`() {
        val projected =
            projectTopInferredGenres(
                mapOf(
                    MediaId(1L) to
                        listOf(
                            PlayerInferredGenre(-100_074L, 0.5f),
                            PlayerInferredGenre(-100_050L, 0.99f),
                            PlayerInferredGenre(-100_022L, 0.8f),
                        ),
                ),
                names,
                Locale.US,
                emptySet(),
            )

        assertEquals(PlayerTopInferredGenre(-100_022L, "Disco", 0.8f), projected[MediaId(1L)])
    }

    @Test
    fun `exclusions apply before selection and equal confidence uses signed genre id`() {
        val inferred =
            mapOf(
                MediaId(1L) to
                    listOf(
                        PlayerInferredGenre(-100_022L, 0.8f),
                        PlayerInferredGenre(-100_074L, 0.5f),
                    ),
                MediaId(2L) to
                    listOf(
                        PlayerInferredGenre(-100_022L, 0.5f),
                        PlayerInferredGenre(-100_074L, 0.5f),
                    ),
            )

        val projected = projectTopInferredGenres(inferred, names, Locale.US, setOf(8L))

        assertEquals(PlayerTopInferredGenre(-100_074L, "Rock", 0.5f), projected[MediaId(1L)])
        assertEquals(PlayerTopInferredGenre(-100_074L, "Rock", 0.5f), projected[MediaId(2L)])
        assertFalse(projected.containsKey(MediaId(3L)))
    }
}
