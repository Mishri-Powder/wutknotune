package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.selection.AiFeaturePoint
import link.roadio.riff.player.library.PlayerPteFeature
import link.roadio.riff.player.library.PlayerReplayGainValue
import link.roadio.riff.player.library.PlayerTopInferredGenre
import link.roadio.riff.player.playback.selectReplayGainQ8ByMediaId
import link.roadio.riff.player.storage.PlaybackQueueSnapshot

internal fun shouldInstallAiHeatmapResult(
    requestToken: Long,
    currentToken: Long,
    builtSelection: AiSelectionUiState,
    currentSelection: AiSelectionUiState,
): Boolean = requestToken == currentToken && builtSelection == currentSelection

internal fun PlayerUiAction.mutatesSafGrantDraft(): Boolean =
    this == PlayerUiAction.SaveSafGrantSelector ||
        this == PlayerUiAction.CancelSafGrantSelector ||
        this == PlayerUiAction.OpenSafDocumentTree ||
        this == PlayerUiAction.ConfirmSafGrantRemoval ||
        this == PlayerUiAction.CancelSafGrantRemoval ||
        this is PlayerUiAction.SafGrantPicked ||
        this is PlayerUiAction.RequestSafGrantRemoval

internal fun PlayerUiAction.requiresAvailableStorage(): Boolean =
    when (this) {
        is PlayerUiAction.Play,
        is PlayerUiAction.SetReaction,
        is PlayerUiAction.SetCurrentReaction,
        PlayerUiAction.AdvanceAudiobookSpeed,
        PlayerUiAction.ResetAudiobookSpeed,
        PlayerUiAction.Previous,
        PlayerUiAction.Next,
        PlayerUiAction.TogglePlayback,
        PlayerUiAction.ToggleRepeat,
        PlayerUiAction.ToggleShuffle,
        is PlayerUiAction.Seek,
        PlayerUiAction.PlaybackFinished,
        PlayerUiAction.CreateAiQueue,
        PlayerUiAction.SaveSettings,
        PlayerUiAction.SaveMoreSettings,
        PlayerUiAction.OpenAiPlaylistSelector,
        is PlayerUiAction.OpenPlaylistSelector,
        is PlayerUiAction.AddToPlaylist,
        PlayerUiAction.RequestNewPlaylist,
        is PlayerUiAction.RequestPlaylistRename,
        PlayerUiAction.ConfirmPlaylistName,
        is PlayerUiAction.RequestPlaylistDelete,
        PlayerUiAction.ConfirmPlaylistDelete,
        is PlayerUiAction.RemovePlaylistOccurrence,
        is PlayerUiAction.RemoveBookmark,
        -> true

        else -> false
    }

internal fun PlayerUiAction.requiresSettingsAccess(): Boolean =
    when (this) {
        PlayerUiAction.OpenSettings,
        PlayerUiAction.OpenMoreSettings,
        PlayerUiAction.OpenSafGrantSelector,
        PlayerUiAction.SaveSafGrantSelector,
        PlayerUiAction.OpenSafDocumentTree,
        is PlayerUiAction.SafGrantPicked,
        is PlayerUiAction.RequestSafGrantRemoval,
        PlayerUiAction.ConfirmSafGrantRemoval,
        PlayerUiAction.OpenDataStorageSelector,
        is PlayerUiAction.SelectDataStorage,
        PlayerUiAction.ConfirmDataStorageSelection,
        is PlayerUiAction.SetDraftTheme,
        is PlayerUiAction.SetDraftProfile,
        is PlayerUiAction.SetDraftDiagonal,
        is PlayerUiAction.SetDraftLongPress,
        is PlayerUiAction.SetDraftIgnoreLeadingArticles,
        is PlayerUiAction.SetDraftAutoSearch,
        is PlayerUiAction.SetDraftDimBacklight,
        is PlayerUiAction.SetDraftReplayGainMode,
        is PlayerUiAction.SetDraftReplayGainTarget,
        is PlayerUiAction.SetDraftRebuild,
        PlayerUiAction.SaveSettings,
        PlayerUiAction.SaveMoreSettings,
        PlayerUiAction.OpenCalibration,
        is PlayerUiAction.AcceptCalibration,
        PlayerUiAction.OpenScreenDiagonal,
        is PlayerUiAction.AcceptScreenDiagonal,
        -> true

        else -> false
    }

