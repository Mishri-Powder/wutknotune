package com.example.musicapp.sampler

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class SamplerEngine {
    private var audioThread: Thread? = null
    @Volatile
    private var isRunning = false
    @Volatile
    private var currentFrequency = 440f

    fun playTone(frequency: Float) {
        stopTone() // Stop any existing tone first
        currentFrequency = frequency
        isRunning = true
        
        audioThread = Thread {
            val sampleRate = 44100
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize,
                AudioTrack.MODE_STREAM
            )
            
            audioTrack.play()
            
            val durationMs = 500L // Play for 500ms per trigger
            val totalSamples = (sampleRate * durationMs / 1000).toInt()
            var samplesPlayed = 0
            
            val buffer = ShortArray(minBufferSize / 2)
            
            while (isRunning && samplesPlayed < totalSamples) {
                val samplesToGenerate = minOf(buffer.size, totalSamples - samplesPlayed)
                
                for (i in 0 until samplesToGenerate) {
                    val time = (samplesPlayed + i).toDouble() / sampleRate
                    val value = sin(2.0 * PI * currentFrequency * time) * Short.MAX_VALUE * 0.3
                    buffer[i] = value.toInt().toShort()
                }
                
                audioTrack.write(buffer, 0, samplesToGenerate)
                samplesPlayed += samplesToGenerate
            }
            
            audioTrack.stop()
            audioTrack.release()
        }
        
        audioThread?.start()
    }

    fun stopTone() {
        isRunning = false
        audioThread?.join(1000) // Wait up to 1 second for thread to finish
        audioThread = null
    }

    fun release() {
        stopTone()
    }
}