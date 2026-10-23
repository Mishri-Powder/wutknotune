package link.roadio.riff.ui

import android.graphics.Bitmap
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.playback.AudiobookSpeed
import link.roadio.riff.player.core.selection.AiHeatmap
import link.roadio.riff.player.core.selection.AiPteBand
import link.roadio.riff.player.core.settings.PlayerDimBacklightMode
import link.roadio.riff.player.core.settings.SettingsAccess
import link.roadio.riff.player.core.settings.SettingsEntry
import java.util.Locale

/** Toolkit-neutral state and action boundary consumed by the Player composition. */
interface PlayerUiController {
    val state: PlayerUiState

    fun observe(observer: (PlayerUiState) -> Unit): AutoCloseable

    fun dispatch(action: PlayerUiAction)
}

enum class PlayerScreen {
    PLAYBACK,
    MAIN,
    HIERARCHY,
    AZ_SEARCH,
    AI,
    AI_ACTIONS,
    SETTINGS,
    SETTINGS_MORE,
    SAF_GRANTS,
    DATA_STORAGE,
    ROW_ACTIONS,
    PLAYLIST_SELECTOR,
    CALIBRATION,
    SCREEN_DIAGONAL,
}

internal fun requiresInitialSafGrantSelection(hasPersistedReadGrant: Boolean?): Boolean = hasPersistedReadGrant == false

enum class HierarchyRoot(
    val label: String,
    val categoryId: String,
    val icon: String,
) {
    ALBUMS("Albums", "category:albums", "album"),
    ARTISTS("Artists", "category:artists", "artist"),
    GENRES("Genres", "category:genres", "genre"),
    PLAYLISTS("Playlists", "category:playlists", "playlist"),
    FOLDERS("Folders", "category:folders", "folder"),
}

enum class ContentMode { MUSIC, AUDIOBOOK }

enum class AudiobookChoice { MUSIC, BOOK, AUTO }

internal fun HierarchyRoot.labelFor(contentMode: ContentMode): String =
    when (this) {
        HierarchyRoot.ALBUMS -> if (contentMode == ContentMode.AUDIOBOOK) "Titles" else label
        HierarchyRoot.ARTISTS -> if (contentMode == ContentMode.AUDIOBOOK) "Authors" else label
        HierarchyRoot.PLAYLISTS -> if (contentMode == ContentMode.AUDIOBOOK) "Bookmarks" else label
        else -> label
    }

enum class Preference { NEUTRAL, DISLIKED, LIKED }

data class ReactionState(
    val preference: Preference = Preference.NEUTRAL,
    val ownBanned: Boolean = false,
)

enum class ColorScheme { DAY, NIGHT, SYSTEM }

internal fun ColorScheme.resolvedNight(systemNight: Boolean): Boolean =
    when (this) {
        ColorScheme.DAY -> false
        ColorScheme.NIGHT -> true
        ColorScheme.SYSTEM -> systemNight
    }

internal fun ColorScheme.next(): ColorScheme =
    when (this) {
        ColorScheme.DAY -> ColorScheme.NIGHT
        ColorScheme.NIGHT -> ColorScheme.SYSTEM
        ColorScheme.SYSTEM -> ColorScheme.DAY
    }

enum class DiagonalChoice { DIAGONAL, CARD, AUTO }

enum class DataLocation { HU, SD }

enum class DatabaseRebuild { AU, IA }

enum class ReplayGainMode { N, I, F }

enum class ReplayGainTarget(
    val displayValue: Int,
) {
    ZERO(0),
    LUFS_16(16),
    LUFS_18(18),
    LUFS_23(23),
}

enum class BrowserKind {
    ROOT,
    CATEGORY,
    ALBUM,
    ARTIST,
    GENRE,
    FOLDER,
    TRACK,
    PLAYLIST,
    AI,
}

data class PlaybackSourceSegment(
    val displayName: String,
    val mimeType: String,
)

