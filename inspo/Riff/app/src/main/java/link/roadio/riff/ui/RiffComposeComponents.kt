@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalRiffConfig =
    staticCompositionLocalOf<UiConfig> { error("Riff UiConfig is not installed") }
internal val LocalRiffChromeText = staticCompositionLocalOf { false }
internal val LocalRiffPalette =
    staticCompositionLocalOf<UiPalette> { error("Riff palette is not installed") }
internal val LocalRiffDispatch =
    staticCompositionLocalOf<(PlayerUiAction) -> Unit> { error("Riff action sink is not installed") }
internal val LocalBackgroundFlipActive = staticCompositionLocalOf { false }

internal data class TopActionSpec(
    val icon: String,
    val label: String,
    val enabled: Boolean = true,
    val action: PlayerUiAction? = null,
    val onClick: (() -> Unit)? = null,
    val enabledAppearance: Boolean? = null,
    val onLongClick: PlayerUiAction? = null,
    val onLongClickLabel: String? = null,
)

@Composable
internal fun RiffTopBar(
    leading: List<TopActionSpec>,
    title: String? = null,
    trailing: TopActionSpec? = null,
    wide: Boolean,
    titleAfterLeadingCount: Int? = null,
    titleContent: (@Composable (Boolean) -> Unit)? = null,
) {
    CompositionLocalProvider(LocalRiffChromeText provides true) {
        Box(Modifier.fillMaxWidth().height(uiDimension("topBarHeight")), contentAlignment = Alignment.CenterStart) {
            RiffTopBarContent(leading, title, trailing, wide, titleAfterLeadingCount, titleContent)
        }
    }
}

