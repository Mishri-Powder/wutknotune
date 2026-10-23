// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.RiffFeatures

internal val RIFF_FEATURE_ATTRIBUTES =
    setOf(
        "riffPte",
        "riffPteConfidence",
        "riffArousal",
        "riffArousalConfidence",
        "riffValence",
        "riffValenceConfidence",
        "riffGenre",
        "riffGenreConfidence",
    )

/** Preserve typed observations; runtime acceptance and dictionary mapping belong to the consumer. */
internal fun riffFeatureAttributes(features: RiffFeatures): Attributes =
    buildMap {
        listOf("riffPte" to features.pte, "riffArousal" to features.arousal, "riffValence" to features.valence).forEach { (key, metric) ->
            if (metric != null) {
                put(key, listOf(metric.value.toString()))
                metric.confidence?.let { put(key + "Confidence", listOf(it.toString())) }
            }
        }
        if (features.genres.isNotEmpty()) {
            put("riffGenre", features.genres.map { it.value })
            put("riffGenreConfidence", features.genres.map { it.confidence?.toString().orEmpty() })
        }
    }