data class PlaybackSourceRef(
    val bindingId: Long,
    val rootBindingId: Long,
    /** Path below the grant root, including the media file as the final segment. */
    val segments: List<PlaybackSourceSegment>,
    val beginUs: Long? = null,
    val endUs: Long? = null,
)

data class TocChapter(
    val startMs: Long,
    val title: String? = null,
)

enum class ArtworkOrigin { EMBEDDED, DIRECTORY, DEFAULT }

data class ArtworkUiState(
    val sourceBindingId: Long? = null,
    val bitmap: Bitmap? = null,
    val origin: ArtworkOrigin = ArtworkOrigin.DEFAULT,
)

internal enum class SettingsItem {
    USER,
    LONG_PRESS,
    AUTO_SEARCH,
    IGNORE_LEADING_ARTICLES,
    THEME,
    MORE,
    MEDIA_FOLDERS,
    DIAGONAL,
    DATA_LOCATION,
    DIM_BACKLIGHT,
    REPLAY_GAIN,
    REBUILD_DB,
}

internal const val MEDIA_FOLDERS_HEADING = "Media folders…"

internal fun settingsItems(): List<SettingsItem> =
    listOf(
        SettingsItem.USER,
        SettingsItem.LONG_PRESS,
        SettingsItem.AUTO_SEARCH,
        SettingsItem.IGNORE_LEADING_ARTICLES,
        SettingsItem.THEME,
        SettingsItem.MORE,
    )

internal fun moreSettingsItems(): List<SettingsItem> =
    listOf(
        SettingsItem.MEDIA_FOLDERS,
        SettingsItem.DIAGONAL,
        SettingsItem.DATA_LOCATION,
        SettingsItem.DIM_BACKLIGHT,
        SettingsItem.REPLAY_GAIN,
        SettingsItem.REBUILD_DB,
    )

internal fun idleDestination(playbackActive: Boolean): PlayerScreen = if (playbackActive) PlayerScreen.PLAYBACK else PlayerScreen.MAIN

internal fun libraryReloadDestination(
    current: PlayerScreen,
    hierarchyPathValid: Boolean,
    aiPlaylistSelector: Boolean = false,
): PlayerScreen =
    when (current) {
        PlayerScreen.HIERARCHY,
        PlayerScreen.AZ_SEARCH,
        PlayerScreen.ROW_ACTIONS,
        -> {
            if (hierarchyPathValid) current else PlayerScreen.MAIN
        }

        PlayerScreen.PLAYLIST_SELECTOR -> {
            if (hierarchyPathValid || aiPlaylistSelector) current else PlayerScreen.MAIN
        }

        else -> {
            current
        }
    }

data class BrowserItem(
    val id: String,
    val label: String,
    val kind: BrowserKind,
    val icon: String? = null,
    val playable: Boolean = false,
    val enabled: Boolean = true,
    val mediaId: MediaId? = null,
    val artist: String = "",
    val album: String = "",
    /** Exact SAF provider filename; never inferred from title or parsed for numbers. */
    val sourceFileName: String = "",
    val fragment: Boolean = false,
    val discNumber: Long? = null,
    val trackNumber: Long? = null,
    /** Stable reaction identity; folder occurrences may have distinct row IDs. */
    val reactionKey: String = id,
    val sourceBindingId: Long? = null,
    val playbackSource: PlaybackSourceRef? = null,
    val playlistId: Long? = null,
    val playlistRevisionId: Long? = null,
    val playlistOccurrenceId: Long? = null,
    val bookmarks: Boolean = false,
    val bookmarkMarkId: Long? = null,
    val bookmarkOrdinal: Int? = null,
    val bookmarkPositionUs: Long? = null,
    /** Effective Book classification after profile overrides and AlbumGroup propagation. */
    val audiobook: Boolean = false,
    /** Automatic MDIA Q106833 result before profile overrides. */
    val automaticAudiobook: Boolean = false,
    /** True only when Ban/Dislike/Like may affect every media member represented by this row. */
    val aiReactionEligible: Boolean = true,
    val albumGroupId: Long? = null,
    val tocMarkersMs: List<Long> = emptyList(),
    val children: List<BrowserItem> = emptyList(),
    val ignoreLeadingArticles: Boolean = false,
    val genreId: Long? = null,
    /** Catalog display decoration must not replace the original search/sort input. */
    val comparisonNameOverride: String? = null,
) {
    internal val comparisonName: String get() = comparisonNameOverride ?: label
    internal val comparisonLabel: String = browseComparisonName(comparisonName, ignoreLeadingArticles)
}

