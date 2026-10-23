package link.roadio.riff.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SinglesGlyphInstrumentedTest {
    @Test
    fun sharedSystemFontHasDiamondAtOrdinaryAndLargeTextSizes() {
        val paint = Paint().apply { typeface = Typeface.DEFAULT }
        listOf(16f, 32f, 64f).forEach { size ->
            paint.textSize = size
            assertTrue("Missing U+25C7 at $size", paint.hasGlyph("◇"))
            assertTrue(paint.measureText("Artist ◇ Singles") > paint.measureText("Artist Singles"))
        }
    }
}
