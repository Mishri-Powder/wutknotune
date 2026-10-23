package link.roadio.riff.player.library

import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.selection.AiFeaturePoint
import link.roadio.riff.player.storage.StoredAudiobookOverride
import link.roadio.riff.player.storage.StoredContentMode
import link.roadio.riff.ui.BrowserItem
import link.roadio.riff.ui.PlaybackSourceRef
import link.roadio.riff.ui.TocChapter

data class PlayerLibraryProjection(
    val contentMode: StoredContentMode = StoredContentMode.MUSIC,
    val audiobookOverrides: Map<String, StoredAudiobookOverride> = emptyMap(),
)

data class PlayerLibraryModel(
    val root: BrowserItem,
    val tracks: List<BrowserItem>,
    val retainedTracks: List<BrowserItem> = tracks,
    val playbackSourcesByMediaId: Map<MediaId, List<PlaybackSourceRef>>,
    val generation: Long,
    val mediaIds: Set<MediaId>,
    val mediaAliases: Map<MediaId, MediaId> = emptyMap(),
    val aiFeaturesByMediaId: Map<MediaId, AiFeaturePoint> = emptyMap(),
    val pteByMediaId: Map<MediaId, PlayerPteFeature> = emptyMap(),
    val topInferredGenreByMediaId: Map<MediaId, PlayerTopInferredGenre> = emptyMap(),
    val augmentationGeneration: Long? = null,
    /** Automatic book membership supplied by the Indexer projection. */
    val audiobookMediaIds: Set<MediaId> = emptySet(),
    val effectiveBookMediaIds: Set<MediaId> = emptySet(),
    val tocMarkersByBindingId: Map<Long, List<Long>> = emptyMap(),
    val tocChaptersByBindingId: Map<Long, List<TocChapter>> = emptyMap(),
    val projection: PlayerLibraryProjection = PlayerLibraryProjection(),
    val catalogsReady: Boolean = false,
    /** Canonical -18 LUFS ReplayGain data keyed by Media.ID. */
    val replayGainByMediaId: Map<MediaId, PlayerReplayGainValue> = emptyMap(),
    val replayGainGeneration: Long? = null,
) {
    val tracksByMediaId: Map<MediaId, BrowserItem> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val actual = tracks.associateBy { requireNotNull(it.mediaId) }
        actual +
            mediaAliases.mapNotNull { (alias, canonical) ->
                actual[canonical]?.let { alias to it }
            }
    }
}

data class PlayerLibraryModels(
    val music: PlayerLibraryModel,
    val audiobook: PlayerLibraryModel,
) {
    fun forMode(mode: StoredContentMode): PlayerLibraryModel = if (mode == StoredContentMode.AUDIOBOOK) audiobook else music
}