@Composable
internal fun RiffTopBarContent(
    leading: List<TopActionSpec>,
    title: String?,
    trailing: TopActionSpec?,
    wide: Boolean,
    titleAfterLeadingCount: Int?,
    titleContent: (@Composable (Boolean) -> Unit)?,
) {
    val palette = LocalRiffPalette.current
    if (titleContent != null) {
        val direction = LocalLayoutDirection.current
        BoxWithConstraints(Modifier.fillMaxWidth().height(uiDimension("navTarget")).testTag("az:browser-chrome")) {
            val slot = uiDimension("navTarget") + uiDimension("targetSpacingMin")
            val minimumWidth = slot * leading.size + uiDimension("navTarget") + if (trailing != null) slot else 0.dp
            val stripModifier =
                if (maxWidth < minimumWidth) {
                    Modifier.horizontalScroll(rememberScrollState()).width(minimumWidth)
                } else {
                    Modifier.fillMaxWidth()
                }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(stripModifier.height(uiDimension("navTarget")), verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        leading.forEachIndexed { index, spec ->
                            if (index > 0) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                            TopAction(spec, wide)
                        }
                        if (leading.isNotEmpty()) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                        Box(Modifier.weight(1f).height(uiDimension("navTarget")).testTag("az:browser-plates")) { titleContent(true) }
                        trailing?.let {
                            Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                            TopAction(it, wide)
                        }
                    }
                }
            }
        }
        return
    }
    val split = titleAfterLeadingCount?.coerceIn(0, leading.size)
    val before = split?.let(leading::take) ?: leading
    val after = split?.let(leading::drop).orEmpty()
    if (!wide) {
        Row(
            Modifier.fillMaxWidth().height(TOP_BAR_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            before.forEachIndexed { index, spec ->
                if (index > 0) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                TopAction(spec, false)
            }
            if (before.isNotEmpty() && (title != null || after.isNotEmpty())) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
            // Keep edge actions pinned; only the title/page region may overflow.
            Row(
                Modifier.weight(1f).fillMaxHeight().then(
                    if (title != null || after.isNotEmpty()) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title?.let {
                    RiffText(
                        it,
                        uiTextSize("textTitleList"),
                        palette.text,
                        medium = true,
                        modifier = Modifier.width(220.dp).fillMaxHeight(),
                        maxLines = 1,
                    )
                }
                after.forEach { spec ->
                    Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                    TopAction(spec, false)
                }
            }
            trailing?.let {
                Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                TopAction(it, false)
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().height(TOP_BAR_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        before.forEachIndexed { index, spec ->
            if (index > 0) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
            TopAction(spec, wide)
        }
        if (split == null) {
            if (title != null) {
                RiffText(
                    title,
                    uiTextSize("textTitleList"),
                    palette.text,
                    medium = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        } else {
            if (before.isNotEmpty()) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
            title?.let {
                RiffText(
                    it,
                    uiTextSize("textTitleList"),
                    palette.text,
                    medium = true,
                    // Keep later page actions measurable: fillMaxWidth would consume their slots.
                    modifier = Modifier.width(220.dp).fillMaxHeight(),
                    maxLines = 1,
                )
            }
            after.forEach { spec ->
                Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                TopAction(spec, wide)
            }
            Spacer(Modifier.weight(1f))
        }
        trailing?.let { TopAction(it, wide) }
    }
}

@Composable
internal fun TopAction(
    spec: TopActionSpec,
    wide: Boolean,
) {
    val dispatch = LocalRiffDispatch.current
    RiffIconButton(
        spec.icon,
        spec.label,
        enabled = spec.enabled,
        appearanceEnabled = spec.enabledAppearance,
        wide = wide,
        contentPadding = BREADCRUMB_BUTTON_CONTENT_PADDING_DP.dp,
        onClick = { spec.onClick?.invoke() ?: spec.action?.let(dispatch) },
        onLongClick = spec.onLongClick?.let { action -> { dispatch(action) } },
        onLongClickLabel = spec.onLongClickLabel,
        modifier = Modifier.size(uiDimension("navTarget")),
    )
}

// The full allocation is the click/focus target; glyph padding must not shrink it.
// AI-INTENT: Keep padding outside the vector viewport so hit/focus geometry never changes.
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun RiffIconButton(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    appearanceEnabled: Boolean? = null,
    artworkBackdrop: Boolean = false,
    squareViewport: Boolean = true,
    contentPadding: Dp = BUTTON_CONTENT_PADDING_DP.dp,
    wide: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    text: String? = null,
) {
    val config = LocalRiffConfig.current
    val palette = LocalRiffPalette.current
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val tint =
        when {
            appearanceEnabled == false -> Color(palette.inactiveIcon)
            appearanceEnabled == null && !enabled -> Color(palette.disabledIcon)
            focused -> Color(palette.onPrimary)
            pressed || selected -> Color(palette.primary)
            else -> Color(palette.text)
        }
    val shape = RoundedCornerShape(12.dp)
    val description =
        "$label${if (selected) "; selected" else ""}${if (enabled) "" else "; unavailable"}"
    val disabledTapBlocker = disabledBackgroundFlipBlocker(enabled)
    Box(
        modifier
            .semantics {
                contentDescription = description
                role = Role.Button
                this.selected = selected
                if (!enabled) disabled()
            }.onFocusChanged { focused = it.isFocused }
            .then(disabledTapBlocker)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClickLabel = onLongClickLabel,
                onLongClick =
                    onLongClick?.let { action ->
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            action()
                        }
                    },
            ).focusable(enabled, interaction),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled && (focused || pressed)) {
            RiffStateOutline(
                Modifier.matchParentSize(),
                Color(palette.focusRing),
                shape,
                3.dp,
                surround = artworkBackdrop || LocalRiffUncontrolledBackground.current,
            )
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            val viewportSide = minOf(maxWidth, maxHeight)
            if (selected) {
                SelectedControlCircle(
                    Modifier.size(viewportSide),
                    enabled = enabled,
                    surround = artworkBackdrop || LocalRiffUncontrolledBackground.current,
                )
            }
            if (text != null) {
                RiffText(
                    text,
                    config.float("typography.mixedButtonMinFontSize"),
                    tint.toArgb(),
                    monospace = true,
                    bold = true,
                    fixedLabel = true,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    artworkBackdrop = artworkBackdrop,
                )
            } else {
                RiffIcon(
                    iconDrawableName(icon),
                    tint,
                    squareViewport = squareViewport,
                    modifier =
                        Modifier.fillMaxSize().contrastHalo(
                            if (artworkBackdrop || LocalRiffUncontrolledBackground.current) {
                                if (config.night) Color.Black else Color.White
                            } else {
                                null
                            },
                        ),
                )
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun ChoiceTarget(
    description: String,
    selected: Boolean,
    enabled: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    checkbox: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = LocalRiffPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val disabledTapBlocker = disabledBackgroundFlipBlocker(enabled)
    Box(
        modifier
            .semantics {
                contentDescription = description
                role = if (checkbox) Role.Checkbox else Role.Button
                if (checkbox) {
                    toggleableState =
                        if (selected) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
                } else {
                    this.selected = selected
                }
                if (!enabled) disabled()
            }.onFocusChanged { focused = it.isFocused }
            .then(disabledTapBlocker)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = if (checkbox) Role.Checkbox else Role.Button,
                onClick = onClick,
            ).focusable(enabled, interaction),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled && (focused || pressed)) {
            RiffStateOutline(Modifier.matchParentSize(), Color(palette.focusRing), shape, 3.dp)
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(BUTTON_CONTENT_PADDING_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                SelectedControlCircle(Modifier.size(minOf(maxWidth, maxHeight)), enabled = enabled)
            }
            content()
        }
    }
}

@Composable
internal fun RiffControl(
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    contentPadding: Dp = BUTTON_CONTENT_PADDING_DP.dp,
    content: @Composable () -> Unit,
) {
    val config = LocalRiffConfig.current
    val palette = LocalRiffPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val active = selected || pressed || focused
    val shape = RoundedCornerShape(config.float("geometry.cornerRadius").dp)
    val background =
        Color(if (active) palette.selected else palette.surface)
            .copy(alpha = config.int("shape.panelAlpha") / 255f)
    val borderWidth =
        when {
            focused || pressed -> 3.dp
            selected -> config.float("geometry.nowPlayingBorder").dp
            else -> config.float("shape.panelStrokeDp").dp
        }
    val haptic = LocalHapticFeedback.current
    val disabledTapBlocker = disabledBackgroundFlipBlocker(enabled)
    val clickModifier =
        if (onLongClick != null) {
            Modifier.combinedClickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClickLabel = onLongClickLabel,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
        } else {
            Modifier.clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
        }
    Box(
        modifier
            .semantics {
                contentDescription = description
                role = Role.Button
                this.selected = selected
                if (!enabled) disabled()
            }.onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .then(disabledTapBlocker)
            .then(clickModifier)
            .focusable(enabled, interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalRiffControlEnabled provides enabled) { content() }
        }
        if (enabled && borderWidth > 0.dp) {
            RiffStateOutline(
                Modifier.matchParentSize(),
                Color(if (focused || pressed) palette.focusRing else palette.outline),
                shape,
                borderWidth,
            )
        }
    }
}

internal fun Modifier.backgroundFlipTap(onTap: () -> Unit): Modifier =
    pointerInput(onTap) {
        val touchSlopPx = viewConfiguration.touchSlop
        val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down =
                awaitFirstDown(
                    requireUnconsumed = true,
                    pass = PointerEventPass.Final,
                )
            var cancelled = false
            var multiplePointers = false
            var maximumDistancePx = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (event.changes.count { it.pressed } > 1) multiplePointers = true
                if (event.changes.any { it.isConsumed }) cancelled = true
                if (change == null) {
                    cancelled = true
                } else {
                    maximumDistancePx =
                        maxOf(
                            maximumDistancePx,
                            (change.position - down.position).getDistance(),
                        )
                    if (
                        change.position.x !in 0f..size.width.toFloat() ||
                        change.position.y !in 0f..size.height.toFloat()
                    ) {
                        cancelled = true
                    }
                    if (change.changedToUpIgnoreConsumed()) {
                        if (
                            backgroundFlipTapEligible(
                                cancelled = cancelled,
                                multiplePointers = multiplePointers,
                                maximumDistancePx = maximumDistancePx,
                                elapsedMillis = change.uptimeMillis - down.uptimeMillis,
                                touchSlopPx = touchSlopPx,
                                longPressTimeoutMillis = longPressTimeoutMillis,
                            )
                        ) {
                            onTap()
                        }
                        break
                    }
                }
                if (event.changes.none { it.pressed }) break
            }
        }
    }

@Composable
internal fun disabledBackgroundFlipBlocker(enabled: Boolean): Modifier =
    if (enabled || !LocalBackgroundFlipActive.current) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { },
                onTap = { },
            )
        }
    }

