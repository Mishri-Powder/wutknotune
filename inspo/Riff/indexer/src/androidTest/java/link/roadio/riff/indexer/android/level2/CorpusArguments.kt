package link.roadio.riff.indexer.android.level2

import android.os.Bundle
import org.junit.Assume.assumeTrue

/** Corpus experiments are opt-in; ordinary connected tests must not inspect a user's library. */
internal fun configuredCorpusVolume(
    arguments: Bundle,
    key: String,
): String {
    val volume = arguments.getString(key)
    assumeTrue("Corpus experiment requires explicit instrumentation argument $key", volume != null)
    return requireNotNull(volume)
}
