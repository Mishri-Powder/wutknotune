package link.roadio.riff.player.core.settings

enum class ApplicationMode { AAOS, AUTOMOTIVE, PHONE }

enum class MotionRestriction { UNRESTRICTED, RESTRICTED, UNKNOWN }

enum class RestrictionSource { AAOS_UXR, PARKING_BRAKE, UNAVAILABLE }

enum class SettingsAccess { ALLOWED, RECOVERY_REQUIRED, BLOCKED }

enum class SettingsEntry { PRESS, LONG_PRESS, RECOVERY_HOLD, BLOCKED }

/** Code-only bindings; entry gestures never replace live session/mutation permission checks.
 * AI-INTENT: https://wiki.271.pics/Riff/LATEST/application-modes-modes#5-settings-entry
 */
enum class SettingsEntryStrategy {
    PRESS,
    LONG_PRESS,
    MOTION,
    MOTION_WITH_RECOVERY,
    ;

    companion object {
        fun forMode(mode: ApplicationMode): SettingsEntryStrategy =
            when (mode) {
                ApplicationMode.PHONE -> LONG_PRESS
                ApplicationMode.AUTOMOTIVE -> MOTION_WITH_RECOVERY
                ApplicationMode.AAOS -> MOTION
            }
    }
}

/** Mark is the one bookmark mutation permitted in automotive and phone motion. */
data class BookmarkActionPolicy(
    val mode: ApplicationMode,
    val motion: MotionRestriction,
) {
    val allowed: Boolean get() = mode != ApplicationMode.AAOS || motion == MotionRestriction.UNRESTRICTED
}

/**
 * Resolves platform motion evidence into fail-closed Settings session permissions and a separate entry gesture.
 *
 * Recovery authorization exists only for the Automotive/unknown-evidence case; it must not weaken an
 * explicit restriction reported by AAOS or a parking-brake source.
 */
data class SettingsAccessPolicy(
    val mode: ApplicationMode,
    val motion: MotionRestriction,
    val source: RestrictionSource,
    val recoveryAuthorized: Boolean = false,
    val entryStrategy: SettingsEntryStrategy = SettingsEntryStrategy.forMode(mode),
) {
    val entry: SettingsEntry
        get() {
            if (access == SettingsAccess.BLOCKED) return SettingsEntry.BLOCKED
            return when (entryStrategy) {
                SettingsEntryStrategy.PRESS -> {
                    if (access == SettingsAccess.ALLOWED) SettingsEntry.PRESS else SettingsEntry.BLOCKED
                }

                SettingsEntryStrategy.LONG_PRESS -> {
                    if (access ==
                        SettingsAccess.ALLOWED
                    ) {
                        SettingsEntry.LONG_PRESS
                    } else {
                        SettingsEntry.BLOCKED
                    }
                }

                SettingsEntryStrategy.MOTION, SettingsEntryStrategy.MOTION_WITH_RECOVERY -> {
                    when {
                        motion == MotionRestriction.UNRESTRICTED && access == SettingsAccess.ALLOWED -> SettingsEntry.PRESS

                        entryStrategy == SettingsEntryStrategy.MOTION_WITH_RECOVERY &&
                            mode != ApplicationMode.AAOS &&
                            motion == MotionRestriction.UNKNOWN &&
                            source == RestrictionSource.UNAVAILABLE -> SettingsEntry.RECOVERY_HOLD

                        else -> SettingsEntry.BLOCKED
                    }
                }
            }
        }

    /** Local playlist editing is never a custom AAOS host action. */
    val playlistMutationsAllowed: Boolean
        get() =
            when (mode) {
                ApplicationMode.PHONE -> true
                ApplicationMode.AUTOMOTIVE -> motion == MotionRestriction.UNRESTRICTED
                ApplicationMode.AAOS -> false
            }

    val access: SettingsAccess
        get() =
            when (mode) {
                ApplicationMode.PHONE -> {
                    SettingsAccess.ALLOWED
                }

                ApplicationMode.AAOS -> {
                    if (motion == MotionRestriction.UNRESTRICTED) {
                        SettingsAccess.ALLOWED
                    } else {
                        SettingsAccess.BLOCKED
                    }
                }

                ApplicationMode.AUTOMOTIVE -> {
                    when (motion) {
                        MotionRestriction.UNRESTRICTED -> {
                            SettingsAccess.ALLOWED
                        }

                        MotionRestriction.RESTRICTED -> {
                            SettingsAccess.BLOCKED
                        }

                        MotionRestriction.UNKNOWN -> {
                            if (source == RestrictionSource.UNAVAILABLE) {
                                if (recoveryAuthorized) {
                                    SettingsAccess.ALLOWED
                                } else {
                                    SettingsAccess.RECOVERY_REQUIRED
                                }
                            } else {
                                SettingsAccess.BLOCKED
                            }
                        }
                    }
                }
            }

    companion object {
        val PHONE =
            SettingsAccessPolicy(
                ApplicationMode.PHONE,
                MotionRestriction.UNRESTRICTED,
                RestrictionSource.UNAVAILABLE,
            )
    }
}

