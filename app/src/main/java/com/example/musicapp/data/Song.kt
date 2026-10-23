package com.example.musicapp.data

/**
 * Core data model for a track in the offline library.
 * Mirrors RetroMusic/Effin's Song entity, trimmed to what we actually need.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,          // absolute file path — playback is always from disk (offline)
    val durationMs: Long,
    val sizeBytes: Long = 0,
    val albumId: Long = -1,
    val artistId: Long = -1,
    val year: Int = 0,
    val trackNumber: Int = 0,
    var isInPlaylist: Boolean = false
) {
    val displayTitle: String get() = title.ifBlank { path.substringAfterLast('/') }
}

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val songs: List<Song>
)

data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int
)

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
    val isPublic: Boolean = true
)

enum class RepeatMode { NONE, ALL, ONE }

enum class ShuffleMode { OFF, TRACKS, ALBUMS }
