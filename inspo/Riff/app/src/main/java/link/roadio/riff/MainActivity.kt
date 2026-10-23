package link.roadio.riff

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import link.roadio.riff.player.core.settings.ApplicationMode
import link.roadio.riff.player.core.settings.MotionRestriction
import link.roadio.riff.player.core.settings.RestrictionSource
import link.roadio.riff.player.core.settings.SettingsAccess
import link.roadio.riff.player.core.settings.SettingsAccessPolicy
import link.roadio.riff.shared.config.AndroidProjectConfiguration
import link.roadio.riff.storage.root.ApplicationDataStorage
import link.roadio.riff.ui.AndroidSystemBrightnessWorkaround
import link.roadio.riff.ui.CalibrationSessionState
import link.roadio.riff.ui.PhysicalDensityProvider
import link.roadio.riff.ui.PlayerComposeUi
import link.roadio.riff.ui.PlayerScreen
import link.roadio.riff.ui.PlayerSettingsUiState
import link.roadio.riff.ui.PlayerUiAction
import link.roadio.riff.ui.PlayerUiState
import link.roadio.riff.ui.ProcessReleaseAgeWarningSession
import link.roadio.riff.ui.ReleaseIdentity
import link.roadio.riff.ui.RiffPlayerUiController
import link.roadio.riff.ui.StorageStartupAction
import link.roadio.riff.ui.StorageWaitingDialog
import link.roadio.riff.ui.StorageWaitingState
import link.roadio.riff.ui.UiConfig
import link.roadio.riff.ui.UiPalette
import link.roadio.riff.ui.WindowBacklight
import link.roadio.riff.ui.applyRiffImmersiveMode
import link.roadio.riff.ui.resolvedNight
import link.roadio.riff.ui.shouldHandleBackInApp
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Android host for the Player process.
 *
 * Lifecycle, raw-input idle timing, DocumentsUI, and the one Compose root stop here. Durable state,
 * navigation, playback decisions, and Indexer coordination belong to [RiffPlayerUiController], so an
 * Activity recreation cannot become a second domain state machine. The bootstrap gate admits the selected
 * root before constructing that controller; mount absence never changes storage authority.
 */
