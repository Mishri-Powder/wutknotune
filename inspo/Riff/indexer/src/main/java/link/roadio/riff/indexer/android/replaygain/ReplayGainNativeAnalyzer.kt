package link.roadio.riff.indexer.android.replaygain

import android.media.AudioFormat
import java.nio.ByteBuffer

internal class ReplayGainNativeAnalyzer(
    private val channels: Int,
    sampleRate: Int,
) : AutoCloseable {
    private var handle = nativeCreate(channels, sampleRate).also { check(it != 0L) }

    fun add(samples: FloatArray) {
        check(handle != 0L && samples.size % channels == 0)
        nativeAdd(handle, samples, samples.size / channels)
    }

    fun addPcm(
        buffer: ByteBuffer,
        byteOffset: Int,
        frames: Int,
        pcmEncoding: Int,
    ) {
        check(handle != 0L && buffer.isDirect)
        require(byteOffset >= 0 && frames >= 0)
        val byteCount = frames.toLong() * channels * pcmBytesPerSample(pcmEncoding)
        require(byteOffset.toLong() + byteCount <= buffer.capacity())
        nativeAddPcm(handle, buffer, byteOffset, frames, pcmEncoding)
    }

    fun finish(): Double = nativeFinish(handle)

    internal fun handle(): Long = handle.also { check(it != 0L) }

    override fun close() {
        if (handle != 0L) nativeDestroy(handle)
        handle = 0L
    }

    private external fun nativeCreate(
        channels: Int,
        sampleRate: Int,
    ): Long

    private external fun nativeAdd(
        handle: Long,
        samples: FloatArray,
        frames: Int,
    )

    private external fun nativeAddPcm(
        handle: Long,
        buffer: ByteBuffer,
        byteOffset: Int,
        frames: Int,
        pcmEncoding: Int,
    )

    private external fun nativeFinish(handle: Long): Double

    private external fun nativeFinishMultiple(handles: LongArray): Double

    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("riff_replay_gain")
        }

        fun finishMultiple(analyzers: List<ReplayGainNativeAnalyzer>): Double {
            require(analyzers.isNotEmpty())
            return analyzers.first().nativeFinishMultiple(analyzers.map { it.handle() }.toLongArray())
        }

        fun pcmBytesPerSample(pcmEncoding: Int): Int =
            when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_16BIT -> 2
                else -> throw IllegalArgumentException("Unsupported decoder PCM encoding: $pcmEncoding")
            }
    }
}
