@file:Suppress("FunctionName")

package link.roadio.riff.ui

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import link.roadio.riff.player.core.settings.PlayerDimBacklightMode
import kotlin.math.roundToInt

internal fun settingsOkAction(
    description: String,
    enabled: Boolean,
    action: PlayerUiAction,
): TopActionSpec = TopActionSpec("ok", description, enabled, action)

// This rendering boundary accepts immutable state and emits typed actions.
// Authority: https://wiki.271.pics/Riff/LATEST/ui-foundation
// AI-INTENT: Keep databases, Binder, Media3, storage, and Indexer calls outside this file. Calibration's
// AndroidView is the sole raw-pixel exception; it still reports typed actions instead of owning state.
internal fun replayGainTargetDescription(
    current: ReplayGainTarget,
    next: ReplayGainTarget,
): String {
    fun ReplayGainTarget.spoken(): String = if (this == ReplayGainTarget.ZERO) "zero, unity gain" else "minus $displayValue LUFS"
    return "ReplayGain target ${current.spoken()}; change to ${next.spoken()}"
}

@Composable
internal fun BooleanSettingRow(
    label: String,
    enabled: Boolean,
    wide: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(label, wide) {
        val value = if (enabled) "Yes" else "No"
        // AI-INTENT: Boolean preferences keep fixed icon targets and expose their full value in semantics.
        // Authority: https://wiki.271.pics/Riff/LATEST/ui-foundation#boolean-preference-controls
        RiffIconButton(
            icon = if (enabled) "ic_yes" else "ic_no",
            label = "$label: $value; toggle",
            selected = enabled,
            wide = wide,
            onClick = { onChange(!enabled) },
            modifier = Modifier.size(settingsTarget(wide)),
        )
    }
}

@Composable
internal fun SettingsScreen(
    state: PlayerUiState,
    more: Boolean,
) {
    val draft = if (more) state.moreSettingsDraft ?: state.settings else state.settingsDraft ?: state.settings
    val saveEnabled = !state.storageMigrationInProgress && state.selectedStorageAvailable
    BoundedSelector(
        tag = if (more) "screen:SETTINGS_MORE" else "screen:SETTINGS",
        title = if (more) "More" else "Settings",
        items = if (more) moreSettingsItems() else settingsItems(),
        itemKey = { it.name },
        itemContentType = { "settings" },
        page = state.page,
        selectorIndex = state.selectorIndex,
        selectorOffset = state.selectorOffset,
        selectorKey = "settings",
        emptyMessage = "No settings available",
        // Shared line rhythm with Album/Folder selectors; only the row content differs.
        // AI-INTENT: Do not derive Settings height from its choice target or add vertical padding.
        rowHeightWide = SELECTOR_ROW_HEIGHT_WIDE_DP.dp,
        rowHeightNarrow = SELECTOR_ROW_HEIGHT_NARROW_DP.dp,
        background = Color(LocalRiffPalette.current.background),
        showNarrowPager = false,
        titleAfterLeadingCount = 1,
        leading = { canUp, canDown, pageSize, _ ->
            listOf(
                TopActionSpec(
                    "cancel",
                    if (more) "Back to settings" else "Cancel settings",
                    action = if (more) PlayerUiAction.CloseMoreSettings else PlayerUiAction.CancelSettings,
                ),
                TopActionSpec(
                    "arrowhead_up",
                    "Previous page",
                    canUp,
                    PlayerUiAction.PreviousPage(pageSize),
                ),
                TopActionSpec(
                    "arrowhead_down",
                    "Next page",
                    canDown,
                    PlayerUiAction.NextPage(pageSize),
                ),
            )
        },
        trailing =
            settingsOkAction(
                if (more) "Save more settings" else "Save settings",
                saveEnabled,
                if (more) PlayerUiAction.SaveMoreSettings else PlayerUiAction.SaveSettings,
            ),
    ) { item, _, _, wide ->
        SettingsRow(state, draft, item, wide)
    }
}

