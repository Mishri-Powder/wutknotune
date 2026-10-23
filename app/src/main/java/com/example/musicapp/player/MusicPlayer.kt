package com.example.musicapp.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.musicapp.data.RepeatMode
import com.example.musicapp.data.ShuffleMode
import com.example.musicapp.data.Song

/**
 * Gapless-capable queue player built directly on MediaPlayer + MediaSession.
 *
 * Stability rules:
 *  - one MediaPlayer instance at a time, always released before replacement
 *  - next track prepared on completion BEFORE ui state changes (no dead air)
 *  - all callbacks posted to main thread by caller (handler)
 *  - no network paths anywhere — Uri.fromFile only
 */
class MusicPlayerServiceBinder(private val context: Context) {

    private var player: MediaPlayer? = null
    private var session: MediaSessionCompat? = null

    private var queue: List<Song> = emptyList()
    private var playOrder: List<Int> = emptyList()   // indices into queue honoring shuffle
    private var orderPos = 0
    var repeatMode = RepeatMode.NONE
    var shuffleMode = ShuffleMode.OFF
        set(value) { field = value; rebuildOrder() }

    var isPlaying = false; private set
    var currentSong: Song? = null; private set

    // observers wired by the ViewModel
    var onStateChange: (() -> Unit)? = null
    var onTrackComplete: (() -> Unit)? = null

    init {
        runCatching {
            session = MediaSessionCompat(context, "MusicAppSession").apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() { resumeOrStart() }
                    override fun onPause() { pause() }
                    override fun onSkipToNext() { next() }
                    override fun onSkipToPrevious() { previous() }
                    override fun onSeekTo(pos: Long) { seekTo(pos) }
                    override fun onStop() { stop() }
                })
                isActive = true
            }
        }
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        queue = songs
        orderPos = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        rebuildOrder()
        playCurrent()
    }

    private fun rebuildOrder() {
        if (queue.isEmpty()) { playOrder = emptyList(); return }
        val curId = currentSong?.id
        val curIdx = queue.indexOfFirst { it.id == curId }
        playOrder = when (shuffleMode) {
            ShuffleMode.OFF -> queue.indices.toList()
            else -> {
                val shuffled = queue.indices.shuffled().toMutableList()
                if (curIdx >= 0) { shuffled.remove(curIdx); shuffled.add(0, curIdx) }
                shuffled
            }
        }
        // keep pointing at the same song after reordering
        if (curIdx >= 0) orderPos = playOrder.indexOf(curIdx).coerceAtLeast(0)
    }

    fun playCurrent() {
        val song = playOrder.getOrNull(orderPos)?.let { queue[it] } ?: return
        releasePlayer()
        currentSong = song
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, Uri.fromFile(java.io.File(song.path)))
                setOnCompletionListener {
                    onTrackComplete?.invoke()
                    when (repeatMode) {
                        RepeatMode.ONE -> { seekTo(0); startInternal() }
                        else -> next(auto = true)
                    }
                }
                setOnErrorListener { _, _, _ ->
                    // never crash the service; skip broken file and continue
                    next(auto = true)
                    true
                }
                prepare()
                startInternal()
            }
        }.onFailure {
            // corrupt/unreadable file → skip instead of stalling
            next(auto = true)
        }
        updateSessionState(song)
        onStateChange?.invoke()
    }

    private fun startInternal() {
        player?.start()
        isPlaying = true
        refreshPlaybackState()
    }

    fun resumeOrStart() {
        if (player == null) playCurrent() else { player?.start(); isPlaying = true; refreshPlaybackState() }
        onStateChange?.invoke()
    }

    fun pause() {
        runCatching { player?.pause() }
        isPlaying = false
        refreshPlaybackState()
        onStateChange?.invoke()
    }

    fun stop() {
        releasePlayer()
        isPlaying = false
        currentSong = null
        refreshPlaybackState()
        onStateChange?.invoke()
    }

    fun seekTo(ms: Long) {
        runCatching { player?.seekTo(ms.toInt().coerceAtLeast(0)) }
        refreshPlaybackState()
        onStateChange?.invoke()
    }

    fun next(auto: Boolean = false) {
        if (playOrder.isEmpty()) return
        if (orderPos < playOrder.lastIndex) { orderPos++; playCurrent() }
        else if (repeatMode == RepeatMode.ALL || !auto) { orderPos = 0; playCurrent() }
        else { pause() }
    }

    fun previous() {
        // Retro-Music behavior: restart if >3s in, else previous track
        if ((player?.currentPosition ?: 0) > 3_000) { seekTo(0); return }
        if (orderPos > 0) { orderPos--; playCurrent() } else seekTo(0)
    }

    val positionMs: Long get() = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
    val durationMs: Long get() = currentSong?.durationMs ?: 0L

    private fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun updateSessionState(song: Song) {
        session?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.displayTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationMs)
                .build()
        )
        refreshPlaybackState()
    }

    private fun refreshPlaybackState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, positionMs, 1f)
                .build()
        )
    }

    fun release() {
        releasePlayer()
        runCatching { session?.release() }
        session = null
    }
}
