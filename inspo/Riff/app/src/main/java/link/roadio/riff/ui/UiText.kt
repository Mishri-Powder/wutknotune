package link.roadio.riff.ui

import java.util.regex.Pattern

private val UI_GRAPHEME = Pattern.compile("\\X")

/** Bound painted text without cutting a grapheme; callers retain the complete accessible label. */
internal fun boundedUiText(
    value: String,
    maximumClusters: Int,
): String {
    require(maximumClusters > 0)
    val matcher = UI_GRAPHEME.matcher(value)
    var count = 0
    var lastRetainedEnd = 0
    while (matcher.find()) {
        count += 1
        if (count < maximumClusters) lastRetainedEnd = matcher.end()
        if (count > maximumClusters) return value.substring(0, lastRetainedEnd) + "…"
    }
    return value
}
