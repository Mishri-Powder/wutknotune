package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationProposalTest {
    @Test fun `candidate horizontal ppi is rounded for the proposed dpi label`() {
        assertEquals(346, CalibrationProposal.roundedDpi(345.6f))
        assertEquals("346 x 180 dpi", CalibrationProposal.dpiLabel(345.6f, 180.4f))
    }

    @Test fun `reference mode advances and wraps in declaration order`() {
        assertEquals(1, CalibrationProposal.nextIndex(0, 3))
        assertEquals(2, CalibrationProposal.nextIndex(1, 3))
        assertEquals(0, CalibrationProposal.nextIndex(2, 3))
    }

    @Test fun `fresh scale is identical after viewport dimensions swap`() {
        val portrait = initialScale(1080f, 2316f)
        val landscape = initialScale(2316f, 1080f)
        assertEquals(portrait, landscape, 0f)
    }

    @Test fun `fresh note scale measures the short edge vertically in portrait`() {
        val portrait = initialNoteScale(1080f, 2316f)
        val landscape = initialNoteScale(2316f, 1080f)

        assertEquals(portrait, landscape, 0f)
        assertEquals(27.179f, portrait, 0.001f)
    }

    @Test fun cardReferenceMatchesLandscapeAndPortraitOrientation() {
        val landscape =
            CalibrationProposal.orientationMatchedCardReference(
                screenWidth = 2316f,
                screenHeight = 1080f,
                longEdgeMm = 85.60f,
                shortEdgeMm = 53.98f,
            )
        val portrait =
            CalibrationProposal.orientationMatchedCardReference(
                screenWidth = 1080f,
                screenHeight = 2316f,
                longEdgeMm = 85.60f,
                shortEdgeMm = 53.98f,
            )

        assertEquals(85.60f, landscape.first, 0f)
        assertEquals(53.98f, landscape.second, 0f)
        assertEquals(53.98f, portrait.first, 0f)
        assertEquals(85.60f, portrait.second, 0f)
    }

    @Test fun cardInitialScaleIsInvariantAcrossOrientation() {
        val portrait = initialCardScale(1080f, 2316f)
        val landscape = initialCardScale(2316f, 1080f)

        assertEquals(landscape, portrait, 0f)
    }

    private fun initialCardScale(
        width: Float,
        height: Float,
    ) = CalibrationProposal.orientationInvariantCardInitialScale(
        screenWidth = width,
        screenHeight = height,
        longEdgeMm = 85.60f,
        shortEdgeMm = 53.98f,
        paddingFraction = 0.025f,
        controlHeightFraction = 0.14f,
        gapFraction = 0.02f,
        initialCap = 0.88f,
        initialReduction = 0.98f,
    )

    private fun initialScale(
        width: Float,
        height: Float,
    ) = CalibrationProposal.orientationInvariantInitialScale(
        screenWidth = width,
        screenHeight = height,
        referenceWidthMm = 85.60f,
        referenceHeightMm = 53.98f,
        measureBothAxes = true,
        paddingFraction = 0.025f,
        controlHeightFraction = 0.14f,
        gapFraction = 0.02f,
        initialCap = 0.88f,
        initialReduction = 0.98f,
    )

    private fun initialNoteScale(
        width: Float,
        height: Float,
    ) = CalibrationProposal.orientationInvariantInitialScale(
        screenWidth = width,
        screenHeight = height,
        referenceWidthMm = 66.29f,
        referenceHeightMm = 66.29f,
        measureBothAxes = false,
        paddingFraction = 0.025f,
        controlHeightFraction = 0.14f,
        gapFraction = 0.02f,
        initialCap = 0.88f,
        initialReduction = 0.98f,
    )
}
