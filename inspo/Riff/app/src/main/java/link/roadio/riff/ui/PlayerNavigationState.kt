package link.roadio.riff.ui

/** Process-only browse/search return state. Storage and playback never enter this owner. */
internal class PlayerNavigationState {
    private var azReturnSnapshot: AzSearchSnapshot? = null
    private var azReturnRoot: HierarchyRoot? = null
    private val rootMemory = mutableMapOf<HierarchyRoot, HierarchyNavigationSnapshot>()
    private val containerMemory = mutableMapOf<List<String>, HierarchyNavigationSnapshot>()
    val roots: Map<HierarchyRoot, HierarchyNavigationSnapshot> get() = rootMemory
    val containers: Map<List<String>, HierarchyNavigationSnapshot> get() = containerMemory

    fun clearSearchReturn() {
        azReturnSnapshot = null
        azReturnRoot = null
    }

    fun openCategory(
        old: PlayerUiState,
        categoryId: String,
    ): PlayerUiState {
        if (old.root.children
                .firstOrNull { it.id == categoryId }
                ?.enabled == false
        ) {
            return old
        }
        if (old.contentMode == ContentMode.AUDIOBOOK && categoryId == "category:ai") {
            return old
        }
        if (categoryId == "category:ai") {
            return old.copy(
                screen = PlayerScreen.AI,
                browserPath = listOf(categoryId),
                hierarchyRoot = null,
                page = 0,
                playlistSourceItemId = null,
                azSteps = emptyList(),
                azDraftSteps = null,
                azAutoEntryPending = false,
            )
        }
        val root = HierarchyRoot.entries.firstOrNull { it.categoryId == categoryId } ?: return old
        // AI-INTENT: Collection launchers return to their list, with that list's own browse memory.
        // A successful Add may remember a child playlist; it must not turn MAIN into a child shortcut.
        // Authority: https://wiki.271.pics/Riff/LATEST/hierarchy-screen-scope-and-shared-implementation
        val entryMemory = if (root == HierarchyRoot.PLAYLISTS) containerMemory[listOf(categoryId)] else rootMemory[root]
        val remembered = entryMemory?.takeIf { pathNode(old.root, it.browserPath) != null }
        return old.copy(
            screen = PlayerScreen.HIERARCHY,
            browserPath = remembered?.browserPath ?: listOf(categoryId),
            hierarchyRoot = root,
            page = remembered?.page ?: 0,
            pageSize = remembered?.pageSize ?: 1,
            selectorIndex = remembered?.selectorIndex ?: 0,
            selectorOffset = remembered?.selectorOffset ?: 0,
            focusedItemId = remembered?.focusedItemId,
            azSteps = remembered?.azSteps.orEmpty(),
            azDraftSteps = null,
            azAutoEntryPending = true,
        )
    }

    fun navigateAzBreadcrumb(
        old: PlayerUiState,
        action: PlayerUiAction.SelectAzBreadcrumb,
    ): PlayerUiState {
        val selected = selectAzBreadcrumb(old, action)
        if (selected !== old && old.screen == PlayerScreen.AZ_SEARCH) {
            return saveAzSearchFilter(selected)
        }
        if (
            selected === old ||
            old.screen == PlayerScreen.AZ_SEARCH ||
            selected.browserPath == old.browserPath
        ) {
            return selected
        }
        val remembered = containerMemory[selected.browserPath]
        return if (remembered?.azSteps == selected.azSteps) {
            restoreRememberedHierarchyContainer(selected, remembered)
        } else {
            selected
        }
    }

    fun navigate(
        old: PlayerUiState,
        itemId: String,
    ): PlayerUiState {
        val item = findBrowserItem(old.currentNode(), itemId) ?: return old
        if (item.kind == BrowserKind.TRACK) return old
        return navigateHierarchyPath(old, old.browserPath + itemId, itemId)
    }

    fun navigateParent(old: PlayerUiState): PlayerUiState =
        if (old.browserPath.size <= 1) {
            if (old.azDraftSteps != null) old else old.openMain()
        } else {
            navigateHierarchyPath(old, old.browserPath.dropLast(1), old.browserPath.last())
        }

