package link.roadio.riff.player.storage

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.icu.lang.UCharacter
import android.icu.text.BreakIterator
import android.icu.text.Normalizer2
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.bookmarks.BookMarkAdmission
import link.roadio.riff.player.core.bookmarks.BookMarkPosition
import link.roadio.riff.player.core.bookmarks.BookMarkSpacing
import link.roadio.riff.player.core.playback.AudiobookSpeed
import link.roadio.riff.player.core.settings.PlayerColorScheme
import link.roadio.riff.player.core.settings.PlayerDataLocation
import link.roadio.riff.player.core.settings.PlayerDiagonalChoice
import link.roadio.riff.player.core.settings.PlayerSettings
import java.io.File
import java.util.Locale

/**
 * Sole durable authority owned by the Player process.
 *
 * Authority: https://wiki.271.pics/Riff/LATEST/player-state
 * AI-INTENT: Keep semantic mutations inside [mutate]. A successful logical mutation advances
 * state_revision exactly once; read-only UI projections must never advance it.
 */
class PlayerStateStore(
    private val playerDirectory: File,
    private val recentHistoryGenerations: Long = DEFAULT_RECENT_HISTORY_GENERATIONS,
    bookmarkMinSpacingSeconds: Long = BookMarkSpacing.DEFAULT_MIN_SPACING_SECONDS,
) : AutoCloseable {
    val databaseFile = File(playerDirectory, FILE_NAME)
    private var database: SQLiteDatabase? = null
    private var activeHistoryGeneration: Long? = null

    private data class BookMarkState(
        val playlistId: Long,
        val mediaIds: List<MediaId>,
        val marks: List<BookMarkPosition>,
        val durationsUs: Map<Int, Long>,
    )

    // Complete committed Book state only. Position callbacks never enter SQLite.
    // Authority: https://wiki.271.pics/Riff/LATEST/player-bookmarks#mark-action-and-spacing
    @Volatile private var bookMarkStates: Map<Pair<Int, String>, BookMarkState> = emptyMap()
    private val bookmarkMinimumUs = Math.multiplyExact(bookmarkMinSpacingSeconds, 1_000_000L)

    init {
        require(recentHistoryGenerations in 1L..10_000L)
        require(bookmarkMinSpacingSeconds in 1L..Int.MAX_VALUE.toLong())
        require(playerDirectory.isDirectory || playerDirectory.mkdirs())
        val opened = openConfigured(databaseFile)
        try {
            initializeSchema(opened)
            database = opened
            bookMarkStates = readBookMarkStates(opened)
        } catch (error: Throwable) {
            opened.close()
            throw error
        }
    }

    fun loadSettings(activeLocation: PlayerDataLocation): PlayerSettings = loadSettingsForProfile(activeProfile(), activeLocation)

    fun loadSettingsForProfile(
        profileId: Int,
        activeLocation: PlayerDataLocation,
    ): PlayerSettings {
        require(profileId in VALID_PROFILES)
        val db = requireDatabase()
        return db
            .rawQuery(
                """
                SELECT p.color_scheme, i.diagonal, p.long_press, p.auto_search,
                       i.calibration_x, i.calibration_y, p.ignore_leading_articles
                FROM profile_settings p
                JOIN installation_settings i ON i.id = 1
                WHERE p.profile_id = ?
                """.trimIndent(),
                arrayOf(profileId.toString()),
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Active Player profile is missing" }
                PlayerSettings(
                    colorScheme = enumOr(cursor.getString(0), PlayerColorScheme.SYSTEM),
                    diagonal = enumOr(cursor.getString(1), PlayerDiagonalChoice.AUTO),
                    dataLocation = activeLocation,
                    longPressStartsPlayback = cursor.getInt(2) != 0,
                    autoSearch = cursor.getInt(3) != 0,
                    ignoreLeadingArticles = cursor.getInt(6) != 0,
                    calibrationPpiX = cursor.takeFloat(4),
                    calibrationPpiY = cursor.takeFloat(5),
                )
            }
    }

    fun saveSettings(
        settings: PlayerSettings,
        profileId: Int = activeProfile(),
    ): Boolean =
        mutate { db, _ ->
            require(profileId in VALID_PROFILES)
            val activeProfile = profileId
            val current = loadSettingsForProfile(profileId, settings.dataLocation)
            val storedLocation =
                db.stringOrNull("SELECT data_location FROM installation_settings WHERE id = 1")
            if (
                current.copy(
                    dimBacklight = settings.dimBacklight,
                    replayGainMode = settings.replayGainMode,
                    replayGainTarget = settings.replayGainTarget,
                ) == settings &&
                storedLocation == settings.dataLocation.name
            ) {
                return@mutate false
            }
            writeSettings(db, settings, activeProfile)
            true
        }

    fun setDataLocation(location: PlayerDataLocation): Boolean =
        mutate { db, _ ->
            val stored =
                db.stringOrNull("SELECT data_location FROM installation_settings WHERE id = 1")
            if (stored == location.name) return@mutate false
            db.execSQL(
                "UPDATE installation_settings SET data_location = ? WHERE id = 1",
                arrayOf(location.name),
            )
            true
        }

    fun activeProfile(): Int = activeProfileId(requireDatabase())

    fun loadContentMode(profileId: Int = activeProfile()): StoredContentMode {
        require(profileId in VALID_PROFILES)
        val value =
            requireDatabase().stringOrNull(
                "SELECT content_mode FROM profile_settings WHERE profile_id = ?",
                arrayOf(profileId.toString()),
            )
        return enumOr<StoredContentMode>(value, StoredContentMode.MUSIC)
    }

    fun setContentMode(
        mode: StoredContentMode,
        profileId: Int = activeProfile(),
    ): Boolean =
        mutate { db, _ ->
            require(profileId in VALID_PROFILES)
            val profile = profileId
            if (loadContentMode(profile) == mode) return@mutate false
            db.execSQL(
                "UPDATE profile_settings SET content_mode = ? WHERE profile_id = ?",
                arrayOf<Any?>(mode.name, profile),
            )
            true
        }

    fun loadAudiobookSpeed(profileId: Int = activeProfile()): AudiobookSpeed {
        require(profileId in VALID_PROFILES)
        return requireDatabase()
            .rawQuery(
                "SELECT audiobook_speed_tenths FROM profile_settings WHERE profile_id = ?",
                arrayOf(profileId.toString()),
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Player profile is missing" }
                AudiobookSpeed.fromTenths(cursor.getInt(0))
            }
    }

    fun saveAudiobookSpeed(
        profileId: Int,
        speed: AudiobookSpeed,
    ): Boolean =
        mutate { db, _ ->
            require(profileId in VALID_PROFILES)
            if (loadAudiobookSpeed(profileId) == speed) return@mutate false
            db.execSQL(
                "UPDATE profile_settings SET audiobook_speed_tenths = ? WHERE profile_id = ?",
                arrayOf(speed.tenths, profileId),
            )
            true
        }

    fun loadAiSelection(profileId: Int = activeProfile()): StoredAiSelection {
        require(profileId in VALID_PROFILES)
        return requireDatabase()
            .rawQuery(
                """
                SELECT ai_queue_size, ai_pte_bands, ai_arousal, ai_valence
                FROM profile_settings
                WHERE profile_id = ?
                """.trimIndent(),
                arrayOf(profileId.toString()),
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Player profile is missing" }
                StoredAiSelection(
                    queueSize = cursor.getInt(0),
                    pteBandsMask = cursor.getInt(1),
                    arousal = cursor.getFloat(2),
                    valence = cursor.getFloat(3),
                )
            }
    }

    fun saveAiSelection(
        profileId: Int,
        selection: StoredAiSelection,
    ): Boolean {
        require(profileId in VALID_PROFILES)
        return mutate { db, _ ->
            if (loadAiSelection(profileId) == selection) return@mutate false
            db.execSQL(
                """
                UPDATE profile_settings
                SET ai_queue_size = ?, ai_pte_bands = ?, ai_arousal = ?, ai_valence = ?
                WHERE profile_id = ?
                """.trimIndent(),
                arrayOf<Any?>(
                    selection.queueSize,
                    selection.pteBandsMask,
                    selection.arousal,
                    selection.valence,
                    profileId,
                ),
            )
            true
        }
    }

    fun loadAudiobookOverrides(profileId: Int = activeProfile()): Map<String, StoredAudiobookOverride> {
        require(profileId in VALID_PROFILES)
        return requireDatabase()
            .rawQuery(
                "SELECT object_key, classification FROM audiobook_overrides WHERE profile_id = ? ORDER BY object_key",
                arrayOf(profileId.toString()),
            ).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(0), StoredAudiobookOverride.valueOf(cursor.getString(1)))
                    }
                }
            }
    }

    /** Null is Auto and therefore has no durable row. */
    fun setAudiobookOverride(
        objectKey: String,
        classification: StoredAudiobookOverride?,
    ): Boolean = commitAudiobookOverride(objectKey, classification, emptySet())

    /**
     * Commits one ROW_ACTIONS draft. Album commits also remove every member-track
     * override so one durable album row becomes the new local baseline.
     */
    fun commitAudiobookOverride(
        objectKey: String,
        classification: StoredAudiobookOverride?,
        clearedTrackKeys: Set<String>,
        profileId: Int = activeProfile(),
    ): Boolean {
        require(objectKey.isNotBlank())
        require(profileId in VALID_PROFILES)
        require(clearedTrackKeys.all { it.startsWith("track:") && it.substringAfter(':').isNotBlank() })
        val clearedKeys =
            if (objectKey.startsWith("album:")) {
                clearedTrackKeys
            } else {
                emptySet()
            }
        return mutate { db, _ ->
            val profile = profileId
            val keys = (clearedKeys + objectKey).sorted()
            val placeholders = keys.joinToString(",") { "?" }
            val arguments = arrayOf(profile.toString(), *keys.toTypedArray())
            val current =
                db
                    .rawQuery(
                        "SELECT object_key, classification FROM audiobook_overrides " +
                            "WHERE profile_id = ? AND object_key IN (" + placeholders + ")",
                        arguments,
                    ).use { cursor ->
                        buildMap {
                            while (cursor.moveToNext()) {
                                put(cursor.getString(0), StoredAudiobookOverride.valueOf(cursor.getString(1)))
                            }
                        }
                    }
            val desired = classification?.let { mapOf(objectKey to it) }.orEmpty()
            if (current == desired) return@mutate false
            db.delete(
                "audiobook_overrides",
                "profile_id = ? AND object_key IN (" + placeholders + ")",
                arguments,
            )
            if (classification != null) {
                db.execSQL(
                    "INSERT INTO audiobook_overrides(profile_id, object_key, classification) VALUES(?, ?, ?)",
                    arrayOf<Any?>(profile, objectKey, classification.name),
                )
            }
            true
        }
    }

    fun commitProfileSelection(
        profileId: Int,
        settings: PlayerSettings,
    ): Boolean {
        require(profileId in VALID_PROFILES)
        return mutate { db, _ ->
            val profileChanged = activeProfileId(db) != profileId
            val current = loadSettingsForProfile(profileId, settings.dataLocation)
            val storedLocation =
                db.stringOrNull("SELECT data_location FROM installation_settings WHERE id = 1")
            val settingsChanged =
                current.copy(
                    dimBacklight = settings.dimBacklight,
                    replayGainMode = settings.replayGainMode,
                    replayGainTarget = settings.replayGainTarget,
                ) != settings ||
                    storedLocation != settings.dataLocation.name
            if (!profileChanged && !settingsChanged) return@mutate false
            writeSettings(db, settings, profileId)
            db.execSQL("UPDATE active_profile SET profile_id = ? WHERE id = 1", arrayOf(profileId))
            true
        }
    }

    fun loadReactions(profileId: Int = activeProfile()): Map<String, StoredReaction> {
        require(profileId in VALID_PROFILES)
        val profile = profileId
        return requireDatabase()
            .rawQuery(
                "SELECT object_key, preference, own_banned FROM reactions WHERE profile_id = ? ORDER BY object_key",
                arrayOf(profile.toString()),
            ).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        put(
                            cursor.getString(0),
                            StoredReaction(
                                preference = enumOr(cursor.getString(1), StoredPreference.NEUTRAL),
                                ownBanned = cursor.getInt(2) != 0,
                            ),
                        )
                    }
                }
            }
    }

    /** Computes the toggle from the committed row, so rapid UI taps cannot lose an update. */
    fun updateReaction(
        objectKey: String,
        requested: StoredPreference?,
        toggleBan: Boolean,
        profileId: Int = activeProfile(),
    ): StoredReaction {
        require(objectKey.isNotBlank())
        require(profileId in VALID_PROFILES)
        var result = StoredReaction()
        mutate { db, _ ->
            val profile = profileId
            val current = readReaction(db, profile, objectKey)
            result =
                current.copy(
                    preference =
                        requested?.let { if (current.preference == it) StoredPreference.NEUTRAL else it }
                            ?: current.preference,
                    ownBanned = if (toggleBan) !current.ownBanned else current.ownBanned,
                )
            if (result == current) return@mutate false
            if (result == StoredReaction()) {
                db.execSQL(
                    "DELETE FROM reactions WHERE profile_id = ? AND object_key = ?",
                    arrayOf<Any?>(profile, objectKey),
                )
            } else {
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO reactions(profile_id, object_key, preference, own_banned)
                    VALUES(?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(profile, objectKey, result.preference.name, result.ownBanned.toSql()),
                )
            }
            true
        }
        return result
    }

    fun putLearnedState(
        key: String,
        value: ByteArray,
    ): Boolean {
        require(key.isNotBlank())
        return mutate { db, revision ->
            val profile = activeProfileId(db)
            val unchanged =
                db
                    .rawQuery(
                        "SELECT value FROM learned_state WHERE profile_id = ? AND state_key = ?",
                        arrayOf(profile.toString(), key),
                    ).use { cursor -> cursor.moveToFirst() && cursor.getBlob(0).contentEquals(value) }
            if (unchanged) return@mutate false
            db.execSQL(
                """
                INSERT OR REPLACE INTO learned_state(profile_id, state_key, value, updated_revision)
                VALUES(?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(profile, key, value, revision),
            )
            true
        }
    }

    fun loadLearnedState(key: String): ByteArray? {
        require(key.isNotBlank())
        return requireDatabase()
            .rawQuery(
                "SELECT value FROM learned_state WHERE profile_id = ? AND state_key = ?",
                arrayOf(activeProfile().toString(), key),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getBlob(0) else null }
    }

    /** Materializes a finite immutable queue and switches the active pointer atomically. */
    fun materializeQueue(
        mediaIds: List<MediaId>,
        kind: PlaybackQueueKind,
        playWhenReady: Boolean,
        temporary: Boolean = true,
        initialOrdinal: Int = 0,
        initialPositionMs: Long = 0L,
        chosenBindingId: Long? = null,
        contextKey: String = kind.name,
        albumContext: Boolean = false,
        bookTitleContext: Boolean = false,
        bookTitleName: String? = null,
        bookTitleMembers: List<MediaId> = emptyList(),
        bookQueueOrdinals: List<Int> = emptyList(),
        audiobook: Boolean = false,
        resume: Boolean = false,
        profileId: Int = activeProfile(),
        requireCurrentProfile: Boolean = false,
    ): PlaybackQueueSnapshot {
        require(mediaIds.isNotEmpty()) { "A playback queue must not be empty" }
        require(initialOrdinal in mediaIds.indices) { "Initial queue ordinal is out of bounds" }
        require(initialPositionMs >= 0L)
        if (bookTitleContext) {
            require(!bookTitleName.isNullOrBlank() && bookTitleMembers.isNotEmpty())
            require(bookQueueOrdinals.size == mediaIds.size)
            require(bookQueueOrdinals.distinct().size == bookQueueOrdinals.size)
            require(bookQueueOrdinals.indices.all { bookTitleMembers.getOrNull(bookQueueOrdinals[it]) == mediaIds[it] })
        }
        var queueRevisionId = 0L
        mutate { db, revision ->
            check(!requireCurrentProfile || activeProfileId(db) == profileId) { "Playback profile changed" }
            val saved = if (resume) loadSavedQueue(contextKey, profileId) else null
            val orderedIds = saved?.occurrences?.map(PlaybackQueueOccurrence::mediaId) ?: mediaIds
            val sourceMembers = saved?.bookTitleMembers?.takeIf(List<MediaId>::isNotEmpty) ?: bookTitleMembers
            val sourceOrdinals = saved?.bookQueueOrdinals?.takeIf(List<Int>::isNotEmpty) ?: bookQueueOrdinals
            val sourceName = saved?.bookTitleName ?: bookTitleName
            val startOrdinal =
                saved?.occurrences?.firstOrNull { it.occurrenceId == saved.checkpoint.occurrenceId }?.ordinal ?: initialOrdinal
            db.execSQL(
                "INSERT INTO playback_queue_revisions(kind, profile_id, temporary, created_state_revision, state) VALUES(?, ?, ?, ?, 'READY')",
                arrayOf<Any?>(kind.name, profileId, temporary.toSql(), revision),
            )
            queueRevisionId = lastInsertId(db)
            db.execSQL(
                "INSERT INTO queue_resume_metadata(queue_revision_id, context_key, album_context, book_title_context, book_title_name) VALUES(?, ?, ?, ?, ?)",
                arrayOf<Any?>(queueRevisionId, contextKey, albumContext.toSql(), bookTitleContext.toSql(), sourceName),
            )
            if (bookTitleContext) {
                require(sourceOrdinals.size == orderedIds.size)
                require(sourceOrdinals.indices.all { sourceMembers.getOrNull(sourceOrdinals[it]) == orderedIds[it] })
                val queueOrdinalBySource = sourceOrdinals.withIndex().associate { it.value to it.index }
                sourceMembers.forEachIndexed { sourceOrdinal, mediaId ->
                    db.execSQL(
                        "INSERT INTO queue_book_members(queue_revision_id, source_ordinal, queue_ordinal, media_id_high, media_id_low, media_id_low_present) VALUES(?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(
                            queueRevisionId,
                            sourceOrdinal,
                            queueOrdinalBySource[sourceOrdinal],
                            mediaId.high,
                            mediaId.low,
                            mediaId.lowPresent.toSql(),
                        ),
                    )
                }
            }
            val shufflePosition = IntArray(orderedIds.size)
            if (saved != null) {
                saved.occurrences.forEach { shufflePosition[it.ordinal] = it.shuffleOrdinal }
            } else {
                orderedIds.indices.shuffled().forEachIndexed { position, ordinal -> shufflePosition[ordinal] = position }
            }
            orderedIds.forEachIndexed { ordinal, mediaId ->
                db.execSQL(
                    "INSERT INTO playback_queue_occurrences(queue_revision_id, ordinal, shuffle_ordinal, media_id_high, media_id_low, media_id_low_present) VALUES(?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        queueRevisionId,
                        ordinal,
                        shufflePosition[ordinal],
                        mediaId.high,
                        mediaId.low,
                        mediaId.lowPresent.toSql(),
                    ),
                )
            }
            val initialOccurrence = queueOccurrenceIdAtOrdinal(db, queueRevisionId, startOrdinal)
            val checkpoint =
                (saved?.checkpoint ?: PlaybackCheckpoint(null, positionMs = initialPositionMs, chosenBindingId = chosenBindingId))
                    .copy(occurrenceId = initialOccurrence, playWhenReady = playWhenReady)
            activateCheckpoint(db, queueRevisionId, checkpoint)
            saveResume(db, queueRevisionId, checkpoint, profileId, audiobook)
            true
        }
        return requireNotNull(loadQueue(queueRevisionId))
    }

    fun loadActiveQueue(): PlaybackQueueSnapshot? {
        val queueId = requireDatabase().longOrNull("SELECT queue_revision_id FROM active_queue WHERE id = 1")
        return queueId?.let(::loadQueue)
    }

    fun saveCheckpoint(
        queueRevisionId: Long,
        checkpoint: PlaybackCheckpoint,
        profileId: Int = activeProfile(),
        audiobook: Boolean = false,
        reactivateCompleted: Boolean = false,
    ): Boolean =
        mutate { db, _ ->
            val active = db.longOrNull("SELECT queue_revision_id FROM active_queue WHERE id = 1")
            if (active != queueRevisionId) {
                if (!reactivateCompleted || active != null) return@mutate false
                activateCheckpoint(db, queueRevisionId, checkpoint)
            }
            checkpoint.occurrenceId?.let { occurrenceId ->
                check(
                    db
                        .rawQuery(
                            "SELECT 1 FROM playback_queue_occurrences WHERE queue_revision_id = ? AND occurrence_id = ?",
                            arrayOf(queueRevisionId.toString(), occurrenceId.toString()),
                        ).use(Cursor::moveToFirst),
                ) { "Checkpoint occurrence is not in the queue revision" }
            }
            db.execSQL(
                """
                INSERT OR REPLACE INTO playback_checkpoint(
                    id, queue_revision_id, occurrence_id, position_ms, play_when_ready,
                    repeat_enabled, shuffle_enabled, chosen_binding_id
                ) VALUES(1, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    queueRevisionId,
                    checkpoint.occurrenceId,
                    checkpoint.positionMs,
                    checkpoint.playWhenReady.toSql(),
                    checkpoint.repeat.toSql(),
                    checkpoint.shuffle.toSql(),
                    checkpoint.chosenBindingId,
                ),
            )
            saveResume(db, queueRevisionId, checkpoint, profileId, audiobook)
            true
        }

    /** Begins a cold store cycle; ordinary UI recreation never calls this. */
    fun beginPlaybackCycle(): Boolean =
        mutate { db, _ ->
            val cycle = playbackCycle(db)
            check(cycle < Long.MAX_VALUE)
            db.execSQL("UPDATE playback_cycle SET cycle = ? WHERE id = 1", arrayOf(cycle + 1L))
            db.execSQL(
                "DELETE FROM saved_playback WHERE album_context = 0 OR ? - saved_cycle > ? * retention_multiplier",
                arrayOf(cycle + 1L, recentHistoryGenerations),
            )
            db.execSQL(
                "DELETE FROM profile_last_playback WHERE NOT EXISTS (SELECT 1 FROM saved_playback s WHERE s.profile_id = profile_last_playback.profile_id AND s.context_key = profile_last_playback.context_key)",
            )
            val last =
                db.stringOrNull(
                    "SELECT context_key FROM profile_last_playback WHERE profile_id = ?",
                    arrayOf(activeProfileId(db).toString()),
                )
            val saved = last?.let { loadSavedQueue(it, activeProfileId(db)) }
            db.delete("playback_checkpoint", null, null)
            db.delete("active_queue", null, null)
            if (saved != null) activateCheckpoint(db, saved.revisionId, saved.checkpoint)
            db.execSQL(
                "DELETE FROM playback_queue_revisions WHERE queue_revision_id NOT IN (SELECT queue_revision_id FROM saved_playback) AND queue_revision_id NOT IN (SELECT queue_revision_id FROM active_queue)",
            )
            true
        }

    fun loadSavedQueue(
        contextKey: String,
        profileId: Int = activeProfile(),
    ): PlaybackQueueSnapshot? {
        val db = requireDatabase()
        return db
            .rawQuery(
                "SELECT queue_revision_id, occurrence_id, position_ms, play_when_ready, repeat_enabled, shuffle_enabled, chosen_binding_id FROM saved_playback WHERE profile_id = ? AND context_key = ? AND (album_context = 1 OR saved_cycle = ?) AND ? - saved_cycle <= ? * retention_multiplier",
                arrayOf(
                    profileId.toString(),
                    contextKey,
                    playbackCycle(db).toString(),
                    playbackCycle(db).toString(),
                    recentHistoryGenerations.toString(),
                ),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                loadQueue(cursor.getLong(0))?.copy(
                    checkpoint =
                        PlaybackCheckpoint(
                            occurrenceId = cursor.takeLong(1),
                            positionMs = cursor.getLong(2),
                            playWhenReady = cursor.getInt(3) != 0,
                            repeat = cursor.getInt(4) != 0,
                            shuffle = cursor.getInt(5) != 0,
                            chosenBindingId = cursor.takeLong(6),
                        ),
                )
            }
    }

    fun completePlaybackContext(
        queueRevisionId: Long,
        profileId: Int,
    ): Boolean =
        mutate { db, _ ->
            if (db.longOrNull("SELECT queue_revision_id FROM active_queue WHERE id = 1") != queueRevisionId) return@mutate false
            db.execSQL(
                "DELETE FROM profile_last_playback WHERE profile_id = ? AND context_key IN (SELECT context_key FROM saved_playback WHERE profile_id = ? AND queue_revision_id = ?)",
                arrayOf<Any?>(profileId, profileId, queueRevisionId),
            )
            db.delete(
                "saved_playback",
                "profile_id = ? AND queue_revision_id = ?",
                arrayOf(profileId.toString(), queueRevisionId.toString()),
            )
            db.delete("playback_checkpoint", null, null)
            db.delete("active_queue", null, null)
            true
        }

    private fun playbackCycle(db: SQLiteDatabase): Long = checkNotNull(db.longOrNull("SELECT cycle FROM playback_cycle WHERE id = 1"))

    private fun activateCheckpoint(
        db: SQLiteDatabase,
        queueId: Long,
        checkpoint: PlaybackCheckpoint,
    ) {
        db.execSQL("INSERT OR REPLACE INTO active_queue(id, queue_revision_id) VALUES(1, ?)", arrayOf(queueId))
        db.execSQL(
            "INSERT OR REPLACE INTO playback_checkpoint(id, queue_revision_id, occurrence_id, position_ms, play_when_ready, repeat_enabled, shuffle_enabled, chosen_binding_id) VALUES(1, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                queueId,
                checkpoint.occurrenceId,
                checkpoint.positionMs,
                checkpoint.playWhenReady.toSql(),
                checkpoint.repeat.toSql(),
                checkpoint.shuffle.toSql(),
                checkpoint.chosenBindingId,
            ),
        )
    }

    private fun saveResume(
        db: SQLiteDatabase,
        queueId: Long,
        checkpoint: PlaybackCheckpoint,
        profileId: Int,
        audiobook: Boolean,
    ) {
        val metadata =
            db
                .rawQuery(
                    "SELECT context_key, album_context FROM queue_resume_metadata WHERE queue_revision_id = ?",
                    arrayOf(queueId.toString()),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) return
                    cursor.getString(0) to cursor.getInt(1)
                }
        db.execSQL(
            "INSERT OR REPLACE INTO saved_playback(profile_id, context_key, queue_revision_id, album_context, occurrence_id, position_ms, play_when_ready, repeat_enabled, shuffle_enabled, chosen_binding_id, saved_cycle, retention_multiplier) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                profileId,
                metadata.first,
                queueId,
                metadata.second,
                checkpoint.occurrenceId,
                checkpoint.positionMs,
                checkpoint.playWhenReady.toSql(),
                checkpoint.repeat.toSql(),
                checkpoint.shuffle.toSql(),
                checkpoint.chosenBindingId,
                playbackCycle(db),
                if (audiobook) 3 else 1,
            ),
        )
        db.execSQL(
            "INSERT OR REPLACE INTO profile_last_playback(profile_id, context_key) VALUES(?, ?)",
            arrayOf<Any?>(profileId, metadata.first),
        )
    }

    fun createPlaylist(
        name: String,
        mediaIds: List<MediaId> = emptyList(),
        commandId: String? = null,
        profileId: Int = activeProfile(),
    ): PermanentPlaylist {
        val normalized = normalizedPlaylistName(name)
        require(mediaIds.isNotEmpty()) { "A Music playlist must contain a track" }
        require(commandId == null || commandId.isNotBlank())
        var playlistId = 0L
        mutate { db, revision ->
            require(profileId in VALID_PROFILES)
            val profile = profileId
            if (commandId != null) {
                val deliveredPlaylist =
                    db.longOrNull(
                        "SELECT playlist_id FROM playlist_commands WHERE profile_id = ? AND command_id = ?",
                        arrayOf(profile.toString(), commandId),
                    )
                if (deliveredPlaylist != null) {
                    playlistId = deliveredPlaylist
                    return@mutate false
                }
            }
            requirePlaylistNameAvailable(db, normalized, profileId)
            db.execSQL(
                "INSERT INTO playlists(profile_id, display_name, canonical_name) VALUES(?, ?, ?)",
                arrayOf<Any?>(profile, name.trim(), normalized),
            )
            playlistId = lastInsertId(db)
            val resultRevision = replacePlaylistRevision(db, playlistId, mediaIds, revision, profileId)
            if (commandId != null) {
                db.execSQL(
                    """
                    INSERT INTO playlist_commands(profile_id, command_id, playlist_id, resulting_revision_id)
                    VALUES(?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(profile, commandId, playlistId, resultRevision),
                )
            }
            true
        }
        return requireNotNull(loadPlaylistForProfile(playlistId, profileId))
    }

    fun renamePlaylist(
        playlistId: Long,
        name: String,
        profileId: Int = activeProfile(),
    ): Boolean {
        val normalized = normalizedPlaylistName(name)
        return mutate { db, revision ->
            require(profileId in VALID_PROFILES)
            val current = requireNotNull(loadPlaylistForProfile(db, playlistId, profileId)) { "Playlist does not exist" }
            if (current.name == name.trim()) return@mutate false
            if (current.kind == PlaylistKind.MUSIC) requirePlaylistNameAvailable(db, normalized, profileId, playlistId)
            val mediaIds = current.occurrences.map { it.mediaId }
            db.execSQL(
                "UPDATE playlists SET display_name = ?, canonical_name = ? WHERE playlist_id = ?",
                arrayOf<Any?>(name.trim(), normalized, playlistId),
            )
            replacePlaylistRevision(db, playlistId, mediaIds, revision, profileId)
            true
        }
    }

    fun deletePlaylist(
        playlistId: Long,
        profileId: Int = activeProfile(),
    ): Boolean =
        mutate(refreshBookmarks = true) { db, _ ->
            require(profileId in VALID_PROFILES)
            val count =
                db.delete(
                    "playlists",
                    "playlist_id = ? AND profile_id = ?",
                    arrayOf(playlistId.toString(), profileId.toString()),
                )
            count > 0
        }

    fun appendToPlaylist(
        playlistId: Long,
        mediaIds: List<MediaId>,
        commandId: String,
        profileId: Int = activeProfile(),
    ): PermanentPlaylist {
        require(mediaIds.isNotEmpty())
        require(commandId.isNotBlank())
        mutate { db, revision ->
            require(profileId in VALID_PROFILES)
            val profile = profileId
            val deliveredPlaylist =
                db.longOrNull(
                    "SELECT playlist_id FROM playlist_commands WHERE profile_id = ? AND command_id = ?",
                    arrayOf(profile.toString(), commandId),
                )
            if (deliveredPlaylist != null) {
                check(deliveredPlaylist == playlistId) { "Playlist command ID was reused for another playlist" }
                return@mutate false
            }
            check(loadPlaylistForProfile(playlistId, profileId)?.kind == PlaylistKind.MUSIC) { "Cannot append to Bookmarks" }
            val existing = readPlaylistMediaIds(db, playlistId, profileId)
            val resultRevision =
                replacePlaylistRevision(db, playlistId, existing + mediaIds, revision, profileId)
            db.execSQL(
                """
                INSERT INTO playlist_commands(profile_id, command_id, playlist_id, resulting_revision_id)
                VALUES(?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(profile, commandId, playlistId, resultRevision),
            )
            true
        }
        return requireNotNull(loadPlaylistForProfile(playlistId, profileId))
    }

    fun removePlaylistOccurrence(
        playlistId: Long,
        occurrenceId: Long,
        profileId: Int = activeProfile(),
    ): PermanentPlaylist {
        var removedSnapshot: PermanentPlaylist? = null
        mutate { db, revision ->
            require(profileId in VALID_PROFILES)
            val current = requireNotNull(loadPlaylistForProfile(playlistId, profileId)) { "Playlist does not exist" }
            removedSnapshot = current.copy(occurrences = emptyList())
            check(current.occurrences.any { it.occurrenceId == occurrenceId }) {
                "Playlist occurrence does not exist"
            }
            check(current.kind == PlaylistKind.MUSIC) { "Cannot remove a frozen Bookmarks queue item" }
            val remaining = current.occurrences.filterNot { it.occurrenceId == occurrenceId }.map { it.mediaId }
            if (remaining.isEmpty()) {
                db.delete("playlists", "playlist_id = ?", arrayOf(playlistId.toString()))
            } else {
                replacePlaylistRevision(db, playlistId, remaining, revision, profileId)
            }
            true
        }
        return loadPlaylistForProfile(playlistId, profileId) ?: requireNotNull(removedSnapshot)
    }

    fun markBook(
        titleKey: String,
        title: String,
        queueIds: List<MediaId>,
        ordinal: Int,
        positionUs: Long,
        sectionLabel: String,
        durationUs: Long?,
        profileId: Int = activeProfile(),
    ): PermanentPlaylist {
        require(titleKey.startsWith("album:") && queueIds.isNotEmpty())
        require(ordinal in queueIds.indices && positionUs >= 0L)
        require(durationUs == null || durationUs > 0L)
        var playlistId = 0L
        mutate(refreshBookmarks = true) { db, revision ->
            playlistId = db.longOrNull(
                "SELECT playlist_id FROM playlists WHERE profile_id = ? AND kind = 'BOOK' AND title_key = ?",
                arrayOf(profileId.toString(), titleKey),
            ) ?: run {
                val name = bookCollectionName(title)
                db.execSQL(
                    "INSERT INTO playlists(profile_id, display_name, canonical_name, kind, title_key) VALUES(?, ?, ?, 'BOOK', ?)",
                    arrayOf<Any?>(profileId, name, normalizedPlaylistName(name), titleKey),
                )
                val id = lastInsertId(db)
                replacePlaylistRevision(db, id, queueIds, revision, profileId)
                id
            }
            val frozen = readPlaylistMediaIds(db, playlistId, profileId)
            check(ordinal < frozen.size && frozen[ordinal] == queueIds[ordinal]) {
                "This Title occurrence is outside its frozen Bookmarks queue"
            }
            if (durationUs != null) writeBookDuration(db, playlistId, ordinal, durationUs)
            val book = readBookMarkState(db, playlistId, profileId)
            val admission =
                BookMarkSpacing.evaluate(
                    book.marks,
                    book.durationsUs,
                    BookMarkPosition(ordinal, positionUs),
                    durationUs,
                    bookmarkMinimumUs,
                )
            check(admission == BookMarkAdmission.ALLOWED) {
                when (admission) {
                    BookMarkAdmission.TOO_CLOSE -> "A bookmark is already within the configured interval"
                    else -> "Book duration is unknown across this chapter boundary"
                }
            }
            db.execSQL(
                "INSERT INTO bookmark_marks(playlist_id, ordinal, position_us, label) VALUES(?, ?, ?, ?)",
                arrayOf<Any?>(playlistId, ordinal, positionUs, sectionLabel),
            )
            true
        }
        return requireNotNull(loadPlaylistForProfile(requireDatabase(), playlistId, profileId))
    }

    fun recordBookDuration(
        titleKey: String,
        ordinal: Int,
        mediaId: MediaId,
        durationUs: Long,
        profileId: Int = activeProfile(),
    ) {
        if (durationUs <= 0L) return
        mutate(refreshBookmarks = true) { db, _ ->
            val id =
                db.longOrNull(
                    "SELECT playlist_id FROM playlists WHERE kind = 'BOOK' AND title_key = ? AND profile_id = ?",
                    arrayOf(titleKey, profileId.toString()),
                ) ?: return@mutate false
            val frozen = readPlaylistMediaIds(db, id, profileId)
            if (frozen.getOrNull(ordinal) != mediaId) return@mutate false
            writeBookDuration(db, id, ordinal, durationUs)
            true
        }
    }

    fun removeBookmark(
        playlistId: Long,
        markId: Long,
        profileId: Int = activeProfile(),
    ): Boolean =
        mutate(refreshBookmarks = true) { db, _ ->
            val playlist = requireNotNull(loadPlaylistForProfile(playlistId, profileId))
            check(playlist.kind == PlaylistKind.BOOK)
            val removed =
                db.delete(
                    "bookmark_marks",
                    "playlist_id = ? AND mark_id = ?",
                    arrayOf(playlistId.toString(), markId.toString()),
                ) > 0
            if (removed &&
                db.longOrNull("SELECT COUNT(*) FROM bookmark_marks WHERE playlist_id = ?", arrayOf(playlistId.toString())) == 0L
            ) {
                db.delete("playlists", "playlist_id = ?", arrayOf(playlistId.toString()))
            }
            removed
        }

    fun pruneExpiredCollections(retainedMediaIds: Set<MediaId>): Boolean =
        mutate(refreshBookmarks = true) { db, revision ->
            var changed = false
            val entries =
                db.rawQuery("SELECT playlist_id, profile_id FROM playlists", null).use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getInt(1)) }
                }
            for ((id, profile) in entries) {
                val playlist = loadPlaylistForProfile(db, id, profile) ?: continue
                val survivors = playlist.occurrences.map(PlaylistOccurrence::mediaId).filter(retainedMediaIds::contains)
                if (survivors.size == playlist.occurrences.size) continue
                if (playlist.kind == PlaylistKind.BOOK || survivors.isEmpty()) {
                    db.delete("playlists", "playlist_id = ?", arrayOf(id.toString()))
                } else {
                    replacePlaylistRevision(db, id, survivors, revision, profile)
                }
                changed = true
            }
            changed
        }

    private fun writeBookDuration(
        db: SQLiteDatabase,
        playlistId: Long,
        ordinal: Int,
        durationUs: Long,
    ) {
        db.execSQL(
            "INSERT OR REPLACE INTO bookmark_durations(playlist_id, ordinal, duration_us) VALUES(?, ?, ?)",
            arrayOf<Any?>(playlistId, ordinal, durationUs),
        )
    }

    /** Read-only admission for the active queue's profile and exact frozen occurrence. */
    fun canMarkBook(
        queue: PlaybackQueueSnapshot,
        activeOrdinal: Int,
        mediaId: MediaId,
        positionUs: Long,
        durationUs: Long?,
    ): Boolean {
        if (database?.isOpen != true || !queue.bookTitleContext) return false
        val ordinal = queue.bookQueueOrdinals.getOrNull(activeOrdinal) ?: return false
        if (queue.bookTitleMembers.getOrNull(ordinal) != mediaId) return false
        val contextKey = queue.contextKey ?: return false
        val states = bookMarkStates
        val book =
            if (contextKey.startsWith("album:")) {
                states[queue.sourceProfileId to contextKey]
            } else if (contextKey.startsWith("bookmarks:")) {
                val id = contextKey.removePrefix("bookmarks:").toLongOrNull() ?: return false
                states.entries.firstOrNull { it.key.first == queue.sourceProfileId && it.value.playlistId == id }?.value ?: return false
            } else {
                return false
            }
        if (book != null && book.mediaIds.getOrNull(ordinal) != mediaId) return false
        return BookMarkSpacing.evaluate(
            book?.marks.orEmpty(),
            book?.durationsUs.orEmpty(),
            BookMarkPosition(ordinal, positionUs),
            durationUs,
            bookmarkMinimumUs,
        ) == BookMarkAdmission.ALLOWED
    }

    private fun readBookMarkState(
        db: SQLiteDatabase,
        playlistId: Long,
        profileId: Int,
    ): BookMarkState {
        val marks =
            db
                .rawQuery(
                    "SELECT ordinal, position_us FROM bookmark_marks WHERE playlist_id = ?",
                    arrayOf(playlistId.toString()),
                ).use { cursor ->
                    buildList { while (cursor.moveToNext()) add(BookMarkPosition(cursor.getInt(0), cursor.getLong(1))) }
                }
        val durations =
            db
                .rawQuery(
                    "SELECT ordinal, duration_us FROM bookmark_durations WHERE playlist_id = ?",
                    arrayOf(playlistId.toString()),
                ).use { cursor ->
                    buildMap { while (cursor.moveToNext()) put(cursor.getInt(0), cursor.getLong(1)) }
                }
        return BookMarkState(playlistId, readPlaylistMediaIds(db, playlistId, profileId), marks, durations)
    }

    private fun readBookMarkStates(db: SQLiteDatabase): Map<Pair<Int, String>, BookMarkState> =
        db.rawQuery("SELECT playlist_id, profile_id, title_key FROM playlists WHERE kind = 'BOOK'", null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val profileId = cursor.getInt(1)
                    put(profileId to cursor.getString(2), readBookMarkState(db, cursor.getLong(0), profileId))
                }
            }
        }

    fun listPlaylists(profileId: Int = activeProfile()): List<PermanentPlaylist> =
        requireDatabase()
            .rawQuery(
                "SELECT playlist_id FROM playlists WHERE profile_id = ? ORDER BY canonical_name, playlist_id",
                arrayOf(profileId.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) loadPlaylistForProfile(cursor.getLong(0), profileId)?.let(::add)
                }
            }

    /** Allocates a collision-free Player play-instance ID from the semantic revision. */
    fun recordPlaybackStart(
        mediaId: MediaId,
        positionMs: Long,
        durationMs: Long?,
        audiobook: Boolean = false,
        profileId: Int = activeProfile(),
    ): Long {
        require(profileId in 1..3)
        require(positionMs >= 0L)
        require(durationMs == null || durationMs >= 0L)
        var playInstanceId = -1L
        var activatedGeneration: Long? = null
        mutate { db, revision ->
            val profile = profileId
            val generation =
                activeHistoryGeneration
                    ?: checkedNextHistoryGeneration(db).also {
                        db.execSQL(
                            "UPDATE playback_history_generation SET current_generation = ? WHERE id = 1",
                            arrayOf(it),
                        )
                        pruneListeningHistory(db, it)
                        activatedGeneration = it
                    }
            playInstanceId = revision
            db.execSQL(
                """
                INSERT INTO recent_listening_events(
                    profile_id, media_id_high, media_id_low, media_id_low_present, play_instance_id, history_generation,
                    event_type, position_ms, duration_ms, created_state_revision
                ) VALUES(?, ?, ?, ?, ?, ?, 'PLAY_STARTED', ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    profile,
                    mediaId.high,
                    mediaId.low,
                    mediaId.lowPresent.toSql(),
                    playInstanceId,
                    generation,
                    positionMs,
                    durationMs,
                    revision,
                ),
            )
            db.execSQL(
                "INSERT INTO listening_event_retention(event_id, multiplier) VALUES(?, ?)",
                arrayOf(lastInsertId(db), if (audiobook) 3 else 1),
            )
            val current =
                db.longOrNull(
                    "SELECT total_started_count FROM media_playback_totals WHERE profile_id = ? AND media_id_high = ? AND media_id_low = ? AND media_id_low_present = ?",
                    arrayOf(profile.toString(), mediaId.high.toString(), mediaId.low.toString(), mediaId.lowPresent.toSql().toString()),
                )
            check(current == null || current < Long.MAX_VALUE) { "total_started_count exhausted" }
            if (current == null) {
                db.execSQL(
                    """
                    INSERT INTO media_playback_totals(
                        profile_id, media_id_high, media_id_low, media_id_low_present, total_started_count,
                        last_start_generation, updated_state_revision
                    ) VALUES(?, ?, ?, ?, 1, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(profile, mediaId.high, mediaId.low, mediaId.lowPresent.toSql(), generation, revision),
                )
            } else {
                db.execSQL(
                    """
                    UPDATE media_playback_totals
                    SET total_started_count = ?, last_start_generation = ?,
                        updated_state_revision = ?
                    WHERE profile_id = ? AND media_id_high = ? AND media_id_low = ? AND media_id_low_present = ?
                    """.trimIndent(),
                    arrayOf<Any?>(current + 1L, generation, revision, profile, mediaId.high, mediaId.low, mediaId.lowPresent.toSql()),
                )
            }
            true
        }
        activatedGeneration?.let { activeHistoryGeneration = it }
        check(playInstanceId >= 0L)
        return playInstanceId
    }

    /** Closes attempts that could not emit an end event before the prior process died. */
    fun recordDanglingPlaybackAttemptsAsCensored(
        currentMediaId: MediaId?,
        positionMs: Long,
        durationMs: Long?,
    ): Int {
        require(positionMs >= 0L)
        require(durationMs == null || durationMs >= 0L)
        val dangling =
            requireDatabase()
                .rawQuery(
                    """
                    SELECT started.media_id_high, started.media_id_low, started.media_id_low_present, started.play_instance_id
                    FROM recent_listening_events AS started
                    WHERE started.event_type = 'PLAY_STARTED'
                      AND NOT EXISTS (
                        SELECT 1 FROM recent_listening_events AS ended
                        WHERE ended.profile_id = started.profile_id
                          AND ended.play_instance_id = started.play_instance_id
                          AND ended.event_type != 'PLAY_STARTED'
                      )
                    ORDER BY started.event_id
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(MediaId(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2) != 0) to cursor.getLong(3))
                        }
                    }
                }
        dangling.forEach { (mediaId, playInstanceId) ->
            recordListeningEvent(
                mediaId = mediaId,
                playInstanceId = playInstanceId,
                eventType = ListeningEventType.CENSORED_END,
                positionMs = if (mediaId == currentMediaId) positionMs else 0L,
                durationMs = if (mediaId == currentMediaId) durationMs else null,
            )
        }
        return dangling.size
    }

    fun recordListeningEvent(
        mediaId: MediaId,
        playInstanceId: Long,
        eventType: ListeningEventType,
        positionMs: Long,
        durationMs: Long?,
    ): Long {
        require(playInstanceId >= 0L)
        require(positionMs >= 0L)
        require(durationMs == null || durationMs >= 0L)
        var eventId = 0L
        var activatedGeneration: Long? = null
        mutate { db, revision ->
            val profile =
                db
                    .longOrNull(
                        "SELECT profile_id FROM recent_listening_events WHERE play_instance_id = ? AND event_type = 'PLAY_STARTED'",
                        arrayOf(playInstanceId.toString()),
                    )?.toInt() ?: activeProfileId(db)
            val existing =
                db.longOrNull(
                    """
                    SELECT event_id FROM recent_listening_events
                    WHERE profile_id = ? AND play_instance_id = ? AND event_type = ?
                    """.trimIndent(),
                    arrayOf(profile.toString(), playInstanceId.toString(), eventType.name),
                )
            if (existing != null) {
                eventId = existing
                return@mutate false
            }
            val generation =
                activeHistoryGeneration
                    ?: checkedNextHistoryGeneration(db).also {
                        db.execSQL(
                            "UPDATE playback_history_generation SET current_generation = ? WHERE id = 1",
                            arrayOf(it),
                        )
                        pruneListeningHistory(db, it)
                        activatedGeneration = it
                    }
            db.execSQL(
                """
                INSERT INTO recent_listening_events(
                    profile_id, media_id_high, media_id_low, media_id_low_present, play_instance_id, history_generation,
                    event_type, position_ms, duration_ms, created_state_revision
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    profile,
                    mediaId.high,
                    mediaId.low,
                    mediaId.lowPresent.toSql(),
                    playInstanceId,
                    generation,
                    eventType.name,
                    positionMs,
                    durationMs,
                    revision,
                ),
            )
            eventId = lastInsertId(db)
            val multiplier =
                db.longOrNull(
                    "SELECT r.multiplier FROM listening_event_retention r JOIN recent_listening_events e ON e.event_id = r.event_id WHERE e.profile_id = ? AND e.play_instance_id = ? LIMIT 1",
                    arrayOf(profile.toString(), playInstanceId.toString()),
                )
                    ?: 1L
            db.execSQL("INSERT INTO listening_event_retention(event_id, multiplier) VALUES(?, ?)", arrayOf(eventId, multiplier))
            if (eventType == ListeningEventType.PLAY_STARTED) {
                val current =
                    db.longOrNull(
                        "SELECT total_started_count FROM media_playback_totals WHERE profile_id = ? AND media_id_high = ? AND media_id_low = ? AND media_id_low_present = ?",
                        arrayOf(profile.toString(), mediaId.high.toString(), mediaId.low.toString(), mediaId.lowPresent.toSql().toString()),
                    )
                check(current == null || current < Long.MAX_VALUE) { "total_started_count exhausted" }
                if (current == null) {
                    db.execSQL(
                        """
                        INSERT INTO media_playback_totals(
                            profile_id, media_id_high, media_id_low, media_id_low_present, total_started_count,
                            last_start_generation, updated_state_revision
                        ) VALUES(?, ?, ?, ?, 1, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any?>(profile, mediaId.high, mediaId.low, mediaId.lowPresent.toSql(), generation, revision),
                    )
                } else {
                    db.execSQL(
                        """
                        UPDATE media_playback_totals
                        SET total_started_count = ?, last_start_generation = ?,
                            updated_state_revision = ?
                        WHERE profile_id = ? AND media_id_high = ? AND media_id_low = ? AND media_id_low_present = ?
                        """.trimIndent(),
                        arrayOf<Any?>(current + 1L, generation, revision, profile, mediaId.high, mediaId.low, mediaId.lowPresent.toSql()),
                    )
                }
            }
            true
        }
        activatedGeneration?.let { activeHistoryGeneration = it }
        return eventId
    }

    private fun pruneListeningHistory(
        db: SQLiteDatabase,
        generation: Long,
    ) {
        db.execSQL(
            "DELETE FROM recent_listening_events WHERE ? - history_generation >= ? * COALESCE((SELECT multiplier FROM listening_event_retention r WHERE r.event_id = recent_listening_events.event_id), 1)",
            arrayOf(generation, recentHistoryGenerations),
        )
    }

    fun listeningEvents(): List<ListeningEvent> =
        requireDatabase()
            .rawQuery(
                """
                SELECT event_id, profile_id, media_id_high, media_id_low, media_id_low_present, play_instance_id,
                       history_generation, event_type, position_ms, duration_ms,
                       created_state_revision
                FROM recent_listening_events WHERE profile_id = ? ORDER BY event_id
                """.trimIndent(),
                arrayOf(activeProfile().toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            ListeningEvent(
                                eventId = cursor.getLong(0),
                                profileId = cursor.getInt(1),
                                mediaId = MediaId(cursor.getLong(2), cursor.getLong(3), cursor.getInt(4) != 0),
                                playInstanceId = cursor.getLong(5),
                                historyGeneration = cursor.getLong(6),
                                eventType = ListeningEventType.valueOf(cursor.getString(7)),
                                positionMs = cursor.getLong(8),
                                durationMs = cursor.takeLong(9),
                                stateRevision = cursor.getLong(10),
                            ),
                        )
                    }
                }
            }

    fun loadAiPlaybackHistory(profileId: Int = activeProfile()): AiPlaybackHistorySnapshot =
        requireDatabase()
            .rawQuery(
                """
                SELECT generation.current_generation, events.media_id_high, events.media_id_low, events.media_id_low_present,
                       events.history_generation, COUNT(events.event_id)
                FROM playback_history_generation AS generation
                LEFT JOIN recent_listening_events AS events
                  ON events.profile_id = ?
                 AND events.event_type = 'PLAY_STARTED'
                WHERE generation.id = 1
                GROUP BY generation.current_generation, events.media_id_high, events.media_id_low, events.media_id_low_present, events.history_generation
                ORDER BY events.media_id_high, events.media_id_low, events.media_id_low_present, events.history_generation
                """.trimIndent(),
                arrayOf(profileId.toString()),
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Player history generation is missing" }
                val currentGeneration = cursor.getLong(0)
                val starts =
                    buildList {
                        do {
                            if (!cursor.isNull(1)) {
                                add(
                                    PlaybackStartCount(
                                        mediaId = MediaId(cursor.getLong(1), cursor.getLong(2), cursor.getInt(3) != 0),
                                        historyGeneration = cursor.getLong(4),
                                        starts = cursor.getLong(5),
                                    ),
                                )
                            }
                        } while (cursor.moveToNext())
                    }
                AiPlaybackHistorySnapshot(currentGeneration, starts)
            }

    fun mediaPlaybackTotal(mediaId: MediaId): MediaPlaybackTotal? =
        requireDatabase()
            .rawQuery(
                """
                SELECT profile_id, media_id_high, media_id_low, media_id_low_present, total_started_count,
                       last_start_generation, updated_state_revision
                FROM media_playback_totals
                WHERE profile_id = ? AND media_id_high = ? AND media_id_low = ? AND media_id_low_present = ?
                """.trimIndent(),
                arrayOf(activeProfile().toString(), mediaId.high.toString(), mediaId.low.toString(), mediaId.lowPresent.toSql().toString()),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                MediaPlaybackTotal(
                    profileId = cursor.getInt(0),
                    mediaId = MediaId(cursor.getLong(1), cursor.getLong(2), cursor.getInt(3) != 0),
                    totalStartedCount = cursor.getLong(4),
                    lastStartGeneration = cursor.getLong(5),
                    stateRevision = cursor.getLong(6),
                )
            }

    fun stateRevision(): Long =
        requireDatabase().longOrNull("SELECT state_revision FROM state_metadata WHERE id = 1")
            ?: error("Player state revision is missing")

    fun integrityCheck(): Boolean = requireDatabase().hasFullIntegrity()

    /** Returns true only when the WAL was completely checkpointed and truncated. */
    fun checkpointTruncate(): Boolean {
        repeat(2) {
            val complete =
                requireDatabase().rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getInt(0) == 0 && cursor.getLong(1) == cursor.getLong(2)
                }
            if (complete) return true
        }
        return false
    }

    override fun close() {
        database?.close()
        database = null
    }

    private fun loadQueue(queueRevisionId: Long): PlaybackQueueSnapshot? {
        val db = requireDatabase()
        val header =
            db
                .rawQuery(
                    """
                    SELECT kind, profile_id, temporary FROM playback_queue_revisions
                    WHERE queue_revision_id = ? AND state = 'READY'
                    """.trimIndent(),
                    arrayOf(queueRevisionId.toString()),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    Triple(cursor.getString(0), cursor.getInt(1), cursor.getInt(2) != 0)
                }
        val occurrences =
            db
                .rawQuery(
                    """
                    SELECT occurrence_id, ordinal, shuffle_ordinal, media_id_high, media_id_low, media_id_low_present
                    FROM playback_queue_occurrences
                    WHERE queue_revision_id = ? ORDER BY ordinal
                    """.trimIndent(),
                    arrayOf(queueRevisionId.toString()),
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                PlaybackQueueOccurrence(
                                    cursor.getLong(0),
                                    cursor.getInt(1),
                                    cursor.getInt(2),
                                    MediaId(cursor.getLong(3), cursor.getLong(4), cursor.getInt(5) != 0),
                                ),
                            )
                        }
                    }
                }
        val bookMembers =
            db
                .rawQuery(
                    "SELECT queue_ordinal, media_id_high, media_id_low, media_id_low_present FROM queue_book_members WHERE queue_revision_id = ? ORDER BY source_ordinal",
                    arrayOf(queueRevisionId.toString()),
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                (if (cursor.isNull(0)) null else cursor.getInt(0)) to
                                    MediaId(cursor.getLong(1), cursor.getLong(2), cursor.getInt(3) != 0),
                            )
                        }
                    }
                }
        val queueOrdinals =
            bookMembers
                .mapIndexedNotNull {
                    sourceOrdinal,
                    member,
                    ->
                    member.first?.let { it to sourceOrdinal }
                }.sortedBy(Pair<Int, Int>::first)
                .map(Pair<Int, Int>::second)
        return PlaybackQueueSnapshot(
            revisionId = queueRevisionId,
            kind = enumOr(header.first, PlaybackQueueKind.MANUAL),
            sourceProfileId = header.second,
            temporary = header.third,
            occurrences = occurrences,
            checkpoint = readCheckpoint(db, queueRevisionId),
            contextKey =
                db.stringOrNull(
                    "SELECT context_key FROM queue_resume_metadata WHERE queue_revision_id = ?",
                    arrayOf(queueRevisionId.toString()),
                ),
            albumContext =
                db.longOrNull(
                    "SELECT album_context FROM queue_resume_metadata WHERE queue_revision_id = ?",
                    arrayOf(queueRevisionId.toString()),
                ) ==
                    1L,
            bookTitleContext =
                db.longOrNull(
                    "SELECT book_title_context FROM queue_resume_metadata WHERE queue_revision_id = ?",
                    arrayOf(queueRevisionId.toString()),
                ) == 1L,
            bookTitleName =
                db.stringOrNull(
                    "SELECT book_title_name FROM queue_resume_metadata WHERE queue_revision_id = ?",
                    arrayOf(queueRevisionId.toString()),
                ),
            bookTitleMembers = bookMembers.map(Pair<Int?, MediaId>::second),
            bookQueueOrdinals = queueOrdinals,
        )
    }

    private fun readCheckpoint(
        db: SQLiteDatabase,
        queueRevisionId: Long,
    ): PlaybackCheckpoint =
        db
            .rawQuery(
                """
                SELECT occurrence_id, position_ms, play_when_ready, repeat_enabled,
                       shuffle_enabled, chosen_binding_id
                FROM playback_checkpoint WHERE id = 1 AND queue_revision_id = ?
                """.trimIndent(),
                arrayOf(queueRevisionId.toString()),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use PlaybackCheckpoint(null)
                PlaybackCheckpoint(
                    occurrenceId = cursor.takeLong(0),
                    positionMs = cursor.getLong(1),
                    playWhenReady = cursor.getInt(2) != 0,
                    repeat = cursor.getInt(3) != 0,
                    shuffle = cursor.getInt(4) != 0,
                    chosenBindingId = cursor.takeLong(5),
                )
            }

    private fun requirePlaylistNameAvailable(
        db: SQLiteDatabase,
        canonicalName: String,
        profileId: Int,
        excludingPlaylistId: Long = -1,
    ) {
        require(
            db.longOrNull(
                "SELECT playlist_id FROM playlists WHERE profile_id = ? AND kind = 'MUSIC' AND canonical_name = ? AND playlist_id != ?",
                arrayOf(profileId.toString(), canonicalName, excludingPlaylistId.toString()),
            ) == null,
        ) { "A playlist with this name already exists" }
    }

    private fun loadPlaylist(playlistId: Long): PermanentPlaylist? {
        val db = requireDatabase()
        return loadPlaylistForProfile(db, playlistId, activeProfileId(db))
    }

    fun loadPlaylistForProfile(
        playlistId: Long,
        profileId: Int,
    ): PermanentPlaylist? {
        require(profileId in VALID_PROFILES)
        return loadPlaylistForProfile(requireDatabase(), playlistId, profileId)
    }

    private fun loadPlaylistForProfile(
        db: SQLiteDatabase,
        playlistId: Long,
        profile: Int,
    ): PermanentPlaylist? {
        return db
            .rawQuery(
                """
                SELECT display_name, current_revision_id, kind, title_key FROM playlists
                WHERE playlist_id = ? AND profile_id = ?
                """.trimIndent(),
                arrayOf(playlistId.toString(), profile.toString()),
            ).use { header ->
                if (!header.moveToFirst()) return@use null
                val name = header.getString(0)
                val revisionId = header.getLong(1)
                val kind = PlaylistKind.valueOf(header.getString(2))
                val titleKey = header.getString(3)
                val occurrences =
                    db
                        .rawQuery(
                            """
                            SELECT occurrence_id, ordinal, media_id_high, media_id_low, media_id_low_present FROM playlist_occurrences
                            WHERE revision_id = ? ORDER BY ordinal
                            """.trimIndent(),
                            arrayOf(revisionId.toString()),
                        ).use { rows ->
                            buildList {
                                while (rows.moveToNext()) {
                                    add(
                                        PlaylistOccurrence(
                                            rows.getLong(0),
                                            rows.getInt(1),
                                            MediaId(rows.getLong(2), rows.getLong(3), rows.getInt(4) != 0),
                                        ),
                                    )
                                }
                            }
                        }
                val marks =
                    if (kind == PlaylistKind.BOOK) {
                        db
                            .rawQuery(
                                "SELECT mark_id, ordinal, position_us, label FROM bookmark_marks WHERE playlist_id = ? ORDER BY ordinal, position_us, mark_id",
                                arrayOf(playlistId.toString()),
                            ).use { cursor ->
                                buildList {
                                    while (cursor.moveToNext()) {
                                        add(
                                            BookmarkMark(cursor.getLong(0), cursor.getInt(1), cursor.getLong(2), cursor.getString(3)),
                                        )
                                    }
                                }
                            }
                    } else {
                        emptyList()
                    }
                PermanentPlaylist(playlistId, name, revisionId, occurrences, kind, titleKey, marks)
            }
    }

    private fun readPlaylistMediaIds(
        db: SQLiteDatabase,
        playlistId: Long,
        profileId: Int = activeProfileId(db),
    ): List<MediaId> {
        check(
            db
                .rawQuery(
                    "SELECT 1 FROM playlists WHERE playlist_id = ? AND profile_id = ?",
                    arrayOf(playlistId.toString(), profileId.toString()),
                ).use(Cursor::moveToFirst),
        ) { "Playlist does not exist" }
        return db
            .rawQuery(
                """
                SELECT o.media_id_high, o.media_id_low, o.media_id_low_present FROM playlist_occurrences o
                JOIN playlists p ON p.current_revision_id = o.revision_id
                WHERE p.playlist_id = ? ORDER BY o.ordinal
                """.trimIndent(),
                arrayOf(playlistId.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(MediaId(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2) != 0))
                }
            }
    }

    private fun replacePlaylistRevision(
        db: SQLiteDatabase,
        playlistId: Long,
        mediaIds: List<MediaId>,
        revision: Long,
        profileId: Int = activeProfileId(db),
    ): Long {
        check(
            db
                .rawQuery(
                    "SELECT 1 FROM playlists WHERE playlist_id = ? AND profile_id = ?",
                    arrayOf(playlistId.toString(), profileId.toString()),
                ).use(Cursor::moveToFirst),
        ) { "Playlist does not exist" }
        db.execSQL(
            "INSERT INTO playlist_revisions(playlist_id, created_state_revision) VALUES(?, ?)",
            arrayOf(playlistId, revision),
        )
        val revisionId = lastInsertId(db)
        mediaIds.forEachIndexed { ordinal, mediaId ->
            db.execSQL(
                "INSERT INTO playlist_occurrences(revision_id, ordinal, media_id_high, media_id_low, media_id_low_present) VALUES(?, ?, ?, ?, ?)",
                arrayOf<Any?>(revisionId, ordinal, mediaId.high, mediaId.low, mediaId.lowPresent.toSql()),
            )
        }
        db.execSQL(
            "UPDATE playlists SET current_revision_id = ? WHERE playlist_id = ?",
            arrayOf(revisionId, playlistId),
        )
        return revisionId
    }

    private fun readReaction(
        db: SQLiteDatabase,
        profile: Int,
        key: String,
    ): StoredReaction =
        db
            .rawQuery(
                "SELECT preference, own_banned FROM reactions WHERE profile_id = ? AND object_key = ?",
                arrayOf(profile.toString(), key),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use StoredReaction()
                StoredReaction(enumOr(cursor.getString(0), StoredPreference.NEUTRAL), cursor.getInt(1) != 0)
            }

    @Synchronized
    private fun mutate(
        refreshBookmarks: Boolean = false,
        block: (SQLiteDatabase, Long) -> Boolean,
    ): Boolean {
        val db = requireDatabase()
        var nextBookStates: Map<Pair<Int, String>, BookMarkState>? = null
        val changed: Boolean
        db.beginTransactionNonExclusive()
        try {
            val revision = checkedNextRevision(db)
            changed = block(db, revision)
            if (changed) {
                db.execSQL("UPDATE state_metadata SET state_revision = ? WHERE id = 1", arrayOf(revision))
                if (refreshBookmarks) nextBookStates = readBookMarkStates(db)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // Publish only after commit, in writer order. Failed writes leave the UI snapshot intact.
        nextBookStates?.let { bookMarkStates = it }
        return changed
    }

    private fun checkedNextRevision(db: SQLiteDatabase): Long {
        val current =
            db.longOrNull("SELECT state_revision FROM state_metadata WHERE id = 1")
                ?: error("Player state revision is missing")
        check(current >= 0L && current < Long.MAX_VALUE) { "Player state revision exhausted" }
        return current + 1L
    }

    private fun checkedNextHistoryGeneration(db: SQLiteDatabase): Long {
        val current =
            db.longOrNull("SELECT current_generation FROM playback_history_generation WHERE id = 1")
                ?: error("Player history generation is missing")
        check(current >= 0L && current < Long.MAX_VALUE) { "Player history generation exhausted" }
        return current + 1L
    }

    private fun activeProfileId(db: SQLiteDatabase): Int =
        db.longOrNull("SELECT profile_id FROM active_profile WHERE id = 1")?.toInt()
            ?: error("Active Player profile is missing")

    private fun queueOccurrenceIdAtOrdinal(
        db: SQLiteDatabase,
        revisionId: Long,
        ordinal: Int,
    ): Long =
        db.longOrNull(
            "SELECT occurrence_id FROM playback_queue_occurrences WHERE queue_revision_id = ? AND ordinal = ?",
            arrayOf(revisionId.toString(), ordinal.toString()),
        ) ?: error("Materialized queue occurrence is missing")

    private fun writeSettings(
        db: SQLiteDatabase,
        settings: PlayerSettings,
        profileId: Int,
    ) {
        db.execSQL(
            """
            UPDATE installation_settings
            SET diagonal = ?, calibration_x = ?, calibration_y = ?,
                data_location = ?
            WHERE id = 1
            """.trimIndent(),
            arrayOf<Any?>(
                settings.diagonal.name,
                settings.calibrationPpiX,
                settings.calibrationPpiY,
                settings.dataLocation.name,
            ),
        )
        db.execSQL(
            """
            UPDATE profile_settings
            SET color_scheme = ?, long_press = ?, auto_search = ?, ignore_leading_articles = ?
            WHERE profile_id = ?
            """.trimIndent(),
            arrayOf<Any?>(
                settings.colorScheme.name,
                settings.longPressStartsPlayback.toSql(),
                settings.autoSearch.toSql(),
                settings.ignoreLeadingArticles.toSql(),
                profileId,
            ),
        )
    }

    private fun initializeSchema(db: SQLiteDatabase) {
        val version = db.version
        require(version == 0 || version == SCHEMA_VERSION) { "Unsupported Player state schema version $version" }
        if (version == SCHEMA_VERSION) return
        db.beginTransaction()
        try {
            createSchema(db)
            db.version = SCHEMA_VERSION
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        SCHEMA_SQL.forEach(db::execSQL)
        db.execSQL("INSERT INTO state_metadata(id, state_revision) VALUES(1, 0)")
        VALID_PROFILES.forEach { profile ->
            db.execSQL("INSERT INTO profiles(profile_id) VALUES(?)", arrayOf(profile))
            db.execSQL(
                """
                INSERT INTO profile_settings(profile_id, color_scheme, long_press, auto_search, content_mode)
                VALUES(?, 'SYSTEM', 0, 1, 'MUSIC')
                """.trimIndent(),
                arrayOf(profile),
            )
        }
        db.execSQL("INSERT INTO active_profile(id, profile_id) VALUES(1, 1)")
        db.execSQL(
            """
            INSERT INTO installation_settings(
                id, diagonal, data_location, calibration_x, calibration_y
            ) VALUES(1, 'AUTO', 'HU', NULL, NULL)
            """.trimIndent(),
        )
        db.execSQL("INSERT INTO playback_history_generation(id, current_generation) VALUES(1, 0)")
        db.execSQL("INSERT INTO playback_cycle(id, cycle) VALUES(1, 0)")
    }

    private fun requireDatabase(): SQLiteDatabase = database?.takeIf(SQLiteDatabase::isOpen) ?: error("Player state database is closed")

    private fun bookCollectionName(title: String): String {
        val prefix = "Bookmarks for "
        val source = title.trim().ifBlank { "Book" }
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT)
        boundaries.setText(source)
        var end = 0
        repeat(MAX_STRING_LENGTH - graphemeCount(prefix)) {
            val next = boundaries.next()
            if (next == BreakIterator.DONE) return "$prefix$source"
            end = next
        }
        return prefix + source.substring(0, end).trimEnd()
    }

    private fun normalizedPlaylistName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name must not be blank" }
        require(graphemeCount(trimmed) <= MAX_STRING_LENGTH) {
            "Playlist name exceeds $MAX_STRING_LENGTH grapheme clusters"
        }
        val normalized = Normalizer2.getNFKCInstance().normalize(trimmed)
        return UCharacter.foldCase(normalized, true)
    }

    private fun graphemeCount(value: String): Int {
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT)
        boundaries.setText(value)
        var count = 0
        boundaries.first()
        while (boundaries.next() != BreakIterator.DONE) count += 1
        return count
    }

    private fun lastInsertId(db: SQLiteDatabase): Long =
        db.longOrNull("SELECT last_insert_rowid()") ?: error("SQLite did not return a row id")

    private fun Boolean.toSql(): Int = if (this) 1 else 0

    private fun Cursor.takeFloat(index: Int): Float? = if (isNull(index)) null else getFloat(index)

    private fun Cursor.takeLong(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private inline fun <reified T : Enum<T>> enumOr(
        value: String?,
        fallback: T,
    ): T = enumValues<T>().singleOrNull { it.name == value } ?: fallback

    companion object {
        const val FILE_NAME = "player-state.sqlite"
        const val SCHEMA_VERSION = 15
        const val DEFAULT_RECENT_HISTORY_GENERATIONS = 128L
        const val MAX_STRING_LENGTH = 120
        val VALID_PROFILES = 1..3

        fun openReadOnly(file: File): SQLiteDatabase =
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).also { db ->
                db.rawQuery("PRAGMA foreign_keys = ON", null).close()
            }

        fun readSchemaVersion(file: File): Int? =
            runCatching {
                openReadOnly(file).use { it.version }
            }.getOrNull()

        private fun openConfigured(file: File): SQLiteDatabase {
            val db = SQLiteDatabase.openOrCreateDatabase(file, null)
            return try {
                db.setForeignKeyConstraintsEnabled(true)
                db.enableWriteAheadLogging()
                db.execSQL("PRAGMA synchronous=FULL")
                // AI-INTENT: Android classifies busy_timeout as a query PRAGMA even when it assigns a value.
                // Force evaluation with rawQuery so this works on both the HU and desktop test SQLite.
                db.rawQuery("PRAGMA busy_timeout=5000", null).use { cursor ->
                    check(cursor.moveToFirst()) { "SQLite busy timeout was not configured" }
                }
                check(db.pragmaString("journal_mode").equals("wal", ignoreCase = true)) { "WAL mode was not enabled" }
                check(db.pragmaLong("synchronous") == 2L) { "SQLite synchronous mode is not FULL" }
                check(db.pragmaLong("foreign_keys") == 1L) { "SQLite foreign keys are not enabled" }
                db
            } catch (error: Throwable) {
                db.close()
                throw error
            }
        }

        fun validateSnapshot(file: File): Long {
            val db = openReadOnly(file)
            return try {
                check(db.version == SCHEMA_VERSION) { "Unexpected Player state schema ${db.version}" }
                val revision =
                    db.longOrNull("SELECT state_revision FROM state_metadata WHERE id = 1")
                        ?: error("Snapshot state_revision is missing")
                check(revision >= 0L) { "Snapshot state_revision is negative" }
                check(db.hasFullIntegrity()) { "Snapshot logical integrity check failed" }
                revision
            } finally {
                db.close()
            }
        }

        // BEGIN PLAYER_STATE_SCHEMA_V15
        private val AUDIOBOOK_SPEED_TENTHS = AudiobookSpeed.entries.joinToString(", ") { it.tenths.toString() }

        private val SCHEMA_SQL =
            listOf(
                "CREATE TABLE state_metadata(id INTEGER PRIMARY KEY CHECK(id = 1), state_revision INTEGER NOT NULL CHECK(state_revision >= 0))",
                "CREATE TABLE profiles(profile_id INTEGER PRIMARY KEY CHECK(profile_id IN (1, 2, 3)))",
                "CREATE TABLE active_profile(id INTEGER PRIMARY KEY CHECK(id = 1), profile_id INTEGER NOT NULL REFERENCES profiles(profile_id))",
                "CREATE TABLE installation_settings(id INTEGER PRIMARY KEY CHECK(id = 1), diagonal TEXT NOT NULL CHECK(diagonal IN ('DIAGONAL', 'CARD', 'AUTO')), data_location TEXT NOT NULL CHECK(data_location IN ('HU', 'SD')), calibration_x REAL, calibration_y REAL)",
                """
                CREATE TABLE profile_settings(
                    profile_id INTEGER PRIMARY KEY REFERENCES profiles(profile_id) ON DELETE CASCADE,
                    color_scheme TEXT NOT NULL CHECK(color_scheme IN ('DAY', 'NIGHT', 'SYSTEM')),
                    long_press INTEGER NOT NULL CHECK(long_press IN (0, 1)),
                    auto_search INTEGER NOT NULL CHECK(auto_search IN (0, 1)),
                    ignore_leading_articles INTEGER NOT NULL DEFAULT 1 CHECK(ignore_leading_articles IN (0, 1)),
                    content_mode TEXT NOT NULL CHECK(content_mode IN ('MUSIC', 'AUDIOBOOK')),
                    audiobook_speed_tenths INTEGER NOT NULL DEFAULT ${AudiobookSpeed.NORMAL.tenths}
                        CHECK(audiobook_speed_tenths IN ($AUDIOBOOK_SPEED_TENTHS)),
                    ai_queue_size INTEGER NOT NULL DEFAULT 20 CHECK(ai_queue_size IN (10, 20, 50)),
                    ai_pte_bands INTEGER NOT NULL DEFAULT 0 CHECK(ai_pte_bands BETWEEN 0 AND 7),
                    ai_arousal REAL NOT NULL DEFAULT 0.0 CHECK(ai_arousal BETWEEN -1.0 AND 1.0),
                    ai_valence REAL NOT NULL DEFAULT 0.0 CHECK(ai_valence BETWEEN -1.0 AND 1.0)
                )
                """.trimIndent(),
                "CREATE TABLE reactions(profile_id INTEGER NOT NULL REFERENCES profiles(profile_id) ON DELETE CASCADE, object_key TEXT NOT NULL, preference TEXT NOT NULL CHECK(preference IN ('NEUTRAL', 'DISLIKED', 'LIKED')), own_banned INTEGER NOT NULL CHECK(own_banned IN (0, 1)), PRIMARY KEY(profile_id, object_key)) WITHOUT ROWID",
                "CREATE TABLE audiobook_overrides(profile_id INTEGER NOT NULL REFERENCES profiles(profile_id) ON DELETE CASCADE, object_key TEXT NOT NULL, classification TEXT NOT NULL CHECK(classification IN ('BOOK', 'MUSIC')), PRIMARY KEY(profile_id, object_key)) WITHOUT ROWID",
                "CREATE TABLE learned_state(profile_id INTEGER NOT NULL REFERENCES profiles(profile_id) ON DELETE CASCADE, state_key TEXT NOT NULL, value BLOB NOT NULL, updated_revision INTEGER NOT NULL CHECK(updated_revision >= 0), PRIMARY KEY(profile_id, state_key)) WITHOUT ROWID",
                "CREATE TABLE playlists(playlist_id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL REFERENCES profiles(profile_id) ON DELETE CASCADE, display_name TEXT NOT NULL, canonical_name TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'MUSIC' CHECK(kind IN ('MUSIC', 'BOOK')), title_key TEXT, current_revision_id INTEGER REFERENCES playlist_revisions(revision_id) DEFERRABLE INITIALLY DEFERRED, UNIQUE(profile_id, kind, title_key))",
                "CREATE TABLE playlist_revisions(revision_id INTEGER PRIMARY KEY AUTOINCREMENT, playlist_id INTEGER NOT NULL REFERENCES playlists(playlist_id) ON DELETE CASCADE, created_state_revision INTEGER NOT NULL CHECK(created_state_revision >= 0))",
                "CREATE TABLE playlist_occurrences(occurrence_id INTEGER PRIMARY KEY AUTOINCREMENT, revision_id INTEGER NOT NULL REFERENCES playlist_revisions(revision_id) ON DELETE CASCADE, ordinal INTEGER NOT NULL CHECK(ordinal >= 0), media_id_high INTEGER NOT NULL, media_id_low INTEGER NOT NULL, media_id_low_present INTEGER NOT NULL CHECK(media_id_low_present IN (0, 1)), UNIQUE(revision_id, ordinal))",
                "CREATE TABLE bookmark_marks(mark_id INTEGER PRIMARY KEY AUTOINCREMENT, playlist_id INTEGER NOT NULL REFERENCES playlists(playlist_id) ON DELETE CASCADE, ordinal INTEGER NOT NULL CHECK(ordinal >= 0), position_us INTEGER NOT NULL CHECK(position_us >= 0), label TEXT NOT NULL)",
                "CREATE TABLE bookmark_durations(playlist_id INTEGER NOT NULL REFERENCES playlists(playlist_id) ON DELETE CASCADE, ordinal INTEGER NOT NULL CHECK(ordinal >= 0), duration_us INTEGER NOT NULL CHECK(duration_us > 0), PRIMARY KEY(playlist_id, ordinal)) WITHOUT ROWID",
                "CREATE TABLE playlist_commands(profile_id INTEGER NOT NULL REFERENCES profiles(profile_id) ON DELETE CASCADE, command_id TEXT NOT NULL, playlist_id INTEGER NOT NULL REFERENCES playlists(playlist_id) ON DELETE CASCADE, resulting_revision_id INTEGER NOT NULL REFERENCES playlist_revisions(revision_id) ON DELETE CASCADE, PRIMARY KEY(profile_id, command_id)) WITHOUT ROWID",
                "CREATE TABLE playback_queue_revisions(queue_revision_id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('MANUAL', 'PERMANENT_PLAYLIST', 'AI')), profile_id INTEGER NOT NULL REFERENCES profiles(profile_id), temporary INTEGER NOT NULL CHECK(temporary IN (0, 1)), created_state_revision INTEGER NOT NULL CHECK(created_state_revision >= 0), state TEXT NOT NULL CHECK(state IN ('READY')))",
                "CREATE TABLE playback_queue_occurrences(occurrence_id INTEGER PRIMARY KEY AUTOINCREMENT, queue_revision_id INTEGER NOT NULL REFERENCES playback_queue_revisions(queue_revision_id) ON DELETE CASCADE, ordinal INTEGER NOT NULL CHECK(ordinal >= 0), shuffle_ordinal INTEGER NOT NULL CHECK(shuffle_ordinal >= 0), media_id_high INTEGER NOT NULL, media_id_low INTEGER NOT NULL, media_id_low_present INTEGER NOT NULL CHECK(media_id_low_present IN (0, 1)), UNIQUE(queue_revision_id, ordinal), UNIQUE(queue_revision_id, shuffle_ordinal))",
                "CREATE TABLE active_queue(id INTEGER PRIMARY KEY CHECK(id = 1), queue_revision_id INTEGER NOT NULL REFERENCES playback_queue_revisions(queue_revision_id))",
                "CREATE TABLE playback_checkpoint(id INTEGER PRIMARY KEY CHECK(id = 1), queue_revision_id INTEGER NOT NULL REFERENCES playback_queue_revisions(queue_revision_id), occurrence_id INTEGER REFERENCES playback_queue_occurrences(occurrence_id), position_ms INTEGER NOT NULL CHECK(position_ms >= 0), play_when_ready INTEGER NOT NULL CHECK(play_when_ready IN (0, 1)), repeat_enabled INTEGER NOT NULL CHECK(repeat_enabled IN (0, 1)), shuffle_enabled INTEGER NOT NULL CHECK(shuffle_enabled IN (0, 1)), chosen_binding_id INTEGER)",
                "CREATE TABLE playback_history_generation(id INTEGER PRIMARY KEY CHECK(id = 1), current_generation INTEGER NOT NULL CHECK(current_generation >= 0))",
                "CREATE TABLE recent_listening_events(event_id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL REFERENCES profiles(profile_id) ON DELETE CASCADE, media_id_high INTEGER NOT NULL, media_id_low INTEGER NOT NULL, media_id_low_present INTEGER NOT NULL CHECK(media_id_low_present IN (0, 1)), play_instance_id INTEGER NOT NULL CHECK(play_instance_id >= 0), history_generation INTEGER NOT NULL CHECK(history_generation >= 0), event_type TEXT NOT NULL CHECK(event_type IN ('PLAY_STARTED', 'USER_SKIPPED', 'COMPLETED', 'CENSORED_END')), position_ms INTEGER NOT NULL CHECK(position_ms >= 0), duration_ms INTEGER CHECK(duration_ms IS NULL OR duration_ms >= 0), created_state_revision INTEGER NOT NULL CHECK(created_state_revision >= 0), UNIQUE(profile_id, play_instance_id, event_type))",
                "CREATE TABLE media_playback_totals(profile_id INTEGER NOT NULL REFERENCES profiles(profile_id) ON DELETE CASCADE, media_id_high INTEGER NOT NULL, media_id_low INTEGER NOT NULL, media_id_low_present INTEGER NOT NULL CHECK(media_id_low_present IN (0, 1)), total_started_count INTEGER NOT NULL CHECK(total_started_count >= 0), last_start_generation INTEGER NOT NULL CHECK(last_start_generation >= 0), updated_state_revision INTEGER NOT NULL CHECK(updated_state_revision >= 0), PRIMARY KEY(profile_id, media_id_high, media_id_low, media_id_low_present)) WITHOUT ROWID",
                "CREATE TABLE playback_cycle(id INTEGER PRIMARY KEY CHECK(id = 1), cycle INTEGER NOT NULL CHECK(cycle >= 0))",
                "CREATE TABLE queue_resume_metadata(queue_revision_id INTEGER PRIMARY KEY REFERENCES playback_queue_revisions(queue_revision_id) ON DELETE CASCADE, context_key TEXT NOT NULL, album_context INTEGER NOT NULL CHECK(album_context IN (0, 1)), book_title_context INTEGER NOT NULL DEFAULT 0 CHECK(book_title_context IN (0, 1)), book_title_name TEXT)",
                "CREATE TABLE queue_book_members(queue_revision_id INTEGER NOT NULL REFERENCES playback_queue_revisions(queue_revision_id) ON DELETE CASCADE, source_ordinal INTEGER NOT NULL CHECK(source_ordinal >= 0), queue_ordinal INTEGER CHECK(queue_ordinal >= 0), media_id_high INTEGER NOT NULL, media_id_low INTEGER NOT NULL, media_id_low_present INTEGER NOT NULL CHECK(media_id_low_present IN (0, 1)), PRIMARY KEY(queue_revision_id, source_ordinal), UNIQUE(queue_revision_id, queue_ordinal)) WITHOUT ROWID",
                "CREATE TABLE saved_playback(profile_id INTEGER NOT NULL REFERENCES profiles(profile_id), context_key TEXT NOT NULL, queue_revision_id INTEGER NOT NULL REFERENCES playback_queue_revisions(queue_revision_id), album_context INTEGER NOT NULL CHECK(album_context IN (0, 1)), occurrence_id INTEGER REFERENCES playback_queue_occurrences(occurrence_id), position_ms INTEGER NOT NULL CHECK(position_ms >= 0), play_when_ready INTEGER NOT NULL CHECK(play_when_ready IN (0, 1)), repeat_enabled INTEGER NOT NULL CHECK(repeat_enabled IN (0, 1)), shuffle_enabled INTEGER NOT NULL CHECK(shuffle_enabled IN (0, 1)), chosen_binding_id INTEGER, saved_cycle INTEGER NOT NULL CHECK(saved_cycle >= 0), retention_multiplier INTEGER NOT NULL CHECK(retention_multiplier IN (1, 3)), PRIMARY KEY(profile_id, context_key)) WITHOUT ROWID",
                "CREATE TABLE profile_last_playback(profile_id INTEGER PRIMARY KEY REFERENCES profiles(profile_id), context_key TEXT NOT NULL)",
                "CREATE TABLE listening_event_retention(event_id INTEGER PRIMARY KEY REFERENCES recent_listening_events(event_id) ON DELETE CASCADE, multiplier INTEGER NOT NULL CHECK(multiplier IN (1, 3)))",
                // AI-INTENT: Book collections identify a Title independently of display spelling.
                // Music name uniqueness must not prevent equally named Titles from retaining separate marks.
                // https://wiki.271.pics/Riff/LATEST/player-bookmarks#collection-and-identity
                "CREATE UNIQUE INDEX music_playlist_names ON playlists(profile_id, canonical_name) WHERE kind = 'MUSIC'",
                "CREATE INDEX reactions_object_key ON reactions(object_key)",
                "CREATE INDEX queue_occurrences_media ON playback_queue_occurrences(media_id_high, media_id_low, media_id_low_present)",
                "CREATE INDEX recent_history_generation ON recent_listening_events(history_generation)",
                "CREATE INDEX recent_history_media ON recent_listening_events(profile_id, media_id_high, media_id_low, media_id_low_present, history_generation)",
            )
        // END PLAYER_STATE_SCHEMA_V15
    }
}

private fun SQLiteDatabase.longOrNull(
    sql: String,
    arguments: Array<String>? = null,
): Long? = rawQuery(sql, arguments).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

private fun SQLiteDatabase.stringOrNull(
    sql: String,
    arguments: Array<String>? = null,
): String? = rawQuery(sql, arguments).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

private fun SQLiteDatabase.pragmaLong(name: String): Long = longOrNull("PRAGMA $name") ?: error("PRAGMA $name returned no value")

private fun SQLiteDatabase.pragmaString(name: String): String = stringOrNull("PRAGMA $name") ?: error("PRAGMA $name returned no value")

/** Validates SQLite pages, foreign keys, and cross-table identities not expressible as FKs. */
private fun SQLiteDatabase.hasFullIntegrity(): Boolean {
    val pagesAreValid =
        rawQuery("PRAGMA integrity_check", null).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()
        }
    if (!pagesAreValid) return false
    if (rawQuery("PRAGMA foreign_key_check", null).use(Cursor::moveToFirst)) return false
    val fixedStateMismatch =
        rawQuery(
            """
            SELECT 1 WHERE
              (SELECT group_concat(profile_id, ',') FROM
                (SELECT profile_id FROM profiles ORDER BY profile_id)) != '1,2,3'
              OR (SELECT COUNT(*) FROM profile_settings) != 3
              OR (SELECT COUNT(*) FROM installation_settings WHERE id = 1) != 1
              OR (SELECT COUNT(*) FROM active_profile WHERE id = 1) != 1
              OR (SELECT COUNT(*) FROM state_metadata WHERE id = 1) != 1
              OR (SELECT COUNT(*) FROM playback_history_generation WHERE id = 1) != 1
            """.trimIndent(),
            null,
        ).use(Cursor::moveToFirst)
    if (fixedStateMismatch) return false
    val checkpointMismatch =
        rawQuery(
            """
            SELECT 1 FROM playback_checkpoint c
            LEFT JOIN playback_queue_occurrences o
              ON o.occurrence_id = c.occurrence_id
             AND o.queue_revision_id = c.queue_revision_id
            WHERE c.occurrence_id IS NOT NULL AND o.occurrence_id IS NULL
            LIMIT 1
            """.trimIndent(),
            null,
        ).use(Cursor::moveToFirst)
    if (checkpointMismatch) return false
    val queueCardinalityMismatch =
        rawQuery(
            """
            SELECT 1 WHERE
              (SELECT COUNT(*) FROM active_queue) !=
                (SELECT COUNT(*) FROM playback_checkpoint)
              OR EXISTS(
                SELECT 1 FROM playback_queue_revisions q
                LEFT JOIN playback_queue_occurrences o
                  ON o.queue_revision_id = q.queue_revision_id
                GROUP BY q.queue_revision_id HAVING COUNT(o.occurrence_id) = 0
              )
            """.trimIndent(),
            null,
        ).use(Cursor::moveToFirst)
    if (queueCardinalityMismatch) return false
    val activeQueueMismatch =
        rawQuery(
            """
            SELECT 1 FROM active_queue a
            LEFT JOIN playback_checkpoint c
              ON c.id = 1 AND c.queue_revision_id = a.queue_revision_id
            WHERE a.id = 1 AND c.id IS NULL
            LIMIT 1
            """.trimIndent(),
            null,
        ).use(Cursor::moveToFirst)
    if (activeQueueMismatch) return false
    val playlistMismatch =
        rawQuery(
            """
            SELECT 1 FROM playlists p
            LEFT JOIN playlist_revisions r
              ON r.revision_id = p.current_revision_id
             AND r.playlist_id = p.playlist_id
            WHERE p.current_revision_id IS NULL OR r.revision_id IS NULL
            LIMIT 1
            """.trimIndent(),
            null,
        ).use(Cursor::moveToFirst)
    if (playlistMismatch) return false
    val emptyCollection =
        rawQuery(
            "SELECT 1 FROM playlists p LEFT JOIN playlist_occurrences o ON o.revision_id = p.current_revision_id LEFT JOIN bookmark_marks m ON m.playlist_id = p.playlist_id WHERE o.occurrence_id IS NULL OR (p.kind = 'BOOK' AND m.mark_id IS NULL) LIMIT 1",
            null,
        ).use(Cursor::moveToFirst)
    if (emptyCollection) return false
    if (longOrNull("SELECT COUNT(*) FROM playback_cycle") != 1L) return false
    val invalidResume =
        rawQuery(
            "SELECT 1 FROM saved_playback s LEFT JOIN playback_queue_occurrences o ON o.occurrence_id = s.occurrence_id AND o.queue_revision_id = s.queue_revision_id WHERE s.occurrence_id IS NOT NULL AND o.occurrence_id IS NULL LIMIT 1",
            null,
        ).use(Cursor::moveToFirst)
    if (invalidResume) return false
    val invalidLast =
        rawQuery(
            "SELECT 1 FROM profile_last_playback p LEFT JOIN saved_playback s ON s.profile_id = p.profile_id AND s.context_key = p.context_key WHERE s.profile_id IS NULL LIMIT 1",
            null,
        ).use(Cursor::moveToFirst)
    if (invalidLast) return false
    return !rawQuery(
        """
        SELECT 1 FROM playlist_commands c
        LEFT JOIN playlist_revisions r
          ON r.revision_id = c.resulting_revision_id
         AND r.playlist_id = c.playlist_id
        WHERE r.revision_id IS NULL
        LIMIT 1
        """.trimIndent(),
        null,
    ).use(Cursor::moveToFirst)
}
