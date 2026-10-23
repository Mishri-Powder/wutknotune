package com.example.musicapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.musicapp.sampler.SamplerEngine
import com.example.musicapp.ui.AppState

/** Kwala-style 8-pad grid. Tap = trigger, long-press = assign a file to the pad. */
@Composable
fun SamplerScreen(state: AppState) {
    var targetPad by remember { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.importToPad(targetPad, uri)
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Sampler", style = MaterialTheme.typography.headlineMedium)
        Text("Tap a pad to play. Long-press to load an audio file onto that pad.",
             style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        // 2 x 4 grid
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0 until 4) {
                    val idx = row * 4 + col
                    PadButton(
                        index = idx,
                        label = { state.samplerVersion; state.sampler.padLabel(idx) }(),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        state.sampler.triggerPad(idx)
                    } onLongPress = {
                        targetPad = idx
                        picker.launch(arrayOf("audio/*"))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { state.sampler.stopAll() }, modifier = Modifier.weight(1f)) { Text("STOP ALL") }
            Button(
                onClick = {
                    targetPad = (targetPad + 1) % SamplerEngine.PAD_COUNT
                    picker.launch(arrayOf("audio/*"))
                },
                modifier = Modifier.weight(1f)
            ) { Text("Load → Pad ${targetPad + 1}") }
        }
    }
}

@Composable
private fun PadButton(
    index: Int,
    label: String,
    modifier: Modifier,
    onPress: () -> Unit,
    onLongPress: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (pressed) 12.dp else 3.dp,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    onPress()
                    tryAwaitRelease()
                    pressed = false
                },
                onLongPress = { onLongPress() }
            )
        }
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${index + 1}", style = MaterialTheme.typography.headlineSmall)
                Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}
