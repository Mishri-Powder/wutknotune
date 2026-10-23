package link.roadio.riff.player.playback

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import link.roadio.riff.ui.PlaybackSourceRef
import java.io.IOException

/** Resolves one exact MDIA binding through its persisted SAF tree. */
class PlayerMediaUriResolver(
    private val contentResolver: ContentResolver,
    private val treeUriForRoot: (Long) -> String?,
) {
    fun resolve(
        source: PlaybackSourceRef,
        cancellation: CancellationSignal,
    ): Uri? = resolveLocation(source, cancellation)?.media

    data class Location(
        val tree: Uri,
        val parent: Uri,
        val media: Uri,
    )

    fun resolveLocation(
        source: PlaybackSourceRef,
        cancellation: CancellationSignal,
    ): Location? {
        if (source.segments.isEmpty()) return null
        cancellation.throwIfCanceled()
        val tree = Uri.parse(treeUriForRoot(source.rootBindingId) ?: return null)
        var parent =
            DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
        source.segments.forEachIndexed { index, segment ->
            val child = uniqueChild(tree, parent, segment.displayName, segment.mimeType, cancellation) ?: return null
            if (index == source.segments.lastIndex) return Location(tree, parent, child)
            parent = child
        }
        return null
    }

    private fun uniqueChild(
        tree: Uri,
        directory: Uri,
        displayName: String,
        mimeType: String,
        cancellation: CancellationSignal,
    ): Uri? {
        val children =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                tree,
                DocumentsContract.getDocumentId(directory),
            )
        var match: Uri? = null
        contentResolver.query(children, IDENTITY_PROJECTION, null, null, null, cancellation).use { cursor ->
            requireNotNull(cursor) { "Document provider returned a null cursor" }
            if (cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING)) throw IOException("Document listing is still loading")
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                cancellation.throwIfCanceled()
                if (cursor.getString(nameColumn) != displayName ||
                    (
                        mimeType.isNotEmpty() &&
                            cursor.getString(mimeColumn) != mimeType
                    )
                ) {
                    continue
                }
                if (match != null) return null
                match = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn))
            }
        }
        return match
    }

    private companion object {
        val IDENTITY_PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
    }
}
