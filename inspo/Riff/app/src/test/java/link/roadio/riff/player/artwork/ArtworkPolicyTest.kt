package link.roadio.riff.player.artwork

import link.roadio.riff.ui.ArtworkOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkPolicyTest {
    @Test fun `embedded artwork has precedence`() {
        assertEquals(ArtworkChoice("tag", ArtworkOrigin.EMBEDDED), chooseArtwork("tag", listOf("cover")))
    }

    @Test fun `one usable directory image is selected`() {
        assertEquals(ArtworkChoice("cover", ArtworkOrigin.DIRECTORY), chooseArtwork(null, listOf("cover")))
    }

    @Test fun `zero or multiple directory images use default`() {
        listOf(emptyList(), listOf("front", "back")).forEach { candidates ->
            val result = chooseArtwork(null, candidates)
            assertEquals(ArtworkOrigin.DEFAULT, result.origin)
            assertNull(result.value)
        }
    }
}
