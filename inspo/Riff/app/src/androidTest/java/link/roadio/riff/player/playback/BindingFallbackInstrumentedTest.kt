@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class BindingFallbackInstrumentedTest {
    private class Source(
        val open: (DataSpec) -> Long,
        val closed: () -> Unit,
    ) : DataSource {
        override fun addTransferListener(listener: TransferListener) = Unit

        override fun open(spec: DataSpec) = open.invoke(spec)

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            buffer[offset] = 42
            return 1
        }

        override fun getUri(): Uri = Uri.parse("content://fixture/audio")

        override fun close() = closed()
    }

    @Test fun failedOpenUsesHealthyCopyAndOnlyReportsTheOpenedBinding() {
        val opened = mutableListOf<Long>()
        val attempts = mutableListOf<String?>()
        var closes = 0
        val factory =
            DataSource.Factory {
                Source({ spec ->
                    attempts += spec.uri.lastPathSegment
                    if (spec.uri.lastPathSegment == "1") throw IOException("Provider denied open")
                    1L
                }, { closes++ })
            }
        BindingFallbackDataSource(factory) { original, rejected ->
            val id = listOf(1L, 2L).firstOrNull { it !in rejected } ?: throw IOException("Exhausted")
            BindingDataSpec(original.withUri(Uri.parse("content://fixture/$id")), id) { opened += id }
        }.let { source ->
            assertEquals(1L, source.open(DataSpec(Uri.parse("riff://1/1"))))
            assertEquals(listOf("1", "2"), attempts)
            assertEquals(listOf(2L), opened)
            val bytes = ByteArray(1)
            assertEquals(1, source.read(bytes, 0, 1))
            assertEquals(42.toByte(), bytes[0])
            source.close()
        }
        assertEquals(2, closes)
    }

    @Test fun exhaustedCopiesAreAttemptedOnceAndClosed() {
        var opens = 0
        var closes = 0
        val source =
            BindingFallbackDataSource(
                DataSource.Factory {
                    Source({
                        opens++
                        throw IOException("Failure")
                    }, { closes++ })
                },
            ) { spec, rejected ->
                val id = listOf(1L, 2L).firstOrNull { it !in rejected } ?: throw IOException("Exhausted")
                BindingDataSpec(spec, id)
            }
        assertThrows(IOException::class.java) { source.open(DataSpec(Uri.parse("riff://1/1"))) }
        source.close()
        assertEquals(2, opens)
        assertEquals(2, closes)
    }

    @Test fun queueReplacementStopsFallbackBeforeAnotherOpen() {
        var current = true
        var opens = 0
        val source =
            BindingFallbackDataSource(
                DataSource.Factory {
                    Source({
                        opens++
                        current = false
                        throw IOException("Failure")
                    }, {})
                },
            ) { spec, _ ->
                if (!current) throw IOException("Obsolete queue")
                BindingDataSpec(spec, 1L)
            }
        assertThrows(IOException::class.java) { source.open(DataSpec(Uri.parse("riff://1/1"))) }
        assertEquals(1, opens)
    }

    @Test fun repeatedResolverCandidateCannotLoop() {
        var opens = 0
        val source =
            BindingFallbackDataSource(
                DataSource.Factory {
                    Source({
                        opens++
                        throw IOException("Failure")
                    }, {})
                },
            ) { spec, _ -> BindingDataSpec(spec, 1L) }
        assertThrows(IOException::class.java) { source.open(DataSpec(Uri.parse("riff://1/1"))) }
        assertEquals(1, opens)
    }
}