// AI-INTENT: Playlist containers and frozen Bookmarks share the body-and-More row regardless of
// member classification; eligible Music track occurrences still use the reaction tier.
// Authority: https://wiki.271.pics/Riff/LATEST/hierarchy-screen-scope-and-shared-implementation#rows-and-actions
internal val BrowserItem.hierarchyUsesActionTier: Boolean
    get() = !bookmarks && kind != BrowserKind.PLAYLIST && aiReactionEligible

enum class PlaylistNameOperation { CREATE, RENAME }

data class AiSelectionUiState(
    val size: Int = 20,
    val pteBands: Set<AiPteBand> = emptySet(),
    val arousal: Float = 0f,
    val valence: Float = 0f,
)

internal fun AiSelectionUiState.selectSize(size: Int): AiSelectionUiState = if (size in setOf(10, 20, 50)) copy(size = size) else this

internal fun AiSelectionUiState.togglePteBand(value: AiPteBand): AiSelectionUiState =
    when {
        value in pteBands -> copy(pteBands = pteBands - value)
        pteBands.size < 2 -> copy(pteBands = pteBands + value)
        else -> this
    }

internal fun AiSelectionUiState.isPteBandEnabled(
    value: AiPteBand,
    pteAvailable: Boolean,
): Boolean = pteAvailable && (value in pteBands || pteBands.size < 2)

internal fun AiSelectionUiState.selectPoint(
    arousal: Float,
    valence: Float,
): AiSelectionUiState =
    if (arousal.isFinite() && valence.isFinite()) {
        copy(
            arousal = arousal.coerceIn(-1f, 1f),
            valence = valence.coerceIn(-1f, 1f),
        )
    } else {
        this
    }

data class PlaylistNameUiRequest(
    val operation: PlaylistNameOperation,
    val playlistId: Long? = null,
)

data class PlaylistDeleteUiRequest(
    val playlistId: Long,
    val name: String,
)

data class PlaylistSelectorUiItem(
    val playlistId: Long?,
    val label: String,
)

internal const val NEW_PLAYLIST_LABEL = "New Playlist"
internal const val AI_PLAYLIST_SOURCE_ITEM_ID = "ai:generated"

data class NowPlayingUiState(
    val mediaId: MediaId? = null,
    val title: String = "Nothing played yet",
    val artist: String = "",
    val album: String = "",
    val chapter: String? = null,
    val positionFraction: Float = 0f,
    val elapsed: String = "0:00",
    val duration: String = "0:00",
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val aiReactionEligible: Boolean = false,
    val bookTitleContext: Boolean = false,
    val effectiveBook: Boolean = false,
    val artwork: ArtworkUiState = ArtworkUiState(),
)

data class PlaybackGenreDiagnostic(
    val confidence: Float,
    val label: String,
)

data class PlaybackDiagnosticsUiState(
    val arousal: Float? = null,
    val valence: Float? = null,
    val pte: Float? = null,
    val replayGainDb: Float? = null,
    val genre: PlaybackGenreDiagnostic? = null,
)

