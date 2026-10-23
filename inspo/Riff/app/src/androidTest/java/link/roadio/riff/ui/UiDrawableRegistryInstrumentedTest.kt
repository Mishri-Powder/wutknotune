package link.roadio.riff.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiDrawableRegistryInstrumentedTest {
    @Test
    fun allAdoptedVectorDrawablesInflateAndRenderFromApkResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = UiConfig.defaults(context)
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        assertEquals(3L, config.long("player.rewindPreviousThresholdSeconds.music"))
        assertEquals(15L, config.long("player.rewindPreviousThresholdSeconds.audiobook"))
        assertEquals(
            listOf(
                6.8f,
                7.0f,
                8.0f,
                8.8f,
                9.0f,
                10.1f,
                10.25f,
                10.4f,
                11.0f,
                11.6f,
                12.3f,
                12.4f,
                12.95f,
                13.3f,
                14.1f,
                15.1f,
            ),
            config.floatList("player.screenDiagonalChoicesInches"),
        )
        assertEquals(96, UiDrawableRegistry.names.size)
        UiDrawableRegistry.names.forEach { name ->
            UiDrawableRegistry.drawable(context, name).apply {
                setBounds(0, 0, bitmap.width, bitmap.height)
                draw(canvas)
            }
        }

        assertFalse(bitmap.isRecycled)
        bitmap.recycle()
    }

    @Test
    fun drawableXmlCannotBeAddedThroughUserConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            link.roadio.riff.config.ProjectConfiguration
                .parse("""{"drawables":{"ic_play":"xml"}}""")
        }
    }
}
