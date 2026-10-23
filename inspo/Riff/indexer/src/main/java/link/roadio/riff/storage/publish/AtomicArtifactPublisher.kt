package link.roadio.riff.storage.publish

import android.system.Os
import android.system.OsConstants
import link.roadio.riff.storage.hash.NativeXxh3Factory
import link.roadio.riff.storage.hash.Xxh3Factory
import link.roadio.riff.storage.hash.readXxh3CanonicalLong
import link.roadio.riff.storage.hash.toXxh3CanonicalBytes
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

fun interface ContentWriter {
    fun writeTo(sink: ContentSink)
}

/** Non-owning sink: the publisher alone controls force and close. */
fun interface ContentSink {
    fun write(source: ByteBuffer)
}

fun interface ArtifactValidator {
    fun validate(
        channel: FileChannel,
        length: Long,
    )
}

fun interface DirectorySync {
    fun sync(directory: Path)
}

enum class PublicationMode {
    /** A generation pathname that must not already exist. */
    CREATE,

    /** Fixed-name metadata such as volume-uuid whose invalid pair may be replaced. */
    REPLACE,
}

data class PublishedArtifact(
    val dataPath: Path,
    val sidecarPath: Path,
    val length: Long,
    val checksum: Long,
)

/**
 * Installs data before its XXH3 sidecar publication marker.
 *
 * The sidecar is the commit record: unsigned data is never readable as current and is intentionally left for
 * recovery after a failure between renames. This class is synchronous by design and must run in the Indexer
 * process on an I/O dispatcher.
 */