@Composable
internal fun SettingsRow(
    state: PlayerUiState,
    draft: PlayerSettingsUiState,
    item: SettingsItem,
    wide: Boolean,
) {
    val dispatch = LocalRiffDispatch.current
    when (item) {
        SettingsItem.MEDIA_FOLDERS -> {
            InteractiveSettingRow(
                heading = MEDIA_FOLDERS_HEADING,
                description = "Edit media folders",
                onClick = { dispatch(PlayerUiAction.OpenSafGrantSelector) },
            )
        }

        SettingsItem.USER -> {
            SettingRow("User", wide) {
                (1..3).forEachIndexed { index, profileId ->
                    if (index > 0) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                    RiffIconButton(
                        "digit_$profileId",
                        "User $profileId",
                        selected = draft.profileId == profileId,
                        wide = wide,
                        onClick = { dispatch(PlayerUiAction.SetDraftProfile(profileId)) },
                        modifier = Modifier.size(settingsTarget(wide)),
                    )
                }
            }
        }

        SettingsItem.MORE -> {
            InteractiveSettingRow(
                heading = "More…",
                description = "Open more settings",
                onClick = { dispatch(PlayerUiAction.OpenMoreSettings) },
            )
        }

        SettingsItem.DIAGONAL -> {
            SettingRow("Diagonal", wide) {
                val choices =
                    listOf(
                        Triple("scale_diagonal", "Diagonal", DiagonalChoice.DIAGONAL),
                        Triple("scale_card", "Card calibration", DiagonalChoice.CARD),
                        Triple("scale_auto", "Automatic", DiagonalChoice.AUTO),
                    )
                choices.forEachIndexed { index, (icon, label, diagonal) ->
                    if (index > 0) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                    RiffIconButton(
                        icon,
                        label,
                        selected = draft.diagonal == diagonal,
                        squareViewport = diagonal == DiagonalChoice.AUTO,
                        wide = wide,
                        onClick = {
                            dispatch(
                                when (diagonal) {
                                    DiagonalChoice.DIAGONAL -> PlayerUiAction.OpenScreenDiagonal
                                    DiagonalChoice.CARD -> PlayerUiAction.OpenCalibration
                                    DiagonalChoice.AUTO -> PlayerUiAction.SetDraftDiagonal(diagonal)
                                },
                            )
                        },
                        modifier = Modifier.size(settingsTarget(wide)),
                    )
                }
            }
        }

        SettingsItem.DATA_LOCATION -> {
            SettingRow("Save data on", wide, scrollChoices = true) {
                RiffText(
                    state.activeDataStorageLabel,
                    32f,
                    LocalRiffPalette.current.text,
                    medium = true,
                    modifier = Modifier.widthIn(min = 180.dp, max = 360.dp).fillMaxHeight(),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                RiffIconButton(
                    "settings",
                    "Change data storage",
                    enabled = state.selectedStorageAvailable,
                    wide = wide,
                    onClick = { dispatch(PlayerUiAction.OpenDataStorageSelector) },
                    modifier = Modifier.size(settingsTarget(wide)),
                )
            }
        }

        SettingsItem.LONG_PRESS -> {
            BooleanSettingRow("Long press starts playback", draft.longPressStartsPlayback, wide) {
                dispatch(PlayerUiAction.SetDraftLongPress(it))
            }
        }

        SettingsItem.AUTO_SEARCH -> {
            BooleanSettingRow("Auto search", draft.autoSearch, wide) {
                dispatch(PlayerUiAction.SetDraftAutoSearch(it))
            }
        }

        SettingsItem.IGNORE_LEADING_ARTICLES -> {
            BooleanSettingRow("Ignore leading articles", draft.ignoreLeadingArticles, wide) {
                dispatch(PlayerUiAction.SetDraftIgnoreLeadingArticles(it))
            }
        }

        SettingsItem.THEME -> {
            SettingRow("Theme", wide) {
                val scheme = draft.colorScheme
                val name = if (scheme == ColorScheme.SYSTEM) "Auto" else scheme.name.lowercase().replaceFirstChar { it.uppercase() }
                val next = scheme.next()
                val nextName = if (next == ColorScheme.SYSTEM) "Auto" else next.name.lowercase().replaceFirstChar { it.uppercase() }
                RiffIconButton(
                    if (scheme == ColorScheme.SYSTEM) "theme_auto" else "theme_${scheme.name.lowercase()}",
                    "Theme: $name; next $nextName",
                    selected = true,
                    wide = wide,
                    onClick = { dispatch(PlayerUiAction.SetDraftTheme(next)) },
                    modifier = Modifier.size(settingsTarget(wide)),
                )
            }
        }

        SettingsItem.DIM_BACKLIGHT -> {
            SettingRow("Dim Backlight", wide) {
                val mode = draft.dimBacklight
                val next = PlayerDimBacklightMode.entries[(mode.ordinal + 1) % PlayerDimBacklightMode.entries.size]
                val name = mode.name.lowercase().replaceFirstChar { it.uppercase() }
                val nextName = next.name.lowercase().replaceFirstChar { it.uppercase() }
                RiffIconButton(
                    if (mode == PlayerDimBacklightMode.OFF) "ban" else "theme_${mode.name.lowercase()}",
                    "Dim Backlight: $name; next $nextName",
                    selected = mode != PlayerDimBacklightMode.OFF,
                    appearanceEnabled = mode != PlayerDimBacklightMode.OFF,
                    wide = wide,
                    onClick = { dispatch(PlayerUiAction.SetDraftDimBacklight(next)) },
                    modifier = Modifier.size(settingsTarget(wide)),
                )
            }
        }

        SettingsItem.REPLAY_GAIN -> {
            SettingRow("ReplayGain", wide) {
                val nextMode =
                    when (draft.replayGainMode) {
                        ReplayGainMode.N -> ReplayGainMode.I
                        ReplayGainMode.I -> ReplayGainMode.F
                        ReplayGainMode.F -> ReplayGainMode.N
                    }
                val nextTarget =
                    when (draft.replayGainTarget) {
                        ReplayGainTarget.ZERO -> ReplayGainTarget.LUFS_16
                        ReplayGainTarget.LUFS_16 -> ReplayGainTarget.LUFS_18
                        ReplayGainTarget.LUFS_18 -> ReplayGainTarget.LUFS_23
                        ReplayGainTarget.LUFS_23 -> ReplayGainTarget.ZERO
                    }
                TextChoice(
                    draft.replayGainMode.name,
                    draft.replayGainMode != ReplayGainMode.N,
                    wide = wide,
                    contentDescription = "ReplayGain mode ${draft.replayGainMode.name}; change to ${nextMode.name}",
                ) {
                    dispatch(PlayerUiAction.SetDraftReplayGainMode(nextMode))
                }
                Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                val targetEnabled = draft.replayGainMode != ReplayGainMode.N
                TextChoice(
                    draft.replayGainTarget.displayValue.toString(),
                    selected = targetEnabled,
                    enabled = targetEnabled,
                    wide = wide,
                    contentDescription = replayGainTargetDescription(draft.replayGainTarget, nextTarget),
                ) {
                    dispatch(PlayerUiAction.SetDraftReplayGainTarget(nextTarget))
                }
            }
        }

        SettingsItem.REBUILD_DB -> {
            SettingRow("Rebuild DB", wide) {
                TextChoice("AU", DatabaseRebuild.AU in draft.pendingRebuild, wide = wide, toggleDescription = "Rebuild audio analysis") {
                    dispatch(PlayerUiAction.SetDraftRebuild(DatabaseRebuild.AU))
                }
                Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                TextChoice("IA", DatabaseRebuild.IA in draft.pendingRebuild, wide = wide, toggleDescription = "Rebuild Cuetree index") {
                    dispatch(PlayerUiAction.SetDraftRebuild(DatabaseRebuild.IA))
                }
            }
        }
    }
}

@Composable
internal fun ScreenDiagonalScreen(hostContext: Context) {
    val config = LocalRiffConfig.current
    val palette = LocalRiffPalette.current
    val dispatch = LocalRiffDispatch.current
    val choices = remember(config) { config.floatList("player.screenDiagonalChoicesInches") }
    var selected by remember { mutableStateOf<Float?>(null) }
    SafeContent(Color(palette.selectBackground), "screen:SCREEN_DIAGONAL") {
        Box(Modifier.fillMaxSize()) {
            val wide = LocalRiffConfig.current.wide
            Column(Modifier.fillMaxSize()) {
                RiffTopBar(
                    leading =
                        listOf(
                            TopActionSpec(
                                "cancel",
                                "Cancel screen diagonal",
                                action = PlayerUiAction.CancelScreenDiagonal,
                            ),
                        ),
                    title = "Screen Diagonal",
                    trailing =
                        TopActionSpec(
                            "ok",
                            "Accept screen diagonal",
                            enabled = selected != null,
                            onClick = {
                                selected?.let { diagonalInches ->
                                    val bounds =
                                        hostContext
                                            .getSystemService(WindowManager::class.java)
                                            .currentWindowMetrics
                                            .bounds
                                    val ppi =
                                        PhysicalDensityResolver.diagonalPpi(
                                            bounds.width(),
                                            bounds.height(),
                                            diagonalInches,
                                        )
                                    if (ppi.isFinite() && ppi > 0f) {
                                        dispatch(PlayerUiAction.AcceptScreenDiagonal(ppi))
                                    }
                                }
                            },
                        ),
                    wide = wide,
                )
                Spacer(Modifier.height(uiDimension("targetSpacingMin")))
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(uiDimension("targetSpacingMin")),
                ) {
                    choices.chunked(4).forEach { rowChoices ->
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(uiDimension("targetSpacingMin")),
                        ) {
                            rowChoices.forEach { diagonalInches ->
                                val label = "${formatDiagonalInches(diagonalInches)}″"
                                RiffControl(
                                    description =
                                        "$label; ${if (selected == diagonalInches) "selected" else "not selected"}",
                                    selected = selected == diagonalInches,
                                    onClick = { selected = diagonalInches },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    RiffText(
                                        label,
                                        if (wide) 32f else 24f,
                                        palette.text,
                                        medium = true,
                                        modifier = Modifier.fillMaxSize(),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatDiagonalInches(value: Float): String = if (value % 1f == 0f) value.roundToInt().toString() else value.toString()

@Composable
internal fun SettingRow(
    heading: String,
    wide: Boolean,
    scrollChoices: Boolean = false,
    choices: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        RiffText(
            heading,
            uiTextSize("mixedButtonMinFontSize"),
            LocalRiffPalette.current.text,
            medium = true,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            maxLines = 1,
        )
        val choiceModifier =
            if (scrollChoices) {
                val choiceScrollState = rememberScrollState()
                // The Settings contract requires a constrained strip to begin at logical END.
                LaunchedEffect(choiceScrollState.maxValue) {
                    choiceScrollState.scrollTo(choiceScrollState.maxValue)
                }
                Modifier
                    .weight(if (wide) 2f else 1.5f)
                    .fillMaxHeight()
                    .horizontalScroll(choiceScrollState)
            } else {
                Modifier.fillMaxHeight()
            }
        Row(
            choiceModifier,
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = choices,
        )
    }
}

@Composable
internal fun InteractiveSettingRow(
    heading: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    RiffControl(
        description = description,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
    ) {
        RiffText(
            heading,
            uiTextSize("mixedButtonMinFontSize"),
            if (enabled) LocalRiffPalette.current.text else LocalRiffPalette.current.muted,
            medium = true,
            modifier = Modifier.fillMaxSize(),
            maxLines = 1,
        )
    }
}

@Composable
internal fun TextChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    wide: Boolean,
    toggleDescription: String? = null,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val palette = LocalRiffPalette.current
    ChoiceTarget(
        description =
            "${contentDescription ?: toggleDescription ?: label}; ${if (selected) "selected" else "not selected"}" +
                if (enabled) "" else "; unavailable",
        selected = selected,
        checkbox = toggleDescription != null,
        enabled = enabled,
        wide = wide,
        onClick = onClick,
        modifier = Modifier.size(settingsTarget(wide)),
    ) {
        RiffText(
            label,
            uiTextSize("mixedButtonMinFontSize"),
            if (enabled) palette.text else palette.muted,
            medium = true,
            modifier = Modifier.fillMaxSize(),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
