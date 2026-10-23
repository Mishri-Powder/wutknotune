package link.roadio.riff.indexer.core.pipeline

/** A single monotonic deadline across L0/L1/L2 and every artifact stream. No state is persisted. */
internal class PipelinePublicationClock(
    private val clock: () -> Long,
    private val startedAt: Long = clock(),
    private val onCommitted: (Long) -> Unit = {},
) {
    private var lastSuccessfulAt: Long? = null

    @Synchronized
    fun due(): Boolean {
        val previous = lastSuccessfulAt
        return clock() - (previous ?: startedAt) >= if (previous == null) 30_000L else 60_000L
    }

    @Synchronized
    fun committed() {
        val committedAt = clock()
        lastSuccessfulAt = committedAt
        onCommitted(committedAt)
    }
}
