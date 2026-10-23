package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiControlGeometryTest {
    @Test
    fun `wide rows and play share width while all controls share height`() {
        val width = 464f
        val height = 488f
        val gap = 24f
        val geometry = aiWideControlGeometry(width, height, gap)

        assertEquals(width, geometry.choiceWidthDp * 3f + gap * 2f, 0.001f)
        assertEquals(height, geometry.controlHeightDp * 3f + gap * 2f, 0.001f)
    }

    @Test
    fun `narrow lower strip partitions width two to one after gap`() {
        val width = 592f
        val height = 256f
        val gap = 24f
        val geometry = aiNarrowControlGeometry(width, height, gap)

        assertEquals(width, geometry.choicesWidthDp + gap + geometry.playWidthDp, 0.001f)
        assertEquals(2f, geometry.choicesWidthDp / geometry.playWidthDp, 0.001f)
        assertEquals(geometry.choicesWidthDp, geometry.choiceWidthDp * 3f + gap * 2f, 0.001f)
        assertEquals(height, geometry.choiceHeightDp * 2f + gap, 0.001f)
    }

    @Test
    fun `narrow split is used only while every target minimum fits`() {
        assertTrue(
            canUseAiNarrowSplit(
                widthDp = 592f,
                heightDp = 256f,
                gapDp = 24f,
                minimumChoiceDp = 64f,
                minimumPlayDp = 76f,
            ),
        )
        assertFalse(
            canUseAiNarrowSplit(
                widthDp = 360f,
                heightDp = 256f,
                gapDp = 24f,
                minimumChoiceDp = 64f,
                minimumPlayDp = 76f,
            ),
        )
        assertFalse(
            canUseAiNarrowSplit(
                widthDp = 592f,
                heightDp = 140f,
                gapDp = 24f,
                minimumChoiceDp = 64f,
                minimumPlayDp = 76f,
            ),
        )
    }
}
