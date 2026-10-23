package com.example.musicapp.sampler

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Kwala-style sampler engine.
 *
 * Each pad holds a Sample (decoded PCM from a user file, or a generated tone).
 * Pads are polyphonic: triggering pad B does not stop pad A.
 * Low-latency: MODE_STATIC AudioTrack per voice — sample is uploaded once and
 * started instantly on tap, no streaming buffer warmup.
 */
class SamplerEngine {

    data class Sample(
        val name: String,
        val pcm: ShortArray,          // mono 16-bit PCM at [sampleRate]
        val sampleRate: Int = 44100
    )

    companion object {
        const val PAD_COUNT = 8

        /** Decode an audio file (mp3/wav/etc.) into mono PCM for a pad. Fully offline. */
        fun decodeToSample(uri: Uri, context: android.content.Context, name: String): Sample? {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: return null
                var trackIndex = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                        trackIndex = i; format = f; break
                    }
                }
                if (trackIndex < 0 || format == null) return null
                extractor.selectTrack(trackIndex)
                codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                codec.configure(format, null, null, 0)
                codec.start()

                val outBufInfo = MediaCodec.BufferInfo()
                val pcm = mutableListOf<Short>()
                var eosSeen = false
                while (!eosSeen) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val sampleTime = extractor.readSampleData(codec.getInputBuffer(inIdx)!!, 0)
                        if (sampleTime < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosSeen = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleTime, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(outBufInfo, 10_000)
                    if (outIdx >= 0) {
                            if (outBufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eosSeen = true
                            val bb = codec.getOutputBuffer(outIdx)!!
                            bb.position(outBufInfo.offset)
                            bb.limit(outBufInfo.offset + outBufInfo.size)
                            val bytes = ByteArray(outBufInfo.size)
                            bb.get(bytes)
                            // downmix stereo→mono, read 16-bit LE
                            var ch = 1
                            runCatching { ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                            var i = 0
                            while (i + 1 < bytes.size) {
                                val s = ((bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)).toShort()
                                if (ch <= 1) {
                                    pcm.add(s)
                                    i += 2
                                } else {
                                    // average next ch channels
                                    var sum = s.toInt(); var n = 1
                                    while (n < ch && i + n * 2 + 1 < bytes.size) {
                                        sum += ((bytes[i + n * 2].toInt() and 0xFF) or
                                                (bytes[i + n * 2 + 1].toInt() shl 8)).toShort().toInt()
                                        n++
                                    }
                                    pcm.add((sum / n).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                                    i += n * 2
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                    }
                }
                val rate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(44100)
                if (pcm.isEmpty()) return null
                return Sample(name, ShortArray(pcm.size) { pcm[it] }, rate)
            } catch (e: Exception) {
                null
            } finally {
                runCatching { codec?.stop(); codec?.release() }
                runCatching { extractor.release() }
            }
        }

        /** Built-in synthesized tones so pads work even with zero files imported. */
        fun tone(index: Int): Sample {
            val freq = floatArrayOf(261.63f, 293.66f, 329.63f, 392.0f, 440.0f, 523.25f, 587.33f, 659.25f)[index % 8]
            val rate = 44100
            val len = (rate * 0.5).toInt()
            val pcm = ShortArray(len)
            for (i in 0 until len) {
                val t = i.toDouble() / rate
                val env = exp(-3.0 * t) // fast decay like a drum pad
                val v = sin(2.0 * PI * freq * t) * env * Short.MAX_VALUE * 0.6
                pcm[i] = v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            return Sample("Pad ${index + 1}", pcm, rate)
        }
    }

    private val pads = arrayOfNulls<Sample>(PAD_COUNT)
    private val activeTracks = java.util.Collections.synchronizedList(mutableListOf<AudioTrack>())

    init {
        // preload demo tones so the pad grid is instantly playable (stability-first)
        for (i in 0 until PAD_COUNT) pads[i] = tone(i)
    }

    fun padLabel(index: Int): String = pads[index]?.name ?: "Empty"
    fun isPadLoaded(index: Int): Boolean = pads[index] != null

    /** Assign decoded audio (from file picker) to a pad — the Kwala workflow. */
    fun loadSample(index: Int, sample: Sample) {
        if (index in 0 until PAD_COUNT) pads[index] = sample
    }

    fun clearPad(index: Int) { pads[index] = null }

    /** Fire a pad. Non-blocking; returns immediately, audio plays on its own static track. */
    fun triggerPad(index: Int) {
        val sample = pads[index] ?: return
        Thread {
            runCatching {
                val minBuf = AudioTrack.getMinBufferSize(
                    sample.sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(sample.pcm.size * 2)

                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sample.sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    minBuf,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                synchronized(activeTracks) { activeTracks.add(track) }
                try {
                    track.write(sample.pcm, 0, sample.pcm.size)
                    track.setNotificationMarkerPosition(sample.pcm.size)
                    track.setPlaybackPositionUpdateListener(object :
                        AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(t: AudioTrack?) { releaseTrack(t) }
                        override fun onPeriodicNotification(t: AudioTrack?) {}
                    })
                    track.play()
                } catch (e: Exception) {
                    releaseTrack(track)
                }
            }
        }.start()
    }

    /** Stop every currently sounding pad (panic button). */
    fun stopAll() {
        synchronized(activeTracks) {
            activeTracks.forEach { runCatching { it.stop(); it.release() } }
            activeTracks.clear()
        }
    }

    private fun releaseTrack(track: AudioTrack?) {
        track ?: return
        synchronized(activeTracks) { activeTracks.remove(track) }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    fun release() {
        stopAll()
    }
}
