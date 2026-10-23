package link.roadio.riff.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.playback.AudiobookSpeed
import link.roadio.riff.player.core.selection.AiFeaturePoint
import link.roadio.riff.player.core.selection.AiHeatmapBuilder
import link.roadio.riff.player.core.selection.AiPteBand
import link.roadio.riff.player.core.selection.AiQueueCandidate
import link.roadio.riff.player.core.selection.AiQueueGenerator
import link.roadio.riff.player.core.settings.ApplicationMode
import link.roadio.riff.player.core.settings.BookmarkActionPolicy
import link.roadio.riff.player.core.settings.MotionRestriction
import link.roadio.riff.player.core.settings.PlayerDataLocation
import link.roadio.riff.player.core.settings.PlayerDatabaseRebuild
import link.roadio.riff.player.core.settings.PlayerDimBacklightMode
import link.roadio.riff.player.core.settings.PlayerReplayGainMode
import link.roadio.riff.player.core.settings.PlayerReplayGainTarget
import link.roadio.riff.player.core.settings.PlayerSettings
import link.roadio.riff.player.core.settings.PlayerSettingsCommand
import link.roadio.riff.player.core.settings.PlayerSettingsController
import link.roadio.riff.player.core.settings.PlayerSettingsSnapshot
import link.roadio.riff.player.core.settings.RestrictionSource
import link.roadio.riff.player.core.settings.SettingsAccess
import link.roadio.riff.player.core.settings.SettingsAccessPolicy
import link.roadio.riff.player.core.settings.SettingsEntry
import link.roadio.riff.player.library.CuetreeBrowserTree
import link.roadio.riff.player.library.PlayerCuetreeClient
import link.roadio.riff.player.library.PlayerLibraryModel
import link.roadio.riff.player.library.PlayerLibraryProjection
import link.roadio.riff.player.library.PlayerPteFeature
import link.roadio.riff.player.library.PlayerReplayGainValue
import link.roadio.riff.player.library.PlayerTopInferredGenre
import link.roadio.riff.player.playback.PlaybackEngineState
import link.roadio.riff.player.playback.PlayerPlaybackBridge
import link.roadio.riff.player.playback.PlayerPlaybackConnection
import link.roadio.riff.player.storage.AiPlaybackHistorySnapshot
import link.roadio.riff.player.storage.PlaybackCheckpoint
import link.roadio.riff.player.storage.PlaybackQueueKind
import link.roadio.riff.player.storage.PlaybackQueueSnapshot
import link.roadio.riff.player.storage.PlayerProcessStore
import link.roadio.riff.player.storage.PlayerSafGrantStore
import link.roadio.riff.player.storage.PlayerStateStore
import link.roadio.riff.player.storage.StoredAiSelection
import link.roadio.riff.player.storage.StoredAudiobookOverride
import link.roadio.riff.player.storage.StoredContentMode
import link.roadio.riff.player.storage.StoredPreference
import link.roadio.riff.player.storage.StoredReaction
import link.roadio.riff.storage.root.ApplicationDataLocation
import link.roadio.riff.storage.root.ApplicationDataSelection
import link.roadio.riff.storage.root.ApplicationDataStorage
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Sole production state machine exposed to the Player composition.
 *
 * It joins immutable Indexer generations, Player-owned SQLite, Media3 transport, and transient navigation
 * into one [PlayerUiState]. Background executors may perform blocking work, but only this controller commits
 * domain transitions and publishes replacement snapshots; Compose remains a renderer of those snapshots.
 */
