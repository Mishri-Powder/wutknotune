package link.roadio.riff.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReleaseDefaultsTest {
    @Test
    fun sharedDefaultKeepsExplicitUserValuesAndSingleStartupRead() {
        assertTrue(PackagedConfiguration.snapshot.value("behavior.enableStatusLines") as Boolean)
        val startup = StartupConfiguration()
        assertEquals(true, startup.load { null }.snapshot.value("behavior.enableStatusLines"))
        assertEquals(true, startup.load { error("Must not reread") }.snapshot.value("behavior.enableStatusLines"))
        assertEquals(
            true,
            StartupConfiguration().load { throw IOException("unavailable") }.snapshot.value("behavior.enableStatusLines"),
        )
        for (requested in listOf(false, true)) {
            val text = """{"skins":{"default":{"behavior":{"enableStatusLines":$requested}}}}"""
            assertEquals(requested, StartupConfiguration().load { text }.snapshot.value("behavior.enableStatusLines"))
        }
    }
}