enum class PlayerColorScheme { DAY, NIGHT, SYSTEM }

/** https://wiki.271.pics/Riff/LATEST/player-inactivity#settings-and-defaults */
enum class PlayerDimBacklightMode { OFF, DAY, NIGHT, AUTO }

enum class PlayerDiagonalChoice { DIAGONAL, CARD, AUTO }

enum class PlayerDataLocation { HU, SD }

enum class PlayerDatabaseRebuild { AU, IA }

enum class PlayerReplayGainMode { N, I, F }

enum class PlayerReplayGainTarget(
    val displayValue: Int,
    val canonicalOffsetQ8: Int?,
) {
    ZERO(0, null),
    LUFS_16(16, 2 * 256),
    LUFS_18(18, 0),
    LUFS_23(23, -5 * 256),
}

data class PlayerSettings(
    val colorScheme: PlayerColorScheme = PlayerColorScheme.SYSTEM,
    val diagonal: PlayerDiagonalChoice = PlayerDiagonalChoice.AUTO,
    val dataLocation: PlayerDataLocation = PlayerDataLocation.HU,
    val longPressStartsPlayback: Boolean = false,
    val autoSearch: Boolean = true,
    val ignoreLeadingArticles: Boolean = true,
    val dimBacklight: PlayerDimBacklightMode = PlayerDimBacklightMode.AUTO,
    val replayGainMode: PlayerReplayGainMode = PlayerReplayGainMode.F,
    val replayGainTarget: PlayerReplayGainTarget = PlayerReplayGainTarget.LUFS_23,
    val calibrationPpiX: Float? = null,
    val calibrationPpiY: Float? = null,
)

/** Immutable reducer output carrying both the committed profile and its optional transaction draft. */
data class PlayerSettingsSnapshot(
    val committed: PlayerSettings,
    val draft: PlayerSettings?,
    val moreDraft: PlayerSettings?,
    val open: Boolean,
    val access: SettingsAccess,
    val pendingRebuild: Set<PlayerDatabaseRebuild>,
)

sealed interface PlayerSettingsCommand {
    data object Open : PlayerSettingsCommand

    data object OpenMore : PlayerSettingsCommand

    data class SetTheme(
        val colorScheme: PlayerColorScheme,
    ) : PlayerSettingsCommand

    data class SetProfileSettings(
        val colorScheme: PlayerColorScheme,
        val longPressStartsPlayback: Boolean,
        val autoSearch: Boolean,
        val ignoreLeadingArticles: Boolean,
    ) : PlayerSettingsCommand

    data class SetDiagonal(
        val diagonal: PlayerDiagonalChoice,
    ) : PlayerSettingsCommand

    data class SetLongPress(
        val enabled: Boolean,
    ) : PlayerSettingsCommand

    data class SetIgnoreLeadingArticles(
        val enabled: Boolean,
    ) : PlayerSettingsCommand

    data class SetAutoSearch(
        val enabled: Boolean,
    ) : PlayerSettingsCommand

    data class SetDimBacklight(
        val mode: PlayerDimBacklightMode,
    ) : PlayerSettingsCommand

    data class SetReplayGainMode(
        val mode: PlayerReplayGainMode,
    ) : PlayerSettingsCommand

    data class SetReplayGainTarget(
        val target: PlayerReplayGainTarget,
    ) : PlayerSettingsCommand

    data class SetRebuild(
        val rebuild: PlayerDatabaseRebuild,
    ) : PlayerSettingsCommand

    data class RebaseDataLocation(
        val location: PlayerDataLocation,
    ) : PlayerSettingsCommand

    data class AcceptCalibration(
        val ppiX: Float,
        val ppiY: Float,
    ) : PlayerSettingsCommand

    data class AcceptDiagonal(
        val ppiX: Float,
    ) : PlayerSettingsCommand

    data object Save : PlayerSettingsCommand

    data object SaveMore : PlayerSettingsCommand

    data object Cancel : PlayerSettingsCommand

