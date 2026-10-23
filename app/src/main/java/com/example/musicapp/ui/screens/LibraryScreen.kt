package com.example.musicapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicapp.data.Song
import com.example.musicapp.ui.AppState

/** Songs tab — Retro-Music style list with a persistent mini player bar. */
@Composable
fun LibraryScreen(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = { state.refreshLibrary() }) { Text("Rescan") }
        }
        if (state.isLoadingLibrary) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.libraryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.songs.isEmpty() && !state.isLoadingLibrary) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No local music found.\nPut audio files on the device and hit Rescan.")
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(state.songs, key = { it.id }) { song ->
                    SongRow(song) { state.playFrom(state.songs, state.songs.indexOf(song)) }
                    HorizontalDivider()
                }
            }
        }
        MiniPlayerBar(state)
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(song.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Text("${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                 style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(fmtMmSs(song.durationMs), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MiniPlayerBar(state: AppState) {
    val song = state.player.currentSong ?: return
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(song, state.player.isPlaying) {
        while (true) {
            position = state.player.positionMs
            kotlinx.coroutines.delay(500)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(song.displayTitle, maxLines = 1, style = MaterialTheme.typography.titleMedium)
            Text(song.artist, style = MaterialTheme.typography.bodySmall)
            Slider(
                value = if (song.durationMs > 0) position.toFloat() else 0f,
                onValueChange = { },
                valueRange = 0f..song.durationMs.coerceAtLeast(1).toFloat(),
                onValueChangeFinished = { state.player.seekTo(position) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { state.player.previous() }) { Text("⏮") }
                FilledIconButton(onClick = {
                    if (state.player.isPlaying) state.player.pause() else state.player.resumeOrStart()
                }) { Text(if (state.player.isPlaying) "⏸" else "▶") }
                TextButton(onClick = { state.player.next() }) { Text("⏭") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    state.player.repeatMode = when (state.player.repeatMode) {
                        com.example.musicapp.data.RepeatMode.NONE -> com.example.musicapp.data.RepeatMode.ALL
                        com.example.musicapp.data.RepeatMode.ALL -> com.example.musicapp.data.RepeatMode.ONE
                        else -> com.example.musicapp.data.RepeatMode.NONE
                    }
                }) { Text("🔁 ${state.player.repeatMode}") }
                TextButton(onClick = {
                    state.player.shuffleMode = if (state.player.shuffleMode == com.example.musicapp.data.ShuffleMode.OFF)
                        com.example.musicapp.data.ShuffleMode.TRACKS else com.example.musicapp.data.ShuffleMode.OFF
                }) { Text("🔀") }
            }
        }
    }
}

fun fmtMmSs(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
