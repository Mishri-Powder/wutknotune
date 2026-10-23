package link.roadio.riff.player.storage

import link.roadio.riff.indexer.core.media.MediaId

/** Durable preference and ban are orthogonal; ban never implies transport. */
enum class StoredPreference { NEUTRAL, DISLIKED, LIKED }

data class StoredReaction(
    val preference: StoredPreference = StoredPreference.NEUTRAL,
    val ownBanned: Boolean = false,
)

enum class StoredContentMode { MUSIC, AUDIOBOOK }

enum class StoredAudiobookOverride { BOOK, MUSIC }

data class StoredAiSelection(
    val queueSize: Int = 20,
    val pteBandsMask: Int = 0,
    val arousal: Float = 0f,
    val valence: Float = 0f,
) {
    init {
        require(queueSize in setOf(10, 20, 50))
        require(pteBandsMask in 0..7)
        require(arousal.isFinite() && arousal in -1f..1f)
        require(valence.isFinite() && valence in -1f..1f)
    }
}

enum class PlaybackQueueKind { MANUAL, PERMANENT_PLAYLIST, AI }

data class PlaybackQueueOccurrence(
    val occurrenceId: Long,
    val ordinal: Int,
    val shuffleOrdinal: Int,
    val mediaId: MediaId,
)

data class PlaybackCheckpoint(
    val occurrenceId: Long?,
    val positionMs: Long = 0,
    val playWhenReady: Boolean = false,
    val repeat: Boolean = false,
    val shuffle: Boolean = false,
    val chosenBindingId: Long? = null,
)

data class PlaybackQueueSnapshot(
    val revisionId: Long,
    val kind: PlaybackQueueKind,
    val sourceProfileId: Int,
    val temporary: Boolean,
    val occurrences: List<PlaybackQueueOccurrence>,
    val checkpoint: PlaybackCheckpoint,
    val contextKey: String? = null,
    val albumContext: Boolean = false,
    val bookTitleContext: Boolean = false,
    val bookTitleName: String? = null,
    val bookTitleMembers: List<MediaId> = emptyList(),
    val bookQueueOrdinals: List<Int> = emptyList(),
)

enum class ListeningEventType { PLAY_STARTED, USER_SKIPPED, COMPLETED, CENSORED_END }

data class ListeningEvent(
    val eventId: Long,
    val profileId: Int,
    val mediaId: MediaId,
    val playInstanceId: Long,
    val historyGeneration: Long,
    val eventType: ListeningEventType,
    val positionMs: Long,
    val durationMs: Long?,
    val stateRevision: Long,
)

data class PlaybackStartCount(
    val mediaId: MediaId,
    val historyGeneration: Long,
    val starts: Long,
)

data class AiPlaybackHistorySnapshot(
    val currentGeneration: Long,
    val starts: List<PlaybackStartCount>,
)

data class MediaPlaybackTotal(
    val profileId: Int,
    val mediaId: MediaId,
    val totalStartedCount: Long,
    val lastStartGeneration: Long,
    val stateRevision: Long,
)

enum class PlaylistKind { MUSIC, BOOK }

data class BookmarkMark(
    val markId: Long,
    val ordinal: Int,
    val positionUs: Long,
    val label: String,
)

data class PermanentPlaylist(
    val playlistId: Long,
    val name: String,
    val revisionId: Long,
    val occurrences: List<PlaylistOccurrence>,
    val kind: PlaylistKind = PlaylistKind.MUSIC,
    val titleKey: String? = null,
    val marks: List<BookmarkMark> = emptyList(),
)

data class PlaylistOccurrence(
    val occurrenceId: Long,
    val ordinal: Int,
    val mediaId: MediaId,
)
