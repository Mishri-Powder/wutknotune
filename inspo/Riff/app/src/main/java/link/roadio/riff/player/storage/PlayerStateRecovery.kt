package link.roadio.riff.player.storage

import android.util.Log
import link.roadio.riff.player.core.bookmarks.BookMarkSpacing
import link.roadio.riff.storage.hash.NativeXxh3Factory
import link.roadio.riff.storage.hash.readXxh3CanonicalLong
import link.roadio.riff.storage.hash.toXxh3CanonicalBytes
import link.roadio.riff.storage.publish.AndroidDirectorySync
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

data class PlayerStateOpenResult(
    val store: PlayerStateStore,
    val recovered: Boolean,
)

/**
 * Cold-start validation, checkpoint, snapshot publication, and corruption recovery.
 *
 * Authority: https://wiki.271.pics/Riff/LATEST/player-state
 * AI-INTENT: This block must finish before the Indexer client starts. Temporary and quarantine
 * files are evidence/recovery mechanics and never count toward the two-generation limit.
 */
object PlayerStateRecovery {
    fun open(
        playerDirectory: File,
        recentHistoryGenerations: Long = PlayerStateStore.DEFAULT_RECENT_HISTORY_GENERATIONS,
        bookmarkMinSpacingSeconds: Long = BookMarkSpacing.DEFAULT_MIN_SPACING_SECONDS,
    ): PlayerStateOpenResult {
        require(playerDirectory.isDirectory || playerDirectory.mkdirs())
        cleanTemporaryFiles(playerDirectory)
        val prePublicStateWasReset = resetUnsupportedPrePublicState(playerDirectory)
        var snapshots = validSnapshots(playerDirectory)
        val live = File(playerDirectory, PlayerStateStore.FILE_NAME)
        var recovered = false

        if (!live.exists() && snapshots.isNotEmpty()) {
            restore(playerDirectory, snapshots.first().file)
            recovered = true
        } else if (!live.exists() && !prePublicStateWasReset && hasPriorStateEvidence(playerDirectory)) {
            error("Player state is unavailable and no valid recovery snapshot exists")
        }

        // AI-INTENT: Some Android SQLite builds silently replace a non-SQLite file when opened with
        // OPEN_CREATE. Reject an invalid live header before SQLite can erase recovery evidence.
        if (live.exists() && !hasSqliteHeader(live)) {
            recoverCorruptLive(
                playerDirectory,
                snapshots,
                IllegalStateException("Player state does not have a SQLite header"),
            )
            recovered = true
        }

        var store =
            try {
                PlayerStateStore(playerDirectory, recentHistoryGenerations, bookmarkMinSpacingSeconds)
            } catch (error: Throwable) {
                if (!error.isDatabaseCorruption()) throw error
                recoverCorruptLive(playerDirectory, snapshots, error)
                recovered = true
                PlayerStateStore(playerDirectory, recentHistoryGenerations, bookmarkMinSpacingSeconds)
            }

        val integrityValid =
            try {
                store.integrityCheck()
            } catch (error: Throwable) {
                if (!error.isDatabaseCorruption()) throw error
                false
            }
        if (!integrityValid) {
            store.close()
            recoverCorruptLive(playerDirectory, snapshots, IllegalStateException("SQLite integrity_check failed"))
            recovered = true
            store = PlayerStateStore(playerDirectory, recentHistoryGenerations, bookmarkMinSpacingSeconds)
        }

        var revision = store.stateRevision()
        val newestSnapshot = snapshots.firstOrNull()
        if (newestSnapshot != null && newestSnapshot.revision > revision) {
            store.close()
            quarantineLiveSet(playerDirectory)
            restore(playerDirectory, newestSnapshot.file)
            recovered = true
            store = PlayerStateStore(playerDirectory, recentHistoryGenerations, bookmarkMinSpacingSeconds)
            revision = store.stateRevision()
        }
        if (snapshots.firstOrNull()?.revision != revision) {
            if (!store.checkpointTruncate()) {
                store.close()
                Log.w(TAG, "Player WAL checkpoint was busy; snapshot publication skipped")
                return PlayerStateOpenResult(
                    PlayerStateStore(playerDirectory, recentHistoryGenerations, bookmarkMinSpacingSeconds),
                    recovered,
                )
            }
            store.close()
            publishSnapshot(playerDirectory, live, revision)
            snapshots = validSnapshots(playerDirectory)
            retainNewestTwo(playerDirectory, snapshots)
            store = PlayerStateStore(playerDirectory, recentHistoryGenerations, bookmarkMinSpacingSeconds)
        }
        return PlayerStateOpenResult(store, recovered)
    }

