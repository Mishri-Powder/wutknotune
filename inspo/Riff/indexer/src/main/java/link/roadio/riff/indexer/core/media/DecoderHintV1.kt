package link.roadio.riff.indexer.core.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Versioned XXH3 input framing for one encoded-audio decoder cohort. */
class DecoderHintV1(
    private val xxh3: (ByteArray) -> Long,
) {
    fun form(
        sampleMimeType: String,
        sampleRate: Int,
        channelCount: Int,
        initializationData: List<ByteArray>,
        isAdts: Boolean,
    ): Long {
        require(sampleMimeType.startsWith("audio/")) { "Decoder MIME must be audio" }
        require(sampleRate > 0) { "Decoder sample rate must be positive" }
        require(channelCount > 0) { "Decoder channel count must be positive" }
        val mime = sampleMimeType.encodeToByteArray()
        val capacity =
            DOMAIN.size +
                Int.SIZE_BYTES + mime.size +
                Int.SIZE_BYTES * 3 + 1 +
                initializationData.sumOf { Int.SIZE_BYTES + it.size }
        val input = ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN)
        input.put(DOMAIN)
        input.putInt(mime.size).put(mime)
        input.putInt(sampleRate)
        input.putInt(channelCount)
        input.put((if (isAdts) 1 else 0).toByte())
        input.putInt(initializationData.size)
        initializationData.forEach { bytes -> input.putInt(bytes.size).put(bytes) }
        check(!input.hasRemaining())
        return xxh3(input.array())
    }

    private companion object {
        val DOMAIN = "RIFF-DECODER-HINT-V1".encodeToByteArray()
    }
}