    private fun navigateHierarchyPath(
        old: PlayerUiState,
        path: List<String>,
        focusFallback: String,
    ): PlayerUiState {
        val remembered = containerMemory[path]
        return restoreHierarchyContainer(
            old,
            path,
            remembered?.azSteps.orEmpty(),
            remembered?.page ?: 0,
            remembered?.pageSize ?: old.pageSize,
            remembered?.selectorIndex ?: 0,
            remembered?.selectorOffset ?: 0,
            remembered?.focusedItemId ?: focusFallback,
        )
    }

    fun openAzSearch(old: PlayerUiState): PlayerUiState =
        if (old.screen == PlayerScreen.HIERARCHY && old.currentNode().children.isNotEmpty()) {
            azReturnSnapshot = old.azSnapshot
            azReturnRoot = old.hierarchyRoot
            old.copy(
                screen = PlayerScreen.AZ_SEARCH,
                azDraftSteps = old.azSteps,
                azAutoEntryPending = false,
            )
        } else {
            old.copy(azAutoEntryPending = false)
        }

    fun cancelAzSearch(old: PlayerUiState): PlayerUiState {
        if (old.azDraftSteps == null) return old
        val remembered = rootMemory[azReturnRoot ?: old.hierarchyRoot]
        val path = remembered?.browserPath ?: old.browserPath
        val steps = remembered?.azSteps ?: old.azSteps
        val source = pathNode(old.root, path)?.children
        return old.copy(
            screen = PlayerScreen.HIERARCHY,
            browserPath = path,
            hierarchyRoot = azReturnRoot ?: old.hierarchyRoot,
            page = remembered?.page ?: old.page,
            pageSize = remembered?.pageSize ?: old.pageSize,
            selectorIndex = remembered?.selectorIndex ?: old.selectorIndex,
            selectorOffset = remembered?.selectorOffset ?: old.selectorOffset,
            focusedItemId = remembered?.focusedItemId,
            azSteps = steps,
            azDraftSteps = null,
            azSnapshot =
                azReturnSnapshot?.takeIf { it.query.source === source && it.query.applied == steps }
                    ?: old.azSnapshot?.takeIf { it.query.source === source && it.query.applied == steps },
            azAutoEntryPending = false,
            azAutoApplyPending = false,
        )
    }

    fun backspaceAzSearch(old: PlayerUiState): PlayerUiState {
        val draft = old.azDraftSteps?.takeIf { it.isNotEmpty() } ?: return old
        return saveAzSearchFilter(old.copy(azDraftSteps = draft.dropLast(1)), resetReturnAnchor = true)
    }

    /** Accepted Play remembers the browser anchor even when its More menu owns the visible list. */
    fun rememberPlaybackFilter(
        old: PlayerUiState,
        opening: PlayerUiState? = null,
    ): PlayerUiState {
        val browser = if (old.screen == PlayerScreen.ROW_ACTIONS) resumeHierarchyAfterMenu(old, opening) else old
        val saved = saveAzSearchFilter(browser)
        return old.copy(
            azSteps = saved.azSteps,
            azDraftSteps = saved.azDraftSteps,
            azSnapshot = saved.azSnapshot,
            azAutoEntryPending = saved.azAutoEntryPending,
            azAutoApplyPending = saved.azAutoApplyPending,
            azApplyRevision = saved.azApplyRevision,
        )
    }

