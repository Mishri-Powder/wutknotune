package link.roadio.riff.player.core.bookmarks

/** Logical queue occurrences, never physical offsets or deduplicated MediaIDs. */
data class BookMarkPosition(
    val ordinal: Int,
    val positionUs: Long,
)

enum class BookMarkAdmission { ALLOWED, TOO_CLOSE, UNKNOWN_DISTANCE }

/**
 * UI snapshots and the transactional writer use identical book-time arithmetic.
 * Unknown/overflowing distances fail closed; a chapter boundary is not a spacing reset.
 * Authority: https://wiki.271.pics/Riff/LATEST/player-bookmarks#mark-action-and-spacing
 */
object BookMarkSpacing {
    const val DEFAULT_MIN_SPACING_SECONDS = 15L

    fun evaluate(
        marks: List<BookMarkPosition>,
        durationsUs: Map<Int, Long>,
        candidate: BookMarkPosition,
        currentDurationUs: Long?,
        minimumUs: Long,
    ): BookMarkAdmission {
        require(minimumUs > 0L)
        if (candidate.ordinal < 0 || candidate.positionUs < 0L) return BookMarkAdmission.UNKNOWN_DISTANCE
        for (mark in marks) {
            if (mark.ordinal < 0 || mark.positionUs < 0L) return BookMarkAdmission.UNKNOWN_DISTANCE
            val distance = distanceUs(mark, candidate, durationsUs, currentDurationUs) ?: return BookMarkAdmission.UNKNOWN_DISTANCE
            if (distance < minimumUs) return BookMarkAdmission.TOO_CLOSE
        }
        return BookMarkAdmission.ALLOWED
    }

    private fun distanceUs(
        mark: BookMarkPosition,
        candidate: BookMarkPosition,
        durationsUs: Map<Int, Long>,
        currentDurationUs: Long?,
    ): Long? {
        if (mark.ordinal == candidate.ordinal) return kotlin.math.abs(mark.positionUs - candidate.positionUs)
        val (first, last) = if (mark.ordinal < candidate.ordinal) mark to candidate else candidate to mark
        var distance = -first.positionUs
        return try {
            for (ordinal in first.ordinal until last.ordinal) {
                val duration =
                    if (ordinal == candidate.ordinal && currentDurationUs != null) currentDurationUs else durationsUs[ordinal]
                if (duration == null || duration <= 0L || (ordinal == first.ordinal && duration < first.positionUs)) return null
                distance = Math.addExact(distance, duration)
            }
            Math.addExact(distance, last.positionUs).takeIf { it >= 0L }
        } catch (_: ArithmeticException) {
            null
        }
    }
}
