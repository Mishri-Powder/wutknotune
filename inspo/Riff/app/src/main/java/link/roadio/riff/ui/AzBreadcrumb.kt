package link.roadio.riff.ui

internal fun clearAzSearch(state: PlayerUiState): PlayerUiState {
    if (state.screen != PlayerScreen.AZ_SEARCH) return state
    return state.copy(
        screen = PlayerScreen.HIERARCHY,
        azSteps = emptyList(),
        azDraftSteps = null,
        azSnapshot = null,
        azAutoEntryPending = false,
        azAutoApplyPending = false,
        azApplyRevision = state.azApplyRevision + 1,
        page = 0,
        selectorIndex = 0,
        selectorOffset = 0,
        focusedItemId =
            state
                .currentNode()
                .children
                .firstOrNull()
                ?.id,
    )
}

/** A filter belongs to its container, never to the container's descendants. */
data class AzBreadcrumbScope(
    val path: List<String>,
    val label: String,
    val steps: List<AzGroup>,
)

internal fun azBreadcrumbScopes(
    root: BrowserItem,
    path: List<String>,
    currentSteps: List<AzGroup>,
    rememberedSteps: (List<String>) -> List<AzGroup>,
): List<AzBreadcrumbScope> {
    if (path.isEmpty()) return listOf(AzBreadcrumbScope(emptyList(), root.label, currentSteps))
    val scopes = mutableListOf<AzBreadcrumbScope>()
    var node = root
    for (depth in path.indices) {
        node = node.children.firstOrNull { it.id == path[depth] } ?: return emptyList()
        val prefix = path.take(depth + 1)
        scopes += AzBreadcrumbScope(prefix, node.label, if (prefix == path) currentSteps else rememberedSteps(prefix))
    }
    return scopes
}

internal fun PlayerUiState.azBreadcrumbScopesForDisplay(): List<AzBreadcrumbScope> {
    val scopes =
        azBreadcrumbs.ifEmpty {
            azBreadcrumbScopes(root, browserPath, azSteps) { emptyList() }
        }
    return scopes.map { scope ->
        if (screen == PlayerScreen.AZ_SEARCH && scope.path == browserPath) {
            scope.copy(steps = azDraftSteps ?: azSteps)
        } else {
            scope
        }
    }
}

/** Accept only displayed prefixes of current ancestors; never reinterpret stale targets. */
internal fun selectAzBreadcrumb(
    state: PlayerUiState,
    action: PlayerUiAction.SelectAzBreadcrumb,
): PlayerUiState {
    val searching = state.screen == PlayerScreen.AZ_SEARCH
    if (state.screen != PlayerScreen.HIERARCHY && !searching) return state
    if (action.inSearch != searching) return state
    if (action.path.size > state.browserPath.size || state.browserPath.take(action.path.size) != action.path) return state
    val scope = state.azBreadcrumbScopesForDisplay().firstOrNull { it.path == action.path } ?: return state
    if (action.steps.size > scope.steps.size || scope.steps.take(action.steps.size) != action.steps) return state
    var target = state.root
    for (id in action.path) {
        target = target.children.firstOrNull { it.id == id } ?: return state
    }
    val applied = action.steps
    return state.copy(
        browserPath = action.path,
        page = 0,
        selectorIndex = 0,
        selectorOffset = 0,
        focusedItemId = null,
        azSteps = applied,
        azDraftSteps = if (searching) action.steps else null,
        azSnapshot = state.azSnapshot?.takeIf { it.query.source === target.children && (searching || it.query.applied == applied) },
        azAutoEntryPending = !searching && (action.path != state.browserPath || applied != state.azSteps),
        azAutoApplyPending = false,
        azApplyRevision = state.azApplyRevision + 1,
    )
}