class AtomicArtifactPublisher(
    private val directory: Path,
    private val xxh3Factory: Xxh3Factory = NativeXxh3Factory,
    private val directorySync: DirectorySync = AndroidDirectorySync,
    private val ioBufferSize: Int = DEFAULT_IO_BUFFER_SIZE,
) {
    init {
        require(ioBufferSize > 0) { "ioBufferSize must be positive" }
    }

    @Synchronized
    fun publish(
        finalName: String,
        writer: ContentWriter,
        validator: ArtifactValidator,
        mode: PublicationMode = PublicationMode.CREATE,
    ): PublishedArtifact {
        validateFinalName(finalName)
        Files.createDirectories(directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS)) {
            "Publication directory is not a directory: $directory"
        }

        val dataPath = directory.resolve(finalName)
        val sidecarPath = directory.resolve("$finalName.xxh3")
        checkTargets(dataPath, sidecarPath, mode)

        val temporaryData = createTemporary(finalName, "data")
        try {
            FileChannel.open(temporaryData, WRITE, TRUNCATE_EXISTING).use { channel ->
                val sink = ContentSink { source -> writeFully(channel, source) }
                writer.writeTo(sink)
                channel.force(true)
            }

            atomicMove(temporaryData, dataPath, mode)
            directorySync.sync(directory)

            val digest = hashAndValidate(dataPath, validator)
            publishSmallFile(
                sidecarPath,
                ByteBuffer.wrap(digest.checksum.toXxh3CanonicalBytes()),
                mode,
            )

            val verified = hashAndValidate(dataPath, validator)
            val sidecarChecksum = readSidecar(sidecarPath)
            check(verified.length == digest.length) { "Published data length changed" }
            check(verified.checksum == digest.checksum) { "Published data checksum changed" }
            check(sidecarChecksum == verified.checksum) { "Published sidecar does not match data" }

            return PublishedArtifact(
                dataPath = dataPath,
                sidecarPath = sidecarPath,
                length = verified.length,
                checksum = verified.checksum,
            )
        } finally {
            Files.deleteIfExists(temporaryData)
            // Once the data rename succeeds, an error intentionally leaves an unsigned final file.
            // Recovery owns that orphan; deleting it here could erase evidence after a power fault.
        }
    }

    fun publish(
        finalName: String,
        content: ByteBuffer,
        validator: ArtifactValidator,
        mode: PublicationMode = PublicationMode.CREATE,
    ): PublishedArtifact {
        val source = content.duplicate()
        return publish(
            finalName = finalName,
            writer = ContentWriter { sink -> sink.write(source) },
            validator = validator,
            mode = mode,
        )
    }

    /** Returns null for every missing or invalid pair; it never repairs or mutates the pair. */
    @Synchronized
    fun validatePublished(
        finalName: String,
        validator: ArtifactValidator,
    ): PublishedArtifact? {
        validateFinalName(finalName)
        val dataPath = directory.resolve(finalName)
        val sidecarPath = directory.resolve("$finalName.xxh3")
        if (!Files.isRegularFile(dataPath, NOFOLLOW_LINKS) ||
            !Files.isRegularFile(sidecarPath, NOFOLLOW_LINKS)
        ) {
            return null
        }
        return try {
            val digest = hashAndValidate(dataPath, validator)
            check(readSidecar(sidecarPath) == digest.checksum) { "Sidecar checksum mismatch" }
            PublishedArtifact(dataPath, sidecarPath, digest.length, digest.checksum)
        } catch (_: Exception) {
            null
        }
    }

    private fun hashAndValidate(
        path: Path,
        validator: ArtifactValidator,
    ): Digest {
        check(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Not a regular data file: $path" }
        FileChannel.open(path, READ).use { channel ->
            val length = channel.size()
            val bytes = ByteArray(ioBufferSize)
            val buffer = ByteBuffer.wrap(bytes)
            xxh3Factory.create().use { hasher ->
                while (true) {
                    buffer.clear()
                    val count = channel.read(buffer)
                    if (count < 0) break
                    check(count > 0) { "Zero-byte read while hashing $path" }
                    hasher.update(bytes, 0, count)
                }
                val checksum = hasher.digest()
                channel.position(0)
                validator.validate(channel, length)
                check(channel.size() == length) { "Data length changed during validation" }
                return Digest(length, checksum)
            }
        }
    }

    private fun publishSmallFile(
        path: Path,
        content: ByteBuffer,
        mode: PublicationMode,
    ) {
        val temporary = createTemporary(path.fileName.toString(), "metadata")
        try {
            FileChannel.open(temporary, WRITE, TRUNCATE_EXISTING).use { channel ->
                writeFully(channel, content.duplicate())
                channel.force(true)
            }
            atomicMove(temporary, path, mode)
            directorySync.sync(directory)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun readSidecar(path: Path): Long {
        check(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Not a regular sidecar: $path" }
        return Files.readAllBytes(path).readXxh3CanonicalLong()
    }

    private fun createTemporary(
        finalName: String,
        kind: String,
    ): Path = Files.createTempFile(directory, ".$finalName.$kind.", ".tmp")

    private fun checkTargets(
        dataPath: Path,
        sidecarPath: Path,
        mode: PublicationMode,
    ) {
        if (mode == PublicationMode.CREATE) {
            check(!Files.exists(dataPath, NOFOLLOW_LINKS)) { "Data generation already exists: $dataPath" }
            check(!Files.exists(sidecarPath, NOFOLLOW_LINKS)) {
                "Sidecar generation already exists: $sidecarPath"
            }
        }
    }

    private fun atomicMove(
        source: Path,
        target: Path,
        mode: PublicationMode,
    ) {
        try {
            if (mode == PublicationMode.REPLACE) {
                Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } else {
                // Indexer serializes allocation and publication. The explicit preflight check
                // prevents accidental generation reuse before the same-filesystem atomic rename.
                check(!Files.exists(target, NOFOLLOW_LINKS)) { "Target already exists: $target" }
                Files.move(source, target, ATOMIC_MOVE)
            }
        } catch (error: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic rename is unavailable for $target", error)
        }
    }

    private fun validateFinalName(finalName: String) {
        require(finalName.isNotBlank()) { "finalName must not be blank" }
        require(finalName != "." && finalName != "..") { "Invalid finalName: $finalName" }
        require('/' !in finalName && '\\' !in finalName) { "finalName must be a single path segment" }
    }

    private fun writeFully(
        channel: FileChannel,
        source: ByteBuffer,
    ) {
        while (source.hasRemaining()) {
            check(channel.write(source) > 0) { "Zero-byte write" }
        }
    }

    private data class Digest(
        val length: Long,
        val checksum: Long,
    )

    companion object {
        const val DEFAULT_IO_BUFFER_SIZE = 256 * 1024
    }
}

object AndroidDirectorySync : DirectorySync {
    override fun sync(directory: Path) {
        val descriptor =
            Os.open(
                directory.toString(),
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC,
                0,
            )
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }
}