    fun validSnapshots(playerDirectory: File): List<PlayerStateSnapshot> =
        playerDirectory
            .listFiles()
            .orEmpty()
            .mapNotNull { file ->
                val match = SNAPSHOT_PATTERN.matchEntire(file.name) ?: return@mapNotNull null
                val revision = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                validatePair(file, revision)?.let { PlayerStateSnapshot(revision, file) }
            }.sortedByDescending(PlayerStateSnapshot::revision)

    private fun recoverCorruptLive(
        playerDirectory: File,
        snapshots: List<PlayerStateSnapshot>,
        error: Throwable,
    ) {
        quarantineLiveSet(playerDirectory)
        val newest =
            snapshots.firstOrNull()
                ?: throw IllegalStateException("Player state is corrupt and no valid recovery snapshot exists", error)
        restore(playerDirectory, newest.file)
    }

    private fun publishSnapshot(
        directory: File,
        live: File,
        revision: Long,
    ) {
        val name = "player-state-${revision.toString().padStart(20, '0')}.sqlite"
        val data = File(directory, name)
        val sidecar = File(directory, "$name.xxh3")
        quarantineInvalidTargets(directory, data, sidecar, revision)
        check(!data.exists() && !sidecar.exists()) { "Snapshot generation already exists: $name" }

        val temporaryData = File.createTempFile(".$name.data.", ".tmp", directory)
        try {
            copyAndForce(live, temporaryData)
            atomicMove(temporaryData, data, replace = false)
            AndroidDirectorySync.sync(directory.toPath())
            check(PlayerStateStore.validateSnapshot(data) == revision) { "Copied snapshot revision changed" }

            val checksum = hash(data)
            val temporarySidecar = File.createTempFile(".$name.sidecar.", ".tmp", directory)
            try {
                FileChannel.open(temporarySidecar.toPath(), WRITE, TRUNCATE_EXISTING).use { channel ->
                    writeFully(channel, ByteBuffer.wrap(checksum.toXxh3CanonicalBytes()))
                    channel.force(true)
                }
                atomicMove(temporarySidecar, sidecar, replace = false)
                AndroidDirectorySync.sync(directory.toPath())
            } finally {
                temporarySidecar.delete()
            }
            check(validatePair(data, revision) != null) { "Published Player snapshot failed final validation" }
        } finally {
            temporaryData.delete()
        }
    }

    private fun validatePair(
        data: File,
        expectedRevision: Long,
    ): Long? {
        val sidecar = File("${data.path}.xxh3")
        if (!data.isFile || !sidecar.isFile || sidecar.length() != Long.SIZE_BYTES.toLong()) return null
        return runCatching {
            val recorded = sidecar.readBytes().readXxh3CanonicalLong()
            check(hash(data) == recorded) { "Snapshot checksum mismatch" }
            val revision =
                try {
                    PlayerStateStore.validateSnapshot(data)
                } finally {
                    cleanReadOnlySnapshotCompanions(data)
                }
            check(revision == expectedRevision) { "Snapshot filename and content revisions differ" }
            revision
        }.getOrNull()
    }

    private fun cleanReadOnlySnapshotCompanions(snapshot: File) {
        val companions =
            listOf("-wal", "-shm", "-journal")
                .map { suffix -> File("${snapshot.path}$suffix") }
                .filter(File::exists)
        companions.forEach { file -> check(file.delete()) { "Could not remove snapshot companion ${file.name}" } }
        if (companions.isNotEmpty()) AndroidDirectorySync.sync(requireNotNull(snapshot.parentFile).toPath())
    }

