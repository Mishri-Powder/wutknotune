// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class InvalidPublicationTest {
    @Test fun missingNamedMemberAndMalformedPointerAreTypedValidationFailures() {
        val directory = Files.createTempDirectory("cuetree-invalid")
        try {
            val store = SnapshotStore(directory)
            val cohort = store.write(Snapshot.empty("test").copy(generation = 1))
            Files.delete(directory.resolve(cohort).resolve("MANIFEST"))
            assertThrows(InvalidPublicationException::class.java) { store.read() }
            Files.writeString(directory.resolve("CURRENT"), "../outside")
            assertThrows(InvalidPublicationException::class.java) { store.read() }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun operationalReadFailureIsNotPermissionToDiscardAStore() {
        val directory = Files.createTempDirectory("cuetree-unavailable")
        try {
            val store = SnapshotStore(directory)
            val cohort = store.write(Snapshot.empty("test").copy(generation = 1))
            val manifest = directory.resolve(cohort).resolve("MANIFEST")
            Files.setPosixFilePermissions(manifest, emptySet())
            assumeFalse("Privileged test users bypass the read-denial fixture", Files.isReadable(manifest))
            val error = assertThrows(IOException::class.java) { store.read() }
            assertFalse(error is InvalidPublicationException)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
