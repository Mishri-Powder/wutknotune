package link.roadio.riff.storage.publish

import link.roadio.riff.storage.TestHashing
import link.roadio.riff.storage.hash.readXxh3CanonicalLong
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

class AtomicArtifactPublisherTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun streamingPublicationInstallsDataBeforeCanonicalSidecar() {
        val directory = temporaryFolder.newFolder("published").toPath()
        var directorySyncCount = 0
        val publisher =
            AtomicArtifactPublisher(
                directory = directory,
                xxh3Factory = TestHashing,
                directorySync = DirectorySync { directorySyncCount += 1 },
                ioBufferSize = 3,
            )
        val expected = "streamed-content".toByteArray(UTF_8)

        val result =
            publisher.publish(
                finalName = "library-00000001.bin",
                writer =
                    ContentWriter { sink ->
                        sink.write(ByteBuffer.wrap(expected, 0, 4))
                        sink.write(ByteBuffer.wrap(expected, 4, expected.size - 4))
                    },
                validator =
                    ArtifactValidator { channel, length ->
                        assertEquals(expected.size.toLong(), length)
                        val actual = ByteArray(expected.size)
                        channel.read(ByteBuffer.wrap(actual))
                        assertArrayEquals(expected, actual)
                    },
            )

        assertArrayEquals(expected, Files.readAllBytes(result.dataPath))
        assertEquals(TestHashing.hash(expected), result.checksum)
        assertEquals(
            result.checksum,
            Files.readAllBytes(result.sidecarPath).readXxh3CanonicalLong(),
        )
        assertEquals(2, directorySyncCount)
    }

    @Test
    fun bufferedPublicationDoesNotChangeCallerPosition() {
        val directory = temporaryFolder.newFolder("buffered").toPath()
        val publisher = publisher(directory)
        val source = ByteBuffer.wrap("prefix-payload".toByteArray(UTF_8)).apply { position(7) }

        publisher.publish(
            "metadata",
            source,
            ArtifactValidator { _, length ->
                assertEquals(7L, length)
            },
        )

        assertEquals(7, source.position())
        assertArrayEquals("payload".toByteArray(UTF_8), Files.readAllBytes(directory.resolve("metadata")))
    }

    @Test
    fun validationFailureLeavesUnsignedFinalData() {
        val directory = temporaryFolder.newFolder("invalid").toPath()
        val publisher = publisher(directory)

        assertThrows(IllegalStateException::class.java) {
            publisher.publish(
                "library-00000002.bin",
                ByteBuffer.wrap(byteArrayOf(1, 2, 3)),
                ArtifactValidator { _, _ -> error("invalid FlatBuffer") },
            )
        }

        assertTrue(Files.exists(directory.resolve("library-00000002.bin")))
        assertFalse(Files.exists(directory.resolve("library-00000002.bin.xxh3")))
    }

    @Test
    fun createModeNeverReusesGenerationName() {
        val directory = temporaryFolder.newFolder("collision").toPath()
        val publisher = publisher(directory)
        val validator = ArtifactValidator { _, _ -> Unit }
        publisher.publish("generation.bin", ByteBuffer.wrap(byteArrayOf(1)), validator)

        assertThrows(IllegalStateException::class.java) {
            publisher.publish("generation.bin", ByteBuffer.wrap(byteArrayOf(2)), validator)
        }
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(directory.resolve("generation.bin")))
    }

    private fun publisher(directory: java.nio.file.Path) =
        AtomicArtifactPublisher(
            directory = directory,
            xxh3Factory = TestHashing,
            directorySync = DirectorySync { Unit },
            ioBufferSize = 4,
        )
}
