package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PhysicalDensityResolverTest {
    @Test fun `all window sizes use the same policy regardless of reported density`() {
        // https://wiki.271.pics/Riff/LATEST/ui-foundation
        // AI-INTENT: These are geometry cases, not approved devices. Reported density cannot override
        // a committed measurement or AUTO while raw window bounds are available.
        for ((width, height) in listOf(800 to 480, 1280 to 800, 1080 to 2316, 2800 to 1752)) {
            for (reported in listOf(1f, 2.125f, 450f / 160f, Float.NaN, Float.POSITIVE_INFINITY)) {
                assertEquals(
                    hypot(width.toFloat(), height.toFloat()) / 9f / 160f,
                    resolve(DiagonalChoice.AUTO, 200f, width, height, reported),
                    0f,
                )
                assertEquals(200f / 160f * (7f / 9f), resolve(DiagonalChoice.CARD, 200f, width, height, reported), 0f)
                assertEquals(200f / 160f, resolve(DiagonalChoice.DIAGONAL, 200f, width, height, reported), 0f)
            }
        }
    }

    @Test fun `invalid committed measurements fall back to AUTO instead of reported density`() {
        for (choice in listOf(DiagonalChoice.CARD, DiagonalChoice.DIAGONAL)) {
            for (ppi in listOf(null, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                assertEquals(hypot(1280f, 800f) / 9f / 160f, resolve(choice, ppi, reported = 3f), 0f)
            }
        }
    }

    @Test fun `unavailable window bounds never produce invalid density`() {
        for ((width, height) in listOf(0 to 0, 0 to 800, 1280 to 0, -1280 to 800)) {
            assertEquals(2f, resolve(DiagonalChoice.AUTO, null, width, height, 2f), 0f)
            for (reported in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                val density = resolve(DiagonalChoice.AUTO, null, width, height, reported)
                assertEquals(1f, density, 0f)
                assertTrue(density.isFinite() && density > 0f)
            }
            assertEquals(200f / 160f, resolve(DiagonalChoice.DIAGONAL, 200f, width, height, Float.NaN), 0f)
        }
    }

    @Test fun `diagonal measurement is invariant under window rotation`() {
        assertEquals(
            PhysicalDensityResolver.diagonalPpi(1280, 800, 10.1f),
            PhysicalDensityResolver.diagonalPpi(800, 1280, 10.1f),
            0f,
        )
    }

    private fun resolve(
        choice: DiagonalChoice,
        ppi: Float?,
        width: Int = 1280,
        height: Int = 800,
        reported: Float = 1f,
    ): Float =
        PhysicalDensityResolver.resolve(
            PlayerSettingsUiState(diagonal = choice, calibrationPpiX = ppi),
            width,
            height,
            reported,
        )
}
