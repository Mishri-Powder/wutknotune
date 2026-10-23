@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SafGrantScreen(state: PlayerUiState) {
    val dispatch = LocalRiffDispatch.current
    BoundedSelector(
        tag = "screen:SAF_GRANTS",
        title = "Media folders",
        items = safGrantSelectorItems(state.safGrants),
        itemKey = { it.id },
        itemContentType = { "saf-grant" },
        page = state.page,
        selectorIndex = state.selectorIndex,
        selectorOffset = state.selectorOffset,
        selectorKey = "saf-grants",
        emptyMessage = "Add a media folder",
        rowHeightWide = uiDimension("ratingTarget"),
        rowHeightNarrow = uiDimension("touchTargetMin"),
        trailing =
            settingsOkAction(
                "Save media folder changes",
                !state.safGrantCommitInProgress,
                PlayerUiAction.SaveSafGrantSelector,
            ),
        leading = { canUp, canDown, pageSize, wide ->
            buildList {
                add(
                    TopActionSpec(
                        "cancel",
                        "Cancel media folder changes",
                        action = PlayerUiAction.CancelSafGrantSelector,
                    ),
                )
                if (wide) {
                    add(
                        TopActionSpec(
                            "arrowhead_up",
                            "Previous page",
                            canUp,
                            PlayerUiAction.PreviousPage(pageSize),
                        ),
                    )
                    add(
                        TopActionSpec(
                            "arrowhead_down",
                            "Next page",
                            canDown,
                            PlayerUiAction.NextPage(pageSize),
                        ),
                    )
                }
            }
        },
        interactionEnabled = !state.safGrantCommitInProgress,
    ) { item, selectedPage, pageCount, _ ->
        RiffControl(
            description = "${item.label}; page ${selectedPage + 1} of $pageCount",
            enabled = !state.safGrantCommitInProgress,
            onClick = {
                dispatch(
                    if (item.id == SAF_ADD_ITEM_ID) {
                        PlayerUiAction.OpenSafDocumentTree
                    } else {
                        PlayerUiAction.RequestSafGrantRemoval(item.id)
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            RiffText(
                item.label,
                uiTextSize("textTitleList"),
                LocalRiffPalette.current.text,
                medium = true,
                modifier = Modifier.fillMaxSize(),
                maxLines = 1,
            )
        }
    }
}

internal const val DATA_STORAGE_WARNING =
    "Internal is safest while Riff is running, but it uses non-replaceable internal flash " +
        "that may be low quality on inexpensive head units. SD cards are replaceable and " +
        "less likely to be removed accidentally, but vibration or poor contact can disconnect " +
        "them. USB storage is easy to unplug or move. You are responsible for choosing and " +
        "keeping removable storage connected. Loss can destroy Riff data. At startup Riff waits " +
        "for missing storage; resetting to fresh Internal data requires confirmation."

@Composable
internal fun DataStorageScreen(state: PlayerUiState) {
    val dispatch = LocalRiffDispatch.current
    BoundedSelector(
        tag = "screen:DATA_STORAGE",
        title = "Save data on",
        items = state.dataStorageLocations,
        itemKey = { it.id },
        itemContentType = { "data-storage" },
        page = state.page,
        selectorIndex = state.selectorIndex,
        selectorOffset = state.selectorOffset,
        selectorKey = "data-storage",
        emptyMessage = "No storage locations available",
        rowHeightWide = uiDimension("ratingTarget"),
        rowHeightNarrow = uiDimension("touchTargetMin"),
        footerHeight = 192.dp,
        footer = {
            androidx.compose.foundation.text.BasicText(
                text = DATA_STORAGE_WARNING,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .semantics { contentDescription = DATA_STORAGE_WARNING },
                style = TextStyle(color = Color(LocalRiffPalette.current.muted), fontSize = 20.sp),
            )
        },
        leading = { canUp, canDown, pageSize, wide ->
            buildList {
                add(
                    TopActionSpec(
                        "cancel",
                        "Cancel data storage selection",
                        action = PlayerUiAction.CancelDataStorageSelector,
                    ),
                )
                if (wide) {
                    add(TopActionSpec("arrowhead_up", "Previous page", canUp, PlayerUiAction.PreviousPage(pageSize)))
                    add(TopActionSpec("arrowhead_down", "Next page", canDown, PlayerUiAction.NextPage(pageSize)))
                }
            }
        },
        trailing =
            TopActionSpec(
                "ok",
                "Move application data to selected storage",
                state.dataStorageDraftId != null && !state.storageMigrationInProgress,
                PlayerUiAction.ConfirmDataStorageSelection,
            ),
        interactionEnabled = !state.storageMigrationInProgress,
    ) { item, selectedPage, pageCount, _ ->
        val selected = item.id == state.dataStorageDraftId
        RiffControl(
            description =
                "${item.label}; ${if (selected) "selected" else "not selected"}; " +
                    "page ${selectedPage + 1} of $pageCount",
            selected = selected,
            enabled = !state.storageMigrationInProgress,
            onClick = { dispatch(PlayerUiAction.SelectDataStorage(item.id)) },
            modifier = Modifier.fillMaxSize(),
        ) {
            RiffText(
                item.label,
                uiTextSize("textTitleList"),
                LocalRiffPalette.current.text,
                medium = true,
                modifier = Modifier.fillMaxSize(),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun PlaylistSelectorScreen(state: PlayerUiState) {
    val dispatch = LocalRiffDispatch.current
    BoundedSelector(
        tag = "screen:PLAYLIST_SELECTOR",
        title = "Add to playlist",
        items = state.playlistSelectorChoices.ifEmpty { listOf(PlaylistSelectorUiItem(null, NEW_PLAYLIST_LABEL)) },
        itemKey = { item -> item.playlistId?.let { "playlist:$it" } ?: "playlist:new" },
        itemContentType = { "playlist" },
        page = state.page,
        selectorIndex = state.selectorIndex,
        selectorOffset = state.selectorOffset,
        selectorKey = "playlist-selector",
        emptyMessage = NEW_PLAYLIST_LABEL,
        rowHeightWide = uiDimension("ratingTarget"),
        rowHeightNarrow = uiDimension("touchTargetMin"),
        leading = { canUp, canDown, pageSize, wide ->
            buildList {
                add(
                    TopActionSpec(
                        "arrow_up",
                        "Back to actions",
                        action = PlayerUiAction.ClosePlaylistSelector,
                    ),
                )
                if (wide) {
                    add(
                        TopActionSpec(
                            "arrowhead_up",
                            "Previous page",
                            canUp,
                            PlayerUiAction.PreviousPage(pageSize),
                        ),
                    )
                    add(
                        TopActionSpec(
                            "arrowhead_down",
                            "Next page",
                            canDown,
                            PlayerUiAction.NextPage(pageSize),
                        ),
                    )
                }
            }
        },
    ) { item, selectedPage, pageCount, _ ->
        RiffControl(
            description = "${item.label}; page ${selectedPage + 1} of $pageCount",
            enabled = state.playlistMutationsAllowed && !state.playlistMutationInProgress,
            onClick = {
                dispatch(
                    item.playlistId?.let(PlayerUiAction::AddToPlaylist)
                        ?: PlayerUiAction.RequestNewPlaylist,
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                RiffIcon(
                    iconDrawableName(if (item.playlistId == null) "plus" else "playlist"),
                    Color(LocalRiffPalette.current.text),
                    modifier = Modifier.size(uiDimension("touchTargetMin")),
                )
                RiffText(
                    item.label,
                    uiTextSize("textTitleList"),
                    LocalRiffPalette.current.text,
                    medium = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    maxLines = 1,
                )
            }
        }
    }
}
