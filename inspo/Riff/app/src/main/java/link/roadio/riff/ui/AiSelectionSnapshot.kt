package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.selection.AiFeaturePoint
import link.roadio.riff.player.core.selection.AiHistoryCount
import link.roadio.riff.player.core.selection.AiPreference
import link.roadio.riff.player.core.selection.AiQueueCandidate
import link.roadio.riff.player.library.PlayerPteFeature
import link.roadio.riff.player.storage.AiPlaybackHistorySnapshot

internal data class AiSelectionCandidateInput(
    val tracks: List<BrowserItem>,
    val root: BrowserItem,
    val reactions: Map<String, ReactionState>,
    val featuresByMediaId: Map<MediaId, AiFeaturePoint>,
    val pteByMediaId: Map<MediaId, PlayerPteFeature>,
    val audiobookMediaIds: Set<MediaId>,
    val effectiveBookMediaIds: Set<MediaId>,
    val mediaAliases: Map<MediaId, MediaId> = emptyMap(),
)

/** Builds the immutable Player-owned input consumed by the toolkit-neutral selector. */
internal fun buildAiQueueCandidates(
    input: AiSelectionCandidateInput,
    history: AiPlaybackHistorySnapshot,
): List<AiQueueCandidate> {
    val tracks = input.tracks
    val root = input.root
    val reactions = input.reactions
    val featuresByMediaId = input.featuresByMediaId
    val pteByMediaId = input.pteByMediaId
    val audiobookMediaIds = input.audiobookMediaIds
    val effectiveBookMediaIds = input.effectiveBookMediaIds
    val bannedMediaIds = effectiveAiBannedMediaIds(root, reactions)
    val albumPreferences = linkedMapOf<MediaId, MutableList<AiPreference>>()
    val artistPreferences = linkedMapOf<MediaId, MutableList<AiPreference>>()
    val genrePreferences = linkedMapOf<MediaId, MutableList<AiPreference>>()
    val directoryPaths = linkedMapOf<MediaId, MutableList<List<AiPreference>>>()

    fun addEntityPreferences(
        categoryId: String,
        destination: MutableMap<MediaId, MutableList<AiPreference>>,
    ) {
        val category = root.children.firstOrNull { it.id == categoryId } ?: return
        category.children.forEach { entity ->
            val preference = reactions[entity.reactionKey].toAiPreference()
            entity.mediaIds().forEach { mediaId ->
                destination.getOrPut(mediaId, ::mutableListOf).add(preference)
            }
        }
    }

    addEntityPreferences("category:albums", albumPreferences)
    addEntityPreferences("category:artists", artistPreferences)
    addEntityPreferences("category:genres", genrePreferences)

    fun visitFolder(
        item: BrowserItem,
        nearestFirst: List<AiPreference>,
    ) {
        val path =
            if (item.kind == BrowserKind.FOLDER) {
                listOf(reactions[item.reactionKey].toAiPreference()) + nearestFirst
            } else {
                nearestFirst
            }
        if (item.kind == BrowserKind.TRACK) {
            item.mediaId?.let { mediaId ->
                val paths = directoryPaths.getOrPut(mediaId, ::mutableListOf)
                if (path !in paths) paths += path
            }
        } else {
            item.children.forEach { child -> visitFolder(child, path) }
        }
    }
    root.children
        .firstOrNull { it.id == "category:folders" }
        ?.children
        ?.forEach { visitFolder(it, emptyList()) }

    val startsByMediaId =
        history.starts.groupBy { input.mediaAliases[it.mediaId] ?: it.mediaId }.mapValues { (_, starts) ->
            starts.map { AiHistoryCount(it.historyGeneration, it.starts) }
        }

    return tracks.mapNotNull { track ->
        val mediaId = track.mediaId ?: return@mapNotNull null
        if (mediaId in audiobookMediaIds || mediaId in effectiveBookMediaIds) return@mapNotNull null
        val feature = featuresByMediaId[mediaId]
        val pte = pteByMediaId[mediaId]
        AiQueueCandidate(
            mediaId = mediaId,
            sourceBindingId = track.sourceBindingId,
            playable = track.playable,
            effectivelyBanned = mediaId in bannedMediaIds,
            pte = pte?.value,
            pteConfidence = pte?.confidence,
            arousal = feature?.arousal,
            arousalConfidence = feature?.arousalConfidence,
            valence = feature?.valence,
            valenceConfidence = feature?.valenceConfidence,
            trackPreference = reactions[track.reactionKey].toAiPreference(),
            directoryPreferencePaths = directoryPaths[mediaId].orEmpty().toList(),
            albumPreferences = albumPreferences[mediaId].orEmpty().toList(),
            artistPreferences = artistPreferences[mediaId].orEmpty().toList(),
            genrePreferences = genrePreferences[mediaId].orEmpty().toList(),
            playHistory = startsByMediaId[mediaId].orEmpty(),
        )
    }
}

private fun BrowserItem.mediaIds(): Set<MediaId> =
    buildSet {
        fun visit(item: BrowserItem) {
            item.mediaId?.let(::add)
            item.children.forEach(::visit)
        }
        visit(this@mediaIds)
    }

private fun ReactionState?.toAiPreference(): AiPreference =
    when (this?.preference ?: Preference.NEUTRAL) {
        Preference.NEUTRAL -> AiPreference.NEUTRAL
        Preference.DISLIKED -> AiPreference.DISLIKED
        Preference.LIKED -> AiPreference.LIKED
    }
