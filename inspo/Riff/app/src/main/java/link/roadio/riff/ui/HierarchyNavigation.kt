package link.roadio.riff.ui

internal data class HierarchyNavigationSnapshot(
    val browserPath: List<String>,
    val page: Int,
    val pageSize: Int,
    val selectorIndex: Int,
    val selectorOffset: Int,
    val focusedItemId: String?,
    val azSteps: List<AzGroup>,
)

/** Search is a browser session, which may temporarily show the ordinary More flow. */
internal fun PlayerUiState.hierarchyScreen(): PlayerScreen = if (azDraftSteps != null) PlayerScreen.AZ_SEARCH else PlayerScreen.HIERARCHY

/** One container restoration policy for child, parent and committed destinations. */
internal fun restoreHierarchyContainer(
    state: PlayerUiState,
    path: List<String>,
    steps: List<AzGroup>,
    page: Int = 0,
    pageSize: Int = state.pageSize,
    index: Int = 0,
    offset: Int = 0,
    focus: String? = null,
): PlayerUiState =
    state.copy(
        screen = state.hierarchyScreen(),
        browserPath = path,
        page = page,
        pageSize = pageSize,
        selectorIndex = index,
        selectorOffset = offset,
        focusedItemId = focus,
        azSteps = steps,
        azDraftSteps = if (state.azDraftSteps != null) steps else null,
        azAutoEntryPending = state.azDraftSteps == null,
        azAutoApplyPending = false,
    )

internal fun restoreRememberedHierarchyContainer(
    state: PlayerUiState,
    remembered: HierarchyNavigationSnapshot,
): PlayerUiState =
    restoreHierarchyContainer(
        state = state,
        path = remembered.browserPath,
        steps = remembered.azSteps,
        page = remembered.page,
        pageSize = remembered.pageSize,
        index = remembered.selectorIndex,
        offset = remembered.selectorOffset,
        focus = remembered.focusedItemId,
    )

/** Auto-entry eligibility is based on the complete immediate source, before a restored filter. */
internal fun autoSearchSourceItemCount(state: PlayerUiState): Int = state.currentNode().children.size

internal fun applyAzSearch(state: PlayerUiState): PlayerUiState {
    if (state.screen != PlayerScreen.AZ_SEARCH) return state
    val steps = state.azDraftSteps ?: return state
    val snapshot = state.currentAzSnapshot() ?: return state
    val visible = snapshot.draftItems
    val anchor =
        retainedSelectorAnchor(
            visible,
            state.selectorIndex,
            state.selectorOffset,
            state.focusedItemId,
            state.pageSize,
        )
    return state.copy(
        screen = PlayerScreen.HIERARCHY,
        page = anchor.page,
        selectorIndex = anchor.index,
        selectorOffset = anchor.offset,
        focusedItemId = anchor.focusedItemId,
        azSteps = steps,
        azApplyRevision = state.azApplyRevision + 1L,
        azDraftSteps = null,
        azAutoEntryPending = false,
        azAutoApplyPending = false,
        azSnapshot =
            AzSearchSnapshot(
                AzSearchQuery(snapshot.query.source, steps, null),
                visible,
                visible,
                snapshot.resultCounts,
            ),
    )
}

/** Auxiliary selectors can use their own viewport without losing the browser's. */
internal fun resumeHierarchyAfterMenu(
    state: PlayerUiState,
    opening: PlayerUiState?,
): PlayerUiState {
    val position = opening?.takeIf { it.browserPath == state.browserPath && it.libraryGeneration == state.libraryGeneration }
    return state.copy(
        screen = state.hierarchyScreen(),
        page = position?.page ?: state.page,
        pageSize = position?.pageSize ?: state.pageSize,
        selectorIndex = position?.selectorIndex ?: state.selectorIndex,
        selectorOffset = position?.selectorOffset ?: state.selectorOffset,
        focusedItemId = position?.focusedItemId ?: state.focusedItemId,
        actionsItemId = null,
        audiobookChoiceDraft = null,
        playlistSourceItemId = null,
        azAutoEntryPending = false,
        azAutoApplyPending = false,
    )
}

/** Keep at least one action-height of unobscured results on constrained displays. */
internal fun selectorBottomLayerHeight(
    bodyHeight: Float,
    rowHeight: Float,
    rows: Int,
    wide: Boolean,
    keyHeight: Float = if (wide) 100f else 76f,
    gap: Float = 24f,
): Float {
    val contentHeight = rows * keyHeight + (rows - 1).coerceAtLeast(0) * gap
    val available = (bodyHeight - keyHeight - gap).coerceAtLeast(0f)
    val comfortable = (bodyHeight - rowHeight - gap).coerceAtLeast(keyHeight)
    return minOf(contentHeight, comfortable, available)
}

/** Shared preorder lookup for row actions, reactions and controller commands. */
internal fun findBrowserItem(
    node: BrowserItem,
    id: String,
): BrowserItem? = findBrowserItem(node) { it.id == id }

internal fun findByReactionKey(
    node: BrowserItem,
    reactionKey: String,
): BrowserItem? = findBrowserItem(node) { it.reactionKey == reactionKey }

private fun findBrowserItem(
    node: BrowserItem,
    predicate: (BrowserItem) -> Boolean,
): BrowserItem? = if (predicate(node)) node else node.children.firstNotNullOfOrNull { findBrowserItem(it, predicate) }

internal data class SelectorAnchor(
    val page: Int,
    val index: Int,
    val offset: Int,
    val focusedItemId: String?,
)

internal fun retainedSelectorAnchor(
    items: List<BrowserItem>,
    index: Int,
    offset: Int,
    focusedItemId: String?,
    pageSize: Int,
): SelectorAnchor {
    val retainedIndex = index.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    val retainedFocus = focusedItemId?.takeIf { id -> items.any { it.id == id } } ?: items.getOrNull(retainedIndex)?.id
    return SelectorAnchor(
        page = retainedIndex / pageSize.coerceAtLeast(1),
        index = retainedIndex,
        offset = offset.coerceAtLeast(0),
        focusedItemId = retainedFocus,
    )
}
