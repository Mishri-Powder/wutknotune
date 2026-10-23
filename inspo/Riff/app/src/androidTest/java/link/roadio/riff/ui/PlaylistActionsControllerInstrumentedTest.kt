package link.roadio.riff.ui

import android.os.StrictMode
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.playback.AudiobookSpeed
import link.roadio.riff.player.core.selection.AiFeaturePoint
import link.roadio.riff.player.core.selection.AiHeatmap
import link.roadio.riff.player.core.selection.AiQueueCandidate
import link.roadio.riff.player.core.settings.PlayerDataLocation
import link.roadio.riff.player.storage.PlaybackQueueKind
import link.roadio.riff.player.storage.PlayerProcessStore
import link.roadio.riff.player.storage.PlayerStateStore
import link.roadio.riff.player.storage.StoredContentMode
import link.roadio.riff.storage.root.ApplicationDataStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Exercises the production controller/store boundary with an isolated, admitted packaged Phone fixture. */
class PlaylistActionsControllerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun queuedSpeedCommandsRetainProfileOrderAndCannotReplaceAnotherProfilesUi() =
        withFixture { controller, store, tracks ->
            val originalProfile = store.activeProfile()
            val speeds = PlayerStateStore.VALID_PROFILES.associateWith(store::loadAudiobookSpeed)
            val modes = PlayerStateStore.VALID_PROFILES.associateWith(store::loadContentMode)
            val queue = store.loadActiveQueue()
            val release = blockWorker(controller, "profileWorkers")
            try {
                store.commitProfileSelection(1, store.loadSettingsForProfile(1, PlayerDataLocation.HU))
                store.saveAudiobookSpeed(1, AudiobookSpeed.NORMAL)
                store.saveAudiobookSpeed(2, AudiobookSpeed.SEVEN_TENTHS)
                store.setContentMode(StoredContentMode.AUDIOBOOK, 1)
                onMain {
                    field(controller, "activeProfileId", 1)
                    field(controller, "effectiveBookMediaIds", setOf(requireNotNull(tracks.first().mediaId)))
                    field(
                        controller,
                        "state",
                        controller.state.copy(
                            screen = PlayerScreen.PLAYBACK,
                            contentMode = ContentMode.AUDIOBOOK,
                            audiobookSpeed = AudiobookSpeed.NORMAL,
                            nowPlaying = controller.state.nowPlaying.copy(mediaId = tracks.first().mediaId, effectiveBook = true),
                        ),
                    )
                    controller.dispatch(PlayerUiAction.AdvanceAudiobookSpeed)
                    controller.dispatch(PlayerUiAction.AdvanceAudiobookSpeed)
                    controller.dispatch(PlayerUiAction.ResetAudiobookSpeed)
                    controller.dispatch(PlayerUiAction.AdvanceAudiobookSpeed)
                }
                assertEquals(AudiobookSpeed.NORMAL, store.loadAudiobookSpeed(1))
                store.commitProfileSelection(2, store.loadSettingsForProfile(2, PlayerDataLocation.HU))
                onMain {
                    field(controller, "activeProfileId", 2)
                    field(
                        controller,
                        "state",
                        controller.state.copy(contentMode = ContentMode.MUSIC, audiobookSpeed = AudiobookSpeed.SEVEN_TENTHS),
                    )
                }
                release.countDown()
                await(controller) { store.loadAudiobookSpeed(1) == AudiobookSpeed.ELEVEN_TENTHS }
                onMain { assertEquals(AudiobookSpeed.SEVEN_TENTHS, controller.state.audiobookSpeed) }
                assertEquals(AudiobookSpeed.SEVEN_TENTHS, store.loadAudiobookSpeed(2))
                assertEquals(queue?.revisionId, store.loadActiveQueue()?.revisionId)
                dispatch(controller, PlayerUiAction.AdvanceAudiobookSpeed)
                dispatch(controller, PlayerUiAction.ResetAudiobookSpeed)
                assertEquals(AudiobookSpeed.SEVEN_TENTHS, store.loadAudiobookSpeed(2))
                var recreated: RiffPlayerUiController? = null
                try {
                    onMain { recreated = RiffPlayerUiController(instrumentation.targetContext) }
                    assertEquals(AudiobookSpeed.SEVEN_TENTHS, requireNotNull(recreated).state.audiobookSpeed)
                } finally {
                    onMain { recreated?.close() }
                }
            } finally {
                release.countDown()
                speeds.forEach { (profile, speed) -> store.saveAudiobookSpeed(profile, speed) }
                modes.forEach { (profile, mode) -> store.setContentMode(mode, profile) }
                store.commitProfileSelection(originalProfile, store.loadSettingsForProfile(originalProfile, PlayerDataLocation.HU))
            }
        }

    @Test
    fun speedProfileSaveRestoresIndependentPreferencesAndEndedStoreRejectsQueuedWrites() =
        withFixture { controller, store, tracks ->
            val originalProfile = store.activeProfile()
            val speeds = PlayerStateStore.VALID_PROFILES.associateWith(store::loadAudiobookSpeed)
            val modes = PlayerStateStore.VALID_PROFILES.associateWith(store::loadContentMode)
            try {
                store.saveAudiobookSpeed(1, AudiobookSpeed.SEVEN_TENTHS)
                store.saveAudiobookSpeed(2, AudiobookSpeed.FIFTEEN_TENTHS)
                store.setContentMode(StoredContentMode.AUDIOBOOK, 1)
                store.setContentMode(StoredContentMode.MUSIC, 2)
                dispatch(controller, PlayerUiAction.OpenMain)
                dispatch(controller, PlayerUiAction.OpenSettingsLongPress)
                dispatch(controller, PlayerUiAction.SetDraftProfile(2))
                dispatch(controller, PlayerUiAction.SaveSettings)
                await(controller) { !it.settingsSaveInProgress && it.settings.profileId == 2 }
                assertEquals(AudiobookSpeed.FIFTEEN_TENTHS, controller.state.audiobookSpeed)
                assertEquals(ContentMode.MUSIC, controller.state.contentMode)
                dispatch(controller, PlayerUiAction.OpenSettingsLongPress)
                dispatch(controller, PlayerUiAction.SetDraftProfile(1))
                dispatch(controller, PlayerUiAction.SaveSettings)
                await(controller) { !it.settingsSaveInProgress && it.settings.profileId == 1 }
                assertEquals(AudiobookSpeed.SEVEN_TENTHS, controller.state.audiobookSpeed)
                assertEquals(ContentMode.AUDIOBOOK, controller.state.contentMode)
                val release = blockWorker(controller, "profileWorkers")
                try {
                    onMain {
                        field(controller, "effectiveBookMediaIds", setOf(requireNotNull(tracks.first().mediaId)))
                        field(
                            controller,
                            "state",
                            controller.state.copy(
                                screen = PlayerScreen.PLAYBACK,
                                nowPlaying = controller.state.nowPlaying.copy(mediaId = tracks.first().mediaId, effectiveBook = true),
                            ),
                        )
                        controller.dispatch(PlayerUiAction.AdvanceAudiobookSpeed)
                        val storeField =
                            RiffPlayerUiController::class.java
                                .getDeclaredField(
                                    "playerStateStore",
                                ).apply { isAccessible = true }
                        storeField.set(controller, null)
                    }
                    release.countDown()
                    val worker =
                        RiffPlayerUiController::class.java
                            .getDeclaredField("profileWorkers")
                            .apply {
                                isAccessible = true
                            }.get(controller) as ExecutorService
                    worker.submit {}.get(5, TimeUnit.SECONDS)
                    onMain { field(controller, "playerStateStore", store) }
                    assertEquals(AudiobookSpeed.SEVEN_TENTHS, store.loadAudiobookSpeed(1))
                    onMain { field(controller, "selectedStorageAvailable", false) }
                    dispatch(controller, PlayerUiAction.AdvanceAudiobookSpeed)
                    assertEquals(AudiobookSpeed.SEVEN_TENTHS, store.loadAudiobookSpeed(1))
                    onMain { field(controller, "selectedStorageAvailable", true) }
                } finally {
                    release.countDown()
                    onMain { field(controller, "playerStateStore", store) }
                }
            } finally {
                speeds.forEach { (profile, speed) -> store.saveAudiobookSpeed(profile, speed) }
                modes.forEach { (profile, mode) -> store.setContentMode(mode, profile) }
                store.commitProfileSelection(originalProfile, store.loadSettingsForProfile(originalProfile, PlayerDataLocation.HU))
            }
        }

    @Test
    fun aiAddFreezesOrderCreatesOrAppendsAndNeverActivatesPlayback() =
        withFixture { controller, store, tracks ->
            val before = store.loadActiveQueue()
            val beforeHistory = store.loadAiPlaybackHistory()
            val selection = controller.state.aiSelection
            dispatch(controller, PlayerUiAction.OpenAiPlaylistSelector)
            await(controller) { it.screen == PlayerScreen.PLAYLIST_SELECTOR && !it.playlistMutationInProgress }
            val frozen = frozenIds(controller)
            assertEquals(10, frozen.size)
            assertTrue(frozen.all { id -> tracks.any { it.mediaId == id } })
            assertEquals(before, store.loadActiveQueue())
            assertTrue(controller.state.playlistSelectorChoices.any { it.playlistId == null })

            // The destination must consume the original result even if acquisition inputs change.
            onMain { field(controller, "tracks", emptyList<BrowserItem>()) }
            dispatch(controller, PlayerUiAction.RequestNewPlaylist)
            dispatch(controller, PlayerUiAction.UpdatePlaylistName(""))
            dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
            await(controller) { !it.playlistMutationInProgress && it.status.startsWith("Playlist") }
            assertEquals(frozen, frozenIds(controller))
            assertEquals(before, store.loadActiveQueue())
            val name = "AI fixture ${UUID.randomUUID()}"
            dispatch(controller, PlayerUiAction.UpdatePlaylistName(name))
            dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
            await(controller) { it.screen == PlayerScreen.HIERARCHY && !it.playlistMutationInProgress }
            val playlist = store.listPlaylists().single { it.name == name }
            assertEquals(frozen, playlist.occurrences.map { it.mediaId })
            assertEquals(listOf(HierarchyRoot.PLAYLISTS.categoryId, playlistItemId(playlist.playlistId)), controller.state.browserPath)
            assertEquals(0, controller.state.selectorIndex)
            assertEquals(selection, controller.state.aiSelection)
            assertEquals(before, store.loadActiveQueue())

            onMain {
                field(controller, "tracks", tracks)
                field(controller, "state", controller.state.copy(screen = PlayerScreen.AI_ACTIONS))
            }
            dispatch(controller, PlayerUiAction.OpenAiPlaylistSelector)
            await(controller) { it.screen == PlayerScreen.PLAYLIST_SELECTOR && !it.playlistMutationInProgress }
            val second = frozenIds(controller)
            onMain { field(controller, "tracks", emptyList<BrowserItem>()) }
            dispatch(controller, PlayerUiAction.AddToPlaylist(playlist.playlistId))
            await(controller) { it.screen == PlayerScreen.HIERARCHY && !it.playlistMutationInProgress }
            assertEquals(
                frozen + second,
                store
                    .listPlaylists()
                    .single { it.playlistId == playlist.playlistId }
                    .occurrences
                    .map { it.mediaId },
            )
            assertEquals(before, store.loadActiveQueue())
            assertEquals(beforeHistory, store.loadAiPlaybackHistory())
        }

    @Test
    fun cancelAndEmptyAiAddLeavePlaylistsAndQueueUnchangedAndPermitRetry() =
        withFixture { controller, store, tracks ->
            val before = store.loadActiveQueue()
            val playlists = store.listPlaylists()
            val selection = controller.state.aiSelection
            dispatch(controller, PlayerUiAction.OpenAiPlaylistSelector)
            await(controller) { it.screen == PlayerScreen.PLAYLIST_SELECTOR && !it.playlistMutationInProgress }
            dispatch(controller, PlayerUiAction.RequestNewPlaylist)
            dispatch(controller, PlayerUiAction.CancelPlaylistName)
            dispatch(controller, PlayerUiAction.ClosePlaylistSelector)
            assertEquals(PlayerScreen.AI, controller.state.screen)
            assertEquals(selection, controller.state.aiSelection)
            assertTrue(frozenIds(controller).isEmpty())
            onMain { field(controller, "tracks", emptyList<BrowserItem>()) }
            dispatch(controller, PlayerUiAction.OpenAiActions)
            dispatch(controller, PlayerUiAction.OpenAiPlaylistSelector)
            await(controller) { !it.playlistMutationInProgress && it.status == "No eligible tracks for AI queue" }
            assertEquals(playlists, store.listPlaylists())
            assertEquals(before, store.loadActiveQueue())
            onMain { field(controller, "tracks", tracks) }
            dispatch(controller, PlayerUiAction.OpenAiPlaylistSelector)
            await(controller) { it.screen == PlayerScreen.PLAYLIST_SELECTOR && !it.playlistMutationInProgress }
            assertEquals(10, frozenIds(controller).size)
        }

    @Test
    fun ordinaryTrackAddCanCreatePlaylistAndAiPlayActivatesCurrentSize() =
        withFixture { controller, store, tracks ->
            val before = store.loadActiveQueue()
            dispatch(controller, PlayerUiAction.OpenRowActions(tracks.first().id))
            dispatch(controller, PlayerUiAction.OpenPlaylistSelector(tracks.first().id))
            dispatch(controller, PlayerUiAction.RequestNewPlaylist)
            val name = "Track fixture ${UUID.randomUUID()}"
            dispatch(controller, PlayerUiAction.UpdatePlaylistName(name))
            dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
            await(controller) { it.screen == PlayerScreen.HIERARCHY && !it.playlistMutationInProgress }
            assertEquals(
                listOf(tracks.first().mediaId),
                store
                    .listPlaylists()
                    .single { it.name == name }
                    .occurrences
                    .map { it.mediaId },
            )
            assertEquals(before, store.loadActiveQueue())
            onMain { field(controller, "state", controller.state.copy(screen = PlayerScreen.AI_ACTIONS)) }
            dispatch(controller, PlayerUiAction.CreateAiQueue)
            await(controller) { it.screen == PlayerScreen.PLAYBACK && it.queueMediaIds.size == 10 }
            val active = requireNotNull(store.loadActiveQueue())
            assertEquals(PlaybackQueueKind.AI, active.kind)
            assertTrue(active.temporary)
            assertTrue(active.checkpoint.playWhenReady)
            assertEquals(10, active.occurrences.size)
            assertEquals(active.occurrences.map { it.mediaId }, controller.state.queueMediaIds)
        }

    @Test
    fun namedDeleteCancelAndRepeatedConfirmationPreserveActiveQueue() =
        withFixture { controller, store, tracks ->
            val active =
                store.materializeQueue(
                    tracks.take(3).map {
                        requireNotNull(it.mediaId)
                    },
                    PlaybackQueueKind.PERMANENT_PLAYLIST,
                    false,
                    temporary = false,
                )
            val history = store.loadAiPlaybackHistory()
            dispatch(controller, PlayerUiAction.OpenAiPlaylistSelector)
            await(controller) { it.screen == PlayerScreen.PLAYLIST_SELECTOR && !it.playlistMutationInProgress }
            dispatch(controller, PlayerUiAction.RequestNewPlaylist)
            val name = "Delete fixture ${UUID.randomUUID()}"
            dispatch(controller, PlayerUiAction.UpdatePlaylistName(name))
            dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
            await(controller) { it.screen == PlayerScreen.HIERARCHY && !it.playlistMutationInProgress }
            val playlist = store.listPlaylists().single { it.name == name }
            val revision = store.stateRevision()
            dispatch(controller, PlayerUiAction.RequestPlaylistDelete(playlist.playlistId))
            assertEquals(name, controller.state.pendingPlaylistDelete?.name)
            dispatch(controller, PlayerUiAction.CancelPlaylistDelete)
            assertEquals(null, controller.state.pendingPlaylistDelete)
            assertEquals(playlist, store.listPlaylists().single { it.playlistId == playlist.playlistId })
            assertEquals(revision, store.stateRevision())
            dispatch(controller, PlayerUiAction.RequestPlaylistDelete(playlist.playlistId))
            onMain {
                controller.dispatch(PlayerUiAction.ConfirmPlaylistDelete)
                controller.dispatch(PlayerUiAction.ConfirmPlaylistDelete)
            }
            await(controller) { it.pendingPlaylistDelete == null && !it.playlistMutationInProgress }
            assertTrue(store.listPlaylists().none { it.playlistId == playlist.playlistId })
            assertEquals(active, store.loadActiveQueue())
            assertEquals(history, store.loadAiPlaybackHistory())
        }

    @Test
    fun phoneSettingsRequiresHoldAndPreservesDraftCancel() =
        withFixture { controller, store, _ ->
            val before = store.activeProfile()
            dispatch(controller, PlayerUiAction.OpenMain)
            await(controller) { it.screen == PlayerScreen.MAIN }
            dispatch(controller, PlayerUiAction.OpenSettings)
            dispatch(controller, PlayerUiAction.OpenSettingsRecovery)
            onMain {
                assertEquals(PlayerScreen.MAIN, controller.state.screen)
                assertEquals(link.roadio.riff.player.core.settings.SettingsEntry.LONG_PRESS, controller.state.settingsEntry)
            }
            dispatch(controller, PlayerUiAction.OpenSettingsLongPress)
            await(controller) { it.screen == PlayerScreen.SETTINGS && it.settingsDraft != null }
            dispatch(controller, PlayerUiAction.SetDraftProfile(if (before == 1) 2 else 1))
            dispatch(controller, PlayerUiAction.CancelSettings)
            await(controller) { it.screen == PlayerScreen.MAIN && it.settingsDraft == null }
            assertEquals(before, store.activeProfile())
        }

    @Test
    fun acceptedPlaylistWriteBlocksCancelBackIdleAndProfileNavigation() =
        withFixture { controller, store, tracks ->
            val original = store.activeProfile()
            val name = "Accepted ${UUID.randomUUID()}"
            prepareName(controller, tracks, name)
            val release = blockWorker(controller, "workers")
            try {
                dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
                listOf(
                    PlayerUiAction.CancelPlaylistName,
                    PlayerUiAction.IdleTimeout,
                    PlayerUiAction.OpenMain,
                    PlayerUiAction.OpenSettingsLongPress,
                    PlayerUiAction.SetDraftProfile(if (original == 1) 2 else 1),
                    PlayerUiAction.SaveSettings,
                    PlayerUiAction.ConfirmPlaylistName,
                ).forEach { dispatch(controller, it) }
                assertTrue(controller.state.playlistMutationInProgress)
                assertTrue(controller.state.playlistNameRequest != null)
                assertEquals(name, controller.state.playlistNameDraft)
                assertEquals(original, store.activeProfile())
                assertTrue(store.listPlaylists().none { it.name == name })
            } finally {
                release.countDown()
            }
            await(controller) { !it.playlistMutationInProgress }
            assertEquals(1, store.listPlaylists().count { it.name == name })
            assertEquals(PlayerScreen.HIERARCHY, controller.state.screen)
        }

    @Test
    fun queuedCreateKeepsAcceptedProfileAndNeverNavigatesReplacementSession() =
        withFixture { controller, store, tracks ->
            val original = store.activeProfile()
            val target = if (original == 1) 2 else 1
            val name = "Frozen profile ${UUID.randomUUID()}"
            prepareName(controller, tracks, name)
            val release = blockWorker(controller, "workers")
            try {
                dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
                store.commitProfileSelection(
                    target,
                    store.loadSettingsForProfile(target, link.roadio.riff.player.core.settings.PlayerDataLocation.HU),
                )
                onMain {
                    field(controller, "activeProfileId", target)
                    field(controller, "state", controller.state.copy(screen = PlayerScreen.MAIN, playlistNameRequest = null))
                }
                release.countDown()
                await(controller) { !it.playlistMutationInProgress }
                assertTrue(store.listPlaylists(target).none { it.name == name })
                assertEquals(1, store.listPlaylists(original).count { it.name == name })
                assertEquals(PlayerScreen.MAIN, controller.state.screen)
            } finally {
                release.countDown()
                await(controller) { !it.playlistMutationInProgress }
                store.commitProfileSelection(
                    original,
                    store.loadSettingsForProfile(original, link.roadio.riff.player.core.settings.PlayerDataLocation.HU),
                )
            }
        }

    @Test
    fun duplicateFailureRetainsDraftAndRetryClearsInlineError() =
        withFixture { controller, store, tracks ->
            val name = "Duplicate ${UUID.randomUUID()}"
            store.createPlaylist(name, listOf(requireNotNull(tracks.first().mediaId)))
            prepareName(controller, tracks, name)
            val originalRoot = controller.state.root
            onMain { field(controller, "state", controller.state.copy(root = originalRoot.copy(children = emptyList()))) }
            dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
            await(controller) { it.playlistMutationError == "No playable tracks to add" }
            assertEquals(name, controller.state.playlistNameDraft)
            onMain { field(controller, "state", controller.state.copy(root = originalRoot)) }
            dispatch(controller, PlayerUiAction.UpdatePlaylistName(name))
            dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
            await(controller) { !it.playlistMutationInProgress && it.playlistMutationError != null }
            assertEquals(name, controller.state.playlistNameDraft)
            assertTrue(controller.state.playlistNameRequest != null)
            assertEquals("A playlist with this name already exists", controller.state.playlistMutationError)
            dispatch(controller, PlayerUiAction.UpdatePlaylistName("$name retry"))
            assertEquals(null, controller.state.playlistMutationError)
            dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
            await(controller) { !it.playlistMutationInProgress && it.playlistNameRequest == null }
            assertEquals(null, controller.state.playlistMutationError)
        }

    @Test
    fun settingsAndMoreSaveAvoidMainThreadDiskWrites() =
        withFixture { controller, _, _ ->
            dispatch(controller, PlayerUiAction.OpenMain)
            dispatch(controller, PlayerUiAction.OpenSettingsLongPress)
            dispatch(controller, PlayerUiAction.OpenMoreSettings)
            val violations = CopyOnWriteArrayList<String>()
            var original: StrictMode.ThreadPolicy? = null
            onMain {
                original = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy
                        .Builder()
                        .detectDiskWrites()
                        .penaltyListener(Executor { it.run() }) {
                            violations += it.stackTraceToString()
                        }.build(),
                )
            }
            try {
                dispatch(controller, PlayerUiAction.SaveMoreSettings)
                await(controller) { !it.settingsSaveInProgress && it.screen == PlayerScreen.SETTINGS }
                dispatch(controller, PlayerUiAction.SaveSettings)
                await(controller) { !it.settingsSaveInProgress && it.screen == PlayerScreen.MAIN }
                assertTrue(violations.joinToString("\n"), violations.isEmpty())
            } finally {
                onMain { StrictMode.setThreadPolicy(requireNotNull(original)) }
            }
        }

    @Test
    fun settingsSaveRechecksStorageBeforeQueuedCommitAndRetainsDraft() =
        withFixture { controller, store, _ ->
            dispatch(controller, PlayerUiAction.OpenMain)
            dispatch(controller, PlayerUiAction.OpenSettingsLongPress)
            val profile = store.activeProfile()
            val before = store.loadSettingsForProfile(profile, PlayerDataLocation.HU)
            dispatch(controller, PlayerUiAction.SetDraftIgnoreLeadingArticles(!before.ignoreLeadingArticles))
            val draft = controller.state.settingsDraft
            val release = blockWorker(controller, "profileWorkers")
            try {
                dispatch(controller, PlayerUiAction.SaveSettings)
                dispatch(controller, PlayerUiAction.CancelSettings)
                assertTrue(controller.state.settingsSaveInProgress)
                onMain { field(controller, "selectedStorageAvailable", false) }
            } finally {
                release.countDown()
            }
            await(controller) { !it.settingsSaveInProgress }
            assertEquals(draft, controller.state.settingsDraft)
            assertEquals(before, store.loadSettingsForProfile(profile, PlayerDataLocation.HU))
            assertEquals(profile, store.activeProfile())
            assertTrue(controller.state.status.contains("unavailable"))
            onMain { field(controller, "selectedStorageAvailable", true) }
        }

    @Test
    fun settingsSaveRechecksRestrictionsBeforeQueuedCommit() =
        withFixture { controller, store, _ ->
            dispatch(controller, PlayerUiAction.OpenMain)
            dispatch(controller, PlayerUiAction.OpenSettingsLongPress)
            val profile = store.activeProfile()
            val before = store.loadSettingsForProfile(profile, PlayerDataLocation.HU)
            dispatch(controller, PlayerUiAction.SetDraftIgnoreLeadingArticles(!before.ignoreLeadingArticles))
            val release = blockWorker(controller, "profileWorkers")
            try {
                dispatch(controller, PlayerUiAction.SaveSettings)
                onMain {
                    field(controller, "bookmarkMode", link.roadio.riff.player.core.settings.ApplicationMode.AUTOMOTIVE)
                    field(controller, "bookmarkMotion", link.roadio.riff.player.core.settings.MotionRestriction.RESTRICTED)
                }
            } finally {
                release.countDown()
            }
            await(controller) { !it.settingsSaveInProgress }
            assertEquals(before, store.loadSettingsForProfile(profile, PlayerDataLocation.HU))
            assertEquals(profile, store.activeProfile())
            assertTrue(controller.state.status.contains("unavailable"))
            onMain { field(controller, "bookmarkMode", link.roadio.riff.player.core.settings.ApplicationMode.PHONE) }
        }

    @Test
    fun audiobookDraftSwitchesDirectlyAndCommitsOnlyOnExit() =
        withFixture { controller, store, tracks ->
            val selected = tracks.first().copy(reactionKey = "track:1")
            val root = BrowserItem("root", "Fixture", BrowserKind.ROOT, children = listOf(selected))
            val original = store.loadAudiobookOverrides()[selected.reactionKey]
            val queue = store.loadActiveQueue()
            try {
                store.setAudiobookOverride(selected.reactionKey, null)
                onMain {
                    field(controller, "state", controller.state.copy(root = root, audiobookOverrides = emptyMap()))
                }
                dispatch(controller, PlayerUiAction.OpenRowActions(selected.id))
                dispatch(controller, PlayerUiAction.SetAudiobookChoice(selected.reactionKey, AudiobookChoice.MUSIC))
                dispatch(controller, PlayerUiAction.SetAudiobookChoice(selected.reactionKey, AudiobookChoice.BOOK))
                onMain { assertEquals(AudiobookChoice.BOOK, controller.state.audiobookChoiceDraft) }
                assertTrue(store.loadAudiobookOverrides().isEmpty())
                dispatch(controller, PlayerUiAction.SetAudiobookChoice(selected.reactionKey, AudiobookChoice.AUTO))
                dispatch(controller, PlayerUiAction.SetAudiobookChoice(selected.reactionKey, AudiobookChoice.BOOK))
                onMain { assertEquals(AudiobookChoice.BOOK, controller.state.audiobookChoiceDraft) }
                assertTrue(store.loadAudiobookOverrides().isEmpty())
                dispatch(controller, PlayerUiAction.CloseRowActions)
                await(controller) { !it.audiobookCommitInProgress && it.screen != PlayerScreen.ROW_ACTIONS }
                assertEquals(
                    link.roadio.riff.player.storage.StoredAudiobookOverride.BOOK,
                    store.loadAudiobookOverrides()[selected.reactionKey],
                )
                onMain {
                    assertEquals(AudiobookChoice.BOOK, controller.state.audiobookOverrides[selected.reactionKey])
                    field(controller, "state", controller.state.copy(root = root, screen = PlayerScreen.HIERARCHY))
                }
                dispatch(controller, PlayerUiAction.OpenRowActions(selected.id))
                dispatch(controller, PlayerUiAction.SetAudiobookChoice(selected.reactionKey, AudiobookChoice.AUTO))
                dispatch(controller, PlayerUiAction.CloseRowActions)
                await(controller) { !it.audiobookCommitInProgress && it.screen != PlayerScreen.ROW_ACTIONS }
                assertTrue(selected.reactionKey !in store.loadAudiobookOverrides())
                assertEquals(queue, store.loadActiveQueue())
            } finally {
                store.setAudiobookOverride(selected.reactionKey, original)
            }
        }

    @Test
    fun mainPlaylistsOpensCollectionListAfterCreatingFirstPlaylist() =
        withFixture { controller, store, tracks ->
            assertTrue(store.listPlaylists().isEmpty())
            val queue = store.loadActiveQueue()
            val name = "Created playlist"
            prepareName(controller, tracks, name)
            dispatch(controller, PlayerUiAction.ConfirmPlaylistName)
            await(controller) { !it.playlistMutationInProgress && it.screen == PlayerScreen.HIERARCHY }
            val playlist = store.listPlaylists().single()
            onMain {
                assertEquals(listOf(HierarchyRoot.PLAYLISTS.categoryId, playlistItemId(playlist.playlistId)), controller.state.browserPath)
            }
            dispatch(controller, PlayerUiAction.OpenMain)
            dispatch(controller, PlayerUiAction.OpenCategory(HierarchyRoot.PLAYLISTS.categoryId))
            onMain {
                assertEquals(PlayerScreen.HIERARCHY, controller.state.screen)
                assertEquals(listOf(HierarchyRoot.PLAYLISTS.categoryId), controller.state.browserPath)
                assertEquals(
                    listOf(name),
                    controller.state
                        .currentNode()
                        .children
                        .map { it.label },
                )
            }
            assertEquals(queue, store.loadActiveQueue())
        }

    private fun prepareName(
        controller: RiffPlayerUiController,
        tracks: List<BrowserItem>,
        name: String,
    ) {
        dispatch(controller, PlayerUiAction.OpenRowActions(tracks.first().id))
        dispatch(controller, PlayerUiAction.OpenPlaylistSelector(tracks.first().id))
        dispatch(controller, PlayerUiAction.RequestNewPlaylist)
        dispatch(controller, PlayerUiAction.UpdatePlaylistName(name))
    }

    private fun blockWorker(
        controller: RiffPlayerUiController,
        fieldName: String,
    ): CountDownLatch {
        val worker =
            RiffPlayerUiController::class.java
                .getDeclaredField(
                    fieldName,
                ).apply { isAccessible = true }
                .get(controller) as ExecutorService
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        worker.execute {
            started.countDown()
            check(release.await(20, TimeUnit.SECONDS))
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        return release
    }

    private fun withFixture(test: (RiffPlayerUiController, PlayerStateStore, List<BrowserItem>) -> Unit) {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".instrumented"))
        val storage = ApplicationDataStorage(context)
        check(storage.prepareForStartup())
        val config = UiConfig.load(context)
        assertEquals("phone", storage.cachedApplicationMode())
        val store =
            requireNotNull(
                PlayerProcessStore.open(
                    context,
                    config.long("player.recentHistoryGenerations"),
                    config.long("player.bookmarkMinSpacingSeconds"),
                ),
            )
        val originalIds = store.listPlaylists().map { it.playlistId }.toSet()
        val tracks =
            (1L..60L).map { id ->
                BrowserItem("fixture:$id", "Fixture $id", BrowserKind.TRACK, playable = true, mediaId = MediaId(id))
            }
        val root =
            BrowserItem(
                "root",
                "Fixture",
                BrowserKind.ROOT,
                children =
                    listOf(
                        BrowserItem(HierarchyRoot.FOLDERS.categoryId, "Folders", BrowserKind.CATEGORY, children = tracks),
                        BrowserItem(HierarchyRoot.PLAYLISTS.categoryId, "Playlists", BrowserKind.CATEGORY),
                    ),
            )
        var controller: RiffPlayerUiController? = null
        try {
            onMain { controller = RiffPlayerUiController(context) }
            val current = requireNotNull(controller)
            assertTrue(current.state.playlistMutationsAllowed)
            onMain {
                field(current, "tracks", tracks)
                field(current, "retainedTracks", tracks)
                field(current, "libraryRoot", root)
                field(
                    current,
                    "aiFeaturesByMediaId",
                    tracks.associate {
                        requireNotNull(it.mediaId) to
                            AiFeaturePoint(requireNotNull(it.mediaId), 0.4f, -0.3f)
                    },
                )
                field(
                    current,
                    "state",
                    current.state.copy(
                        root = root,
                        screen = PlayerScreen.AI_ACTIONS,
                        contentMode = ContentMode.MUSIC,
                        aiSelection = AiSelectionUiState(size = 10, arousal = 0.4f, valence = -0.3f),
                        aiDataAvailable = true,
                        aiHeatmap = AiHeatmap.empty(1, 1),
                        playlistMutationInProgress = false,
                    ),
                )
            }
            assertTrue(current.state.playlistMutationsAllowed)
            test(current, store, tracks)
        } finally {
            onMain { controller?.close() }
            store.listPlaylists().filter { it.playlistId !in originalIds }.forEach { store.deletePlaylist(it.playlistId) }
        }
    }

    private fun dispatch(
        controller: RiffPlayerUiController,
        action: PlayerUiAction,
    ) = onMain { controller.dispatch(action) }

    private fun await(
        controller: RiffPlayerUiController,
        ready: (PlayerUiState) -> Boolean,
    ) {
        val deadline = System.nanoTime() + 10_000_000_000L
        var state = controller.state
        while (System.nanoTime() < deadline) {
            onMain { state = controller.state }
            if (ready(state)) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out: ${state.screen}, ${state.status}, busy=${state.playlistMutationInProgress}")
    }

    private fun frozenIds(controller: RiffPlayerUiController): List<MediaId> {
        val field = RiffPlayerUiController::class.java.getDeclaredField("pendingAiQueue").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val candidates = field.get(controller) as? List<AiQueueCandidate>
        return candidates.orEmpty().map { it.mediaId }
    }

    private fun field(
        controller: RiffPlayerUiController,
        name: String,
        value: Any,
    ) {
        RiffPlayerUiController::class.java
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .set(controller, value)
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
}
