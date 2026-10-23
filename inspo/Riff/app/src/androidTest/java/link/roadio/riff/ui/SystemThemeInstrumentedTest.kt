package link.roadio.riff.ui

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Configuration changes must invalidate the shared palette even when domain state is unchanged. */
class SystemThemeInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = UiConfig.defaults(context)
    private val day =
        Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
    private val night =
        Configuration(day).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
    private var configuration by mutableStateOf(day)
    private val host = context.createConfigurationContext(day)
    private var state by mutableStateOf(
        PlayerUiState(PlayerScreen.MAIN, BrowserItem("root", "Library", BrowserKind.ROOT), nowPlaying = NowPlayingUiState()),
    )

    private fun install() {
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(0.65f, 1f),
            ) {
                PlayerComposeUi(state, config, CalibrationSessionState(), host) {}
            }
        }
    }

    private fun assertPalette(dark: Boolean) {
        val palette = if (dark) UiPalette.night(config) else UiPalette.day(config)
        assertEquals(Color(palette.selectBackground), compose.onNodeWithTag("screen:MAIN").captureToImage().toPixelMap()[0, 0])
    }

    @Test
    fun autoRepaintsOnConfigurationOnlyChangesInBothDirections() {
        install()
        assertPalette(false)
        val unchanged = state
        repeat(3) {
            compose.runOnIdle { configuration = night }
            assertPalette(true)
            assertEquals(unchanged, state)
            compose.runOnIdle { configuration = day }
            assertPalette(false)
            assertEquals(unchanged, state)
        }
    }

    @Test
    fun explicitThemeAndDraftPreviewRetainPrecedenceOverSystem() {
        install()
        for (scheme in listOf(ColorScheme.DAY, ColorScheme.NIGHT)) {
            for (preview in listOf(false, true)) {
                compose.runOnIdle {
                    state =
                        state.copy(
                            settings = state.settings.copy(colorScheme = if (preview) ColorScheme.SYSTEM else scheme),
                            settingsDraft = if (preview) state.settings.copy(colorScheme = scheme) else null,
                        )
                }
                val unchanged = state
                for (current in listOf(day, night, day)) {
                    compose.runOnIdle { configuration = current }
                    assertPalette(scheme == ColorScheme.NIGHT)
                    assertEquals(unchanged, state)
                }
            }
        }
        compose.runOnIdle { state = state.copy(settingsDraft = null, settings = state.settings.copy(colorScheme = ColorScheme.SYSTEM)) }
        compose.runOnIdle { configuration = night }
        assertPalette(true)
    }
}