    // AI-INTENT: Explicit filter saves advance both navigation memory and the cancellation checkpoint.
    // Authority: https://wiki.271.pics/Riff/LATEST/player-az-navigation
    private fun saveAzSearchFilter(
        old: PlayerUiState,
        resetReturnAnchor: Boolean = false,
    ): PlayerUiState {
        val steps = old.azDraftSteps ?: return old.also(::rememberHierarchy)
        val ready = old.azDisplaySnapshot()?.takeIf { (it.query.draft ?: it.query.applied) == steps }
        val saved =
            old.copy(
                azSteps = steps,
                azSnapshot =
                    ready?.let {
                        AzSearchSnapshot(
                            AzSearchQuery(it.query.source, steps, steps),
                            it.draftItems,
                            it.draftItems,
                            it.resultCounts,
                        )
                    } ?: old.azSnapshot,
                azAutoEntryPending = false,
                azAutoApplyPending = false,
                azApplyRevision = old.azApplyRevision + if (old.azSteps == steps) 0L else 1L,
            )
        val committed = saved.copy(screen = PlayerScreen.HIERARCHY, azDraftSteps = null)
        rememberHierarchy(
            if (resetReturnAnchor) {
                committed.copy(page = 0, selectorIndex = 0, selectorOffset = 0, focusedItemId = null)
            } else {
                committed
            },
        )
        azReturnRoot = saved.hierarchyRoot
        azReturnSnapshot =
            ready?.let {
                AzSearchSnapshot(
                    AzSearchQuery(it.query.source, steps, null),
                    it.draftItems,
                    it.draftItems,
                    it.resultCounts,
                )
            }
        return saved
    }

    fun selectAzGroup(
        old: PlayerUiState,
        group: AzGroup,
    ): PlayerUiState {
        val draft = old.azDraftSteps ?: return old
        if (group !in old.currentAzSnapshot()?.resultCounts.orEmpty()) return old
        val next = draft + group
        return old.copy(azDraftSteps = next, azAutoApplyPending = old.settings.autoSearch)
    }

    fun reportHierarchyViewport(
        old: PlayerUiState,
        action: PlayerUiAction.ReportHierarchyViewport,
    ): PlayerUiState {
        val measured = old.copy(azVisibleCapacity = action.visibleCapacity.coerceAtLeast(0))
        if (old.azSteps.isNotEmpty() && old.currentAzSnapshot() == null) return measured
        if (!old.azAutoEntryPending) return measured
        val settled = measured.copy(azAutoEntryPending = false)
        return if (
            old.settings.autoSearch &&
            shouldAutoOpenAzSearch(
                itemCount = autoSearchSourceItemCount(old),
                rowHeightPx = action.rowHeightPx,
                rowGapPx = action.rowGapPx,
                viewportHeightPx = action.viewportHeightPx,
            )
        ) {
            openAzSearch(settled)
        } else {
            settled
        }
    }

    // AI-INTENT: Never persist this map; it deliberately resets with the Player process.
    fun rememberHierarchy(value: PlayerUiState) {
        if (value.azDraftSteps != null) return
        val root = value.hierarchyRoot ?: return
        if (
            value.screen != PlayerScreen.HIERARCHY &&
            value.screen != PlayerScreen.ROW_ACTIONS
        ) {
            return
        }
        val snapshot =
            HierarchyNavigationSnapshot(
                browserPath = value.browserPath,
                page = value.page,
                pageSize = value.pageSize.coerceAtLeast(1),
                selectorIndex = value.selectorIndex,
                selectorOffset = value.selectorOffset,
                focusedItemId = value.focusedItemId,
                azSteps = value.azSteps,
            )
        rootMemory[root] = snapshot
        containerMemory[value.browserPath] = snapshot
    }

    fun repairNavigationMemory(
        oldRoot: BrowserItem,
        newRoot: BrowserItem,
        orders: AzReloadOrders,
    ) {
        rootMemory.replaceAll { _, snapshot -> repairNavigationSnapshot(oldRoot, newRoot, snapshot, orders) }
        val repaired =
            containerMemory.values
                .filter { pathNode(newRoot, it.browserPath) != null }
                .map { snapshot -> repairNavigationSnapshot(oldRoot, newRoot, snapshot, orders) }
                .associateBy(HierarchyNavigationSnapshot::browserPath)
        containerMemory.clear()
        containerMemory.putAll(repaired)
    }

