package link.roadio.riff.indexer.android.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.media.MediaIdV32AccessUnitCursor
import link.roadio.riff.indexer.core.media.MediaIdV32AccessUnitInput
import link.roadio.riff.indexer.core.media.MediaIdV32Generator
import link.roadio.riff.indexer.core.media.MediaIdV32Input
import link.roadio.riff.indexer.core.media.MediaIdXxh3Factory
import link.roadio.riff.indexer.core.media.MediaIdXxh3Session
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.storage.hash.NativeXxh3Factory
import link.roadio.riff.storage.hash.nativeXxh3
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class MediaIdV32GoldenTest {
    @Test
    fun headTailMatchesIndependentXxhashGoldenVector() {
        val packets =
            List(600) { index ->
                ByteBuffer.allocate(1024).putInt(index).array()
            }
        val input = GoldenInput(packets)
        val generator =
            object : MediaIdV32Generator(MediaIdXxh3Factory(::NativeSession), "android-golden") {
                override fun openInput(source: SourceMedia): MediaIdV32Input = input
            }

        val result = generator.generateTail(SourceMedia("golden", "golden.media"), NeverCancelled)

        assertEquals(0xb56718a04e73b81cUL.toLong(), result)
    }

    private class GoldenInput(
        private val packets: List<ByteArray>,
    ) : MediaIdV32AccessUnitInput {
        override val channelCount = 2
        override val durationUs = 600_000_000L

        override fun openCursor(sourceTimeUs: Long?): MediaIdV32AccessUnitCursor {
            val targetIndex = sourceTimeUs?.div(1_000_000L)?.toInt() ?: 0
            return GoldenCursor(packets, (targetIndex - 1).coerceAtLeast(0))
        }

        override fun close() = Unit
    }

    private class GoldenCursor(
        private val packets: List<ByteArray>,
        start: Int,
    ) : MediaIdV32AccessUnitCursor {
        private var index = start - 1

        override fun advance(): Boolean {
            index += 1
            return index < packets.size
        }

        override val presentationTimeUs: Long
            get() = index * 1_000_000L

        override val encodedSize: Int
            get() = packets[index].size

        override fun readEncodedBytes(): ByteArray = packets[index].copyOf()

        override fun close() = Unit
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

    private object NeverCancelled : CancellationSignal {
        override val cancelled = false

        override fun ensureAvailable() = Unit
    }
}
