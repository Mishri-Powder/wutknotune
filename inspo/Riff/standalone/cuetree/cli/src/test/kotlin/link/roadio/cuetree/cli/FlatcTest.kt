// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree.cli

import com.google.flatbuffers.Constants
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class FlatcTest {
    @Test fun explicitSelectionThenHomeThenPathAndSharedRuntimeVersion() {
        val home = Files.createTempDirectory("cuetree-flatc-home-")
        try {
            assertEquals("flatc", Flatc.executable(null, home))
            val candidate = Files.createDirectories(home.resolve("bin")).resolve("flatc")
            Files.writeString(candidate, "")
            assertTrue(candidate.toFile().setExecutable(true))
            assertEquals(candidate.toString(), Flatc.executable(null, home))
            assertEquals(candidate.toString(), Flatc.executable("", home))
            assertEquals("/explicit/compiler", Flatc.executable("/explicit/compiler", home))
            Constants::class.java.getMethod("FLATBUFFERS_${Flatc.version.replace('.', '_')}").invoke(null)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test fun wrongVersionReportsTheSelectedExecutableAndObservedVersion() {
        val home = Files.createTempDirectory("cuetree-flatc-version-")
        try {
            val compiler = Files.writeString(home.resolve("flatc"), "#!/bin/sh\nprintf 'flatc version 0.0.0\\n'\n")
            assertTrue(compiler.toFile().setExecutable(true))
            val failure = assertThrows(IOException::class.java) { JsonExport(compiler.toString()).checkVersion() }
            assertTrue(failure.message!!.contains(compiler.toString()))
            assertTrue(failure.message!!.contains("0.0.0"))
            assertTrue(failure.message!!.contains(Flatc.version))
        } finally {
            home.toFile().deleteRecursively()
        }
    }
}
