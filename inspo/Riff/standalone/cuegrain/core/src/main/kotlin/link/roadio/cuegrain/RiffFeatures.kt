// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import java.util.Locale

/** A measured value. Absence of confidence remains unknown rather than becoming an implicit zero or one. */
data class RiffMetric(
    val value: Float,
    val confidence: Float? = null,
)

/** Genre is repeatable because classifiers may expose several ordered candidates for one scope. */
data class RiffGenre(
    val value: String,
    val confidence: Float? = null,
)

/** Typed RIFF analysis observations belonging to exactly one [Source] or [Entry]. */
data class RiffFeatures(
    val arousal: RiffMetric? = null,
    val valence: RiffMetric? = null,
    val pte: RiffMetric? = null,
    val genres: List<RiffGenre> = emptyList(),
)

internal data class RiffFeatureIssue(
    val code: String,
    val location: String?,
    val message: String,
)

internal data class ParsedRiffFeatures(
    val value: RiffFeatures,
    val issues: List<RiffFeatureIssue>,
)

internal object RiffFeatureParser {
    private val decimal = Regex("[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?)|(?:\\.[0-9]+))(?:[eE][+-]?[0-9]+)?")

    private class Scalar(
        val name: String,
    ) {
        var value: Float? = null
        var confidence: Float? = null
    }

    fun parse(tags: List<Tag>): ParsedRiffFeatures {
        val issues = mutableListOf<RiffFeatureIssue>()
        val scalars =
            linkedMapOf(
                "RIFF_AROUSAL" to Scalar("RIFF_AROUSAL"),
                "RIFF_VALENCE" to Scalar("RIFF_VALENCE"),
                "RIFF_PTE" to Scalar("RIFF_PTE"),
            )
        val genres = mutableListOf<RiffGenre>()
        val genreByTagIndex = mutableMapOf<Int, Int>()

        fun number(tag: Tag): Float? {
            val raw = tag.value.trim()
            val value = raw.takeIf(decimal::matches)?.toFloatOrNull()
            if (value == null || !value.isFinite()) {
                issues += RiffFeatureIssue("RIFF_FEATURE_FLOAT_INVALID", tag.location, "Invalid finite decimal for ${tag.key}")
                return null
            }
            return value
        }

        tags.forEachIndexed { index, tag ->
            val key = tag.key.uppercase(Locale.ROOT)
            val scalarKey = key.removeSuffix("_CONFIDENCE")
            val scalar = scalars[scalarKey]
            when {
                scalar != null && key == scalarKey -> {
                    val parsed = number(tag) ?: return@forEachIndexed
                    if (scalar.value == null) {
                        scalar.value = parsed
                    } else {
                        issues += RiffFeatureIssue("RIFF_FEATURE_DUPLICATE", tag.location, "Duplicate ${scalar.name}")
                    }
                }

                scalar != null && key == "${scalarKey}_CONFIDENCE" -> {
                    val parsed = number(tag) ?: return@forEachIndexed
                    if (scalar.confidence == null) {
                        scalar.confidence = parsed
                    } else {
                        issues += RiffFeatureIssue("RIFF_FEATURE_DUPLICATE", tag.location, "Duplicate ${scalar.name}_CONFIDENCE")
                    }
                }

                key == "RIFF_GENRE" -> {
                    if (tag.value.isBlank()) {
                        issues += RiffFeatureIssue("RIFF_FEATURE_GENRE_INVALID", tag.location, "RIFF_GENRE must not be blank")
                    } else {
                        genres += RiffGenre(tag.value)
                        genreByTagIndex[index] = genres.lastIndex
                    }
                }

                key == "RIFF_GENRE_CONFIDENCE" -> {
                    // Positional pairing is deliberate: an optional confidence cannot be matched safely after a missing item.
                    val genreIndex = genreByTagIndex[index - 1]
                    val parsed = number(tag)
                    if (genreIndex == null) {
                        issues +=
                            RiffFeatureIssue(
                                "RIFF_FEATURE_CONFIDENCE_ORPHAN",
                                tag.location,
                                "RIFF_GENRE_CONFIDENCE must immediately follow RIFF_GENRE",
                            )
                    } else if (parsed != null) {
                        val genre = genres[genreIndex]
                        genres[genreIndex] = genre.copy(confidence = parsed)
                    }
                }
            }
        }

        scalars.values.filter { it.value == null && it.confidence != null }.forEach {
            issues +=
                RiffFeatureIssue(
                    "RIFF_FEATURE_CONFIDENCE_ORPHAN",
                    null,
                    "${it.name}_CONFIDENCE has no valid ${it.name}",
                )
        }

        fun Scalar.metric(): RiffMetric? = value?.let { RiffMetric(it, confidence) }

        return ParsedRiffFeatures(
            RiffFeatures(
                arousal = checkNotNull(scalars["RIFF_AROUSAL"]).metric(),
                valence = checkNotNull(scalars["RIFF_VALENCE"]).metric(),
                pte = checkNotNull(scalars["RIFF_PTE"]).metric(),
                genres = genres,
            ),
            issues,
        )
    }
}
