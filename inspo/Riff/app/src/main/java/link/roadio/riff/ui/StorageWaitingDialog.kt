@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import link.roadio.riff.player.core.settings.SettingsAccess

enum class StorageStartupAction { REQUEST_RESET, AUTHORIZE_RECOVERY, CONFIRM_RESET, CANCEL_RESET }

data class StorageWaitingState(
    val volumeName: String,
    val access: SettingsAccess,
    val confirmation: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

/** Bootstrap-only presentation: callers own admission, mode policy and the reset transaction. */
@Composable
fun StorageWaitingDialog(
    state: StorageWaitingState,
    config: UiConfig,
    palette: UiPalette,
    dispatch: (StorageStartupAction) -> Unit,
) {
    val density = LocalDensity.current.density
    // AI-INTENT: Resolve presentation from the supplied bootstrap snapshot without reading a root.
    // https://wiki.271.pics/Riff/LATEST/ui-modal-dialogs#scoped-semantics-and-bootstrap-boundary
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val resolved = config.resolved(constraints.maxWidth / density, config.night)
        val recovery = state.access == SettingsAccess.RECOVERY_REQUIRED
        RiffModalDialog(
            title = if (state.confirmation) "Reset to Internal?" else "Waiting for USB/SD storage…",
            message =
                if (state.confirmation) {
                    "Start over with fresh Internal data? Settings, playlists and listening history on the selected storage will not be loaded."
                } else {
                    state.volumeName + "\nRiff will continue when the selected storage is available."
                },
            confirmation =
                RiffDialogAction(
                    label = if (state.confirmation) "Reset" else "Forget and reset to Internal",
                    enabled = !state.busy && state.access != SettingsAccess.BLOCKED,
                    onClick = {
                        dispatch(if (state.confirmation) StorageStartupAction.CONFIRM_RESET else StorageStartupAction.REQUEST_RESET)
                    },
                    onLongClick =
                        if (!state.confirmation && recovery) {
                            { dispatch(StorageStartupAction.AUTHORIZE_RECOVERY) }
                        } else {
                            null
                        },
                    onLongClickLabel = if (!state.confirmation && recovery) "Confirm storage reset recovery" else null,
                ),
            cancellation =
                if (state.confirmation) {
                    RiffDialogAction("Cancel", { dispatch(StorageStartupAction.CANCEL_RESET) }, enabled = !state.busy)
                } else {
                    null
                },
            config = resolved,
            palette = palette,
            testTag = "storage:waiting-dialog",
        ) {
            val gap = resolved.float("geometry.targetSpacingMin").dp
            if (!state.confirmation) {
                Spacer(Modifier.height(gap))
                RiffModalText("To start over, forget this selection and reset to Internal.")
                if (recovery) {
                    Spacer(Modifier.height(gap))
                    RiffModalText("Touch and hold to reset when it is safe to do so.")
                }
            }
            state.error?.let {
                Spacer(Modifier.height(gap))
                RiffModalText(it)
            }
        }
    }
}
