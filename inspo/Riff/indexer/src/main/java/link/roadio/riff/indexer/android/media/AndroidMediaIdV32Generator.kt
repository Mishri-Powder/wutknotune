package link.roadio.riff.indexer.android.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import link.roadio.cuegrain.CachedMediaInput
import link.roadio.cuegrain.MediaInput
import link.roadio.riff.indexer.android.cuetree.AndroidDescriptorMedia
import link.roadio.riff.indexer.core.media.MediaIdBenchmarkObserver
import link.roadio.riff.indexer.core.media.MediaIdV32AccessUnitCursor
import link.roadio.riff.indexer.core.media.MediaIdV32AccessUnitInput
import link.roadio.riff.indexer.core.media.MediaIdV32FullScanReason
import link.roadio.riff.indexer.core.media.MediaIdV32Generator
import link.roadio.riff.indexer.core.media.MediaIdV32Input
import link.roadio.riff.indexer.core.media.MediaIdV32RandomAccessInput
import link.roadio.riff.indexer.core.media.MediaIdXxh3Factory
import link.roadio.riff.indexer.core.media.MediaIdXxh3Session
import link.roadio.riff.indexer.core.model.IndexingException
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.storage.hash.NativeXxh3Factory
import link.roadio.riff.storage.hash.nativeXxh3
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/** Android acquisition adapter for the sole MDIA-ID-V3.2 implementation. */
class AndroidMediaIdV32Generator(
    private val context: Context,
    benchmarkObserver: MediaIdBenchmarkObserver? = null,
    onFullScan: (MediaIdV32FullScanReason) -> Unit = {},
    private val borrowedInput: MediaInput? = null,
) : MediaIdV32Generator(
        xxh3Factory = MediaIdXxh3Factory(::NativeSession),
        platform = "android",
        benchmarkObserver = benchmarkObserver,
        onFullScan = onFullScan,
    ) {
    override fun openInput(source: SourceMedia): MediaIdV32Input {
        val random = runCatching { openRandomAccess(source.locator) }.getOrNull()
        if (random != null) {
            try {
                if (random.isRiffWave() && supportsWaveRaw(random)) return random
            } catch (error: Throwable) {
                random.close()
                throw error
            }
            random.close()
        }
        return AndroidAccessUnitInput(context, source.locator, borrowedInput)
    }

    private fun openRandomAccess(locator: String): MediaIdV32RandomAccessInput {
        borrowedInput?.let { input ->
            return object : MediaIdV32RandomAccessInput {
                override val size = input.length

                override fun readFully(
                    offset: Long,
                    bytes: ByteArray,
                    destinationOffset: Int,
                    length: Int,
                ) {
                    require(destinationOffset >= 0 && length >= 0 && destinationOffset <= bytes.size - length)
                    input.read(offset, length).copyInto(bytes, destinationOffset)
                }

                override fun close() = Unit
            }
        }
        if (!locator.contains("://") && !locator.startsWith("content:")) {
            val channel = FileChannel.open(Paths.get(locator), StandardOpenOption.READ)
            return AndroidRandomAccessInput(channel, 0L, channel.size(), channel)
        }
        val descriptor =
            context.contentResolver.openAssetFileDescriptor(Uri.parse(locator), "r")
                ?: throw IndexingException("OPEN_FAILED", "Unable to open media descriptor")
        val stream = FileInputStream(descriptor.fileDescriptor)
        val length = descriptor.declaredLength.takeIf { it >= 0L } ?: (stream.channel.size() - descriptor.startOffset)
        return AndroidRandomAccessInput(stream.channel, descriptor.startOffset, length, stream, descriptor)
    }

    private class AndroidRandomAccessInput(
        private val channel: FileChannel,
        private val baseOffset: Long,
        override val size: Long,
        private vararg val resources: AutoCloseable,
    ) : MediaIdV32RandomAccessInput {
        override fun readFully(
            offset: Long,
            bytes: ByteArray,
            destinationOffset: Int,
            length: Int,
        ) {
            require(offset >= 0L && length >= 0 && offset <= size - length)
            require(destinationOffset >= 0 && destinationOffset <= bytes.size - length)
            val buffer = ByteBuffer.wrap(bytes, destinationOffset, length)
            var position = baseOffset + offset
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, position)
                if (read <= 0) throw IndexingException("TRUNCATED_MEDIA", "Media read ended at offset $offset")
                position += read
            }
        }

        override fun close() {
            resources.forEach { runCatching { it.close() } }
        }
    }

    private class AndroidAccessUnitInput(
        private val context: Context,
        private val locator: String,
        private val borrowed: MediaInput?,
    ) : MediaIdV32AccessUnitInput {
        private val audioTrack: Int
        private var initialExtractor: MediaExtractor? = null
        override val channelCount: Int?
        override val durationUs: Long?

        init {
            val extractor = configuredExtractor(context, locator, borrowed)
            try {
                audioTrack = extractor.firstAudioTrack()
                val format = extractor.getTrackFormat(audioTrack)
                channelCount =
                    format
                        .takeIf { it.containsKey(MediaFormat.KEY_CHANNEL_COUNT) }
                        ?.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        ?.takeIf { it > 0 }
                durationUs =
                    format
                        .takeIf { it.containsKey(MediaFormat.KEY_DURATION) }
                        ?.getLong(MediaFormat.KEY_DURATION)
                        ?.takeIf { it > 0L }
                initialExtractor = extractor
            } finally {
                if (initialExtractor !== extractor) extractor.release()
            }
        }

        override fun openCursor(sourceTimeUs: Long?): MediaIdV32AccessUnitCursor {
            val extractor = initialExtractor?.also { initialExtractor = null } ?: configuredExtractor(context, locator, borrowed)
            try {
                val currentTrack = extractor.firstAudioTrack()
                if (currentTrack != audioTrack) {
                    throw IndexingException("SOURCE_CHANGED", "Selected audio track changed during Media.ID formation")
                }
                extractor.selectTrack(currentTrack)
                if (sourceTimeUs != null) {
                    try {
                        extractor.seekTo(sourceTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    } catch (error: RuntimeException) {
                        throw IndexingException("SEEK_UNAVAILABLE", "Media source cannot seek for Media.ID", error)
                    }
                }
                return AndroidAccessUnitCursor(extractor)
            } catch (error: Throwable) {
                extractor.release()
                throw error
            }
        }

        override fun close() {
            initialExtractor?.release()
            initialExtractor = null
        }
    }

    private class AndroidAccessUnitCursor(
        private val extractor: MediaExtractor,
    ) : MediaIdV32AccessUnitCursor {
        private var first = true
        private var positioned = false

        override fun advance(): Boolean {
            positioned =
                if (first) {
                    first = false
                    extractor.sampleTrackIndex >= 0
                } else {
                    extractor.advance()
                }
            return positioned
        }

        override val presentationTimeUs: Long?
            get() {
                check(positioned) { "Cursor is not positioned on an access unit" }
                return extractor.sampleTime.takeIf { it >= 0L }
            }

        override val encodedSize: Int
            get() {
                check(positioned) { "Cursor is not positioned on an access unit" }
                val size = extractor.sampleSize
                if (size > Int.MAX_VALUE) {
                    throw IndexingException("INVALID_AUDIO_PACKET", "Encoded access unit exceeds Int.MAX_VALUE")
                }
                return size.toInt()
            }

        override fun readEncodedBytes(): ByteArray {
            val expected = encodedSize
            val bytes = ByteArray(expected)
            val actual = extractor.readSampleData(ByteBuffer.wrap(bytes), 0)
            if (actual != expected) {
                throw IndexingException(
                    "TRUNCATED_AUDIO_PACKET",
                    "MediaExtractor returned $actual bytes for an access unit of $expected bytes",
                )
            }
            return bytes
        }

        override fun close() = extractor.release()
    }

    private class NativeSession : MediaIdXxh3Session {
        private val stream = NativeXxh3Factory.create()

        override fun update(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) = stream.update(bytes, offset, length)

        override fun hash(bytes: ByteArray): Long = nativeXxh3(bytes)

        override fun digest(): Long = stream.digest()

        override fun close() = stream.close()
    }

    private companion object {
        fun MediaIdV32RandomAccessInput.isRiffWave(): Boolean {
            if (size < 12L) return false
            val bytes = ByteArray(12)
            readFully(0L, bytes)
            return bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                bytes.copyOfRange(8, 12).decodeToString() == "WAVE"
        }

        fun configuredExtractor(
            context: Context,
            locator: String,
            borrowed: MediaInput? = null,
        ): MediaExtractor =
            MediaExtractor().also { extractor ->
                try {
                    var original = borrowed
                    while (original is CachedMediaInput) original = original.input
                    if (original is AndroidDescriptorMedia) {
                        extractor.setDataSource(original.descriptor, original.offset, original.length)
                    } else if (locator.contains("://") || locator.startsWith("content:")) {
                        extractor.setDataSource(context, Uri.parse(locator), emptyMap())
                    } else {
                        extractor.setDataSource(locator)
                    }
                } catch (error: Throwable) {
                    extractor.release()
                    throw error
                }
            }

        fun MediaExtractor.firstAudioTrack(): Int =
            (0 until trackCount).firstOrNull { index ->
                getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IndexingException("NO_AUDIO_TRACK", "Media source has no audio track")
    }
}
