package link.roadio.riff.indexer.level1

import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia

/** Checks the actual source before hashing or metadata-only relationship reuse. */
fun interface Level1SourceAdmission {
    fun check(
        source: SourceMedia,
        cancellation: CancellationSignal,
    ): Level1ScanResult<Long>
}
