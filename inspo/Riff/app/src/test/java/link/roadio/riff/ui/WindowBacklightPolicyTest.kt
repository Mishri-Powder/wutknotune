package link.roadio.riff.ui

import link.roadio.riff.player.core.settings.PlayerDimBacklightMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowBacklightPolicyTest {
    @Test
    fun applicabilityUsesBothResolvedThemesAndTheCompleteModeCycle() {
        for (night in listOf(false, true)) {
            for (mode in PlayerDimBacklightMode.entries) {
                val allowed =
                    mode == PlayerDimBacklightMode.AUTO ||
                        mode == if (night) PlayerDimBacklightMode.NIGHT else PlayerDimBacklightMode.DAY
                assertEquals(if (allowed) 0.8f * 0.3f else -1f, dimmedWindowBrightness(mode, night, true, true, 204, 30f), 0.0001f)
            }
        }
        assertEquals(ColorScheme.DAY, ColorScheme.SYSTEM.next())
        assertEquals(ColorScheme.NIGHT, ColorScheme.DAY.next())
        assertEquals(ColorScheme.SYSTEM, ColorScheme.NIGHT.next())
        assertEquals(true, ColorScheme.SYSTEM.resolvedNight(true))
        assertEquals(false, ColorScheme.SYSTEM.resolvedNight(false))
        assertEquals(false, ColorScheme.DAY.resolvedNight(true))
        assertEquals(true, ColorScheme.NIGHT.resolvedNight(false))
    }

    @Test
    fun inactiveOrUnknownBaselineAlwaysReturnsSystemPolicy() {
        for (eligible in listOf(false, true)) {
            for (idle in listOf(false, true)) {
                assertEquals(
                    if (eligible && idle) 0.3f else -1f,
                    dimmedWindowBrightness(PlayerDimBacklightMode.AUTO, false, eligible, idle, 255, 30f),
                    0.0001f,
                )
            }
        }
        for (baseline in listOf(null, -1, 256)) {
            assertEquals(-1f, dimmedWindowBrightness(PlayerDimBacklightMode.AUTO, false, true, true, baseline, 30f), 0f)
        }
    }

    @Test
    fun themedPercentIsRelativeToTheSystemSettingAndZeroRemainsADimTarget() {
        val config = UiConfig.fromUserText("""{"skins":{"default":{"behavior":{"dimBacklightPercent":{"day":15,"night":0}}}}}""")
        for (night in listOf(false, true)) {
            val percent = config.themed(night).float("behavior.dimBacklightPercent")
            assertEquals(
                if (night) 0f else 128f * 0.15f / 255f,
                dimmedWindowBrightness(PlayerDimBacklightMode.AUTO, night, true, true, 128, percent),
                0.0001f,
            )
            assertEquals(-1f, dimmedWindowBrightness(PlayerDimBacklightMode.OFF, night, true, true, 128, percent), 0f)
        }
        assertEquals(12.8f / 255f, dimmedWindowBrightness(PlayerDimBacklightMode.AUTO, false, true, true, 128, 10f), 0.0001f)
        assertEquals(128f / 255f, dimmedWindowBrightness(PlayerDimBacklightMode.AUTO, false, true, true, 128, 100f), 0.0001f)
        assertEquals(0.125f / 255f, dimmedWindowBrightness(PlayerDimBacklightMode.AUTO, false, true, true, 1, 12.5f), 0.0001f)
    }

    @Test
    fun repeatedUpdatesUseSystemBaselineWithoutCompoundingAndAcceptNewBaseline() {
        repeat(10) { assertEquals(0.8f * 0.3f, dimmedWindowBrightness(PlayerDimBacklightMode.AUTO, false, true, true, 204, 30f), 0.0001f) }
        assertEquals(0.4f * 0.3f, dimmedWindowBrightness(PlayerDimBacklightMode.AUTO, false, true, true, 102, 30f), 0.0001f)
    }
}