internal fun backgroundFlipTapEligible(
    cancelled: Boolean,
    multiplePointers: Boolean,
    maximumDistancePx: Float,
    elapsedMillis: Long,
    touchSlopPx: Float,
    longPressTimeoutMillis: Long,
): Boolean =
    !cancelled &&
        !multiplePointers &&
        maximumDistancePx <= touchSlopPx &&
        elapsedMillis < longPressTimeoutMillis

internal const val BREADCRUMB_TEXT_SP = 32f

@Composable
internal fun riffTextStyle(
    sizeSp: Float,
    color: Int,
    medium: Boolean = false,
    monospace: Boolean = false,
    chrome: Boolean = LocalRiffChromeText.current,
    textAlign: TextAlign = TextAlign.Start,
    bold: Boolean = false,
): TextStyle {
    val config = LocalRiffConfig.current
    val fontFamily =
        when {
            monospace -> {
                FontFamily.Monospace
            }

            !chrome -> {
                FontFamily.Default
            }

            else -> {
                when (config.string("typography.topBarFontFamily")) {
                    "serif" -> FontFamily.Serif
                    "monospace" -> FontFamily.Monospace
                    else -> FontFamily.SansSerif
                }
            }
        }
    val fontWeight =
        if (bold) {
            FontWeight.Bold
        } else if (chrome && !monospace) {
            FontWeight(config.int("typography.topBarFontWeight"))
        } else if (medium) {
            FontWeight.Medium
        } else {
            FontWeight.Normal
        }
    return TextStyle(
        color = Color(if (LocalRiffControlEnabled.current) color else LocalRiffPalette.current.disabledIcon),
        fontSize = sizeSp.sp,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
    )
}

