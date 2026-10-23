package link.roadio.riff.config

import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {
    @Test
    fun operableOffAndBanCannotUseDisabledContrast() {
        reject("""{"iconInactive":{"day":"#9AA7B4"}}""", "iconInactive")
        reject("""{"textSuppressedColor":{"night":"#5F7182"}}""", "textSuppressedColor")
        ProjectConfiguration.parse("""{"skins":{"default":{"colors":{"iconDisabled":{"day":"#EEF1F5"}}}}}""")
    }

    @Test
    fun focusRequiresAutomotiveThresholdRatherThanGenericThreeToOne() {
        val focus = parseColor("#778A9D")
        val background = parseColor("#293846")
        assertTrue(colorContrast(focus, background) >= 3.0)
        assertTrue(colorContrast(focus, background) < 4.5)
        reject("""{"focusRingColor":{"night":"#778A9D"}}""", "focusRingColor")
    }

    @Test
    fun selectedCircleCannotHideItsOwnIconAndText() {
        reject("""{"circleFillActiveAlpha":{"night":1.0}}""", "selected")
    }

    @Test
    fun excellentSolidContrastCannotAdmitInvisibleHaloForeground() {
        val roles =
            listOf(
                "textPrimaryColor",
                "fragmentTitleColor",
                "textSecondaryColor",
                "textSuppressedColor",
                "iconInactive",
                "accentActive",
                "focusRingColor",
                "nowPlayingBorderColor",
                "positive",
                "error",
            )
        val surfaces = listOf("screenBackground", "rowSurface", "rowSurfaceNowPlaying", "controlPlate", "accentCircle")
        val json = (roles.map { "\"$it\":{\"day\":\"#FFFFFF\"}" } + surfaces.map { "\"$it\":{\"day\":\"#000000\"}" }).joinToString(",")
        reject("{$json}", "halo")
    }

    @Test
    fun configuredSelectedRowCannotRemoveItsStateOutline() {
        try {
            ProjectConfiguration.parse("""{"skins":{"default":{"geometry":{"nowPlayingBorder":0}}}}""")
            error("Accepted invisible selected outline")
        } catch (_: IllegalArgumentException) {
            // Whole-file rejection preserves the packaged state geometry.
        }
    }

    private fun reject(
        colors: String,
        reason: String,
    ) {
        try {
            ProjectConfiguration.parse("""{"skins":{"default":{"colors":$colors}}}""")
            error("Accepted unreadable colors")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains(reason))
        }
    }
}