internal fun playbackStatusLine(diagnostics: PlaybackDiagnosticsUiState): String =
    listOf(
        diagnosticValue("A", diagnostics.arousal),
        diagnosticValue("V", diagnostics.valence),
        diagnosticValue("PTE", diagnostics.pte),
        diagnostics.replayGainDb?.let { String.format(Locale.ROOT, "RG %.1f", it) } ?: "RG --",
        diagnostics.genre?.let { String.format(Locale.ROOT, "%.1f/%s", it.confidence, it.label) } ?: "--/--",
    ).joinToString(" ")

private fun diagnosticValue(
    label: String,
    value: Float?,
): String = value?.let { String.format(Locale.ROOT, "%s %.1f", label, it) } ?: "$label --"

internal fun completedPlayback(state: NowPlayingUiState): NowPlayingUiState =
    if (state.repeat) state else state.copy(playing = false, positionFraction = 1f)

internal fun trackReactionKey(mediaId: MediaId): String = "track:${mediaId.hex()}"

internal fun isAudiobookOverrideKey(objectKey: String): Boolean =
    (objectKey.startsWith("track:") || objectKey.startsWith("folder:") || objectKey.startsWith("album:")) &&
        objectKey.substringAfter(':').isNotBlank()

internal fun currentChapterLabel(
    chapters: List<TocChapter>,
    positionMs: Long,
    durationMs: Long?,
): String? {
    if (chapters.any { it.startMs < 0L } || chapters.zipWithNext().any { (left, right) -> left.startMs >= right.startMs }) {
        return null
    }
    val available = chapters.filter { chapter -> durationMs == null || chapter.startMs < durationMs }
    if (available.size <= 1) return null
    val index =
        available
            .indexOfLast { chapter -> chapter.startMs <= positionMs.coerceAtLeast(0L) }
            .coerceAtLeast(0)
    return available[index].title?.takeIf(String::isNotBlank) ?: "Chapter ${index + 1}"
}

internal fun nextReactionState(
    current: ReactionState,
    requested: Preference?,
    toggleBan: Boolean,
): ReactionState =
    current.copy(
        preference =
            requested?.let {
                if (current.preference == it) Preference.NEUTRAL else it
            } ?: current.preference,
        ownBanned = if (toggleBan) !current.ownBanned else current.ownBanned,
    )

internal fun nextQueueIndex(
    queueSize: Int,
    currentIndex: Int,
    repeat: Boolean,
): Int? {
    if (queueSize <= 0) return null
    val normalized = currentIndex.coerceIn(0, queueSize - 1)
    return if (normalized + 1 < queueSize) normalized + 1 else 0.takeIf { repeat }
}

data class PlayerSettingsUiState(
    val profileId: Int = 1,
    val colorScheme: ColorScheme = ColorScheme.SYSTEM,
    val diagonal: DiagonalChoice = DiagonalChoice.AUTO,
    val dataLocation: DataLocation = DataLocation.HU,
    val longPressStartsPlayback: Boolean = false,
    val autoSearch: Boolean = true,
    val ignoreLeadingArticles: Boolean = true,
    val dimBacklight: PlayerDimBacklightMode = PlayerDimBacklightMode.AUTO,
    val replayGainMode: ReplayGainMode = ReplayGainMode.F,
    val replayGainTarget: ReplayGainTarget = ReplayGainTarget.LUFS_23,
    val pendingRebuild: Set<DatabaseRebuild> = emptySet(),
    val calibrationPpiX: Float? = null,
    val calibrationPpiY: Float? = null,
)

data class SafGrantUiItem(
    val id: String,
    val label: String,
)

internal const val SAF_ADD_ITEM_ID = "saf:add"

internal fun safGrantSelectorItems(grants: List<SafGrantUiItem>): List<SafGrantUiItem> =
    listOf(SafGrantUiItem(SAF_ADD_ITEM_ID, "Add..")) + grants

data class DataStorageUiItem(
    val id: String,
    val label: String,
    val portableVolumeId: String? = null,
)

internal const val INTERNAL_DATA_STORAGE_ID = "internal"

