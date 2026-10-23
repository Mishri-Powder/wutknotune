package link.roadio.riff.indexer.level1

import link.roadio.riff.indexer.core.replaygain.ReplayGainSource

sealed interface Level1ScanResult<out T> {
    data class Success<T>(
        val value: T,
    ) : Level1ScanResult<T>

    data object SourceUnavailable : Level1ScanResult<Nothing>

    data object ContentScanFailed : Level1ScanResult<Nothing>
}

data class Level1ReplayGainMetadata(
    val trackGainQ8: Short? = null,
    val trackPeak: Float? = null,
    val albumGainQ8: Short? = null,
    val albumPeak: Float? = null,
    val source: ReplayGainSource = ReplayGainSource.NONE,
)

fun interface GenreMatcher {
    val revision: String get() = "NONE"

    /** Returns GNRE Genre.id values for every matched source genre. */
    fun match(canonicalGenre: String): Set<Long>

    companion object {
        val NONE = GenreMatcher { emptySet() }
    }
}
