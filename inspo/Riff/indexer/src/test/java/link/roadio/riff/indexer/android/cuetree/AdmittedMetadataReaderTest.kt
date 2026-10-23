package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuegrain.ByteArrayInput
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.MetadataReader
import link.roadio.cuetree.Observation
import link.roadio.cuetree.SOURCE_DURATION_US
import link.roadio.riff.indexer.android.level1.AdmittedAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class AdmittedMetadataReaderTest {
    private val input = ByteArrayInput(byteArrayOf(), "source")
    private val observation = Observation(mapOf("title" to listOf("Title")), emptyList(), emptyList())

    @Test fun cueIsParsedWithoutAnyPlaybackProbe() {
        val reader =
            AdmittedMetadataReader(
                MetadataReader { _, hint, _ ->
                    assertEquals("cue", hint)
                    observation
                },
            ) { _, _ -> error("CUE must not reach the playback probe") }
        assertEquals(observation, reader.read(input, "cue", Cancellation()))
    }

    @Test fun admittedAudioRetainsItsDecoderHintForAnalysis() {
        var admitted = false
        val reader =
            AdmittedMetadataReader(
                MetadataReader { _, _, _ ->
                    check(admitted)
                    observation
                },
            ) { _, _ ->
                admitted = true
                AdmittedAudio(73L, 123_000_000L)
            }
        val result = reader.read(input, "flac", Cancellation())
        assertEquals(observation.attributes, result.attributes)
        assertEquals(73L, AndroidCuetreeSourceEvidence.hint(result.sourceEvidence))
        assertEquals(listOf("123000000"), result.sourceEvidence[SOURCE_DURATION_US])
    }

    @Test fun unknownDurationPreservesSupportedSourceAndExistingEvidence() {
        val existing = observation.copy(sourceEvidence = mapOf("test.retained" to listOf("evidence")))
        val reader = AdmittedMetadataReader(MetadataReader { _, _, _ -> existing }) { _, _ -> AdmittedAudio(73L) }
        val result = reader.read(input, "mp3", Cancellation())
        assertEquals(listOf("evidence"), result.sourceEvidence["test.retained"])
        assertEquals(null, result.sourceEvidence[SOURCE_DURATION_US])
        assertEquals(observation.attributes, result.attributes)
    }

    @Test fun rejectedAudioCannotPublishParsedMetadata() {
        val reader =
            AdmittedMetadataReader(MetadataReader { _, _, _ -> error("Rejected source") }) { _, _ ->
                throw IOException("Unsupported audio")
            }
        assertThrows(IOException::class.java) { reader.read(input, "mp3", Cancellation()) }
    }
}
