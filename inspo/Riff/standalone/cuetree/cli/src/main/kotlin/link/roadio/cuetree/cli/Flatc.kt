// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree.cli

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

internal object Flatc {
    val version: String =
        Properties()
            .apply {
                requireNotNull(Flatc::class.java.getResourceAsStream("/cuetree/flatbuffers.properties")) { "Missing FlatBuffers version resource" }.use(::load)
            }.getProperty("version")

    fun executable(
        configured: String? = System.getenv("CUETREE_FLATC"),
        home: Path = Path.of(System.getProperty("user.home")),
    ): String {
        if (!configured.isNullOrBlank()) return configured
        val candidate = home.resolve("bin/flatc")
        return if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) candidate.toAbsolutePath().toString() else "flatc"
    }
}
