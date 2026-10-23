package com.example.musicapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.musicapp.ui.AppState
import com.example.musicapp.ui.Tab
import com.example.musicapp.ui.screens.EditorScreen
import com.example.musicapp.ui.screens.LibraryScreen
import com.example.musicapp.ui.screens.SamplerScreen
import com.example.musicapp.ui.theme.MusicAppTheme
import com.example.musicapp.ui.wall.PosterWall

class MainActivity : ComponentActivity() {
    private lateinit var appState: AppState

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            appState.refreshLibrary()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appState = AppState(application)

        // runtime permissions for MediaStore read (offline library scan)
        val perms = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        runCatching { permissionLauncher.launch(perms) }

        setContent {
            MusicAppTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = appState.tab == Tab.LIBRARY,
                                onClick = { appState.tab = Tab.LIBRARY },
                                icon = { Icon(Icons.Default.LibraryMusic, null) },
                                label = { Text("Library") }
                            )
                            NavigationBarItem(
                                selected = appState.tab == Tab.WALL,
                                onClick = { appState.tab = Tab.WALL },
                                icon = { Icon(Icons.Default.PhotoLibrary, null) },
                                label = { Text("Wall") }
                            )
                            NavigationBarItem(
                                selected = appState.tab == Tab.SAMPLER,
                                onClick = { appState.tab = Tab.SAMPLER },
                                icon = { Icon(Icons.Default.GraphicEq, null) },
                                label = { Text("Sampler") }
                            )
                            NavigationBarItem(
                                selected = appState.tab == Tab.EDITOR,
                                onClick = { appState.tab = Tab.EDITOR },
                                icon = { Icon(Icons.Default.Tune, null) },
                                label = { Text("Editor") }
                            )
                        }
                    }
                ) { padding ->
                    val content = Modifier.fillMaxSize().padding(padding)
                    Surface(modifier = content, color = MaterialTheme.colorScheme.background) {
                        when (appState.tab) {
                            Tab.LIBRARY -> LibraryScreen(appState)
                            // read playerVersion so the wall recomposes on play/pause/skip
                            Tab.WALL -> {
                                @Suppress("UNUSED_EXPRESSION") appState.playerVersion
                                PosterWall(
                                    songs = appState.songs,
                                    currentId = appState.player.currentSong?.id,
                                    isPlaying = appState.player.isPlaying,
                                    positionProvider = {
                                        appState.player.positionMs to appState.player.durationMs
                                    },
                                    seekProvider = { frac ->
                                        val dur = appState.player.durationMs
                                        if (dur > 0) appState.player.seekTo((frac * dur).toLong())
                                    },
                                    onSelect = { song ->
                                        appState.playFrom(appState.songs, appState.songs.indexOfFirst { it.id == song.id })
                                    },
                                    onToggle = {
                                        if (appState.player.isPlaying) appState.player.pause()
                                        else appState.player.resumeOrStart()
                                    }
                                )
                            }
                            Tab.SAMPLER -> SamplerScreen(appState)
                            Tab.EDITOR -> EditorScreen(appState)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::appState.isInitialized) appState.release()
    }
}