@Composable
internal fun RiffText(
    value: String,
    sizeSp: Float,
    color: Int,
    modifier: Modifier = Modifier,
    medium: Boolean = false,
    monospace: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    artworkBackdrop: Boolean = false,
    bold: Boolean = false,
    fixedLabel: Boolean = false,
) {
    val config = LocalRiffConfig.current
    val limit = config.int("behavior.maxStringLength")
    val displayed = remember(value, limit, fixedLabel) { if (fixedLabel) value else boundedUiText(value, limit) }
    val alignment =
        when (textAlign) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    Box(modifier, contentAlignment = alignment) {
        GlyphBackdropText(
            value = displayed,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = value },
            style =
                riffTextStyle(sizeSp, color, medium, monospace, textAlign = textAlign, bold = bold),
            maxLines = maxLines,
            softWrap = softWrap,
            backdropColor =
                if (artworkBackdrop || LocalRiffUncontrolledBackground.current) {
                    if (config.night) Color.Black else Color.White
                } else {
                    null
                },
        )
    }
}

@Composable
internal fun RiffIcon(
    drawableName: String,
    tint: Color,
    modifier: Modifier = Modifier,
    squareViewport: Boolean = true,
) {
    Image(
        painter = painterResource(UiDrawableRegistry.resourceId(drawableName)),
        contentDescription = null,
        colorFilter = ColorFilter.tint(if (LocalRiffControlEnabled.current) tint else Color(LocalRiffPalette.current.disabledIcon)),
        contentScale = if (squareViewport) ContentScale.Fit else ContentScale.FillBounds,
        modifier = modifier,
    )
}

@Composable
internal fun RiffDrawable(
    drawableName: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(UiDrawableRegistry.resourceId(drawableName)),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
internal fun settingsTarget(wide: Boolean): Dp = uiDimension(if (wide) "ratingTarget" else "ratingTargetNarrow")

@Composable
internal fun uiDimension(key: String): Dp = LocalRiffConfig.current.float("geometry.$key").dp

@Composable
internal fun uiTextSize(key: String): Float = LocalRiffConfig.current.float("typography.$key")

internal val TOP_BAR_DP: Float
    @Composable get() = LocalRiffConfig.current.float("geometry.topBarHeight")
internal const val BUTTON_CONTENT_PADDING_DP = 10f
internal const val BREADCRUMB_BUTTON_CONTENT_PADDING_DP = 15f
internal const val MAIN_LABEL_HEIGHT_DP = 58f
internal val TILE_GAP_DP: Float
    @Composable get() = LocalRiffConfig.current.float("geometry.targetSpacingMin")
internal const val AI_WIDE_MIN_CONTROLS_WIDTH_DP = 480f
internal const val AI_CHOICE_MIN_DP = 64f
internal const val AI_PLAY_MIN_DP = 76f
internal val AI_CANVAS_TEXT_INSET_DP: Float
    @Composable get() = LocalRiffConfig.current.float("geometry.aiStatusInset")
internal const val AI_CANVAS_ADVISORY = "AI can make mistakes."
internal const val ROW_ACTIONS_AI_ADVISORY =
    "Changing a book to Music manually does not make it available for AI recommendations."
internal val ROW_GAP_DP: Float
    @Composable get() = LocalRiffConfig.current.float("geometry.listRowGap")
internal val SELECTOR_ROW_HEIGHT_WIDE_DP: Float
    @Composable get() = LocalRiffConfig.current.float("geometry.listRowHeight")
internal val SELECTOR_ROW_HEIGHT_NARROW_DP: Float
    @Composable get() = LocalRiffConfig.current.float("geometry.listRowHeightTwoTier")
internal const val PLAYBACK_TITLE_ARTIST_GAP_DP = 16f
internal const val PLAYBACK_ARTIST_ALBUM_GAP_DP = 12f
internal const val PLAYBACK_ALBUM_CHAPTER_GAP_DP = 8f
