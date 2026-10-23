package link.roadio.riff.player.library

import link.roadio.riff.indexer.core.media.MediaId
import java.util.Locale

data class PlayerPteFeature(
    val value: Float,
    val confidence: Float,
)

data class PlayerInferredGenre(
    val genreId: Long,
    val confidence: Float,
)

data class PlayerTopInferredGenre(
    val sourceGenreId: Long,
    val label: String,
    val confidence: Float,
)

internal fun projectTopInferredGenres(
    inferredGenresByMediaId: Map<MediaId, List<PlayerInferredGenre>>,
    genreNames: GnreDisplayNames,
    systemLocale: Locale,
    excludedGenreIds: Set<Long>,
): Map<MediaId, PlayerTopInferredGenre> =
    buildMap {
        inferredGenresByMediaId.forEach { (mediaId, candidates) ->
            val selected =
                candidates
                    .sortedWith(
                        compareByDescending<PlayerInferredGenre> { it.confidence }
                            .thenBy(PlayerInferredGenre::genreId),
                    ).firstNotNullOfOrNull { candidate ->
                        if (candidate.genreId in excludedGenreIds) return@firstNotNullOfOrNull null
                        val visibleGenreId = genreNames.visibleGenreId(candidate.genreId) ?: return@firstNotNullOfOrNull null
                        if (visibleGenreId in excludedGenreIds) return@firstNotNullOfOrNull null
                        PlayerTopInferredGenre(
                            sourceGenreId = candidate.genreId,
                            label = genreNames.displayName(visibleGenreId, systemLocale),
                            confidence = candidate.confidence,
                        )
                    }
            if (selected != null) put(mediaId, selected)
        }
    }