    data object CancelMore : PlayerSettingsCommand
}

class PlayerSettingsCommandException(
    val code: String,
    message: String,
) : IllegalStateException(message)

/**
 * In-memory draft/commit reducer for one Settings session.
 *
 * The reducer intentionally performs no persistence, migration, rebuild, or calibration UI work. Its
 * caller commits those side effects only after [PlayerSettingsCommand.Save], keeping Cancel lossless.
 */
class PlayerSettingsController(
    initial: PlayerSettings = PlayerSettings(),
    private val accessPolicy: () -> SettingsAccessPolicy = { SettingsAccessPolicy.PHONE },
) {
    private var committed = initial
    private var draft: PlayerSettings? = null
    private var moreDraft: PlayerSettings? = null
    private var pendingRebuild: Set<PlayerDatabaseRebuild> = emptySet()

    @Synchronized
    fun snapshot(): PlayerSettingsSnapshot =
        PlayerSettingsSnapshot(
            committed = committed,
            draft = draft,
            moreDraft = moreDraft,
            open = draft != null,
            access = accessPolicy().access,
            pendingRebuild = pendingRebuild,
        )

    @Synchronized
    fun execute(command: PlayerSettingsCommand): PlayerSettingsSnapshot {
        // A draft may outlive a parked state. Check at every command, including final Save.
        // https://wiki.271.pics/Riff/LATEST/application-modes-modes#5-settings-entry
        if (
            command != PlayerSettingsCommand.Cancel &&
            command != PlayerSettingsCommand.CancelMore &&
            command !is PlayerSettingsCommand.RebaseDataLocation &&
            accessPolicy().access != SettingsAccess.ALLOWED
        ) {
            throw PlayerSettingsCommandException("SETTINGS_UNAVAILABLE", "Settings are unavailable under the current restrictions")
        }
        when (command) {
            PlayerSettingsCommand.Open -> {
                open()
            }

            PlayerSettingsCommand.OpenMore -> {
                openMore()
            }

            is PlayerSettingsCommand.SetTheme -> {
                updateDraft { it.copy(colorScheme = command.colorScheme) }
            }

            is PlayerSettingsCommand.SetProfileSettings -> {
                updateDraft {
                    it.copy(
                        colorScheme = command.colorScheme,
                        longPressStartsPlayback = command.longPressStartsPlayback,
                        autoSearch = command.autoSearch,
                        ignoreLeadingArticles = command.ignoreLeadingArticles,
                    )
                }
            }

            is PlayerSettingsCommand.SetDiagonal -> {
                require(command.diagonal == PlayerDiagonalChoice.AUTO) {
                    "DIAGONAL and CARD require a measured DPI"
                }
                updateMoreDraft {
                    it.copy(
                        diagonal = command.diagonal,
                        calibrationPpiX = null,
                        calibrationPpiY = null,
                    )
                }
            }

            is PlayerSettingsCommand.SetLongPress -> {
                updateDraft { it.copy(longPressStartsPlayback = command.enabled) }
            }

            is PlayerSettingsCommand.SetIgnoreLeadingArticles -> {
                updateDraft { it.copy(ignoreLeadingArticles = command.enabled) }
            }

            is PlayerSettingsCommand.SetAutoSearch -> {
                updateDraft { it.copy(autoSearch = command.enabled) }
            }

            is PlayerSettingsCommand.SetDimBacklight -> {
                updateMoreDraft { it.copy(dimBacklight = command.mode) }
            }

            is PlayerSettingsCommand.SetReplayGainMode -> {
                updateMoreDraft { it.copy(replayGainMode = command.mode) }
            }

            is PlayerSettingsCommand.SetReplayGainTarget -> {
                updateMoreDraft { it.copy(replayGainTarget = command.target) }
            }

            is PlayerSettingsCommand.SetRebuild -> {
                requireMoreDraft()
                pendingRebuild =
                    if (command.rebuild in pendingRebuild) pendingRebuild - command.rebuild else pendingRebuild + command.rebuild
            }

            is PlayerSettingsCommand.RebaseDataLocation -> {
                committed = committed.copy(dataLocation = command.location)
                draft = draft?.copy(dataLocation = command.location)
                moreDraft = moreDraft?.copy(dataLocation = command.location)
            }

            is PlayerSettingsCommand.AcceptCalibration -> {
                acceptCalibration(command)
            }

            is PlayerSettingsCommand.AcceptDiagonal -> {
                acceptDiagonal(command)
            }

            PlayerSettingsCommand.Save -> {
                save()
            }

            PlayerSettingsCommand.SaveMore -> {
                saveMore()
            }

            PlayerSettingsCommand.Cancel -> {
                cancel()
            }

            PlayerSettingsCommand.CancelMore -> {
                cancelMore()
            }
        }
        return snapshot()
    }

    /** Apply an already durable result, even if restrictions cancelled its draft during persistence. */
    @Synchronized
    fun completePersistedSave(
        saved: PlayerSettings,
        more: Boolean,
    ): PlayerSettingsSnapshot {
        if (more) {
            committed = committed.withInstallationValues(saved)
            draft = draft?.withInstallationValues(saved)
            moreDraft = null
        } else {
            committed = saved
            draft = null
            moreDraft = null
        }
        pendingRebuild = emptySet()
        return snapshot()
    }

    private fun open() {
        if (accessPolicy().access != SettingsAccess.ALLOWED) {
            throw PlayerSettingsCommandException(
                "SETTINGS_UNAVAILABLE",
                "Settings entry is unavailable under the configured application-mode policy",
            )
        }
        if (draft != null) {
            throw PlayerSettingsCommandException("SETTINGS_ALREADY_OPEN", "Settings is already open")
        }
        draft = committed
        pendingRebuild = emptySet()
    }

    private fun updateDraft(transform: (PlayerSettings) -> PlayerSettings) {
        val current = requireDraft()
        draft = transform(current)
    }

    private fun openMore() {
        requireDraft()
        if (moreDraft != null) {
            throw PlayerSettingsCommandException("MORE_SETTINGS_ALREADY_OPEN", "More settings is already open")
        }
        moreDraft = committed
        pendingRebuild = emptySet()
    }

    private fun updateMoreDraft(transform: (PlayerSettings) -> PlayerSettings) {
        moreDraft = transform(requireMoreDraft())
    }

    private fun requireDraft(): PlayerSettings =
        draft
            ?: throw PlayerSettingsCommandException(
                "SETTINGS_NOT_OPEN",
                "A Settings draft must be open",
            )

    private fun requireMoreDraft(): PlayerSettings =
        moreDraft
            ?: throw PlayerSettingsCommandException(
                "MORE_SETTINGS_NOT_OPEN",
                "A More settings draft must be open",
            )

    private fun acceptCalibration(command: PlayerSettingsCommand.AcceptCalibration) {
        require(command.ppiX.isFinite() && command.ppiX > 0f) { "ppiX must be finite and positive" }
        require(command.ppiY.isFinite() && command.ppiY > 0f) { "ppiY must be finite and positive" }
        updateMoreDraft {
            it.copy(
                diagonal = PlayerDiagonalChoice.CARD,
                calibrationPpiX = command.ppiX,
                calibrationPpiY = command.ppiY,
            )
        }
    }

    private fun acceptDiagonal(command: PlayerSettingsCommand.AcceptDiagonal) {
        require(command.ppiX.isFinite() && command.ppiX > 0f) {
            "ppiX must be finite and positive"
        }
        updateMoreDraft {
            it.copy(
                diagonal = PlayerDiagonalChoice.DIAGONAL,
                calibrationPpiX = command.ppiX,
                calibrationPpiY = null,
            )
        }
    }

    private fun save() {
        val current =
            draft
                ?: throw PlayerSettingsCommandException(
                    "SETTINGS_NOT_OPEN",
                    "A Settings draft must be open",
                )
        committed = current
        draft = null
        moreDraft = null
        pendingRebuild = emptySet()
    }

    private fun saveMore() {
        val current = requireMoreDraft()
        committed = committed.withInstallationValues(current)
        draft = draft?.withInstallationValues(current)
        moreDraft = null
        pendingRebuild = emptySet()
    }

    private fun cancel() {
        if (draft == null) {
            throw PlayerSettingsCommandException("SETTINGS_NOT_OPEN", "A Settings draft must be open")
        }
        draft = null
        moreDraft = null
        pendingRebuild = emptySet()
    }

    private fun cancelMore() {
        requireMoreDraft()
        moreDraft = null
        pendingRebuild = emptySet()
    }

    private fun PlayerSettings.withInstallationValues(source: PlayerSettings): PlayerSettings =
        copy(
            diagonal = source.diagonal,
            dataLocation = source.dataLocation,
            dimBacklight = source.dimBacklight,
            replayGainMode = source.replayGainMode,
            replayGainTarget = source.replayGainTarget,
            calibrationPpiX = source.calibrationPpiX,
            calibrationPpiY = source.calibrationPpiY,
        )
}
