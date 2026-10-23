package com.example.musicapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.musicapp.editor.AudioEditor
import com.example.musicapp.ui.AppState

/** Offline editor: pick a file, see waveform, trim/fade/gain, export WAV (optionally → pad). */
@Composable
fun EditorScreen(state: AppState) {
    val pcm = state.editorPcm
    var trimStart by remember { mutableFloatStateOf(0f) }
    var trimEnd by remember { mutableFloatStateOf(1f) }
    var fadeInSec by remember { mutableFloatStateOf(0f) }
    var fadeOutSec by remember { mutableFloatStateOf(0f) }
    var gainDb by remember { mutableFloatStateOf(0f) }
    var sendToPad by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.loadIntoEditor(uri)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Audio Editor", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Open file") }
        }
        state.editorStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(12.dp))
        // Waveform with trim region highlight
        Card(Modifier.fillMaxWidth().height(160.dp)) {
            if (pcm == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No sample loaded")
                }
            } else {
                val env = remember(pcm) { AudioEditor.waveform(pcm, 240) }
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val w = size.width; val h = size.height; val mid = h / 2f
                    val bw = w / env.size
                    env.forEachIndexed { i, (mn, mx) ->
                        drawLine(
                            Color.Gray,
                            Offset(i * bw, mid + mn.toFloat() / Short.MAX_VALUE * mid),
                            Offset(i * bw, mid + mx.toFloat() / Short.MAX_VALUE * mid),
                            strokeWidth = 1.5f
                        )
                    }
                    drawRect(Color(0x336200EE), Offset(trimStart * w, 0f),
                            Size((trimEnd - trimStart) * w, h))
                }
            }
        }

        if (pcm != null) {
            Slider(value = trimStart, onValueChange = { trimStart = it.coerceAtMost(trimEnd - 0.01f) },
                   modifier = Modifier.padding(top = 8.dp))
            Slider(value = trimEnd, onValueChange = { trimEnd = it.coerceAtLeast(trimStart + 0.01f) })
            Text("Fade in: ${fadeInSec.toInt()}s")
            Slider(value = fadeInSec, onValueChange = { fadeInSec = it }, valueRange = 0f..5f)
            Text("Fade out: ${fadeOutSec.toInt()}s")
            Slider(value = fadeOutSec, onValueChange = { fadeOutSec = it }, valueRange = 0f..5f)
            Text("Gain: ${gainDb.toInt()} dB")
            Slider(value = gainDb, onValueChange = { gainDb = it }, valueRange = -12f..12f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(checked = sendToPad, onCheckedChange = { sendToPad = it })
                Text("Send result to next free pad", modifier = Modifier.padding(top = 12.dp))
            }
            Button(
                onClick = {
                    val durMs = pcm.size * 1000L / state.editorRate
                    state.applyEdit(
                        AudioEditor.Edit(
                            trimStartMs = (trimStart * durMs).toLong(),
                            trimEndMs = (trimEnd * durMs).toLong(),
                            fadeInMs = (fadeInSec * 1000).toLong(),
                            fadeOutMs = (fadeOutSec * 1000).toLong(),
                            gainDb = gainDb
                        ),
                        assignToPad = if (sendToPad)
                            (0 until com.example.musicapp.sampler.SamplerEngine.PAD_COUNT)
                                .firstOrNull { idx -> state.sampler.padLabel(idx).startsWith("Pad") } ?: 0
                            else null
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Apply & Export WAV") }
        }
    }
}
