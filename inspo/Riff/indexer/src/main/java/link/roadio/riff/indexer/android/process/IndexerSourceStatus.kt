package link.roadio.riff.indexer.android.process

/** Shared bounded filename details for identity, loudness and source-failure diagnostics. */
internal fun sourceFileStatus(
    message: String,
    displayName: String,
): String {
    val filename =
        displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filterNot(Char::isISOControl)
            .replace('|', ' ')
            .trim()
            .ifBlank { "Current file" }
    val end = filename.offsetByCodePoints(0, minOf(88, filename.codePointCount(0, filename.length)))
    return message + ": " + filename.substring(0, end)
}

/** Source detail never owns counters, timing or publication; one progress reporter formats every line. */
internal class IndexerSourceStatus(
    private val reporter: IndexerProgressReporter,
    private val emit: (String) -> Unit,
) {
    private data class Detail(
        val stage: IndexerProgressStage,
        val text: String,
    )

    private val gate = Any()
    private var suspect: Detail? = null
    private var failed: Detail? = null

    fun beginSuspect(
        stage: IndexerProgressStage,
        name: String,
    ) {
        val detail = Detail(stage, sourceFileStatus("Suspect file", name))
        synchronized(gate) { suspect = detail }
        emit(reporter.text(stage, detail.text))
    }

    fun endSuspect(
        stage: IndexerProgressStage,
        name: String,
    ) {
        val detail = Detail(stage, sourceFileStatus("Suspect file", name))
        synchronized(gate) { if (suspect == detail) suspect = null }
    }

    fun failed(
        stage: IndexerProgressStage,
        name: String,
    ) {
        val detail = Detail(stage, sourceFileStatus("FERR file", name))
        synchronized(gate) { failed = detail }
        emit(reporter.text(stage, detail.text))
    }

    fun text(
        stage: IndexerProgressStage,
        detail: String? = null,
    ): String {
        val selected =
            synchronized(gate) {
                val pending = failed
                failed = null
                detail ?: pending?.takeIf { it.stage == stage }?.text ?: suspect?.takeIf { it.stage == stage }?.text
            }
        return reporter.text(stage, selected)
    }
}
