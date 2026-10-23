package link.roadio.riff.indexer.android.level0

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract.Document

/**
 * Test-owned document tree that exercises the real ContentResolver/Level-0 adapter boundary.
 *
 * ai: Keep this provider in androidTest and non-exported. It supplies metadata only; no device
 * permission, mounted media, serial allowlist or user's persisted grant should affect correctness.
 */
class FixtureDocumentsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: COLUMNS
        val isChildren = uri.lastPathSegment == "children"
        val row =
            mapOf(
                Document.COLUMN_DOCUMENT_ID to if (isChildren) "track" else "root",
                Document.COLUMN_DISPLAY_NAME to if (isChildren) "fixture.mp3" else "Fixture",
                Document.COLUMN_MIME_TYPE to if (isChildren) "audio/mpeg" else Document.MIME_TYPE_DIR,
                Document.COLUMN_SIZE to if (isChildren) 128L else null,
                Document.COLUMN_LAST_MODIFIED to 1L,
                Document.COLUMN_FLAGS to 0,
            )
        return MatrixCursor(columns).apply { addRow(columns.map(row::get)) }
    }

    override fun getType(uri: Uri): String = Document.MIME_TYPE_DIR

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = error("Read-only test tree")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = error("Read-only test tree")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = error("Read-only test tree")

    private companion object {
        val COLUMNS =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_FLAGS,
            )
    }
}