data class PlayerUiState(
    val screen: PlayerScreen,
    val root: BrowserItem,
    val browserPath: List<String> = emptyList(),
    val hierarchyRoot: HierarchyRoot? = null,
    val page: Int = 0,
    val pageSize: Int = 1,
    /** First visible selector row and its pixel offset; process-memory navigation state only. */
    val selectorIndex: Int = 0,
    val selectorOffset: Int = 0,
    val focusedItemId: String? = null,
    /** Applied progressive A-Z filter for the exact current browser container. */
    val azSteps: List<AzGroup> = emptyList(),
    val azBreadcrumbs: List<AzBreadcrumbScope> = emptyList(),
    /** Non-null throughout search/auxiliary menus; Cancel discards changes after the latest explicit save. */
    val azDraftSteps: List<AzGroup>? = null,
    /** Set only by an explicit hierarchy transition, never by library refresh. */
    val azAutoEntryPending: Boolean = false,
    /** Complete rows fitting the ordinary result viewport after search closes. */
    val azVisibleCapacity: Int = 1,
    val azSnapshot: AzSearchSnapshot? = null,
    val azAutoApplyPending: Boolean = false,
    val azApplyRevision: Long = 0,
    val nowPlaying: NowPlayingUiState,
    val playbackDiagnostics: PlaybackDiagnosticsUiState = PlaybackDiagnosticsUiState(),
    val reactions: Map<String, ReactionState> = emptyMap(),
    val contentMode: ContentMode = ContentMode.MUSIC,
    val audiobookOverrides: Map<String, AudiobookChoice> = emptyMap(),
    val audiobookSpeed: AudiobookSpeed = AudiobookSpeed.NORMAL,
    /** Uncommitted ROW_ACTIONS choice; persisted once when the screen is left. */
    val audiobookChoiceDraft: AudiobookChoice? = null,
    val audiobookCommitInProgress: Boolean = false,
    val aiSelection: AiSelectionUiState = AiSelectionUiState(),
    val aiDataAvailable: Boolean = false,
    val aiPteAvailable: Boolean = false,
    val aiHeatmap: AiHeatmap? = null,
    val settings: PlayerSettingsUiState = PlayerSettingsUiState(),
    val settingsDraft: PlayerSettingsUiState? = null,
    val moreSettingsDraft: PlayerSettingsUiState? = null,
    val actionsItemId: String? = null,
    val playlistSourceItemId: String? = null,
    val playlistSelectorChoices: List<PlaylistSelectorUiItem> = emptyList(),
    val playlistNameRequest: PlaylistNameUiRequest? = null,
    val playlistNameDraft: String = "",
    val pendingPlaylistDelete: PlaylistDeleteUiRequest? = null,
    val playlistMutationInProgress: Boolean = false,
    val playlistMutationError: String? = null,
    val settingsSaveInProgress: Boolean = false,
    val playlistMutationsAllowed: Boolean = true,
    val settingsAccess: SettingsAccess = SettingsAccess.ALLOWED,
    val settingsEntry: SettingsEntry = SettingsEntry.LONG_PRESS,
    val bookmarkMarkAllowed: Boolean = false,
    val status: String = "Loading library…",
    val indexerStatus: String = "",
    val safListingBugWarning: link.roadio.riff.player.library.SafListingNotice? = null,
    val libraryLoading: Boolean = false,
    val libraryPlayableTrackCount: Int? = null,
    val libraryGeneration: Long? = null,
    val selectedStorageAvailable: Boolean = true,
    val storageMigrationInProgress: Boolean = false,
    val dataStorageLocations: List<DataStorageUiItem> = emptyList(),
    val activeDataStorageId: String = INTERNAL_DATA_STORAGE_ID,
    val activeDataStorageLabel: String = "Internal storage",
    val dataStorageDraftId: String? = null,
    val safGrants: List<SafGrantUiItem> = emptyList(),
    val pendingSafGrantRemoval: SafGrantUiItem? = null,
    val safGrantCommitInProgress: Boolean = false,
    val queueMediaIds: List<MediaId> = emptyList(),
    val queueIndex: Int = -1,
) {
    fun currentNode(): BrowserItem =
        browserPath.fold(root) { node, id ->
            node.children.firstOrNull { it.id == id } ?: node
        }

    fun azQuery(): AzSearchQuery = AzSearchQuery(currentNode().children, azSteps, azDraftSteps)

    fun currentAzSnapshot(): AzSearchSnapshot? = azSnapshot?.takeIf { it.query.sameAs(azQuery()) }

    fun azAppliedReady(): Boolean = azSteps.isEmpty() || azDisplaySnapshot() != null

    /** Pending work retains the last complete presentation from this exact source. */
    fun azDisplaySnapshot(): AzSearchSnapshot? =
        azSnapshot?.takeIf {
            it.query.source === currentNode().children &&
                (it.query.applied == azSteps || (screen == PlayerScreen.AZ_SEARCH && azDraftSteps != null))
        }

    fun azDisplayedSteps(): List<AzGroup> =
        if (screen == PlayerScreen.AZ_SEARCH && azDraftSteps != null) {
            if (azDraftSteps.isEmpty()) emptyList() else azDisplaySnapshot()?.query?.let { it.draft ?: it.applied } ?: azSteps
        } else {
            azSteps
        }

    fun visibleChildren(): List<BrowserItem> {
        val children = currentNode().children
        if (screen == PlayerScreen.AZ_SEARCH && azDraftSteps != null) {
            if (azDraftSteps.isEmpty()) return children
            azDisplaySnapshot()?.let { return it.draftItems }
        }
        if (azSteps.isEmpty()) return children
        return azDisplaySnapshot()?.visibleItems.orEmpty()
    }

    /** Artist/Genre is the remembered parent; its selected child surface is Albums. */
    fun visibleHierarchyRoot(): HierarchyRoot? =
        if (browserPath.size > 1 &&
            (hierarchyRoot == HierarchyRoot.ARTISTS || hierarchyRoot == HierarchyRoot.GENRES)
        ) {
            HierarchyRoot.ALBUMS
        } else {
            hierarchyRoot
        }

    fun visibleHierarchyLabel(): String? = visibleHierarchyRoot()?.labelFor(contentMode)
}