    private fun repairNavigationSnapshot(
        oldRoot: BrowserItem,
        newRoot: BrowserItem,
        snapshot: HierarchyNavigationSnapshot,
        orders: AzReloadOrders,
    ): HierarchyNavigationSnapshot {
        var path = snapshot.browserPath
        while (path.size > 1 && pathNode(newRoot, path) == null) path = path.dropLast(1)
        val oldNode = pathNode(oldRoot, path)
        val newNode =
            pathNode(newRoot, path)
                ?: return snapshot.copy(
                    browserPath = snapshot.browserPath.take(1),
                    page = 0,
                    selectorIndex = 0,
                    selectorOffset = 0,
                    focusedItemId = null,
                )
        val restoredSteps = if (path == snapshot.browserPath) snapshot.azSteps else containerMemory[path]?.azSteps.orEmpty()
        val oldOrder = orders.order(oldNode?.children.orEmpty(), restoredSteps)
        val newOrder = orders.order(newNode.children, restoredSteps)
        val oldTop = oldOrder.getOrNull(snapshot.selectorIndex)
        val desired = if (path == snapshot.browserPath) oldTop else snapshot.browserPath.getOrNull(path.size)
        val anchor = reloadAnchorIndex(oldOrder, newOrder, desired)
        return snapshot.copy(
            browserPath = path,
            azSteps = restoredSteps,
            page = anchor / snapshot.pageSize.coerceAtLeast(1),
            selectorIndex = anchor,
            selectorOffset = if (path == snapshot.browserPath && desired in newOrder) snapshot.selectorOffset else 0,
            focusedItemId = snapshot.focusedItemId?.takeIf(newOrder::contains) ?: newOrder.getOrNull(anchor),
        )
    }
}

internal fun PlayerUiState.openMain(): PlayerUiState =
    copy(
        screen = PlayerScreen.MAIN,
        browserPath = emptyList(),
        hierarchyRoot = null,
        page = 0,
        selectorIndex = 0,
        selectorOffset = 0,
        azSteps = emptyList(),
        azDraftSteps = null,
        azAutoEntryPending = false,
        actionsItemId = null,
        playlistSourceItemId = null,
        playlistNameRequest = null,
        playlistNameDraft = "",
        playlistMutationError = null,
        pendingPlaylistDelete = null,
    )

internal fun pathNode(
    root: BrowserItem,
    path: List<String>,
): BrowserItem? {
    var node = root
    path.forEach { id -> node = node.children.firstOrNull { it.id == id } ?: return null }
    return node
}

internal fun reloadAnchorIndex(
    oldOrder: List<String>,
    newOrder: List<String>,
    desired: String?,
): Int {
    if (newOrder.isEmpty()) return 0
    newOrder.indexOf(desired).takeIf { it >= 0 }?.let { return it }
    val oldIndex = oldOrder.indexOf(desired).takeIf { it >= 0 } ?: 0
    oldOrder.drop(oldIndex + 1).firstOrNull(newOrder::contains)?.let { return newOrder.indexOf(it) }
    oldOrder
        .take(oldIndex)
        .asReversed()
        .firstOrNull(newOrder::contains)
        ?.let { return newOrder.indexOf(it) }
    return 0
}

internal fun navigationOrderRequests(
    old: PlayerUiState,
    projectedRoot: BrowserItem,
    roots: Map<HierarchyRoot, HierarchyNavigationSnapshot>,
    containers: Map<List<String>, HierarchyNavigationSnapshot>,
): List<Pair<List<BrowserItem>, List<AzGroup>>> {
    val scopes =
        buildList {
            add(old.browserPath to old.azSteps)
            (roots.values + containers.values).forEach { add(it.browserPath to it.azSteps) }
        }
    return scopes.flatMap { (originalPath, steps) ->
        (0..originalPath.size).flatMap { length ->
            val path = originalPath.take(length)
            val scopeSteps = if (path == originalPath) steps else containers[path]?.azSteps.orEmpty()
            listOf(
                pathNode(old.root, path)?.children.orEmpty() to scopeSteps,
                pathNode(projectedRoot, path)?.children.orEmpty() to scopeSteps,
            )
        }
    }
}
