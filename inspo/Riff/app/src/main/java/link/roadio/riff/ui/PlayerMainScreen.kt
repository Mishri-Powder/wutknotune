@file:Suppress("FunctionName")

package link.roadio.riff.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import link.roadio.riff.player.core.settings.SettingsEntry

@Composable
internal fun MainScreen(
    state: PlayerUiState,
    releaseIdentity: ReleaseIdentity,
) {
    val palette = LocalRiffPalette.current
    val config = LocalRiffConfig.current
    val dispatch = LocalRiffDispatch.current
    SafeContent(
        background = Color(palette.selectBackground),
        tag = "screen:MAIN",
        onBackgroundTap = { dispatch(PlayerUiAction.TogglePlaybackSurface) },
        underlay = { RiffStatusOverlay(mainStatusLine(state)) },
    ) {
        Box(Modifier.fillMaxSize()) {
            val wide = LocalRiffConfig.current.wide
            val pattern = if (wide) listOf(4, 3) else listOf(2, 2, 2, 1)
            Column(Modifier.fillMaxSize()) {
                RiffTopBar(
                    leading =
                        listOf(
                            TopActionSpec(
                                "flip",
                                "Switch to playback",
                                action = PlayerUiAction.TogglePlaybackSurface,
                            ),
                        ),
                    trailing =
                        TopActionSpec(
                            "settings",
                            when (state.settingsEntry) {
                                SettingsEntry.LONG_PRESS -> "Settings; hold to open"
                                SettingsEntry.RECOVERY_HOLD -> "Settings; motion input unavailable; hold to open"
                                SettingsEntry.BLOCKED -> "Settings unavailable while restricted"
                                SettingsEntry.PRESS -> "Settings"
                            },
                            enabled = state.settingsEntry != SettingsEntry.BLOCKED,
                            action = PlayerUiAction.OpenSettings,
                            enabledAppearance = state.settingsEntry != SettingsEntry.RECOVERY_HOLD,
                            onLongClick =
                                when (state.settingsEntry) {
                                    SettingsEntry.LONG_PRESS -> PlayerUiAction.OpenSettingsLongPress
                                    SettingsEntry.RECOVERY_HOLD -> PlayerUiAction.OpenSettingsRecovery
                                    else -> null
                                },
                            onLongClickLabel =
                                if (state.settingsEntry == SettingsEntry.RECOVERY_HOLD) {
                                    "Hold to open Settings with unavailable motion input"
                                } else {
                                    "Hold to open Settings"
                                },
                        ),
                    wide = wide,
                )
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("main:grid-viewport")) {
                    // Fit the measured body, so resize, insets and skin chrome share one geometry path.
                    val gap = uiDimension("targetSpacingMin")
                    val viewportWidth = maxWidth
                    val density = LocalDensity.current
                    val tileSide =
                        MainGridGeometry
                            .tileSideDp(
                                viewportWidthDp = maxWidth.value,
                                viewportHeightDp = maxHeight.value,
                                columnCount = pattern.max(),
                                rowCount = pattern.size,
                                requestedTileDp = config.float("geometry.mainCategoryTileSide"),
                                gapDp = gap.value,
                                density = density.density,
                            ).dp
                    // A fitting grid has no scroll gesture; the 128dp floor alone permits overflow.
                    val verticalOverflow =
                        with(density) {
                            tileSide.roundToPx() * pattern.size + gap.roundToPx() * (pattern.size - 1) > constraints.maxHeight
                        }
                    val gridScroll = if (verticalOverflow) Modifier.verticalScroll(rememberScrollState()) else Modifier
                    Column(
                        Modifier.fillMaxSize().then(gridScroll),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        var index = 0
                        pattern.forEachIndexed { rowIndex, count ->
                            val horizontalOverflow = tileSide * count + gap * (count - 1) > viewportWidth
                            val rowScroll = if (horizontalOverflow) Modifier.horizontalScroll(rememberScrollState()) else Modifier
                            Row(
                                Modifier.fillMaxWidth().then(rowScroll),
                                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                            ) {
                                repeat(count) {
                                    state.root.children.getOrNull(index++)?.let { item ->
                                        CategoryTile(item, tileSide, state.contentMode, state.aiDataReady)
                                    }
                                }
                            }
                            if (rowIndex != pattern.lastIndex) Spacer(Modifier.height(gap))
                        }
                    }
                }
            }
            releaseIdentity.announcementLabel?.let { label -> ReleaseAnnouncement(label) }
        }
    }
}

