package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class MainGridGeometryTest {
    @Test fun `supported HU keeps configured 240dp tiles`() {
        assertEquals(240f, side(1070f, 520f, 4, 2), 0f)
    }

    @Test fun `short wide window fits both rows above the status slot`() {
        val tile = side(1257f, 423f, 4, 2)
        assertEquals(199f, tile, 0f)
        assertTrue(2 * tile + 24f <= 423f)
        assertTrue(4 * tile + 3 * 24f <= 1257f)
    }

    @Test fun `narrow rows fit after a height only resize`() {
        assertEquals(240f, side(600f, 1100f, 2, 4), 0f)
        val tile = side(600f, 800f, 2, 4)
        assertTrue(4 * tile + 3 * 24f <= 800f)
        assertTrue(2 * tile + 24f <= 600f)
    }

    @Test fun `wide form also respects constrained width`() {
        val tile = side(752f, 520f, 4, 2)
        assertEquals(170f, tile, 0f)
        assertTrue(4 * tile + 3 * 24f <= 752f)
    }

    @Test fun `very small window preserves 128dp floor for scrolling`() {
        assertEquals(128f, side(220f, 250f, 2, 4), 0f)
    }

    @Test fun `admitted skin tile and spacing remain authoritative`() {
        assertEquals(180f, MainGridGeometry.tileSideDp(1200f, 700f, 4, 2, 180f, 32f), 0f)
        val tile = MainGridGeometry.tileSideDp(760f, 380f, 4, 2, 240f, 32f)
        assertTrue(4 * tile + 3 * 32f <= 760f)
        assertTrue(2 * tile + 32f <= 380f)
    }

    @Test fun `rounded rows and gaps fit at fractional physical densities`() {
        listOf(0.43878788f, 0.75f, 1.1f, 1.25f, 2.625f).forEach { density ->
            val tile = MainGridGeometry.tileSideDp(549.24f, 834.117f, 2, 4, 240f, 24f, density)
            val tilePx = (tile * density).roundToInt()
            val gapPx = (24f * density).roundToInt()
            assertTrue(4 * tilePx + 3 * gapPx <= (834.117f * density).roundToInt())
            assertTrue(2 * tilePx + gapPx <= (549.24f * density).roundToInt())
        }
    }

    private fun side(
        width: Float,
        height: Float,
        columns: Int,
        rows: Int,
    ) = MainGridGeometry.tileSideDp(width, height, columns, rows, 240f, 24f)
}
