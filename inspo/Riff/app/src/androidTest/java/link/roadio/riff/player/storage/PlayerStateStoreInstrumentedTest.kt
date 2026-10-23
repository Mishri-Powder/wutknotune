package link.roadio.riff.player.storage

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.playback.AudiobookSpeed
import link.roadio.riff.player.core.settings.PlayerColorScheme
import link.roadio.riff.player.core.settings.PlayerDataLocation
import link.roadio.riff.player.core.settings.PlayerDiagonalChoice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlayerStateStoreInstrumentedTest {
    private lateinit var directory: File

    private fun ids(vararg values: Long): List<MediaId> = values.map(::MediaId)

    @Before
    fun createDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.cacheDir, "player-state-store-test")
        directory.deleteRecursively()
        assertTrue(directory.mkdirs())
    }

    @After
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun audiobookSpeedIsProfileOwnedDurableAndNeverRewritesQueues() {
        PlayerStateStore(directory).use { store ->
            for (profile in PlayerStateStore.VALID_PROFILES) assertEquals(AudiobookSpeed.NORMAL, store.loadAudiobookSpeed(profile))
            AudiobookSpeed.entries.forEach { speed ->
                store.saveAudiobookSpeed(3, speed)
                assertEquals(speed, store.loadAudiobookSpeed(3))
            }
            store.saveAudiobookSpeed(3, AudiobookSpeed.NORMAL)
            val queue = store.materializeQueue(ids(10, 20), PlaybackQueueKind.MANUAL, false)
            val revision = store.stateRevision()
            assertFalse(store.saveAudiobookSpeed(1, AudiobookSpeed.NORMAL))
            assertEquals(revision, store.stateRevision())
            assertTrue(store.saveAudiobookSpeed(1, AudiobookSpeed.FIFTEEN_TENTHS))
            assertEquals(revision + 1, store.stateRevision())
            store.commitProfileSelection(2, store.loadSettingsForProfile(2, PlayerDataLocation.HU))
            assertEquals(AudiobookSpeed.NORMAL, store.loadAudiobookSpeed())
            assertTrue(store.saveAudiobookSpeed(1, AudiobookSpeed.SEVEN_TENTHS))
            assertTrue(store.saveAudiobookSpeed(2, AudiobookSpeed.FOURTEEN_TENTHS))
            assertEquals(AudiobookSpeed.NORMAL, store.loadAudiobookSpeed(3))
            assertEquals(queue, store.loadActiveQueue())
            assertTrue(store.integrityCheck())
        }
        PlayerStateStore(directory).use { store ->
            assertEquals(2, store.activeProfile())
            assertEquals(AudiobookSpeed.SEVEN_TENTHS, store.loadAudiobookSpeed(1))
            assertEquals(AudiobookSpeed.FOURTEEN_TENTHS, store.loadAudiobookSpeed(2))
            assertEquals(AudiobookSpeed.NORMAL, store.loadAudiobookSpeed(3))
            val revision = store.stateRevision()
            assertTrue(runCatching { store.saveAudiobookSpeed(4, AudiobookSpeed.FIFTEEN_TENTHS) }.isFailure)
            assertEquals(revision, store.stateRevision())
            SQLiteDatabase.openDatabase(store.databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                listOf("6", "16", "7.5").forEach { invalid ->
                    assertTrue(
                        runCatching {
                            database.execSQL("UPDATE profile_settings SET audiobook_speed_tenths = $invalid WHERE profile_id = 1")
                        }.isFailure,
                    )
                }
            }
            assertEquals(AudiobookSpeed.SEVEN_TENTHS, store.loadAudiobookSpeed(1))
        }
    }

    @Test
    fun queuedPlaylistCommandsUseFrozenProfileAndPreserveEveryPosition() {
        PlayerStateStore(directory).use { store ->
            store.commitProfileSelection(2, store.loadSettingsForProfile(2, PlayerDataLocation.HU))
            val playlist = store.createPlaylist("Frozen", ids(10, 10, 20), "create", profileId = 1)
            assertTrue(store.listPlaylists().isEmpty())
            assertEquals(ids(10, 10, 20), playlist.occurrences.map { it.mediaId })
            val appended = store.appendToPlaylist(playlist.playlistId, ids(20, 20, 10), "append", profileId = 1)
            assertEquals(ids(10, 10, 20, 20, 20, 10), appended.occurrences.map { it.mediaId })
            assertEquals(appended, store.appendToPlaylist(playlist.playlistId, ids(20, 20, 10), "append", profileId = 1))
            store.renamePlaylist(playlist.playlistId, "Renamed", profileId = 1)
            val renamed = store.listPlaylists(1).single()
            assertEquals("Renamed", renamed.name)
            val removed = store.removePlaylistOccurrence(playlist.playlistId, renamed.occurrences[1].occurrenceId, profileId = 1)
            assertEquals(ids(10, 20, 20, 20, 10), removed.occurrences.map { it.mediaId })
            assertFalse(store.deletePlaylist(playlist.playlistId, profileId = 2))
            assertTrue(store.deletePlaylist(playlist.playlistId, profileId = 1))
            assertTrue(store.listPlaylists(1).isEmpty())
            val book = store.markBook("album:frozen", "Frozen book", ids(10, 20), 0, 0L, "First", 60_000_000L, profileId = 1)
            assertTrue(store.listPlaylists(2).isEmpty())
            val music = store.createPlaylist(book.name, ids(30), profileId = 1)
            store.renamePlaylist(music.playlistId, "Shared display name", profileId = 1)
            assertTrue(store.renamePlaylist(book.playlistId, "Shared display name", profileId = 1))
            assertTrue(runCatching { store.createPlaylist("Shared display name", ids(40), profileId = 1) }.isFailure)
            assertTrue(store.deletePlaylist(music.playlistId, profileId = 1))
            assertTrue(store.removeBookmark(book.playlistId, book.marks.single().markId, profileId = 1))
            assertTrue(store.listPlaylists(1).isEmpty())
            assertEquals(2, store.activeProfile())
            assertTrue(store.integrityCheck())
        }
    }

    @Test
    fun shortAndPresentZeroLowIdsRemainDistinctAcrossState() {
        val short = MediaId(0x1234, 0, lowPresent = false)
        val fullZero = MediaId(0x1234, 0, lowPresent = true)
        PlayerStateStore(directory).use { store ->
            val queue = store.materializeQueue(listOf(short, fullZero), PlaybackQueueKind.MANUAL, false)
            assertEquals(listOf(short, fullZero), queue.occurrences.map(PlaybackQueueOccurrence::mediaId))
            assertEquals(listOf(short, fullZero), store.loadActiveQueue()?.occurrences?.map(PlaybackQueueOccurrence::mediaId))
            store.recordPlaybackStart(short, 0, null)
            store.recordPlaybackStart(fullZero, 0, null)
            assertEquals(1L, store.mediaPlaybackTotal(short)?.totalStartedCount)
            assertEquals(1L, store.mediaPlaybackTotal(fullZero)?.totalStartedCount)
            assertEquals(setOf(short, fullZero), store.listeningEvents().map(ListeningEvent::mediaId).toSet())
        }
    }

    @Test
    fun albumResumePreservesOrderFlagsAndIsolatesFoldersAndUsers() {
        PlayerStateStore(directory).use { store ->
            store.beginPlaybackCycle()
            val album =
                store.materializeQueue(
                    ids(10, 20, 30),
                    PlaybackQueueKind.MANUAL,
                    true,
                    contextKey = "album:1",
                    albumContext = true,
                )
            val saved =
                PlaybackCheckpoint(album.occurrences[1].occurrenceId, 42_000, false, repeat = true, shuffle = true, chosenBindingId = 123)
            assertTrue(store.saveCheckpoint(album.revisionId, saved))
            val folder = store.materializeQueue(ids(10, 20, 30), PlaybackQueueKind.MANUAL, true, contextKey = "folder:1")
            store.saveCheckpoint(folder.revisionId, folder.checkpoint.copy(positionMs = 9_000))
            assertEquals(saved, store.loadSavedQueue("album:1")?.checkpoint)
            assertFalse(store.saveCheckpoint(album.revisionId, saved.copy(positionMs = 99_000)))
            val resumed =
                store.materializeQueue(
                    ids(30, 20, 10),
                    PlaybackQueueKind.MANUAL,
                    true,
                    contextKey = "album:1",
                    albumContext = true,
                    resume = true,
                )
            assertNotEquals(album.revisionId, resumed.revisionId)
            assertEquals(
                album.occurrences.map { it.mediaId to it.shuffleOrdinal },
                resumed.occurrences.map {
                    it.mediaId to
                        it.shuffleOrdinal
                },
            )
            assertEquals(42_000L, resumed.checkpoint.positionMs)
            assertTrue(resumed.checkpoint.repeat && resumed.checkpoint.shuffle)
            assertEquals(MediaId(20L), resumed.occurrences.first { it.occurrenceId == resumed.checkpoint.occurrenceId }.mediaId)
            store.commitProfileSelection(2, store.loadSettingsForProfile(2, PlayerDataLocation.HU))
            assertEquals(null, store.loadSavedQueue("album:1"))
            store.saveCheckpoint(resumed.revisionId, resumed.checkpoint.copy(positionMs = 55_000))
            assertEquals(42_000L, store.loadSavedQueue("album:1", 1)?.checkpoint?.positionMs)
            assertEquals(55_000L, store.loadSavedQueue("album:1", 2)?.checkpoint?.positionMs)
            assertTrue(store.integrityCheck())
        }
    }

    @Test
    fun savedAlbumTtlIsRenewedOnlyBySavingAndTemporaryContextsEndNextCycle() {
        PlayerStateStore(directory, recentHistoryGenerations = 2).use { store ->
            store.beginPlaybackCycle()
            store.materializeQueue(ids(10), PlaybackQueueKind.MANUAL, false, contextKey = "album:music", albumContext = true)
            store.materializeQueue(
                ids(20),
                PlaybackQueueKind.MANUAL,
                false,
                contextKey = "album:book",
                albumContext = true,
                audiobook = true,
            )
            store.materializeQueue(ids(30), PlaybackQueueKind.MANUAL, false, contextKey = "folder:1")
            store.beginPlaybackCycle()
            assertEquals(null, store.loadSavedQueue("folder:1"))
            assertEquals(null, store.loadActiveQueue())
            store.beginPlaybackCycle()
            assertTrue(store.loadSavedQueue("album:music") != null)
            store.beginPlaybackCycle()
            assertEquals(null, store.loadSavedQueue("album:music"))
            assertTrue(store.loadSavedQueue("album:book") != null)
            val resumed =
                store.materializeQueue(
                    ids(20),
                    PlaybackQueueKind.MANUAL,
                    false,
                    contextKey = "album:book",
                    albumContext = true,
                    audiobook = true,
                    resume = true,
                )
            store.saveCheckpoint(resumed.revisionId, resumed.checkpoint.copy(positionMs = 100), audiobook = true)
            repeat(6) { store.beginPlaybackCycle() }
            assertTrue(store.loadSavedQueue("album:book") != null)
            store.beginPlaybackCycle()
            assertEquals(null, store.loadSavedQueue("album:book"))
            assertEquals(null, store.loadActiveQueue())
            assertTrue(store.integrityCheck())
        }
    }

    @Test
    fun explicitTrackResetsPositionAndCompletionCannotBeResurrected() {
        PlayerStateStore(directory).use { store ->
            val first = store.materializeQueue(ids(10, 20), PlaybackQueueKind.MANUAL, true, contextKey = "album:1", albumContext = true)
            store.saveCheckpoint(first.revisionId, first.checkpoint.copy(positionMs = 42_000, shuffle = true))
            val selected =
                store.materializeQueue(
                    ids(10, 20),
                    PlaybackQueueKind.MANUAL,
                    true,
                    initialOrdinal = 1,
                    contextKey = "album:1",
                    albumContext = true,
                )
            assertEquals(0L, selected.checkpoint.positionMs)
            assertFalse(selected.checkpoint.shuffle)
            assertTrue(store.completePlaybackContext(selected.revisionId, 1))
            assertFalse(store.saveCheckpoint(selected.revisionId, selected.checkpoint.copy(positionMs = 99_000)))
            assertEquals(null, store.loadSavedQueue("album:1"))
            assertEquals(null, store.loadActiveQueue())
            assertTrue(store.integrityCheck())
        }
    }

    @Test
    fun audiobookHistoryRetainsTripleWindowWithoutChangingLifetimeCounts() {
        PlayerStateStore(directory, 2).use { store ->
            store.recordPlaybackStart(MediaId(10), 0, null)
            store.recordPlaybackStart(MediaId(20), 0, null, audiobook = true)
        }
        repeat(2) {
            PlayerStateStore(directory, 2).use { it.recordPlaybackStart(MediaId(30), 0, null) }
        }
        PlayerStateStore(directory, 2).use { store ->
            assertTrue(store.listeningEvents().none { it.mediaId == MediaId(10L) })
            assertTrue(store.listeningEvents().any { it.mediaId == MediaId(20L) })
            assertEquals(1L, store.mediaPlaybackTotal(MediaId(10))?.totalStartedCount)
        }
        repeat(4) {
            PlayerStateStore(directory, 2).use { it.recordPlaybackStart(MediaId(30), 0, null) }
        }
        PlayerStateStore(directory, 2).use { store ->
            assertTrue(store.listeningEvents().none { it.mediaId == MediaId(20L) })
            assertEquals(1L, store.mediaPlaybackTotal(MediaId(20))?.totalStartedCount)
        }
    }

    @Test
    fun unsupportedSchemaIsRejectedWithoutMigration() {
        val file = File(directory, PlayerStateStore.FILE_NAME)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE incompatible(value TEXT)")
            db.version = PlayerStateStore.SCHEMA_VERSION - 1
        }
        assertTrue(runCatching { PlayerStateStore(directory).close() }.isFailure)
        PlayerStateStore.openReadOnly(file).use { db ->
            assertEquals(PlayerStateStore.SCHEMA_VERSION - 1, db.version)
            assertTrue(db.rawQuery("SELECT name FROM sqlite_master WHERE name = 'incompatible'", null).use { it.moveToFirst() })
        }
    }

    @Test
    fun leadingArticlesDefaultToYesForEveryProfileAndPersistIndependently() {
        PlayerStateStore(directory).use { store ->
            PlayerStateStore.VALID_PROFILES.forEach { profile ->
                assertTrue(store.loadSettingsForProfile(profile, PlayerDataLocation.HU).ignoreLeadingArticles)
            }
            val settings = store.loadSettingsForProfile(2, PlayerDataLocation.HU).copy(ignoreLeadingArticles = false)
            assertTrue(store.commitProfileSelection(2, settings))
        }
        PlayerStateStore(directory).use { store ->
            assertTrue(store.loadSettingsForProfile(1, PlayerDataLocation.HU).ignoreLeadingArticles)
            assertFalse(store.loadSettingsForProfile(2, PlayerDataLocation.HU).ignoreLeadingArticles)
            assertTrue(store.loadSettingsForProfile(3, PlayerDataLocation.HU).ignoreLeadingArticles)
            assertTrue(store.integrityCheck())
        }
    }

    @Test
    fun reactionsQueuesProfilesAndPlaylistRevisionsAreDurable() {
        PlayerStateStore(directory).use { store ->
            assertEquals(0L, store.stateRevision())
            assertEquals(PlayerDataLocation.HU, store.loadSettings(PlayerDataLocation.HU).dataLocation)

            val likedAndBanned = store.updateReaction("track:1", StoredPreference.LIKED, toggleBan = true)
            assertEquals(StoredPreference.LIKED, likedAndBanned.preference)
            assertTrue(likedAndBanned.ownBanned)
            assertTrue(store.putLearnedState("taste", byteArrayOf(1, 2, 3)))
            assertEquals(listOf<Byte>(1, 2, 3), store.loadLearnedState("taste")?.toList())

            val queue =
                store.materializeQueue(
                    ids(7L, 7L, 9L),
                    PlaybackQueueKind.MANUAL,
                    true,
                    initialOrdinal = 1,
                )
            assertEquals(ids(7L, 7L, 9L), queue.occurrences.map { it.mediaId })
            assertNotEquals(queue.occurrences[0].occurrenceId, queue.occurrences[1].occurrenceId)
            assertEquals(queue.occurrences[1].occurrenceId, queue.checkpoint.occurrenceId)
            assertEquals(1, queue.sourceProfileId)
            assertTrue(queue.temporary)

            val eventId =
                store.recordListeningEvent(
                    mediaId = MediaId(7L),
                    playInstanceId = 1L,
                    eventType = ListeningEventType.PLAY_STARTED,
                    positionMs = 0L,
                    durationMs = null,
                )
            assertTrue(eventId > 0L)
            assertEquals(ids(7L), store.listeningEvents().map { it.mediaId })
            assertEquals(1L, store.mediaPlaybackTotal(MediaId(7L))?.totalStartedCount)

            val playlist = store.createPlaylist("Road", ids(7L, 7L), "create-1")
            val revisionAfterCreate = store.stateRevision()
            val retriedCreate = store.createPlaylist("Road", ids(7L, 7L), "create-1")
            assertEquals(playlist.playlistId, retriedCreate.playlistId)
            assertEquals(playlist.revisionId, retriedCreate.revisionId)
            assertEquals(revisionAfterCreate, store.stateRevision())
            val appended = store.appendToPlaylist(playlist.playlistId, ids(9L, 9L), "add-1")
            val revisionAfterAppend = store.stateRevision()
            val retried = store.appendToPlaylist(playlist.playlistId, ids(9L, 9L), "add-1")
            assertEquals(appended.revisionId, retried.revisionId)
            assertEquals(revisionAfterAppend, store.stateRevision())
            assertEquals(ids(7L, 7L, 9L, 9L), retried.occurrences.map { it.mediaId })
            assertTrue(runCatching { store.createPlaylist("ＲＯＡＤ", ids(7L)) }.isFailure)
            assertTrue(runCatching { store.createPlaylist("x".repeat(121), ids(7L)) }.isFailure)
            val other = store.createPlaylist("Other", ids(8L))
            assertTrue(runCatching { store.appendToPlaylist(other.playlistId, ids(7L), "add-1") }.isFailure)
            val updated =
                store.removePlaylistOccurrence(
                    playlist.playlistId,
                    retried.occurrences.first().occurrenceId,
                )
            assertNotEquals(playlist.revisionId, updated.revisionId)
            assertEquals(ids(7L, 9L, 9L), updated.occurrences.map { it.mediaId })
            val activeBeforePlaylistEdits =
                store.materializeQueue(
                    updated.occurrences.map { it.mediaId },
                    PlaybackQueueKind.PERMANENT_PLAYLIST,
                    true,
                    temporary = false,
                )
            assertFalse(activeBeforePlaylistEdits.temporary)
            assertTrue(store.renamePlaylist(playlist.playlistId, "Road Trip"))
            assertEquals(activeBeforePlaylistEdits, store.loadActiveQueue())
            assertTrue(store.deletePlaylist(playlist.playlistId))
            assertEquals(activeBeforePlaylistEdits, store.loadActiveQueue())

            val profileTwo =
                store
                    .loadSettingsForProfile(2, PlayerDataLocation.HU)
                    .copy(
                        colorScheme = PlayerColorScheme.NIGHT,
                        diagonal = PlayerDiagonalChoice.DIAGONAL,
                        autoSearch = false,
                        calibrationPpiX = 242f,
                        calibrationPpiY = null,
                    )
            assertTrue(store.commitProfileSelection(2, profileTwo))
            assertTrue(store.loadReactions().isEmpty())
            assertEquals(PlayerColorScheme.NIGHT, store.loadSettings(PlayerDataLocation.HU).colorScheme)
            assertEquals(PlayerDiagonalChoice.DIAGONAL, store.loadSettings(PlayerDataLocation.HU).diagonal)
            assertFalse(store.loadSettings(PlayerDataLocation.HU).autoSearch)
            assertEquals(242f, store.loadSettings(PlayerDataLocation.HU).calibrationPpiX)
            assertEquals(null, store.loadSettings(PlayerDataLocation.HU).calibrationPpiY)
            assertEquals(null, store.loadLearnedState("taste"))
            assertTrue(store.listeningEvents().isEmpty())
            assertTrue(store.listPlaylists().isEmpty())
            assertEquals(1, store.loadActiveQueue()?.sourceProfileId)
            assertFalse(store.commitProfileSelection(2, profileTwo))
            assertTrue(store.integrityCheck())
        }

        PlayerStateStore(directory).use { reopened ->
            assertEquals(2, reopened.activeProfile())
            assertFalse(reopened.loadSettings(PlayerDataLocation.HU).autoSearch)
            assertTrue(reopened.loadReactions().isEmpty())
            assertEquals(ids(7L, 9L, 9L), reopened.loadActiveQueue()?.occurrences?.map { it.mediaId })
        }
    }

    @Test
    fun aiSelectionControlsAreProfileScopedAndDurable() {
        val profileOne =
            StoredAiSelection(
                queueSize = 50,
                pteBandsMask = 5,
                arousal = 0.4f,
                valence = -0.25f,
            )
        val profileTwo =
            StoredAiSelection(
                queueSize = 10,
                pteBandsMask = 2,
                arousal = -0.7f,
                valence = 0.8f,
            )
        PlayerStateStore(directory).use { store ->
            assertEquals(StoredAiSelection(), store.loadAiSelection(1))
            assertEquals(StoredAiSelection(), store.loadAiSelection(2))
            assertTrue(store.saveAiSelection(1, profileOne))
            assertFalse(store.saveAiSelection(1, profileOne))
            assertTrue(store.saveAiSelection(2, profileTwo))
            assertEquals(profileOne, store.loadAiSelection(1))
            assertEquals(profileTwo, store.loadAiSelection(2))
        }

        PlayerStateStore(directory).use { reopened ->
            assertEquals(profileOne, reopened.loadAiSelection(1))
            assertEquals(profileTwo, reopened.loadAiSelection(2))
            assertEquals(StoredAiSelection(), reopened.loadAiSelection(3))
            assertTrue(reopened.loadActiveQueue() == null)
        }
    }

    @Test
    fun contentModeAndAudiobookOverridesAreProfileScopedAndAutoDeletesTheRow() {
        PlayerStateStore(directory).use { store ->
            assertEquals(StoredContentMode.MUSIC, store.loadContentMode())
            assertTrue(store.setContentMode(StoredContentMode.AUDIOBOOK))
            assertTrue(store.setAudiobookOverride("folder:books", StoredAudiobookOverride.BOOK))
            assertEquals(StoredContentMode.AUDIOBOOK, store.loadContentMode())
            assertEquals(
                mapOf("folder:books" to StoredAudiobookOverride.BOOK),
                store.loadAudiobookOverrides(),
            )

            val profileTwo = store.loadSettingsForProfile(2, PlayerDataLocation.HU)
            assertTrue(store.commitProfileSelection(2, profileTwo))
            assertEquals(StoredContentMode.MUSIC, store.loadContentMode())
            assertTrue(store.loadAudiobookOverrides().isEmpty())

            assertTrue(store.commitProfileSelection(1, store.loadSettingsForProfile(1, PlayerDataLocation.HU)))
            assertTrue(store.setAudiobookOverride("folder:books", null))
            assertTrue(store.loadAudiobookOverrides().isEmpty())
        }
    }

    @Test
    fun booksSurvivesProfileRoundTripReopenAndLateWritesToOutgoingProfile() {
        PlayerStateStore(directory).use { store ->
            store.setContentMode(StoredContentMode.AUDIOBOOK, 1)
            store.commitProfileSelection(2, store.loadSettingsForProfile(2, PlayerDataLocation.HU))
            assertEquals(StoredContentMode.MUSIC, store.loadContentMode())
            // A queued gesture belongs to profile 1 even after profile 2 becomes active.
            store.setContentMode(StoredContentMode.MUSIC, 1)
            store.setContentMode(StoredContentMode.AUDIOBOOK, 1)
            assertEquals(StoredContentMode.MUSIC, store.loadContentMode())
            store.commitProfileSelection(1, store.loadSettingsForProfile(1, PlayerDataLocation.HU))
            assertEquals(StoredContentMode.AUDIOBOOK, store.loadContentMode())
        }
        PlayerStateStore(directory).use { store ->
            assertEquals(1, store.activeProfile())
            assertEquals(StoredContentMode.AUDIOBOOK, store.loadContentMode())
            assertEquals(StoredContentMode.MUSIC, store.loadContentMode(2))
            assertEquals(StoredContentMode.MUSIC, store.loadContentMode(3))
        }
    }

    @Test
    fun albumAudiobookCommitClearsMemberRowsInOneRevision() {
        PlayerStateStore(directory).use { store ->
            store.setAudiobookOverride("track:1", StoredAudiobookOverride.BOOK)
            store.setAudiobookOverride("track:2", StoredAudiobookOverride.MUSIC)
            val before = store.stateRevision()

            assertTrue(
                store.commitAudiobookOverride(
                    "album:10",
                    StoredAudiobookOverride.BOOK,
                    setOf("track:1", "track:2"),
                ),
            )
            assertEquals(before + 1L, store.stateRevision())
            assertEquals(
                mapOf("album:10" to StoredAudiobookOverride.BOOK),
                store.loadAudiobookOverrides(),
            )
            assertFalse(
                store.commitAudiobookOverride(
                    "album:10",
                    StoredAudiobookOverride.BOOK,
                    setOf("track:1", "track:2"),
                ),
            )
            assertEquals(before + 1L, store.stateRevision())

            assertTrue(
                store.commitAudiobookOverride(
                    "album:10",
                    null,
                    setOf("track:1", "track:2"),
                ),
            )
            assertTrue(store.loadAudiobookOverrides().isEmpty())
        }
    }

    @Test
    fun coldStartPublishesOnlyChangedRevisions() {
        val first = PlayerStateRecovery.open(directory)
        first.store.close()
        assertEquals(listOf(0L), PlayerStateRecovery.validSnapshots(directory).map { it.revision })

        val unchanged = PlayerStateRecovery.open(directory)
        unchanged.store.close()
        assertEquals(listOf(0L), PlayerStateRecovery.validSnapshots(directory).map { it.revision })

        val changed = PlayerStateRecovery.open(directory)
        changed.store.updateReaction("track:2", StoredPreference.DISLIKED, toggleBan = false)
        changed.store.close()
        val checkpointed = PlayerStateRecovery.open(directory)
        checkpointed.store.close()
        assertEquals(listOf(1L, 0L), PlayerStateRecovery.validSnapshots(directory).map { it.revision })
    }

    @Test
    fun startsAreIdempotentAndRecentEventsExpireButLifetimeTotalSurvives() {
        PlayerStateStore(directory, recentHistoryGenerations = 1L).use { store ->
            val first =
                store.recordListeningEvent(MediaId(7L), 100L, ListeningEventType.PLAY_STARTED, 0L, 180_000L)
            val revision = store.stateRevision()
            val duplicate =
                store.recordListeningEvent(MediaId(7L), 100L, ListeningEventType.PLAY_STARTED, 0L, 180_000L)
            assertEquals(first, duplicate)
            assertEquals(revision, store.stateRevision())
            assertEquals(1L, store.mediaPlaybackTotal(MediaId(7L))?.totalStartedCount)
            store.recordListeningEvent(MediaId(7L), 100L, ListeningEventType.USER_SKIPPED, 90_000L, 180_000L)
            assertEquals(2, store.listeningEvents().size)
        }

        PlayerStateStore(directory, recentHistoryGenerations = 1L).use { store ->
            store.recordListeningEvent(MediaId(7L), 101L, ListeningEventType.PLAY_STARTED, 0L, 180_000L)
            assertEquals(listOf(101L), store.listeningEvents().map { it.playInstanceId })
            assertEquals(2L, store.mediaPlaybackTotal(MediaId(7L))?.totalStartedCount)

            val profileTwo = store.loadSettingsForProfile(2, PlayerDataLocation.HU)
            assertTrue(store.commitProfileSelection(2, profileTwo))
            assertEquals(null, store.mediaPlaybackTotal(MediaId(7L)))
            store.recordListeningEvent(MediaId(7L), 100L, ListeningEventType.PLAY_STARTED, 0L, null)
            assertEquals(1L, store.mediaPlaybackTotal(MediaId(7L))?.totalStartedCount)
        }
    }

    @Test
    fun aiHistorySnapshotAggregatesStartsAndUsesTheActiveProfile() {
        PlayerStateStore(directory).use { store ->
            store.recordPlaybackStart(MediaId(7L), 0L, 90_000L)
            store.recordPlaybackStart(MediaId(7L), 0L, 90_000L)
            val first = store.loadAiPlaybackHistory()
            assertEquals(1L, first.currentGeneration)
            assertEquals(2L, first.starts.single().starts)

            val profileTwo = store.loadSettingsForProfile(2, PlayerDataLocation.HU)
            assertTrue(store.commitProfileSelection(2, profileTwo))
            val second = store.loadAiPlaybackHistory()
            assertEquals(1L, second.currentGeneration)
            assertTrue(second.starts.isEmpty())
        }
    }

    @Test
    fun titleQueueRetainsAllMembersAndOrdinalMappingAfterReopen() {
        val full = ids(11L, 22L, 33L)
        PlayerStateStore(directory).use { store ->
            val queue =
                store.materializeQueue(
                    ids(11L, 33L),
                    PlaybackQueueKind.MANUAL,
                    playWhenReady = true,
                    contextKey = "album:pilot",
                    albumContext = true,
                    bookTitleContext = true,
                    bookTitleName = "Pilot",
                    bookTitleMembers = full,
                    bookQueueOrdinals = listOf(0, 2),
                    audiobook = true,
                )
            assertEquals(full, queue.bookTitleMembers)
            assertEquals(listOf(0, 2), queue.bookQueueOrdinals)
        }
        PlayerStateStore(directory).use { store ->
            val restored = requireNotNull(store.loadActiveQueue())
            assertEquals("Pilot", restored.bookTitleName)
            assertEquals(full, restored.bookTitleMembers)
            assertEquals(listOf(0, 2), restored.bookQueueOrdinals)
            val resumed =
                store.materializeQueue(
                    ids(11L, 33L),
                    PlaybackQueueKind.MANUAL,
                    playWhenReady = true,
                    contextKey = "album:pilot",
                    albumContext = true,
                    bookTitleContext = true,
                    bookTitleName = "Pilot",
                    bookTitleMembers = full,
                    bookQueueOrdinals = listOf(0, 2),
                    audiobook = true,
                    resume = true,
                )
            assertEquals(full, resumed.bookTitleMembers)
            assertEquals(listOf(0, 2), resumed.bookQueueOrdinals)
            assertTrue(store.integrityCheck())
        }
    }

    @Test
    fun bookmarkPlaybackRetainsTitleContextAndCanAppendToItsFrozenCollection() {
        val frozen = ids(11L, 22L)
        var collectionId = -1L
        PlayerStateStore(directory).use { store ->
            val collection = store.markBook("album:pilot", "Pilot", frozen, 1, 20_000_000L, "2. Voyage", 90_000_000L)
            collectionId = collection.playlistId
            val queue =
                store.materializeQueue(
                    frozen,
                    PlaybackQueueKind.PERMANENT_PLAYLIST,
                    playWhenReady = true,
                    temporary = false,
                    initialOrdinal = 1,
                    initialPositionMs = 20_000L,
                    contextKey = "bookmarks:$collectionId",
                    bookTitleContext = true,
                    bookTitleName = "Pilot",
                    bookTitleMembers = frozen,
                    bookQueueOrdinals = frozen.indices.toList(),
                    audiobook = true,
                )
            assertTrue(queue.bookTitleContext)
            assertEquals("bookmarks:$collectionId", queue.contextKey)
            assertEquals(frozen, queue.bookTitleMembers)
            assertEquals(listOf(0, 1), queue.bookQueueOrdinals)
        }
        PlayerStateStore(directory).use { store ->
            val queue = requireNotNull(store.loadActiveQueue())
            val collection = requireNotNull(store.loadPlaylistForProfile(collectionId, queue.sourceProfileId))
            assertEquals("album:pilot", collection.titleKey)
            val updated =
                store.markBook(
                    requireNotNull(collection.titleKey),
                    requireNotNull(queue.bookTitleName),
                    queue.bookTitleMembers,
                    1,
                    40_000_000L,
                    "2. Voyage",
                    90_000_000L,
                    queue.sourceProfileId,
                )
            assertEquals(collectionId, updated.playlistId)
            assertEquals(listOf(20_000_000L, 40_000_000L), updated.marks.map(BookmarkMark::positionUs))
            assertEquals(queue.revisionId, store.loadActiveQueue()?.revisionId)
        }
    }

    @Test
    fun bookmarksFreezeQueueAndGuardContinuousBookTimeAcrossChapters() {
        PlayerStateStore(directory).use { store ->
            val queue = ids(11L, 22L)
            val first = store.markBook("album:pilot", "Pilot", queue, 0, 90_000_000L, "1. Start", 100_000_000L)
            assertEquals(PlaylistKind.BOOK, first.kind)
            assertEquals(queue, first.occurrences.map(PlaylistOccurrence::mediaId))
            assertTrue(
                runCatching {
                    store.markBook("album:pilot", "Pilot", queue, 1, 3_000_000L, "2. Voyage", 90_000_000L)
                }.isFailure,
            )
            assertEquals(
                1,
                store
                    .listPlaylists()
                    .single()
                    .marks.size,
            )
            val second = store.markBook("album:pilot", "Pilot", queue, 1, 5_000_000L, "2. Voyage", 90_000_000L)
            assertEquals(listOf(0, 1), second.marks.map(BookmarkMark::ordinal))
            assertEquals(5_000_000L, second.marks.last().positionUs)
            assertTrue(
                runCatching {
                    store.markBook("album:pilot", "Pilot", ids(99L, 22L), 0, 40_000_000L, "New", null)
                }.isFailure,
            )
            assertTrue(store.removeBookmark(second.playlistId, second.marks.first().markId))
            assertTrue(store.removeBookmark(second.playlistId, second.marks.last().markId))
            assertTrue(store.listPlaylists().isEmpty())
            assertTrue(store.integrityCheck())
        }
    }

    @Test
    fun bookmarkAvailabilitySnapshotTracksWritesDurationsDeletionAndStartupPolicy() {
        val ids = ids(11L, 22L)

        fun queue(
            profile: Int = 1,
            context: String = "album:spacing",
        ) = PlaybackQueueSnapshot(
            1L,
            PlaybackQueueKind.MANUAL,
            profile,
            false,
            ids.mapIndexed { ordinal, mediaId -> PlaybackQueueOccurrence(ordinal.toLong(), ordinal, ordinal, mediaId) },
            PlaybackCheckpoint(0L),
            contextKey = context,
            bookTitleContext = true,
            bookTitleMembers = ids,
            bookQueueOrdinals = listOf(0, 1),
        )
        var playlistId = 0L
        PlayerStateStore(directory, bookmarkMinSpacingSeconds = 20L).use { store ->
            assertTrue(store.canMarkBook(queue(), 0, ids[0], 30_000_000L, null))
            val collection = store.markBook("album:spacing", "Spacing", ids, 0, 30_000_000L, "One", null)
            playlistId = collection.playlistId
            assertFalse(store.canMarkBook(queue(), 0, ids[0], 10_000_001L, null))
            assertTrue(store.canMarkBook(queue(), 0, ids[0], 10_000_000L, null))
            assertFalse(store.canMarkBook(queue(), 0, ids[0], 49_999_999L, null))
            assertTrue(store.canMarkBook(queue(), 0, ids[0], 50_000_000L, null))
            assertTrue(store.canMarkBook(queue(profile = 2), 0, ids[0], 30_000_000L, null))
            assertFalse(store.canMarkBook(queue(), 1, ids[1], 0L, null))
            store.recordBookDuration("album:spacing", 0, ids[0], 60_000_000L)
            assertTrue(store.canMarkBook(queue(), 1, ids[1], 0L, null))
            assertTrue(store.canMarkBook(queue(context = "bookmarks:$playlistId"), 1, ids[1], 0L, null))
        }
        PlayerStateStore(directory, bookmarkMinSpacingSeconds = 25L).use { store ->
            assertFalse(store.canMarkBook(queue(), 0, ids[0], 50_000_000L, null))
            assertTrue(store.canMarkBook(queue(), 0, ids[0], 55_000_000L, null))
            val collection = store.loadPlaylistForProfile(playlistId, 1)!!
            assertEquals(1, collection.marks.size)
            assertTrue(store.removeBookmark(playlistId, collection.marks.single().markId))
            assertTrue(store.canMarkBook(queue(), 0, ids[0], 30_000_000L, null))
            assertFalse(store.canMarkBook(queue(context = "bookmarks:$playlistId"), 0, ids[0], 30_000_000L, null))
            val another = store.markBook("album:spacing", "Spacing", ids, 0, 30_000_000L, "One", 60_000_000L)
            assertTrue(store.deletePlaylist(another.playlistId))
            assertTrue(store.canMarkBook(queue(), 0, ids[0], 30_000_000L, null))
            store.markBook("album:spacing", "Spacing", ids, 0, 30_000_000L, "One", 60_000_000L)
            assertTrue(store.pruneExpiredCollections(setOf(ids[0])))
            assertTrue(store.canMarkBook(queue(), 0, ids[0], 30_000_000L, null))
        }
    }

    @Test
    fun concurrentBookmarkCommandsCannotBypassConfiguredSpacing() {
        PlayerStateStore(directory, bookmarkMinSpacingSeconds = 20L).use { store ->
            val start = java.util.concurrent.CountDownLatch(1)
            val executor =
                java.util.concurrent.Executors
                    .newFixedThreadPool(2)
            try {
                val futures =
                    (0..1).map {
                        executor.submit<Boolean> {
                            start.await()
                            runCatching { store.markBook("album:race", "Race", ids(11L), 0, 30_000_000L, "One", 60_000_000L) }.isSuccess
                        }
                    }
                start.countDown()
                assertEquals(1, futures.count { it.get(10, java.util.concurrent.TimeUnit.SECONDS) })
                assertEquals(
                    1,
                    store
                        .listPlaylists()
                        .single()
                        .marks.size,
                )
                assertTrue(store.integrityCheck())
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun expiredMediaPrunesMusicButDeletesWholeBookCollection() {
        PlayerStateStore(directory).use { store ->
            val music = store.createPlaylist("Road", ids(11L, 22L))
            store.markBook("album:pilot", "Pilot", ids(11L, 22L), 0, 10_000_000L, "1. Start", 100_000_000L)
            assertFalse(store.pruneExpiredCollections(ids(11L, 22L).toSet()))
            assertTrue(store.pruneExpiredCollections(ids(11L).toSet()))
            val remaining = store.listPlaylists().single()
            assertEquals(music.playlistId, remaining.playlistId)
            assertEquals(ids(11L), remaining.occurrences.map(PlaylistOccurrence::mediaId))
            assertTrue(store.pruneExpiredCollections(emptySet()))
            assertTrue(store.listPlaylists().isEmpty())
            assertTrue(store.integrityCheck())
        }
    }

    @Test
    fun playbackStartAllocatesItsIdentityAndTotalInOneRevision() {
        PlayerStateStore(directory).use { store ->
            val playInstanceId = store.recordPlaybackStart(MediaId(0x7fffL), 1_500L, 240_000L)
            val event = store.listeningEvents().single()

            assertEquals(ListeningEventType.PLAY_STARTED, event.eventType)
            assertEquals(playInstanceId, event.playInstanceId)
            assertEquals(playInstanceId, event.stateRevision)
            assertEquals(1L, store.mediaPlaybackTotal(MediaId(0x7fffL))?.totalStartedCount)
            assertEquals(playInstanceId, store.mediaPlaybackTotal(MediaId(0x7fffL))?.stateRevision)
        }
    }

    @Test
    fun processDeathClosesOnlyDanglingStartsAsCensored() {
        PlayerStateStore(directory).use { store ->
            val completed = store.recordPlaybackStart(MediaId(7L), 0L, 90_000L)
            store.recordListeningEvent(MediaId(7L), completed, ListeningEventType.COMPLETED, 90_000L, 90_000L)
            val dangling = store.recordPlaybackStart(MediaId(8L), 0L, 120_000L)

            assertEquals(1, store.recordDanglingPlaybackAttemptsAsCensored(MediaId(8L), 42_000L, 120_000L))
            assertEquals(0, store.recordDanglingPlaybackAttemptsAsCensored(MediaId(8L), 43_000L, 120_000L))
            val recovered = store.listeningEvents().filter { it.playInstanceId == dangling }
            assertEquals(
                listOf(ListeningEventType.PLAY_STARTED, ListeningEventType.CENSORED_END),
                recovered.map { it.eventType },
            )
            assertEquals(42_000L, recovered.last().positionMs)
            assertEquals(1L, store.mediaPlaybackTotal(MediaId(7L))?.totalStartedCount)
            assertEquals(1L, store.mediaPlaybackTotal(MediaId(8L))?.totalStartedCount)
        }
    }

    @Test
    fun prePublicPreviousSchemaStateIsRecreatedWithoutDeletingUserConfig() {
        val live = File(directory, PlayerStateStore.FILE_NAME)
        SQLiteDatabase.openOrCreateDatabase(live, null).use { database ->
            database.execSQL("CREATE TABLE development_only(value TEXT)")
            database.version = 5
        }
        val userConfig = File(directory, "ui-overrides.json")
        userConfig.writeText("""{"player":{"recentHistoryGenerations":1}}""")

        PlayerStateRecovery.open(directory).store.use { store ->
            assertEquals(PlayerStateStore.SCHEMA_VERSION, PlayerStateStore.readSchemaVersion(store.databaseFile))
            assertEquals(0L, store.stateRevision())
        }
        assertTrue(userConfig.isFile)
        PlayerStateStore.openReadOnly(live).use { database ->
            assertFalse(
                database
                    .rawQuery(
                        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'development_only'",
                        null,
                    ).use { it.moveToFirst() },
            )
        }
    }

    @Test
    fun corruptionWithoutSnapshotRemainsFailClosed() {
        File(directory, PlayerStateStore.FILE_NAME).writeBytes("not a database".encodeToByteArray())
        assertTrue(runCatching { PlayerStateRecovery.open(directory) }.isFailure)
        assertTrue(runCatching { PlayerStateRecovery.open(directory) }.isFailure)
    }
}
