package link.roadio.riff.indexer.android.cuetree

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

/**
 * Rebuilds preserve the watermark so a Player cannot confuse new content with an accepted publication.
 * Each database owns its sequence: https://wiki.271.pics/Riff/LATEST/immutable-publication
 */
internal class PublicationGeneration(
    private val root: Path,
) {
    fun last(): Long {
        val path = root.resolve(FILE_NAME)
        if (!Files.exists(path)) return 0L
        require(Files.size(path) in 1..32) { "Invalid generation watermark" }
        return String(Files.readAllBytes(path), Charsets.UTF_8).trim().toLong().also { require(it >= 0) }
    }

    fun remember(generation: Long) {
        require(generation >= last())
        Files.createDirectories(root)
        val temporary = Files.createTempFile(root, ".generation-", ".tmp")
        try {
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap((generation.toString() + "\n").toByteArray())
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, root.resolve(FILE_NAME), ATOMIC_MOVE, REPLACE_EXISTING)
            FileChannel.open(root, READ).use { it.force(true) }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        const val FILE_NAME = "LAST_GENERATION"
    }
}
