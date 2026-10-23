package link.roadio.riff.indexer.android.cuetree

import link.roadio.riff.config.ProjectCatalogVocabulary
import link.roadio.riff.config.ProjectLanguages
import link.roadio.riff.indexer.core.tags.genre.GenreParser
import link.roadio.riff.storage.hash.nativeXxh3

/** Dependency interpretation changes require reacquiring the complete factual and analysis cohort. */
internal object AndroidCuetreeMetadataRevision {
    // AI-INTENT: Bump this for Riff/Cuegrain interpretation changes outside the list and GNRE
    // revisions, including decoder/native updates. Reject old facts before Player receives a catalog;
    // the host performs one full AU+IA recreation, without retaining old metadata or analysis.
    // https://wiki.271.pics/Riff/LATEST/genre-processing#gp-5-revision-and-conformance
    private const val PROCESSING = "RIFF-METADATA-V5"
    private val vocabularyFingerprint =
        java.lang.Long.toUnsignedString(
            nativeXxh3(ProjectCatalogVocabulary.canonical.encodeToByteArray()),
            16,
        )
    val current: String = "$PROCESSING/${GenreParser.REVISION}/GNRE-${ProjectLanguages.gnreGeneration}/VOC-$vocabularyFingerprint"
}
