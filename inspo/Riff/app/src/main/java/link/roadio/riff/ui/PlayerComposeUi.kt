@file:Suppress("FunctionName")

package link.roadio.riff.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun PlayerComposeUi(
    state: PlayerUiState,
    config: UiConfig,
    calibrationSession: CalibrationSessionState,
    hostContext: Context,
    releaseIdentity: ReleaseIdentity = ReleaseIdentity.fromBuildConfig(),
    releaseAgeWarningVisible: Boolean = false,
    onAcknowledgeReleaseAge: () -> Unit = {},
    dispatch: (PlayerUiAction) -> Unit,
) {
    // AI-INTENT: Auto observes Android configuration even when Player domain state is unchanged.
    // https://wiki.271.pics/Riff/LATEST/player-inactivity#settings-and-defaults
    val systemNight = isSystemInDarkTheme()
    val scheme = state.settingsDraft?.colorScheme ?: state.settings.colorScheme
    val night = scheme.resolvedNight(systemNight)
    val rootDensity = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Read current density explicitly: identical pixel constraints can survive a density change.
        val rootWidthDp = constraints.maxWidth / rootDensity
        val resolvedConfig = remember(config, rootWidthDp, night) { config.resolved(rootWidthDp, night) }
        val palette = if (night) UiPalette.night(resolvedConfig) else UiPalette.day(resolvedConfig)
        CompositionLocalProvider(
            LocalRiffConfig provides resolvedConfig,
            LocalRiffPalette provides palette,
            LocalRiffDispatch provides dispatch,
            LocalRiffUncontrolledBackground provides (state.screen == PlayerScreen.PLAYBACK),
            LocalBackgroundFlipActive provides
                (state.screen == PlayerScreen.PLAYBACK || state.screen == PlayerScreen.MAIN),
        ) {
            Box(Modifier.fillMaxSize().background(Color(palette.background))) {
                when (state.screen) {
                    PlayerScreen.PLAYBACK -> PlaybackScreen(state, night)
                    PlayerScreen.MAIN -> MainScreen(state, releaseIdentity)
                    PlayerScreen.HIERARCHY, PlayerScreen.AZ_SEARCH -> HierarchyScreen(state)
                    PlayerScreen.AI -> AiScreen(state, night)
                    PlayerScreen.AI_ACTIONS -> AiActionsScreen(state)
                    PlayerScreen.SETTINGS -> SettingsScreen(state, more = false)
                    PlayerScreen.SETTINGS_MORE -> SettingsScreen(state, more = true)
                    PlayerScreen.SAF_GRANTS -> SafGrantScreen(state)
                    PlayerScreen.DATA_STORAGE -> DataStorageScreen(state)
                    PlayerScreen.ROW_ACTIONS -> RowActionsScreen(state)
                    PlayerScreen.PLAYLIST_SELECTOR -> PlaylistSelectorScreen(state)
                    PlayerScreen.CALIBRATION -> CalibrationHost(calibrationSession, hostContext)
                    PlayerScreen.SCREEN_DIAGONAL -> ScreenDiagonalScreen(hostContext)
                }
                if (state.safListingBugWarning == null) AppDialogs(state)
                if (releaseAgeWarningVisible && state.screen == PlayerScreen.MAIN) {
                    RiffModalDialog(
                        title = releaseIdentity.ageWarningTitle,
                        message = releaseIdentity.ageWarningMessage,
                        confirmation = RiffDialogAction("OK", onAcknowledgeReleaseAge),
                    )
                } else {
                    state.safListingBugWarning?.let { notice ->
                        RiffModalDialog(
                            title = notice.title,
                            message = notice.message,
                            confirmation = RiffDialogAction("OK", { dispatch(PlayerUiAction.AcknowledgeSafListingBug) }),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SafeContent(
    background: Color,
    tag: String,
    onBackgroundTap: (() -> Unit)? = null,
    safeInsets: WindowInsets? = null,
    underlay: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val backgroundTapModifier =
        onBackgroundTap?.let { Modifier.backgroundFlipTap(it) } ?: Modifier
    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .then(backgroundTapModifier),
    ) {
        underlay?.invoke()
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(safeInsets ?: WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .padding(uiDimension("screenEdgeMargin"))
                .testTag(tag),
        ) {
            content()
        }
    }
}

@Composable
internal fun CalibrationHost(
    session: CalibrationSessionState,
    hostContext: Context,
) {
    val dispatch = LocalRiffDispatch.current
    val palette = LocalRiffPalette.current
    // AI-INTENT: Calibration uses raw host pixels and context through the sole app-owned View bridge.
    // https://wiki.271.pics/Riff/LATEST/calibration-screen-why-this-screen-is-different-from-every-other-screen
    AndroidView(
        factory = {
            CalibrationScreenView(
                hostContext,
                session,
                palette,
                drawable = { name -> UiDrawableRegistry.drawable(hostContext, name) },
                onCancel = { dispatch(PlayerUiAction.CancelCalibration) },
                onAccept = { ppiX, ppiY ->
                    dispatch(PlayerUiAction.AcceptCalibration(ppiX, ppiY))
                },
            )
        },
        update = { view ->
            view.drawable = { name -> UiDrawableRegistry.drawable(hostContext, name) }
            view.palette = palette
        },
        modifier = Modifier.fillMaxSize().testTag("screen:CALIBRATION"),
    )
}
