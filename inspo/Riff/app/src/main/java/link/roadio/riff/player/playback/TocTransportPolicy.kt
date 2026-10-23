package link.roadio.riff.player.playback

/** Shared previous/next marker policy used by every Media3 transport surface. */
internal object TocTransportPolicy {
    fun usable(markersMs: List<Long>): List<Long> =
        markersMs
            .takeIf {
                it.size > 1 &&
                    it.all { marker -> marker >= 0L } &&
                    it.zipWithNext().all { (before, after) -> before < after }
            }.orEmpty()

    fun nextTarget(
        markersMs: List<Long>,
        positionMs: Long,
    ): Long? = usable(markersMs).firstOrNull { it > positionMs.coerceAtLeast(0L) }

    fun previousItemEntryTarget(markersMs: List<Long>): Long = usable(markersMs).lastOrNull() ?: 0L

    fun previousTarget(
        markersMs: List<Long>,
        positionMs: Long,
        restartThresholdMs: Long,
    ): Long? {
        require(restartThresholdMs >= 0L)
        val markers = usable(markersMs)
        if (markers.isEmpty()) return null
        val position = positionMs.coerceAtLeast(0L)
        val boundaries = (listOf(0L) + markers).distinct().sorted()
        val currentStart = boundaries.lastOrNull { it <= position } ?: 0L
        return if (position - currentStart > restartThresholdMs) {
            currentStart
        } else {
            boundaries.lastOrNull { it < currentStart }
        }
    }
}
