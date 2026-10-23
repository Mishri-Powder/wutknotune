// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.cli

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

class FatJarTest {
    @get:Rule val temporary = TemporaryFolder()
    private val artifact get() = File(System.getProperty("cuegrain.fatJar"))
    private val withCed get() = System.getProperty("cuegrain.withCed").toBoolean()
    private val cue = "PERFORMER \"Ансамбль народной музыки\"\nTITLE \"Любимые песни родного края\"\nFILE \"album.flac\" WAVE\nTRACK 01 AUDIO\nTITLE \"Прекрасная музыка летнего вечера\"\nINDEX 01 00:00:00"

    @Test fun archiveContainsRuntimeAndConditionalNativeProvider() {
        JarFile(artifact).use { jar ->
            val names =
                jar
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .toList()
            assertEquals(names.size, names.distinct().size)
            assertEquals("link.roadio.cuegrain.cli.MainKt", jar.manifest.mainAttributes.getValue("Main-Class"))
            assertNull(jar.manifest.mainAttributes.getValue("Class-Path"))
            for (name in listOf(
                "link/roadio/cuegrain/Cuegrain.class",
                "kotlin/Unit.class",
                "META-INF/cuegrain/LICENSE",
                "META-INF/cuegrain/THIRD_PARTY_NOTICES.txt",
                "META-INF/cuegrain/licenses/kotlin/threetenbp_license.txt",
                "META-INF/cuegrain/schema.json",
            )) {
                assertTrue(name, name in names)
            }
            assertEquals(withCed, "META-INF/services/link.roadio.cuegrain.EncodingDetector" in names)
            assertEquals(withCed, names.any { it.startsWith("link/roadio/cuegrain/ced/") })
            assertEquals(withCed, names.any { it.startsWith("native/") && !it.endsWith("/") })
            assertFalse(names.any { it.endsWith("module-info.class") })
        }
    }

    @Test fun copiedJarRunsWithoutAdjacentLibrariesAndKeepsJsonErrors() {
        val directory = isolatedJar()
        assertTrue(run(directory, 0, "--help").contains("\"output\":\"JSON\""))
        assertTrue(run(directory, 0, "--version").contains("\"version\":"))
        assertTrue(run(directory, 2, "--unknown").contains("\"code\":\"ARGUMENT_INVALID\""))
        assertTrue(run(directory, 2).contains("\"code\":\"ARGUMENT_INVALID\""))
        assertTrue(run(directory, 1, "missing.mp3").contains("\"code\":\"INPUT_IO\""))
    }

    @Test fun copiedJarReadsUtf8AndLegacyCueReadOnly() {
        val directory = isolatedJar()
        val utf8 = File(directory, "Unicode sample.cue").apply { writeText(cue, Charsets.UTF_8) }
        val original = utf8.readBytes()
        val report = run(directory, 0, utf8.name)
        assertTrue(report.contains("\"ced\":$withCed"))
        assertTrue(report.contains("Прекрасная музыка летнего вечера"))
        assertArrayEquals(original, utf8.readBytes())
        val legacy = File(directory, "legacy.cue").apply { writeBytes(cue.toByteArray(charset("windows-1251"))) }
        val legacyBytes = legacy.readBytes()
        assertTrue(run(directory, 0, "--charset", "windows-1251", legacy.name).contains("\"status\":\"ok\""))
        val detected = run(directory, if (withCed) 0 else 1, legacy.name)
        assertTrue(detected.contains(if (withCed) "\"encodingBasis\":\"ced\"" else "\"code\":\"ENCODING_REQUIRED\""))
        assertArrayEquals(legacyBytes, legacy.readBytes())
    }

    private fun isolatedJar(): File = temporary.newFolder().also { artifact.copyTo(File(it, "cuegrain.jar")) }

    private fun run(
        directory: File,
        expected: Int,
        vararg args: String,
    ): String {
        val stdout = File(directory, "stdout.json")
        val stderr = File(directory, "stderr.txt")
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val builder =
            ProcessBuilder(listOf(java, "-jar", "cuegrain.jar") + args)
                .directory(directory)
                .redirectOutput(stdout)
                .redirectError(stderr)
        builder.environment().remove("CLASSPATH")
        val process = builder.start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor()
            fail("Executable JAR timed out")
        }
        val text = stdout.readText(Charsets.UTF_8).trim()
        assertEquals(stderr.readText(), "", stderr.readText())
        assertEquals(text, expected, process.exitValue())
        assertTrue(text, text.startsWith("{") && text.endsWith("}"))
        return text
    }
}
