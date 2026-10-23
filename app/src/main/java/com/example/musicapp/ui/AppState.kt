package com.example.musicapp.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.musicapp.data.Album
import com.example.musicapp.data.Artist
import com.example.musicapp.data.MusicRepository
import com.example.musicapp.data.Song
import com.example.musicapp.editor.AudioEditor
import com.example.musicapp.player.MusicPlayerServiceBinder
import com.example.musicapp.sampler.SamplerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

enum class Tab { LIBRARY, SAMPLER, EDITOR }

/**
 * Single app-wide state holder. Deliberately simple (no DI framework) for stability:
 * one scope with SupervisorJob so a failed scan never kills the app.
 */
class AppState(val application: Application) {
    private val repo = MusicRepository(application)
    val player = MusicPlayerServiceBinder(application)
    val sampler = SamplerEngine()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var artists by mutableStateOf<List<Artist>>(emptyList()); private set
    var isLoadingLibrary by mutableStateOf(false); private set
    var libraryError by mutableStateOf<String?>(null); private set

    var tab by mutableStateOf(Tab.LIBRARY)

    /** bump to force recomposition when pad contents change */
    var samplerVersion by mutableStateOf(0); private set

    // editor state
    var editorPcm by mutableStateOf<ShortArray?>(null); private set
    var editorRate by mutableStateOf(44100); private set
    var editorStatus by mutableStateOf<String?>(null); private set

    fun refreshLibrary() {
        scope.launch {
            isLoadingLibrary = true
            libraryError = null
            runCatching { repo.loadSongs() }
                .onSuccess {
                    songs = it
                    albums = repo.songsToAlbums(it)
                    artists = repo.songsToArtists(it)
                }
                .onFailure { libraryError = "Scan failed: ${it.message}" }
            isLoadingLibrary = false
        }
    }

    fun playFrom(songs: List<Song>, index: Int) = player.setQueue(songs, index)

    /** Import a picked file into a sampler pad (async decode, UI stays responsive). */
    fun importToPad(padIndex: Int, uri: Uri) {
        scope.launch {
            val sample = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Sample"
                    SamplerEngine.decodeToSample(uri, application, name)
                }.getOrNull()
            }
            if (sample != null) { sampler.loadSample(padIndex, sample); samplerVersion++ }
        }
    }

    fun loadIntoEditor(uri: Uri) {
        scope.launch {
            editorStatus = "Decoding…"
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { AudioEditor.decodeMono(uri, application) }
            }.getOrNull()
            if (result != null) {
                editorPcm = result.first; editorRate = result.second; editorStatus = "Loaded (${result.first.size} samples)"
            } else editorStatus = "Decode failed — unsupported file?"
        }
    }

    fun applyEdit(edit: AudioEditor.Edit, assignToPad: Int?) {
        val pcm = editorPcm ?: return
        scope.launch {
            editorStatus = "Processing…"
            val ok = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
                    val out = AudioEditor.applyPcm(pcm, editorRate, edit)
                    val f = File(application.cacheDir, "edit_${System.currentTimeMillis()}.wav")
                    val wrote = AudioEditor.writeWav(out, editorRate, f)
                    if (wrote && assignToPad != null) {
                        sampler.loadSample(assignToPad, SamplerEngine.Sample("Edited", out, editorRate)); samplerVersion++
                    }
                    wrote
                }.getOrDefault(false)
            }
            editorStatus = if (ok) "Exported to cache WAV${if (assignToPad != null) " → Pad ${assignToPad + 1}" else ""}"
                           else "Export failed"
        }
    }

    fun release() { player.release(); sampler.release() }
}
