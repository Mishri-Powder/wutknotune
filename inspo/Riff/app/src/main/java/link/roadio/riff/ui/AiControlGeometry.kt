package link.roadio.riff.ui

internal data class AiWideControlGeometry(
    val choiceWidthDp: Float,
    val controlHeightDp: Float,
)

internal data class AiNarrowControlGeometry(
    val choicesWidthDp: Float,
    val playWidthDp: Float,
    val choiceWidthDp: Float,
    val choiceHeightDp: Float,
)

internal fun aiWideControlGeometry(
    widthDp: Float,
    heightDp: Float,
    gapDp: Float,
): AiWideControlGeometry {
    require(widthDp > gapDp * 2f)
    require(heightDp > gapDp * 2f)
    return AiWideControlGeometry(
        choiceWidthDp = (widthDp - gapDp * 2f) / 3f,
        controlHeightDp = (heightDp - gapDp * 2f) / 3f,
    )
}

internal fun aiNarrowControlGeometry(
    widthDp: Float,
    heightDp: Float,
    gapDp: Float,
): AiNarrowControlGeometry {
    require(widthDp > gapDp)
    require(heightDp > gapDp)
    val partitionWidth = widthDp - gapDp
    val playWidth = partitionWidth / 3f
    val choicesWidth = partitionWidth - playWidth
    return AiNarrowControlGeometry(
        choicesWidthDp = choicesWidth,
        playWidthDp = playWidth,
        choiceWidthDp = (choicesWidth - gapDp * 2f) / 3f,
        choiceHeightDp = (heightDp - gapDp) / 2f,
    )
}

internal fun canUseAiNarrowSplit(
    widthDp: Float,
    heightDp: Float,
    gapDp: Float,
    minimumChoiceDp: Float,
    minimumPlayDp: Float,
): Boolean {
    if (widthDp <= gapDp || heightDp <= gapDp) return false
    val geometry = aiNarrowControlGeometry(widthDp, heightDp, gapDp)
    return geometry.choiceWidthDp >= minimumChoiceDp &&
        geometry.choiceHeightDp >= minimumChoiceDp &&
        geometry.playWidthDp >= minimumPlayDp &&
        heightDp >= minimumPlayDp
}
