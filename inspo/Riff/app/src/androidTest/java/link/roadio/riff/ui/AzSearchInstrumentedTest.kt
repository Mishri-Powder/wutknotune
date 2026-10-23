package link.roadio.riff.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AzSearchInstrumentedTest {
    @Test
    fun platformExtendedGraphemesAndSimpleCaseMappingsMatch() {
        listOf("👨‍👩‍👧‍👦", "🇨🇦", "👍🏽", "🏳️‍🌈").forEach { emoji ->
            assertTrue(azNameMatches(emoji + "ẞ", listOf(AzGroup.SYMBOL, AzGroup.LATIN_PS)))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(32, UiConfig.defaults(context).int("behavior.azRgbSubtract"))
        assertEquals(51, UiConfig.defaults(context).int("behavior.azDisabledAlpha"))
    }
}