internal fun PlayerUiAction.isPlaylistMutation(): Boolean =
    when (this) {
        PlayerUiAction.OpenAiPlaylistSelector,
        is PlayerUiAction.OpenPlaylistSelector,
        is PlayerUiAction.AddToPlaylist,
        PlayerUiAction.RequestNewPlaylist,
        is PlayerUiAction.RequestPlaylistRename,
        PlayerUiAction.ConfirmPlaylistName,
        is PlayerUiAction.RequestPlaylistDelete,
        PlayerUiAction.ConfirmPlaylistDelete,
        is PlayerUiAction.RemovePlaylistOccurrence,
        is PlayerUiAction.RemoveBookmark,
        -> true

        else -> false
    }

internal fun PlaybackQueueSnapshot?.toInitialNowPlaying(): NowPlayingUiState {
    val queue = this ?: return NowPlayingUiState()
    val occurrence = queue.orderedOccurrences().firstOrNull { it.occurrenceId == queue.checkpoint.occurrenceId }
    return NowPlayingUiState(
        mediaId = occurrence?.mediaId,
        positionFraction = (queue.checkpoint.positionMs / 330_000f).coerceIn(0f, 1f),
        elapsed = "%d:%02d".format(queue.checkpoint.positionMs / 60_000L, queue.checkpoint.positionMs / 1_000L % 60L),
        playing = queue.checkpoint.playWhenReady,
        shuffle = queue.checkpoint.shuffle,
        repeat = queue.checkpoint.repeat,
        bookTitleContext = queue.bookTitleContext,
        artwork = ArtworkUiState(queue.checkpoint.chosenBindingId),
    )
}

internal fun playbackDiagnosticsFor(
    mediaId: MediaId?,
    queueMediaIds: List<MediaId>,
    featuresByMediaId: Map<MediaId, AiFeaturePoint>,
    pteByMediaId: Map<MediaId, PlayerPteFeature>,
    replayGainByMediaId: Map<MediaId, PlayerReplayGainValue>,
    topInferredGenreByMediaId: Map<MediaId, PlayerTopInferredGenre>,
): PlaybackDiagnosticsUiState {
    val features = mediaId?.let(featuresByMediaId::get)
    val pte = mediaId?.let(pteByMediaId::get)
    val genre = mediaId?.let(topInferredGenreByMediaId::get)
    val selectedGains = selectReplayGainQ8ByMediaId(queueMediaIds, replayGainByMediaId)
    val gainQ8 = mediaId?.let(selectedGains::get)
    return PlaybackDiagnosticsUiState(
        arousal = features?.arousal,
        valence = features?.valence,
        pte = pte?.value,
        replayGainDb = gainQ8?.toInt()?.div(256f),
        genre = genre?.let { PlaybackGenreDiagnostic(it.confidence, it.label) },
    )
}

internal fun artworkForProjection(
    oldMediaId: MediaId?,
    oldArtwork: ArtworkUiState,
    newMediaId: MediaId?,
    newBindingId: Long?,
): ArtworkUiState =
    if (
        oldMediaId == newMediaId &&
        newBindingId != null &&
        oldArtwork.sourceBindingId == newBindingId
    ) {
        oldArtwork
    } else {
        ArtworkUiState(newBindingId)
    }

internal fun PlaybackQueueSnapshot.orderedOccurrences() =
    if (checkpoint.shuffle) {
        occurrences.sortedBy {
            it.shuffleOrdinal
        }
    } else {
        occurrences
    }
