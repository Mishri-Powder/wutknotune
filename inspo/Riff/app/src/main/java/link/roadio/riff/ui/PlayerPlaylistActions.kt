package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.storage.PlayerStateStore
import java.util.UUID
import java.util.concurrent.Executor

/** UI coordination only; the store remains the sole playlist transaction authority. */
internal class PlayerPlaylistActions(
    private val host: Host,
) {
    interface Host {
        val state: PlayerUiState
        val profile: Int
        val store: PlayerStateStore?
        val executor: Executor

        fun beginWrite(): Boolean

        fun sourceMediaIds(sourceId: String?): List<MediaId>

        fun projectRoot(profile: Int): BrowserItem

        fun publish(
            root: BrowserItem,
            profile: Int,
            transform: (PlayerUiState, BrowserItem) -> PlayerUiState,
        )

        fun updateState(transform: (PlayerUiState) -> PlayerUiState)

        fun clearAiQueue()

        fun playlistSteps(playlistId: Long): List<AzGroup>
    }

    private val state get() = host.state
    private val activeProfileId get() = host.profile
    private val playerStateStore get() = host.store
    private val workers get() = host.executor

    private fun beginPlaylistWrite() = host.beginWrite()

    private fun sourceMediaIds(sourceId: String?) = host.sourceMediaIds(sourceId)

    private fun projectPlaylistRoot(profile: Int) = host.projectRoot(profile)

    private fun updatePlaylistProjection(
        root: BrowserItem,
        profile: Int,
        transform: (PlayerUiState, BrowserItem) -> PlayerUiState,
    ) = host.publish(root, profile, transform)

    private fun updateState(transform: (PlayerUiState) -> PlayerUiState) = host.updateState(transform)

    fun addSourceToPlaylist(playlistId: Long) {
        val fromAi = state.playlistSourceItemId == AI_PLAYLIST_SOURCE_ITEM_ID
        val mediaIds = sourceMediaIds(state.playlistSourceItemId)
        if (mediaIds.isEmpty()) return updateState { it.copy(status = "No playable tracks to add") }
        val store = playerStateStore ?: return updateState { it.copy(status = "Player state is unavailable") }
        if (!beginPlaylistWrite()) return
        val profile = activeProfileId
        val commandId = UUID.randomUUID().toString()
        workers.execute {
            runCatching { store.appendToPlaylist(playlistId, mediaIds, commandId, profile) }
                .onSuccess { playlist ->
                    if (fromAi) host.clearAiQueue()
                    val projected = projectPlaylistRoot(profile)
                    updatePlaylistProjection(projected, profile) { old, readyRoot ->
                        if (old.contentMode == ContentMode.AUDIOBOOK) {
                            old.copy(
                                root = readyRoot,
                                screen = PlayerScreen.ROW_ACTIONS,
                                playlistSourceItemId = null,
                                playlistMutationInProgress = false,
                                status = "Added ${mediaIds.size} tracks to ${playlist.name}",
                            )
                        } else {
                            val opened =
                                openCommittedPlaylist(
                                    state = old,
                                    projectedRoot = readyRoot,
                                    playlistId = playlist.playlistId,
                                    steps =
                                        host.playlistSteps(playlist.playlistId),
                                    status =
                                        "Added ${mediaIds.size} track" +
                                            "${if (mediaIds.size == 1) "" else "s"} to ${playlist.name}",
                                )
                            opened
                        }
                    }
                }.onFailure { playlistMutationFailed(it, profile) }
        }
    }

    fun confirmPlaylistName() {
        val request = state.playlistNameRequest ?: return
        val name = state.playlistNameDraft
        val sourceId = state.playlistSourceItemId
        val fromAi = sourceId == AI_PLAYLIST_SOURCE_ITEM_ID
        val mediaIds = if (request.operation == PlaylistNameOperation.CREATE) sourceMediaIds(sourceId) else emptyList()
        if (request.operation == PlaylistNameOperation.CREATE && mediaIds.isEmpty()) {
            return playlistMutationFailed(IllegalArgumentException("No playable tracks to add"))
        }
        val store = playerStateStore ?: return playlistMutationFailed(IllegalArgumentException("Player state is unavailable"))
        if (!beginPlaylistWrite()) return
        val profile = activeProfileId
        val commandId = UUID.randomUUID().toString()
        workers.execute {
            runCatching {
                when (request.operation) {
                    PlaylistNameOperation.CREATE -> {
                        store.createPlaylist(name, mediaIds, commandId, profile)
                    }

                    PlaylistNameOperation.RENAME -> {
                        val playlistId = requireNotNull(request.playlistId)
                        store.renamePlaylist(playlistId, name, profile)
                        requireNotNull(store.listPlaylists(profile).firstOrNull { it.playlistId == playlistId })
                    }
                }
            }.onSuccess { playlist ->
                if (fromAi) host.clearAiQueue()
                val projected = projectPlaylistRoot(profile)
                updatePlaylistProjection(projected, profile) { old, readyRoot ->
                    if (request.operation == PlaylistNameOperation.CREATE) {
                        if (old.contentMode == ContentMode.AUDIOBOOK) {
                            old.copy(
                                root = readyRoot,
                                screen = PlayerScreen.ROW_ACTIONS,
                                playlistSourceItemId = null,
                                playlistNameRequest = null,
                                playlistNameDraft = "",
                                playlistMutationError = null,
                                playlistMutationInProgress = false,
                                status = "Created playlist ${playlist.name} with ${mediaIds.size} tracks",
                            )
                        } else {
                            val opened =
                                openCommittedPlaylist(
                                    state = old,
                                    projectedRoot = readyRoot,
                                    playlistId = playlist.playlistId,
                                    steps =
                                        host.playlistSteps(playlist.playlistId),
                                    status =
                                        "Created playlist ${playlist.name} with ${mediaIds.size} track" +
                                            if (mediaIds.size == 1) "" else "s",
                                )
                            opened
                        }
                    } else {
                        old.copy(
                            root = readyRoot,
                            screen = PlayerScreen.ROW_ACTIONS,
                            page = 0,
                            playlistSourceItemId = null,
                            playlistNameRequest = null,
                            playlistNameDraft = "",
                            playlistMutationError = null,
                            playlistMutationInProgress = false,
                            status = "Playlist renamed to ${playlist.name}",
                        )
                    }
                }
            }.onFailure { playlistMutationFailed(it, profile) }
        }
    }

    fun confirmPlaylistDelete() {
        val request = state.pendingPlaylistDelete ?: return
        val store = playerStateStore ?: return playlistMutationFailed(IllegalArgumentException("Player state is unavailable"))
        if (!beginPlaylistWrite()) return
        val profile = activeProfileId
        workers.execute {
            runCatching {
                check(store.deletePlaylist(request.playlistId, profile))
                request
            }.onSuccess {
                val projected = projectPlaylistRoot(profile)
                updatePlaylistProjection(projected, profile) { old, readyRoot ->
                    old.copy(
                        root = readyRoot,
                        screen = old.hierarchyScreen(),
                        actionsItemId = null,
                        pendingPlaylistDelete = null,
                        playlistMutationInProgress = false,
                        status = "Deleted playlist ${request.name}",
                    )
                }
            }.onFailure { playlistMutationFailed(it, profile) }
        }
    }

    fun removePlaylistOccurrence(
        playlistId: Long,
        occurrenceId: Long,
    ) {
        val store = playerStateStore ?: return updateState { it.copy(status = "Player state is unavailable") }
        if (!beginPlaylistWrite()) return
        val profile = activeProfileId
        workers.execute {
            runCatching { store.removePlaylistOccurrence(playlistId, occurrenceId, profile) }
                .onSuccess { playlist ->
                    val projected = projectPlaylistRoot(profile)
                    updatePlaylistProjection(projected, profile) { old, readyRoot ->
                        val path =
                            old.browserPath.takeIf { pathNode(readyRoot, it) != null }
                                ?: listOf(HierarchyRoot.PLAYLISTS.categoryId)
                        restoreHierarchyContainer(old.copy(root = readyRoot), path, emptyList()).copy(
                            actionsItemId = null,
                            playlistMutationInProgress = false,
                            status = "Removed track from ${playlist.name}",
                        )
                    }
                }.onFailure { playlistMutationFailed(it, profile) }
        }
    }

    fun playlistMutationFailed(
        error: Throwable,
        profile: Int? = null,
    ) {
        updateState {
            if (profile != null && activeProfileId != profile) return@updateState it.copy(playlistMutationInProgress = false)
            val message =
                if (error is IllegalArgumentException) {
                    error.message ?: "Could not update playlist"
                } else {
                    "Could not update playlist. Try again."
                }
            it.copy(
                playlistMutationInProgress = false,
                playlistMutationError = message,
                status = "Playlist update failed: $message",
            )
        }
    }

    fun removeBookmark(
        playlistId: Long,
        markId: Long,
    ) {
        val store = playerStateStore ?: return updateState { it.copy(status = "Player state is unavailable") }
        if (!beginPlaylistWrite()) return
        val profile = activeProfileId
        workers.execute {
            runCatching { store.removeBookmark(playlistId, markId, profile) }
                .onSuccess { removed ->
                    updatePlaylistProjection(projectPlaylistRoot(profile), profile) { old, root ->
                        val path =
                            old.browserPath.takeIf { pathNode(root, it) != null }
                                ?: listOf(HierarchyRoot.PLAYLISTS.categoryId)
                        restoreHierarchyContainer(old.copy(root = root), path, emptyList()).copy(
                            actionsItemId = null,
                            playlistMutationInProgress = false,
                            status = if (removed) "Bookmark removed" else "Bookmark is no longer present",
                        )
                    }
                }.onFailure { playlistMutationFailed(it, profile) }
        }
    }
}
