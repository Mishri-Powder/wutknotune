package link.roadio.riff.player.library

import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot

internal fun isCuetreeRebuildMask(mode: Int): Boolean = mode in 1..3

/** Optional analysis must never prevent admission of a valid library cohort. */
internal fun readOptionalCuetreeAnalysis(read: () -> AnalysisSnapshot?): AnalysisSnapshot? =
    try {
        read()?.validate()
    } catch (_: Exception) {
        null
    }
