package link.roadio.riff.ui

import android.content.Intent
import link.roadio.riff.player.library.PlayerCuetreeClient
import link.roadio.riff.player.playback.PlayerPlaybackConnection
import link.roadio.riff.player.storage.PlayerSafGrantStore
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one staged media-folder session and its acknowledged Indexer barrier. */
internal class PlayerSafGrantEditor(
    private val host: Host,
) {
    interface Host {
        val grantStore: PlayerSafGrantStore
        val executor: Executor
        val libraryClient: PlayerCuetreeClient
        var libraryClientEnabled: Boolean
        val selectedStorageAvailable: Boolean
        val playbackConnection: PlayerPlaybackConnection

        fun currentGrants(): List<SafGrantUiItem>

        fun updateState(transform: (PlayerUiState) -> PlayerUiState)
    }

    val commitInProgress = AtomicBoolean()
    private val safGrantCommitInProgress get() = commitInProgress
    private val safGrantStore get() = host.grantStore
    private val workers get() = host.executor
    private val libraryClient get() = host.libraryClient
    private var libraryClientEnabled: Boolean
        get() = host.libraryClientEnabled
        set(value) {
            host.libraryClientEnabled = value
        }
    private val selectedStorageAvailable get() = host.selectedStorageAvailable
    private val playbackConnection get() = host.playbackConnection

    private fun currentSafGrants() = host.currentGrants()

    private fun updateState(transform: (PlayerUiState) -> PlayerUiState) = host.updateState(transform)

    private var safGrantDraftBase: List<SafGrantUiItem> = emptyList()
    private val safGrantDraftAdditions = linkedMapOf<String, SafGrantUiItem>()
    private val safGrantDraftFlags = linkedMapOf<String, Int>()
    private val safGrantDraftRemovals = linkedSetOf<String>()

    fun openSafGrantSelector(old: PlayerUiState): PlayerUiState {
        safGrantDraftBase = currentSafGrants()
        safGrantDraftAdditions.clear()
        safGrantDraftFlags.clear()
        safGrantDraftRemovals.clear()
        return old.copy(
            screen = PlayerScreen.SAF_GRANTS,
            page = 0,
            selectorIndex = 0,
            selectorOffset = 0,
            safGrants = safGrantDraftBase,
            pendingSafGrantRemoval = null,
        )
    }

    fun cancelSafGrantSelector(old: PlayerUiState): PlayerUiState {
        clearSafGrantDraft()
        return old.copy(
            screen = PlayerScreen.SETTINGS_MORE,
            page = 0,
            selectorIndex = 0,
            selectorOffset = 0,
            safGrants = currentSafGrants(),
            pendingSafGrantRemoval = null,
        )
    }

    fun stageSafGrantAddition(
        old: PlayerUiState,
        uri: String,
        grantFlags: Int,
    ): PlayerUiState =
        runCatching {
            require(grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val preview = safGrantStore.preview(uri).let { SafGrantUiItem(it.uri, it.label) }
            safGrantDraftRemovals.remove(uri)
            if (safGrantDraftBase.none { it.id == uri }) {
                safGrantDraftAdditions[uri] = preview
                safGrantDraftFlags[uri] = grantFlags
            } else if (grantFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 &&
                !safGrantStore.hasPersistedWriteGrant(uri)
            ) {
                safGrantDraftFlags[uri] = grantFlags
            } else {
                safGrantDraftFlags.remove(uri)
            }
            old.copy(
                safGrants = effectiveSafGrantDraft(),
                pendingSafGrantRemoval = null,
                status = "Media folder staged; press OK to apply",
            )
        }.getOrElse {
            old.copy(status = "Media folder was not added")
        }

    fun stageSafGrantRemoval(old: PlayerUiState): PlayerUiState {
        val grant = old.pendingSafGrantRemoval ?: return old
        safGrantDraftFlags.remove(grant.id)
        if (safGrantDraftAdditions.remove(grant.id) == null) {
            safGrantDraftRemovals += grant.id
        }
        return old.copy(
            pendingSafGrantRemoval = null,
            safGrants = effectiveSafGrantDraft(),
            status = "Media folder removal staged; press OK to apply",
        )
    }

    fun saveSafGrantSelector(old: PlayerUiState): PlayerUiState {
        val additions = safGrantDraftFlags.toMap()
        val removals = safGrantDraftRemovals.toSet()
        if (additions.isEmpty() && removals.isEmpty()) {
            clearSafGrantDraft()
            return old.copy(
                screen = PlayerScreen.SETTINGS_MORE,
                page = 0,
                selectorIndex = 0,
                selectorOffset = 0,
                safGrants = currentSafGrants(),
                pendingSafGrantRemoval = null,
                status = "Media folders unchanged",
            )
        }
        if (!safGrantCommitInProgress.compareAndSet(false, true)) return old
        workers.execute {
            val indexerWasBound = libraryClientEnabled
            try {
                runSafGrantChangeBarrier(
                    indexerWasBound = indexerWasBound,
                    quiesceIndexer = libraryClient::quiesceForSafGrantChange,
                    mutateGrants = { safGrantStore.commitDraft(additions, removals) },
                    resumeIndexer = libraryClient::resumeAfterSafGrantChange,
                )
                val grants = currentSafGrants()
                val requested =
                    if (indexerWasBound) {
                        true
                    } else if (grants.isNotEmpty() && selectedStorageAvailable) {
                        libraryClientEnabled = true
                        libraryClient.start()
                    } else {
                        false
                    }
                check(requested || !selectedStorageAvailable) { "Indexer is unavailable" }
                if (grants.isNotEmpty()) playbackConnection.refreshLibraryClassification()
                updateState { current ->
                    clearSafGrantDraft()
                    safGrantCommitInProgress.set(false)
                    current.copy(
                        screen = PlayerScreen.SETTINGS_MORE,
                        page = 0,
                        selectorIndex = 0,
                        selectorOffset = 0,
                        safGrants = grants,
                        pendingSafGrantRemoval = null,
                        safGrantCommitInProgress = false,
                        status =
                            if (requested) {
                                "Media folders saved; indexing requested"
                            } else {
                                "Media folders saved; indexing unavailable until restart"
                            },
                    )
                }
            } catch (error: Throwable) {
                updateState { current ->
                    safGrantCommitInProgress.set(false)
                    current.copy(
                        pendingSafGrantRemoval = null,
                        safGrants = effectiveSafGrantDraft(),
                        safGrantCommitInProgress = false,
                        status = "Media folders were not saved: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
        return old.copy(safGrantCommitInProgress = true)
    }

    private fun clearSafGrantDraft() {
        safGrantDraftBase = emptyList()
        safGrantDraftAdditions.clear()
        safGrantDraftFlags.clear()
        safGrantDraftRemovals.clear()
    }

    private fun effectiveSafGrantDraft(): List<SafGrantUiItem> =
        (safGrantDraftBase.filterNot { it.id in safGrantDraftRemovals } + safGrantDraftAdditions.values)
            .distinctBy(SafGrantUiItem::id)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, SafGrantUiItem::label).thenBy(SafGrantUiItem::id))
}
