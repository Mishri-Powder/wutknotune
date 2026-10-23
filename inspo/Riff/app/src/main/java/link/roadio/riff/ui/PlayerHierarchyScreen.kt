@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal data class HierarchyRowModel(
    val id: String,
    val label: String,
    val kind: BrowserKind,
    val playable: Boolean,
    val enabled: Boolean,
    val reactionKey: String,
    val actionTier: Boolean,
    val fragment: Boolean,
)

internal fun BrowserItem.toHierarchyRowModel(): HierarchyRowModel =
    HierarchyRowModel(
        id = id,
        label = label,
        kind = kind,
        playable = playable,
        enabled = enabled,
        reactionKey = reactionKey,
        actionTier = hierarchyUsesActionTier,
        fragment = fragment,
    )

@Composable
internal fun AzBreadcrumb(
    state: PlayerUiState,
    scrollable: Boolean,
    inSearch: Boolean = false,
) {
    val palette = LocalRiffPalette.current
    val dispatch = LocalRiffDispatch.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scopes = state.azBreadcrumbScopesForDisplay()
    val contents: @Composable RowScope.() -> Unit = {
        scopes.forEachIndexed { scopeIndex, scope ->
            ((if (scopeIndex == 0) 0 else 1)..scope.steps.size).forEach { count ->
                if (scopeIndex > 0 || count > 0) {
                    Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                }
                val prefix = scope.steps.take(count)
                // AI-INTENT: The terminal filter plate steps back with the keypad open or closed; its label stays current.
                // Authority: https://wiki.271.pics/Riff/LATEST/player-az-navigation
                val destinationPrefix =
                    if (scope.path == state.browserPath && count > 0 && count == scope.steps.size) {
                        prefix.dropLast(1)
                    } else {
                        prefix
                    }
                val label = if (count == 0) scope.label else prefix.last().label
                val measured =
                    measurer.measure(
                        AnnotatedString(label),
                        riffTextStyle(BREADCRUMB_TEXT_SP, palette.text, medium = true, monospace = count > 0, chrome = true),
                        maxLines = 1,
                        softWrap = false,
                    )
                val width = with(density) { measured.size.width.toDp() + 30.dp }.coerceIn(uiDimension("navTarget"), 240.dp)
                RiffControl(
                    description =
                        "Browse ${scope.label}; " +
                            if (destinationPrefix.isEmpty()) {
                                "no A-Z filter"
                            } else {
                                "A-Z ${destinationPrefix.joinToString(
                                    " · ",
                                ) { it.label }}"
                            },
                    modifier =
                        Modifier
                            .width(width)
                            .height(uiDimension("navTarget"))
                            .testTag("az:${if (inSearch) "search-crumb" else "crumb"}:$scopeIndex:$count"),
                    enabled = if (inSearch) state.screen == PlayerScreen.AZ_SEARCH else state.screen == PlayerScreen.HIERARCHY,
                    selected = scope.path == state.browserPath && prefix == (if (inSearch) state.azDraftSteps else state.azSteps),
                    contentPadding = 15.dp,
                    onClick = { dispatch(PlayerUiAction.SelectAzBreadcrumb(scope.path, destinationPrefix, inSearch)) },
                ) {
                    RiffText(
                        label,
                        BREADCRUMB_TEXT_SP,
                        palette.text,
                        medium = true,
                        monospace = count > 0,
                        modifier = Modifier.fillMaxSize(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    if (scrollable) {
        RightAlignedStrip(contents)
    } else {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.height(uiDimension("navTarget")), verticalAlignment = Alignment.CenterVertically, content = contents)
        }
    }
}

internal fun hierarchyChromeActions(
    state: PlayerUiState,
    rootUpHome: Boolean,
): List<TopActionSpec> =
    listOf(
        TopActionSpec("flip", "Switch to playback", action = PlayerUiAction.TogglePlaybackSurface),
        TopActionSpec("home", "Main menu", action = PlayerUiAction.OpenMain),
        TopActionSpec(
            "arrow_up",
            if (state.browserPath.size <= 1 && rootUpHome) "Main menu" else "Parent",
            state.browserPath.size > 1 || rootUpHome,
            if (state.browserPath.size <= 1 && rootUpHome) PlayerUiAction.OpenMain else PlayerUiAction.NavigateParent,
        ),
        TopActionSpec(
            "sort_az",
            if (state.screen == PlayerScreen.AZ_SEARCH) "Apply and close A-Z search" else "A-Z search",
            if (state.screen == PlayerScreen.AZ_SEARCH) {
                state.currentAzSnapshot() != null
            } else {
                state.currentNode().children.isNotEmpty()
            },
            PlayerUiAction.OpenAzSearch,
        ),
    )

@Composable
internal fun HierarchyScreen(state: PlayerUiState) {
    val node = state.currentNode()
    val rootUpHome = LocalRiffConfig.current.string("behavior.hierarchyRootUpBehavior") == "home"
    val title = state.visibleHierarchyLabel() ?: "Library"
    val dispatch = LocalRiffDispatch.current
    val preview = state.screen == PlayerScreen.AZ_SEARCH
    val rows = state.visibleChildren().map(BrowserItem::toHierarchyRowModel)
    val inheritedBan = state.browserPath.any { state.reactions[it]?.ownBanned == true }
    BoundedSelector(
        tag = if (preview) "screen:AZ_SEARCH" else "screen:HIERARCHY",
        title = title,
        titleContent = { wide -> AzBreadcrumb(state, wide, inSearch = preview) },
        bottomLayer = if (preview) ({ wide -> AzSearchContent(state, wide) }) else null,
        bottomLayerRows = { wide ->
            azGridRows(
                wide,
                state
                    .azDisplaySnapshot()
                    ?.resultCounts
                    .orEmpty()
                    .keys,
            ).size
        },
        dimContent = preview,
        items = rows,
        itemKey = { it.id },
        itemContentType = { if (it.actionTier) "hierarchy-actions" else "hierarchy-expanded" },
        page = state.page,
        selectorIndex = state.selectorIndex,
        selectorOffset = state.selectorOffset,
        selectorKey =
            state.browserPath.joinToString("/") +
                ":" + node.id +
                ":" + state.libraryGeneration +
                ":" + state.azDisplayedSteps().joinToString(",") + ":" + preview +
                ":" + state.azApplyRevision + ":" + state.azAppliedReady(),
        interactionEnabled = state.azAppliedReady(),
        emptyMessage = if (state.azAppliedReady()) "No ${title.lowercase()} available" else "Preparing search…",
        rowHeightWide = SELECTOR_ROW_HEIGHT_WIDE_DP.dp,
        // Allocate from the complete source: filtering cannot move Music targets or change A-Z capacity.
        // Authority: https://wiki.271.pics/Riff/LATEST/hierarchy-screen-scope-and-shared-implementation#rows-and-actions
        rowHeightNarrow =
            if (node.children.any { it.hierarchyUsesActionTier }) {
                SELECTOR_ROW_HEIGHT_NARROW_DP.dp
            } else {
                maxOf(SELECTOR_ROW_HEIGHT_WIDE_DP.dp, uiDimension("ratingTargetNarrow"))
            },
        onViewport = { rowHeightPx, rowGapPx, viewportHeightPx, visibleCapacity ->
            dispatch(
                PlayerUiAction.ReportHierarchyViewport(
                    rowHeightPx,
                    rowGapPx,
                    viewportHeightPx,
                    visibleCapacity,
                ),
            )
        },
        leading = { _, _, _, _ ->
            hierarchyChromeActions(state, rootUpHome)
        },
    ) { item, selectedPage, pageCount, wide ->
        HierarchyRow(
            item = item,
            contextLabel = title,
            longPressStartsPlayback = state.settings.longPressStartsPlayback,
            reaction = state.reactions[item.reactionKey] ?: ReactionState(),
            inheritedBan = inheritedBan,
            page = selectedPage,
            pageCount = pageCount,
            wide = wide,
        )
    }
}

@Composable
internal fun AzSearchContent(
    state: PlayerUiState,
    wide: Boolean,
) {
    val resultCounts = state.azDisplaySnapshot()?.resultCounts.orEmpty()
    val keyBounds = remember { mutableMapOf<AzKey, Rect>() }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { gridOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (keyBounds.values.none { it.contains(down.position + gridOrigin) }) {
                        down.consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }.verticalScroll(rememberScrollState())
            .testTag("az:grid"),
        verticalArrangement = Arrangement.spacedBy(uiDimension("targetSpacingMin")),
    ) {
        azGridRows(wide, resultCounts.keys).forEach { groups ->
            key(groups) {
                DisposableEffect(groups) {
                    onDispose { groups.forEach(keyBounds::remove) }
                }
                AzGroupRow(
                    groups,
                    resultCounts,
                    wide,
                    state.azDraftSteps.orEmpty().isNotEmpty(),
                    state.currentAzSnapshot() != null,
                    onKeyBounds = { key, bounds -> keyBounds[key] = bounds },
                )
            }
        }
    }
}

@Composable
internal fun AzGroupRow(
    groups: List<AzKey>,
    resultCounts: Map<AzGroup, Int>,
    wide: Boolean,
    canBackspace: Boolean,
    ready: Boolean,
    onKeyBounds: (AzKey, Rect) -> Unit,
) {
    val available = resultCounts.keys
    val disabledOpacity = LocalRiffConfig.current.int("behavior.azDisabledAlpha") / 255f
    val dispatch = LocalRiffDispatch.current
    val palette = LocalRiffPalette.current
    val target = if (wide) uiDimension("ratingTarget") else uiDimension("ratingTargetNarrow")
    val columns = if (wide) 8 else 4
    val gap = uiDimension("targetSpacingMin")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rowWidth = maxOf(maxWidth, target * columns + gap * (columns - 1))
        val baseWidth = (rowWidth - gap * (columns - 1).toFloat()) / columns.toFloat()
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).width(rowWidth),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            groups.forEach { key ->
                val group = (key as? AzKey.Group)?.group
                val shape = RoundedCornerShape(12.dp)
                val span = key.columnSpan
                val keyWidth = baseWidth * span.toFloat() + gap * (span - 1).toFloat()
                val availableNow =
                    when (key) {
                        AzKey.Backspace -> canBackspace
                        is AzKey.Group -> key.group in available
                    }
                val enabled = availableNow && (key == AzKey.Backspace || ready)
                RiffIconButton(
                    icon = group?.icon ?: "rew",
                    label =
                        when (key) {
                            AzKey.Backspace -> "Remove last A-Z step"
                            is AzKey.Group -> "${key.group.label} A-Z group; ${resultCounts[key.group] ?: 0} results"
                        },
                    enabled = enabled,
                    appearanceEnabled = availableNow,
                    wide = wide,
                    onClick = {
                        if (enabled) {
                            dispatch(
                                when (key) {
                                    AzKey.Backspace -> PlayerUiAction.AzBackspace
                                    is AzKey.Group -> PlayerUiAction.SelectAzGroup(key.group)
                                },
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .width(keyWidth)
                            .height(target)
                            .onGloballyPositioned { onKeyBounds(key, it.boundsInRoot()) }
                            .testTag("az:group:${group?.name ?: "BACKSPACE"}")
                            .alpha(if (availableNow) 1f else disabledOpacity)
                            .background(Color(palette.surface), shape)
                            .clip(shape)
                            .border(2.dp, Color(palette.outline), shape),
                )
            }
        }
    }
}

@Composable
internal fun HierarchyRow(
    item: HierarchyRowModel,
    contextLabel: String,
    longPressStartsPlayback: Boolean,
    reaction: ReactionState,
    inheritedBan: Boolean,
    page: Int,
    pageCount: Int,
    wide: Boolean,
) {
    val palette = LocalRiffPalette.current
    val dispatch = LocalRiffDispatch.current
    val reactionsApply = item.actionTier
    val bodyOpacity =
        if (item.enabled &&
            (item.kind != BrowserKind.TRACK || item.playable)
        ) {
            1f
        } else {
            LocalRiffConfig.current.int("behavior.azDisabledAlpha") / 255f
        }
    val effectiveBan = reactionsApply && (reaction.ownBanned || inheritedBan)
    val rowColor =
        when {
            effectiveBan -> {
                palette.suppressed
            }

            item.fragment && LocalRiffConfig.current.boolean("player.highlightFragments") -> {
                LocalRiffConfig.current.color("colors.fragmentTitleColor")
            }

            else -> {
                palette.text
            }
        }
    val description =
        if (reactionsApply) {
            "${item.label}; ${if (item.fragment) "Fragment; " else ""}$contextLabel; page ${page + 1} of $pageCount; " +
                "preference ${reaction.preference.name.lowercase()}; own ban ${reaction.ownBanned}; " +
                "effective ban $effectiveBan"
        } else {
            "${item.label}; ${if (item.fragment) "Fragment; " else ""}$contextLabel; page ${page + 1} of $pageCount"
        }
    val click = {
        dispatch(PlayerUiAction.RememberFocus(item.id))
        dispatch(
            if (item.kind == BrowserKind.TRACK) {
                PlayerUiAction.Play(item.id)
            } else {
                PlayerUiAction.NavigateTo(item.id)
            },
        )
    }
    val longClick =
        if (item.enabled && longPressStartsPlayback && item.playable) {
            {
                dispatch(PlayerUiAction.Play(item.id))
            }
        } else {
            null
        }
    val body: @Composable (Modifier) -> Unit = { modifier ->
        RiffControl(
            description = description,
            enabled = item.enabled && (item.kind != BrowserKind.TRACK || item.playable),
            onClick = click,
            onLongClick = longClick,
            onLongClickLabel = longClick?.let { "Play" },
            modifier = modifier,
        ) {
            RiffText(
                item.label,
                uiTextSize("textTitleList"),
                rowColor,
                medium = true,
                modifier = Modifier.fillMaxSize().alpha(bodyOpacity),
                maxLines = 1,
            )
        }
    }
    when {
        !item.actionTier -> {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                body(Modifier.weight(1f).height(if (wide) uiDimension("ratingTarget") else uiDimension("ratingTargetNarrow")))
                Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                MoreButton(item, wide, item.kind == BrowserKind.TRACK || item.enabled)
            }
        }

        wide -> {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                body(Modifier.weight(1f).height(uiDimension("ratingTarget")))
                ReactionStrip(
                    item,
                    reaction,
                    true,
                    Modifier
                        .width(uiDimension("ratingTarget") * 4 + uiDimension("targetSpacingMin") * 3)
                        .height(uiDimension("ratingTarget")),
                )
            }
        }

        else -> {
            Column(Modifier.fillMaxSize()) {
                body(Modifier.fillMaxWidth().height(uiDimension("ratingTargetNarrow")))
                Spacer(Modifier.height(uiDimension("targetSpacingMin")))
                ReactionStrip(
                    item,
                    reaction,
                    false,
                    Modifier.fillMaxWidth().height(uiDimension("ratingTargetNarrow")),
                )
            }
        }
    }
}

