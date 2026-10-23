@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun AppDialogs(state: PlayerUiState) {
    val dispatch = LocalRiffDispatch.current
    state.playlistNameRequest?.let { request ->
        PlaylistNameDialog(state, request)
    }
    state.pendingPlaylistDelete?.let { playlist ->
        RiffModalDialog(
            title = "Delete playlist?",
            testTag = "playlist:delete-dialog",
            confirmation =
                RiffDialogAction("OK", { dispatch(PlayerUiAction.ConfirmPlaylistDelete) }, enabled = !state.playlistMutationInProgress),
            cancellation =
                RiffDialogAction(
                    "Cancel",
                    { dispatch(PlayerUiAction.CancelPlaylistDelete) },
                    enabled = !state.playlistMutationInProgress,
                ),
            errorMessage = state.playlistMutationError,
            dismissible = !state.playlistMutationInProgress,
        ) {
            RiffModalText(playlist.name)
            Spacer(Modifier.height(uiDimension("targetSpacingMin")))
            RiffModalText("The active playback queue will not change.")
        }
    }
    state.pendingSafGrantRemoval?.let { grant ->
        RiffModalDialog(
            title = "Remove media folder?",
            message =
                if (state.selectedStorageAvailable) {
                    "Stop using “${grant.label}” after you press OK?"
                } else {
                    "Stop using “${grant.label}” after you press OK? Application data storage is unavailable, " +
                        "so indexing cannot run."
                },
            dismissible = true,
            cancellation = RiffDialogAction("Cancel", { dispatch(PlayerUiAction.CancelSafGrantRemoval) }),
            confirmation = RiffDialogAction("Remove", { dispatch(PlayerUiAction.ConfirmSafGrantRemoval) }),
        )
    }
}

@Composable
internal fun PlaylistNameDialog(
    state: PlayerUiState,
    request: PlaylistNameUiRequest,
) {
    val dispatch = LocalRiffDispatch.current
    val palette = LocalRiffPalette.current
    val config = LocalRiffConfig.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val title = if (request.operation == PlaylistNameOperation.CREATE) "New playlist" else "Rename playlist"
    LaunchedEffect(request) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    RiffModalDialog(
        title = title,
        testTag = "playlist:name-dialog",
        confirmation =
            RiffDialogAction(
                "OK",
                { dispatch(PlayerUiAction.ConfirmPlaylistName) },
                enabled = !state.playlistMutationInProgress && state.playlistNameDraft.isNotBlank(),
            ),
        cancellation =
            RiffDialogAction(
                "Cancel",
                { dispatch(PlayerUiAction.CancelPlaylistName) },
                enabled = !state.playlistMutationInProgress,
            ),
        errorMessage = state.playlistMutationError,
        dismissible = !state.playlistMutationInProgress,
    ) {
        BasicTextField(
            value = state.playlistNameDraft,
            onValueChange = { dispatch(PlayerUiAction.UpdatePlaylistName(it)) },
            enabled = !state.playlistMutationInProgress,
            singleLine = true,
            textStyle = riffModalTextStyle(),
            cursorBrush = SolidColor(Color(palette.primary)),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = uiDimension("navTarget"))
                    .clip(RoundedCornerShape(config.float("geometry.cornerRadius").dp))
                    .background(Color(palette.background))
                    .border(2.dp, Color(palette.outline), RoundedCornerShape(config.float("geometry.cornerRadius").dp))
                    .padding(BUTTON_CONTENT_PADDING_DP.dp)
                    .focusRequester(focusRequester)
                    .testTag("playlist:name-input"),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.playlistNameDraft.isEmpty()) {
                        RiffModalText("Playlist name", muted = true)
                    }
                    field()
                }
            },
        )
    }
}