class MainActivity : ComponentActivity() {
    private lateinit var controller: RiffPlayerUiController
    private val densityProvider by lazy { PhysicalDensityProvider(applicationContext) }
    private val calibrationSession = CalibrationSessionState()
    private val releaseIdentity by lazy { ReleaseIdentity.fromBuildConfig() }
    private var uiState by mutableStateOf<PlayerUiState?>(null)
    private var uiConfig by mutableStateOf<UiConfig?>(null)
    private var releaseAgeWarningVisible by mutableStateOf(false)
    private var observation: AutoCloseable? = null
    private var startupLabel by mutableStateOf("Loading…")
    private var storageWaiting by mutableStateOf<StorageWaitingState?>(null)
    private var startupInFlight = false
    private var startupStartedAt = 0L
    private var recoveryAuthorized = false
    private val storageRetry = Runnable { attemptStartup() }
    private val storageReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                attemptStartup()
            }
        }
    private val idleHandler = Handler(Looper.getMainLooper())
    private val startupExecutor = Executors.newSingleThreadExecutor()
    private val startupClosed = AtomicBoolean()
    private var idleTimeoutMillis = DEFAULT_IDLE_TIMEOUT_MILLIS
    private var activityResumed = false
    private var windowFocused = false
    private var pointerActive = false
    private var idleExpired = false
    private var lastFullscreen: Boolean? = null
    private var backlight: WindowBacklight? = null
    private var lastPlaybackActive = false
    private var lastIdleBlocked = false
    private val idleRunnable =
        Runnable {
            if (!activityResumed ||
                !windowFocused ||
                pointerActive ||
                !::controller.isInitialized ||
                idleBlocked(controller.state)
            ) {
                return@Runnable
            }
            calibrationSession.clear()
            controller.dispatch(PlayerUiAction.IdleTimeout)
            idleExpired = true
            updateBacklight()
        }
    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = handleBack()
        }
    private val safTreeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleSafTreeResult(result.resultCode, result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startupStartedAt = SystemClock.elapsedRealtime()
        val loadedUiConfig = UiConfig.defaults(this)
        releaseAgeWarningVisible =
            ProcessReleaseAgeWarningSession.current.shouldShow(releaseIdentity, LocalDate.now())
        uiConfig = loadedUiConfig
        reloadIdleTimeout(loadedUiConfig)
        backlight = WindowBacklight(window, contentResolver, ::updateBacklight)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applySystemBars()
        onBackPressedDispatcher.addCallback(this, backCallback)

        // Install the first app-owned frame before opening removable storage or SQLite.
        setContent {
            val configuration = LocalConfiguration.current
            val state = uiState
            val config = uiConfig
            if (state == null || config == null) {
                val startupConfig = config ?: loadedUiConfig
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(
                            densityProvider.densityDpi(this, PlayerSettingsUiState()) / DENSITY_DEFAULT_DPI,
                            configuration.fontScale,
                        ),
                ) {
                    playerStartupUi(startupConfig, startupLabel)
                    storageWaiting?.let { waiting ->
                        val night = isSystemInDarkTheme()
                        StorageWaitingDialog(
                            waiting,
                            startupConfig.themed(night),
                            if (night) UiPalette.night(startupConfig) else UiPalette.day(startupConfig),
                            ::dispatchStorageAction,
                        )
                    }
                }
            } else {
                val densityDpi = densityProvider.densityDpi(this, state.settings)
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(
                            density = densityDpi / DENSITY_DEFAULT_DPI,
                            fontScale = configuration.fontScale,
                        ),
                ) {
                    PlayerComposeUi(
                        state = state,
                        config = config,
                        calibrationSession = calibrationSession,
                        hostContext = this,
                        dispatch = ::dispatch,
                        releaseIdentity = releaseIdentity,
                        releaseAgeWarningVisible = releaseAgeWarningVisible,
                        onAcknowledgeReleaseAge = ::acknowledgeReleaseAgeWarning,
                    )
                }
            }
        }
        Log.i(STARTUP_LOG_TAG, "first_frame_scheduled elapsed_ms=${SystemClock.elapsedRealtime() - startupStartedAt}")

        @Suppress("UnspecifiedRegisterReceiverFlag")
        registerReceiver(
            storageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addDataScheme("file")
            },
        )
        attemptStartup()
    }

    private fun storageAccess(): SettingsAccess {
        val storage = ApplicationDataStorage(this)
        val actualAaos = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val mode =
            if (actualAaos) {
                ApplicationMode.AAOS
            } else {
                when (storage.cachedApplicationMode()) {
                    "phone" -> ApplicationMode.PHONE
                    "aaos" -> ApplicationMode.AAOS
                    else -> ApplicationMode.AUTOMOTIVE
                }
            }
        // No supported live motion adapter exists yet: UNKNOWN must never be interpreted as parked.
        return SettingsAccessPolicy(mode, MotionRestriction.UNKNOWN, RestrictionSource.UNAVAILABLE).access
    }

    private fun dispatchStorageAction(action: StorageStartupAction) {
        val waiting = storageWaiting ?: return
        if (startupClosed.get() || startupInFlight || ::controller.isInitialized) return
        val access = storageAccess()
        when (action) {
            StorageStartupAction.CANCEL_RESET -> {
                recoveryAuthorized = false
                storageWaiting = waiting.copy(confirmation = false, access = access)
            }

            StorageStartupAction.REQUEST_RESET, StorageStartupAction.AUTHORIZE_RECOVERY -> {
                recoveryAuthorized = action == StorageStartupAction.AUTHORIZE_RECOVERY && access == SettingsAccess.RECOVERY_REQUIRED
                if (access == SettingsAccess.ALLOWED ||
                    recoveryAuthorized
                ) {
                    storageWaiting = waiting.copy(confirmation = true, access = access)
                }
            }

            StorageStartupAction.CONFIRM_RESET -> {
                if (waiting.confirmation &&
                    (access == SettingsAccess.ALLOWED || (access == SettingsAccess.RECOVERY_REQUIRED && recoveryAuthorized))
                ) {
                    attemptStartup(reset = true)
                }
            }
        }
    }

    private fun attemptStartup(reset: Boolean = false) {
        if (startupClosed.get() || startupInFlight || ::controller.isInitialized) return
        idleHandler.removeCallbacks(storageRetry)
        startupInFlight = true
        if (reset) storageWaiting = storageWaiting?.copy(busy = true)
        val access = storageAccess()
        startupExecutor.execute {
            val storage = ApplicationDataStorage(applicationContext)
            val result =
                runCatching {
                    if (reset) storage.resetToInternal(storage.selection())
                    if (!storage.prepareForStartup()) {
                        null
                    } else {
                        val config = UiConfig.load(applicationContext)
                        val created = RiffPlayerUiController(applicationContext)
                        Triple(
                            config,
                            created,
                            AndroidSystemBrightnessWorkaround.create(
                                applicationContext,
                                created.systemBrightnessWorkaroundAllowed,
                            ),
                        )
                    }
                }
            idleHandler.post {
                startupInFlight = false
                val created = result.getOrNull()?.second
                if (startupClosed.get()) {
                    created?.close()
                    result.getOrNull()?.third?.close()
                    return@post
                }
                if (created != null) {
                    storageWaiting = null
                    recoveryAuthorized = false
                    uiConfig = result.getOrThrow()!!.first
                    reloadIdleTimeout(uiConfig!!)
                    backlight?.configure(result.getOrThrow()!!.third)
                    controller = created
                    observation = created.observe(::render)
                    AndroidProjectConfiguration.takeStartupProblem()?.let {
                        Toast.makeText(this, "Invalid configuration. Using packaged defaults.", Toast.LENGTH_LONG).show()
                    }
                    restartIdleTimer()
                    Log.i(STARTUP_LOG_TAG, "controller_ready elapsed_ms=${SystemClock.elapsedRealtime() - startupStartedAt}")
                } else if (result.isSuccess || !storage.selectedRootAvailable() || reset) {
                    val previous = storageWaiting
                    storageWaiting =
                        StorageWaitingState(
                            storage.savedDisplayName(),
                            access,
                            confirmation = previous?.confirmation == true && !reset,
                            error = if (result.isFailure) "Storage is not ready. Reconnect it or try again." else null,
                        )
                    startupLabel = "Waiting for selected storage…"
                    if (activityResumed) idleHandler.postDelayed(storageRetry, STORAGE_RETRY_MILLIS)
                    Log.i(STARTUP_LOG_TAG, "waiting_for_selected_storage")
                } else {
                    startupLabel = "Player failed to start"
                    Log.e(STARTUP_LOG_TAG, "controller_start_failed type=${result.exceptionOrNull()?.javaClass?.simpleName}")
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
        // Window/theme changes only resolve the already loaded immutable snapshot in Compose.
        if (::controller.isInitialized) {
            controller.onSystemLocaleChanged()
            uiState = controller.state
        }
        updateBacklight()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        attemptStartup()
        restartIdleTimer()
    }

    override fun onPause() {
        activityResumed = false
        pointerActive = false
        idleExpired = false
        backlight?.restore()
        idleHandler.removeCallbacks(storageRetry)
        cancelIdleTimer()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocused = hasFocus
        pointerActive = false
        if (hasFocus) {
            applySystemBars()
            restartIdleTimer()
        } else {
            cancelIdleTimer()
            idleExpired = false
            backlight?.restore()
        }
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        restartIdleTimer()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        restartIdleTimer()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        pointerActive = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
        restartIdleTimer()
        return super.dispatchTouchEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        restartIdleTimer()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        restartIdleTimer()
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        restartIdleTimer()
        return super.onKeyUp(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        restartIdleTimer()
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDestroy() {
        backlight?.close()
        backlight = null
        cancelIdleTimer()
        startupClosed.set(true)
        idleHandler.removeCallbacks(storageRetry)
        unregisterReceiver(storageReceiver)
        startupExecutor.shutdownNow()
        observation?.close()
        if (::controller.isInitialized) controller.close()
        super.onDestroy()
    }

    private fun handleSafTreeResult(
        resultCode: Int,
        data: Intent?,
    ) {
        if (!::controller.isInitialized) return
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return
        val grantFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        controller.dispatch(
            if (grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                PlayerUiAction.SafGrantPicked(uri.toString(), grantFlags)
            } else {
                PlayerUiAction.SafGrantPickerFailed
            },
        )
    }

    private fun handleBack() {
        if (!::controller.isInitialized) return
        restartIdleTimer()
        val state = controller.state
        when {
            state.safListingBugWarning != null -> {
                return
            }

            state.playlistNameRequest != null -> {
                controller.dispatch(PlayerUiAction.CancelPlaylistName)
            }

            state.pendingPlaylistDelete != null -> {
                controller.dispatch(PlayerUiAction.CancelPlaylistDelete)
            }

            state.pendingSafGrantRemoval != null -> {
                controller.dispatch(PlayerUiAction.CancelSafGrantRemoval)
            }

            else -> {
                controller.dispatch(
                    when (state.screen) {
                        PlayerScreen.PLAYBACK -> PlayerUiAction.OpenMain
                        PlayerScreen.MAIN -> return
                        PlayerScreen.HIERARCHY -> PlayerUiAction.NavigateParent
                        PlayerScreen.AZ_SEARCH -> PlayerUiAction.CancelAzSearch
                        PlayerScreen.AI -> PlayerUiAction.OpenMain
                        PlayerScreen.AI_ACTIONS -> PlayerUiAction.CloseAiActions
                        PlayerScreen.SETTINGS -> PlayerUiAction.CancelSettings
                        PlayerScreen.SETTINGS_MORE -> PlayerUiAction.CloseMoreSettings
                        PlayerScreen.SAF_GRANTS -> PlayerUiAction.CancelSafGrantSelector
                        PlayerScreen.DATA_STORAGE -> PlayerUiAction.CancelDataStorageSelector
                        PlayerScreen.ROW_ACTIONS -> PlayerUiAction.CloseRowActions
                        PlayerScreen.PLAYLIST_SELECTOR -> PlayerUiAction.ClosePlaylistSelector
                        PlayerScreen.CALIBRATION -> PlayerUiAction.CancelCalibration
                        PlayerScreen.SCREEN_DIAGONAL -> PlayerUiAction.CancelScreenDiagonal
                    },
                )
            }
        }
    }

    private fun render(state: PlayerUiState) {
        backCallback.isEnabled = shouldHandleBackInApp(state.screen)
        val idleBlocked = idleBlocked(state)
        val playbackJustStopped =
            lastPlaybackActive && !state.nowPlaying.playing && !state.nowPlaying.repeat
        if (idleBlocked) {
            cancelIdleTimer()
        } else if (lastIdleBlocked || playbackJustStopped) {
            restartIdleTimer()
        }
        lastPlaybackActive = state.nowPlaying.playing
        lastIdleBlocked = idleBlocked
        uiState = state
        updateBacklight()
    }

    private fun dispatch(action: PlayerUiAction) {
        if (!::controller.isInitialized) return
        if (releaseAgeWarningVisible) return
        restartIdleTimer()
        if (action == PlayerUiAction.OpenCalibration) calibrationSession.clear()
        if (action == PlayerUiAction.OpenSafDocumentTree && controller.canOpenSettingsDocumentTree()) {
            openSafDocumentTree()
        } else {
            controller.dispatch(action)
        }
        if (action == PlayerUiAction.CancelCalibration || action is PlayerUiAction.AcceptCalibration) {
            calibrationSession.clear()
        }
        window.decorView.postDelayed(::applySystemBars, SYSTEM_BAR_REHIDE_DELAY_MS)
    }

    private fun acknowledgeReleaseAgeWarning() {
        ProcessReleaseAgeWarningSession.current.acknowledge()
        releaseAgeWarningVisible = false
        restartIdleTimer()
    }

    private fun openSafDocumentTree() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            safTreeLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            controller.dispatch(PlayerUiAction.SafGrantPickerFailed)
        }
    }

    private fun applySystemBars() {
        window.applyRiffImmersiveMode()
    }

    // Authority: https://wiki.271.pics/Riff/LATEST/player-inactivity
    // AI-INTENT: This is the sole clock/input owner; controller receives only the expiry event.
    private fun restartIdleTimer() {
        cancelIdleTimer()
        idleExpired = false
        backlight?.restore()
        if (!activityResumed || !windowFocused || pointerActive || !::controller.isInitialized || idleBlocked(controller.state)) return
        idleHandler.postDelayed(idleRunnable, idleTimeoutMillis)
    }

    private fun cancelIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable)
    }

    private fun idleBlocked(state: PlayerUiState): Boolean =
        state.settingsSaveInProgress ||
            (state.playlistMutationInProgress && (state.playlistNameRequest != null || state.pendingPlaylistDelete != null)) ||
            releaseAgeWarningVisible ||
            state.safListingBugWarning != null ||
            state.storageMigrationInProgress ||
            state.pendingSafGrantRemoval != null

    // Android owns the baseline; both policy and UI use the same actual draft-preview theme.
    // https://wiki.271.pics/Riff/LATEST/player-inactivity#one-inactivity-interval
    private fun updateBacklight() {
        val state = uiState
        val config = uiConfig
        if (state == null || config == null) {
            backlight?.restore()
            return
        }
        val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val night = (state.settingsDraft?.colorScheme ?: state.settings.colorScheme).resolvedNight(systemNight)
        val fullscreen =
            !isInMultiWindowMode &&
                !isInPictureInPictureMode &&
                !window.isFloating &&
                windowManager.currentWindowMetrics.bounds == windowManager.maximumWindowMetrics.bounds
        val regainedFullscreen = lastFullscreen == false && fullscreen
        lastFullscreen = fullscreen
        if (regainedFullscreen) restartIdleTimer()
        backlight?.update(
            state.settings.dimBacklight,
            night,
            activityResumed && windowFocused && fullscreen && !idleBlocked(state),
            idleExpired,
            config.themed(night).float("behavior.dimBacklightPercent"),
        )
    }

    private fun reloadIdleTimeout(config: UiConfig) {
        idleTimeoutMillis = config.long("behavior.idleTimeoutSeconds") * 1_000L
    }

    private companion object {
        const val STARTUP_LOG_TAG = "RiffStartup"
        const val SYSTEM_BAR_REHIDE_DELAY_MS = 250L
        const val DEFAULT_IDLE_TIMEOUT_MILLIS = 30_000L
        const val STORAGE_RETRY_MILLIS = 2_000L
        const val DENSITY_DEFAULT_DPI = 160f
    }
}

@Composable
private fun playerStartupUi(
    config: UiConfig,
    label: String,
) {
    val night = isSystemInDarkTheme()
    val palette = if (night) UiPalette.night(config) else UiPalette.day(config)
    Box(
        modifier = Modifier.fillMaxSize().background(Color(palette.selectBackground)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(color = Color(palette.text), fontSize = 28.sp),
        )
    }
}
