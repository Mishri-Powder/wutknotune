package link.roadio.riff.indexer.android.media

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import link.roadio.riff.indexer.core.media.DecoderHintV1
import link.roadio.riff.storage.hash.nativeXxh3

/** Forms the opaque decoder-cohort key from the codec parameters observed during source admission. */
@UnstableApi
internal object AndroidDecoderHint {
    private val generator = DecoderHintV1(::nativeXxh3)

    fun from(format: Format): Long =
        generator.form(
            sampleMimeType = requireNotNull(format.sampleMimeType) { "Audio MIME is missing" },
            sampleRate = format.sampleRate.requireKnown("sample rate"),
            channelCount = format.channelCount.requireKnown("channel count"),
            initializationData = format.initializationData,
            isAdts = format.containerMimeType == ADTS_CONTAINER_MIME,
        )

    private fun Int.requireKnown(name: String): Int = takeIf { it > 0 } ?: throw IllegalArgumentException("Audio $name is missing")

    private const val ADTS_CONTAINER_MIME = "audio/aac"
}
