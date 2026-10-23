package link.roadio.riff.shared.encoding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class Base32HexTest {
    @Test
    fun `encodes unpadded RFC 4648 Base32hex and decodes it`() {
        val bytes = "Riff XML".encodeToByteArray()
        val encoded = Base32Hex.encode(bytes)

        assertEquals("A9KMCPH0B16KO", encoded)
        assertArrayEquals(bytes, Base32Hex.decode(encoded))
    }

    @Test
    fun `matches unpadded RFC 4648 Base32hex vectors`() {
        mapOf(
            "" to "",
            "f" to "CO",
            "fo" to "CPNG",
            "foo" to "CPNMU",
            "foob" to "CPNMUOG",
            "fooba" to "CPNMUOJ1",
            "foobar" to "CPNMUOJ1E8",
        ).forEach { (plain, encoded) ->
            assertEquals(encoded, Base32Hex.encode(plain.encodeToByteArray()))
            assertArrayEquals(plain.encodeToByteArray(), Base32Hex.decode(encoded))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects former W replacement padding`() {
        Base32Hex.decode("A9KMCPH0B16KOWWW")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects impossible unpadded length`() {
        Base32Hex.decode("0")
    }

    @Test
    fun `adopted drawable XML sources are packaged byte for byte as APK resources`() {
        val projectRoot =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "references/drawable").isDirectory }
        val sources =
            File(projectRoot, "references/drawable")
                .listFiles()
                .orEmpty()
                .filter { it.extension == "xml" && it.name.startsWith("ic_") }
                .sortedBy(File::getName)
        val resources =
            File(projectRoot, "app/src/main/res/drawable")
                .listFiles()
                .orEmpty()
                .filter { it.extension == "xml" && it.name.startsWith("ic_") && !it.name.startsWith("ic_launcher_") }
                .sortedBy(File::getName)

        assertEquals(sources.map(File::getName), resources.map(File::getName))
        sources.zip(resources).forEach { (source, resource) ->
            assertArrayEquals(
                source.readBytes(),
                resource.readBytes(),
            )
        }
    }
}
