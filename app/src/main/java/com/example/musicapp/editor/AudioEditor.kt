package com.example.musicapp.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Offline waveform editor: trim + fade in/out + gain, exported as 16-bit WAV.
 * Decodes via MediaCodec (offline), processes PCM in memory, writes WAV by hand.
 * All operations are pure functions on ShortArray so they can be unit-tested.
 */
object AudioEditor {

    data class Edit(
        val trimStartMs: Long = 0,
        val trimEndMs: Long = Long.MAX_VALUE,
        val fadeInMs: Long = 0,
        val fadeOutMs: Long = 0,
        val gainDb: Float = 0f
    )

    /** Apply edits to mono/stereo-interleaved PCM. Sample-exact math, no allocation churn. */
    fun applyPcm(pcm: ShortArray, sampleRate: Int, edit: Edit): ShortArray {
        val start = ((edit.trimStartMs * sampleRate) / 1000).toInt().coerceIn(0, pcm.size)
        val end = if (edit.trimEndMs == Long.MAX_VALUE) pcm.size
                  else ((edit.trimEndMs * sampleRate) / 1000).toInt().coerceIn(start, pcm.size)
        val out = pcm.copyOfRange(start, end)
        val gain = Math.pow(10.0, edit.gainDb / 20.0).toFloat()

        val fadeSamples = (edit.fadeInMs * sampleRate / 1000).toInt().coerceAtMost(out.size)
        for (i in 0 until fadeSamples) {
            out[i] = scale(out[i], gain * i.toFloat() / fadeSamples)
        }
        val fadeOutSamples = (edit.fadeOutMs * sampleRate / 1000).toInt().coerceAtMost(out.size)
        for (i in 0 until fadeOutSamples) {
            val idx = out.size - 1 - i
            out[idx] = scale(out[idx], gain * i.toFloat() / fadeOutSamples)
        }
        // remaining samples still get gain applied
        for (i in fadeSamples until out.size - fadeOutSamples) {
            out[i] = scale(out[i], gain)
        }
        return out
    }

    private fun scale(s: Short, factor: Float): Short {
        val v = s * factor
        return v.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    /** Peak-normalize a sample (used by sampler "normalize" action). */
    fun normalize(pcm: ShortArray, targetDb: Float = -1f): ShortArray {
        var peak = 1
        for (s in pcm) peak = maxOf(peak, abs(s.toInt()))
        val factor = exp((targetDb / 20.0) * kotlin.math.ln(10.0)) / peak
        return ShortArray(pcm.size) { scale(pcm[it], factor.toFloat()) }
    }

    /** Compute a min/max envelope for waveform drawing. */
    fun waveform(pcm: ShortArray, buckets: Int): List<Pair<Short, Short>> {
        if (pcm.isEmpty()) return emptyList()
        val per = (pcm.size + buckets - 1) / buckets
        return (0 until buckets).map { b ->
            var mn: Short = Short.MAX_VALUE; var mx: Short = Short.MIN_VALUE
            val s0 = b * per; val s1 = minOf(s0 + per, pcm.size)
            var i = s0
            while (i < s1) { val v = pcm[i]; if (v < mn) mn = v; if (v > mx) mx = v; i++ }
            mn to mx
        }
    }

    /** Decode any supported audio file to mono 16-bit PCM + rate. */
    fun decodeMono(uri: Uri, context: Context): Pair<ShortArray, Int>? =
        com.example.musicapp.sampler.SamplerEngine
            .decodeToSample(uri, context, uri.lastPathSegment ?: "sample")
            ?.let { it.pcm to it.sampleRate }

    /** Write mono PCM as a standard 44-byte-header WAV file. */
    fun writeWav(pcm: ShortArray, sampleRate: Int, outFile: File): Boolean = runCatching {
        RandomAccessFile(outFile, "rw").use { raf ->
            raf.setLength(0)
            val dataBytes = pcm.size * 2
            raf.writeBytes("RIFF"); raf.writeIntLE(36 + dataBytes); raf.writeBytes("WAVE")
            raf.writeBytes("fmt "); raf.writeIntLE(16)
            raf.writeShortLE(1); raf.writeShortLE(1)             // PCM, mono
            raf.writeIntLE(sampleRate); raf.writeIntLE(sampleRate * 2)
            raf.writeShortLE(2); raf.writeShortLE(16)            // block align, bits
            raf.writeBytes("data"); raf.writeIntLE(dataBytes)
            val bytes = ByteArray(dataBytes)
            for (i in pcm.indices) {
                bytes[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = ((pcm[i].toInt() shr 8) and 0xFF).toByte()
            }
            raf.write(bytes)
        }
        true
    }.getOrDefault(false)

    private fun java.io.DataOutput.writeBytes(s: String) { for (c in s) write(c.code) }
    private fun RandomAccessFile.writeShortLE(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
    private fun RandomAccessFile.writeIntLE(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF) }
}
