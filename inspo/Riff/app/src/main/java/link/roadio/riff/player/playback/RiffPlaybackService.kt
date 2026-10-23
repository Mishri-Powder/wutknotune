@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.playback.StockPlayback
import link.roadio.riff.player.artwork.ArtworkKey
import link.roadio.riff.player.artwork.PlayerArtworkLoader
import link.roadio.riff.player.artwork.PlayerArtworkResolver
import link.roadio.riff.player.core.playback.AudiobookAudioParameters
import link.roadio.riff.player.core.playback.AudiobookSpeed
import link.roadio.riff.player.core.settings.PlayerReplayGainMode
import link.roadio.riff.player.core.settings.PlayerReplayGainTarget
import link.roadio.riff.player.library.PlayerCuetreeClient
import link.roadio.riff.player.library.PlayerLibraryModel
import link.roadio.riff.player.library.PlayerLibraryProjection
import link.roadio.riff.player.storage.ListeningEventType
import link.roadio.riff.player.storage.PlaybackCheckpoint
import link.roadio.riff.player.storage.PlaybackQueueOccurrence
import link.roadio.riff.player.storage.PlaybackQueueSnapshot
import link.roadio.riff.player.storage.PlayerProcessStore
import link.roadio.riff.player.storage.PlayerSafGrantStore
import link.roadio.riff.player.storage.StoredContentMode
import link.roadio.riff.storage.root.ApplicationDataStorage
import link.roadio.riff.ui.ArtworkUiState
import link.roadio.riff.ui.PlaybackSourceRef
import link.roadio.riff.ui.UiConfig
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Player-owned Media3 execution boundary over one durable READY Riff queue.
 *
 * Player SQLite owns queue membership, order, selected occurrence, and history. Media3 receives a derived,
 * rebuildable execution plan and exposes transport only; it must never become a competing queue database.
 * State writes and provider/source resolution use separate serial executors so slow SAF I/O cannot reorder
 * listening events.
 */
class RiffPlaybackService : MediaSessionService() {
    private val main = Handler(Looper.getMainLooper())
    private val stateExecutor = Executors.newSingleThreadExecutor()
    private val queueExecutor = Executors.newSingleThreadExecutor()
    private var queuePreparation: Future<*>? = null
    private var queuePreparationToken = 0L
    private val sourceExecutor = Executors.newSingleThreadExecutor()
    private val executionPlan = AtomicReference<QueueExecutionPlan?>()
    private val resolvedBindings = ConcurrentHashMap<Long, ResolvedBinding>()
    private var library: PlayerLibraryModel? = null
    private var reloadPending = true
    private var lastItem: PlaybackIdentity? = null
    private var lastKnownPositionMs = 0L
    private var lastKnownDurationMs: Long? = null
    private var historyRecoveryScheduled = false
    private var musicRewindPreviousThresholdMs = 0L
    private var audiobookRewindPreviousThresholdMs = 0L
    private var markerCheckpoint: MarkerCheckpoint? = null
    private val replayGainStreamGains = ReplayGainStreamGains()
    private var replayGainMode = PlayerReplayGainMode.F
    private var replayGainTarget = PlayerReplayGainTarget.LUFS_23

    private var audiobookSpeed = AudiobookSpeed.NORMAL
    private var audiobookProjection: PlayerLibraryProjection? = null
    private var audiobookParametersReady = false
    private var audiobookParametersToken = 0L

    private val checkpointCadence = CheckpointCadence(SystemClock.elapsedRealtime())
    private var installingQueue = false
    private var completedQueueRevisionId: Long? = null

    @Volatile
    private var checkpointFailure = false
    private val pendingCheckpoint = AtomicReference<CheckpointWrite?>()
    private val checkpointDrainScheduled = AtomicBoolean()
    private val serviceDestroyed = AtomicBoolean()

    // History fields are touched only by stateExecutor, preserving callback order.
    private var historyQueueRevisionId: Long? = null
    private var historyOccurrenceId: Long? = null
    private var historyMediaId: MediaId? = null
    private var historyPlayInstanceId: Long? = null

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var audioFocus: NavigationAudioFocus
    private lateinit var transportPlayer: Player
    private lateinit var mediaSession: MediaSession
    private lateinit var libraryClient: PlayerCuetreeClient
    private lateinit var mediaUriResolver: PlayerMediaUriResolver
    private lateinit var artworkLoader: PlayerArtworkLoader

