package com.example.musicapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.sampler.SamplerEngine
import com.example.musicapp.ui.theme.MusicAppTheme

class MainActivity : ComponentActivity() {
    private lateinit var samplerEngine: SamplerEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        samplerEngine = SamplerEngine()

        setContent {
            MusicAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(samplerEngine = samplerEngine)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        samplerEngine.release()
    }
}

@Composable
fun MainScreen(samplerEngine: SamplerEngine) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Music App",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Core Features Section
        Text(
            text = "Core Features",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FeatureCard(title = "Offline Playback", description = "Flawless gapless audio playback")
        FeatureCard(title = "Media Library", description = "Browse songs, albums, artists")
        FeatureCard(title = "Playlists", description = "Create and manage playlists")
        FeatureCard(title = "EQ & ReplayGain", description = "Audio enhancement")
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sampler Section
        Text(
            text = "Kwala-Style Sampler",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SamplerPad(samplerEngine = samplerEngine)
    }
}

@Composable
fun FeatureCard(title: String, description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SamplerPad(samplerEngine: SamplerEngine) {
    var frequency by remember { mutableFloatStateOf(440f) }
    var isPlaying by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sample Pad", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            
            Slider(
                value = frequency,
                onValueChange = { frequency = it },
                valueRange = 220f..880f,
                steps = 3,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text("Frequency: ${frequency.toInt()} Hz")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    if (isPlaying) {
                        samplerEngine.stopTone()
                        isPlaying = false
                    } else {
                        samplerEngine.playTone(frequency)
                        isPlaying = true
                    }
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPlaying) "Stop" else "Play Tone")
                }
            }
        }
    }
}