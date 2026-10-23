package link.roadio.riff.ui

import link.roadio.riff.player.core.selection.AiAxisRange
import org.junit.Assert.assertEquals
import org.junit.Test

class AiPointTest {
    @Test
    fun `ltr corners map to normalized arousal and valence`() {
        assertEquals(AiPoint(1f, -1f), aiPointFromCanvas(0f, 0f, 100f, 100f, rtl = false))
        assertEquals(AiPoint(1f, 1f), aiPointFromCanvas(100f, 0f, 100f, 100f, rtl = false))
        assertEquals(AiPoint(-1f, -1f), aiPointFromCanvas(0f, 100f, 100f, 100f, rtl = false))
        assertEquals(AiPoint(-1f, 1f), aiPointFromCanvas(100f, 100f, 100f, 100f, rtl = false))
    }

    @Test
    fun `rtl keeps negative valence at logical start`() {
        assertEquals(AiPoint(1f, -1f), aiPointFromCanvas(100f, 0f, 100f, 100f, rtl = true))
        assertEquals(AiPoint(-1f, 1f), aiPointFromCanvas(0f, 100f, 100f, 100f, rtl = true))
    }

    @Test
    fun `coordinates clamp outside the canvas`() {
        assertEquals(AiPoint(1f, -1f), aiPointFromCanvas(-20f, -20f, 100f, 100f, rtl = false))
        assertEquals(AiPoint(-1f, 1f), aiPointFromCanvas(120f, 120f, 100f, 100f, rtl = false))
    }

    @Test
    fun `data fitted ranges map canvas corners and invert the marker position`() {
        val arousalRange = AiAxisRange(-0.2f, 0.6f)
        val valenceRange = AiAxisRange(-0.4f, 0.8f)

        assertEquals(
            AiPoint(0.6f, -0.4f),
            aiPointFromCanvas(0f, 0f, 100f, 100f, false, arousalRange, valenceRange),
        )
        val selected = AiPoint(arousal = 0.4f, valence = 0.2f)
        val canvas = aiPointToCanvas(selected, 100f, 100f, false, arousalRange, valenceRange)

        assertEquals(50f, canvas.x, 0.0001f)
        assertEquals(25f, canvas.y, 0.0001f)
        val restored =
            aiPointFromCanvas(canvas.x, canvas.y, 100f, 100f, false, arousalRange, valenceRange)
        assertEquals(selected.arousal, restored.arousal, 0.0001f)
        assertEquals(selected.valence, restored.valence, 0.0001f)
    }

    @Test
    fun `same numeric selection moves when viewport bounds change`() {
        val selection = AiPoint(arousal = 0.1f, valence = 0f)
        val full = aiPointToCanvas(selection, 100f, 100f, false)
        val fitted =
            aiPointToCanvas(
                selection,
                100f,
                100f,
                false,
                AiAxisRange(-0.2f, 0.2f),
                AiAxisRange(-0.1f, 0.3f),
            )

        assertEquals(50f, full.x, 0f)
        assertEquals(45f, full.y, 0.0001f)
        assertEquals(25f, fitted.x, 0.0001f)
        assertEquals(25f, fitted.y, 0.0001f)
    }
}
