package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackChapterLabelTest {
    @Test
    fun chapterLabelRequiresMoreThanOneUsableChapter() {
        assertNull(currentChapterLabel(emptyList(), 0L, null))
        assertNull(currentChapterLabel(listOf(TocChapter(0L, "Only")), 0L, null))
        assertNull(
            currentChapterLabel(
                listOf(TocChapter(0L, "Opening"), TocChapter(10_000L, "Beyond duration")),
                0L,
                10_000L,
            ),
        )
    }

    @Test
    fun chapterLabelFollowsPositionAndUsesOneBasedFallback() {
        val chapters =
            listOf(
                TocChapter(5_000L, "Opening"),
                TocChapter(10_000L),
                TocChapter(20_000L, "Finale"),
            )

        assertEquals("Opening", currentChapterLabel(chapters, 0L, null))
        assertEquals("Opening", currentChapterLabel(chapters, 9_999L, null))
        assertEquals("Chapter 2", currentChapterLabel(chapters, 10_000L, null))
        assertEquals("Finale", currentChapterLabel(chapters, Long.MAX_VALUE, null))
    }

    @Test
    fun invalidChapterOrderIsRejected() {
        assertNull(
            currentChapterLabel(
                listOf(TocChapter(0L), TocChapter(0L)),
                0L,
                null,
            ),
        )
        assertNull(
            currentChapterLabel(
                listOf(TocChapter(-1L), TocChapter(1L)),
                0L,
                null,
            ),
        )
    }

    @Test
    fun audiobookOverrideKeysAcceptTrackFolderAndAlbumIdentities() {
        assertEquals(true, isAudiobookOverrideKey("track:1"))
        assertEquals(true, isAudiobookOverrideKey("folder:books/fantasy"))
        assertEquals(true, isAudiobookOverrideKey("album:1"))
        assertEquals(false, isAudiobookOverrideKey("track:"))
    }
}
