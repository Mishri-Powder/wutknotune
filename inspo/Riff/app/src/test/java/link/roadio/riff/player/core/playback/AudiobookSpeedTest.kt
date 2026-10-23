package link.roadio.riff.player.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AudiobookSpeedTest {
    @Test
    fun completeCycleWrapsWithoutDriftFromDefault() {
        var speed = AudiobookSpeed.NORMAL
        val expected = listOf(1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 0.7f, 0.8f, 0.9f)
        repeat(100) {
            expected.forEach { value ->
                assertEquals(value, speed.multiplier, 0f)
                assertEquals(speed, AudiobookSpeed.fromTenths(speed.tenths))
                speed = speed.next()
            }
            assertEquals(AudiobookSpeed.NORMAL, speed)
        }
    }

    @Test
    fun labelsHaveOneDecimalDigitAndTrailingXInEverySystemLocale() {
        val original = Locale.getDefault()
        try {
            listOf(Locale.US, Locale.GERMANY, Locale.forLanguageTag("ar"), Locale.forLanguageTag("ru")).forEach { locale ->
                Locale.setDefault(locale)
                assertEquals(
                    listOf("0.7x", "0.8x", "0.9x", "1.0x", "1.1x", "1.2x", "1.3x", "1.4x", "1.5x"),
                    AudiobookSpeed.entries.map { it.label },
                )
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun everyModeAndOccurrenceUsesUnityPitchAndOnlyBooksSkipSilence() {
        AudiobookSpeed.entries.forEach { speed ->
            for (mode in listOf(false, true)) {
                for (book in listOf(false, true)) {
                    val actual = AudiobookAudioParameters.resolve(mode, book, speed)
                    assertEquals(1f, actual.pitch, 0f)
                    assertEquals(if (mode && book) speed.multiplier else 1f, actual.speed, 0f)
                    assertEquals(mode && book, actual.skipSilence)
                }
            }
            val book = AudiobookAudioParameters.resolve(true, true, speed)
            assertTrue(book.skipSilence)
            val music = AudiobookAudioParameters.resolve(true, false, speed)
            assertFalse(music.skipSilence)
        }
    }

    @Test
    fun invalidPersistedStepsCannotBecomeArbitrarySpeeds() {
        listOf(Int.MIN_VALUE, -1, 0, 6, 16, Int.MAX_VALUE).forEach { value ->
            assertTrue(runCatching { AudiobookSpeed.fromTenths(value) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
