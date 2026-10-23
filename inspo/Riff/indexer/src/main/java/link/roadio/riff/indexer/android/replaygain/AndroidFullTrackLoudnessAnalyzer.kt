package link.roadio.riff.indexer.android.replaygain

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import link.roadio.cuetree.Span
import link.roadio.riff.indexer.android.level2.AsyncOutput
import link.roadio.riff.indexer.android.level2.CodecCallbackSession
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.analysis.pcmSpanFrames
import link.roadio.riff.indexer.core.model.SourceMedia
import java.io.IOException
import java.util.concurrent.CancellationException

internal class AndroidFullTrackLoudnessAnalyzer(
    private val context: Context,
) {
    fun analyze(
        source: SourceMedia,
        cancellation: CancellationSignal,
        span: Span? = null,
    ): Double {
        val extractor = MediaExtractor()
        try {
            if (source.locator.contains("://")) {
                extractor.setDataSource(context, Uri.parse(source.locator), emptyMap())
            } else {
                extractor.setDataSource(source.locator)
            }
            val track =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: throw IllegalArgumentException("Media source has no audio track")
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val beginUs = span?.beginUs ?: 0L
            if (beginUs > 0L) extractor.seekTo(beginUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codecName = requireNotNull(MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format))
            return CodecCallbackSession(codecName, format).use { session -> drain(extractor, session, cancellation, span) }
        } finally {
            extractor.release()
        }
    }

    private fun drain(
        extractor: MediaExtractor,
        session: CodecCallbackSession,
        cancellation: CancellationSignal,
        span: Span?,
    ): Double {
        val codec = session.codec
        var analyzer: ReplayGainNativeAnalyzer? = null
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var channels = 0
        var rate = 0
        val beginUs = span?.beginUs ?: 0L
        val firstAccessUnitUs = extractor.sampleTime
        require(firstAccessUnitUs >= 0L) { "Integrated loudness is unavailable: no audio in requested span" }
        var decodedFrames = 0L
        var inputEnded = false
        var outputEnded = false
        var lastProgressAt = SystemClock.elapsedRealtime()
        try {
            while (!outputEnded) {
                cancellation.ensureAvailable()
                if (Thread.currentThread().isInterrupted) throw CancellationException("Loudness analysis cancelled")
                var progressed = false
                if (!inputEnded) {
                    val inputIndex = session.inputIndices.poll()
                    if (inputIndex != null) {
                        val input = requireNotNull(codec.getInputBuffer(inputIndex)).apply { clear() }
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0 || span?.endUs?.let { extractor.sampleTime >= it } == true) {
                            codec.queueInputBuffer(inputIndex, 0, 0, maxOf(0L, extractor.sampleTime), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                        progressed = true
                    }
                }
                when (val event = session.outputEvents.poll()) {
                    is AsyncOutput.Format -> {
                        require(decodedFrames == 0L) { "Decoder PCM format changed during loudness analysis" }
                        channels = event.format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        rate = event.format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        encoding =
                            if (event.format.containsKey(
                                    MediaFormat.KEY_PCM_ENCODING,
                                )
                            ) {
                                event.format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                        analyzer?.close()
                        analyzer = ReplayGainNativeAnalyzer(channels, rate)
                        progressed = true
                    }

                    is AsyncOutput.Buffer -> {
                        try {
                            for (info in event.infos) {
                                if (info.size > 0) {
                                    val buffer = requireNotNull(codec.getOutputBuffer(event.index))
                                    require(channels > 0 && rate > 0)
                                    val bytesPerFrame = ReplayGainNativeAnalyzer.pcmBytesPerSample(encoding) * channels
                                    require(info.size % bytesPerFrame == 0) { "Decoder PCM buffer is not frame-aligned" }
                                    val frames = info.size / bytesPerFrame
                                    val range = pcmSpanFrames(firstAccessUnitUs, beginUs, span?.endUs, rate, decodedFrames, frames)
                                    if (!range.isEmpty()) {
                                        requireNotNull(analyzer).addPcm(
                                            buffer,
                                            info.offset + range.first * bytesPerFrame,
                                            range.last - range.first + 1,
                                            encoding,
                                        )
                                    }
                                    decodedFrames += frames
                                }
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                            }
                        } finally {
                            codec.releaseOutputBuffer(event.index, false)
                        }
                        progressed = true
                    }

                    is AsyncOutput.Error -> {
                        throw event.error
                    }

                    null -> {
                        Unit
                    }
                }
                if (progressed) {
                    lastProgressAt = SystemClock.elapsedRealtime()
                } else {
                    if (SystemClock.elapsedRealtime() - lastProgressAt >=
                        30_000L
                    ) {
                        throw IOException("Loudness decoder stopped producing buffers")
                    }
                    Thread.sleep(1L)
                }
            }
            return requireNotNull(analyzer).finish()
        } finally {
            analyzer?.close()
        }
    }
}
