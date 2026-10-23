@file:Suppress("FunctionName")

package link.roadio.riff.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import link.roadio.riff.player.core.selection.AiAxisRange
import link.roadio.riff.player.core.selection.AiHeatmap
import link.roadio.riff.player.core.selection.AiHeatmapRenderStyle
import link.roadio.riff.player.core.selection.AiPteBand
import link.roadio.riff.player.core.selection.aiSmptePlacement
import link.roadio.riff.player.core.selection.rasterizeAiHeatmap
import java.util.Locale

@Composable
internal fun AiScreen(
    state: PlayerUiState,
    night: Boolean,
) {
    val palette = LocalRiffPalette.current
    val config = LocalRiffConfig.current
    val dispatch = LocalRiffDispatch.current
    val effectiveSelection = state.effectiveAiSelection()
    val scheme = if (night) "night" else "day"
    val heatmapStyle =
        remember(config, scheme) {
            AiHeatmapRenderStyle(
                topRight = config.color("ai.heatmapColors.$scheme.topRight"),
                bottomRight = config.color("ai.heatmapColors.$scheme.bottomRight"),
                bottomLeft = config.color("ai.heatmapColors.$scheme.bottomLeft"),
                topLeft = config.color("ai.heatmapColors.$scheme.topLeft"),
                alphaMap = config.alphaByteList("ai.heatmapAlphaMap"),
            )
        }
    SafeContent(Color(palette.selectBackground), "screen:AI") {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = LocalRiffConfig.current.wide
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            val bodyHeight = availableHeight - TOP_BAR_DP.dp - TILE_GAP_DP.dp
            val canvasSide =
                if (wide) {
                    minOf(
                        bodyHeight,
                        availableWidth - AI_WIDE_MIN_CONTROLS_WIDTH_DP.dp,
                    ).coerceAtLeast(1.dp)
                } else {
                    availableWidth
                }
            val statusText =
                if (config.boolean("behavior.enableStatusLines")) {
                    formatAiStatusLine(
                        arousal = effectiveSelection.arousal,
                        valence = effectiveSelection.valence,
                        candidateCount =
                            state.aiHeatmap?.candidateCount(
                                effectiveSelection.valence,
                                effectiveSelection.arousal,
                            ) ?: 0,
                    )
                } else {
                    null
                }
            Column(Modifier.fillMaxSize()) {
                RiffTopBar(
                    leading =
                        listOf(
                            TopActionSpec(
                                "cancel",
                                "Cancel AI queue",
                                action = PlayerUiAction.OpenMain,
                            ),
                        ),
                    wide = wide,
                    titleContent = { AiTitlePlate() },
                )
                Spacer(Modifier.height(uiDimension("targetSpacingMin")))
                if (wide) {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        AiCanvasWithStatus(
                            selection = effectiveSelection,
                            heatmap = state.aiHeatmap,
                            enabled = state.aiDataReady,
                            style = heatmapStyle,
                            letterboxColor = palette.selectBackground,
                            statusText = statusText,
                            statusTextSp = config.float("behavior.statusLineTextSp"),
                            night = night,
                            onPointChanged = { arousal, valence ->
                                dispatch(PlayerUiAction.SetAiPoint(arousal, valence))
                            },
                            modifier = Modifier.size(canvasSide),
                        )
                        Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                        AiControls(
                            state = state,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        AiCanvasWithStatus(
                            selection = effectiveSelection,
                            heatmap = state.aiHeatmap,
                            enabled = state.aiDataReady,
                            style = heatmapStyle,
                            letterboxColor = palette.selectBackground,
                            statusText = statusText,
                            statusTextSp = config.float("behavior.statusLineTextSp"),
                            night = night,
                            onPointChanged = { arousal, valence ->
                                dispatch(PlayerUiAction.SetAiPoint(arousal, valence))
                            },
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Spacer(Modifier.height(uiDimension("targetSpacingMin")))
                        AiNarrowControls(
                            state = state,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// AI-INTENT: Keep the sole menu/title plate at the physical right edge across resize and RTL.
// Authority: https://wiki.271.pics/Riff/LATEST/ai-screen-scope-and-entry#2-stable-screen-actions
@Composable
internal fun AiTitlePlate() {
    val config = LocalRiffConfig.current
    val palette = LocalRiffPalette.current
    val dispatch = LocalRiffDispatch.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val family =
        when (config.string("typography.topBarFontFamily")) {
            "serif" -> FontFamily.Serif
            "monospace" -> FontFamily.Monospace
            else -> FontFamily.SansSerif
        }
    val measured =
        measurer.measure(
            AnnotatedString("AI"),
            TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight(config.int("typography.topBarFontWeight")),
                fontFamily = family,
            ),
            maxLines = 1,
            softWrap = false,
        )
    val width = with(density) { measured.size.width.toDp() + 30.dp }.coerceIn(uiDimension("navTarget"), 240.dp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            RiffControl(
                description = "AI actions",
                modifier = Modifier.width(width).height(uiDimension("navTarget")).testTag("ai:title-plate"),
                contentPadding = 15.dp,
                onClick = { dispatch(PlayerUiAction.OpenAiActions) },
            ) {
                RiffText(
                    "AI",
                    32f,
                    palette.text,
                    modifier = Modifier.fillMaxSize(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

internal fun formatAiStatusLine(
    arousal: Float,
    valence: Float,
    candidateCount: Int,
): String =
    String.format(
        Locale.ROOT,
        "A %.02f V %.02f C %2d",
        arousal,
        valence,
        candidateCount,
    )

@Composable
internal fun AiControls(
    state: PlayerUiState,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val geometry = aiWideControlGeometry(maxWidth.value, maxHeight.value, TILE_GAP_DP)
        Column(Modifier.fillMaxSize()) {
            AiPteChoices(state, geometry.choiceWidthDp.dp, geometry.controlHeightDp.dp)
            Spacer(Modifier.height(TILE_GAP_DP.dp))
            AiSizeChoices(state, geometry.choiceWidthDp.dp, geometry.controlHeightDp.dp)
            Spacer(Modifier.height(TILE_GAP_DP.dp))
            AiPlayControl(
                state = state,
                modifier = Modifier.fillMaxWidth().height(geometry.controlHeightDp.dp),
            )
        }
    }
}

@Composable
internal fun AiNarrowControls(
    state: PlayerUiState,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val split =
            canUseAiNarrowSplit(
                widthDp = maxWidth.value,
                heightDp = maxHeight.value,
                gapDp = TILE_GAP_DP,
                minimumChoiceDp = AI_CHOICE_MIN_DP,
                minimumPlayDp = AI_PLAY_MIN_DP,
            )
        if (split) {
            val geometry = aiNarrowControlGeometry(maxWidth.value, maxHeight.value, TILE_GAP_DP)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(geometry.choicesWidthDp.dp).fillMaxHeight()) {
                        AiPteChoices(state, geometry.choiceWidthDp.dp, geometry.choiceHeightDp.dp)
                        Spacer(Modifier.height(TILE_GAP_DP.dp))
                        AiSizeChoices(state, geometry.choiceWidthDp.dp, geometry.choiceHeightDp.dp)
                    }
                    Spacer(Modifier.width(TILE_GAP_DP.dp))
                    AiPlayControl(
                        state = state,
                        modifier = Modifier.width(geometry.playWidthDp.dp).fillMaxHeight(),
                    )
                }
            }
        } else {
            val target = minOf(uiDimension("navTarget"), (maxWidth - 48.dp) / 3).coerceAtLeast(64.dp)
            Column(Modifier.fillMaxWidth()) {
                AiPteChoices(state, target, target)
                Spacer(Modifier.height(uiDimension("targetSpacingMin")))
                AiSizeChoices(state, target, target)
                Spacer(Modifier.height(uiDimension("targetSpacingMin")))
                AiPlayControl(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(uiDimension("touchTargetMin")),
                )
            }
        }
    }
}

@Composable
internal fun AiPteChoices(
    state: PlayerUiState,
    choiceWidth: Dp,
    choiceHeight: Dp,
) {
    val dispatch = LocalRiffDispatch.current
    AiChoiceRow(Modifier.height(choiceHeight)) {
        AiPteBand.entries.forEachIndexed { index, pteBand ->
            if (index > 0) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
            val label = pteBand.name.lowercase().replaceFirstChar(Char::uppercase)
            val selected = pteBand in state.aiSelection.pteBands
            AiIconChoice(
                icon = pteBand.iconName,
                label = "$label perceived speed",
                selected = selected,
                enabled = state.aiSelection.isPteBandEnabled(pteBand, state.aiPteAvailable),
                modifier = Modifier.width(choiceWidth).fillMaxHeight(),
                onClick = { dispatch(PlayerUiAction.ToggleAiPteBand(pteBand)) },
            )
        }
    }
}

internal val AiPteBand.iconName: String
    get() =
        when (this) {
            AiPteBand.SLOW -> "tempo_slow"
            AiPteBand.MEDIUM -> "tempo_normal"
            AiPteBand.FAST -> "tempo_fast"
        }

@Composable
internal fun AiSizeChoices(
    state: PlayerUiState,
    choiceWidth: Dp,
    choiceHeight: Dp,
) {
    val dispatch = LocalRiffDispatch.current
    AiChoiceRow(Modifier.height(choiceHeight)) {
        listOf(10, 20, 50).forEachIndexed { index, queueSize ->
            if (index > 0) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
            AiIconChoice(
                icon = "scale_eur_$queueSize",
                label = "$queueSize track queue",
                selected = state.aiSelection.size == queueSize,
                modifier = Modifier.width(choiceWidth).fillMaxHeight(),
                onClick = { dispatch(PlayerUiAction.SetAiSize(queueSize)) },
            )
        }
    }
}

@Composable
internal fun AiPlayControl(
    state: PlayerUiState,
    modifier: Modifier,
) {
    val palette = LocalRiffPalette.current
    val dispatch = LocalRiffDispatch.current
    val enabled = state.aiPlayEnabled
    RiffControl(
        description = "Play AI queue",
        enabled = enabled,
        onClick = { dispatch(PlayerUiAction.CreateAiQueue) },
        modifier = modifier,
    ) {
        RiffIcon(
            iconDrawableName("play"),
            Color(if (enabled) palette.text else palette.muted),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun AiChoiceRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun AiIconChoice(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalRiffPalette.current
    RiffControl(
        description = "$label; ${if (selected) "selected" else "not selected"}",
        enabled = enabled,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    ) {
        RiffIcon(
            iconDrawableName(icon),
            Color(if (enabled) palette.text else palette.muted),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun AiCanvasWithStatus(
    selection: AiSelectionUiState,
    heatmap: AiHeatmap?,
    enabled: Boolean,
    style: AiHeatmapRenderStyle,
    letterboxColor: Int,
    statusText: String?,
    statusTextSp: Float,
    night: Boolean,
    onPointChanged: (Float, Float) -> Unit,
    modifier: Modifier,
) {
    Box(modifier) {
        AvCanvas(
            selection = selection,
            heatmap = heatmap,
            enabled = enabled,
            style = style,
            letterboxColor = letterboxColor,
            onPointChanged = onPointChanged,
            modifier = Modifier.fillMaxSize(),
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics { },
            ) {
                AiCanvasText(
                    value = "A",
                    sizeSp = statusTextSp,
                    night = night,
                    textAlign = TextAlign.Left,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .absolutePadding(
                                left = AI_CANVAS_TEXT_INSET_DP.dp,
                                top = AI_CANVAS_TEXT_INSET_DP.dp,
                            ),
                )
                statusText?.let { value ->
                    AiCanvasText(
                        value = value,
                        sizeSp = statusTextSp,
                        night = night,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .absolutePadding(
                                    left = AI_CANVAS_TEXT_INSET_DP.dp,
                                    top = AI_CANVAS_TEXT_INSET_DP.dp,
                                    right = AI_CANVAS_TEXT_INSET_DP.dp,
                                ),
                    )
                }
                AiCanvasText(
                    value = "V",
                    sizeSp = statusTextSp,
                    night = night,
                    textAlign = TextAlign.Right,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .absolutePadding(
                                right = AI_CANVAS_TEXT_INSET_DP.dp,
                                bottom = AI_CANVAS_TEXT_INSET_DP.dp,
                            ),
                )
                AiCanvasText(
                    value = AI_CANVAS_ADVISORY,
                    sizeSp = statusTextSp,
                    night = night,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .absolutePadding(
                                left = AI_CANVAS_TEXT_INSET_DP.dp,
                                right = AI_CANVAS_TEXT_INSET_DP.dp,
                                bottom = AI_CANVAS_TEXT_INSET_DP.dp,
                            ),
                )
            }
        }
    }
}

@Composable
internal fun AiCanvasText(
    value: String,
    sizeSp: Float,
    night: Boolean,
    textAlign: TextAlign,
    modifier: Modifier,
) {
    GlyphBackdropText(
        value = value,
        style =
            TextStyle(
                color = if (night) Color.White else Color.Black,
                fontSize = sizeSp.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = textAlign,
            ),
        modifier = modifier,
        backdropColor = if (night) Color.Black else Color.White,
        maxLines = 1,
        softWrap = false,
    )
}

internal data class RenderedAiHeatmap(
    val heatmap: AiHeatmap,
    val image: ImageBitmap,
    val width: Int,
    val height: Int,
    val rtl: Boolean,
    val style: AiHeatmapRenderStyle,
)

@Composable
internal fun AvCanvas(
    selection: AiSelectionUiState,
    heatmap: AiHeatmap?,
    enabled: Boolean,
    style: AiHeatmapRenderStyle,
    letterboxColor: Int,
    onPointChanged: (Float, Float) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl
    val shape = RoundedCornerShape(16.dp)
    val palette = LocalRiffPalette.current
    val config = LocalRiffConfig.current
    var focused by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var renderedHeatmap by remember { mutableStateOf<RenderedAiHeatmap?>(null) }
    val readyHeatmap =
        renderedHeatmap?.takeIf { rendered ->
            rendered.width == canvasSize.width &&
                rendered.height == canvasSize.height &&
                rendered.rtl == rtl &&
                rendered.style === style
        }
    val arousalRange = readyHeatmap?.heatmap?.arousalRange ?: AiAxisRange.FULL
    val valenceRange = readyHeatmap?.heatmap?.valenceRange ?: AiAxisRange.FULL
    val arousalStep = arousalRange.span / 10f
    val valenceStep = valenceRange.span / 10f
    val smpteBars =
        remember(context) {
            UiDrawableRegistry.drawable(context, "ic_smpte_bars").apply { setBounds(0, 0, 480, 360) }
        }
    LaunchedEffect(heatmap, canvasSize, rtl, style) {
        if (heatmap == null) {
            renderedHeatmap = null
        } else if (canvasSize.width > 0 && canvasSize.height > 0) {
            val rendered =
                withContext(Dispatchers.Default) {
                    val raster = rasterizeAiHeatmap(heatmap, canvasSize.width, canvasSize.height, rtl, style)
                    val image =
                        Bitmap
                            .createBitmap(raster.width, raster.height, Bitmap.Config.ARGB_8888)
                            .apply {
                                setPixels(raster.pixels, 0, raster.width, 0, 0, raster.width, raster.height)
                            }.asImageBitmap()
                    RenderedAiHeatmap(
                        heatmap = heatmap,
                        image = image,
                        width = canvasSize.width,
                        height = canvasSize.height,
                        rtl = rtl,
                        style = style,
                    )
                }
            renderedHeatmap = rendered
        }
    }
    Box(
        modifier
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription =
                    "Arousal ${selection.arousal.asAiValue()}; " +
                    "Valence ${selection.valence.asAiValue()}; $AI_CANVAS_ADVISORY"
                if (enabled) {
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Increase Arousal") {
                                onPointChanged(
                                    (selection.arousal + arousalStep).coerceAtMost(arousalRange.maximum),
                                    selection.valence,
                                )
                                true
                            },
                            CustomAccessibilityAction("Decrease Arousal") {
                                onPointChanged(
                                    (selection.arousal - arousalStep).coerceAtLeast(arousalRange.minimum),
                                    selection.valence,
                                )
                                true
                            },
                            CustomAccessibilityAction("Increase Valence") {
                                onPointChanged(
                                    selection.arousal,
                                    (selection.valence + valenceStep).coerceAtMost(valenceRange.maximum),
                                )
                                true
                            },
                            CustomAccessibilityAction("Decrease Valence") {
                                onPointChanged(
                                    selection.arousal,
                                    (selection.valence - valenceStep).coerceAtLeast(valenceRange.minimum),
                                )
                                true
                            },
                        )
                } else {
                    disabled()
                    customActions = emptyList()
                }
            }.focusable(enabled),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(enabled, rtl, arousalRange, valenceRange) {
                    if (enabled) {
                        detectTapGestures { offset ->
                            val point =
                                aiPointFromCanvas(
                                    offset.x,
                                    offset.y,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                    rtl,
                                    arousalRange,
                                    valenceRange,
                                )
                            onPointChanged(point.arousal, point.valence)
                        }
                    }
                }.pointerInput(enabled, rtl, arousalRange, valenceRange) {
                    if (enabled) {
                        fun update(offset: Offset) {
                            val point =
                                aiPointFromCanvas(
                                    offset.x,
                                    offset.y,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                    rtl,
                                    arousalRange,
                                    valenceRange,
                                )
                            onPointChanged(point.arousal, point.valence)
                        }
                        detectDragGestures(
                            onDragStart = ::update,
                            onDrag = { change, _ ->
                                change.consume()
                                update(change.position)
                            },
                        )
                    }
                },
        ) {
            drawRect(Color(letterboxColor))
            val readyImage = readyHeatmap?.image
            if (readyImage == null) {
                val placement = aiSmptePlacement(size.width, size.height)
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    val save = nativeCanvas.save()
                    nativeCanvas.translate(placement.left, placement.top)
                    nativeCanvas.scale(placement.width / 480f, placement.height / 360f)
                    smpteBars.draw(nativeCanvas)
                    nativeCanvas.restoreToCount(save)
                }
                return@Canvas
            }
            drawImage(readyImage)
        }
        if (readyHeatmap != null) {
            Canvas(Modifier.matchParentSize().contrastHalo(if (config.night) Color.Black else Color.White)) {
                val canvasPoint =
                    aiPointToCanvas(
                        point = AiPoint(selection.arousal, selection.valence),
                        width = size.width,
                        height = size.height,
                        rtl = rtl,
                        arousalRange = requireNotNull(readyHeatmap).heatmap.arousalRange,
                        valenceRange = readyHeatmap.heatmap.valenceRange,
                    )
                val markerX = canvasPoint.x
                val markerY = canvasPoint.y
                val marker = Offset(markerX, markerY)
                val markerColor = Color(palette.primary)
                drawLine(markerColor, Offset(markerX, 0f), Offset(markerX, size.height), 2f)
                drawLine(markerColor, Offset(0f, markerY), Offset(size.width, markerY), 2f)
                drawCircle(markerColor, radius = 17f, center = marker, style = Stroke(width = 3f))
            }
        }
        RiffStateOutline(
            Modifier.matchParentSize(),
            Color(if (focused && enabled) palette.focusRing else palette.outline),
            shape,
            if (focused && enabled) 3.dp else 2.dp,
            surround = true,
        )
    }
}

@Composable
internal fun AiActionsScreen(state: PlayerUiState) {
    val palette = LocalRiffPalette.current
    val dispatch = LocalRiffDispatch.current
    SafeContent(Color(palette.selectBackground), "screen:AI_ACTIONS") {
        Box(Modifier.fillMaxSize()) {
            val wide = LocalRiffConfig.current.wide
            Column(Modifier.fillMaxSize()) {
                RiffTopBar(
                    leading =
                        listOf(
                            TopActionSpec(
                                "cancel",
                                "Back to AI selection",
                                action = PlayerUiAction.CloseAiActions,
                            ),
                        ),
                    title = "AI actions",
                    wide = wide,
                )
                listOf(
                    Triple("Play", "play", PlayerUiAction.CreateAiQueue),
                    Triple("Add to playlist…", "plus", PlayerUiAction.OpenAiPlaylistSelector),
                ).forEachIndexed { index, (label, icon, action) ->
                    if (index > 0) Spacer(Modifier.height(ROW_GAP_DP.dp))
                    val playlistAction = action == PlayerUiAction.OpenAiPlaylistSelector
                    val playlistAllowed = state.playlistMutationsAllowed && !state.playlistMutationInProgress
                    val enabled =
                        state.selectedStorageAvailable &&
                            (action != PlayerUiAction.CreateAiQueue || state.aiDataReady) &&
                            (!playlistAction || playlistAllowed)
                    RiffControl(
                        description = label,
                        enabled = enabled,
                        onClick = { dispatch(action) },
                        modifier = Modifier.fillMaxWidth().height(uiDimension("ratingTarget")),
                    ) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            RiffIcon(
                                iconDrawableName(icon),
                                Color(palette.text),
                                modifier = Modifier.size(uiDimension("ratingTarget")),
                            )
                            RiffText(
                                label,
                                uiTextSize("textTitleList"),
                                palette.text,
                                medium = true,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

internal fun Float.asAiValue(): String = String.format(Locale.ROOT, "%.2f", this)
