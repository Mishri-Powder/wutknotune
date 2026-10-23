package com.example.musicapp.data

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans MediaStore for locally-stored audio only (flawless offline requirement).
 * No network sources, no streaming. Equivalent to Effin/Retro Music's music repository.
 */
class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (c.moveToNext()) {
                val path = c.getString(dataCol) ?: continue
                songs.add(
                    Song(
                        id = c.getLong(idCol),
                        title = c.getString(titleCol) ?: "",
                        artist = c.getString(artistCol) ?: "<unknown>",
                        album = c.getString(albumCol) ?: "<unknown>",
                        path = path,
                        durationMs = c.getLong(durCol),
                        sizeBytes = c.getLong(sizeCol),
                        albumId = c.getLong(albumIdCol),
                        artistId = c.getLong(artistIdCol),
                        year = c.getInt(yearCol),
                        trackNumber = c.getInt(trackCol)
                    )
                )
            }
        }
        songs
    }

    fun songsToAlbums(songs: List<Song>): List<Album> =
        songs.groupBy { it.album }.map { (name, list) ->
            Album(
                id = list.first().albumId,
                name = name,
                artist = list.first().artist,
                songCount = list.size,
                songs = list.sortedBy { it.trackNumber }
            )
        }.sortedBy { it.name.lowercase() }

    fun songsToArtists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }.map { (name, list) ->
            Artist(
                id = list.first().artistId,
                name = name,
                albumCount = list.map { it.album }.distinct().size,
                songCount = list.size
            )
        }.sortedBy { it.name.lowercase() }
}
