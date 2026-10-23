package link.roadio.riff.indexer.android.cuetree

import android.database.Cursor
import android.provider.DocumentsContract
import java.io.IOException

/**
 * Loading, errors and null cursors cannot prove absence. In particular they must not authorize a
 * diagnostic or UUID write; this check precedes dot-name/media filtering.
 * https://wiki.271.pics/Riff/LATEST/saf-empty-listing-workaround#admission-before-marker-writes
 */
internal fun requireCompleteSafListing(cursor: Cursor?): Cursor {
    val rows = cursor ?: throw IOException("SAF returned no directory listing")
    val extras = rows.extras
    if (extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) ||
        !extras.getString(DocumentsContract.EXTRA_ERROR).isNullOrEmpty()
    ) {
        throw IOException("SAF directory listing is incomplete")
    }
    return rows
}

internal class SafListingBugActiveException : IOException("SAF platform returned a false-empty root listing")

internal class SafEmptyRootUnverifiedException(
    cause: Exception? = null,
) : IOException("SAF empty root could not be verified", cause)
