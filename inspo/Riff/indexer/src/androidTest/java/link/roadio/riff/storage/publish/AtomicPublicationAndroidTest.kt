package link.roadio.riff.storage.publish

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.flatbuffers.kotlin.FlatBufferBuilder
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AtomicPublicationAndroidTest {
    @Test
    fun publishesAndValidatesReadOnlyArtifactOnAndroidFilesystem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = context.cacheDir.toPath().resolve("publication-${UUID.randomUUID()}")
        val published = testRoot.resolve("published")

        try {
            val artifactPublisher = AtomicArtifactPublisher(published)
            val builder = FlatBufferBuilder(64)
            val value = builder.createString("x".repeat(32 * 1024))
            builder.startTable(1)
            builder.add(0, value, 0)
            val root = builder.endTable<Any>()
            builder.finish(root, "TEST")
            val bytes = builder.sizedByteArray()
            val artifact =
                artifactPublisher.publish(
                    finalName = "test-00000001.fb",
                    writer = ContentWriter { sink -> sink.write(ByteBuffer.wrap(bytes)) },
                    validator =
                        ArtifactValidator { channel, length ->
                            check(length > 32 * 1024)
                            val header = ByteBuffer.allocate(8)
                            while (header.hasRemaining()) check(channel.read(header) > 0)
                            check(header.array().copyOfRange(4, 8).contentEquals("TEST".toByteArray()))
                        },
                )

            assertNotNull(
                artifactPublisher.validatePublished(
                    artifact.dataPath.fileName.toString(),
                    ArtifactValidator { _, length -> check(length == artifact.length) },
                ),
            )
        } finally {
            if (Files.exists(testRoot)) {
                Files.walk(testRoot).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
    }
}
