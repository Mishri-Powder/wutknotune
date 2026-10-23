package link.roadio.riff.ui

import link.roadio.riff.player.core.settings.PlayerColorScheme
import link.roadio.riff.player.core.settings.PlayerDatabaseRebuild
import link.roadio.riff.player.core.settings.PlayerDiagonalChoice
import link.roadio.riff.player.core.settings.PlayerReplayGainMode
import link.roadio.riff.player.core.settings.PlayerReplayGainTarget
import link.roadio.riff.player.core.settings.PlayerSettingsCommand

/** One typed command mapping; policy and durable Save remain in the production controller. */
internal data class SettingsUiTransition(
    val command: PlayerSettingsCommand,
    val screen: PlayerScreen,
    val resetPosition: Boolean = false,
    val status: String? = null,
)

internal fun settingsUiTransition(action: PlayerUiAction): SettingsUiTransition? =
    when (action) {
        PlayerUiAction.OpenMoreSettings -> {
            SettingsUiTransition(
                PlayerSettingsCommand.OpenMore,
                PlayerScreen.SETTINGS_MORE,
                resetPosition = true,
            )
        }

        PlayerUiAction.CloseMoreSettings -> {
            SettingsUiTransition(
                PlayerSettingsCommand.CancelMore,
                PlayerScreen.SETTINGS,
                resetPosition = true,
            )
        }

        is PlayerUiAction.SetDraftTheme -> {
            SettingsUiTransition(
                PlayerSettingsCommand.SetTheme(PlayerColorScheme.valueOf(action.scheme.name)),
                PlayerScreen.SETTINGS,
            )
        }

        is PlayerUiAction.SetDraftDiagonal -> {
            SettingsUiTransition(
                PlayerSettingsCommand.SetDiagonal(PlayerDiagonalChoice.valueOf(action.diagonal.name)),
                PlayerScreen.SETTINGS_MORE,
            )
        }

        is PlayerUiAction.SetDraftLongPress -> {
            SettingsUiTransition(
                PlayerSettingsCommand.SetLongPress(action.enabled),
                PlayerScreen.SETTINGS,
            )
        }

        is PlayerUiAction.SetDraftIgnoreLeadingArticles -> {
            SettingsUiTransition(
                PlayerSettingsCommand.SetIgnoreLeadingArticles(action.enabled),
                PlayerScreen.SETTINGS,
            )
        }

        is PlayerUiAction.SetDraftAutoSearch -> {
            SettingsUiTransition(
                PlayerSettingsCommand.SetAutoSearch(action.enabled),
                PlayerScreen.SETTINGS,
            )
        }

        is PlayerUiAction.SetDraftDimBacklight -> {
            SettingsUiTransition(
                PlayerSettingsCommand.SetDimBacklight(action.mode),
                PlayerScreen.SETTINGS_MORE,
            )
        }

        is PlayerUiAction.SetDraftReplayGainMode -> {
            SettingsUiTransition(
                PlayerSettingsCommand.SetReplayGainMode(PlayerReplayGainMode.valueOf(action.mode.name)),
                PlayerScreen.SETTINGS_MORE,
            )
        }

        is PlayerUiAction.SetDraftReplayGainTarget -> {
            SettingsUiTransition(
                PlayerSettingsCommand.SetReplayGainTarget(PlayerReplayGainTarget.valueOf(action.target.name)),
                PlayerScreen.SETTINGS_MORE,
            )
        }

        is PlayerUiAction.SetDraftRebuild -> {
            SettingsUiTransition(
                PlayerSettingsCommand.SetRebuild(PlayerDatabaseRebuild.valueOf(action.rebuild.name)),
                PlayerScreen.SETTINGS_MORE,
            )
        }

        is PlayerUiAction.AcceptCalibration -> {
            SettingsUiTransition(
                PlayerSettingsCommand.AcceptCalibration(action.ppiX, action.ppiY),
                PlayerScreen.SETTINGS_MORE,
                status = "Calibration candidate %.1f × %.1f ppi · draft only".format(action.ppiX, action.ppiY),
            )
        }

        is PlayerUiAction.AcceptScreenDiagonal -> {
            SettingsUiTransition(
                PlayerSettingsCommand.AcceptDiagonal(action.ppiX),
                PlayerScreen.SETTINGS_MORE,
                status = "Scale: %.0f dpi · draft only".format(action.ppiX),
            )
        }

        else -> {
            null
        }
    }
