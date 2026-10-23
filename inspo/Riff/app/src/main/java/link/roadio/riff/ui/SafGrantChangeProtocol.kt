package link.roadio.riff.ui

/**
 * Applies one SAF grant mutation across the Player/Indexer barrier.
 *
 * The old grants remain authoritative until an already-bound Indexer has drained its
 * current pipeline. Any attempted mutation, including a partial failure, requires one
 * full post-barrier pipeline because Android persisted-permission calls are not atomic.
 */
internal fun runSafGrantChangeBarrier(
    indexerWasBound: Boolean,
    quiesceIndexer: () -> Boolean,
    mutateGrants: () -> Unit,
    resumeIndexer: (requestFullPipeline: Boolean) -> Boolean,
) {
    if (indexerWasBound && !quiesceIndexer()) {
        resumeIndexer(false)
        error("Indexer did not finish the current library update")
    }
    var mutationAttempted = false
    try {
        mutationAttempted = true
        mutateGrants()
        if (indexerWasBound) check(resumeIndexer(true)) { "Indexer is unavailable" }
    } catch (error: Throwable) {
        if (indexerWasBound) resumeIndexer(mutationAttempted)
        throw error
    }
}