internal val PlayerUiState.aiDataReady: Boolean
    get() = aiDataAvailable && aiHeatmap != null

internal fun PlayerUiState.effectiveAiSelection(): AiSelectionUiState =
    if (aiDataReady) {
        aiSelection
    } else {
        aiSelection.copy(arousal = 0f, valence = 0f)
    }

internal val PlayerUiState.aiPlayEnabled: Boolean
    get() = selectedStorageAvailable && aiDataReady

internal fun mainStatusLine(state: PlayerUiState): String =
    when {
        !state.selectedStorageAvailable -> "Selected application-data storage is unavailable"
        state.indexerStatus.isNotBlank() -> state.indexerStatus
        state.libraryLoading -> "Loading updated music library…"
        state.libraryPlayableTrackCount == null -> "Preparing music library…"
        state.libraryPlayableTrackCount == 0 -> "No playable tracks found in selected media folders"
        else -> ""
    }

sealed interface PlayerUiAction {
    data object AcknowledgeSafListingBug : PlayerUiAction

    data object IdleTimeout : PlayerUiAction

    /** Playback-engine completion hook; looped queues must not emit this event. */
    data object PlaybackFinished : PlayerUiAction

    data object TogglePlaybackSurface : PlayerUiAction

    data object OpenMain : PlayerUiAction

    data object ToggleContentMode : PlayerUiAction

    data object AdvanceAudiobookSpeed : PlayerUiAction

    data object ResetAudiobookSpeed : PlayerUiAction

    data class OpenCategory(
        val categoryId: String,
    ) : PlayerUiAction

    data class NavigateTo(
        val itemId: String,
    ) : PlayerUiAction

    data object NavigateParent : PlayerUiAction

