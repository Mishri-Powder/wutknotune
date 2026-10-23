@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun RowActionsScreen(state: PlayerUiState) {
    val palette = LocalRiffPalette.current
    val dispatch = LocalRiffDispatch.current
    val item = state.actionsItemId?.let { findBrowserItem(state.root, it) }
    SafeContent(Color(palette.selectBackground), "screen:ROW_ACTIONS") {
        val wide = LocalRiffConfig.current.wide
        Column(Modifier.fillMaxSize()) {
            RiffTopBar(
                leading =
                    listOf(
                        TopActionSpec(
                            "arrow_up",
                            "Back to ${state.visibleHierarchyLabel() ?: "hierarchy"}",
                            action = PlayerUiAction.CloseRowActions,
                        ),
                    ),
                title = item?.label ?: "Actions",
                wide = wide,
            )
            // The advisory scrolls with the actions so large font scales never shrink the controls.
            // https://wiki.271.pics/Riff/LATEST/hierarchy-screen-row-actions-and-audiobook-classification
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                item?.let { selected ->
                    rowActionKinds(selected).forEachIndexed { index, action ->
                        if (index > 0) Spacer(Modifier.height(ROW_GAP_DP.dp))
                        val label =
                            when (action) {
                                RowActionKind.PLAY -> "Play"
                                RowActionKind.ADD_TO_PLAYLIST -> "Add to playlist…"
                                RowActionKind.RENAME -> "Rename"
                                RowActionKind.DELETE -> "Delete"
                                RowActionKind.REMOVE_FROM_PLAYLIST -> "Remove from playlist"
                                RowActionKind.REMOVE_BOOKMARK -> "Remove bookmark"
                            }
                        val icon =
                            when (action) {
                                RowActionKind.PLAY -> "play"
                                RowActionKind.ADD_TO_PLAYLIST -> "plus"
                                RowActionKind.REMOVE_FROM_PLAYLIST, RowActionKind.REMOVE_BOOKMARK -> "minus"
                                RowActionKind.RENAME, RowActionKind.DELETE -> null
                            }
                        val enabled =
                            when (action) {
                                RowActionKind.PLAY -> {
                                    selected.playable && !state.audiobookCommitInProgress
                                }

                                RowActionKind.ADD_TO_PLAYLIST -> {
                                    selected.playable &&
                                        state.playlistMutationsAllowed &&
                                        !state.playlistMutationInProgress &&
                                        !state.audiobookCommitInProgress
                                }

                                else -> {
                                    state.playlistMutationsAllowed &&
                                        !state.playlistMutationInProgress &&
                                        !state.audiobookCommitInProgress
                                }
                            }
                        RiffControl(
                            description = label,
                            enabled = enabled,
                            onClick = {
                                when (action) {
                                    RowActionKind.PLAY -> {
                                        dispatch(PlayerUiAction.Play(selected.id))
                                    }

                                    RowActionKind.ADD_TO_PLAYLIST -> {
                                        dispatch(PlayerUiAction.OpenPlaylistSelector(selected.id))
                                    }

                                    RowActionKind.RENAME -> {
                                        selected.playlistId?.let { dispatch(PlayerUiAction.RequestPlaylistRename(it)) }
                                    }

                                    RowActionKind.DELETE -> {
                                        selected.playlistId?.let { dispatch(PlayerUiAction.RequestPlaylistDelete(it)) }
                                    }

                                    RowActionKind.REMOVE_BOOKMARK -> {
                                        val playlistId = selected.playlistId
                                        val markId = selected.bookmarkMarkId
                                        if (playlistId != null &&
                                            markId != null
                                        ) {
                                            dispatch(PlayerUiAction.RemoveBookmark(playlistId, markId))
                                        }
                                    }

                                    RowActionKind.REMOVE_FROM_PLAYLIST -> {
                                        val playlistId = selected.playlistId
                                        val occurrenceId = selected.playlistOccurrenceId
                                        if (playlistId != null && occurrenceId != null) {
                                            dispatch(PlayerUiAction.RemovePlaylistOccurrence(playlistId, occurrenceId))
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(uiDimension("ratingTarget")),
                        ) {
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                if (icon != null) {
                                    RiffIcon(
                                        iconDrawableName(icon),
                                        Color(palette.text),
                                        modifier = Modifier.size(uiDimension("ratingTarget")),
                                    )
                                } else {
                                    Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                                }
                                RiffText(
                                    label,
                                    uiTextSize("textTitleList"),
                                    palette.text,
                                    medium = true,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
                item
                    ?.takeIf {
                        it.kind in setOf(BrowserKind.ALBUM, BrowserKind.ARTIST, BrowserKind.GENRE, BrowserKind.FOLDER, BrowserKind.TRACK)
                    }?.let { selected ->
                        Spacer(Modifier.height(ROW_GAP_DP.dp))
                        val editable = selected.kind in setOf(BrowserKind.ALBUM, BrowserKind.FOLDER, BrowserKind.TRACK)
                        val ownChoice = state.audiobookChoiceDraft ?: state.audiobookOverrides[selected.reactionKey] ?: AudiobookChoice.AUTO
                        Row(
                            Modifier.fillMaxWidth().height(uiDimension("playbackButtonHeight")),
                            horizontalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(AudiobookChoice.MUSIC, AudiobookChoice.BOOK).forEach { choice ->
                                RiffIconButton(
                                    icon = if (choice == AudiobookChoice.MUSIC) "ic_genre" else "ic_audiobook",
                                    label = "Audiobook ${choice.name.lowercase()}",
                                    selected = choice == ownChoice,
                                    enabled = editable && !state.audiobookCommitInProgress,
                                    wide = wide,
                                    onClick = {
                                        dispatch(
                                            PlayerUiAction.SetAudiobookChoice(
                                                selected.reactionKey,
                                                if (ownChoice == choice) AudiobookChoice.AUTO else choice,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("audiobook-choice:${choice.name}"),
                                )
                            }
                        }
                        Spacer(Modifier.height(ROW_GAP_DP.dp))
                        RiffText(
                            ROW_ACTIONS_AI_ADVISORY,
                            20f,
                            palette.muted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fixedLabel = true,
                        )
                    }
            }
        }
    }
}