    @Volatile private var artworkGeneration = 0L
    private lateinit var playbackMediaSourceFactory: MediaSource.Factory
    private var startupPlayer: ExoPlayer? = null
    private val playerEvents = PlayerEvents()
    private val sessionCallback =
        object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>,
            ): ListenableFuture<List<MediaItem>> =
                Futures.immediateFailedFuture(UnsupportedOperationException("Riff owns durable queue mutations"))

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>,
                startIndex: Int,
                startPositionMs: Long,
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
                Futures.immediateFailedFuture(UnsupportedOperationException("Riff owns durable queue mutations"))

            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                // Riff SQLite owns queue membership/order; Media3 exposes transport only.
                val transportCommands =
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                        .buildUpon()
                        .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                        .remove(Player.COMMAND_SET_MEDIA_ITEM)
                        .remove(Player.COMMAND_SET_SPEED_AND_PITCH)
                        .build()
                return MediaSession.ConnectionResult
                    .AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(transportCommands)
                    .build()
            }
        }

    private val checkpointTicker =
        object : Runnable {
            override fun run() {
                if (::exoPlayer.isInitialized) {
                    checkpointAtNaturalMarker()
                    if (checkpointCadence.due(SystemClock.elapsedRealtime(), exoPlayer.isPlaying)) {
                        persistCheckpoint(immediate = true)
                    }
                }
                main.postDelayed(this, MARKER_POLL_INTERVAL_MS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        // Host/media-button starts obey the launcher's gate before constructing Media3 or mapping data.
        if (!runCatching { ApplicationDataStorage(this).prepareForStartup() }.getOrDefault(false)) {
            Log.i(TAG, "startup_waiting_for_selected_storage")
            stopSelf()
            return
        }
        val loaded = UiConfig.currentOrNull()
        if (loaded != null) {
            initializePlayback(loaded)
            return
        }
        // Headless media-button startup exposes a session without parsing on Media3's thread.
        // This empty player cannot open media or a store while the worker reads configuration.
        val defaults = UiConfig.defaults(this)
        val pending =
            ExoPlayer
                .Builder(this)
                .setMaxSeekToPreviousPositionMs(
                    maxOf(
                        defaults.long("player.rewindPreviousThresholdSeconds.music"),
                        defaults.long("player.rewindPreviousThresholdSeconds.audiobook"),
                    ) * 1_000L,
                ).build()
        startupPlayer = pending
        mediaSession = MediaSession.Builder(this, pending).setCallback(sessionCallback).build()
        stateExecutor.execute {
            val config = runCatching { UiConfig.load(this) }
            main.post {
                if (serviceDestroyed.get()) return@post
                config.fold(
                    onSuccess = { snapshot ->
                        val requestedPlay = pending.playWhenReady
                        initializePlayback(snapshot)
                        if (::exoPlayer.isInitialized) audioFocus.setPlayWhenReady(requestedPlay)
                        pending.release()
                        startupPlayer = null
                    },
                    onFailure = {
                        Log.e(TAG, "startup_configuration_unavailable type=" + it.javaClass.simpleName)
                        stopSelf()
                    },
                )
            }
        }
    }

    private fun initializePlayback(config: UiConfig) {
        if (!ApplicationDataStorage(this).selectedRootAvailable()) {
            stopSelf()
            return
        }
        val installationSettings = getSharedPreferences(INSTALLATION_SETTINGS, MODE_PRIVATE)
        replayGainMode =
            runCatching {
                PlayerReplayGainMode.valueOf(
                    installationSettings.getString(REPLAY_GAIN_MODE, null) ?: PlayerReplayGainMode.F.name,
                )
            }.getOrDefault(PlayerReplayGainMode.F)
        replayGainTarget =
            runCatching {
                PlayerReplayGainTarget.valueOf(
                    installationSettings.getString(REPLAY_GAIN_TARGET, null) ?: PlayerReplayGainTarget.LUFS_23.name,
                )
            }.getOrDefault(PlayerReplayGainTarget.LUFS_23)
        replayGainStreamGains.setTarget(replayGainTarget)
        val recentGenerations = config.long("player.recentHistoryGenerations")
        musicRewindPreviousThresholdMs =
            config.long("player.rewindPreviousThresholdSeconds.music") * 1_000L
        audiobookRewindPreviousThresholdMs =
            config.long("player.rewindPreviousThresholdSeconds.audiobook") * 1_000L
        libraryClient =
            PlayerCuetreeClient(
                this,
                onLibrary = { model ->
                    main.post {
                        if (serviceDestroyed.get()) return@post
                        library = model
                        if (artworkGeneration != model.generation) {
                            artworkGeneration = model.generation
                            artworkLoader.invalidate(model.generation)
                            currentIdentity()?.let { identity ->
                                selectArtwork(identity.queueRevisionId, identity.occurrenceId)
                            }
                        }
                        replayGainStreamGains.replace(model.replayGainByMediaId)
                        if (queuePreparation != null) reloadPending = true
                        reloadIfReady()
                        audioFocus.refreshContentType()
                        refreshAudiobookParameters()
                    }
                },
                onUnavailable = { message -> Log.w(TAG, "library_unavailable reason=${message.take(120)}") },
                onIndexerStatus = {},
                excludedGenreIds = config.excludedGenreIds(),
                projection = {
                    val store = PlayerProcessStore.current()
                    PlayerLibraryProjection(
                        contentMode = store?.loadContentMode() ?: StoredContentMode.MUSIC,
                        audiobookOverrides = store?.loadAudiobookOverrides().orEmpty().toMap(),
                    )
                },
            )
        mediaUriResolver = PlayerMediaUriResolver(contentResolver, libraryClient::resolveGrantTreeUri)
        val artworkResolver = PlayerArtworkResolver(contentResolver, libraryClient::resolveGrantTreeUri)
        artworkLoader = PlayerArtworkLoader(artworkResolver::resolveResult)

        // Per-occurrence source preparation runs on sourceExecutor before ContentDataSource opens bytes.
        val resolvingFactory =
            DataSource.Factory {
                BindingFallbackDataSource(DefaultDataSource.Factory(this), ::resolveDataSpec)
            }
        playbackMediaSourceFactory = StockPlayback.sources(resolvingFactory)
        exoPlayer = createExoPlayer(replayGainMode)
        audioFocus = createAudioFocus(exoPlayer)
        exoPlayer.addListener(playerEvents)
        transportPlayer = createTransportPlayer(exoPlayer)
        if (::mediaSession.isInitialized) {
            mediaSession.setPlayer(transportPlayer)
        } else {
            mediaSession =
                MediaSession
                    .Builder(this, transportPlayer)
                    .setCallback(sessionCallback)
                    .build()
        }
        PlayerPlaybackBridge.attach(
            newOwner = this,
            reloadAction = ::requestReload,
            refreshLibraryAction = {
                libraryClient.start()
                libraryClient.requestReload()
                refreshAudiobookParameters()
            },
            previousAction = ::dispatchPrevious,
            nextAction = ::dispatchNext,
            checkpointAction = { persistCheckpoint() },
            flushAction = { complete ->
                persistCheckpoint(immediate = true)
                stateExecutor.execute { complete(!checkpointFailure) }
            },
            quiesceAction = ::quiesceForStorageChange,
            replayGainSettingsAction = ::changeReplayGainSettings,
            audiobookParametersRefreshAction = ::refreshAudiobookParameters,
            artworkLookupAction = { queue, occurrence, binding ->
                if (queue == null || occurrence == null || binding == null) {
                    null
                } else {
                    artworkLoader.artworkFor(ArtworkKey(queue, occurrence, binding, artworkGeneration)) ?: ArtworkUiState(binding)
                }
            },
        )
        main.post(checkpointTicker)
        stateExecutor.execute {
            val store = PlayerProcessStore.open(this, recentGenerations, config.long("player.bookmarkMinSpacingSeconds"))
            main.post {
                if (serviceDestroyed.get()) return@post
                val hasPersistedReadGrant =
                    runCatching { PlayerSafGrantStore(this).hasPersistedReadGrant() }.getOrNull()
                if (hasPersistedReadGrant != false) libraryClient.start()
                refreshAudiobookParameters()
                reloadIfReady()
                Log.i(TAG, "player_state_ready available=${store != null}")
            }
        }
        Log.i(
            TAG,
            "media3_service_created version=${androidx.media3.common.MediaLibraryInfo.VERSION} replay_gain=${replayGainMode.name} " +
                "target=${replayGainTarget.displayValue} state=initializing",
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (::mediaSession.isInitialized) mediaSession else null

    override fun onDestroy() {
        serviceDestroyed.set(true)
        main.removeCallbacks(checkpointTicker)
        persistCheckpoint(immediate = true)
        endCurrentAttemptAsCensored()
        PlayerPlaybackBridge.detach(this)
        if (::libraryClient.isInitialized) libraryClient.close()
        if (::artworkLoader.isInitialized) artworkLoader.close()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::audioFocus.isInitialized) audioFocus.close()
        if (::exoPlayer.isInitialized) exoPlayer.release()
        startupPlayer?.release()
        startupPlayer = null
        queuePreparation?.cancel(true)
        queueExecutor.shutdownNow()
        sourceExecutor.shutdownNow()
        stateExecutor.shutdown()
        runCatching { stateExecutor.awaitTermination(2, TimeUnit.SECONDS) }
        super.onDestroy()
    }

    private fun requestReload() {
        queuePreparationToken += 1L
        queuePreparation?.cancel(true)
        queuePreparation = null
        reloadPending = true
        reloadIfReady()
    }

    private fun refreshAudiobookParameters() {
        audiobookParametersReady = false
        val token = ++audiobookParametersToken
        stateExecutor.execute {
            val store = PlayerProcessStore.current() ?: return@execute
            val captured =
                runCatching {
                    val profile = store.activeProfile()
                    PlayerLibraryProjection(
                        store.loadContentMode(profile),
                        store.loadAudiobookOverrides(profile).toMap(),
                    ) to store.loadAudiobookSpeed(profile)
                }
            main.post {
                if (serviceDestroyed.get() || token != audiobookParametersToken || PlayerProcessStore.current() !== store) return@post
                captured
                    .onSuccess { (projection, speed) ->
                        audiobookProjection = projection
                        audiobookParametersReady = true
                        audiobookSpeed = speed
                        // AI-INTENT: A mode/profile switch must select the cached projection even when
                        // the catalog cohort is unchanged. https://wiki.271.pics/Riff/LATEST/player-audiobook-speed
                        if (library?.projection != projection && !libraryClient.activateProjection(projection)) {
                            libraryClient.requestReload()
                        }
                        applyAudiobookParameters()
                        reloadIfReady()
                    }.onFailure { Log.w(TAG, "audiobook_parameters_unavailable", it) }
            }
        }
    }

    private fun applyAudiobookParameters() {
        if (!::exoPlayer.isInitialized) return
        val parameters =
            AudiobookAudioParameters.resolve(
                audiobookProjection?.contentMode == StoredContentMode.AUDIOBOOK,
                library?.projection == audiobookProjection &&
                    exoPlayer.currentMediaItem?.identity()?.mediaId in library?.effectiveBookMediaIds.orEmpty(),
                audiobookSpeed,
            )
        exoPlayer.applyAudiobookAudioParameters(parameters)
    }

    private fun createExoPlayer(mode: PlayerReplayGainMode): ExoPlayer {
        val renderersFactory =
            if (mode == PlayerReplayGainMode.N) {
                StockPlayback.renderers(this)
            } else {
                ReplayGainRenderersFactory(this, mode, replayGainStreamGains)
            }
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        return ExoPlayer
            .Builder(this, renderersFactory)
            .setMaxSeekToPreviousPositionMs(maxOf(musicRewindPreviousThresholdMs, audiobookRewindPreviousThresholdMs))
            .setMediaSourceFactory(playbackMediaSourceFactory)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private fun createAudioFocus(player: ExoPlayer): NavigationAudioFocus =
        NavigationAudioFocus(this, player, main) {
            player.currentMediaItem?.identity()?.mediaId in library?.effectiveBookMediaIds.orEmpty()
        }

    private fun createTransportPlayer(player: ExoPlayer): Player =
        object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands =
                super
                    .getAvailableCommands()
                    .buildUpon()
                    .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                    .remove(Player.COMMAND_SET_MEDIA_ITEM)
                    .remove(Player.COMMAND_SET_SPEED_AND_PITCH)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()

            override fun play() = audioFocus.setPlayWhenReady(true)

            override fun pause() {
                audioFocus.cancelResume()
                super.pause()
                persistCheckpoint(immediate = true)
            }

            override fun stop() {
                endCurrentAttemptAsCensored()
                audioFocus.cancelResume()
                super.stop()
                persistCheckpoint(immediate = true)
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                audioFocus.setPlayWhenReady(playWhenReady)
                persistCheckpoint(immediate = true)
            }

            override fun seekToPrevious() = dispatchPrevious()

            override fun seekToPreviousMediaItem() = dispatchPrevious()

            override fun seekToNext() = dispatchNext()

            override fun seekToNextMediaItem() = dispatchNext()
        }

    private fun changeReplayGainSettings(
        nextMode: PlayerReplayGainMode,
        nextTarget: PlayerReplayGainTarget,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (nextMode == replayGainMode) {
            if (nextTarget == replayGainTarget) return
            val previousTarget = replayGainTarget
            replayGainTarget = nextTarget
            replayGainStreamGains.setTarget(nextTarget)
            Log.i(
                TAG,
                "replay_gain_target_changed mode=${nextMode.name} " +
                    "from=${previousTarget.displayValue} to=${nextTarget.displayValue} ramp_ms=75",
            )
            return
        }

        val previousPlayer = exoPlayer
        val previousMode = replayGainMode
        val previousTarget = replayGainTarget
        val nextPlayer =
            runCatching { createExoPlayer(nextMode) }
                .getOrElse { error ->
                    Log.e(TAG, "replay_gain_path_create_failed mode=${nextMode.name}", error)
                    return
                }

        // Constructing the replacement can take time while the old player is still advancing.
        // Snapshot only after it is ready so the restored position is the handoff position.
        val requestedPlay = audioFocus.playRequested
        val items = List(previousPlayer.mediaItemCount, previousPlayer::getMediaItemAt)
        val currentIndex = previousPlayer.currentMediaItemIndex.coerceAtLeast(0)
        val currentPositionMs = previousPlayer.currentPosition.coerceAtLeast(0L)
        val repeatMode = previousPlayer.repeatMode
        val shuffleEnabled = previousPlayer.shuffleModeEnabled
        previousPlayer.pause()
        previousPlayer.removeListener(playerEvents)
        audioFocus.close()
        replayGainStreamGains.clearOffsets()
        replayGainStreamGains.setTarget(nextTarget)
        try {
            replayGainMode = nextMode
            replayGainTarget = nextTarget
            exoPlayer = nextPlayer
            audioFocus = createAudioFocus(nextPlayer)
            nextPlayer.addListener(playerEvents)
            transportPlayer = createTransportPlayer(nextPlayer)

            if (items.isNotEmpty()) {
                nextPlayer.setMediaItems(items, currentIndex.coerceAtMost(items.lastIndex), currentPositionMs)
                shuffleOrderForCurrentPlan(items.size)?.let(nextPlayer::setShuffleOrder)
                nextPlayer.repeatMode = repeatMode
                nextPlayer.shuffleModeEnabled = shuffleEnabled
                applyAudiobookParameters()
                nextPlayer.prepare()
            }
            mediaSession.setPlayer(transportPlayer)
            audioFocus.setPlayWhenReady(requestedPlay)
            previousPlayer.release()
            Log.i(
                TAG,
                "replay_gain_path_changed from=${previousMode.name} to=${nextMode.name} " +
                    "target=${nextTarget.displayValue} item=$currentIndex position_ms=$currentPositionMs " +
                    "play_requested=$requestedPlay",
            )
        } catch (error: Throwable) {
            runCatching { audioFocus.close() }
            runCatching { nextPlayer.removeListener(playerEvents) }
            runCatching { nextPlayer.release() }
            replayGainMode = previousMode
            replayGainTarget = previousTarget
            replayGainStreamGains.setTarget(previousTarget)
            exoPlayer = previousPlayer
            audioFocus = createAudioFocus(previousPlayer)
            previousPlayer.addListener(playerEvents)
            transportPlayer = createTransportPlayer(previousPlayer)
            mediaSession.setPlayer(transportPlayer)
            audioFocus.setPlayWhenReady(requestedPlay)
            Log.e(
                TAG,
                "replay_gain_path_change_failed mode=${nextMode.name} target=${nextTarget.displayValue}",
                error,
            )
        }
    }

    private fun shuffleOrderForCurrentPlan(itemCount: Int): ShuffleOrder? {
        val queue = executionPlan.get()?.queue ?: return null
        val occurrences = queue.occurrences.sortedBy(PlaybackQueueOccurrence::ordinal)
        if (occurrences.size != itemCount) return null
        val order = occurrences.indices.sortedBy { occurrences[it].shuffleOrdinal }.toIntArray()
        return ShuffleOrder.DefaultShuffleOrder(order, queue.revisionId)
    }

    private fun quiesceForStorageChange(complete: () -> Unit) {
        queuePreparationToken += 1L
        queuePreparation?.cancel(true)
        queuePreparation = null
        reloadPending = false
        exoPlayer.pause()
        persistCheckpoint(immediate = true)
        stateExecutor.execute {
            main.post {
                exoPlayer.stop()
                complete()
            }
        }
    }

    private fun reloadIfReady() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!reloadPending || !audiobookParametersReady) return
        val model = library ?: return
        val store = PlayerProcessStore.current() ?: return
        reloadPending = false
        val token = ++queuePreparationToken
        queuePreparation?.cancel(true)
        // AI-INTENT: Full queue traversal and metadata construction never run on main.
        // https://wiki.271.pics/Riff/LATEST/runtime-architecture
        queuePreparation =
            queueExecutor.submit {
                val prepared = runCatching { store.loadActiveQueue()?.let { prepareQueue(it, model) } }
                main.post {
                    if (serviceDestroyed.get() || token != queuePreparationToken) return@post
                    queuePreparation = null
                    if (!audiobookParametersReady || PlayerProcessStore.current() !== store || library !== model) {
                        reloadPending = true
                        reloadIfReady()
                        return@post
                    }
                    prepared
                        .onSuccess { ready ->
                            if (ready == null) {
                                endCurrentAttemptAsCensored()
                                exoPlayer.clearMediaItems()
                                executionPlan.set(null)
                                replayGainStreamGains.setQueue(emptyList())
                            } else {
                                installQueue(ready)
                            }
                        }.onFailure { error -> Log.w(TAG, "queue_preparation_failed", error) }
                }
            }
    }

    private fun installQueue(prepared: PreparedQueue) {
        val (plan, occurrences, items, startIndex, shuffleOrder) = prepared
        val queue = plan.queue
        installingQueue = true
        try {
            completedQueueRevisionId = null
            checkpointCadence.cancel()
            recoverDanglingAttempts(queue, occurrences)
            endCurrentAttemptAsCensored()
            executionPlan.set(plan)
            artworkLoader.invalidate(artworkGeneration, queue.revisionId)
            artworkLoader.select(queue.revisionId, queue.checkpoint.occurrenceId)
            resolvedBindings.clear()
            replayGainStreamGains.setQueue(occurrences.map(PlaybackQueueOccurrence::mediaId))
            audioFocus.setPlayWhenReady(false)
            exoPlayer.setMediaItems(items, startIndex, queue.checkpoint.positionMs)
            exoPlayer.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(shuffleOrder, queue.revisionId))
            exoPlayer.repeatMode = if (queue.checkpoint.repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            exoPlayer.shuffleModeEnabled = queue.checkpoint.shuffle
            applyAudiobookParameters()
            exoPlayer.prepare()
            audioFocus.setPlayWhenReady(queue.checkpoint.playWhenReady)
        } finally {
            installingQueue = false
        }
        checkpointCadence.saved(SystemClock.elapsedRealtime())
        Log.i(
            TAG,
            "queue_projected revision=${queue.revisionId} occurrences=${occurrences.size} start_occurrence=${queue.checkpoint.occurrenceId}",
        )
    }

    private fun artworkKey(binding: ResolvedBinding): ArtworkKey =
        ArtworkKey(binding.queueRevisionId, binding.occurrenceId, binding.source.bindingId, artworkGeneration)

    private fun resolveDataSpec(
        dataSpec: DataSpec,
        rejected: Set<Long>,
    ): BindingDataSpec {
        if (dataSpec.uri.scheme != PlaybackMetadata.URI_SCHEME) return BindingDataSpec(dataSpec)
        val revisionId = dataSpec.uri.authority?.toLongOrNull() ?: throw IOException("Invalid Riff queue URI")
        val occurrenceId = dataSpec.uri.lastPathSegment?.toLongOrNull() ?: throw IOException("Invalid Riff occurrence URI")
        if (executionPlan.get()?.queue?.revisionId != revisionId) throw IOException("Obsolete queue")
        val cached =
            resolvedBindings[occurrenceId]?.takeIf {
                it.queueRevisionId == revisionId && it.source.bindingId !in rejected
            }
        val resolved = cached ?: resolveBindingWithTimeout(occurrenceId, rejected)
        if (resolved.queueRevisionId != revisionId || executionPlan.get()?.queue?.revisionId != revisionId) {
            throw IOException("Obsolete binding")
        }
        return BindingDataSpec(dataSpec.withUri(resolved.uri), resolved.source.bindingId) {
            if (executionPlan.get()?.queue?.revisionId != revisionId) throw IOException("Obsolete queue after source open")
            resolvedBindings[occurrenceId] = resolved
            // Artwork observes the successfully opened binding, without gating audio readiness.
            artworkLoader.request(artworkKey(resolved), resolved.source)
            main.post { bindingResolved(resolved) }
            Log.i(TAG, "source_ready occurrence=$occurrenceId binding=${resolved.source.bindingId} fallback=${rejected.size}")
        }
    }

    private fun selectArtwork(
        queueRevisionId: Long?,
        occurrenceId: Long?,
    ) {
        artworkLoader.select(queueRevisionId, occurrenceId)
        if (queueRevisionId == null || occurrenceId == null) return
        resolvedBindings[occurrenceId]?.takeIf { it.queueRevisionId == queueRevisionId }?.let { resolved ->
            artworkLoader.request(artworkKey(resolved), resolved.source)
        }
    }

    private fun resolveBindingWithTimeout(
        occurrenceId: Long,
        rejected: Set<Long>,
    ): ResolvedBinding {
        val cancellation = CancellationSignal()
        val future = sourceExecutor.submit<ResolvedBinding> { resolveBinding(occurrenceId, rejected, cancellation) }
        return try {
            future.get(SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: Exception) {
            cancellation.cancel()
            future.cancel(true)
            throw IOException("Playback source preparation failed", error)
        }
    }

    private fun resolveBinding(
        occurrenceId: Long,
        rejected: Set<Long>,
        cancellation: CancellationSignal,
    ): ResolvedBinding {
        val plan = executionPlan.get() ?: throw IOException("Playback queue is unavailable")
        val occurrence = plan.occurrences[occurrenceId] ?: throw IOException("Playback occurrence is unavailable")
        occurrence.sources.filter { it.bindingId !in rejected }.forEach { source ->
            cancellation.throwIfCanceled()
            val uri = runCatching { mediaUriResolver.resolve(source, cancellation) }.getOrNull()
            if (uri != null) return ResolvedBinding(occurrence.queueRevisionId, occurrenceId, occurrence.occurrence.mediaId, source, uri)
        }
        throw IOException("No eligible binding for occurrence ${occurrenceId.toULong().toString(16)}")
    }

    private fun bindingResolved(resolved: ResolvedBinding) {
        if (serviceDestroyed.get()) return
        val plan = executionPlan.get() ?: return
        if (plan.queue.revisionId != resolved.queueRevisionId || resolvedBindings[resolved.occurrenceId] !== resolved) return
        val planned = plan.occurrences[resolved.occurrenceId] ?: return
        val index = planned.occurrence.ordinal
        if (index !in 0 until exoPlayer.mediaItemCount) return
        val current = exoPlayer.getMediaItemAt(index)
        if (current.identity()?.occurrenceId != resolved.occurrenceId) return
        if (current.mediaMetadata.extras?.getLong(PlaybackMetadata.EXTRA_BINDING_ID) == resolved.source.bindingId) return
        val metadata =
            current.mediaMetadata
                .buildUpon()
                .setExtras(metadataExtras(planned, resolved.source.bindingId))
                .build()
        exoPlayer.replaceMediaItem(index, current.buildUpon().setMediaMetadata(metadata).build())
        if (resolved.occurrenceId == currentIdentity()?.occurrenceId) persistCheckpoint()
        Log.i(
            TAG,
            "binding_ready occurrence=${resolved.occurrenceId.toULong().toString(
                16,
            )} binding=${resolved.source.bindingId.toULong().toString(16)}",
        )
    }

    private fun dispatchNext() {
        Log.i(TAG, "transport_next occurrence=${currentIdentity()?.occurrenceId?.toULong()?.toString(16)}")
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        TocTransportPolicy.nextTarget(currentTocMarkers(), position)?.let { marker ->
            exoPlayer.seekTo(marker)
            persistCheckpoint()
            return
        }
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        } else {
            recordHistoryEnd(ListeningEventType.USER_SKIPPED, currentIdentity(), position, currentDurationMs())
            exoPlayer.pause()
            exoPlayer.duration.takeIf { it >= 0L && it != C.TIME_UNSET }?.let(exoPlayer::seekTo)
            completeContext()
        }
    }

    private fun dispatchPrevious() {
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val markers = currentTocMarkers()
        val threshold = currentRewindPreviousThresholdMs()
        val target = TocTransportPolicy.previousTarget(markers, position, threshold)
        if (target != null) {
            exoPlayer.seekTo(target)
            persistCheckpoint()
            return
        }
        if (markers.isEmpty() && position > threshold) {
            exoPlayer.seekTo(0L)
            persistCheckpoint()
            return
        }

        val priorOccurrenceId = currentIdentity()?.occurrenceId
        exoPlayer.seekToPreviousMediaItem()
        if (currentIdentity()?.occurrenceId == priorOccurrenceId) {
            exoPlayer.seekTo(0L)
        } else {
            exoPlayer.seekTo(TocTransportPolicy.previousItemEntryTarget(currentTocMarkers()))
        }
        persistCheckpoint()
    }

    private fun currentRewindPreviousThresholdMs(): Long =
        if (currentIdentity()?.mediaId in library?.effectiveBookMediaIds.orEmpty()) {
            audiobookRewindPreviousThresholdMs
        } else {
            musicRewindPreviousThresholdMs
        }

    private fun currentTocMarkers(): List<Long> {
        val identity = currentIdentity() ?: return emptyList()
        val plan = executionPlan.get() ?: return emptyList()
        val planned = plan.occurrences[identity.occurrenceId] ?: return emptyList()
        val bindingId =
            resolvedBindings[identity.occurrenceId]?.source?.bindingId
                ?: planned.sources.firstOrNull()?.bindingId
                ?: return emptyList()
        val markers =
            library
                ?.tocMarkersByBindingId
                ?.get(bindingId)
                .orEmpty()
        val duration = exoPlayer.duration.takeIf { it >= 0L && it != C.TIME_UNSET }
        return markers
            .filter { marker -> duration == null || marker < duration }
            .takeIf { it.size > 1 }
            .orEmpty()
    }

    private fun currentIdentity(): PlaybackIdentity? = exoPlayer.currentMediaItem?.identity()

    private fun persistCheckpoint(immediate: Boolean = false) {
        if (!::exoPlayer.isInitialized || installingQueue) return
        val plan = executionPlan.get() ?: return
        val completed = completedQueueRevisionId == plan.queue.revisionId
        val restarting = completed && exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED
        if (completed && !restarting) return
        if (restarting) completedQueueRevisionId = null
        if (!immediate && !restarting) {
            checkpointCadence.manipulated()
            return
        }
        val identity = currentIdentity() ?: return
        if (identity.queueRevisionId != plan.queue.revisionId) return
        val profileId = PlayerProcessStore.current()?.activeProfile() ?: return
        val audiobook = identity.mediaId in library?.effectiveBookMediaIds.orEmpty()
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val checkpoint =
            PlaybackCheckpoint(
                occurrenceId = identity.occurrenceId,
                positionMs = position,
                playWhenReady = audioFocus.playRequested,
                repeat = exoPlayer.repeatMode == Player.REPEAT_MODE_ALL,
                shuffle = exoPlayer.shuffleModeEnabled,
                chosenBindingId =
                    identity
                        .occurrenceId
                        .let(resolvedBindings::get)
                        ?.source
                        ?.bindingId,
            )
        pendingCheckpoint.set(CheckpointWrite(plan.queue.revisionId, checkpoint, profileId, audiobook, restarting))
        checkpointCadence.submitted()
        scheduleCheckpointDrain()
    }

    private fun completeContext() {
        if (installingQueue) return
        val queueId = executionPlan.get()?.queue?.revisionId ?: return
        if (completedQueueRevisionId == queueId) return
        val profileId = PlayerProcessStore.current()?.activeProfile() ?: return
        completedQueueRevisionId = queueId
        checkpointCadence.cancel()
        pendingCheckpoint.set(null)
        stateExecutor.execute {
            runCatching { checkNotNull(PlayerProcessStore.current()).completePlaybackContext(queueId, profileId) }
                .onFailure { error ->
                    main.postDelayed({
                        if (!serviceDestroyed.get() &&
                            executionPlan.get()?.queue?.revisionId == queueId &&
                            completedQueueRevisionId == queueId
                        ) {
                            completedQueueRevisionId = null
                            completeContext()
                        }
                    }, MARKER_POLL_INTERVAL_MS)
                    Log.w(TAG, "completion_save_failed", error)
                }
        }
    }

    private fun scheduleCheckpointDrain() {
        if (!checkpointDrainScheduled.compareAndSet(false, true)) return
        stateExecutor.execute {
            do {
                pendingCheckpoint.getAndSet(null)?.let { write ->
                    runCatching {
                        val store = checkNotNull(PlayerProcessStore.current())
                        store.saveCheckpoint(
                            write.queueRevisionId,
                            write.checkpoint,
                            write.profileId,
                            write.audiobook,
                            write.reactivateCompleted,
                        )
                    }.onSuccess { saved ->
                        checkpointFailure = false
                        if (saved) checkpointCadence.saved(SystemClock.elapsedRealtime())
                    }.onFailure { error ->
                        checkpointFailure = true
                        main.post { if (!serviceDestroyed.get()) checkpointCadence.manipulated() }
                        Log.w(TAG, "checkpoint_failed", error)
                    }
                }
                checkpointDrainScheduled.set(false)
            } while (pendingCheckpoint.get() != null && checkpointDrainScheduled.compareAndSet(false, true))
        }
    }

    private fun checkpointAtNaturalMarker() {
        val identity =
            currentIdentity() ?: run {
                markerCheckpoint = null
                return
            }
        val index = currentTocMarkers().indexOfLast { it <= exoPlayer.currentPosition }
        val current = MarkerCheckpoint(identity.occurrenceId, index)
        val previous = markerCheckpoint
        markerCheckpoint = current
        if (previous != null && previous.occurrenceId == current.occurrenceId && previous.markerIndex != current.markerIndex) {
            persistCheckpoint(immediate = true)
        }
    }

    private fun recordPlaybackStart(identity: PlaybackIdentity?) {
        if (identity == null) return
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val duration = exoPlayer.duration.takeIf { it >= 0L && it != C.TIME_UNSET }
        val audiobook = identity.mediaId in library?.effectiveBookMediaIds.orEmpty()
        val store = PlayerProcessStore.current() ?: return
        val profileId = store.activeProfile()
        stateExecutor.execute {
            if (historyQueueRevisionId == identity.queueRevisionId &&
                historyOccurrenceId == identity.occurrenceId &&
                historyPlayInstanceId != null
            ) {
                return@execute
            }
            runCatching { store.recordPlaybackStart(identity.mediaId, position, duration, audiobook, profileId) }
                .onSuccess { instance ->
                    historyQueueRevisionId = identity.queueRevisionId
                    historyOccurrenceId = identity.occurrenceId
                    historyMediaId = identity.mediaId
                    historyPlayInstanceId = instance
                }.onFailure { error -> Log.w(TAG, "play_start_history_failed", error) }
        }
    }

    private fun recoverDanglingAttempts(
        queue: PlaybackQueueSnapshot,
        occurrences: List<PlaybackQueueOccurrence>,
    ) {
        if (historyRecoveryScheduled) return
        historyRecoveryScheduled = true
        val currentMediaId =
            occurrences
                .firstOrNull { it.occurrenceId == queue.checkpoint.occurrenceId }
                ?.mediaId
        stateExecutor.execute {
            runCatching {
                PlayerProcessStore.current()?.recordDanglingPlaybackAttemptsAsCensored(
                    currentMediaId = currentMediaId,
                    positionMs = queue.checkpoint.positionMs,
                    durationMs = null,
                ) ?: 0
            }.onSuccess { count ->
                if (count > 0) Log.i(TAG, "history_recovered_censored count=$count")
            }.onFailure { error -> Log.w(TAG, "history_recovery_failed", error) }
        }
    }

    private fun recordHistoryEnd(
        type: ListeningEventType,
        identity: PlaybackIdentity?,
        positionMs: Long,
        durationMs: Long?,
    ) {
        stateExecutor.execute {
            val instance = historyPlayInstanceId ?: return@execute
            val mediaId = historyMediaId ?: identity?.mediaId ?: return@execute
            if (identity != null &&
                (historyQueueRevisionId != identity.queueRevisionId || historyOccurrenceId != identity.occurrenceId)
            ) {
                return@execute
            }
            runCatching {
                PlayerProcessStore.current()?.recordListeningEvent(
                    mediaId,
                    instance,
                    type,
                    positionMs.coerceAtLeast(0L),
                    durationMs?.coerceAtLeast(0L),
                )
            }.onFailure { error -> Log.w(TAG, "play_end_history_failed type=$type", error) }
            historyQueueRevisionId = null
            historyOccurrenceId = null
            historyMediaId = null
            historyPlayInstanceId = null
        }
    }

    private fun currentDurationMs(): Long? = exoPlayer.duration.takeIf { it >= 0L && it != C.TIME_UNSET }

    private fun endCurrentAttemptAsCensored() {
        if (!::exoPlayer.isInitialized) return
        recordHistoryEnd(
            ListeningEventType.CENSORED_END,
            currentIdentity() ?: lastItem,
            exoPlayer.currentPosition,
            currentDurationMs(),
        )
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.i(TAG, "audio_playing value=$isPlaying occurrence=${currentIdentity()?.occurrenceId?.toULong()?.toString(16)}")
            persistCheckpoint()
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            applyAudiobookParameters()
            lastItem = mediaItem?.identity()
            selectArtwork(lastItem?.queueRevisionId, lastItem?.occurrenceId)
            lastKnownPositionMs = 0L
            lastKnownDurationMs = null
            markerCheckpoint = null
            persistCheckpoint(immediate = true)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val oldIdentity = oldPosition.mediaItem?.identity()
            val newIdentity = newPosition.mediaItem?.identity()
            val endType =
                when {
                    reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> ListeningEventType.COMPLETED
                    reason == Player.DISCONTINUITY_REASON_SEEK && oldIdentity != newIdentity -> ListeningEventType.USER_SKIPPED
                    else -> null
                }
            if (endType != null) {
                recordHistoryEnd(endType, oldIdentity, oldPosition.positionMs, lastKnownDurationMs)
            }
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                persistCheckpoint(immediate = true)
            } else {
                val markers = currentTocMarkers()
                val crossed = markers.indexOfLast { it <= oldPosition.positionMs } != markers.indexOfLast { it <= newPosition.positionMs }
                markerCheckpoint =
                    currentIdentity()?.let {
                        MarkerCheckpoint(
                            it.occurrenceId,
                            markers.indexOfLast { marker ->
                                marker <=
                                    newPosition.positionMs
                            },
                        )
                    }
                persistCheckpoint(immediate = crossed)
            }
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            persistCheckpoint()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            persistCheckpoint()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(TAG, "playback_state value=$playbackState occurrence=${currentIdentity()?.occurrenceId?.toULong()?.toString(16)}")
            lastKnownPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            lastKnownDurationMs = exoPlayer.duration.takeIf { it >= 0L && it != C.TIME_UNSET }
            if (playbackState == Player.STATE_ENDED) {
                recordHistoryEnd(ListeningEventType.COMPLETED, currentIdentity(), lastKnownPositionMs, lastKnownDurationMs)
                completeContext()
                exoPlayer.playWhenReady = false
            }
            persistCheckpoint()
        }

        override fun onRepeatModeChanged(repeatMode: Int) = persistCheckpoint()

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = persistCheckpoint()

        override fun onPlayerError(error: PlaybackException) {
            val failed = currentIdentity()
            recordHistoryEnd(ListeningEventType.CENSORED_END, failed, exoPlayer.currentPosition, lastKnownDurationMs)
            failed?.occurrenceId?.let(resolvedBindings::remove)
            if (executionPlan.get()?.queue?.albumContext != true && exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
            } else {
                exoPlayer.playWhenReady = false
            }
            Log.w(TAG, "media3_error code=${error.errorCodeName}", error)
        }

        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) {
            lastKnownPositionMs = player.currentPosition.coerceAtLeast(0L)
            lastKnownDurationMs = player.duration.takeIf { it >= 0L && it != C.TIME_UNSET }
            if (player.isPlaying) recordPlaybackStart(currentIdentity())
        }
    }

    private data class ResolvedBinding(
        val queueRevisionId: Long,
        val occurrenceId: Long,
        val mediaId: MediaId,
        val source: PlaybackSourceRef,
        val uri: Uri,
    )

    private data class PlaybackIdentity(
        val queueRevisionId: Long,
        val occurrenceId: Long,
        val mediaId: MediaId,
    )

    private data class MarkerCheckpoint(
        val occurrenceId: Long,
        val markerIndex: Int,
    )

    private data class CheckpointWrite(
        val queueRevisionId: Long,
        val checkpoint: PlaybackCheckpoint,
        val profileId: Int,
        val audiobook: Boolean,
        val reactivateCompleted: Boolean,
    )

    private fun MediaItem.identity(): PlaybackIdentity? {
        val extras = mediaMetadata.extras ?: return null
        if (!extras.containsKey(PlaybackMetadata.EXTRA_QUEUE_REVISION_ID)) return null
        return PlaybackIdentity(
            queueRevisionId = extras.getLong(PlaybackMetadata.EXTRA_QUEUE_REVISION_ID),
            occurrenceId = extras.getLong(PlaybackMetadata.EXTRA_OCCURRENCE_ID),
            mediaId = requireNotNull(extras.mediaIdOrNull()),
        )
    }

    private companion object {
        const val TAG = "RiffMedia3"
        const val INSTALLATION_SETTINGS = "riff-installation-settings"
        const val REPLAY_GAIN_MODE = "replay-gain-mode"
        const val REPLAY_GAIN_TARGET = "replay-gain-target"
        const val SOURCE_TIMEOUT_SECONDS = 5L
        const val MARKER_POLL_INTERVAL_MS = 1_000L
    }
}
