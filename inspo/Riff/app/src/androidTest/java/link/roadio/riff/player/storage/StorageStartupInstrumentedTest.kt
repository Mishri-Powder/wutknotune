package link.roadio.riff.player.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.settings.PlayerDataLocation
import link.roadio.riff.storage.root.ApplicationDataLocation
import link.roadio.riff.storage.root.ApplicationDataSelection
import link.roadio.riff.storage.root.ApplicationDataStorage
import link.roadio.riff.storage.root.PortableDataRoot
import link.roadio.riff.storage.root.RootBootstrap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/** Uses the real bootstrap/migration implementation with isolated directories and controllable mount evidence. */
class StorageStartupInstrumentedTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var preferences: RootBootstrap
    private lateinit var external: File
    private var mounted = emptyList<PortableDataRoot>()
    private val selected = ApplicationDataSelection(ApplicationDataLocation.SD, "chosen-volume")

    @Before fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, "storage-startup-${UUID.randomUUID()}").apply { mkdirs() }
        val legacyPreferences = base.getSharedPreferences(directory.name, Context.MODE_PRIVATE)
        context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun createDeviceProtectedStorageContext(): Context = this

                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = legacyPreferences

                override fun getNoBackupFilesDir(): File = File(directory, "internal").apply { mkdirs() }
            }
        preferences = RootBootstrap(context)
        external = File(directory, "external").apply { mkdirs() }
        preferences
            .edit()
            .putString("location", "SD")
            .putString("portable_volume_id", selected.portableVolumeId)
            .commit()
    }

    @After fun tearDown() {
        preferences.edit().clear().commit()
        directory.deleteRecursively()
    }

    private fun storage() = ApplicationDataStorage(context) { mounted }

    private fun mount(id: String = "chosen-volume") {
        mounted = listOf(PortableDataRoot(id, external, "USB storage"))
    }

    @Test fun recoveryUsesPackagedModeWithoutOpeningUnavailableRootAndKeepsValidOverride() {
        val storage = storage()
        assertFalse(storage.prepareForStartup())
        val before = preferences.all.toMap()
        assertEquals(link.roadio.riff.config.PackagedConfiguration.snapshot.applicationMode, storage.cachedApplicationMode())
        assertEquals(before, preferences.all)
        listOf("automotive", "aaos", "phone").forEach { mode ->
            storage.cacheApplicationMode(mode)
            assertEquals(mode, storage.cachedApplicationMode())
        }
        preferences.edit().putString("application_mode", "invalid").commit()
        assertEquals(link.roadio.riff.config.PackagedConfiguration.snapshot.applicationMode, storage.cachedApplicationMode())
        assertFalse(storage.prepareForStartup())
        assertFalse(File(context.noBackupFilesDir, "riff-data-v1").exists())
    }

    @Test fun repeatedMissingAndWrongVolumeStartsPreserveBootstrapAndState() {
        val internal = File(context.noBackupFilesDir, "riff-data-v1/player").apply { mkdirs() }
        val sentinel = File(internal, "untouched").apply { writeText("prior internal") }
        val before = preferences.all.toMap()
        repeat(5) {
            assertFalse(storage().prepareForStartup())
            assertEquals(null, storage().activeRootOrNull())
        }
        mount("wrong-volume")
        File(external, "riff-data-v1").mkdirs()
        assertFalse(storage().prepareForStartup())
        assertEquals(before, preferences.all)
        assertEquals("prior internal", sentinel.readText())
        assertEquals(listOf("untouched"), internal.list()!!.toList())
    }

    @Test fun delayedExactVolumeReturnRestoresProfilesPlaylistAndHistory() {
        val player = File(external, "riff-data-v1/player")
        PlayerStateStore(player).use { store ->
            store.commitProfileSelection(3, store.loadSettingsForProfile(3, PlayerDataLocation.SD))
            store.createPlaylist("Keep this book", listOf(MediaId(42)))
            store.recordPlaybackStart(MediaId(42), 7000, 100000)
        }
        assertFalse(storage().prepareForStartup())
        mount()
        val resolved = storage()
        assertTrue(resolved.prepareForStartup())
        assertEquals(selected, resolved.selection())
        PlayerStateStore(File(resolved.activeRoot(), "player")).use { store ->
            assertEquals(3, store.activeProfile())
            assertEquals(1, store.listPlaylists().size)
            assertEquals(1L, store.mediaPlaybackTotal(MediaId(42))?.totalStartedCount)
        }
        assertFalse(File(context.noBackupFilesDir, "riff-data-v1").exists())
    }

    @Test fun confirmedResetCreatesFreshInternalAndNeverModifiesExternal() {
        val oldInternal = File(context.noBackupFilesDir, "riff-data-v1/player")
        PlayerStateStore(oldInternal).use { it.createPlaylist("Old internal", listOf(MediaId(17))) }
        val sentinel = File(external, "keep").apply { writeText("external data") }
        assertTrue(storage().resetToInternal(selected))
        val reopened = storage()
        assertTrue(reopened.prepareForStartup())
        assertEquals(ApplicationDataLocation.HU, reopened.selection().location)
        PlayerStateStore(File(reopened.activeRoot(), "player")).use { assertTrue(it.listPlaylists().isEmpty()) }
        assertEquals("external data", sentinel.readText())
        mount()
        File(external, "riff-data-v1").mkdirs()
        assertEquals(ApplicationDataLocation.HU, storage().selection().location)
    }

    @Test fun staleResetCannotForgetReturnedVolume() {
        File(external, "riff-data-v1").mkdirs()
        mount()
        assertFalse(storage().resetToInternal(selected))
        assertEquals(selected, storage().selection())
        assertFalse(File(context.noBackupFilesDir, "riff-data-v1").exists())
    }

    @Test fun interruptedConfirmedResetFinishesWithoutAdoptingOldInternal() {
        val root = File(context.noBackupFilesDir, "riff-data-v1").apply { mkdirs() }
        val token = UUID.randomUUID().toString()
        File(root, "old-state").writeText("old")
        preferences.edit().putString("confirmed_internal_reset", token).commit()
        assertTrue(root.renameTo(File(context.noBackupFilesDir, ".riff-data-v1.reset-$token")))
        assertTrue(storage().prepareForStartup())
        assertEquals(ApplicationDataLocation.HU, storage().selection().location)
        assertFalse(File(root, "old-state").exists())
        assertTrue(File(root, "player").isDirectory)
        assertFalse(preferences.contains("confirmed_internal_reset"))
        assertTrue(storage().prepareForStartup())
    }

    @Test fun bootstrapIgnoresRetiredPreferencesAndReadersSeeCurrentRecord() {
        val legacy = context.getSharedPreferences("riff_data_root_bootstrap", Context.MODE_PRIVATE)
        legacy
            .edit()
            .putString("location", "SD")
            .putString("portable_volume_id", "legacy-volume")
            .commit()
        assertTrue(File(context.noBackupFilesDir, "riff-bootstrap/root.json").delete())
        assertEquals(null, storage().selection().portableVolumeId)
        assertEquals(ApplicationDataLocation.HU, storage().selection().location)
        val otherReader = RootBootstrap(context)
        assertEquals(null, otherReader.getString("portable_volume_id", null))
        preferences.edit().putString("portable_volume_id", "new-volume").commit()
        assertEquals("new-volume", otherReader.getString("portable_volume_id", null))
    }

    @Test fun unknownStorageSelectionFailsClosed() {
        preferences.edit().putString("location", "INVALID").commit()
        assertFalse(storage().prepareForStartup())
        assertEquals(null, storage().activeRootOrNull())
        assertEquals("INVALID", preferences.getString("location", null))
        assertFalse(File(context.noBackupFilesDir, "riff-data-v1").exists())
    }

    @Test fun internalInitializationDoesNotAdoptRetiredIndexerDirectory() {
        preferences.edit().clear().commit()
        val retired = File(context.noBackupFilesDir, "indexer").apply { mkdirs() }
        File(retired, "untouched").writeText("retired")
        assertTrue(storage().prepareForStartup())
        assertEquals(emptyList<String>(), File(storage().activeRoot(), "indexer").list()!!.toList())
        assertEquals("retired", File(retired, "untouched").readText())
    }

    @Test fun corruptBootstrapFailsClosed() {
        File(context.noBackupFilesDir, "riff-bootstrap/root.json").writeText("not valid JSON")
        assertTrue(runCatching { storage().prepareForStartup() }.isFailure)
        assertFalse(File(context.noBackupFilesDir, "riff-data-v1").exists())
        // Restore this isolated fixture so teardown can remove it normally.
        File(context.noBackupFilesDir, "riff-bootstrap/root.json").writeText("{}")
    }

    @Test fun relocationPreservesCompleteTreeAndForcesDestinationBeforeAuthoritySwitch() {
        preferences.edit().clear().commit()
        mount()
        val source = storage().activeRoot()
        File(source, "nested/empty").mkdirs()
        File(source, "nested/value").writeText("preserved")
        PlayerStateStore(File(source, "player")).use { it.createPlaylist("Relocated", listOf(MediaId(71))) }
        val target = File(external, "riff-data-v1").apply { mkdirs() }
        File(target, "retired").writeText("old target")
        val synchronized = mutableListOf<Pair<String, String?>>()
        val mover =
            ApplicationDataStorage(
                context,
                link.roadio.riff.storage.publish.DirectorySync { path ->
                    link.roadio.riff.storage.publish.AndroidDirectorySync
                        .sync(path)
                    synchronized += path.toString() to preferences.getString("location", null)
                },
            ) { mounted }

        mover.migrateTo(selected)

        assertEquals(selected, mover.selection())
        assertEquals("preserved", File(target, "nested/value").readText())
        assertTrue(File(target, "nested/empty").isDirectory)
        assertFalse(File(target, "retired").exists())
        assertEquals("preserved", File(source, "nested/value").readText())
        assertTrue(synchronized.any { it.first == external.path && it.second == "HU" })
        assertTrue(synchronized.any { it.first.endsWith("nested/empty") })
        PlayerStateStore(File(target, "player")).use { assertEquals("Relocated", it.listPlaylists().single().name) }
    }

    @Test fun failedDestinationDurabilityRetainsSourceAndRestoresPreviousTarget() {
        preferences.edit().clear().commit()
        mount()
        val source = storage().activeRoot()
        File(source, "value").writeText("source")
        val target = File(external, "riff-data-v1").apply { mkdirs() }
        File(target, "retired").writeText("old target")
        var failed = false
        val mover =
            ApplicationDataStorage(
                context,
                link.roadio.riff.storage.publish.DirectorySync { path ->
                    if (!failed && path.toFile() == external && File(target, "value").exists()) {
                        failed = true
                        throw java.io.IOException("destination force failed")
                    }
                    link.roadio.riff.storage.publish.AndroidDirectorySync
                        .sync(path)
                },
            ) { mounted }

        assertTrue(runCatching { mover.migrateTo(selected) }.isFailure)

        assertTrue(failed)
        assertEquals(ApplicationDataLocation.HU, mover.selection().location)
        assertEquals("source", File(source, "value").readText())
        assertEquals("old target", File(target, "retired").readText())
        assertFalse(preferences.contains("migration_phase"))
    }

    @Test fun missingMigrationParticipantDoesNotClearRecoveryJournal() {
        preferences
            .edit()
            .putString("location", "HU")
            .putString("migration_phase", "PUBLISHING")
            .putString("migration_source", File(context.noBackupFilesDir, "riff-data-v1").path)
            .putString("migration_target", File(external, "riff-data-v1").path)
            .commit()
        val before = preferences.all.toMap()
        assertFalse(storage().prepareForStartup())
        assertEquals(before, preferences.all)
    }
}
