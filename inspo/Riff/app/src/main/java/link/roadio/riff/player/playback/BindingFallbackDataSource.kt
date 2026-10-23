@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

internal data class BindingDataSpec(
    val dataSpec: DataSpec,
    val bindingId: Long? = null,
    val opened: () -> Unit = {},
)

/** Retry only failed opens, before a decoder consumes bytes; never splice streams after a read. */
internal class BindingFallbackDataSource(
    private val factory: DataSource.Factory,
    private val resolve: (DataSpec, Set<Long>) -> BindingDataSpec,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        active?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null)
        val rejected = mutableSetOf<Long>()
        var failure: IOException? = null
        while (true) {
            if (Thread.currentThread().isInterrupted) throw IOException("Source opening cancelled", failure)
            val candidate =
                try {
                    resolve(dataSpec, rejected)
                } catch (error: IOException) {
                    failure?.let(error::addSuppressed)
                    throw error
                }
            if (candidate.bindingId != null && candidate.bindingId in rejected) {
                throw IOException("Source resolver repeated an exhausted binding", failure)
            }
            val source = factory.createDataSource()
            listeners.forEach(source::addTransferListener)
            try {
                val length = source.open(candidate.dataSpec)
                candidate.opened()
                active = source
                return length
            } catch (error: IOException) {
                runCatching { source.close() }
                failure = error
                val binding = candidate.bindingId ?: throw error
                rejected += binding
            } catch (error: Throwable) {
                runCatching { source.close() }
                throw error
            }
        }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = checkNotNull(active).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

    override fun close() {
        val source = active
        active = null
        source?.close()
    }
}