    private fun restore(
        directory: File,
        snapshot: File,
    ) {
        val live = File(directory, PlayerStateStore.FILE_NAME)
        val temporary = File.createTempFile(".${PlayerStateStore.FILE_NAME}.restore.", ".tmp", directory)
        try {
            copyAndForce(snapshot, temporary)
            check(PlayerStateStore.validateSnapshot(temporary) >= 0L)
            atomicMove(temporary, live, replace = true)
            AndroidDirectorySync.sync(directory.toPath())
        } finally {
            temporary.delete()
        }
    }

    private fun quarantineLiveSet(directory: File) {
        val members =
            listOf(
                PlayerStateStore.FILE_NAME,
                "${PlayerStateStore.FILE_NAME}-wal",
                "${PlayerStateStore.FILE_NAME}-shm",
                "${PlayerStateStore.FILE_NAME}-journal",
            ).map { File(directory, it) }
                .filter(File::exists)
        if (members.isEmpty()) return
        val quarantine = File(directory, "quarantine")
        require(quarantine.isDirectory || quarantine.mkdirs())
        val sequence = nextQuarantineSequence(quarantine)
        members.forEach { source ->
            val suffix = source.name.removePrefix(PlayerStateStore.FILE_NAME)
            atomicMove(
                source,
                File(quarantine, "player-state-corrupt-${sequence.toString().padStart(6, '0')}.sqlite$suffix"),
                replace = false,
            )
        }
        AndroidDirectorySync.sync(directory.toPath())
        AndroidDirectorySync.sync(quarantine.toPath())
    }

    private fun quarantineInvalidTargets(
        directory: File,
        data: File,
        sidecar: File,
        revision: Long,
    ) {
        if (!data.exists() && !sidecar.exists()) return
        if (validatePair(data, revision) != null) return
        val quarantine = File(directory, "quarantine")
        require(quarantine.isDirectory || quarantine.mkdirs())
        val sequence = nextQuarantineSequence(quarantine)
        listOf(data, sidecar).filter(File::exists).forEach { source ->
            atomicMove(
                source,
                File(quarantine, "snapshot-invalid-${sequence.toString().padStart(6, '0')}-${source.name}"),
                replace = false,
            )
        }
        AndroidDirectorySync.sync(directory.toPath())
        AndroidDirectorySync.sync(quarantine.toPath())
    }

    private fun retainNewestTwo(
        directory: File,
        snapshots: List<PlayerStateSnapshot>,
    ) {
        snapshots.drop(MAX_GENERATIONS).forEach { snapshot ->
            check(snapshot.file.delete()) { "Could not retire old Player snapshot ${snapshot.file.name}" }
            val sidecar = File("${snapshot.file.path}.xxh3")
            check(sidecar.delete()) { "Could not retire old Player sidecar ${sidecar.name}" }
        }
        if (snapshots.size > MAX_GENERATIONS) AndroidDirectorySync.sync(directory.toPath())
    }

    private fun cleanTemporaryFiles(directory: File) {
        val changed =
            directory
                .listFiles()
                .orEmpty()
                .filter { it.name.startsWith(".player-state-") && it.name.endsWith(".tmp") }
                .map(File::delete)
                .any { it }
        if (changed) AndroidDirectorySync.sync(directory.toPath())
    }

    /**
     * Pre-public clean break: development schema versions are data, not corruption.
     *
     * AI-INTENT: Delete only the complete Player SQLite live/snapshot set. Keep the private
     * user configuration beside it so file-only settings survive schema replacement.
     */
    private fun resetUnsupportedPrePublicState(directory: File): Boolean {
        val candidates =
            directory.listFiles().orEmpty().filter { file ->
                file.name == PlayerStateStore.FILE_NAME || SNAPSHOT_PATTERN.matches(file.name)
            }
        val unsupported =
            candidates.any { file ->
                hasSqliteHeader(file) &&
                    PlayerStateStore.readSchemaVersion(file)?.let {
                        it != PlayerStateStore.SCHEMA_VERSION
                    } == true
            }
        if (!unsupported) return false

        val obsoleteFiles =
            directory
                .listFiles()
                .orEmpty()
                .filter { file ->
                    file.name == PlayerStateStore.FILE_NAME ||
                        file.name.startsWith("${PlayerStateStore.FILE_NAME}-") ||
                        SNAPSHOT_PATTERN.matches(file.name) ||
                        SNAPSHOT_SIDECAR_PATTERN.matches(file.name) ||
                        SNAPSHOT_COMPANION_PATTERN.matches(file.name) ||
                        (file.name.startsWith(".player-state-") && file.name.endsWith(".tmp"))
                }
        val changed =
            obsoleteFiles
                .map { file ->
                    check(file.delete() || !file.exists())
                    true
                }.any { it }
        if (changed) AndroidDirectorySync.sync(directory.toPath())
        Log.i(TAG, "Discarded unsupported pre-public Player schema")
        return true
    }

