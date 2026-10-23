package link.roadio.riff.indexer.core.analysis

/** Frame offsets use the extracted AU timeline; decoder output timestamps are not reliable. */
internal fun pcmSpanFrames(
    firstAccessUnitUs: Long,
    beginUs: Long,
    endUs: Long?,
    rate: Int,
    decodedFrames: Long,
    bufferFrames: Int,
): IntRange {
    require(firstAccessUnitUs >= 0 && beginUs >= 0 && rate > 0 && decodedFrames >= 0 && bufferFrames >= 0)

    fun frame(timeUs: Long): Long =
        kotlin.math
            .ceil((timeUs - firstAccessUnitUs).toDouble() * rate / 1_000_000.0)
            .toLong()
            .coerceAtLeast(0L)
    val start = (frame(beginUs) - decodedFrames).coerceIn(0, bufferFrames.toLong()).toInt()
    val end = endUs?.let { (frame(it) - decodedFrames).coerceIn(0, bufferFrames.toLong()).toInt() } ?: bufferFrames
    return start until end
}
