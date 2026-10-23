package link.roadio.riff.indexer.core.model

// Minimal source identity shared by Android media readers and normalization code.
data class SourceMedia(
    val locator: String,
    val relativePath: String,
)

// A stable code lets the Indexer report failures without exposing Android exceptions.
// Authority: https://wiki.271.pics/Riff/LATEST/indexer-library-lifecycle
// AI-INTENT: Add codes at the failure boundary; do not rebuild a generic repository layer here.
class IndexingException(
    code: String,
    message: String,
    cause: Throwable? = null,
) : link.roadio.cuegrain.identity.MediaIdentityException(code, message, cause)