@Composable
internal fun ReleaseAnnouncement(label: String) {
    val palette = LocalRiffPalette.current
    val density = LocalDensity.current
    val textSizePx = with(density) { 52.sp.toPx() }
    val textColor = Color(palette.text).copy(alpha = 0.32f).toArgb()
    val paint =
        remember(textSizePx, textColor) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textAlign = Paint.Align.CENTER
                textSize = textSizePx
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
        }
    Canvas(Modifier.fillMaxSize()) {
        val bandHeight = size.minDimension * 0.18f
        rotate(degrees = -25f, pivot = center) {
            drawRect(
                color = Color(palette.text).copy(alpha = 0.08f),
                topLeft = Offset(-size.width * 0.2f, center.y - bandHeight / 2f),
                size = Size(size.width * 1.4f, bandHeight),
            )
            drawIntoCanvas { canvas ->
                val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
                canvas.nativeCanvas.drawText(label, center.x, baseline, paint)
            }
        }
    }
}

@Composable
internal fun CategoryTile(
    item: BrowserItem,
    side: Dp,
    contentMode: ContentMode,
    aiDataReady: Boolean,
) {
    val palette = LocalRiffPalette.current
    val dispatch = LocalRiffDispatch.current
    val disabled =
        !item.enabled ||
            (
                item.id == "category:ai" &&
                    !aiDataReady
            ) ||
            (
                contentMode == ContentMode.AUDIOBOOK &&
                    item.id == "category:ai"
            )
    val selected = item.id == "category:books" && contentMode == ContentMode.AUDIOBOOK
    val opacity = if (disabled) LocalRiffConfig.current.int("behavior.azDisabledAlpha") / 255f else 1f
    val label =
        HierarchyRoot.entries
            .firstOrNull { root -> root.categoryId == item.id }
            ?.labelFor(contentMode)
            ?: item.label
    val description = if (item.id == "category:ai") "AI mood selection" else label
    RiffControl(
        description = description,
        enabled = !disabled,
        selected = selected,
        onClick = {
            dispatch(
                if (item.id == "category:books") {
                    PlayerUiAction.ToggleContentMode
                } else {
                    PlayerUiAction.OpenCategory(item.id)
                },
            )
        },
        modifier = Modifier.requiredSize(side).alpha(opacity),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RiffIcon(
                drawableName = iconDrawableName(requireNotNull(item.icon)),
                tint = Color(palette.text),
                modifier =
                    Modifier.size(
                        minOf(
                            LocalRiffConfig.current.float("geometry.mainCategoryIconSide").dp,
                            side -
                                MAIN_LABEL_HEIGHT_DP.dp -
                                (BUTTON_CONTENT_PADDING_DP * 2f).dp,
                        ),
                    ),
            )
            RiffText(
                label,
                uiTextSize("mixedButtonMinFontSize"),
                palette.text,
                medium = true,
                modifier = Modifier.fillMaxWidth().height(MAIN_LABEL_HEIGHT_DP.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun RightAlignedStrip(content: @Composable RowScope.() -> Unit) {
    val direction = LocalLayoutDirection.current
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(uiDimension("navTarget"))) {
        val minimumWidth = maxWidth
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.horizontalScroll(scroll).widthIn(min = minimumWidth).height(uiDimension("navTarget")),
                horizontalArrangement = Arrangement.Absolute.Right,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) { content() }
            }
        }
    }
}
