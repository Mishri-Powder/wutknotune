// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import com.dynatrace.hash4j.hashing.Hashing
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.*
import java.util.UUID

/** Immutable cohorts plus atomic pointers keep readers on complete publications. */
class SnapshotStore(
    private val directory: Path,
    private val maxFileBytes: Long = 256L * 1024 * 1024,
) {
    init {
        require(maxFileBytes in 12..Int.MAX_VALUE.toLong())
        cleanupAbandoned()
    }

    fun read(): Snapshot? = readFlat()?.snapshot()

    fun readFlat(): FlatCatalogs? = readFlatWithCohort()?.second

    fun readFlatWithCohort(): Pair<String, FlatCatalogs>? = readCommitted()

    private fun readCommitted(): Pair<String, FlatCatalogs>? {
        var invalid: InvalidPublicationException? = null
        var unavailable: Exception? = null
        for (pointer in listOf("CURRENT", "PREVIOUS")) {
            if (!Files.exists(directory.resolve(pointer))) continue
            try {
                val name = readPointer(pointer)
                return name to readCohort(name)
            } catch (error: InvalidPublicationException) {
                invalid = error
            } catch (error: NoSuchFileException) {
                invalid = InvalidPublicationException("Missing named Cuetree publication member", error)
            } catch (error: Exception) {
                unavailable = error
            }
        }
        // AI-INTENT: An I/O outage must not authorize a host's destructive corruption recovery.
        // https://wiki.271.pics/Cuetree/API/Persistence#snapshotstore
        if (unavailable != null) throw IOException("Cuetree publication is unavailable", unavailable)
        if (invalid != null) throw InvalidPublicationException("No valid Cuetree publication", invalid)
        return null
    }

    private fun readPointer(pointer: String): String {
        val path = directory.resolve(pointer)
        if (Files.size(path) > 80) throw InvalidPublicationException("Invalid $pointer pointer")
        val name = Files.readAllBytes(path).toString(Charsets.UTF_8).trim()
        if (!COHORT_NAME.matches(name)) throw InvalidPublicationException("Invalid $pointer pointer")
        return name
    }

    private fun readCohort(name: String): FlatCatalogs {
        val root = directory.resolve(name)
        val manifest = root.resolve("MANIFEST")
        if (Files.size(manifest) > 1024) throw InvalidPublicationException("Invalid manifest")
        val entries = Files.readAllLines(manifest).map { line -> line.split(' ') }
        if (entries.size != 4 || entries.any { it.size != 3 } || entries.map { it[0] }.toSet() != FlatCatalogs.names.toSet()) throw InvalidPublicationException("Invalid manifest")
        val files =
            entries.associate { (file, sizeText, digest) ->
                val path = root.resolve(file)
                val size = sizeText.toLongOrNull() ?: throw InvalidPublicationException("Invalid size")
                if (size !in 12..maxFileBytes || Files.size(path) != size) throw InvalidPublicationException("Unexpected file size: $file")
                val bytes = Files.readAllBytes(path)
                if (!DIGEST.matches(digest) || xxh3(bytes) != digest) throw InvalidPublicationException("Checksum mismatch: $file")
                file to bytes
            }
        return try {
            FlatCatalogs.read(files)
        } catch (error: Exception) {
            throw InvalidPublicationException("Invalid Cuetree encoded facts", error)
        }
    }

    fun write(
        snapshot: Snapshot,
        cancellation: Cancellation = Cancellation(),
        json: ((FlatCatalogs) -> Map<String, String>)? = null,
    ): String {
        cancellation.check()
        return writePrepared(snapshot, FlatCatalogs.encode(snapshot), cancellation, json)
    }

    /** Reuse an encoding and its already-decoded snapshot from the same format gate. */
    fun writeFlat(
        flat: FlatCatalogs,
        snapshot: Snapshot,
        cancellation: Cancellation = Cancellation(),
        json: ((FlatCatalogs) -> Map<String, String>)? = null,
    ): String {
        cancellation.check()
        val header = requireNotNull(flat.mdia().header())
        require(header.generation() == snapshot.generation && header.identityScheme() == snapshot.identityScheme && header.metadataRevision() == snapshot.metadataRevision)
        return writePrepared(snapshot, flat, cancellation, json)
    }

    private fun writePrepared(
        snapshot: Snapshot,
        flat: FlatCatalogs,
        cancellation: Cancellation,
        json: ((FlatCatalogs) -> Map<String, String>)?,
    ): String {
        val companions = json?.invoke(flat)?.toMap()
        require(companions == null || companions.keys == FlatCatalogs.names.toSet()) { "JSON export must contain all four catalog names" }
        cancellation.check()
        Files.createDirectories(directory)
        FileChannel.open(directory.resolve(".writer.lock"), CREATE, WRITE).use { channel ->
            val lock = channel.tryLock() ?: throw IOException("Another writer owns this store")
            lock.use {
                val before = readCommitted()
                val prior = before?.second?.snapshot()
                require(prior == null || (snapshot.identityScheme == prior.identityScheme && snapshot.metadataRevision == prior.metadataRevision && snapshot.generation > prior.generation)) { "Non-increasing publication generation or incompatible identity/metadata revision" }
                val name = UUID.randomUUID().toString()
                val cohort = Files.createDirectory(directory.resolve(name))
                val manifest = mutableListOf<String>()
                for (file in FlatCatalogs.names) {
                    cancellation.check()
                    val bytes = flat.bytes(file)
                    require(bytes.size.toLong() <= maxFileBytes)
                    durableWrite(cohort.resolve(file), bytes)
                    manifest += "$file ${bytes.size} ${xxh3(bytes)}"
                }
                if (companions != null) {
                    for (file in FlatCatalogs.names) {
                        cancellation.check()
                        durableWrite(cohort.resolve("$file.json"), companions.getValue(file).toByteArray(Charsets.UTF_8))
                    }
                }
                durableWrite(cohort.resolve("MANIFEST"), (manifest.joinToString("\n") + "\n").toByteArray())
                syncDirectory(cohort)
                // PREVIOUS must be durable before CURRENT changes. Readers with open descriptors
                // continue on their immutable bytes even after an older directory is unlinked.
                if (before != null) replacePointer("PREVIOUS", before.first, name)
                replacePointer("CURRENT", name, name, cancellation)
                syncDirectory(directory)
                // Cleanup is post-commit: a failed unlink must never roll back a valid CURRENT.
                runCatching { prune(setOfNotNull(name, before?.first)) }
                return name
            }
        }
    }

    private fun replacePointer(
        pointer: String,
        name: String,
        suffix: String,
        cancellation: Cancellation? = null,
    ) {
        val next = directory.resolve("$pointer.$suffix")
        durableWrite(next, (name + "\n").toByteArray())
        // AI-INTENT: check after staging I/O, immediately before the atomic acceptance boundary.
        // https://wiki.271.pics/Cuetree/persistence
        cancellation?.check()
        Files.move(next, directory.resolve(pointer), ATOMIC_MOVE, REPLACE_EXISTING)
        syncDirectory(directory)
    }

    private fun cleanupAbandoned() {
        if (!Files.isDirectory(directory)) return
        // Only the writer lock permits pruning: another process may be preparing a candidate.
        runCatching {
            FileChannel.open(directory.resolve(".writer.lock"), CREATE, WRITE).use { channel ->
                channel.tryLock()?.use {
                    val retained =
                        listOf("CURRENT", "PREVIOUS")
                            .mapNotNull { pointer -> runCatching { readPointer(pointer) }.getOrNull() }
                            .toSet()
                    prune(retained)
                }
            }
        }
    }

    private fun prune(retained: Set<String>) {
        Files.newDirectoryStream(directory).use { entries ->
            for (path in entries) {
                val name = path.fileName.toString()
                if (STAGING_POINTER.matches(name)) {
                    Files.deleteIfExists(path)
                    continue
                }
                if (!COHORT_NAME.matches(name) || name in retained || !Files.isDirectory(path, NOFOLLOW_LINKS)) continue
                Files.walk(path).use { walk ->
                    walk.sorted(Comparator.reverseOrder()).forEach(Files::delete)
                }
            }
        }
        syncDirectory(directory)
    }

    private fun durableWrite(
        path: Path,
        bytes: ByteArray,
    ) {
        FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    private fun syncDirectory(path: Path) {
        FileChannel.open(path, READ).use { it.force(true) }
    }

    private fun xxh3(bytes: ByteArray): String {
        val hash = Hashing.xxh3_64().hashBytesToLong(bytes)
        val unsignedHex = java.lang.Long.toUnsignedString(hash, 16)
        return unsignedHex.padStart(16, '0')
    }

    private companion object {
        val DIGEST = Regex("[0-9a-f]{16}")
        val COHORT_NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val STAGING_POINTER = Regex("(CURRENT|PREVIOUS)\\.${COHORT_NAME.pattern}")
    }
}
