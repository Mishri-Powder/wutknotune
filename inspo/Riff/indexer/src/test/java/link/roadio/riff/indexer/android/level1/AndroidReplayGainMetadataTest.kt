package link.roadio.riff.indexer.android.level1

import link.roadio.cuegrain.Gain
import link.roadio.cuegrain.Metadata
import link.roadio.cuegrain.Source
import link.roadio.cuegrain.Tag
import link.roadio.riff.indexer.core.replaygain.ReplayGainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidReplayGainMetadataTest {
    @Test
    fun completeReplayGainIsCanonicalizedAndRetainsPeaks() {
        val value =
            AndroidReplayGainMetadata.select(
                format(
                    Tag("REPLAYGAIN_TRACK_GAIN", "-4.52 dB"),
                    Tag("REPLAYGAIN_TRACK_PEAK", "1.125"),
                    Tag("REPLAYGAIN_ALBUM_GAIN", "-5.00 dB"),
                    Tag("REPLAYGAIN_ALBUM_PEAK", "1.25"),
                    Tag("REPLAYGAIN_REFERENCE_LOUDNESS", "-14 LUFS"),
                ),
            )

        assertEquals((-2181).toShort(), value.trackGainQ8)
        assertEquals(1.125f, value.trackPeak)
        assertEquals((-2304).toShort(), value.albumGainQ8)
        assertEquals(1.25f, value.albumPeak)
        assertEquals(ReplayGainSource.REPLAY_GAIN, value.source)
    }

    @Test
    fun completeLowerPriorityR128BeatsIncompleteReplayGainWithoutMixing() {
        val value =
            AndroidReplayGainMetadata.select(
                format(
                    Tag("REPLAYGAIN_TRACK_GAIN", "-4 dB"),
                    Tag("REPLAYGAIN_ALBUM_GAIN", "-5 dB"),
                    Tag("REPLAYGAIN_REFERENCE_LOUDNESS", "-14 LUFS"),
                    Tag("R128_TRACK_GAIN", "-384"),
                    Tag("R128_ALBUM_GAIN", "-512"),
                ),
            )

        assertEquals(896.toShort(), value.trackGainQ8)
        assertEquals(768.toShort(), value.albumGainQ8)
        assertNull(value.trackPeak)
        assertNull(value.albumPeak)
        assertEquals(ReplayGainSource.R128, value.source)
    }

    @Test
    fun highestPriorityUsableIncompleteSourceWinsWhenNoneIsComplete() {
        val value =
            AndroidReplayGainMetadata.select(
                format(
                    Tag("REPLAYGAIN_TRACK_GAIN", "-4 dB"),
                    Tag("R128_TRACK_GAIN", "-384"),
                ),
            )

        assertEquals((-1024).toShort(), value.trackGainQ8)
        assertNull(value.albumGainQ8)
        assertEquals(ReplayGainSource.REPLAY_GAIN, value.source)
    }

    @Test
    fun completeLameSideDataBeatsIncompleteReplayGain() {
        val lame =
            Source(
                "lame",
                "lame",
                "test",
                gains =
                    listOf(
                        Gain("lame", "track", -10.0, "dB", "gain0", ""),
                        Gain("lame", "album", -11.0, "dB", "gain1", ""),
                        Gain("lame", "track", 1.5, "linear", "peak", ""),
                        Gain("lame", "album", 1.5, "linear", "peak", ""),
                    ),
                attributes = mapOf("gain0Originator" to "3", "gain1Originator" to "3"),
            )
        val value = AndroidReplayGainMetadata.select(format(Tag("REPLAYGAIN_TRACK_GAIN", "-4 dB")) + lame)

        assertEquals((-2560).toShort(), value.trackGainQ8)
        assertEquals((-2816).toShort(), value.albumGainQ8)
        assertEquals(1.5f, value.trackPeak)
        assertEquals(1.5f, value.albumPeak)
        assertEquals(ReplayGainSource.LAME, value.source)
    }

    @Test
    fun mp4FreeformAtomUsesCanonicalMinus18Reference() {
        val value =
            AndroidReplayGainMetadata.select(
                format(Tag("REPLAYGAIN_TRACK_GAIN", "-1.0 dB")),
            )

        assertEquals((-256).toShort(), value.trackGainQ8)
        assertEquals(ReplayGainSource.REPLAY_GAIN, value.source)
    }

    @Test
    fun soundCheckIsConvertedFromMinus16ToCanonicalMinus18() {
        val value =
            AndroidReplayGainMetadata.select(
                format(Tag("iTunNORM", VALID_SOUND_CHECK)),
            )

        assertEquals((-512).toShort(), value.trackGainQ8)
        assertNull(value.albumGainQ8)
        assertEquals(ReplayGainSource.SOUND_CHECK, value.source)
    }

    @Test
    fun malformedSoundCheckShapesAreRejected() {
        val malformed =
            listOf(
                "000003E8 000003E8",
                VALID_SOUND_CHECK.substringBeforeLast(" "),
                "$VALID_SOUND_CHECK 00000000",
                VALID_SOUND_CHECK.replaceFirst("000003E8", "00003E8"),
                VALID_SOUND_CHECK.replaceFirst("000003E8", "GGGGGGGG"),
                VALID_SOUND_CHECK.replaceFirst("000003E8", "+00003E8"),
            )

        malformed.forEach { text ->
            val value = AndroidReplayGainMetadata.select(format(Tag("iTunNORM", text)))
            assertNull(value.trackGainQ8)
            assertEquals(ReplayGainSource.NONE, value.source)
        }
    }

    @Test
    fun explicitR128ReferenceIsUsedWithoutBorrowingReplayGainReference() {
        val value =
            AndroidReplayGainMetadata.select(
                format(
                    Tag("REPLAYGAIN_TRACK_GAIN", "-4 dB"),
                    Tag("REPLAYGAIN_REFERENCE_LOUDNESS", "-14 LUFS"),
                    Tag("R128_TRACK_GAIN", "-384"),
                    Tag("R128_ALBUM_GAIN", "-512"),
                    Tag("R128_REFERENCE_LOUDNESS", "-20 LUFS"),
                ),
            )

        assertEquals(128.toShort(), value.trackGainQ8)
        assertEquals(0.toShort(), value.albumGainQ8)
        assertEquals(ReplayGainSource.R128, value.source)
    }

    @Test
    fun neverCompletesACandidateFromAnotherPhysicalMetadataSource() {
        val first = format(Tag("REPLAYGAIN_TRACK_GAIN", "-4 dB"), Tag("REPLAYGAIN_TRACK_PEAK", "1.1"))
        val second = format(Tag("REPLAYGAIN_ALBUM_GAIN", "-5 dB"), Tag("REPLAYGAIN_ALBUM_PEAK", "1.2"))
        val complete = format(Tag("R128_TRACK_GAIN", "0"), Tag("R128_ALBUM_GAIN", "0"))
        val selected = AndroidReplayGainMetadata.select(first + second + complete)
        assertEquals(ReplayGainSource.R128, selected.source)
        assertNull(selected.trackPeak)
    }

    private fun format(vararg tags: Tag): List<Source> =
        listOf(Source("vorbis", "test", "test", tags = tags.toList(), gains = Metadata.gains(tags.toList())))

    private companion object {
        const val VALID_SOUND_CHECK =
            "000003E8 000003E8 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000"
    }
}