@Composable
internal fun ReactionStrip(
    item: HierarchyRowModel,
    reaction: ReactionState,
    wide: Boolean,
    modifier: Modifier,
) {
    val dispatch = LocalRiffDispatch.current
    val target = if (wide) uiDimension("ratingTarget") else uiDimension("ratingTargetNarrow")
    val entries =
        listOf(
            Triple<Preference?, String, String>(
                null,
                "Ban",
                if (reaction.ownBanned) "ban_filled" else "ban",
            ),
            Triple(
                Preference.DISLIKED,
                "Dislike",
                if (reaction.preference == Preference.DISLIKED) "dislike_filled" else "dislike",
            ),
            Triple(
                Preference.LIKED,
                "Like",
                if (reaction.preference == Preference.LIKED) "like_filled" else "like",
            ),
        )
    Row(
        modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEachIndexed { index, (value, label, icon) ->
            if (index > 0) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
            RiffIconButton(
                icon,
                "$label ${item.label}",
                selected = if (value == null) reaction.ownBanned else reaction.preference == value,
                wide = wide,
                onClick = {
                    dispatch(
                        PlayerUiAction.SetReaction(
                            itemId = item.reactionKey,
                            preference = value,
                            toggleBan = value == null,
                        ),
                    )
                },
                modifier = Modifier.size(target),
            )
        }
        Spacer(Modifier.width(uiDimension("targetSpacingMin")))
        MoreButton(item, wide, item.kind == BrowserKind.TRACK || item.enabled)
    }
}

@Composable
internal fun MoreButton(
    item: HierarchyRowModel,
    wide: Boolean,
    enabled: Boolean,
) {
    val dispatch = LocalRiffDispatch.current
    RiffIconButton(
        "dots",
        "Actions for ${item.label}",
        enabled = enabled,
        appearanceEnabled = true,
        wide = wide,
        onClick = { dispatch(PlayerUiAction.OpenRowActions(item.id)) },
        modifier = Modifier.size(if (wide) uiDimension("ratingTarget") else uiDimension("ratingTargetNarrow")),
    )
}
