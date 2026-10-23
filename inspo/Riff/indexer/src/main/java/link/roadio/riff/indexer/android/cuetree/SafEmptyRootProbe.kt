package link.roadio.riff.indexer.android.cuetree

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.util.UUID

/**
 * Distinguishes an empty writable root from the observed false-empty SAF listing failure.
 *
 * Android 16 ExternalStorageProvider can enumerate zero children while documents still exist and
 * remain directly readable. Creating a Riff UUID in that state previously produced renamed .riff
 * directories and new source identities. A successful empty cursor alone is therefore insufficient.
 *
 * The name intentionally has no leading dot: legitimate hidden-file filtering must not masquerade
 * as this failure. The .tmp suffix and binary MIME exclude it from media admission.
 * This probe creates one uniquely named EMPTY non-media document through the granted root, opens
 * the exact provider-returned URI read-only, and enumerates the root again. A still-empty complete
 * response now contradicts a document known to exist. Creating/opening/deleting alone does not
 * diagnose anything; the contradictory enumeration is essential. No payload, UUID or XXH3 is read.
 *
 * Every created document is deleted in finally through only its returned URI. We never guess a
 * descendant ID, access filesystem paths, inspect/delete existing documents, or clean up files by
 * name pattern. A cleanup failure aborts admission rather than allowing marker writes. Process
 * death can leave a harmless uniquely named empty document; filename matching is not deletion
 * authority. Read-only grants cannot run this probe and must remain inconclusive.
 *
 * Authority: https://wiki.271.pics/Riff/LATEST/saf-empty-listing-workaround#why-the-temporary-file-is-necessary
 */
internal class SafEmptyRootProbe(
    private val resolver: ContentResolver,
) {
    fun isListingBugActive(
        tree: Uri,
        root: Uri,
    ): Boolean {
        val temporary =
            DocumentsContract.createDocument(
                resolver,
                root,
                "application/octet-stream",
                "riff-saf-probe-" + UUID.randomUUID() + ".tmp",
            ) ?: throw IOException("SAF diagnostic creation returned no document")
        try {
            resolver.openFileDescriptor(temporary, "r")?.use { }
                ?: throw IOException("SAF diagnostic document could not be opened")
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(root))
            return resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null).use { cursor ->
                !requireCompleteSafListing(cursor).moveToFirst()
            }
        } finally {
            // AI-INTENT: The URI returned by OUR create is the sole cleanup authority. A failed
            // cleanup is inconclusive; never write a source UUID after an unfinished diagnostic.
            if (!DocumentsContract.deleteDocument(resolver, temporary)) {
                throw IOException("SAF diagnostic cleanup did not remove its document")
            }
        }
    }
}