class RiffPlayerUiController(
    context: Context,
) : PlayerUiController,
    AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newSingleThreadExecutor()
    private val profileWorkers = Executors.newSingleThreadExecutor()
    private val azWorkers = Executors.newSingleThreadExecutor()
    private var azFuture: Future<*>? = null
    private var azRequest: AzSearchQuery? = null
    private var azRequestToken = 0L

    @Volatile private var azClosed = false
    private var libraryReloadFuture: Future<*>? = null
    private var libraryReloadToken = 0L
    private val aiSelectionPersistenceGate = Any()
    private val pendingAiSelections = linkedMapOf<ProfileCommandContext<PlayerStateStore>, StoredAiSelection>()
    private var aiSelectionPersistenceScheduled = false

    // Accessed only by azWorkers, bounded to the currently browsed container.
    private var azPrepared: AzPreparedSection? = null
    private val observers = CopyOnWriteArrayList<(PlayerUiState) -> Unit>()
    private val dataStorage =
        ApplicationDataStorage(context).also {
            check(it.prepareForStartup()) { "Selected application-data storage is unavailable" }
        }
    private val uiConfig = UiConfig.load(context)
    private val recentHistoryGenerations = uiConfig.long("player.recentHistoryGenerations")
    private val bookmarkMinSpacingSeconds = uiConfig.long("player.bookmarkMinSpacingSeconds")
    private val aiSelectionModel = uiConfig.aiSelectionModel()
    private val heatmapValenceCells = uiConfig.int("ai.heatmapValenceCells")
    private val heatmapArousalCells = uiConfig.int("ai.heatmapArousalCells")
    private val safGrantStore = PlayerSafGrantStore(context)
    private val initialSelection = dataStorage.selection()

    @Volatile private var selectedStorageAvailable = dataStorage.selectedRootAvailable()
    private var playerStateStore: PlayerStateStore? = openPlayerStateStoreOrNull()
    private var commandEpoch = 0L
    private var activeProfileId = playerStateStore?.activeProfile() ?: 1
    private var draftProfileId: Int? = null
    private var activeQueue: PlaybackQueueSnapshot? = playerStateStore?.loadActiveQueue()
    private val installationPreferences = context.getSharedPreferences("riff-installation-settings", Context.MODE_PRIVATE)

    private fun installationDimBacklight(): PlayerDimBacklightMode =
        runCatching {
            PlayerDimBacklightMode.valueOf(installationPreferences.getString("dim-backlight", null) ?: "AUTO")
        }.getOrDefault(PlayerDimBacklightMode.AUTO)

    private val initialReplayGainMode =
        runCatching {
            PlayerReplayGainMode.valueOf(installationPreferences.getString("replay-gain-mode", null) ?: "F")
        }.getOrDefault(PlayerReplayGainMode.F)
    private val initialReplayGainTarget =
        runCatching {
            PlayerReplayGainTarget.valueOf(
                installationPreferences.getString("replay-gain-target", null) ?: PlayerReplayGainTarget.LUFS_23.name,
            )
        }.getOrDefault(PlayerReplayGainTarget.LUFS_23)
    private val initialSettings =
        playerStateStore
            ?.let { store ->
                runCatching { store.loadSettingsForProfile(activeProfileId, initialSelection.location.toPlayer()) }.getOrNull()
            }?.copy(
                dimBacklight = installationDimBacklight(),
                replayGainMode = initialReplayGainMode,
                replayGainTarget = initialReplayGainTarget,
            )
            ?: PlayerSettings(
                dataLocation = initialSelection.location.toPlayer(),
                dimBacklight = installationDimBacklight(),
                replayGainMode = initialReplayGainMode,
                replayGainTarget = initialReplayGainTarget,
            )
    private var settingsController = PlayerSettingsController(initialSettings, ::settingsAccessPolicy)

    @Volatile
    private var libraryProjection =
        PlayerLibraryProjection(
            contentMode = playerStateStore?.loadContentMode() ?: StoredContentMode.MUSIC,
            audiobookOverrides = playerStateStore?.loadAudiobookOverrides().orEmpty().toMap(),
        )
    private var tracks: List<BrowserItem> = emptyList()
    private var retainedTracks: List<BrowserItem> = emptyList()
    private val recordedBookDurations = mutableSetOf<Triple<String, Int, Long>>()
    private val bookmarkMode =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            ApplicationMode.AAOS
        } else {
            when (dataStorage.cachedApplicationMode()) {
                "phone" -> ApplicationMode.PHONE
                "aaos" -> ApplicationMode.AAOS
                else -> ApplicationMode.AUTOMOTIVE
            }
        }

    internal val systemBrightnessWorkaroundAllowed: Boolean
        get() = systemBrightnessWorkaroundEnabled(uiConfig.boolean("behavior.systemBrightnessWorkaroundEnabled"), bookmarkMode)

    @Volatile private var bookmarkMotion = MotionRestriction.UNKNOWN

    @Volatile private var settingsRecoveryAuthorized = false

    private fun settingsAccessPolicy(): SettingsAccessPolicy =
        SettingsAccessPolicy(
            bookmarkMode,
            bookmarkMotion,
            if (bookmarkMode == ApplicationMode.AAOS) RestrictionSource.AAOS_UXR else RestrictionSource.UNAVAILABLE,
            settingsRecoveryAuthorized,
        )

    fun canOpenSettingsDocumentTree(): Boolean = settingsAccessPolicy().access == SettingsAccess.ALLOWED

    private var bookmarkWriteInProgress = false
    private val bookmarkPolicy: BookmarkActionPolicy
        get() = BookmarkActionPolicy(bookmarkMode, bookmarkMotion)
    private var aaosRestrictions: AaosUxRestrictionMonitor? = null
    private var libraryRoot: BrowserItem =
        CuetreeBrowserTree
            .emptyRoot(libraryProjection.contentMode)
            .withBrowseNamePolicy(settingsController.snapshot().committed.ignoreLeadingArticles)
    private var mediaAliases: Map<MediaId, MediaId> = emptyMap()
    private var aiFeaturesByMediaId: Map<MediaId, AiFeaturePoint> = emptyMap()
    private var aiPteByMediaId: Map<MediaId, PlayerPteFeature> = emptyMap()
    private var replayGainByMediaId: Map<MediaId, PlayerReplayGainValue> = emptyMap()
    private var topInferredGenreByMediaId: Map<MediaId, PlayerTopInferredGenre> = emptyMap()
    private var aiAugmentationGeneration: Long? = null
    private var audiobookMediaIds: Set<MediaId> = emptySet()
    private var effectiveBookMediaIds: Set<MediaId> = emptySet()
    private var tocChaptersByBindingId: Map<Long, List<TocChapter>> = emptyMap()

    @Volatile
    private var pendingAiQueue: List<AiQueueCandidate>? = null

    @Volatile
    private var aiRequestToken = 0L

    @Volatile
    private var aiHeatmapRequestToken = 0L

    @Volatile
    private var aiPteAvailabilityRequestToken = 0L
    private val navigation = PlayerNavigationState()
    private var rowActionsReturn: PlayerUiState? = null
    private var libraryClient = newLibraryClient()
    private var libraryClientEnabled = false
    private val playbackConnection: PlayerPlaybackConnection
    private var playbackObservation: AutoCloseable? = null
    private val storageReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                receiverContext: Context,
                intent: Intent,
            ) {
                workers.execute(::reconcileSelectedStorage)
            }
        }

    private val safGrantEditor =
        PlayerSafGrantEditor(
            object : PlayerSafGrantEditor.Host {
                override val grantStore get() = safGrantStore
                override val executor get() = workers
                override val libraryClient get() = this@RiffPlayerUiController.libraryClient
                override var libraryClientEnabled: Boolean
                    get() = this@RiffPlayerUiController.libraryClientEnabled
                    set(value) {
                        this@RiffPlayerUiController.libraryClientEnabled = value
                    }
                override val selectedStorageAvailable get() = this@RiffPlayerUiController.selectedStorageAvailable
                override val playbackConnection get() = this@RiffPlayerUiController.playbackConnection

                override fun currentGrants() = currentSafGrants()

                override fun updateState(transform: (PlayerUiState) -> PlayerUiState) = this@RiffPlayerUiController.updateState(transform)
            },
        )

    override var state: PlayerUiState =
        PlayerUiState(
            screen = PlayerScreen.MAIN,
            root = projectPlaylistRoot(),
            nowPlaying = activeQueue.toInitialNowPlaying(),
            reactions = playerStateStore?.loadReactions().orEmpty().mapValues { it.value.toUi() },
            contentMode = libraryProjection.contentMode.toUi(),
            audiobookOverrides = libraryProjection.audiobookOverrides.mapValues { it.value.toUi() },
            audiobookSpeed = playerStateStore?.loadAudiobookSpeed(activeProfileId) ?: AudiobookSpeed.NORMAL,
            aiSelection =
                playerStateStore
                    ?.let { store -> runCatching { store.loadAiSelection(activeProfileId).toUi() }.getOrNull() }
                    ?: AiSelectionUiState(),
            aiHeatmap = null,
            settings = settingsController.snapshot().committed.toUiState(activeProfileId),
            selectedStorageAvailable = selectedStorageAvailable,
            dataStorageLocations = availableDataStorageItems(),
            activeDataStorageId = dataStorageId(initialSelection),
            activeDataStorageLabel = dataStorage.displayName(initialSelection),
            status =
                if (!selectedStorageAvailable) {
                    "Selected external storage is unavailable"
                } else if (playerStateStore == null) {
                    "Player state is unavailable"
                } else {
                    "Loading library…"
                },
            queueMediaIds = activeQueue?.orderedOccurrences().orEmpty().map { it.mediaId },
            queueIndex = activeQueue?.checkpoint?.occurrenceId.toQueueIndex(activeQueue),
            bookmarkMarkAllowed = bookmarkPolicy.allowed,
            settingsAccess = settingsAccessPolicy().access,
            settingsEntry = settingsAccessPolicy().entry,
            playlistMutationsAllowed = settingsAccessPolicy().playlistMutationsAllowed,
        )
        private set

    init {
        if (bookmarkMode == ApplicationMode.AAOS && context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            aaosRestrictions =
                runCatching {
                    AaosUxRestrictionMonitor(context) { motion ->
                        bookmarkMotion = motion
                        if (motion != MotionRestriction.UNKNOWN) settingsRecoveryAuthorized = false
                        if (!azClosed) {
                            updateState { old ->
                                val access = settingsAccessPolicy().access
                                val permitted = settingsAccessPolicy().playlistMutationsAllowed
                                val restrictedSettings = old.screen in SETTINGS_DRAFT_SCREENS && access != SettingsAccess.ALLOWED
                                val base =
                                    if (restrictedSettings) {
                                        if (settingsController.snapshot().draft != null) {
                                            settingsController.execute(PlayerSettingsCommand.Cancel)
                                        }
                                        settingsRecoveryAuthorized = false
                                        draftProfileId = null
                                        old.copy(screen = PlayerScreen.MAIN, settingsDraft = null, moreSettingsDraft = null)
                                    } else {
                                        old
                                    }
                                base.copy(
                                    settingsAccess = settingsAccessPolicy().access,
                                    settingsEntry = settingsAccessPolicy().entry,
                                    playlistMutationsAllowed = permitted,
                                    bookmarkMarkAllowed = bookmarkPolicy.allowed,
                                    playlistNameRequest = old.playlistNameRequest.takeIf { permitted },
                                    pendingPlaylistDelete = old.pendingPlaylistDelete.takeIf { permitted },
                                )
                            }
                        }
                    }
                }.getOrNull()
        }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addDataScheme("file")
            }
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(storageReceiver, filter)
        playbackConnection = PlayerPlaybackConnection(context)
        playbackObservation = playbackConnection.observe(::applyPlaybackEngineState)
        val hasPersistedReadGrant = runCatching(safGrantStore::hasPersistedReadGrant).getOrNull()
        if (requiresInitialSafGrantSelection(hasPersistedReadGrant)) {
            state =
                if (settingsAccessPolicy().access == SettingsAccess.ALLOWED) {
                    safGrantEditor.openSafGrantSelector(openSettings(state)).copy(
                        status = "Add a media folder to begin",
                        libraryPlayableTrackCount = 0,
                    )
                } else {
                    state.copy(status = "Media folder selection requires Settings access")
                }
        } else {
            libraryClientEnabled = true
            libraryClient.start()
        }
        Log.i(TAG, "startup_route persisted_read_grant=$hasPersistedReadGrant screen=${state.screen} indexer_enabled=$libraryClientEnabled")
    }

    override fun observe(observer: (PlayerUiState) -> Unit): AutoCloseable {
        observers += observer
        observer(state)
        return AutoCloseable { observers -= observer }
    }

    private val playlistActions =
        PlayerPlaylistActions(
            object : PlayerPlaylistActions.Host {
                override val state get() = this@RiffPlayerUiController.state
                override val profile get() = activeProfileId
                override val store get() = playerStateStore
                override val executor get() = workers

                override fun beginWrite() = beginPlaylistWrite()

                override fun sourceMediaIds(sourceId: String?) = this@RiffPlayerUiController.sourceMediaIds(sourceId)

                override fun projectRoot(profile: Int) = projectPlaylistRoot(profile)

                override fun publish(
                    root: BrowserItem,
                    profile: Int,
                    transform: (PlayerUiState, BrowserItem) -> PlayerUiState,
                ) = updatePlaylistProjection(root, profile, transform)

                override fun updateState(transform: (PlayerUiState) -> PlayerUiState) = this@RiffPlayerUiController.updateState(transform)

                override fun clearAiQueue() {
                    pendingAiQueue = null
                }

                override fun playlistSteps(playlistId: Long) =
                    navigation.containers[listOf(HierarchyRoot.PLAYLISTS.categoryId, playlistItemId(playlistId))]?.azSteps.orEmpty()
            },
        )

    private var settingsSaveInProgress = false
    private var playlistWriteProfile: Int? = null

    override fun dispatch(action: PlayerUiAction) {
        // AI-INTENT: OK acknowledges only the process-memory notice; no navigation/checkpoint or
        // playback command belongs here. Engine observations continue through their own callback.
        // https://wiki.271.pics/Riff/LATEST/saf-empty-listing-workaround#scan-retention-and-warning
        if (action == PlayerUiAction.AcknowledgeSafListingBug) {
            libraryClient.acknowledgeSafListingBug()
            return
        }
        if (state.safListingBugWarning != null) return

        if (settingsSaveInProgress || playlistWriteProfile != null) return
        if (action == PlayerUiAction.OpenSettings ||
            action == PlayerUiAction.OpenSettingsLongPress ||
            action == PlayerUiAction.OpenSettingsRecovery
        ) {
            if (state.screen != PlayerScreen.MAIN) return
            val requestedEntry =
                when (action) {
                    PlayerUiAction.OpenSettings -> SettingsEntry.PRESS
                    PlayerUiAction.OpenSettingsLongPress -> SettingsEntry.LONG_PRESS
                    else -> SettingsEntry.RECOVERY_HOLD
                }
            if (settingsAccessPolicy().entry != requestedEntry) {
                if (action == PlayerUiAction.OpenSettings && settingsAccessPolicy().entry == SettingsEntry.RECOVERY_HOLD) {
                    updateState { it.copy(status = "Motion input unavailable; hold Settings to open") }
                }
                return
            }
            // Entry uses the ordinary synchronous UI command order; drafts follow only after Open.
            // AI-INTENT: https://wiki.271.pics/Riff/LATEST/application-modes-modes#5-settings-entry
            if (requestedEntry == SettingsEntry.RECOVERY_HOLD) settingsRecoveryAuthorized = true
            if (settingsAccessPolicy().access == SettingsAccess.ALLOWED) {
                state = openSettings(state).also(navigation::rememberHierarchy)
                notifyObservers()
            } else {
                settingsRecoveryAuthorized = false
            }
            return
        }
        if (action.requiresSettingsAccess() && settingsAccessPolicy().access != SettingsAccess.ALLOWED) {
            updateState { it.copy(status = "Settings are unavailable under the current restrictions") }
            return
        }
        if (action == PlayerUiAction.SaveSettings || action == PlayerUiAction.SaveMoreSettings) {
            startSettingsSave(more = action == PlayerUiAction.SaveMoreSettings)
            return
        }
        if (action != PlayerUiAction.IdleTimeout) PlayerPlaybackBridge.requestCheckpoint()
        if (state.screen == PlayerScreen.AZ_SEARCH && !action.allowedDuringAzOverlay()) return
        if (
            action == PlayerUiAction.OpenMain ||
            action == PlayerUiAction.IdleTimeout ||
            action == PlayerUiAction.CloseAiActions
        ) {
            aiRequestToken += 1L
            pendingAiQueue = null
        }
        if (action == PlayerUiAction.ClosePlaylistSelector && state.playlistSourceItemId == AI_PLAYLIST_SOURCE_ITEM_ID) {
            pendingAiQueue = null
        }
        if (state.storageMigrationInProgress) return
        if (safGrantEditor.commitInProgress.get() && action.mutatesSafGrantDraft()) return
        if (!state.aiDataReady && action.requiresReadyAiData()) return
        if (!selectedStorageAvailable && action.requiresAvailableStorage()) {
            if (state.playlistNameRequest != null || state.pendingPlaylistDelete != null) {
                playlistActions.playlistMutationFailed(IllegalArgumentException("Selected external storage is unavailable"))
            } else {
                updateState { it.copy(status = "Selected external storage is unavailable") }
            }
            return
        }
        if (action.isPlaylistMutation() && (!settingsAccessPolicy().playlistMutationsAllowed || state.playlistMutationInProgress)) {
            updateState { it.copy(status = "Playlist editing is unavailable") }
            return
        }
        if (
            state.screen == PlayerScreen.ROW_ACTIONS &&
            action.leavesRowActions() &&
            commitRowActionsAudiobookDraft(action)
        ) {
            return
        }
        when (action) {
            PlayerUiAction.AdvanceAudiobookSpeed, PlayerUiAction.ResetAudiobookSpeed -> {
                changeAudiobookSpeed(reset = action == PlayerUiAction.ResetAudiobookSpeed)
                return
            }

            PlayerUiAction.ToggleContentMode -> {
                persistContentMode()
                return
            }

            is PlayerUiAction.SetAudiobookChoice -> {
                state = reduce(state, action)
                notifyObservers()
                return
            }

            is PlayerUiAction.SetAiSize,
            is PlayerUiAction.ToggleAiPteBand,
            is PlayerUiAction.SetAiPoint,
            -> {
                val updated = reduce(state, action)
                if (updated.aiSelection != state.aiSelection) {
                    state = updated
                    notifyObservers()
                    scheduleAiSelectionPersistence(activeProfileId, updated.aiSelection)
                }
                return
            }

            is PlayerUiAction.Play -> {
                persistManualQueue(action.itemId)
                return
            }

            is PlayerUiAction.NavigateTo -> {
                if (findBrowserItem(state.currentNode(), action.itemId)?.kind == BrowserKind.TRACK) {
                    persistManualQueue(action.itemId)
                    return
                }
            }

            PlayerUiAction.CreateAiQueue -> {
                persistAiQueue()
                return
            }

            PlayerUiAction.OpenAiPlaylistSelector -> {
                prepareAiPlaylistSelector()
                return
            }

            is PlayerUiAction.SetReaction -> {
                if (findByReactionKey(state.root, action.itemId)?.aiReactionEligible != true) return
                persistReaction(action.itemId, action.preference, action.toggleBan)
                return
            }

            is PlayerUiAction.SetCurrentReaction -> {
                val mediaId = state.nowPlaying.mediaId ?: return
                if (!state.nowPlaying.aiReactionEligible) return
                persistReaction(trackReactionKey(mediaId), action.preference, action.toggleBan)
                return
            }

            is PlayerUiAction.AddToPlaylist -> {
                playlistActions.addSourceToPlaylist(action.playlistId)
                return
            }

            PlayerUiAction.ConfirmPlaylistName -> {
                playlistActions.confirmPlaylistName()
                return
            }

            PlayerUiAction.ConfirmPlaylistDelete -> {
                playlistActions.confirmPlaylistDelete()
                return
            }

            is PlayerUiAction.RemovePlaylistOccurrence -> {
                playlistActions.removePlaylistOccurrence(action.playlistId, action.occurrenceId)
                return
            }

            is PlayerUiAction.RemoveBookmark -> {
                playlistActions.removeBookmark(action.playlistId, action.markId)
                return
            }

            PlayerUiAction.MarkBookmark -> {
                markBookmark()
                return
            }

            PlayerUiAction.Previous,
            PlayerUiAction.Next,
            PlayerUiAction.TogglePlayback,
            PlayerUiAction.ToggleRepeat,
            PlayerUiAction.ToggleShuffle,
            is PlayerUiAction.Seek,
            -> {
                dispatchPlaybackAction(action)
                return
            }

            PlayerUiAction.PlaybackFinished -> {
                // Completion is emitted by the service-owned Media3 listener.
                return
            }

            else -> {
                Unit
            }
        }
        state = reduce(state, action).also(navigation::rememberHierarchy)
        notifyObservers()
    }

    fun onSystemLocaleChanged() {
        // GNRE names can change without a new MDIA generation.
        libraryClient.requestReload()
    }

    override fun close() {
        azClosed = true
        libraryReloadToken += 1L
        libraryReloadFuture?.cancel(true)
        azRequestToken += 1L
        azWorkers.shutdownNow()
        aiHeatmapRequestToken += 1L
        aiPteAvailabilityRequestToken += 1L
        runCatching { context.unregisterReceiver(storageReceiver) }
        aaosRestrictions?.close()
        playbackObservation?.close()
        playbackConnection.close()
        libraryClient.close()
        profileWorkers.shutdown()
        workers.shutdownNow()
    }

    private fun reduce(
        old: PlayerUiState,
        action: PlayerUiAction,
    ): PlayerUiState {
        settingsUiTransition(action)?.let { transition ->
            var updated = applySettings(old, settingsController.execute(transition.command), transition.screen)
            if (transition.resetPosition) updated = updated.copy(page = 0, selectorIndex = 0, selectorOffset = 0)
            return transition.status?.let { updated.copy(status = it) } ?: updated
        }
        return when (action) {
            PlayerUiAction.AcknowledgeSafListingBug -> {
                old
            }

            PlayerUiAction.IdleTimeout -> {
                idleTransition(old)
            }

            PlayerUiAction.PlaybackFinished -> {
                playbackFinished(old)
            }

            PlayerUiAction.TogglePlaybackSurface -> {
                navigation.cancelAzSearch(old).copy(
                    screen =
                        if (old.screen ==
                            PlayerScreen.PLAYBACK
                        ) {
                            PlayerScreen.MAIN
                        } else {
                            PlayerScreen.PLAYBACK
                        },
                )
            }

            PlayerUiAction.OpenMain -> {
                navigation.cancelAzSearch(old).openMain()
            }

            PlayerUiAction.ToggleContentMode, PlayerUiAction.AdvanceAudiobookSpeed, PlayerUiAction.ResetAudiobookSpeed -> {
                old
            }

            is PlayerUiAction.OpenCategory -> {
                navigation.openCategory(old, action.categoryId)
            }

            is PlayerUiAction.NavigateTo -> {
                navigation.navigate(old, action.itemId)
            }

            PlayerUiAction.NavigateParent -> {
                navigation.navigateParent(old)
            }

            is PlayerUiAction.SelectAzBreadcrumb -> {
                navigation.navigateAzBreadcrumb(old, action)
            }

            PlayerUiAction.OpenAzSearch -> {
                if (old.screen == PlayerScreen.AZ_SEARCH) applyAzSearch(old) else navigation.openAzSearch(old)
            }

            PlayerUiAction.CancelAzSearch -> {
                navigation.cancelAzSearch(old)
            }

            PlayerUiAction.AzBackspace -> {
                navigation.backspaceAzSearch(old)
            }

            is PlayerUiAction.SelectAzGroup -> {
                navigation.selectAzGroup(old, action.group)
            }

            is PlayerUiAction.ReportHierarchyViewport -> {
                navigation.reportHierarchyViewport(old, action)
            }

            is PlayerUiAction.PreviousPage -> {
                old.copy(page = (old.page - 1).coerceAtLeast(0), pageSize = action.pageSize)
            }

            is PlayerUiAction.NextPage -> {
                old.copy(page = old.page + 1, pageSize = action.pageSize)
            }

            is PlayerUiAction.RememberFocus -> {
                old.copy(focusedItemId = action.itemId)
            }

            is PlayerUiAction.RememberSelectorPosition -> {
                old.copy(
                    page = action.index.coerceAtLeast(0) / action.pageSize.coerceAtLeast(1),
                    pageSize = action.pageSize.coerceAtLeast(1),
                    selectorIndex = action.index.coerceAtLeast(0),
                    selectorOffset = action.offset.coerceAtLeast(0),
                )
            }

            is PlayerUiAction.Play -> {
                old
            }

            is PlayerUiAction.SetReaction -> {
                old
            }

            is PlayerUiAction.SetCurrentReaction -> {
                old
            }

            is PlayerUiAction.SetAudiobookChoice -> {
                if (
                    old.screen == PlayerScreen.ROW_ACTIONS &&
                    !old.audiobookCommitInProgress &&
                    findBrowserItem(old.root, old.actionsItemId.orEmpty())
                        ?.takeIf {
                            it.kind == BrowserKind.ALBUM ||
                                it.kind == BrowserKind.FOLDER ||
                                it.kind == BrowserKind.TRACK
                        }?.reactionKey == action.objectKey
                ) {
                    old.copy(audiobookChoiceDraft = action.choice)
                } else {
                    old
                }
            }

            is PlayerUiAction.SetAiSize -> {
                old.copy(aiSelection = old.aiSelection.selectSize(action.size))
            }

            is PlayerUiAction.ToggleAiPteBand -> {
                if (old.aiPteAvailable) {
                    old.copy(aiSelection = old.aiSelection.togglePteBand(action.pteBand))
                } else {
                    old
                }
            }

            is PlayerUiAction.SetAiPoint -> {
                old.copy(aiSelection = old.aiSelection.selectPoint(action.arousal, action.valence))
            }

            PlayerUiAction.OpenAiActions -> {
                old.copy(screen = PlayerScreen.AI_ACTIONS)
            }

            PlayerUiAction.CloseAiActions -> {
                old.copy(
                    screen = PlayerScreen.AI,
                    playlistMutationInProgress = false,
                )
            }

            PlayerUiAction.CreateAiQueue -> {
                old
            }

            PlayerUiAction.OpenAiPlaylistSelector -> {
                old
            }

            is PlayerUiAction.OpenRowActions -> {
                val selected = findBrowserItem(old.root, action.itemId)
                old.also { rowActionsReturn = it.copy(focusedItemId = action.itemId) }.copy(
                    screen = PlayerScreen.ROW_ACTIONS,
                    actionsItemId = action.itemId,
                    audiobookChoiceDraft =
                        selected
                            ?.takeIf {
                                it.kind == BrowserKind.ALBUM ||
                                    it.kind == BrowserKind.FOLDER ||
                                    it.kind == BrowserKind.TRACK
                            }?.reactionKey
                            ?.let { old.audiobookOverrides[it] ?: AudiobookChoice.AUTO },
                    azAutoApplyPending = false,
                    focusedItemId = action.itemId,
                    playlistSourceItemId = null,
                )
            }

            PlayerUiAction.CloseRowActions -> {
                resumeHierarchyAfterMenu(old, rowActionsReturn)
            }

            is PlayerUiAction.OpenPlaylistSelector -> {
                old.copy(
                    screen = PlayerScreen.PLAYLIST_SELECTOR,
                    page = 0,
                    selectorIndex = 0,
                    selectorOffset = 0,
                    playlistSourceItemId = action.sourceItemId,
                    playlistSelectorChoices = musicPlaylistChoices(),
                )
            }

            PlayerUiAction.ClosePlaylistSelector -> {
                old.copy(
                    screen =
                        if (old.playlistSourceItemId == AI_PLAYLIST_SOURCE_ITEM_ID) {
                            PlayerScreen.AI
                        } else {
                            PlayerScreen.ROW_ACTIONS
                        },
                    page = 0,
                    selectorIndex = 0,
                    selectorOffset = 0,
                    playlistSourceItemId = null,
                    playlistNameRequest = null,
                    playlistNameDraft = "",
                    playlistMutationError = null,
                )
            }

            is PlayerUiAction.AddToPlaylist -> {
                old
            }

            PlayerUiAction.RequestNewPlaylist -> {
                old.copy(
                    playlistNameRequest = PlaylistNameUiRequest(PlaylistNameOperation.CREATE),
                    playlistNameDraft = "",
                    playlistMutationError = null,
                )
            }

            is PlayerUiAction.RequestPlaylistRename -> {
                val playlist = findBrowserItem(old.root, playlistItemId(action.playlistId))
                old.copy(
                    playlistNameRequest = PlaylistNameUiRequest(PlaylistNameOperation.RENAME, action.playlistId),
                    playlistNameDraft = playlist?.label.orEmpty(),
                    playlistMutationError = null,
                )
            }

            is PlayerUiAction.UpdatePlaylistName -> {
                old.copy(playlistNameDraft = action.value, playlistMutationError = null)
            }

            PlayerUiAction.ConfirmPlaylistName -> {
                old
            }

            PlayerUiAction.CancelPlaylistName -> {
                old.copy(playlistNameRequest = null, playlistNameDraft = "", playlistMutationError = null)
            }

            is PlayerUiAction.RequestPlaylistDelete -> {
                val playlist = findBrowserItem(old.root, playlistItemId(action.playlistId))
                playlist?.let {
                    old.copy(pendingPlaylistDelete = PlaylistDeleteUiRequest(action.playlistId, it.label), playlistMutationError = null)
                } ?: old
            }

            PlayerUiAction.ConfirmPlaylistDelete -> {
                old
            }

            PlayerUiAction.CancelPlaylistDelete -> {
                old.copy(pendingPlaylistDelete = null, playlistMutationError = null)
            }

            is PlayerUiAction.RemovePlaylistOccurrence, is PlayerUiAction.RemoveBookmark,
            PlayerUiAction.MarkBookmark,
            -> {
                old
            }

            PlayerUiAction.OpenSettings, PlayerUiAction.OpenSettingsLongPress, PlayerUiAction.OpenSettingsRecovery -> {
                old
            }

            PlayerUiAction.OpenSafGrantSelector -> {
                safGrantEditor.openSafGrantSelector(old)
            }

            PlayerUiAction.CancelSafGrantSelector -> {
                safGrantEditor.cancelSafGrantSelector(old)
            }

            PlayerUiAction.SaveSafGrantSelector -> {
                safGrantEditor.saveSafGrantSelector(old)
            }

            PlayerUiAction.OpenSafDocumentTree -> {
                old
            }

            is PlayerUiAction.SafGrantPicked -> {
                safGrantEditor.stageSafGrantAddition(old, action.uri, action.grantFlags)
            }

            PlayerUiAction.SafGrantPickerFailed -> {
                old.copy(status = "Media folder was not added")
            }

            is PlayerUiAction.RequestSafGrantRemoval -> {
                old.copy(pendingSafGrantRemoval = old.safGrants.firstOrNull { it.id == action.grantId })
            }

            PlayerUiAction.ConfirmSafGrantRemoval -> {
                safGrantEditor.stageSafGrantRemoval(old)
            }

            PlayerUiAction.CancelSafGrantRemoval -> {
                old.copy(pendingSafGrantRemoval = null)
            }

            PlayerUiAction.OpenDataStorageSelector -> {
                openDataStorageSelector(old)
            }

            PlayerUiAction.CancelDataStorageSelector -> {
                cancelDataStorageSelector(old)
            }

            is PlayerUiAction.SelectDataStorage -> {
                selectDataStorage(old, action.storageId)
            }

            PlayerUiAction.ConfirmDataStorageSelection -> {
                confirmDataStorageSelection(old)
            }

            is PlayerUiAction.SetDraftProfile -> {
                setDraftProfile(old, action.profileId)
            }

            PlayerUiAction.SaveSettings, PlayerUiAction.SaveMoreSettings -> {
                old
            }

            PlayerUiAction.CancelSettings -> {
                draftProfileId = null
                applySettings(old, settingsController.execute(PlayerSettingsCommand.Cancel), PlayerScreen.MAIN)
            }

            PlayerUiAction.OpenCalibration -> {
                old.copy(screen = PlayerScreen.CALIBRATION)
            }

            PlayerUiAction.CancelCalibration -> {
                old.copy(screen = PlayerScreen.SETTINGS_MORE)
            }

            PlayerUiAction.OpenScreenDiagonal -> {
                old.copy(screen = PlayerScreen.SCREEN_DIAGONAL)
            }

            PlayerUiAction.CancelScreenDiagonal -> {
                old.copy(screen = PlayerScreen.SETTINGS_MORE)
            }

            PlayerUiAction.TogglePlayback -> {
                old
            }

            PlayerUiAction.Previous -> {
                old
            }

            PlayerUiAction.Next -> {
                old
            }

            PlayerUiAction.ToggleShuffle -> {
                old
            }

            PlayerUiAction.ToggleRepeat -> {
                old
            }

            is PlayerUiAction.Seek -> {
                old
            }

            PlayerUiAction.OpenMoreSettings,
            PlayerUiAction.CloseMoreSettings,
            is PlayerUiAction.SetDraftTheme,
            is PlayerUiAction.SetDraftDiagonal,
            is PlayerUiAction.SetDraftLongPress,
            is PlayerUiAction.SetDraftIgnoreLeadingArticles,
            is PlayerUiAction.SetDraftAutoSearch,
            is PlayerUiAction.SetDraftDimBacklight,
            is PlayerUiAction.SetDraftReplayGainMode,
            is PlayerUiAction.SetDraftReplayGainTarget,
            is PlayerUiAction.SetDraftRebuild,
            is PlayerUiAction.AcceptCalibration,
            is PlayerUiAction.AcceptScreenDiagonal,
            -> {
                old
            }
        }
    }

    // Authority: https://wiki.271.pics/Riff/LATEST/player-inactivity
    // AI-INTENT: Keep timing/input detection in MainActivity; this reducer owns only the atomic destination and draft discard.
    private fun idleTransition(old: PlayerUiState): PlayerUiState {
        if (old.storageMigrationInProgress || old.pendingSafGrantRemoval != null) {
            return old
        }
        val destination = idleDestination(old.nowPlaying.playing)
        val settings = settingsController.snapshot()
        val base =
            if (settings.draft != null) {
                draftProfileId = null
                applySettings(
                    old,
                    settingsController.execute(PlayerSettingsCommand.Cancel),
                    destination,
                )
            } else {
                old.copy(screen = destination)
            }
        return base.copy(
            browserPath = emptyList(),
            hierarchyRoot = null,
            page = 0,
            selectorIndex = 0,
            selectorOffset = 0,
            focusedItemId = null,
            azSteps = emptyList(),
            azDraftSteps = null,
            azAutoApplyPending = false,
            azAutoEntryPending = false,
            actionsItemId = null,
            playlistSourceItemId = null,
            pendingSafGrantRemoval = null,
        )
    }

    private fun playbackFinished(old: PlayerUiState): PlayerUiState = old.copy(nowPlaying = completedPlayback(old.nowPlaying))

    private fun openSettings(old: PlayerUiState): PlayerUiState {
        draftProfileId = activeProfileId
        val existing = settingsController.snapshot()
        if (existing.draft != null) {
            if (old.screen in SETTINGS_DRAFT_SCREENS) {
                return applySettings(old, existing, PlayerScreen.SETTINGS)
            }
            // Recover from an interrupted UI transition: never expose or reopen a stale draft.
            // AI-INTENT: Keep this guard even when callers are debounced; controller commands are authoritative.
            settingsController.execute(PlayerSettingsCommand.Cancel)
        }
        val snapshot = settingsController.execute(PlayerSettingsCommand.Open)
        return applySettings(old, snapshot, PlayerScreen.SETTINGS).copy(
            browserPath = emptyList(),
            hierarchyRoot = null,
            page = 0,
            selectorIndex = 0,
            selectorOffset = 0,
            safGrants = currentSafGrants(),
            pendingSafGrantRemoval = null,
            selectedStorageAvailable = selectedStorageAvailable,
            dataStorageLocations = availableDataStorageItems(),
            activeDataStorageId = dataStorageId(dataStorage.selection()),
            activeDataStorageLabel = dataStorage.displayName(),
            dataStorageDraftId = null,
        )
    }

    private fun setDraftProfile(
        old: PlayerUiState,
        profileId: Int,
    ): PlayerUiState {
        if (profileId !in PlayerStateStore.VALID_PROFILES || draftProfileId == profileId) return old
        val current = settingsController.snapshot().draft ?: return old
        return runCatching {
            val profile =
                playerStateStore?.loadSettingsForProfile(profileId, current.dataLocation)
                    ?: PlayerSettings(dataLocation = current.dataLocation)
            draftProfileId = profileId
            applySettings(
                old,
                settingsController.execute(
                    PlayerSettingsCommand.SetProfileSettings(
                        profile.colorScheme,
                        profile.longPressStartsPlayback,
                        profile.autoSearch,
                        profile.ignoreLeadingArticles,
                    ),
                ),
                PlayerScreen.SETTINGS,
            )
        }.getOrElse { error ->
            old.copy(status = "User profile could not be loaded: ${error.javaClass.simpleName}")
        }
    }

    private fun openDataStorageSelector(old: PlayerUiState): PlayerUiState {
        val items = availableDataStorageItems()
        val activeId = dataStorageId(dataStorage.selection())
        return old.copy(
            screen = PlayerScreen.DATA_STORAGE,
            page = 0,
            selectorIndex = 0,
            selectorOffset = 0,
            dataStorageLocations = items,
            activeDataStorageId = activeId,
            activeDataStorageLabel = dataStorage.displayName(),
            dataStorageDraftId = activeId,
        )
    }

    private fun cancelDataStorageSelector(old: PlayerUiState): PlayerUiState =
        old.copy(
            screen = PlayerScreen.SETTINGS_MORE,
            page = 0,
            selectorIndex = 0,
            selectorOffset = 0,
            dataStorageDraftId = null,
        )

    private fun selectDataStorage(
        old: PlayerUiState,
        storageId: String,
    ): PlayerUiState =
        if (old.dataStorageLocations.any { it.id == storageId }) {
            old.copy(dataStorageDraftId = storageId)
        } else {
            old.copy(status = "Selected storage is no longer available")
        }

    private fun confirmDataStorageSelection(old: PlayerUiState): PlayerUiState {
        if (!selectedStorageAvailable) {
            return old.copy(status = "Active external storage is unavailable")
        }
        val item =
            old.dataStorageLocations.firstOrNull { it.id == old.dataStorageDraftId }
                ?: return old.copy(status = "Select a storage location")
        val target = item.toApplicationDataSelection()
        if (target == dataStorage.selection()) return cancelDataStorageSelector(old)
        migrateStorage(target)
        return old.copy(storageMigrationInProgress = true, status = "Moving application data…")
    }

    private fun currentSafGrants(): List<SafGrantUiItem> =
        runCatching { safGrantStore.list().map { SafGrantUiItem(it.uri, it.label) } }.getOrDefault(emptyList())

    private fun startSettingsSave(more: Boolean) {
        val before = settingsController.snapshot()
        val draft = (if (more) before.moreDraft else before.draft) ?: return
        val profile = (if (more) activeProfileId else draftProfileId) ?: return
        val store = playerStateStore ?: return updateState { it.copy(status = "Player state is unavailable") }
        val captured = state
        val previousProjection = libraryProjection
        val sourceRoot = libraryRoot
        val sourceTracks = retainedTracks
        settingsSaveInProgress = true
        state = state.copy(settingsSaveInProgress = true)
        notifyObservers()
        // AI-INTENT: Freeze the command on main; durable writes and result reads belong to the worker.
        // https://wiki.271.pics/Riff/LATEST/settings-screen-scope-and-rows#2-settings-page-top-bars-and-drafts
        profileWorkers.execute {
            val result =
                runCatching {
                    if (!more) check(PlayerPlaybackBridge.flushForContextChange()) { "Unable to save outgoing playback" }
                    check(!azClosed && playerStateStore === store) { "Settings session ended" }
                    check(settingsAccessPolicy().access == SettingsAccess.ALLOWED) { "Settings are unavailable" }
                    check(selectedStorageAvailable && dataStorage.selectedRootAvailable()) { "Selected external storage is unavailable" }
                    if (more) {
                        persistMoreSettings(store, profile, draft, before.committed)
                        previousProjection to captured
                    } else {
                        store.commitProfileSelection(profile, draft)
                        val projection =
                            PlayerLibraryProjection(
                                contentMode = store.loadContentMode(profile),
                                audiobookOverrides = store.loadAudiobookOverrides(profile).toMap(),
                            )
                        val root =
                            if (projection ==
                                previousProjection
                            ) {
                                sourceRoot
                            } else {
                                CuetreeBrowserTree.emptyRoot(projection.contentMode)
                            }
                        projection to
                            captured.copy(
                                root =
                                    projectPlaylists(
                                        root,
                                        store.listPlaylists(profile),
                                        sourceTracks,
                                        root.ignoreLeadingArticles,
                                        projection.contentMode.toUi(),
                                    ),
                                reactions = store.loadReactions(profile).mapValues { it.value.toUi() },
                                aiSelection = store.loadAiSelection(profile).toUi(),
                                audiobookSpeed = store.loadAudiobookSpeed(profile),
                            )
                    }
                }
            updateState { old ->
                if (playerStateStore !== store) return@updateState old
                settingsSaveInProgress = false
                val settled = old.copy(settingsSaveInProgress = false)
                result.fold(
                    onSuccess = { (projection, prepared) ->
                        val snapshot = settingsController.completePersistedSave(draft, more)
                        if (!more) {
                            commandEpoch++
                            activeProfileId = profile
                            draftProfileId = null
                            libraryProjection = projection
                            libraryRoot = prepared.root
                        }
                        if (more &&
                            (
                                draft.replayGainMode != before.committed.replayGainMode ||
                                    draft.replayGainTarget != before.committed.replayGainTarget
                            )
                        ) {
                            playbackConnection.setReplayGainSettings(draft.replayGainMode, draft.replayGainTarget)
                        }
                        val screen =
                            if (more &&
                                snapshot.draft != null &&
                                settingsAccessPolicy().access == SettingsAccess.ALLOWED
                            ) {
                                PlayerScreen.SETTINGS
                            } else {
                                PlayerScreen.MAIN
                            }
                        val base =
                            if (more) {
                                settled
                            } else {
                                settled.copy(
                                    root = prepared.root,
                                    reactions = prepared.reactions,
                                    contentMode = projection.contentMode.toUi(),
                                    audiobookOverrides = projection.audiobookOverrides.mapValues { it.value.toUi() },
                                    aiSelection = prepared.aiSelection,
                                    audiobookSpeed = prepared.audiobookSpeed,
                                )
                            }
                        val status = submitRebuild(before.pendingRebuild) ?: if (more) "More settings saved" else "Settings saved"
                        applySettings(base, snapshot, screen).copy(page = 0, selectorIndex = 0, selectorOffset = 0, status = status).also {
                            if (!more) {
                                if (!libraryClient.activateProjection(projection)) {
                                    updatePlaylistProjection(prepared.root) { current, root -> current.copy(root = root) }
                                    libraryClient.requestReload()
                                }
                                playbackConnection.refreshLibraryClassification()
                            }
                        }
                    },
                    onFailure = { error -> settled.copy(status = "Settings save failed: ${error.message ?: error.javaClass.simpleName}") },
                )
            }
        }
    }

    private fun persistMoreSettings(
        store: PlayerStateStore,
        profile: Int,
        draft: PlayerSettings,
        previous: PlayerSettings,
    ) {
        fun persistInstallation(value: PlayerSettings) {
            check(
                installationPreferences
                    .edit()
                    .putString("dim-backlight", value.dimBacklight.name)
                    .putString("replay-gain-mode", value.replayGainMode.name)
                    .putString("replay-gain-target", value.replayGainTarget.name)
                    .commit(),
            ) { "Installation settings were not persisted" }
        }
        persistInstallation(draft)
        try {
            store.saveSettings(draft, profile)
        } catch (error: Throwable) {
            persistInstallation(previous)
            throw error
        }
    }

    private fun migrateStorage(target: ApplicationDataSelection) {
        workers.execute {
            var indexerQuiesced = false
            try {
                indexerQuiesced = !libraryClientEnabled || libraryClient.quiesceForStorageMigration()
                check(indexerQuiesced) {
                    "Indexer did not stop before the data move"
                }
                check(playbackConnection.quiesceForStorageChange()) {
                    "Player playback did not stop before the data move"
                }
                check(settingsAccessPolicy().access == SettingsAccess.ALLOWED) { "Settings became unavailable before storage commit" }
                PlayerProcessStore.closeForRootChange()
                playerStateStore = null
                check(settingsAccessPolicy().access == SettingsAccess.ALLOWED) { "Settings became unavailable before storage commit" }
                val root = dataStorage.migrateTo(target)
                check(root.isDirectory)
                playerStateStore = PlayerProcessStore.open(context, recentHistoryGenerations, bookmarkMinSpacingSeconds)
                val store = requireNotNull(playerStateStore)
                val playerLocation = target.location.toPlayer()
                store.setDataLocation(playerLocation)
                val snapshot =
                    settingsController.execute(
                        PlayerSettingsCommand.RebaseDataLocation(playerLocation),
                    )
                reloadAudiobookPreferences(store)
                activeQueue = playerStateStore?.loadActiveQueue()
                selectedStorageAvailable = true
                if (libraryClientEnabled) {
                    check(
                        libraryClient.activateStorageRootAfterMigration(
                            target.location.name,
                            target.portableVolumeId,
                            requestFullPipeline = true,
                        ),
                    ) {
                        "Indexer did not activate the moved data"
                    }
                    libraryClient.requestReload()
                }
                playbackConnection.activateReadyQueue()
                updateState {
                    libraryRoot = CuetreeBrowserTree.emptyRoot(libraryProjection.contentMode)
                    applySettings(it, snapshot, PlayerScreen.SETTINGS_MORE).copy(
                        root = projectPlaylistRoot(),
                        storageMigrationInProgress = false,
                        selectedStorageAvailable = true,
                        dataStorageLocations = availableDataStorageItems(),
                        activeDataStorageId = dataStorageId(target),
                        activeDataStorageLabel = dataStorage.displayName(target),
                        dataStorageDraftId = null,
                        reactions = store.loadReactions().mapValues { it.value.toUi() },
                        contentMode = libraryProjection.contentMode.toUi(),
                        audiobookOverrides = libraryProjection.audiobookOverrides.mapValues { it.value.toUi() },
                        aiSelection = store.loadAiSelection(activeProfileId).toUi(),
                        audiobookSpeed = store.loadAudiobookSpeed(activeProfileId),
                        status = "Application data moved to ${dataStorage.displayName(target)}",
                    )
                }
            } catch (error: Throwable) {
                playerStateStore = openPlayerStateStoreOrNull()
                activeQueue = playerStateStore?.loadActiveQueue()
                if (indexerQuiesced && libraryClientEnabled) {
                    dataStorage.selection().let { selection ->
                        libraryClient.activateStorageRootAfterMigration(
                            selection.location.name,
                            selection.portableVolumeId,
                            requestFullPipeline = false,
                        )
                    }
                    libraryClient.requestReload()
                }
                updateState {
                    it.copy(
                        screen = PlayerScreen.DATA_STORAGE,
                        storageMigrationInProgress = false,
                        selectedStorageAvailable = dataStorage.selectedRootAvailable(),
                        dataStorageLocations = availableDataStorageItems(),
                        activeDataStorageId = dataStorageId(dataStorage.selection()),
                        activeDataStorageLabel = dataStorage.displayName(),
                        status = "Data move failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    private fun availableDataStorageItems(): List<DataStorageUiItem> {
        val roots = runCatching(dataStorage::eligiblePortableRoots).getOrDefault(emptyList())
        val duplicateLabels = roots.groupingBy { it.displayName.lowercase() }.eachCount()
        return listOf(DataStorageUiItem(INTERNAL_DATA_STORAGE_ID, "Internal storage")) +
            roots.map { root ->
                val label =
                    if (duplicateLabels[root.displayName.lowercase()] == 1) {
                        root.displayName
                    } else {
                        "${root.displayName} · ${root.volumeId}"
                    }
                DataStorageUiItem(
                    id = "$PORTABLE_DATA_STORAGE_PREFIX${root.volumeId}",
                    label = label,
                    portableVolumeId = root.volumeId,
                )
            }
    }

    private fun dataStorageId(selection: ApplicationDataSelection): String =
        selection.portableVolumeId?.let { "$PORTABLE_DATA_STORAGE_PREFIX$it" }
            ?: INTERNAL_DATA_STORAGE_ID

    private fun DataStorageUiItem.toApplicationDataSelection(): ApplicationDataSelection =
        portableVolumeId?.let {
            ApplicationDataSelection(ApplicationDataLocation.SD, it)
        } ?: ApplicationDataSelection(ApplicationDataLocation.HU)

    private fun newLibraryClient(): PlayerCuetreeClient =
        PlayerCuetreeClient(
            context,
            onLibrary = ::activateLibraryModel,
            onUnavailable = { message -> updateState { it.copy(status = message) } },
            onIndexerStatus = { message -> updateState { it.copy(indexerStatus = message) } },
            onLoadingChanged = { loading -> updateState { it.copy(libraryLoading = loading) } },
            onSafListingBug = { notice -> updateState { it.copy(safListingBugWarning = notice) } },
            excludedGenreIds = uiConfig.excludedGenreIds(),
            projection = { libraryProjection },
        )

    private fun activateLibraryModel(model: PlayerLibraryModel) {
        main.post {
            if (azClosed) return@post
            if (model.projection != libraryProjection) {
                libraryClient.requestReload()
                return@post
            }
            if (state.libraryGeneration != null && model.generation < requireNotNull(state.libraryGeneration)) {
                return@post
            }
            val old = state
            val profile = activeProfileId
            val roots = navigation.roots.toMap()
            val containers = navigation.containers.toMap()
            val ignoreArticles = old.settings.ignoreLeadingArticles
            val playlists = runCatching { playerStateStore?.listPlaylists().orEmpty() }.getOrDefault(emptyList())
            libraryReloadFuture?.cancel(true)
            val token = ++libraryReloadToken
            libraryReloadFuture =
                workers.submit {
                    try {
                        playerStateStore?.pruneExpiredCollections(model.mediaIds)
                        val currentPlaylists = playerStateStore?.listPlaylists() ?: playlists
                        val projectedRoot =
                            projectPlaylists(
                                model.root.withBrowseNamePolicy(ignoreArticles),
                                currentPlaylists,
                                model.retainedTracks,
                                ignoreArticles,
                                model.projection.contentMode.toUi(),
                            )
                        val requests = navigationOrderRequests(old, projectedRoot, roots, containers)
                        val orders = AzReloadOrders(requests)
                        main.post install@{
                            if (azClosed || token != libraryReloadToken) return@install
                            if (model.projection != libraryProjection) {
                                libraryClient.requestReload()
                                return@install
                            }
                            if (state.root !== old.root ||
                                activeProfileId != profile ||
                                state.settings.ignoreLeadingArticles != ignoreArticles ||
                                state.browserPath != old.browserPath ||
                                state.azSteps != old.azSteps ||
                                state.azDraftSteps != old.azDraftSteps ||
                                navigation.roots != roots ||
                                navigation.containers != containers
                            ) {
                                activateLibraryModel(model)
                                return@install
                            }
                            installLibraryModel(model, projectedRoot, orders)
                        }
                    } catch (_: CancellationException) {
                        // A newer generation or shutdown retains the last validated active projection.
                    }
                }
        }
    }

    private fun installLibraryModel(
        model: PlayerLibraryModel,
        projectedRoot: BrowserItem,
        orders: AzReloadOrders,
    ) {
        val previousTrackInputs = tracks.map { Triple(it.mediaId, it.sourceBindingId, it.playable) }
        val heatmapInputsChanged =
            aiFeaturesByMediaId != model.aiFeaturesByMediaId ||
                aiAugmentationGeneration != model.augmentationGeneration
        mediaAliases = model.mediaAliases
        aiFeaturesByMediaId = model.aiFeaturesByMediaId
        aiPteByMediaId = model.pteByMediaId
        replayGainByMediaId = model.replayGainByMediaId
        topInferredGenreByMediaId = model.topInferredGenreByMediaId
        aiAugmentationGeneration = model.augmentationGeneration
        audiobookMediaIds = model.audiobookMediaIds
        effectiveBookMediaIds = model.effectiveBookMediaIds
        tocChaptersByBindingId = model.tocChaptersByBindingId
        state =
            activateLibrary(state, model, projectedRoot, orders)
                .copy(aiDataAvailable = model.aiFeaturesByMediaId.isNotEmpty())
                .also(navigation::rememberHierarchy)
        notifyObservers()
        scheduleAiPteAvailabilityRebuild()
        val currentTrackInputs = tracks.map { Triple(it.mediaId, it.sourceBindingId, it.playable) }
        if (heatmapInputsChanged || previousTrackInputs != currentTrackInputs) {
            scheduleAiHeatmapRebuild()
        }
    }

    private fun updatePlaylistProjection(
        projected: BrowserItem,
        mutationProfile: Int? = null,
        transform: (PlayerUiState, BrowserItem) -> PlayerUiState,
    ) {
        main.post {
            if (azClosed) return@post
            if (mutationProfile != null && activeProfileId != mutationProfile) {
                state = state.copy(playlistMutationInProgress = false)
                notifyObservers()
                return@post
            }
            val old = state
            val profile = activeProfileId
            val roots = navigation.roots.toMap()
            val containers = navigation.containers.toMap()
            val ignoreArticles = old.settings.ignoreLeadingArticles
            workers.execute {
                try {
                    val ready =
                        if (projected.ignoreLeadingArticles ==
                            ignoreArticles
                        ) {
                            projected
                        } else {
                            projected.withBrowseNamePolicy(ignoreArticles)
                        }
                    val requests = navigationOrderRequests(old, ready, roots, containers)
                    val orders = AzReloadOrders(requests)
                    main.post install@{
                        if (azClosed) return@install
                        if (state.root !== old.root ||
                            activeProfileId != profile ||
                            state.settings.ignoreLeadingArticles != ignoreArticles
                        ) {
                            updatePlaylistProjection(projectPlaylistRoot(), mutationProfile, transform)
                            return@install
                        }
                        if (navigation.roots != roots || navigation.containers != containers) {
                            updatePlaylistProjection(projected, mutationProfile, transform)
                            return@install
                        }
                        navigation.repairNavigationMemory(old.root, ready, orders)
                        libraryRoot = ready
                        state = transform(state, ready).also(navigation::rememberHierarchy)
                        notifyObservers()
                    }
                } catch (_: CancellationException) {
                    // Shutdown: the durable playlist remains committed for the next process.
                }
            }
        }
    }

    private fun scheduleAiSelectionPersistence(
        profileId: Int,
        selection: AiSelectionUiState,
    ) {
        val store = playerStateStore ?: return
        val command = ProfileCommandContext(store, profileId, commandEpoch)
        val shouldSchedule =
            synchronized(aiSelectionPersistenceGate) {
                pendingAiSelections[command] = selection.toStored()
                if (aiSelectionPersistenceScheduled) {
                    false
                } else {
                    aiSelectionPersistenceScheduled = true
                    true
                }
            }
        if (!shouldSchedule) return
        runCatching { profileWorkers.execute(::drainAiSelectionPersistence) }
            .onFailure {
                synchronized(aiSelectionPersistenceGate) {
                    aiSelectionPersistenceScheduled = false
                }
            }
    }

    private fun drainAiSelectionPersistence() {
        while (true) {
            val next =
                synchronized(aiSelectionPersistenceGate) {
                    val entry = pendingAiSelections.entries.firstOrNull()
                    if (entry == null) {
                        aiSelectionPersistenceScheduled = false
                        null
                    } else {
                        pendingAiSelections.remove(entry.key)
                        entry.key to entry.value
                    }
                } ?: return
            runCatching { next.first.store.saveAiSelection(next.first.profileId, next.second) }
                .onFailure { error ->
                    Log.w("RiffAiSelection", "profile_selection_save_failed type=${error.javaClass.simpleName}")
                }
        }
    }

    private fun StoredAiSelection.toUi(): AiSelectionUiState =
        AiSelectionUiState(
            size = queueSize,
            pteBands =
                buildSet {
                    if (pteBandsMask and 1 != 0) add(AiPteBand.SLOW)
                    if (pteBandsMask and 2 != 0) add(AiPteBand.MEDIUM)
                    if (pteBandsMask and 4 != 0 && size < 2) add(AiPteBand.FAST)
                },
            arousal = arousal,
            valence = valence,
        )

    private fun AiSelectionUiState.toStored(): StoredAiSelection =
        StoredAiSelection(
            queueSize = size,
            pteBandsMask =
                pteBands.fold(0) { mask, band ->
                    mask or
                        when (band) {
                            AiPteBand.SLOW -> 1
                            AiPteBand.MEDIUM -> 2
                            AiPteBand.FAST -> 4
                        }
                },
            arousal = arousal,
            valence = valence,
        )

    private fun submitRebuild(rebuild: Set<PlayerDatabaseRebuild>): String? {
        if (rebuild.isEmpty()) return null
        val accepted =
            libraryClient.requestDatabaseRebuild(
                rebuild.fold(0) { mask, scope ->
                    mask or if (scope == PlayerDatabaseRebuild.AU) 1 else 2
                },
            )
        return if (accepted) {
            "${rebuild.sortedBy { it.ordinal }.joinToString("+") { it.name }} database rebuild requested"
        } else {
            "Database rebuild request failed"
        }
    }

    private fun activateLibrary(
        old: PlayerUiState,
        model: PlayerLibraryModel,
        projectedRoot: BrowserItem,
        orders: AzReloadOrders,
    ): PlayerUiState {
        if (old.libraryGeneration != null && model.generation < old.libraryGeneration) return old
        tracks = model.tracks
        retainedTracks = model.retainedTracks
        libraryRoot = projectedRoot
        navigation.repairNavigationMemory(old.root, projectedRoot, orders)
        var survivingPath = old.browserPath
        while (survivingPath.isNotEmpty() && pathNode(projectedRoot, survivingPath) == null) {
            survivingPath = survivingPath.dropLast(1)
        }
        val oldAnchorContext = pathNode(old.root, survivingPath)
        val newAnchorContext = pathNode(projectedRoot, survivingPath)
        val samePath = survivingPath == old.browserPath
        val restoredSteps = if (samePath) old.azSteps else navigation.containers[survivingPath]?.azSteps.orEmpty()
        val oldOrder = orders.order(oldAnchorContext?.children.orEmpty(), restoredSteps)
        val newOrder = orders.order(newAnchorContext?.children.orEmpty(), restoredSteps)
        val oldTopIndex = old.selectorIndex.coerceIn(0, oldOrder.lastIndex.coerceAtLeast(0))
        val desired =
            if (survivingPath == old.browserPath) {
                oldOrder.getOrNull(oldTopIndex)
            } else {
                old.browserPath.getOrNull(survivingPath.size)
            }
        val anchorIndex = reloadAnchorIndex(oldOrder, newOrder, desired)
        val pathValid = survivingPath.isNotEmpty() && newAnchorContext != null
        val reloaded =
            old.copy(
                screen =
                    libraryReloadDestination(
                        current = if (!samePath && old.screen == PlayerScreen.AZ_SEARCH) PlayerScreen.HIERARCHY else old.screen,
                        hierarchyPathValid = pathValid,
                        aiPlaylistSelector = old.playlistSourceItemId == AI_PLAYLIST_SOURCE_ITEM_ID,
                    ),
                root = projectedRoot,
                browserPath = if (pathValid) survivingPath else emptyList(),
                hierarchyRoot = if (pathValid) old.hierarchyRoot else null,
                page = if (pathValid) anchorIndex / old.pageSize.coerceAtLeast(1) else 0,
                selectorIndex = if (pathValid) anchorIndex else 0,
                selectorOffset = if (samePath && desired in newOrder) old.selectorOffset else 0,
                focusedItemId = if (pathValid) newOrder.getOrNull(anchorIndex) else null,
                azSteps = if (pathValid) restoredSteps else emptyList(),
                azDraftSteps = if (pathValid && samePath) old.azDraftSteps else null,
                azAutoApplyPending = false,
                azAutoEntryPending = false,
                libraryGeneration = model.generation,
                libraryPlayableTrackCount = model.tracks.size,
                contentMode = model.projection.contentMode.toUi(),
                audiobookOverrides = model.projection.audiobookOverrides.mapValues { it.value.toUi() },
                status = "Cuetree ${model.generation.toULong()} · ${model.tracks.size} playable tracks",
            )
        val diagnosed =
            reloaded.copy(
                playbackDiagnostics =
                    playbackDiagnosticsFor(
                        reloaded.nowPlaying.mediaId,
                        reloaded.queueMediaIds,
                        aiFeaturesByMediaId,
                        aiPteByMediaId,
                        replayGainByMediaId,
                        topInferredGenreByMediaId,
                    ),
            )
        val chosenBinding = activeQueue?.checkpoint?.chosenBindingId
        val current =
            chosenBinding?.let { findByBinding(model.root, it) }
                ?: tracks.firstOrNull { it.mediaId == diagnosed.nowPlaying.mediaId }
                ?: return diagnosed
        val artwork =
            artworkForProjection(
                oldMediaId = diagnosed.nowPlaying.mediaId,
                oldArtwork = diagnosed.nowPlaying.artwork,
                newMediaId = current.mediaId,
                newBindingId = current.sourceBindingId,
            )
        return diagnosed.copy(
            nowPlaying =
                diagnosed.nowPlaying.copy(
                    title = current.label,
                    artist = current.artist,
                    album = current.album,
                    chapter =
                        currentChapterLabel(
                            current.sourceBindingId?.let(tocChaptersByBindingId::get).orEmpty(),
                            activeQueue?.checkpoint?.positionMs ?: 0L,
                            null,
                        ),
                    aiReactionEligible = current.aiReactionEligible,
                    artwork = artwork,
                ),
        )
    }

    private fun applySettings(
        old: PlayerUiState,
        snapshot: PlayerSettingsSnapshot,
        screen: PlayerScreen,
    ): PlayerUiState {
        if (snapshot.draft == null && screen !in SETTINGS_DRAFT_SCREENS) settingsRecoveryAuthorized = false
        return old.copy(
            screen = screen,
            settingsAccess = settingsAccessPolicy().access,
            settingsEntry = settingsAccessPolicy().entry,
            playlistMutationsAllowed = settingsAccessPolicy().playlistMutationsAllowed,
            settings = snapshot.committed.toUiState(activeProfileId),
            settingsDraft =
                snapshot.draft
                    ?.toUiState(draftProfileId ?: activeProfileId),
            moreSettingsDraft =
                snapshot.moreDraft
                    ?.toUiState(activeProfileId)
                    ?.copy(pendingRebuild = snapshot.pendingRebuild.mapTo(mutableSetOf()) { it.toUi() }),
        )
    }

    private fun PlayerSettings.toUiState(profileId: Int): PlayerSettingsUiState =
        PlayerSettingsUiState(
            profileId = profileId,
            colorScheme = ColorScheme.valueOf(colorScheme.name),
            diagonal = DiagonalChoice.valueOf(diagonal.name),
            dataLocation = DataLocation.valueOf(dataLocation.name),
            longPressStartsPlayback = longPressStartsPlayback,
            autoSearch = autoSearch,
            ignoreLeadingArticles = ignoreLeadingArticles,
            dimBacklight = dimBacklight,
            replayGainMode = ReplayGainMode.valueOf(replayGainMode.name),
            replayGainTarget = ReplayGainTarget.valueOf(replayGainTarget.name),
            calibrationPpiX = calibrationPpiX,
            calibrationPpiY = calibrationPpiY,
        )

    // AI-INTENT: SQLite revisions are authority; Compose receives only the refreshed hierarchy projection.
    private fun projectPlaylistRoot(profile: Int = activeProfileId): BrowserItem =
        projectPlaylists(
            libraryRoot,
            runCatching { playerStateStore?.listPlaylists(profile).orEmpty() }.getOrDefault(emptyList()),
            retainedTracks,
            libraryRoot.ignoreLeadingArticles,
            libraryProjection.contentMode.toUi(),
        )

    private fun musicPlaylistChoices(): List<PlaylistSelectorUiItem> =
        listOf(PlaylistSelectorUiItem(null, NEW_PLAYLIST_LABEL)) +
            runCatching { playerStateStore?.listPlaylists().orEmpty() }
                .getOrDefault(emptyList())
                .filter { it.kind == link.roadio.riff.player.storage.PlaylistKind.MUSIC }
                .map { PlaylistSelectorUiItem(it.playlistId, it.name) }

    private fun sourceMediaIds(sourceItemId: String?): List<MediaId> {
        if (sourceItemId == AI_PLAYLIST_SOURCE_ITEM_ID) {
            return pendingAiQueue.orEmpty().map(AiQueueCandidate::mediaId)
        }
        val source = sourceItemId?.let { findBrowserItem(state.root, it) } ?: return emptyList()
        if (source.kind == BrowserKind.PLAYLIST) return emptyList()
        return materializedHierarchyTracks(source).mapNotNull(BrowserItem::mediaId)
    }

    private fun beginPlaylistMutation(): Boolean {
        if (state.playlistMutationInProgress) return false
        state = state.copy(playlistMutationInProgress = true, playlistMutationError = null)
        notifyObservers()
        return true
    }

    private fun beginPlaylistWrite(): Boolean {
        if (!beginPlaylistMutation()) return false
        playlistWriteProfile = activeProfileId
        return true
    }

    private fun bookQueueTarget(
        queue: PlaybackQueueSnapshot,
        activeOrdinal: Int,
        mediaId: MediaId,
    ): Pair<List<MediaId>, Int>? {
        val sourceOrdinal = queue.bookQueueOrdinals.getOrNull(activeOrdinal) ?: return null
        return if (queue.bookTitleMembers.getOrNull(sourceOrdinal) == mediaId) {
            queue.bookTitleMembers to sourceOrdinal
        } else {
            null
        }
    }

    private fun bookTitleKeyForQueue(
        queue: PlaybackQueueSnapshot,
        store: PlayerStateStore,
    ): String? {
        val contextKey = queue.contextKey ?: return null
        if (contextKey.startsWith("album:")) return contextKey
        val collectionId =
            contextKey.removePrefix("bookmarks:").takeIf { contextKey.startsWith("bookmarks:") }?.toLongOrNull()
                ?: return null
        return store
            .loadPlaylistForProfile(collectionId, queue.sourceProfileId)
            ?.takeIf { it.kind == link.roadio.riff.player.storage.PlaylistKind.BOOK }
            ?.titleKey
    }

    private fun markBookmark() {
        if (!bookmarkPolicy.allowed || !state.bookmarkMarkAllowed) return
        val queue = activeQueue?.takeIf { it.bookTitleContext } ?: return
        val actual = playbackConnection.currentState() ?: return
        if (actual.queueRevisionId != queue.revisionId || actual.mediaId == null) return
        val occurrence = queue.occurrences.firstOrNull { it.occurrenceId == actual.occurrenceId && it.mediaId == actual.mediaId } ?: return
        val title = queue.bookTitleName ?: state.nowPlaying.album.ifBlank { "Book" }
        val label = actual.title
        val (orderedIds, frozenOrdinal) = bookQueueTarget(queue, occurrence.ordinal, occurrence.mediaId) ?: return
        val store = playerStateStore ?: return
        val positionUs = Math.multiplyExact(actual.positionMs.coerceAtLeast(0L), 1_000L)
        val durationUs = actual.durationMs.takeIf { it > 0L }?.let { Math.multiplyExact(it, 1_000L) }
        if (!store.canMarkBook(queue, occurrence.ordinal, occurrence.mediaId, positionUs, durationUs) || bookmarkWriteInProgress) return
        bookmarkWriteInProgress = true
        notifyObservers()
        workers.execute {
            runCatching {
                val titleKey = requireNotNull(bookTitleKeyForQueue(queue, store)) { "Bookmarks collection is no longer available" }
                store.markBook(titleKey, title, orderedIds, frozenOrdinal, positionUs, label, durationUs, queue.sourceProfileId)
            }.onSuccess {
                main.post {
                    bookmarkWriteInProgress = false
                    recordedBookDurations.clear()
                    notifyObservers()
                }
                updatePlaylistProjection(projectPlaylistRoot()) { old, root ->
                    old.copy(root = root, status = "Bookmark saved")
                }
            }.onFailure { error ->
                main.post { bookmarkWriteInProgress = false }
                updateState { it.copy(status = error.message ?: "Bookmark could not be saved") }
            }
        }
    }

    private fun persistAiQueue() {
        val store = playerStateStore ?: return updateState { it.copy(status = "Player state is unavailable") }
        val command = captureCommand(store)
        val selection = state.aiSelection
        val selectedPteBands = selection.pteBands.takeIf { state.aiPteAvailable }.orEmpty()
        val input = currentAiSelectionInput(state)
        profileWorkers.execute {
            runCatching {
                val history = store.loadAiPlaybackHistory(command.profileId)
                val candidates = buildAiQueueCandidates(input, history)
                AiQueueGenerator(
                    candidateProvider = { candidates },
                    config = aiSelectionModel,
                    currentHistoryGeneration = history.currentGeneration,
                ).generate(
                    size = selection.size,
                    pteBands = selectedPteBands,
                    arousal = selection.arousal,
                    valence = selection.valence,
                )
            }.onSuccess { generated ->
                if (generated.isEmpty()) {
                    completeCommand(command) { it.copy(status = "No eligible tracks for AI queue") }
                    return@onSuccess
                }
                runCatching {
                    check(PlayerPlaybackBridge.flushForContextChange()) { "Unable to save outgoing playback" }
                    store.materializeQueue(
                        generated.map(AiQueueCandidate::mediaId),
                        PlaybackQueueKind.AI,
                        playWhenReady = true,
                        temporary = true,
                        initialOrdinal = 0,
                        chosenBindingId = generated.first().sourceBindingId,
                        profileId = command.profileId,
                        requireCurrentProfile = true,
                    )
                }.onSuccess { queue ->
                    completeQueueCommand(command, queue, "AI")
                }.onFailure { error -> completeCommand(command) { it.copy(status = error.asPlayerStateFailure()) } }
            }.onFailure { error ->
                completeCommand(command) { it.copy(status = "AI queue failed: ${error.message ?: error.javaClass.simpleName}") }
            }
        }
    }

    private fun prepareAiPlaylistSelector() {
        val store = playerStateStore ?: return
        val command = captureCommand(store)
        val selection = state.aiSelection
        val selectedPteBands = selection.pteBands.takeIf { state.aiPteAvailable }.orEmpty()
        val input = currentAiSelectionInput(state)
        val requestToken = ++aiRequestToken
        if (!beginPlaylistMutation()) return
        workers.execute {
            runCatching {
                val history = store.loadAiPlaybackHistory(command.profileId)
                val candidates = buildAiQueueCandidates(input, history)
                AiQueueGenerator(
                    candidateProvider = { candidates },
                    config = aiSelectionModel,
                    currentHistoryGeneration = history.currentGeneration,
                ).generate(
                    size = selection.size,
                    pteBands = selectedPteBands,
                    arousal = selection.arousal,
                    valence = selection.valence,
                )
            }.onSuccess { generated ->
                if (requestToken != aiRequestToken) return@onSuccess
                if (generated.isEmpty()) {
                    completeCommand(command) {
                        if (requestToken != aiRequestToken) return@completeCommand it
                        it.copy(
                            playlistMutationInProgress = false,
                            status = "No eligible tracks for AI queue",
                        )
                    }
                    return@onSuccess
                }
                completeCommand(command) {
                    if (requestToken != aiRequestToken) return@completeCommand it
                    pendingAiQueue = generated
                    it.copy(
                        screen = PlayerScreen.PLAYLIST_SELECTOR,
                        page = 0,
                        selectorIndex = 0,
                        selectorOffset = 0,
                        playlistSourceItemId = AI_PLAYLIST_SOURCE_ITEM_ID,
                        playlistSelectorChoices = musicPlaylistChoices(),
                        playlistMutationInProgress = false,
                        status = "Generated ${generated.size}-track AI playlist draft",
                    )
                }
            }.onFailure { error ->
                main.post { if (requestToken == aiRequestToken && commandIsCurrent(command)) playlistActions.playlistMutationFailed(error) }
            }
        }
    }

    private fun currentAiSelectionInput(snapshot: PlayerUiState): AiSelectionCandidateInput =
        AiSelectionCandidateInput(
            tracks = tracks.toList(),
            root = snapshot.root,
            reactions = snapshot.reactions.toMap(),
            featuresByMediaId = aiFeaturesByMediaId.toMap(),
            pteByMediaId = aiPteByMediaId.toMap(),
            audiobookMediaIds = audiobookMediaIds.toSet(),
            effectiveBookMediaIds = effectiveBookMediaIds.toSet(),
            mediaAliases = mediaAliases.toMap(),
        )

    private fun scheduleAiPteAvailabilityRebuild() {
        val requestToken = ++aiPteAvailabilityRequestToken
        val input = currentAiSelectionInput(state)
        workers.execute {
            val available =
                runCatching {
                    AiQueueGenerator.isPteAvailable(
                        buildAiQueueCandidates(input, AiPlaybackHistorySnapshot(0L, emptyList())),
                        aiSelectionModel,
                    )
                }.getOrDefault(false)
            main.post {
                if (requestToken == aiPteAvailabilityRequestToken) {
                    updateState { it.copy(aiPteAvailable = available) }
                }
            }
        }
    }

    private fun scheduleAiHeatmapRebuild() {
        val requestToken = ++aiHeatmapRequestToken
        if (aiAugmentationGeneration == null || aiFeaturesByMediaId.isEmpty()) {
            updateState { old ->
                if (requestToken == aiHeatmapRequestToken) old.copy(aiHeatmap = null) else old
            }
            return
        }
        val snapshotTracks = tracks
        val snapshotRoot = state.root
        val snapshotReactions = state.reactions
        val snapshotSelection = state.aiSelection
        val features = aiFeaturesByMediaId
        workers.execute {
            val bannedMediaIds = effectiveAiBannedMediaIds(snapshotRoot, snapshotReactions)
            val candidates =
                snapshotTracks.mapNotNull { track ->
                    val mediaId = track.mediaId ?: return@mapNotNull null
                    if (mediaId in audiobookMediaIds || mediaId in effectiveBookMediaIds) return@mapNotNull null
                    AiQueueCandidate(
                        mediaId = mediaId,
                        sourceBindingId = track.sourceBindingId,
                        playable = track.playable,
                        effectivelyBanned = mediaId in bannedMediaIds,
                    )
                }
            val heatmap =
                AiHeatmapBuilder.build(
                    candidates = candidates,
                    featuresByMediaId = features,
                    valenceCells = heatmapValenceCells,
                    arousalCells = heatmapArousalCells,
                    selectionArousal = snapshotSelection.arousal,
                    selectionValence = snapshotSelection.valence,
                )
            main.post {
                if (
                    !shouldInstallAiHeatmapResult(
                        requestToken,
                        aiHeatmapRequestToken,
                        snapshotSelection,
                        state.aiSelection,
                    )
                ) {
                    if (requestToken == aiHeatmapRequestToken) scheduleAiHeatmapRebuild()
                    return@post
                }
                state = state.copy(aiHeatmap = heatmap).also(navigation::rememberHierarchy)
                notifyObservers()
            }
        }
    }

    // AI-INTENT: The UI receives only a projection after the READY revision and active pointer commit.
    private fun persistBookmarkQueue(selected: BrowserItem) {
        if (!selected.playable || selected.bookmarkMarkId == null) return
        val store = playerStateStore ?: return
        val command = captureCommand(store)
        val playlistId = selected.playlistId ?: return
        val availableTracks = retainedTracks
        state = navigation.rememberPlaybackFilter(state, rowActionsReturn)
        notifyObservers()
        profileWorkers.execute {
            runCatching {
                val collection =
                    store.listPlaylists(command.profileId).first {
                        it.playlistId == playlistId && it.kind == link.roadio.riff.player.storage.PlaylistKind.BOOK
                    }
                val mark = collection.marks.first { it.markId == selected.bookmarkMarkId }
                val startOrdinal = mark.ordinal
                val bindingId =
                    selected.sourceBindingId
                        ?: availableTracks
                            .firstOrNull {
                                it.mediaId == collection.occurrences[startOrdinal].mediaId && it.playable
                            }?.sourceBindingId
                val frozenIds = collection.occurrences.map { it.mediaId }
                check(PlayerPlaybackBridge.flushForContextChange()) { "Unable to save outgoing playback" }
                store.materializeQueue(
                    frozenIds,
                    PlaybackQueueKind.PERMANENT_PLAYLIST,
                    playWhenReady = true,
                    temporary = false,
                    initialOrdinal = startOrdinal,
                    initialPositionMs = mark.positionUs / 1_000L,
                    chosenBindingId = bindingId,
                    contextKey = "bookmarks:$playlistId",
                    profileId = command.profileId,
                    requireCurrentProfile = true,
                    bookTitleContext = true,
                    bookTitleName = collection.name.removePrefix("Bookmarks for "),
                    bookTitleMembers = frozenIds,
                    bookQueueOrdinals = frozenIds.indices.toList(),
                    audiobook = true,
                )
            }.onSuccess { queue ->
                completeQueueCommand(command, queue, selected.label)
            }.onFailure { error -> completeCommand(command) { it.copy(status = error.asPlayerStateFailure()) } }
        }
    }

    private fun persistManualQueue(itemId: String) {
        val selected = state.currentNode().children.firstOrNull { it.id == itemId } ?: return
        if (selected.bookmarks) return persistBookmarkQueue(selected)
        val projection = contextualPlayProjection(state.currentNode(), itemId) ?: return
        val mediaIds = projection.tracks.mapNotNull(BrowserItem::mediaId)
        if (mediaIds.isEmpty()) return
        val queueLabel =
            when {
                projection.selected.kind == BrowserKind.PLAYLIST -> projection.selected.label
                projection.permanentPlaylist -> state.currentNode().label
                else -> "Manual"
            }
        val store = playerStateStore ?: return updateState { it.copy(status = "Player state is unavailable") }
        val command = captureCommand(store)
        val bookTitleContext = projection.albumContext && projection.audiobook && state.contentMode == ContentMode.AUDIOBOOK
        state = navigation.rememberPlaybackFilter(state, rowActionsReturn)
        notifyObservers()
        profileWorkers.execute {
            val kind =
                if (projection.permanentPlaylist) {
                    PlaybackQueueKind.PERMANENT_PLAYLIST
                } else {
                    PlaybackQueueKind.MANUAL
                }
            runCatching {
                check(PlayerPlaybackBridge.flushForContextChange()) { "Unable to save outgoing playback" }
                store.materializeQueue(
                    mediaIds,
                    kind,
                    playWhenReady = true,
                    temporary = !projection.permanentPlaylist,
                    initialOrdinal = projection.selectedQueueIndex,
                    chosenBindingId = projection.tracks[projection.selectedQueueIndex].sourceBindingId,
                    contextKey = projection.contextKey,
                    albumContext = projection.albumContext,
                    bookTitleContext = bookTitleContext,
                    bookTitleName = projection.titleName.takeIf { bookTitleContext },
                    bookTitleMembers = if (bookTitleContext) projection.sourceTracks.map { requireNotNull(it.mediaId) } else emptyList(),
                    bookQueueOrdinals =
                        if (bookTitleContext) {
                            projection.tracks.map { track -> projection.sourceTracks.indexOfFirst { it.id == track.id } }
                        } else {
                            emptyList()
                        },
                    audiobook = projection.audiobook,
                    resume = projection.resume,
                    profileId = command.profileId,
                    requireCurrentProfile = true,
                )
            }.onSuccess { queue ->
                completeQueueCommand(command, queue, queueLabel)
            }.onFailure { error -> completeCommand(command) { it.copy(status = error.asPlayerStateFailure()) } }
        }
    }

    // AI-INTENT: Preference and ban commit together; neither branch calls media transport.
    private fun persistReaction(
        objectKey: String,
        requested: Preference?,
        toggleBan: Boolean,
    ) {
        val store = playerStateStore ?: return updateState { it.copy(status = "Player state is unavailable") }
        val command = captureCommand(store)
        workers.execute {
            runCatching { store.updateReaction(objectKey, requested?.toStored(), toggleBan, command.profileId) }
                .onSuccess { committed ->
                    completeCommand(command) { old ->
                        val reactions = old.reactions.toMutableMap()
                        if (committed == StoredReaction()) reactions.remove(objectKey) else reactions[objectKey] = committed.toUi()
                        old.copy(reactions = reactions)
                    }
                }.onFailure { error -> completeCommand(command) { it.copy(status = error.asPlayerStateFailure()) } }
        }
    }

    private fun changeAudiobookSpeed(reset: Boolean) {
        if (state.contentMode != ContentMode.AUDIOBOOK ||
            !state.nowPlaying.effectiveBook ||
            state.nowPlaying.mediaId == null ||
            !selectedStorageAvailable
        ) {
            return
        }
        val store = playerStateStore ?: return updateState { it.copy(status = "Player state is unavailable") }
        val profile = activeProfileId
        // AI-INTENT: Serial read/step/write binds rapid gestures to their original profile.
        // https://wiki.271.pics/Riff/LATEST/player-audiobook-speed#profile-preference-and-execution
        profileWorkers.execute {
            val result =
                runCatching {
                    check(!azClosed && playerStateStore === store) { "Speed session ended" }
                    check(selectedStorageAvailable && dataStorage.selectedRootAvailable()) { "Selected external storage is unavailable" }
                    val speed = if (reset) AudiobookSpeed.NORMAL else store.loadAudiobookSpeed(profile).next()
                    store.saveAudiobookSpeed(profile, speed)
                    speed
                }
            main.post {
                if (azClosed || playerStateStore !== store || activeProfileId != profile) return@post
                result.fold(
                    onSuccess = { speed ->
                        state = state.copy(audiobookSpeed = speed)
                        notifyObservers()
                        PlayerPlaybackBridge.requestAudiobookParametersRefresh()
                    },
                    onFailure = { error ->
                        state = state.copy(status = "Speed update failed: ${error.message ?: error.javaClass.simpleName}")
                        notifyObservers()
                    },
                )
            }
        }
    }

    private fun persistContentMode() {
        val store = playerStateStore ?: return updateState { it.copy(status = "Player state is unavailable") }
        val command = captureCommand(store)
        val profile = command.profileId
        // AI-INTENT: Bind the write to the gesture's profile, not whichever profile is active when I/O runs.
        profileWorkers.execute {
            runCatching {
                val requested =
                    if (store.loadContentMode(profile) == StoredContentMode.MUSIC) {
                        StoredContentMode.AUDIOBOOK
                    } else {
                        StoredContentMode.MUSIC
                    }
                store.setContentMode(requested, profile)
                requested
            }.onSuccess { requested ->
                main.post {
                    if (!commandIsCurrent(command)) return@post
                    libraryProjection = libraryProjection.copy(contentMode = requested)
                    if (!libraryClient.activateProjection(libraryProjection)) {
                        libraryRoot = CuetreeBrowserTree.emptyRoot(requested)
                        state =
                            state.copy(
                                screen = PlayerScreen.MAIN,
                                root = projectPlaylistRoot(),
                                browserPath = emptyList(),
                                hierarchyRoot = null,
                                contentMode = requested.toUi(),
                                audiobookOverrides = libraryProjection.audiobookOverrides.mapValues { it.value.toUi() },
                            )
                        notifyObservers()
                        libraryClient.requestReload()
                    }
                    playbackConnection.refreshLibraryClassification()
                }
            }.onFailure { error -> completeCommand(command) { it.copy(status = error.asPlayerStateFailure()) } }
        }
    }

    private fun commitRowActionsAudiobookDraft(exitAction: PlayerUiAction): Boolean {
        val selected =
            state.actionsItemId
                ?.let { findBrowserItem(state.root, it) }
                ?.takeIf { it.kind == BrowserKind.ALBUM || it.kind == BrowserKind.FOLDER || it.kind == BrowserKind.TRACK }
                ?: return false
        val choice = state.audiobookChoiceDraft ?: AudiobookChoice.AUTO
        val current = state.audiobookOverrides[selected.reactionKey] ?: AudiobookChoice.AUTO
        val memberTrackKeys =
            if (selected.kind == BrowserKind.ALBUM) {
                selected.children
                    .asSequence()
                    .filter { it.kind == BrowserKind.TRACK }
                    .map(BrowserItem::reactionKey)
                    .toSet()
            } else {
                emptySet()
            }
        val hasMemberOverrides = memberTrackKeys.any(state.audiobookOverrides::containsKey)
        if (choice == current && !hasMemberOverrides) return false
        val store =
            playerStateStore ?: run {
                state = state.copy(status = "Player state is unavailable")
                notifyObservers()
                return true
            }
        val command = captureCommand(store)
        val projection = libraryProjection
        val objectKey = selected.reactionKey
        state = state.copy(audiobookCommitInProgress = true)
        notifyObservers()
        profileWorkers.execute {
            runCatching {
                store.commitAudiobookOverride(objectKey, choice.toStored(), memberTrackKeys, command.profileId)
            }.onSuccess {
                val updatedOverrides =
                    projection.audiobookOverrides
                        .toMutableMap()
                        .apply {
                            memberTrackKeys.forEach(::remove)
                            val value = choice.toStored()
                            // AI-INTENT: Map.put returns the prior value, so a first insertion cannot drive Auto removal.
                            // https://wiki.271.pics/Riff/LATEST/player-content-mode#content-mode-and-profile-overrides
                            if (value == null) remove(objectKey) else put(objectKey, value)
                        }.toMap()
                main.post {
                    if (!commandIsCurrent(command)) return@post
                    libraryProjection = projection.copy(audiobookOverrides = updatedOverrides)
                    state =
                        state
                            .copy(
                                audiobookOverrides = updatedOverrides.mapValues { it.value.toUi() },
                                audiobookCommitInProgress = false,
                                status =
                                    "Audiobook setting: " +
                                        choice.name.lowercase().replaceFirstChar(Char::uppercase),
                            ).also(navigation::rememberHierarchy)
                    notifyObservers()
                    libraryClient.requestReload()
                    playbackConnection.refreshLibraryClassification()
                    dispatch(exitAction)
                }
            }.onFailure { error ->
                completeCommand(command) {
                    it.copy(
                        audiobookCommitInProgress = false,
                        status = error.asPlayerStateFailure(),
                    )
                }
            }
        }
        return true
    }

    private fun PlayerUiAction.leavesRowActions(): Boolean =
        this == PlayerUiAction.CloseRowActions ||
            this is PlayerUiAction.Play ||
            this is PlayerUiAction.OpenPlaylistSelector

    private fun reloadAudiobookPreferences(store: PlayerStateStore) {
        libraryProjection =
            PlayerLibraryProjection(
                contentMode = store.loadContentMode(activeProfileId),
                audiobookOverrides = store.loadAudiobookOverrides(activeProfileId).toMap(),
            )
    }

    // AI-INTENT: Reactions never call this block; queue/checkpoint authority remains Player SQLite.
    private fun dispatchPlaybackAction(action: PlayerUiAction) {
        when (action) {
            PlayerUiAction.Previous -> playbackConnection.previous()
            PlayerUiAction.Next -> playbackConnection.next()
            PlayerUiAction.TogglePlayback -> playbackConnection.togglePlayback()
            PlayerUiAction.ToggleRepeat -> playbackConnection.toggleRepeat()
            PlayerUiAction.ToggleShuffle -> playbackConnection.toggleShuffle()
            is PlayerUiAction.Seek -> playbackConnection.seek(action.fraction)
            else -> Unit
        }
    }

    private fun applyPlaybackEngineState(engine: PlaybackEngineState) {
        if (engine.queueRevisionId == null && activeQueue != null && engine.error == null) return
        val expectedRevision = engine.queueRevisionId
        if (expectedRevision != null && activeQueue?.revisionId != expectedRevision) {
            workers.execute {
                val reloaded = playerStateStore?.loadActiveQueue()
                if (reloaded?.revisionId == expectedRevision) {
                    activeQueue = reloaded
                    main.post {
                        playbackConnection.currentState()?.takeIf { it.queueRevisionId == expectedRevision }?.let(
                            ::applyPlaybackEngineState,
                        )
                    }
                }
            }
            return
        }
        val queue = activeQueue
        val order =
            queue
                ?.occurrences
                .orEmpty()
                .let {
                    if (engine.shuffle) {
                        it.sortedBy { occurrence ->
                            occurrence.shuffleOrdinal
                        }
                    } else {
                        it.sortedBy { occurrence -> occurrence.ordinal }
                    }
                }
        val index = order.indexOfFirst { it.occurrenceId == engine.occurrenceId }
        val track =
            engine.chosenBindingId?.let { findByBinding(state.root, it) }
                ?: tracks.firstOrNull { it.mediaId == engine.mediaId }
        val duration = engine.durationMs.coerceAtLeast(0L)
        val position = engine.positionMs.coerceIn(0L, duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        val fraction = if (duration > 0L) (position.toDouble() / duration.toDouble()).toFloat() else 0f
        val artwork =
            engine.artwork
                ?: artworkForProjection(
                    oldMediaId = state.nowPlaying.mediaId,
                    oldArtwork = state.nowPlaying.artwork,
                    newMediaId = engine.mediaId,
                    newBindingId = engine.chosenBindingId,
                )
        queue?.let {
            activeQueue =
                it.copy(
                    checkpoint =
                        PlaybackCheckpoint(
                            occurrenceId = engine.occurrenceId,
                            positionMs = position,
                            playWhenReady = engine.playWhenReady,
                            repeat = engine.repeat,
                            shuffle = engine.shuffle,
                            chosenBindingId = engine.chosenBindingId,
                        ),
                )
        }
        val queueMediaIds = order.map { it.mediaId }
        val currentOrdinal = queue?.occurrences?.firstOrNull { it.occurrenceId == engine.occurrenceId }?.ordinal
        if (queue?.bookTitleContext == true && duration > 0L && currentOrdinal != null && engine.mediaId != null) {
            val contextKey = queue.contextKey
            val frozenOrdinal = bookQueueTarget(queue, currentOrdinal, engine.mediaId)?.second
            if (contextKey != null && frozenOrdinal != null && recordedBookDurations.add(Triple(contextKey, frozenOrdinal, duration))) {
                val mediaId = engine.mediaId
                workers.execute {
                    runCatching {
                        val store = playerStateStore ?: return@runCatching
                        val titleKey = bookTitleKeyForQueue(queue, store) ?: return@runCatching
                        store.recordBookDuration(
                            titleKey,
                            frozenOrdinal,
                            mediaId,
                            duration * 1_000L,
                            queue.sourceProfileId,
                        )
                        main.post { if (!azClosed) notifyObservers() }
                    }
                }
            }
        }
        updateState { old ->
            old.copy(
                queueMediaIds = queueMediaIds,
                queueIndex = index,
                playbackDiagnostics =
                    playbackDiagnosticsFor(
                        engine.mediaId,
                        queueMediaIds,
                        aiFeaturesByMediaId,
                        aiPteByMediaId,
                        replayGainByMediaId,
                        topInferredGenreByMediaId,
                    ),
                nowPlaying =
                    old.nowPlaying.copy(
                        mediaId = engine.mediaId,
                        title = engine.title,
                        artist = engine.artist,
                        album = engine.album,
                        chapter =
                            currentChapterLabel(
                                engine.chosenBindingId?.let(tocChaptersByBindingId::get).orEmpty(),
                                position,
                                duration.takeIf { it > 0L },
                            ),
                        positionFraction = fraction,
                        elapsed = formatSeconds((position / 1_000L).toInt()),
                        duration = formatSeconds((duration / 1_000L).toInt()),
                        playing = engine.playWhenReady,
                        shuffle = engine.shuffle,
                        repeat = engine.repeat,
                        aiReactionEligible = queue?.bookTitleContext != true && track?.aiReactionEligible == true,
                        bookTitleContext = queue?.bookTitleContext == true,
                        effectiveBook = engine.mediaId in effectiveBookMediaIds,
                        artwork = artwork,
                    ),
                status = engine.error?.let { "Playback error: $it" } ?: old.status,
            )
        }
    }

    private fun applyQueueProjection(
        old: PlayerUiState,
        queue: PlaybackQueueSnapshot,
        screen: PlayerScreen,
        label: String,
        artwork: ArtworkUiState,
    ): PlayerUiState {
        val playbackOrder = queue.orderedOccurrences()
        val index =
            queue.checkpoint.occurrenceId
                .toQueueIndex(queue)
                .coerceAtLeast(0)
        val occurrence = playbackOrder.getOrNull(index)
        val track =
            occurrence?.let { item ->
                if (queue.kind != PlaybackQueueKind.PERMANENT_PLAYLIST) {
                    queueTrackForOccurrence(old.root, queue.contextKey, item.ordinal, item.mediaId)
                } else {
                    null
                }
            } ?: tracks.firstOrNull { it.mediaId == occurrence?.mediaId }
        val queueMediaIds = playbackOrder.map { it.mediaId }
        val fraction = (queue.checkpoint.positionMs / 330_000f).coerceIn(0f, 1f)
        return navigation.cancelAzSearch(old).copy(
            screen = screen,
            queueMediaIds = queueMediaIds,
            queueIndex = index,
            playbackDiagnostics =
                playbackDiagnosticsFor(
                    occurrence?.mediaId,
                    queueMediaIds,
                    aiFeaturesByMediaId,
                    aiPteByMediaId,
                    replayGainByMediaId,
                    topInferredGenreByMediaId,
                ),
            nowPlaying =
                old.nowPlaying.copy(
                    mediaId = occurrence?.mediaId,
                    bookTitleContext = queue.bookTitleContext,
                    title = track?.label ?: old.nowPlaying.title,
                    artist = track?.artist.orEmpty(),
                    album = track?.album.orEmpty(),
                    chapter =
                        currentChapterLabel(
                            queue.checkpoint.chosenBindingId
                                ?.let(tocChaptersByBindingId::get)
                                .orEmpty(),
                            queue.checkpoint.positionMs,
                            null,
                        ),
                    elapsed = formatSeconds((queue.checkpoint.positionMs / 1_000L).toInt()),
                    positionFraction = fraction,
                    playing = queue.checkpoint.playWhenReady,
                    repeat = queue.checkpoint.repeat,
                    shuffle = queue.checkpoint.shuffle,
                    aiReactionEligible = !queue.bookTitleContext && track?.aiReactionEligible == true,
                    artwork = artwork,
                ),
            status = "$label queue READY · ${queue.occurrences.size} tracks",
        )
    }

    private fun openPlayerStateStoreOrNull(): PlayerStateStore? {
        val root = dataStorage.activeRootOrNull()
        selectedStorageAvailable = root != null
        return PlayerProcessStore.open(context, recentHistoryGenerations, bookmarkMinSpacingSeconds)
    }

    // Mounted-volume presentation only. Live removal deliberately has no hot-fallback contract:
    // mmap/SQLite users continue against the chosen root until the process stops or fails.
    private fun reconcileSelectedStorage() {
        val nowAvailable = dataStorage.selectedRootAvailable()
        selectedStorageAvailable = nowAvailable
        updateState { old ->
            old.copy(
                selectedStorageAvailable = nowAvailable,
                dataStorageLocations = availableDataStorageItems(),
                status =
                    if (!nowAvailable) {
                        "Selected external storage was disconnected; stop Riff before changing storage"
                    } else {
                        old.status
                    },
            )
        }
    }

    private fun resetPlayerProjectionFromStore() {
        commandEpoch++
        activeProfileId = playerStateStore?.activeProfile() ?: 1
        playerStateStore?.let(::reloadAudiobookPreferences)
            ?: run { libraryProjection = PlayerLibraryProjection() }
        draftProfileId = null
        val selectedLocation = dataStorage.selection().location.toPlayer()
        val settings =
            playerStateStore
                ?.let { store -> runCatching { store.loadSettingsForProfile(activeProfileId, selectedLocation) }.getOrNull() }
                ?: PlayerSettings(dataLocation = selectedLocation)
        settingsController =
            PlayerSettingsController(
                settings.copy(
                    dimBacklight = installationDimBacklight(),
                    replayGainMode = settingsController.snapshot().committed.replayGainMode,
                    replayGainTarget = settingsController.snapshot().committed.replayGainTarget,
                ),
                ::settingsAccessPolicy,
            )
        activeQueue = playerStateStore?.loadActiveQueue()
    }

    private fun captureCommand(store: PlayerStateStore) = ProfileCommandContext(store, activeProfileId, commandEpoch)

    private fun commandIsCurrent(command: ProfileCommandContext<PlayerStateStore>) =
        !azClosed && command.isCurrent(playerStateStore, activeProfileId, commandEpoch)

    private fun completeCommand(
        command: ProfileCommandContext<PlayerStateStore>,
        transform: (PlayerUiState) -> PlayerUiState,
    ) = updateState { old -> if (commandIsCurrent(command)) transform(old) else old }

    private fun completeQueueCommand(
        command: ProfileCommandContext<PlayerStateStore>,
        queue: PlaybackQueueSnapshot,
        label: String,
    ) = completeCommand(command) { old ->
        activeQueue = queue
        playbackConnection.activateReadyQueue()
        applyQueueProjection(old, queue, PlayerScreen.PLAYBACK, label, ArtworkUiState(queue.checkpoint.chosenBindingId))
    }

    private fun updateState(transform: (PlayerUiState) -> PlayerUiState) {
        main.post {
            if (azClosed) return@post
            val previousReactions = state.reactions
            state = transform(state).also(navigation::rememberHierarchy)
            notifyObservers()
            if (state.reactions != previousReactions) {
                scheduleAiHeatmapRebuild()
                scheduleAiPteAvailabilityRebuild()
            }
        }
    }

    private fun notifyObservers() {
        if (!state.playlistMutationInProgress) playlistWriteProfile = null
        val queue = activeQueue
        val current = queue?.occurrences?.firstOrNull { it.occurrenceId == queue.checkpoint.occurrenceId }
        val durationUs =
            playbackConnection
                .currentState()
                ?.takeIf {
                    it.queueRevisionId == queue?.revisionId &&
                        it.occurrenceId == current?.occurrenceId &&
                        it.durationMs in 1L..Long.MAX_VALUE / 1_000L
                }?.durationMs
                ?.times(1_000L)
        val positionUs =
            queue
                ?.checkpoint
                ?.positionMs
                ?.takeIf { it in 0L..Long.MAX_VALUE / 1_000L }
                ?.times(1_000L)
        // The store's immutable committed cache shares the atomic writer's admission calculation.
        // Authority: https://wiki.271.pics/Riff/LATEST/player-bookmarks#mark-action-and-spacing
        val bookmarkAllowed =
            bookmarkPolicy.allowed &&
                !bookmarkWriteInProgress &&
                queue != null &&
                current != null &&
                positionUs != null &&
                playerStateStore?.canMarkBook(queue, current.ordinal, current.mediaId, positionUs, durationUs) == true
        state =
            state.copy(
                bookmarkMarkAllowed = bookmarkAllowed,
                nowPlaying = state.nowPlaying.copy(effectiveBook = state.nowPlaying.mediaId in effectiveBookMediaIds),
            )
        if (state.screen !in setOf(PlayerScreen.AZ_SEARCH, PlayerScreen.ROW_ACTIONS, PlayerScreen.PLAYLIST_SELECTOR)) {
            state = state.copy(azDraftSteps = null, azAutoApplyPending = false)
        }
        if (state.azDraftSteps == null) {
            navigation.clearSearchReturn()
        }
        if (state.screen != PlayerScreen.ROW_ACTIONS && state.screen != PlayerScreen.PLAYLIST_SELECTOR) rowActionsReturn = null
        state =
            state.copy(
                azBreadcrumbs =
                    azBreadcrumbScopes(state.root, state.browserPath, state.azSteps) {
                        navigation.containers[it]?.azSteps.orEmpty()
                    },
            )
        scheduleAzSearch()
        observers.forEach { it(state) }
    }

    private fun scheduleAzSearch() {
        if (azClosed) return
        if (state.screen != PlayerScreen.HIERARCHY && state.screen != PlayerScreen.AZ_SEARCH) {
            azFuture?.cancel(true)
            azRequest = null
            azRequestToken += 1L
            return
        }
        val query = state.azQuery()
        if (state.currentAzSnapshot() != null) {
            if (azRequest != null) {
                azFuture?.cancel(true)
                azRequest = null
                azRequestToken += 1L
            }
            return
        }
        if (azRequest?.sameAs(query) == true) return
        azFuture?.cancel(true)
        azRequest = query
        val token = ++azRequestToken
        azFuture =
            azWorkers.submit {
                try {
                    val prepared =
                        azPrepared?.takeIf { it.source === query.source }
                            ?: AzPreparedSection(query.source).also { azPrepared = it }
                    val snapshot = prepared.snapshot(query)
                    main.post {
                        if (token != azRequestToken || !query.sameAs(state.azQuery())) return@post
                        azRequest = null
                        val changedFilter = state.azDisplayedSteps() != (query.draft ?: query.applied)
                        var updated =
                            state.copy(
                                azSnapshot = snapshot,
                                selectorIndex = if (changedFilter) 0 else state.selectorIndex,
                                selectorOffset = if (changedFilter) 0 else state.selectorOffset,
                                focusedItemId = if (changedFilter) snapshot.draftItems.firstOrNull()?.id else state.focusedItemId,
                            )
                        if (updated.azAutoApplyPending &&
                            updated.settings.autoSearch &&
                            shouldAutoApplyAzSearch(true, snapshot.draftItems.size, updated.azVisibleCapacity)
                        ) {
                            updated = applyAzSearch(updated)
                        }
                        state = updated.copy(azAutoApplyPending = false).also(navigation::rememberHierarchy)
                        notifyObservers()
                    }
                } catch (_: CancellationException) {
                    // Replacement or close: a partial or stale result is never published.
                }
            }
    }

    private fun findByBinding(
        node: BrowserItem,
        bindingId: Long,
    ): BrowserItem? = if (node.sourceBindingId == bindingId) node else node.children.firstNotNullOfOrNull { findByBinding(it, bindingId) }

    private fun formatSeconds(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

    private fun ApplicationDataLocation.toPlayer(): PlayerDataLocation = PlayerDataLocation.valueOf(name)

    private fun DataLocation.toPlayer(): PlayerDataLocation = PlayerDataLocation.valueOf(name)

    private fun PlayerDatabaseRebuild.toUi(): DatabaseRebuild = DatabaseRebuild.valueOf(name)

    private fun Preference.toStored(): StoredPreference = StoredPreference.valueOf(name)

    private fun StoredReaction.toUi(): ReactionState =
        ReactionState(
            preference = Preference.valueOf(preference.name),
            ownBanned = ownBanned,
        )

    private fun StoredContentMode.toUi(): ContentMode = ContentMode.valueOf(name)

    private fun StoredAudiobookOverride.toUi(): AudiobookChoice = AudiobookChoice.valueOf(name)

    private fun AudiobookChoice.toStored(): StoredAudiobookOverride? =
        when (this) {
            AudiobookChoice.BOOK -> StoredAudiobookOverride.BOOK
            AudiobookChoice.MUSIC -> StoredAudiobookOverride.MUSIC
            AudiobookChoice.AUTO -> null
        }

    private fun Long?.toQueueIndex(queue: PlaybackQueueSnapshot?): Int =
        queue?.orderedOccurrences()?.indexOfFirst { it.occurrenceId == this } ?: -1

    private fun Throwable.asPlayerStateFailure(): String = "Player state update failed: ${message ?: javaClass.simpleName}"

    private companion object {
        val SETTINGS_DRAFT_SCREENS =
            setOf(
                PlayerScreen.SETTINGS,
                PlayerScreen.SETTINGS_MORE,
                PlayerScreen.SAF_GRANTS,
                PlayerScreen.DATA_STORAGE,
                PlayerScreen.CALIBRATION,
                PlayerScreen.SCREEN_DIAGONAL,
            )
        const val TAG = "RiffPlayerUi"
        const val PORTABLE_DATA_STORAGE_PREFIX = "portable:"
    }
}
