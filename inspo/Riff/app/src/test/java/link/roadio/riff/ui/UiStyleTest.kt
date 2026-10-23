package link.roadio.riff.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UiStyleTest {
    @Test
    fun `APK drawables are exact copies of the complete adopted source set`() {
        val projectRoot =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "references/drawable").isDirectory }
        val sources =
            File(projectRoot, "references/drawable")
                .listFiles()
                .orEmpty()
                .filter { it.extension == "xml" && it.name.startsWith("ic_") }
        assertEquals(96, sources.size)
        assertEquals(sources.map { it.nameWithoutExtension }.toSet(), UiDrawableRegistry.names)
        sources.forEach { source ->
            assertArrayEquals(
                source.name,
                source.readBytes(),
                File(projectRoot, "app/src/main/res/drawable/${source.name}").readBytes(),
            )
        }
    }

    @Test
    fun `APK night drawables are exact copies of the adopted qualified source set`() {
        val projectRoot =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "references/drawable-night").isDirectory }
        val sources =
            File(projectRoot, "references/drawable-night")
                .listFiles()
                .orEmpty()
                .filter { it.extension == "xml" && it.name.startsWith("ic_") }

        assertEquals(67, sources.size)
        sources.forEach { source ->
            assertArrayEquals(
                source.name,
                source.readBytes(),
                File(projectRoot, "app/src/main/res/drawable-night/${source.name}").readBytes(),
            )
        }
    }

    @Test
    fun `semantic icon resolver accepts the complete checked-in icon corpus`() {
        val projectRoot =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "references/drawable").isDirectory }
        val iconSources =
            File(projectRoot, "references/drawable")
                .listFiles()
                .orEmpty()
                .filter { file ->
                    file.extension == "xml" &&
                        file.name.startsWith("ic_") &&
                        !file.name.startsWith("ic_default_artwork_") &&
                        file.name != "ic_smpte_bars.xml"
                }

        assertEquals(93, iconSources.size)
        iconSources.forEach { source ->
            val drawableName = source.nameWithoutExtension
            assertEquals(drawableName, iconDrawableName(drawableName.removePrefix("ic_")))
            assertEquals(drawableName, iconDrawableName(drawableName))
        }
        assertTrue(runCatching { iconDrawableName("not_registered") }.isFailure)
        assertTrue(runCatching { iconDrawableName("smpte_bars") }.isFailure)
        listOf("ic_ic_yes", "ic_not_registered", "ic_smpte_bars", "ic_default_artwork_day").forEach { name ->
            assertTrue("Excluded or unknown name $name must stay rejected", runCatching { iconDrawableName(name) }.isFailure)
        }
    }

    @Test
    fun `obsolete standalone launcher is retained only as excluded provenance`() {
        val projectRoot =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "references/drawable").isDirectory }
        val excluded =
            File(projectRoot, "references/drawable/icon-system/excluded/ic_launcher.xml")

        assertTrue(excluded.isFile)
        assertFalse(File(projectRoot, "references/drawable/ic_launcher.xml").exists())
        assertFalse(File(projectRoot, "references/drawable-night/ic_launcher.xml").exists())
        assertFalse(File(projectRoot, "app/src/main/res/drawable/ic_launcher.xml").exists())
        assertFalse(File(projectRoot, "app/src/main/res/drawable-night/ic_launcher.xml").exists())
    }

    @Test
    fun `smpte bars remain the exact supplied non icon drawable`() {
        val projectRoot =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "references/drawable").isDirectory }
        val source = File(projectRoot, "references/drawable/ic_smpte_bars.xml")
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(source.readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }

        assertEquals("012043f21b6f53e1f4ed42f53fc8c21bf3097104c40a0a5898078d33217411e2", digest)
    }
}
