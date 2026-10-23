package link.roadio.riff.player.core.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class BookMarkSpacingTest {
    private fun evaluate(
        marks: List<BookMarkPosition>,
        position: BookMarkPosition,
        durations: Map<Int, Long> = emptyMap(),
        observedDuration: Long? = null,
        minimum: Long = 15_000_000L,
    ) = BookMarkSpacing.evaluate(marks, durations, position, observedDuration, minimum)

    @Test
    fun bothSidesExcludeInteriorAndPermitExactBoundary() {
        val mark = listOf(BookMarkPosition(0, 30_000_000L))
        for (position in listOf(15_000_001L, 30_000_000L, 44_999_999L)) {
            assertEquals(BookMarkAdmission.TOO_CLOSE, evaluate(mark, BookMarkPosition(0, position)))
        }
        for (position in listOf(0L, 15_000_000L, 45_000_000L)) {
            assertEquals(BookMarkAdmission.ALLOWED, evaluate(mark, BookMarkPosition(0, position)))
        }
    }

    @Test
    fun chapterBoundaryUsesLogicalRemainderInEitherDirection() {
        val end = BookMarkPosition(0, 90_000_000L)
        val start = BookMarkPosition(1, 5_000_000L)
        val durations = mapOf(0 to 100_000_000L)
        assertEquals(BookMarkAdmission.ALLOWED, evaluate(listOf(end), start, durations))
        assertEquals(BookMarkAdmission.ALLOWED, evaluate(listOf(start), end, durations))
        assertEquals(BookMarkAdmission.TOO_CLOSE, evaluate(listOf(end), start.copy(positionUs = 4_999_999L), durations))
        assertEquals(BookMarkAdmission.TOO_CLOSE, evaluate(listOf(start), end.copy(positionUs = 90_000_001L), durations))
    }

    @Test
    fun mixedFragmentsAndFilesUseAllInterveningLogicalDurations() {
        val durations = mapOf(0 to 40_000_000L, 1 to 5_000_000L, 2 to 5_000_000L)
        val marks = listOf(BookMarkPosition(0, 39_000_000L))
        assertEquals(BookMarkAdmission.ALLOWED, evaluate(marks, BookMarkPosition(3, 4_000_000L), durations))
        assertEquals(BookMarkAdmission.TOO_CLOSE, evaluate(marks, BookMarkPosition(3, 3_999_999L), durations))
    }

    @Test
    fun firstMarkNeedsNoChapterDurationButUnknownCrossingFailsClosed() {
        assertEquals(BookMarkAdmission.ALLOWED, evaluate(emptyList(), BookMarkPosition(4, 0L)))
        val marks = listOf(BookMarkPosition(0, 0L))
        assertEquals(BookMarkAdmission.UNKNOWN_DISTANCE, evaluate(marks, BookMarkPosition(2, 60_000_000L), mapOf(0 to 30_000_000L)))
    }

    @Test
    fun actualCurrentDurationCanProveDistanceToLaterChapter() {
        val marks = listOf(BookMarkPosition(1, 5_000_000L))
        assertEquals(BookMarkAdmission.UNKNOWN_DISTANCE, evaluate(marks, BookMarkPosition(0, 90_000_000L)))
        assertEquals(BookMarkAdmission.ALLOWED, evaluate(marks, BookMarkPosition(0, 90_000_000L), observedDuration = 100_000_000L))
    }

    @Test
    fun customThresholdIsUsedWithoutRewritingExistingMarks() {
        val marks = listOf(BookMarkPosition(0, 10_000_000L), BookMarkPosition(0, 25_000_000L))
        assertEquals(BookMarkAdmission.TOO_CLOSE, evaluate(marks, BookMarkPosition(0, 44_000_000L), minimum = 20_000_000L))
        assertEquals(BookMarkAdmission.ALLOWED, evaluate(marks, BookMarkPosition(0, 45_000_000L), minimum = 20_000_000L))
        assertEquals(2, marks.size)
    }

    @Test
    fun repeatedOccurrencesAndEveryExistingMarkParticipate() {
        val marks = listOf(BookMarkPosition(0, 0L), BookMarkPosition(2, 30_000_000L))
        assertEquals(
            BookMarkAdmission.TOO_CLOSE,
            evaluate(
                marks,
                BookMarkPosition(2, 40_000_000L),
                mapOf(
                    0 to 100_000_000L,
                    1 to 100_000_000L,
                ),
            ),
        )
        assertEquals(
            BookMarkAdmission.ALLOWED,
            evaluate(marks, BookMarkPosition(2, 45_000_000L), mapOf(0 to 100_000_000L, 1 to 100_000_000L)),
        )
    }

    @Test
    fun invalidOrOverflowingDistanceFailsClosed() {
        val marks = listOf(BookMarkPosition(0, 0L))
        assertEquals(BookMarkAdmission.UNKNOWN_DISTANCE, evaluate(marks, BookMarkPosition(2, 1L), mapOf(0 to Long.MAX_VALUE, 1 to 1L)))
        assertEquals(
            BookMarkAdmission.UNKNOWN_DISTANCE,
            evaluate(listOf(BookMarkPosition(0, 20L)), BookMarkPosition(1, 0L), mapOf(0 to 10L)),
        )
    }
}
