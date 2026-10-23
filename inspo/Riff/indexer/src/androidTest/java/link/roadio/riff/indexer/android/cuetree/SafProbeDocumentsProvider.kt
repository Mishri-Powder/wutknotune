package link.roadio.riff.indexer.android.cuetree

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.util.UUID

/** Test-owned provider with opaque IDs, real descriptors and independently faulty root enumeration. */
class SafProbeDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean {
        directory = File(requireNotNull(context).cacheDir, "saf-probe-fixture")
        reset()
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID))

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor = cursor(projection, listOf(nodes[documentId] ?: error("Missing fixture document")))

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val rows = if (parentDocumentId in hiddenRoots) emptyList() else nodes.values.filter { it.parent == parentDocumentId }
        return cursor(projection, rows).apply {
            if (parentDocumentId in loadingRoots) setExtras(Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, true) })
            if (parentDocumentId in errorRoots) setExtras(Bundle().apply { putString(DocumentsContract.EXTRA_ERROR, "fixture error") })
        }
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        if (displayName.startsWith("riff-saf-probe-")) {
            temporaryCreations++
            if (failCreate) error("Fixture creation failed")
        } else if (displayName == ".riff") {
            markerCreations++
        }
        return seed(parentDocumentId, displayName, mimeType)
    }

    override fun deleteDocument(documentId: String) {
        deletions++
        if (failDelete) error("Fixture deletion failed")
        val node = nodes.remove(documentId) ?: error("Missing fixture document")
        node.file?.delete()
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val node = nodes[documentId] ?: error("Missing fixture document")
        if (failOpen && node.name.startsWith("riff-saf-probe-")) error("Fixture opening failed")
        return ParcelFileDescriptor.open(requireNotNull(node.file), ParcelFileDescriptor.parseMode(mode))
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean {
        var child: Node? = nodes[documentId]
        while (child != null) {
            if (child.id == parentDocumentId) return true
            child = child.parent?.let(nodes::get)
        }
        return false
    }

    private fun cursor(
        projection: Array<out String>?,
        values: List<Node>,
    ): MatrixCursor {
        val columns = projection ?: COLUMNS
        return MatrixCursor(columns).apply {
            values.forEach { node ->
                addRow(
                    columns.map { column ->
                        when (column) {
                            Document.COLUMN_DOCUMENT_ID -> {
                                node.id
                            }

                            Document.COLUMN_DISPLAY_NAME -> {
                                node.name
                            }

                            Document.COLUMN_MIME_TYPE -> {
                                node.mime
                            }

                            Document.COLUMN_FLAGS -> {
                                Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_WRITE or
                                    Document.FLAG_SUPPORTS_DELETE
                            }

                            Document.COLUMN_LAST_MODIFIED -> {
                                1L
                            }

                            Document.COLUMN_SIZE -> {
                                node.file?.length()
                            }

                            else -> {
                                null
                            }
                        }
                    },
                )
            }
        }
    }

    data class Node(
        val id: String,
        val parent: String?,
        val name: String,
        val mime: String,
        val file: File?,
    )

    companion object {
        const val FIRST_ROOT = "opaque-music-root"
        const val SECOND_ROOT = "opaque-books-root"
        private lateinit var directory: File
        val nodes = linkedMapOf<String, Node>()
        val hiddenRoots = mutableSetOf<String>()
        val loadingRoots = mutableSetOf<String>()
        val errorRoots = mutableSetOf<String>()
        var temporaryCreations = 0
        var markerCreations = 0
        var deletions = 0
        var failCreate = false
        var failOpen = false
        var failDelete = false

        fun reset() {
            directory.deleteRecursively()
            directory.mkdirs()
            nodes.clear()
            nodes[FIRST_ROOT] = Node(FIRST_ROOT, null, "Music", Document.MIME_TYPE_DIR, null)
            nodes[SECOND_ROOT] = Node(SECOND_ROOT, null, "Books", Document.MIME_TYPE_DIR, null)
            hiddenRoots.clear()
            loadingRoots.clear()
            errorRoots.clear()
            temporaryCreations = 0
            markerCreations = 0
            deletions = 0
            failCreate = false
            failOpen = false
            failDelete = false
        }

        fun seed(
            parent: String,
            name: String,
            mime: String,
            bytes: ByteArray = byteArrayOf(),
        ): String {
            val id = "opaque-" + UUID.randomUUID()
            val file = if (mime == Document.MIME_TYPE_DIR) null else File(directory, id).apply { writeBytes(bytes) }
            nodes[id] = Node(id, parent, name, mime, file)
            return id
        }

        fun temporaryDocuments(): List<Node> = nodes.values.filter { it.name.startsWith("riff-saf-probe-") }

        private val COLUMNS =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_FLAGS,
                Document.COLUMN_SIZE,
                Document.COLUMN_LAST_MODIFIED,
            )
    }
}