    data class SelectAzBreadcrumb(
        val path: List<String>,
        val steps: List<AzGroup>,
        val inSearch: Boolean = false,
    ) : PlayerUiAction

    data object OpenAzSearch : PlayerUiAction

    data object CancelAzSearch : PlayerUiAction

    data object AzBackspace : PlayerUiAction

    data class SelectAzGroup(
        val group: AzGroup,
    ) : PlayerUiAction

    data class ReportHierarchyViewport(
        val rowHeightPx: Int,
        val rowGapPx: Int,
        val viewportHeightPx: Int,
        val visibleCapacity: Int,
    ) : PlayerUiAction

    data class PreviousPage(
        val pageSize: Int,
    ) : PlayerUiAction

    data class NextPage(
        val pageSize: Int,
    ) : PlayerUiAction

    data class RememberFocus(
        val itemId: String,
    ) : PlayerUiAction

    data class RememberSelectorPosition(
        val index: Int,
        val offset: Int,
        val pageSize: Int,
    ) : PlayerUiAction

    data class Play(
        val itemId: String,
    ) : PlayerUiAction

    data class SetReaction(
        val itemId: String,
        val preference: Preference? = null,
        val toggleBan: Boolean = false,
    ) : PlayerUiAction

    data class SetCurrentReaction(
        val preference: Preference? = null,
        val toggleBan: Boolean = false,
    ) : PlayerUiAction

    data class SetAudiobookChoice(
        val objectKey: String,
        val choice: AudiobookChoice,
    ) : PlayerUiAction

    data class OpenRowActions(
        val itemId: String,
    ) : PlayerUiAction

    data object CloseRowActions : PlayerUiAction

    data class OpenPlaylistSelector(
        val sourceItemId: String,
    ) : PlayerUiAction

    data object ClosePlaylistSelector : PlayerUiAction

    data class AddToPlaylist(
        val playlistId: Long,
    ) : PlayerUiAction

    data object RequestNewPlaylist : PlayerUiAction

    data class RequestPlaylistRename(
        val playlistId: Long,
    ) : PlayerUiAction

    data class UpdatePlaylistName(
        val value: String,
    ) : PlayerUiAction

    data object ConfirmPlaylistName : PlayerUiAction

    data object CancelPlaylistName : PlayerUiAction

    data class RequestPlaylistDelete(
        val playlistId: Long,
    ) : PlayerUiAction

    data object ConfirmPlaylistDelete : PlayerUiAction

    data object CancelPlaylistDelete : PlayerUiAction

    data class RemovePlaylistOccurrence(
        val playlistId: Long,
        val occurrenceId: Long,
    ) : PlayerUiAction

    data object MarkBookmark : PlayerUiAction

    data class RemoveBookmark(
        val playlistId: Long,
        val markId: Long,
    ) : PlayerUiAction

    data object OpenSettings : PlayerUiAction

    data object OpenSettingsLongPress : PlayerUiAction

    data object OpenSettingsRecovery : PlayerUiAction

    data object OpenMoreSettings : PlayerUiAction

    data object CloseMoreSettings : PlayerUiAction

    data object OpenSafGrantSelector : PlayerUiAction

    data object CancelSafGrantSelector : PlayerUiAction

    data object SaveSafGrantSelector : PlayerUiAction

    data object OpenSafDocumentTree : PlayerUiAction

    data class SafGrantPicked(
        val uri: String,
        val grantFlags: Int,
    ) : PlayerUiAction

    data object SafGrantPickerFailed : PlayerUiAction

    data class RequestSafGrantRemoval(
        val grantId: String,
    ) : PlayerUiAction

    data object ConfirmSafGrantRemoval : PlayerUiAction

    data object CancelSafGrantRemoval : PlayerUiAction

    data object OpenDataStorageSelector : PlayerUiAction

    data object CancelDataStorageSelector : PlayerUiAction

