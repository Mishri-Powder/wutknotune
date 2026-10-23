package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AiStatusLineTest {
    @Test
    fun `AI status is always root-locale A V C and has no indexer input`() {
        assertEquals(
            "A 0.25 V -0.50 C  7",
            formatAiStatusLine(
                arousal = 0.25f,
                valence = -0.5f,
                candidateCount = 7,
            ),
        )
    }
}