    private fun hasPriorStateEvidence(directory: File): Boolean {
        val snapshotEvidence =
            directory.listFiles().orEmpty().any { file ->
                file.name.startsWith("player-state-") &&
                    (file.name.endsWith(".sqlite") || file.name.endsWith(".sqlite.xxh3"))
            }
        val quarantineEvidence =
            File(directory, "quarantine")
                .listFiles()
                .orEmpty()
                .any { it.name.startsWith("player-state-corrupt-") }
        return snapshotEvidence || quarantineEvidence
    }

    /**
     * Only readable bytes can establish a corrupt header. Propagate access failures so recovery
     * cannot quarantine a healthy database merely because its selected medium is unavailable.
     * Authority: https://wiki.271.pics/Riff/LATEST/player-state#player-state-profiles-and-recovery
     */
    internal fun hasSqliteHeader(file: File): Boolean {
        val header = ByteArray(SQLITE_HEADER.size)
        return file.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) return@use false
                check(count > 0) { "Zero-byte read while validating SQLite header" }
                offset += count
            }
            header.contentEquals(SQLITE_HEADER)
        }
    }

    private fun copyAndForce(
        source: File,
        target: File,
    ) {
        FileChannel.open(source.toPath(), READ).use { input ->
            FileChannel.open(target.toPath(), WRITE, TRUNCATE_EXISTING).use { output ->
                var position = 0L
                while (position < input.size()) {
                    val count = input.transferTo(position, input.size() - position, output)
                    check(count > 0L) { "Zero-byte copy while publishing Player state" }
                    position += count
                }
                output.force(true)
            }
        }
    }

    private fun hash(file: File): Long {
        val bytes = ByteArray(256 * 1024)
        NativeXxh3Factory.create().use { hasher ->
            file.inputStream().buffered(bytes.size).use { input ->
                while (true) {
                    val count = input.read(bytes)
                    if (count < 0) break
                    check(count > 0) { "Zero-byte read while hashing Player state" }
                    hasher.update(bytes, 0, count)
                }
            }
            return hasher.digest()
        }
    }

    private fun atomicMove(
        source: File,
        target: File,
        replace: Boolean,
    ) {
        try {
            if (replace) {
                Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } else {
                Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE)
            }
        } catch (error: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic rename is unavailable for ${target.path}", error)
        }
    }

    private fun writeFully(
        channel: FileChannel,
        buffer: ByteBuffer,
    ) {
        while (buffer.hasRemaining()) check(channel.write(buffer) > 0) { "Zero-byte write" }
    }

    private fun nextQuarantineSequence(directory: File): Int {
        val names = directory.listFiles().orEmpty().map(File::getName)
        return generateSequence(1, Int::inc).first { sequence ->
            val padded = sequence.toString().padStart(6, '0')
            names.none { name -> padded in name }
        }
    }

    private fun Throwable.isDatabaseCorruption(): Boolean =
        generateSequence(this) { it.cause }
            .any { error ->
                error.javaClass.simpleName.contains("Corrupt", ignoreCase = true) ||
                    listOf("malformed", "not a database", "disk image").any { token ->
                        error.message?.contains(token, ignoreCase = true) == true
                    }
            }

    private const val MAX_GENERATIONS = 2
    private const val TAG = "PlayerStateRecovery"
    private val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
    private val SNAPSHOT_PATTERN = Regex("player-state-([0-9]{20})\\.sqlite")
    private val SNAPSHOT_SIDECAR_PATTERN = Regex("player-state-[0-9]{20}\\.sqlite\\.xxh3")
    private val SNAPSHOT_COMPANION_PATTERN = Regex("player-state-[0-9]{20}\\.sqlite-(wal|shm|journal)")
}

data class PlayerStateSnapshot(
    val revision: Long,
    val file: File,
)
