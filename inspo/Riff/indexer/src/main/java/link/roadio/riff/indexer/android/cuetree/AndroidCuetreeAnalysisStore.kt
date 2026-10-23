package link.roadio.riff.indexer.android.cuetree

import android.os.ParcelFileDescriptor
import link.roadio.cuetree.Cancellation
import link.roadio.riff.indexer.android.ipc.ValidatedAndroidArtifact
import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot
import link.roadio.riff.indexer.core.analysis.CuetreeAnalysisCodec
import link.roadio.riff.indexer.core.ipc.IndexerArtifactKind
import link.roadio.riff.indexer.core.ipc.IndexerArtifactMetadata
import link.roadio.riff.storage.hash.nativeXxh3
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

/** MDRG owns its monotonic publication stream independently of Cuetree library publications. */
internal class AndroidCuetreeAnalysisStore(
    private val root: Path,
    private val beforeCurrentSwitch: () -> Unit = {},
    private val hash: (ByteArray) -> Long = ::nativeXxh3,
) {
    private val generation = PublicationGeneration(root)

    init {
        runCatching { retainTwoValidSnapshots() }
    }

    @Synchronized
    fun read(): AnalysisSnapshot? {
        val path = selectedPath() ?: return null
        return CuetreeAnalysisCodec.decode(verifiedBytes(path))
    }

    @Synchronized
    fun nextGeneration(): Long = Math.addExact(maxOf(generation.last(), read()?.generation ?: 0L), 1L)

    @Synchronized
    fun publish(
        snapshot: AnalysisSnapshot,
        cancellation: Cancellation = Cancellation(),
    ): IndexerArtifactMetadata {
        cancellation.check()
        snapshot.validate()
        val previous = selectedPath()
        require(snapshot.generation >= nextGeneration())
        val bytes = CuetreeAnalysisCodec.encode(snapshot)
        Files.createDirectories(root)
        val name = UUID.randomUUID().toString() + ".mdrg"
        val file = root.resolve(name)
        FileChannel.open(file, CREATE_NEW, WRITE).use { channel ->
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        val checksum = root.resolve("$name.xxh3")
        FileChannel.open(checksum, CREATE_NEW, WRITE).use { channel ->
            val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putLong(hash(bytes))
            buffer.flip()
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        val pointer = root.resolve("CURRENT.$name")
        FileChannel.open(pointer, CREATE_NEW, WRITE).use { channel ->
            val buffer = java.nio.ByteBuffer.wrap((name + "\n").toByteArray())
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        generation.remember(snapshot.generation)
        cancellation.check()
        // The accepted file must be durable as PREVIOUS before CURRENT names its replacement.
        if (previous != null) {
            val priorPointer = root.resolve("PREVIOUS.$name")
            Files.write(priorPointer, (previous.fileName.toString() + "\n").toByteArray(), CREATE_NEW, WRITE)
            FileChannel.open(priorPointer, WRITE).use { it.force(true) }
            Files.move(priorPointer, root.resolve("PREVIOUS"), ATOMIC_MOVE, REPLACE_EXISTING)
            FileChannel.open(root, READ).use { it.force(true) }
        }
        beforeCurrentSwitch()
        // AI-INTENT: the atomic CURRENT switch accepts a publication; observe cancellation at this boundary.
        // https://wiki.271.pics/Riff/LATEST/replaygain-storage#rg-6-one-independent-mdrg-database
        cancellation.check()
        Files.move(pointer, root.resolve("CURRENT"), ATOMIC_MOVE, REPLACE_EXISTING)
        FileChannel.open(root, READ).use { it.force(true) }
        runCatching { retainTwoValidSnapshots() }
        return metadata(snapshot.generation, bytes)
    }

    @Synchronized
    fun currentArtifact(): ValidatedAndroidArtifact? {
        val path = selectedPath() ?: return null
        val bytes = verifiedBytes(path)
        val snapshot = CuetreeAnalysisCodec.decode(bytes)
        return ValidatedAndroidArtifact(
            ParcelFileDescriptor.open(path.toFile(), ParcelFileDescriptor.MODE_READ_ONLY),
            metadata(snapshot.generation, bytes),
            cohortId = path.fileName.toString(),
        )
    }

    @Synchronized
    fun rememberGeneration() = generation.remember(nextGeneration() - 1L)

    private fun selectedPath(): Path? {
        val current = runCatching { currentPath() }.getOrNull()
        if (current != null &&
            runCatching {
                CuetreeAnalysisCodec.decode(verifiedBytes(current))
            }.isSuccess
        ) {
            return current
        }
        val previous = runCatching { currentPath("PREVIOUS") }.getOrNull() ?: return null
        return previous.takeIf { runCatching { CuetreeAnalysisCodec.decode(verifiedBytes(it)) }.isSuccess }
    }

    private fun verifiedBytes(path: Path): ByteArray {
        if (Files.size(path) !in 12..268_435_456L) throw IOException("Invalid MDRG size")
        val bytes = Files.readAllBytes(path)
        val checksum = Files.readAllBytes(root.resolve(path.fileName.toString() + ".xxh3"))
        if (checksum.size != 8 ||
            ByteBuffer.wrap(checksum).order(ByteOrder.LITTLE_ENDIAN).long != hash(bytes)
        ) {
            throw IOException("Invalid MDRG checksum")
        }
        return bytes
    }

    private fun retainTwoValidSnapshots() {
        if (!Files.isDirectory(root)) return
        val keep =
            listOf("CURRENT", "PREVIOUS")
                .mapNotNull { pointer -> runCatching { currentPath(pointer) }.getOrNull() }
                .flatMap { path -> listOf(path.fileName.toString(), path.fileName.toString() + ".xxh3") }
                .toSet()
        // Recovery follows named pointers only. Enumeration removes abandoned writes without promoting them.
        // The Indexer owner serializes this store; already delivered descriptors keep their immutable bytes.
        // https://wiki.271.pics/Riff/LATEST/replaygain-storage#rg-6-one-independent-mdrg-database
        Files.newDirectoryStream(root).use { files ->
            for (file in files) {
                val name = file.fileName.toString()
                if (name !in keep && (PAYLOAD_OR_SIDECAR.matches(name) || STAGING_POINTER.matches(name))) {
                    Files.deleteIfExists(file)
                }
            }
        }
        FileChannel.open(root, READ).use { it.force(true) }
    }

    private fun currentPath(pointerName: String = "CURRENT"): Path? {
        val pointer = root.resolve(pointerName)
        if (!Files.exists(pointer)) return null
        if (Files.size(pointer) > 80L) throw IOException("Invalid MDRG pointer")
        val name = String(Files.readAllBytes(pointer), Charsets.UTF_8).trim()
        if (!name.matches(Regex("[0-9a-f-]{36}\\.mdrg"))) throw IOException("Invalid MDRG pointer")
        return root.resolve(name)
    }

    private companion object {
        const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        val PAYLOAD_OR_SIDECAR = Regex("$UUID_PATTERN\\.mdrg(?:\\.xxh3)?")
        val STAGING_POINTER = Regex("(?:CURRENT|PREVIOUS)\\.$UUID_PATTERN\\.mdrg")
    }

    private fun metadata(
        generation: Long,
        bytes: ByteArray,
    ) = IndexerArtifactMetadata(
        IndexerArtifactKind.MDRG,
        AndroidCuetreeStore.VOLUME,
        generation,
        bytes.size.toLong(),
        CuetreeAnalysisCodec.SCHEMA_VERSION,
        CuetreeAnalysisCodec.SEMANTIC_VERSION,
        hash(bytes),
    )
}