    data class SelectDataStorage(
        val storageId: String,
    ) : PlayerUiAction

    data object ConfirmDataStorageSelection : PlayerUiAction

    data class SetDraftTheme(
        val scheme: ColorScheme,
    ) : PlayerUiAction

    data class SetDraftProfile(
        val profileId: Int,
    ) : PlayerUiAction

    data class SetDraftDiagonal(
        val diagonal: DiagonalChoice,
    ) : PlayerUiAction

    data class SetDraftLongPress(
        val enabled: Boolean,
    ) : PlayerUiAction

    data class SetDraftIgnoreLeadingArticles(
        val enabled: Boolean,
    ) : PlayerUiAction

    data class SetDraftAutoSearch(
        val enabled: Boolean,
    ) : PlayerUiAction

    data class SetDraftDimBacklight(
        val mode: PlayerDimBacklightMode,
    ) : PlayerUiAction

    data class SetDraftReplayGainMode(
        val mode: ReplayGainMode,
    ) : PlayerUiAction

    data class SetDraftReplayGainTarget(
        val target: ReplayGainTarget,
    ) : PlayerUiAction

    data class SetDraftRebuild(
        val rebuild: DatabaseRebuild,
    ) : PlayerUiAction

    data object SaveSettings : PlayerUiAction

    data object SaveMoreSettings : PlayerUiAction

    data object CancelSettings : PlayerUiAction

    data object OpenCalibration : PlayerUiAction

    data object CancelCalibration : PlayerUiAction

    data class AcceptCalibration(
        val ppiX: Float,
        val ppiY: Float,
    ) : PlayerUiAction

    data object OpenScreenDiagonal : PlayerUiAction

    data object CancelScreenDiagonal : PlayerUiAction

    data class AcceptScreenDiagonal(
        val ppiX: Float,
    ) : PlayerUiAction

    data class SetAiSize(
        val size: Int,
    ) : PlayerUiAction

    data class ToggleAiPteBand(
        val pteBand: AiPteBand,
    ) : PlayerUiAction

    data class SetAiPoint(
        val arousal: Float,
        val valence: Float,
    ) : PlayerUiAction

    data object OpenAiActions : PlayerUiAction

    data object CloseAiActions : PlayerUiAction

    data object CreateAiQueue : PlayerUiAction

    data object OpenAiPlaylistSelector : PlayerUiAction

    data object TogglePlayback : PlayerUiAction

    data object Previous : PlayerUiAction

    data object Next : PlayerUiAction

    data object ToggleShuffle : PlayerUiAction

    data object ToggleRepeat : PlayerUiAction

    data class Seek(
        val fraction: Float,
    ) : PlayerUiAction
}

internal fun PlayerUiAction.requiresReadyAiData(): Boolean =
    this is PlayerUiAction.SetAiPoint ||
        this == PlayerUiAction.CreateAiQueue ||
        (
            this is PlayerUiAction.OpenCategory &&
                categoryId == "category:ai"
        )

internal fun PlayerUiAction.allowedDuringAzOverlay(): Boolean =
    when (this) {
        PlayerUiAction.CancelAzSearch,
        PlayerUiAction.AzBackspace, PlayerUiAction.IdleTimeout,
        is PlayerUiAction.SelectAzGroup, is PlayerUiAction.ReportHierarchyViewport,
        PlayerUiAction.OpenMain, PlayerUiAction.TogglePlaybackSurface, PlayerUiAction.NavigateParent,
        PlayerUiAction.OpenAzSearch, is PlayerUiAction.NavigateTo, is PlayerUiAction.Play,
        is PlayerUiAction.SetReaction, is PlayerUiAction.OpenRowActions,
        is PlayerUiAction.RememberFocus, is PlayerUiAction.RememberSelectorPosition,
        is PlayerUiAction.PreviousPage, is PlayerUiAction.NextPage,
        -> true

        is PlayerUiAction.SelectAzBreadcrumb -> inSearch

        else -> false
    }
