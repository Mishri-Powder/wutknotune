package link.roadio.riff.indexer.android.process

import link.roadio.riff.indexer.core.ipc.INDEXER_STATUS_MAX_CHARS
import link.roadio.riff.indexer.core.pipeline.PipelineProgressRates
import link.roadio.riff.indexer.core.pipeline.PipelineProgressUnit
import java.text.BreakIterator
import java.util.Locale

internal enum class IndexerProgressStage(
    val label: String,
    val unit: PipelineProgressUnit,
    val rateUnit: String,
    val countUnit: String,
) {
    DISCOVERY("Checking media folders", PipelineProgressUnit.FOLDERS, "folders", "checked"),
    ACQUISITION("Updating media index", PipelineProgressUnit.MEDIA, "media", "checked"),
    ANALYSIS("Analyzing audio", PipelineProgressUnit.TRACKS, "tracks", "tracks"),
}

/** One display-ready formatter for cumulative counts, publication-period speed and bounded detail. */
internal class IndexerProgressReporter(
    private val rates: PipelineProgressRates,
) {
    private val totals = arrayOfNulls<Long>(IndexerProgressStage.entries.size)

    @Synchronized
    fun observe(
        stage: IndexerProgressStage,
        completed: Long,
        total: Long? = null,
    ) {
        require(total == null || total >= 0L)
        require(stage == IndexerProgressStage.DISCOVERY || total != null)
        rates.record(stage.unit, completed)
        totals[stage.ordinal] = total
    }

    @Synchronized
    fun text(
        stage: IndexerProgressStage,
        detail: String? = null,
    ): String {
        val sample = rates.sample(stage.unit)
        val text =
            formatIndexerProgress(
                stage.label,
                sample.completed,
                totals[stage.ordinal],
                sample.perSecond,
                stage.rateUnit,
                stage.countUnit,
            )
        if (detail.isNullOrBlank()) return boundedStatusPart(text, INDEXER_STATUS_MAX_CHARS)
        val prefix = "$text | "
        if (prefix.length >= INDEXER_STATUS_MAX_CHARS) return boundedStatusPart(text, INDEXER_STATUS_MAX_CHARS)
        return prefix + boundedStatusPart(detail, INDEXER_STATUS_MAX_CHARS - prefix.length)
    }
}

internal fun formatIndexerProgress(
    stage: String,
    completed: Long,
    total: Long?,
    ratePerSecond: Double,
    unit: String,
    countUnit: String = unit,
): String {
    require(stage.isNotBlank())
    require(completed >= 0L)
    require(total == null || total >= 0L)
    require(ratePerSecond.isFinite() && ratePerSecond >= 0.0)
    require(unit.isNotBlank())
    return if (total != null) {
        String.format(Locale.ROOT, "%s: %d / %d %s | %.1f %s/s", stage, completed, total, countUnit, ratePerSecond, unit)
    } else {
        String.format(Locale.ROOT, "%s: %d checked | %.1f %s/s", stage, completed, ratePerSecond, unit)
    }
}

/** Counts/rate take priority; a long Unicode filename cannot exceed the Binder contract. */
private fun boundedStatusPart(
    text: String,
    limit: Int,
): String {
    require(limit > 0)
    if (text.length <= limit) return text
    val boundary = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }.preceding(limit)
    return text.substring(0, boundary.coerceAtLeast(0)) + "…"
}
