package link.roadio.riff.indexer.core

// Cancellation is checked at Android I/O boundaries and between scan units.
// Authority: https://wiki.271.pics/Riff/LATEST/indexer-staging
// AI-INTENT: Keep this interface Android-neutral so deterministic unit tests can cancel scans.
interface CancellationSignal : link.roadio.cuegrain.identity.MediaIdentityCancellation {
    override val cancelled: Boolean

    override fun ensureAvailable()
}
