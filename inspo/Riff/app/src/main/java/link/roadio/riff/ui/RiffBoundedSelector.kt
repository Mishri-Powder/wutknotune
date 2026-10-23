@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.floor

internal data class SelectorScrollPosition(
    val index: Int,
    val offset: Int,
    val pageSize: Int,
)

/** Visible-row payload only; never carries a recursive BrowserItem subtree into LazyColumn reuse. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> BoundedSelector(
    tag: String,
    title: String,
    items: List<T>,
    itemKey: (T) -> Any,
    itemContentType: (T) -> Any?,
    page: Int,
    selectorIndex: Int,
    selectorOffset: Int,
    selectorKey: String,
    emptyMessage: String,
    rowHeightWide: Dp,
    rowHeightNarrow: Dp,
    background: Color? = null,
    showNarrowPager: Boolean = true,
    footerHeight: Dp = 0.dp,
    footer: (@Composable (Boolean) -> Unit)? = null,
    bottomLayer: (@Composable (Boolean) -> Unit)? = null,
    bottomLayerRows: (Boolean) -> Int = { 0 },
    dimContent: Boolean = false,
    titleAfterLeadingCount: Int? = null,
    titleContent: (@Composable (Boolean) -> Unit)? = null,
    onViewport: ((Int, Int, Int, Int) -> Unit)? = null,
    interactionEnabled: Boolean = true,
    leading: (Boolean, Boolean, Int, Boolean) -> List<TopActionSpec>,
    trailing: TopActionSpec? = null,
    row: @Composable (T, Int, Int, Boolean) -> Unit,
) {
    val screenBackground = background ?: Color(LocalRiffPalette.current.selectBackground)
    val rowGap = uiDimension("listRowGap")
    val gap = uiDimension("targetSpacingMin").value
    val navigationHeight = uiDimension("navTarget").value
    val minimumTarget = uiDimension("touchTargetMin").value
    val config = LocalRiffConfig.current
    SafeContent(screenBackground, tag) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = LocalRiffConfig.current.wide
            val rowHeight = if (wide) rowHeightWide else rowHeightNarrow
            val pagerVisible =
                !wide &&
                    showNarrowPager &&
                    (bottomLayer == null || maxHeight.value >= TOP_BAR_DP + navigationHeight + 2 * minimumTarget + gap)
            val pagerHeight = if (pagerVisible) navigationHeight else 0f
            val layerHeight =
                if (bottomLayer == null) {
                    0f
                } else {
                    selectorBottomLayerHeight(
                        maxHeight.value - TOP_BAR_DP - pagerHeight,
                        rowHeight.value,
                        bottomLayerRows(wide),
                        wide,
                        keyHeight = config.float(if (wide) "geometry.ratingTarget" else "geometry.ratingTargetNarrow"),
                        gap = gap,
                    )
                }
            val layerSpace = if (bottomLayer == null) 0f else layerHeight + gap
            val footerSpace = footerHeight.value.takeIf { footer != null } ?: 0f
            val available = maxHeight.value - TOP_BAR_DP - pagerHeight - layerSpace - footerSpace
            // Auto-OK targets the ordinary list after the keypad has closed.
            val resultAvailable =
                maxHeight.value - TOP_BAR_DP -
                    (if (!wide && showNarrowPager) navigationHeight else 0f) -
                    footerSpace
            val pageSize =
                floor(available / (rowHeight.value + rowGap.value))
                    .toInt()
                    .coerceIn(config.int("behavior.rowsPerPageMin"), config.int("behavior.rowsPerPageMax"))
            val density = LocalDensity.current
            val obscuredHeight = if (bottomLayer == null) 0f else layerSpace + pagerHeight
            val defaultBringIntoView = LocalBringIntoViewSpec.current
            val bringIntoView =
                remember(defaultBringIntoView, obscuredHeight, density) {
                    object : BringIntoViewSpec {
                        override fun calculateScrollDistance(
                            offset: Float,
                            size: Float,
                            containerSize: Float,
                        ): Float =
                            defaultBringIntoView.calculateScrollDistance(
                                offset,
                                size,
                                (containerSize - with(density) { obscuredHeight.dp.toPx() }).coerceAtLeast(0f),
                            )
                    }
                }
            LaunchedEffect(selectorKey, rowHeight, rowGap, available, resultAvailable, pageSize, items.size) {
                onViewport?.invoke(
                    with(density) { rowHeight.roundToPx() },
                    with(density) { rowGap.roundToPx() },
                    with(density) { available.dp.roundToPx() },
                    azVisibleCapacity(
                        with(density) { rowHeight.roundToPx() },
                        with(density) { rowGap.roundToPx() },
                        with(density) { resultAvailable.dp.roundToPx() },
                    ),
                )
            }
            val pageCount = ((items.size + pageSize - 1) / pageSize).coerceAtLeast(1)
            val initialIndex = selectorIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val listState =
                remember(selectorKey) {
                    LazyListState(
                        firstVisibleItemIndex = initialIndex,
                        firstVisibleItemScrollOffset = selectorOffset.coerceAtLeast(0),
                    )
                }
            val lastReportedPosition =
                remember(selectorKey) {
                    mutableStateOf(
                        SelectorScrollPosition(
                            initialIndex,
                            selectorOffset.coerceAtLeast(0),
                            pageSize,
                        ),
                    )
                }
            val scope = rememberCoroutineScope()
            val dispatch = LocalRiffDispatch.current
            val selectedPage by remember(listState, pageSize, pageCount) {
                derivedStateOf {
                    (listState.firstVisibleItemIndex / pageSize).coerceIn(0, pageCount - 1)
                }
            }
            val canUp by remember(listState) { derivedStateOf(listState::canScrollBackward) }
            val canDown by remember(listState) { derivedStateOf(listState::canScrollForward) }
            val previousPage: () -> Unit = {
                scope.launch {
                    listState.animateScrollToItem((listState.firstVisibleItemIndex - pageSize).coerceAtLeast(0))
                }
                Unit
            }
            val nextPage: () -> Unit = {
                scope.launch {
                    listState.animateScrollToItem(
                        (listState.firstVisibleItemIndex + pageSize).coerceAtMost((items.size - 1).coerceAtLeast(0)),
                    )
                }
                Unit
            }
            LaunchedEffect(listState, pageSize, interactionEnabled) {
                if (!interactionEnabled) {
                    listState.stopScroll()
                    val position =
                        SelectorScrollPosition(
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset,
                            pageSize,
                        )
                    if (position != lastReportedPosition.value) {
                        lastReportedPosition.value = position
                        dispatch(PlayerUiAction.RememberSelectorPosition(position.index, position.offset, position.pageSize))
                    }
                    return@LaunchedEffect
                }
                androidx.compose.runtime
                    .snapshotFlow {
                        if (listState.isScrollInProgress) {
                            null
                        } else {
                            SelectorScrollPosition(
                                listState.firstVisibleItemIndex,
                                listState.firstVisibleItemScrollOffset,
                                pageSize,
                            )
                        }
                    }.distinctUntilChanged()
                    .filterNotNull()
                    .collect { position ->
                        if (position != lastReportedPosition.value) {
                            lastReportedPosition.value = position
                            dispatch(PlayerUiAction.RememberSelectorPosition(position.index, position.offset, position.pageSize))
                        }
                    }
            }
            val localizedLeading =
                leading(canUp, canDown, pageSize, wide).map { spec ->
                    when (spec.action) {
                        is PlayerUiAction.PreviousPage -> spec.copy(onClick = previousPage)
                        is PlayerUiAction.NextPage -> spec.copy(onClick = nextPage)
                        else -> spec
                    }
                }
            Column(Modifier.fillMaxSize()) {
                RiffTopBar(
                    leading = localizedLeading,
                    title = title,
                    trailing = trailing,
                    wide = wide,
                    titleAfterLeadingCount = titleAfterLeadingCount,
                    titleContent = titleContent,
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .selectorAdjustment(dimContent, screenBackground, config.int("behavior.azRgbSubtract"))
                            .testTag("selector:content"),
                    ) {
                        if (items.isEmpty()) {
                            RiffText(
                                emptyMessage,
                                uiTextSize("mixedButtonMinFontSize"),
                                LocalRiffPalette.current.muted,
                                medium = true,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            // AI-INTENT: Hierarchy, Settings, SAF, and playlist selectors must share this path;
                            // scroll policy belongs to the shared selector.
                            CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(bottom = obscuredHeight.dp),
                                    userScrollEnabled = interactionEnabled,
                                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("selector:list"),
                                ) {
                                    itemsIndexed(
                                        items = items,
                                        key = { _, item -> itemKey(item) },
                                        contentType = { _, item -> itemContentType(item) },
                                    ) { index, item ->
                                        Box(Modifier.fillMaxWidth().height(rowHeight).testTag("selector:row:${itemKey(item)}")) {
                                            row(item, selectedPage, pageCount, wide)
                                        }
                                        if (index != items.lastIndex) {
                                            Spacer(Modifier.height(ROW_GAP_DP.dp))
                                        }
                                    }
                                }
                            }
                        }
                        if (pagerVisible && bottomLayer == null) {
                            NarrowPager(canUp, canDown, previousPage, nextPage)
                        }
                        footer?.let { footerContent ->
                            Box(Modifier.fillMaxWidth().height(footerHeight)) {
                                footerContent(wide)
                            }
                        }
                    }
                    if (bottomLayer != null) {
                        // Transparent, non-semantic backstop for gaps outside the key grid.
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(obscuredHeight.dp)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent().changes.forEach { it.consume() }
                                        }
                                    }
                                }.testTag("az:gap-shield"),
                        )
                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(obscuredHeight.dp),
                        ) {
                            if (pagerVisible) NarrowPager(canUp, canDown, previousPage, nextPage)
                            Spacer(Modifier.height(uiDimension("targetSpacingMin")))
                            Box(Modifier.fillMaxWidth().height(layerHeight.dp).testTag("az:layer")) { bottomLayer(wide) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NarrowPager(
    canUp: Boolean,
    canDown: Boolean,
    previousPage: () -> Unit,
    nextPage: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(uiDimension("navTarget"))) {
        RiffIconButton(
            "arrowhead_up",
            "Previous page",
            enabled = canUp,
            wide = false,
            onClick = previousPage,
            modifier = Modifier.size(uiDimension("navTarget")),
        )
        Spacer(Modifier.weight(1f))
        RiffIconButton(
            "arrowhead_down",
            "Next page",
            enabled = canDown,
            wide = false,
            onClick = nextPage,
            modifier = Modifier.size(uiDimension("navTarget")),
        )
    }
}
