@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import link.roadio.riff.player.library.PlayerLibraryModel
import link.roadio.riff.player.storage.PlaybackQueueKind
import link.roadio.riff.player.storage.PlaybackQueueOccurrence
import link.roadio.riff.player.storage.PlaybackQueueSnapshot
import link.roadio.riff.ui.PlaybackSourceRef
import link.roadio.riff.ui.queueContextTracks

/**
 * Media3 executes a durable queue revision; a new library is only a source/metadata lookup.
 * Occurrence identity, repeated positions and the saved permutation belong to that revision.
 * Authority: https://wiki.271.pics/Riff/LATEST/player-queues#queues-playlists-and-history
 */
internal fun prepareQueue(
    queue: PlaybackQueueSnapshot,
    model: PlayerLibraryModel,
): PreparedQueue {
    val occurrences = queue.occurrences.sortedBy(PlaybackQueueOccurrence::ordinal)
    val context = if (queue.kind == PlaybackQueueKind.PERMANENT_PLAYLIST) emptyList() else queueContextTracks(model.root, queue.contextKey)
    val tracks = model.tracksByMediaId
    val planned =
        occurrences.associate { occurrence ->
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
            val contextTrack = context.getOrNull(occurrence.ordinal)?.takeIf { it.mediaId == occurrence.mediaId }
            val selectedTrack = contextTrack ?: tracks[occurrence.mediaId]
            occurrence.occurrenceId to
                PlannedOccurrence(
                    queueRevisionId = queue.revisionId,
                    occurrence = occurrence,
                    sources =
                        preferredSources(
                            queue,
                            occurrence,
                            model.playbackSourcesByMediaId[occurrence.mediaId].orEmpty(),
                            contextTrack?.sourceBindingId,
                        ),
                    title = selectedTrack?.label ?: "Unknown track",
                    artist = selectedTrack?.artist.orEmpty(),
                    album = selectedTrack?.album.orEmpty(),
                )
        }
    return PreparedQueue(
        QueueExecutionPlan(queue, planned),
        occurrences,
        occurrences.map { mediaItem(requireNotNull(planned[it.occurrenceId])) },
        occurrences.indexOfFirst { it.occurrenceId == queue.checkpoint.occurrenceId }.coerceAtLeast(0),
        occurrences.indices.sortedBy { occurrences[it].shuffleOrdinal }.toIntArray(),
    )
}

private fun preferredSources(
    queue: PlaybackQueueSnapshot,
    occurrence: PlaybackQueueOccurrence,
    sources: List<PlaybackSourceRef>,
    albumBindingId: Long?,
): List<PlaybackSourceRef> {
    val preferred =
        queue.checkpoint.chosenBindingId.takeIf { queue.checkpoint.occurrenceId == occurrence.occurrenceId } ?: albumBindingId
    return if (preferred == null) sources else sources.sortedBy { it.bindingId != preferred }
}

private fun mediaItem(planned: PlannedOccurrence): MediaItem {
    val extras = metadataExtras(planned, null)
    val metadata =
        MediaMetadata
            .Builder()
            .setTitle(planned.title)
            .setArtist(planned.artist)
            .setAlbumTitle(planned.album)
            .setExtras(extras)
            .build()
    val uri =
        Uri
            .Builder()
            .scheme(PlaybackMetadata.URI_SCHEME)
            .authority(planned.queueRevisionId.toString())
            .appendPath(planned.occurrence.occurrenceId.toString())
            .build()
    val clip =
        MediaItem.ClippingConfiguration
            .Builder()
            .apply {
                planned.sources
                    .firstOrNull()
                    ?.beginUs
                    ?.let(::setStartPositionUs)
                planned.sources
                    .firstOrNull()
                    ?.endUs
                    ?.let(::setEndPositionUs)
            }.build()
    require(
        planned.sources.all {
            it.beginUs == planned.sources.firstOrNull()?.beginUs &&
                it.endUs == planned.sources.firstOrNull()?.endUs
        },
    )
    return MediaItem
        .Builder()
        .setMediaId(PlaybackMetadata.mediaItemId(planned.occurrence.occurrenceId))
        .setUri(uri)
        .setClippingConfiguration(clip)
        .setMediaMetadata(metadata)
        .build()
}

internal fun metadataExtras(
    planned: PlannedOccurrence,
    bindingId: Long?,
): Bundle =
    Bundle().apply {
        putLong(PlaybackMetadata.EXTRA_QUEUE_REVISION_ID, planned.queueRevisionId)
        putLong(PlaybackMetadata.EXTRA_OCCURRENCE_ID, planned.occurrence.occurrenceId)
        putMediaId(planned.occurrence.mediaId)
        bindingId?.let { putLong(PlaybackMetadata.EXTRA_BINDING_ID, it) }
    }

internal data class PreparedQueue(
    val plan: QueueExecutionPlan,
    val occurrences: List<PlaybackQueueOccurrence>,
    val items: List<MediaItem>,
    val startIndex: Int,
    val shuffleOrder: IntArray,
)

internal data class QueueExecutionPlan(
    val queue: PlaybackQueueSnapshot,
    val occurrences: Map<Long, PlannedOccurrence>,
)

internal data class PlannedOccurrence(
    val queueRevisionId: Long,
    val occurrence: PlaybackQueueOccurrence,
    val sources: List<PlaybackSourceRef>,
    val title: String,
    val artist: String,
    val album: String,
)
