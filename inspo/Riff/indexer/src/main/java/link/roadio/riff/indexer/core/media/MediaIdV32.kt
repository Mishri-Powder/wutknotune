package link.roadio.riff.indexer.core.media

import link.roadio.cuegrain.identity.MediaIdentityCancellation
import link.roadio.cuegrain.identity.MediaIdentityException
import link.roadio.riff.indexer.core.model.IndexingException
import link.roadio.riff.indexer.core.model.SourceMedia

typealias MediaIdXxh3Session = link.roadio.cuegrain.identity.MediaIdXxh3Session
typealias MediaIdXxh3Factory = link.roadio.cuegrain.identity.MediaIdXxh3Factory
typealias MediaIdV32Input = link.roadio.cuegrain.identity.MediaIdV32Input
typealias MediaIdV32AccessUnitInput = link.roadio.cuegrain.identity.MediaIdV32AccessUnitInput
typealias MediaIdV32AccessUnitCursor = link.roadio.cuegrain.identity.MediaIdV32AccessUnitCursor
typealias MediaIdV32RandomAccessInput = link.roadio.cuegrain.identity.MediaIdV32RandomAccessInput
typealias MediaIdV32FullScanReason = link.roadio.cuegrain.identity.MediaIdV32FullScanReason

// AI-INTENT: Riff supplies its source/error boundary; Cuegrain owns all identity computation.
// Authority: https://wiki.271.pics/Cuegrain/API/MediaIdentity
abstract class MediaIdV32Generator(
    xxh3Factory: MediaIdXxh3Factory,
    platform: String,
    benchmarkObserver: MediaIdBenchmarkObserver? = null,
    onFullScan: (MediaIdV32FullScanReason) -> Unit = {},
    nanoTime: () -> Long = System::nanoTime,
    maximumAccessUnitBytes: Int = link.roadio.cuegrain.identity.MediaIdV32Generator.DEFAULT_MAXIMUM_ACCESS_UNIT_BYTES,
) : link.roadio.cuegrain.identity.MediaIdV32Generator<SourceMedia>(
        xxh3Factory,
        platform,
        { it.relativePath },
        benchmarkObserver,
        onFullScan,
        nanoTime,
        maximumAccessUnitBytes,
    ) {
    override fun generateHigh(
        source: SourceMedia,
        cancellation: MediaIdentityCancellation,
    ): Long = translate { super.generateHigh(source, cancellation) }

    override fun generateTail(
        source: SourceMedia,
        cancellation: MediaIdentityCancellation,
    ): Long = translate { super.generateTail(source, cancellation) }

    private fun translate(action: () -> Long): Long =
        try {
            action()
        } catch (error: MediaIdentityException) {
            throw IndexingException(error.code, error.message ?: error.code, error)
        }

    companion object {
        const val IDENTITY_VERSION = link.roadio.cuegrain.identity.MediaIdV32Generator.IDENTITY_VERSION
        const val UNIQUE_PAYLOAD_TARGET = link.roadio.cuegrain.identity.MediaIdV32Generator.UNIQUE_PAYLOAD_TARGET
    }
}
