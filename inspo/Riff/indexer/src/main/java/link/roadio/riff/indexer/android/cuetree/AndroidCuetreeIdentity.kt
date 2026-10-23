package link.roadio.riff.indexer.android.cuetree

import android.content.Context
import link.roadio.cuegrain.CachedMediaInput
import link.roadio.cuegrain.MediaInput
import link.roadio.cuegrain.identity.fragmentIdentityComponent
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.MediaIdGenerator
import link.roadio.cuetree.Span
import link.roadio.riff.indexer.android.level1.fullMediaIdScanStatus
import link.roadio.riff.indexer.android.media.AndroidMediaIdV32Generator
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.storage.hash.nativeXxh3
import java.io.IOException

/** Media.ID V3.2 for containers; fragment IDs bind the content identity to exact clip bounds. */
class AndroidCuetreeIdentity(
    private val context: Context,
    private val status: (String) -> Unit = {},
) : MediaIdGenerator {
    private fun content(input: MediaInput) =
        AndroidMediaIdV32Generator(context, borrowedInput = input, onFullScan = { reason ->
            status(fullMediaIdScanStatus(displayName(input), reason))
        })

    private fun displayName(input: MediaInput): String {
        var source = input
        while (source is CachedMediaInput) source = source.input
        return (source as? AndroidDescriptorMedia)?.displayName ?: "Current track"
    }

    override val scheme = "riff.android.media-id-v3.2.fragment-span-v1"

    override fun high(
        input: MediaInput,
        span: Span?,
        cancellation: Cancellation,
    ): Long {
        cancellation.check()
        val value =
            try {
                content(input).generateHigh(SourceMedia(input.id, input.id), signal(cancellation))
            } catch (error: RuntimeException) {
                cancellation.check()
                throw IOException("Media identity unavailable", error)
            }
        cancellation.check()
        return if (span == null) value else fragment(0x46, value, span)
    }

    override fun low(
        input: MediaInput,
        span: Span?,
        cancellation: Cancellation,
    ): Long {
        cancellation.check()
        val value =
            try {
                content(input).generateTail(SourceMedia(input.id, input.id), signal(cancellation))
            } catch (error: RuntimeException) {
                cancellation.check()
                throw IOException("Media discriminator unavailable", error)
            }
        cancellation.check()
        return if (span == null) value else fragment(0x4c, value, span)
    }

    private fun signal(cancellation: Cancellation): CancellationSignal =
        object : CancellationSignal {
            override val cancelled: Boolean
                get() =
                    try {
                        cancellation.check()
                        false
                    } catch (_: java.util.concurrent.CancellationException) {
                        true
                    }

            override fun ensureAvailable() = cancellation.check()
        }

    override fun fragmentComponent(
        contentComponent: Long,
        span: Span,
        low: Boolean,
        cancellation: Cancellation,
    ): Long {
        cancellation.check()
        return fragmentIdentityComponent(contentComponent, span.beginUs, span.endUs, low, ::nativeXxh3)
    }

    private fun fragment(
        domain: Byte,
        contentPart: Long,
        span: Span,
    ): Long = fragmentIdentityComponent(contentPart, span.beginUs, span.endUs, domain == 0x4c.toByte(), ::nativeXxh3)
}
