package link.roadio.riff.indexer.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMimeAdmissionTest {
    @Test fun concreteAudioMimeIsOnlyCandidateEvidence() {
        listOf("audio/flac", "audio/mp4", "Audio/MPEG", "audio/x-wav").forEach { assertTrue(isAudioCandidateMime(it)) }
        listOf(null, "", "audio/*", "text/plain", "video/mp4", "audio/flac ", "audio/flac; charset=utf8").forEach {
            assertFalse(isAudioCandidateMime(it))
        }
    }
}
