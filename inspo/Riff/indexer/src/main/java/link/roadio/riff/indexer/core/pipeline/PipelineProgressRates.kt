package link.roadio.riff.indexer.core.pipeline

internal enum class PipelineProgressUnit { FOLDERS, MEDIA, TRACKS }

internal data class PipelineProgressRate(
    val completed: Long,
    val periodCompleted: Long,
    val elapsedMillis: Long,
) {
    val perSecond: Double
        get() = if (elapsedMillis == 0L) 0.0 else periodCompleted * 1_000.0 / elapsedMillis
}

internal data class PipelineProgressPeriod(
    val elapsedMillis: Long,
    val completed: Map<PipelineProgressUnit, Long>,
)

/** Transient counters share the successful publication boundary without resetting display totals. */
internal class PipelineProgressRates(
    private val clock: () -> Long,
    startedAt: Long = clock(),
) {
    private val completed = LongArray(PipelineProgressUnit.entries.size)
    private val baseline = LongArray(completed.size)
    private var periodStartedAt = startedAt

    @Synchronized
    fun record(
        unit: PipelineProgressUnit,
        count: Long,
    ) {
        require(count >= completed[unit.ordinal]) { "Pipeline progress must be cumulative" }
        completed[unit.ordinal] = count
    }

    @Synchronized
    fun sample(unit: PipelineProgressUnit): PipelineProgressRate =
        PipelineProgressRate(
            completed[unit.ordinal],
            completed[unit.ordinal] - baseline[unit.ordinal],
            (clock() - periodStartedAt).coerceAtLeast(0L),
        )

    @Synchronized
    fun committed(at: Long): PipelineProgressPeriod {
        require(at >= periodStartedAt)
        val period =
            PipelineProgressPeriod(
                at - periodStartedAt,
                PipelineProgressUnit.entries.associateWith { completed[it.ordinal] - baseline[it.ordinal] },
            )
        // AI-INTENT: Capture live consumed work at successful commit, including work completed while
        // an asynchronous writer was in flight. Status updates and stage changes do not reset it.
        // https://wiki.271.pics/Riff/LATEST/indexer-staging#progress-and-period-rates
        completed.copyInto(baseline)
        periodStartedAt = at
        return period
    }
}
