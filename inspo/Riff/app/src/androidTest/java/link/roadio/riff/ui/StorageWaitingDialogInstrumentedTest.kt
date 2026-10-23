package link.roadio.riff.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.player.core.settings.SettingsAccess
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StorageWaitingDialogInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmationUsesEnglishAndCancelDoesNotConfirm() {
        val actions = mutableListOf<StorageStartupAction>()
        render(StorageWaitingState("USB storage", SettingsAccess.ALLOWED, confirmation = true), actions, 2f)
        compose.onNodeWithText("Reset to Internal?").assertExists()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.runOnIdle { assertEquals(listOf(StorageStartupAction.CANCEL_RESET), actions) }
    }

    @Test fun restrictedResetIsSemanticallyDisabled() {
        val actions = mutableListOf<StorageStartupAction>()
        render(StorageWaitingState("USB storage", SettingsAccess.BLOCKED), actions)
        compose.onNodeWithContentDescription("Forget and reset to Internal", substring = true).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(emptyList<StorageStartupAction>(), actions) }
    }

    @Test fun automotiveRecoveryHasAnExplicitHoldAction() {
        val actions = mutableListOf<StorageStartupAction>()
        render(StorageWaitingState("USB storage", SettingsAccess.RECOVERY_REQUIRED), actions)
        compose.onNodeWithContentDescription("Forget and reset to Internal", substring = true).performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(listOf(StorageStartupAction.AUTHORIZE_RECOVERY), actions) }
    }

    private fun render(
        state: StorageWaitingState,
        actions: MutableList<StorageStartupAction>,
        fontScale: Float = 1f,
    ) {
        val config = UiConfig.defaults(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                StorageWaitingDialog(state, config, UiPalette.day(config), actions::add)
            }
        }
    }
}
