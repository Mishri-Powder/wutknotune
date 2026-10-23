package link.roadio.riff.storage.root

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import link.roadio.riff.config.PackagedConfiguration
import link.roadio.riff.storage.hash.NativeXxh3Factory
import link.roadio.riff.storage.publish.AndroidDirectorySync
import link.roadio.riff.storage.publish.DirectorySync
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID

enum class ApplicationDataLocation { HU, SD }

data class PortableDataRoot(
    val volumeId: String,
    val applicationDirectory: File,
    val displayName: String,
)

data class ApplicationDataSelection(
    val location: ApplicationDataLocation,
    val portableVolumeId: String? = null,
)

/** Resolves and migrates the one complete Riff application-data root. */
class ApplicationDataStorage(
    context: Context,
    private val directorySync: DirectorySync = AndroidDirectorySync,
    private val portableRoots: (() -> List<PortableDataRoot>)? = null,
) {
    private val context = context.applicationContext
    private val bootstrap = RootBootstrap(this.context)

    /** Bootstrap inspection never activates another root while Android mounts the selected volume. */
    fun prepareForStartup(): Boolean = bootstrap.transaction { prepareLocked() }

    private fun prepareLocked(): Boolean {
        if (bootstrap.contains(KEY_INTERNAL_RESET)) finishConfirmedInternalReset()
        if (!selectedRootAvailable() || !migrationPathsAvailable()) return false
        recoverInterruptedMigration()
        if (selection().location == ApplicationDataLocation.HU) initializeInternalRoot()
        return selectedRootAvailable()
    }

    fun selection(): ApplicationDataSelection = bootstrap.transaction { selectionLocked() }

    private fun selectionLocked(): ApplicationDataSelection {
        val location =
            ApplicationDataLocation.valueOf(
                bootstrap.getString(KEY_LOCATION, ApplicationDataLocation.HU.name)!!,
            )
        return ApplicationDataSelection(location, bootstrap.getString(KEY_VOLUME_ID, null))
    }

    fun activeRoot(): File = bootstrap.transaction { activeRootLocked() }

    private fun activeRootLocked(): File {
        check(prepareForStartup()) { "Selected application-data storage is unavailable" }
        return rootFor(selection())
    }

    fun activeRootOrNull(): File? = runCatching { activeRoot() }.getOrNull()

    fun selectedRootAvailable(): Boolean = bootstrap.transaction { selectedRootAvailableLocked() }

    private fun selectedRootAvailableLocked(): Boolean =
        !bootstrap.contains(KEY_INTERNAL_RESET) &&
            runCatching {
                val selected = selection()
                val root = rootFor(selected)
                selected.location == ApplicationDataLocation.HU || (root.isDirectory && probe(root))
            }.getOrDefault(false)

    fun cachedApplicationMode(): String =
        bootstrap.getString(KEY_APPLICATION_MODE, null)?.takeIf { it in setOf("aaos", "automotive", "phone") }
            ?: PackagedConfiguration.snapshot.applicationMode

    fun cacheApplicationMode(mode: String) {
        if (mode in setOf("aaos", "automotive", "phone")) bootstrap.edit().putString(KEY_APPLICATION_MODE, mode).commit()
    }

    fun savedDisplayName(): String =
        (bootstrap.getString(KEY_VOLUME_LABEL, null) ?: EXTERNAL_DISPLAY_NAME).filterNot(Char::isISOControl).take(120)

    fun displayName(selection: ApplicationDataSelection = selection()): String =
        when (selection.location) {
            ApplicationDataLocation.HU -> {
                INTERNAL_DISPLAY_NAME
            }

            ApplicationDataLocation.SD -> {
                eligiblePortableRoots()
                    .firstOrNull { it.volumeId == selection.portableVolumeId }
                    ?.displayName
                    ?: EXTERNAL_DISPLAY_NAME
            }
        }

    fun rootFor(selection: ApplicationDataSelection): File =
        when (selection.location) {
            ApplicationDataLocation.HU -> {
                huRoot()
            }

            ApplicationDataLocation.SD -> {
                val volumeId = requireNotNull(selection.portableVolumeId) { "Selected external storage has no stable volume identity" }
                val candidate = eligiblePortableRoots().singleOrNull { it.volumeId == volumeId }
                requireNotNull(candidate) { "Selected external storage is not mounted and writable" }
                File(candidate.applicationDirectory, ROOT_DIRECTORY)
            }
        }

    fun eligiblePortableRoots(): List<PortableDataRoot> =
        portableRoots?.invoke() ?: context
            .getExternalFilesDirs(null)
            .asSequence()
            .filterNotNull()
            .mapNotNull(::portableRoot)
            .distinctBy(PortableDataRoot::volumeId)
            .sortedBy(PortableDataRoot::volumeId)
            .toList()

    /**
     * Moves the complete closed root. Callers must quiesce both processes first.
     *
     * Authority: https://wiki.271.pics/Riff/LATEST/storage-roots
     * AI-INTENT: Keep this relative-path complete. Never replace it with a file allowlist.
     */
    fun migrateTo(targetSelection: ApplicationDataSelection): File = bootstrap.transaction { migrateLocked(targetSelection) }

    private fun migrateLocked(targetSelection: ApplicationDataSelection): File {
        val sourceSelection = selection()
        if (sourceSelection == targetSelection) return activeRoot()
        val source = rootFor(sourceSelection)
        require(source.isDirectory) { "Active application-data root is unavailable" }
        val target = rootFor(targetSelection)
        val parent = requireNotNull(target.parentFile)
        val transaction = File(parent, ".$ROOT_DIRECTORY.migrating")
        val replaced = File(parent, ".$ROOT_DIRECTORY.replaced")
        deleteTree(transaction)
        deleteTree(replaced)

        val sourceInventory = inventory(source)
        val requiredBytes = sourceInventory.values.sumOf { it.length }
        require(parent.usableSpace >= requiredBytes) { "Insufficient destination space" }
        val targetExisted = target.exists()
        recordMigration(
            phase = PHASE_COPYING,
            source = source,
            target = target,
            replaced = replaced,
            targetExisted = targetExisted,
            targetSelection = targetSelection,
        )
        var targetPublished = false

        try {
            copyTree(source, transaction)
            require(inventory(transaction) == sourceInventory) { "Destination inventory validation failed" }
            updateMigrationPhase(PHASE_PUBLISHING)
            if (targetExisted) {
                require(target.renameTo(replaced)) { "Could not quarantine previous destination root" }
                directorySync.sync(parent.toPath())
            }
            require(transaction.renameTo(target)) { "Could not publish migrated application-data root" }
            targetPublished = true
            // Bootstrap is on another filesystem. Persist destination directory entries before it
            // can name this tree as authority; forcing file contents alone does not order renames.
            directorySync.sync(parent.toPath())
            commitSelection(targetSelection, PHASE_SWITCHED, source, target, replaced)
            deleteTree(replaced)
            directorySync.sync(parent.toPath())
            clearMigration()
            return target
        } catch (error: Throwable) {
            deleteTree(transaction)
            if (selection() == sourceSelection) {
                if (targetPublished) deleteTree(target)
                restoreReplacedTarget(target, replaced)
                directorySync.sync(parent.toPath())
                clearMigration()
            }
            throw error
        }
    }

    /** Called only after explicit bootstrap confirmation, before either runtime owner opens. */
    fun resetToInternal(expected: ApplicationDataSelection): Boolean = bootstrap.transaction { resetLocked(expected) }

    private fun resetLocked(expected: ApplicationDataSelection): Boolean {
        check(selection() == expected && expected.location == ApplicationDataLocation.SD) { "Storage selection changed" }
        // AI-INTENT: A returned root wins over stale confirmation; never erase a resumed session.
        if (selectedRootAvailable()) return false
        require(bootstrap.edit().putString(KEY_INTERNAL_RESET, UUID.randomUUID().toString()).commit())
        finishConfirmedInternalReset()
        return true
    }

    private fun finishConfirmedInternalReset() {
        val token = requireNotNull(bootstrap.getString(KEY_INTERNAL_RESET, null))
        require(runCatching { UUID.fromString(token).toString() == token }.getOrDefault(false))
        val root = huRoot()
        val retired = File(context.noBackupFilesDir, ".$ROOT_DIRECTORY.reset-$token")
        // Retain the old Internal directory as inactive evidence, never as startup authority.
        if (root.exists() && !retired.exists()) require(root.renameTo(retired)) { "Could not retire previous Internal data" }
        require(File(root, "player").mkdirs() || File(root, "player").isDirectory)
        require(File(root, "indexer").mkdirs() || File(root, "indexer").isDirectory)
        AndroidDirectorySync.sync(root.toPath())
        AndroidDirectorySync.sync(context.noBackupFilesDir.toPath())
        require(
            bootstrap
                .edit()
                .putString(KEY_LOCATION, ApplicationDataLocation.HU.name)
                .remove(KEY_VOLUME_ID)
                .remove(KEY_VOLUME_LABEL)
                .remove(KEY_INTERNAL_RESET)
                .remove(KEY_PHASE)
                .remove(KEY_SOURCE)
                .remove(KEY_TARGET)
                .remove(KEY_REPLACED)
                .remove(KEY_TARGET_EXISTED)
                .remove(KEY_TARGET_LOCATION)
                .remove(KEY_TARGET_VOLUME_ID)
                .commit(),
        ) { "Could not activate fresh Internal application-data root" }
    }

    private fun migrationPathsAvailable(): Boolean {
        if (bootstrap.getString(KEY_PHASE, PHASE_IDLE) == PHASE_IDLE) return true
        return listOf(KEY_SOURCE, KEY_TARGET).all { key ->
            val path = bootstrap.getString(key, null)?.let(::File) ?: return@all false
            path.toPath().startsWith(context.noBackupFilesDir.toPath()) ||
                eligiblePortableRoots().any { path.toPath().startsWith(it.applicationDirectory.toPath()) }
        }
    }

    private fun huRoot(): File = File(context.noBackupFilesDir, ROOT_DIRECTORY)

    private fun portableRoot(directory: File): PortableDataRoot? {
        if (Environment.getExternalStorageState(directory) != Environment.MEDIA_MOUNTED) return null
        if (!Environment.isExternalStorageRemovable(directory) || Environment.isExternalStorageEmulated(directory)) return null
        val volume = context.getSystemService(StorageManager::class.java).getStorageVolume(directory) ?: return null
        val volumeId = volume.uuid?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!probe(directory)) return null
        val displayName =
            volume
                .getDescription(context)
                .trim()
                .takeIf(String::isNotEmpty)
                ?: EXTERNAL_DISPLAY_NAME
        return PortableDataRoot(volumeId.lowercase(Locale.ROOT), directory, displayName)
    }

    private fun probe(directory: File): Boolean =
        runCatching {
            require(directory.isDirectory || directory.mkdirs())
            val probe = File(directory, ".riff-probe-${UUID.randomUUID()}")
            val bytes = UUID.randomUUID().toString().toByteArray()
            RandomAccessFile(probe, "rw").use { file ->
                file.write(bytes)
                file.fd.sync()
                file.seek(0L)
                val actual = ByteArray(bytes.size)
                file.readFully(actual)
                require(actual.contentEquals(bytes))
            }
            require(probe.delete())
        }.isSuccess

    private fun initializeInternalRoot() {
        // AI-INTENT: Never create a fallback internal library while the selected external root is unavailable.
        val root = huRoot()
        if (!root.exists()) require(root.mkdirs()) { "Could not create HU application-data root" }
        val currentIndexer = File(root, "indexer")
        require(File(root, "player").isDirectory || File(root, "player").mkdirs())
        require(currentIndexer.isDirectory || currentIndexer.mkdirs())
        if (!bootstrap.contains(KEY_LOCATION)) {
            require(bootstrap.edit().putString(KEY_LOCATION, ApplicationDataLocation.HU.name).commit())
        }
    }

    private fun recoverInterruptedMigration() {
        val phase = bootstrap.getString(KEY_PHASE, PHASE_IDLE)
        if (phase == PHASE_IDLE) return
        val target = bootstrap.getString(KEY_TARGET, null)?.let(::File)
        val replaced = bootstrap.getString(KEY_REPLACED, null)?.let(::File)
        val targetExisted = bootstrap.getBoolean(KEY_TARGET_EXISTED, false)
        when (phase) {
            PHASE_COPYING -> {
                target?.let { destination ->
                    deleteTree(File(requireNotNull(destination.parentFile), ".$ROOT_DIRECTORY.migrating"))
                }
            }

            PHASE_PUBLISHING -> {
                if (target != null && replaced != null) {
                    deleteTree(File(requireNotNull(target.parentFile), ".$ROOT_DIRECTORY.migrating"))
                    if (targetExisted) {
                        if (replaced.exists()) {
                            deleteTree(target)
                            require(replaced.renameTo(target)) { "Could not restore previous destination root" }
                        }
                    } else {
                        deleteTree(target)
                    }
                }
            }

            PHASE_SWITCHED -> {
                target?.let { deleteTree(File(requireNotNull(it.parentFile), ".$ROOT_DIRECTORY.migrating")) }
                replaced?.let(::deleteTree)
            }
        }
        target?.parentFile?.let { directorySync.sync(it.toPath()) }
        clearMigration()
    }

    private fun recordMigration(
        phase: String,
        source: File,
        target: File,
        replaced: File,
        targetExisted: Boolean,
        targetSelection: ApplicationDataSelection,
    ) {
        require(
            bootstrap
                .edit()
                .putString(KEY_PHASE, phase)
                .putString(KEY_SOURCE, source.absolutePath)
                .putString(KEY_TARGET, target.absolutePath)
                .putString(KEY_REPLACED, replaced.absolutePath)
                .putBoolean(KEY_TARGET_EXISTED, targetExisted)
                .putString(KEY_TARGET_LOCATION, targetSelection.location.name)
                .putString(KEY_TARGET_VOLUME_ID, targetSelection.portableVolumeId)
                .commit(),
        ) { "Could not persist migration journal" }
    }

    private fun commitSelection(
        selection: ApplicationDataSelection,
        phase: String,
        source: File,
        target: File,
        replaced: File,
    ) {
        require(
            bootstrap
                .edit()
                .putString(KEY_LOCATION, selection.location.name)
                .putString(KEY_VOLUME_ID, selection.portableVolumeId)
                .putString(KEY_VOLUME_LABEL, displayName(selection))
                .putString(KEY_PHASE, phase)
                .putString(KEY_SOURCE, source.absolutePath)
                .putString(KEY_TARGET, target.absolutePath)
                .putString(KEY_REPLACED, replaced.absolutePath)
                .commit(),
        ) { "Could not activate migrated application-data root" }
    }

    private fun updateMigrationPhase(phase: String) {
        require(bootstrap.edit().putString(KEY_PHASE, phase).commit()) {
            "Could not update migration journal"
        }
    }

    private fun restoreReplacedTarget(
        target: File,
        replaced: File,
    ) {
        if (!replaced.exists()) return
        deleteTree(target)
        require(replaced.renameTo(target)) { "Could not restore previous destination root" }
    }

    private fun clearMigration() {
        require(
            bootstrap
                .edit()
                .remove(KEY_PHASE)
                .remove(KEY_SOURCE)
                .remove(KEY_TARGET)
                .remove(KEY_REPLACED)
                .remove(KEY_TARGET_EXISTED)
                .remove(KEY_TARGET_LOCATION)
                .remove(KEY_TARGET_VOLUME_ID)
                .commit(),
        ) { "Could not clear migration journal" }
    }

    private fun inventory(root: File): Map<String, FileDigest> {
        val result = sortedMapOf<String, FileDigest>()

        fun visit(file: File) {
            val relative = file.relativeTo(root).invariantSeparatorsPath.ifEmpty { "." }
            when {
                file.isDirectory -> {
                    result[relative] = FileDigest(directory = true, length = 0L, xxh3 = 0L)
                    file.listFiles()?.sortedBy(File::getName)?.forEach(::visit)
                        ?: error("Could not enumerate $relative")
                }

                file.isFile -> {
                    result[relative] = FileDigest(false, file.length(), xxh3(file))
                }

                else -> {
                    error("Unsupported application-data entry: $relative")
                }
            }
        }
        visit(root)
        return result
    }

    private fun copyTree(
        sourceRoot: File,
        targetRoot: File,
    ) {
        fun copy(
            source: File,
            target: File,
        ) {
            if (source.isDirectory) {
                require(target.mkdir() || target.isDirectory) { "Could not create migration directory" }
                source.listFiles()?.sortedBy(File::getName)?.forEach { child -> copy(child, File(target, child.name)) }
                    ?: error("Could not enumerate migration source")
                // Bottom-up durability includes empty directories and names of already forced files.
                directorySync.sync(target.toPath())
            } else {
                FileInputStream(source).use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output, COPY_BUFFER_BYTES)
                        output.fd.sync()
                    }
                }
            }
        }
        copy(sourceRoot, targetRoot)
    }

    private fun deleteTree(root: File) {
        if (!root.exists()) return
        if (root.isDirectory) root.listFiles()?.forEach(::deleteTree)
        require(root.delete()) { "Could not remove migration path" }
    }

    private fun xxh3(file: File): Long {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        return NativeXxh3Factory.create().use { stream ->
            FileInputStream(file).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    stream.update(buffer, 0, count)
                }
            }
            stream.digest()
        }
    }

    private data class FileDigest(
        val directory: Boolean,
        val length: Long,
        val xxh3: Long,
    )

    private companion object {
        const val ROOT_DIRECTORY = "riff-data-v1"
        const val KEY_INTERNAL_RESET = "confirmed_internal_reset"
        const val KEY_APPLICATION_MODE = "application_mode"
        const val KEY_VOLUME_LABEL = "portable_volume_label"
        const val KEY_LOCATION = "location"
        const val KEY_VOLUME_ID = "portable_volume_id"
        const val KEY_PHASE = "migration_phase"
        const val KEY_SOURCE = "migration_source"
        const val KEY_TARGET = "migration_target"
        const val KEY_REPLACED = "migration_replaced"
        const val KEY_TARGET_EXISTED = "migration_target_existed"
        const val KEY_TARGET_LOCATION = "migration_target_location"
        const val KEY_TARGET_VOLUME_ID = "migration_target_volume_id"
        const val PHASE_IDLE = "IDLE"
        const val PHASE_COPYING = "COPYING"
        const val PHASE_PUBLISHING = "PUBLISHING"
        const val PHASE_SWITCHED = "SWITCHED"
        const val COPY_BUFFER_BYTES = 1024 * 1024
        const val INTERNAL_DISPLAY_NAME = "Internal storage"
        const val EXTERNAL_DISPLAY_NAME = "External storage"
    }
}
