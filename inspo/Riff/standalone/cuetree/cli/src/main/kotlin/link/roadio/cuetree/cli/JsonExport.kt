// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree.cli

import link.roadio.cuetree.FlatCatalogs
import java.io.IOException
import java.nio.file.Files
import java.util.Locale

/** Convert the actual wire buffers; a second hand-maintained field mapping would drift from FBS. */
internal class JsonExport(
    private val executable: String = Flatc.executable(),
) {
    fun checkVersion() {
        val actual = run("--version").trim()
        if (actual != "flatc version ${Flatc.version}") throw IOException("JSON export requires flatc ${Flatc.version}; $executable reported $actual. Set CUETREE_FLATC to select the compiler.")
    }

    fun convert(flat: FlatCatalogs): Map<String, String> {
        val temporary = Files.createTempDirectory("cuetree-json-")
        try {
            val schemas = listOf("common.fbs") + FlatCatalogs.names.map { "${it.lowercase(Locale.ROOT)}.fbs" }
            for (name in schemas) {
                val resource = javaClass.getResourceAsStream("/cuetree/fbs/$name") ?: throw IOException("Missing packaged schema: $name")
                resource.use { Files.copy(it, temporary.resolve(name)) }
            }
            return FlatCatalogs.names.associateWith { name ->
                val binary = temporary.resolve(name)
                Files.write(binary, flat.bytes(name))
                run("--json", "--strict-json", "--defaults-json", "--natural-utf8", "-o", temporary.toString(), temporary.resolve("${name.lowercase(Locale.ROOT)}.fbs").toString(), "--", binary.toString())
                escapeControls(Files.readString(temporary.resolve("$name.json")))
            }
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }

    // flatc --natural-utf8 leaves some C0 controls literal; preserve values while fixing JSON syntax.
    private fun escapeControls(text: String): String =
        buildString(text.length) {
            var inString = false
            var escaped = false
            for (character in text) {
                if (inString && character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
                if (escaped) {
                    escaped = false
                } else if (inString && character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    inString = !inString
                }
            }
        }

    private fun run(vararg args: String): String {
        val process =
            try {
                ProcessBuilder(listOf(executable) + args).redirectErrorStream(true).start()
            } catch (failure: IOException) {
                throw IOException("Cannot run $executable for JSON export; set CUETREE_FLATC to flatc ${Flatc.version}", failure)
            }
        try {
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (process.waitFor() != 0) throw IOException("flatc JSON export failed: ${output.trim()}")
            return output
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
