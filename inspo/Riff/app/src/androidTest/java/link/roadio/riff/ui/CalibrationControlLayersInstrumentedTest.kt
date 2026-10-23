package link.roadio.riff.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Native render/input checks use disposable references, not working calibration or media data. */
class CalibrationControlLayersInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun referenceShowsThroughGapsAndInactivePlateButEuroPlatesStayOpaque() {
        instrumentation.runOnMainSync {
            val config = UiConfig.defaults(context)
            for (night in listOf(false, true)) {
                val palette = if (night) UiPalette.night(config) else UiPalette.day(config)
                for (mode in 0..2) {
                    val session =
                        CalibrationSessionState().apply {
                            modeIndex = mode
                            candidateXPxPerMm = 760f /
                                if (mode == 0) {
                                    85.60f
                                } else if (mode == 1) {
                                    66.29f
                                } else {
                                    72f
                                }
                            candidateYPxPerMm = if (mode == 0) 456f / 53.98f else candidateXPxPerMm
                        }
                    val view = view(session, palette, 800, 480)
                    val bitmap = render(view)
                    // Lower portions avoid selector glyphs and rounded corners. These positions are
                    // fixed by the four-control percentage contract, independent of the reference.
                    val emptyPatch = patch(bitmap, 230, 452, 28, 8)
                    if (mode == 2) {
                        assertTrue("Euro denomination plate is opaque", emptyPatch.all { it == palette.surface })
                    } else {
                        assertTrue("Inactive plate reveals hatch texture", emptyPatch.toSet().size > 3)
                        assertTrue(emptyPatch.any { it != palette.background && it != palette.surface })
                    }
                    assertEquals(palette.surface, bitmap.getPixel(87, 458))
                    assertTrue("Gap exposes underlying reference", patch(bitmap, 323, 420, 4, 20).any { it != palette.background })
                    save(bitmap, "${if (night) "NIGHT" else "DAY"}_MODE_$mode")
                    if (mode != 2) {
                        val before = session.candidateXPxPerMm to session.candidateYPxPerMm
                        gesture(view, 246f, 434f, 246f, 100f)
                        assertEquals(
                            "Disabled placeholder retains input exclusion",
                            before,
                            session.candidateXPxPerMm to session.candidateYPxPerMm,
                        )
                    }
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun cardCanGrowBelowButtonsAndControlBoundsNeverBecomeDragTargets() {
        instrumentation.runOnMainSync {
            val palette = UiPalette.night(UiConfig.defaults(context))
            for ((width, height) in listOf(800 to 480, 480 to 800)) {
                val session = CalibrationSessionState()
                val view = view(session, palette, width, height)
                render(view).recycle()
                val shortEdge = minOf(width, height).toFloat()
                val referenceHeight = if (height > width) 85.60f else 53.98f
                gesture(view, width * .1f, height * .2f, width * .1f, height * .99f)
                assertEquals(height - shortEdge * .05f, session.candidateYPxPerMm!! * referenceHeight, .05f)
                val bitmap = render(view)
                val barTop = height - shortEdge * (.025f + .14f)
                assertTrue(session.candidateYPxPerMm!! * referenceHeight + shortEdge * .025f > barTop)
                save(bitmap, "CARD_EXTENDED_${width}_$height")
                bitmap.recycle()
            }
        }
    }

    private fun view(
        session: CalibrationSessionState,
        palette: UiPalette,
        width: Int,
        height: Int,
    ): CalibrationScreenView =
        CalibrationScreenView(
            context,
            session,
            palette,
            drawable = { name ->
                val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                requireNotNull(context.getDrawable(id)) as Drawable
            },
            onCancel = {},
            onAccept = { _, _ -> },
        ).also { it.layout(0, 0, width, height) }

    private fun render(view: CalibrationScreenView): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private fun patch(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): List<Int> = (top until top + height).flatMap { y -> (left until left + width).map { x -> bitmap.getPixel(x, y) } }

    private fun gesture(
        view: CalibrationScreenView,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
    ) {
        val start = SystemClock.uptimeMillis()
        for ((action, x, y) in listOf(
            Triple(MotionEvent.ACTION_DOWN, x0, y0),
            Triple(MotionEvent.ACTION_MOVE, x1, y1),
            Triple(MotionEvent.ACTION_UP, x1, y1),
        )) {
            MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0).also {
                view.onTouchEvent(it)
                it.recycle()
            }
        }
    }

    private fun save(
        bitmap: Bitmap,
        name: String,
    ) {
        val directory = File(context.filesDir, "calibration-layer-fixtures").also { it.mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
