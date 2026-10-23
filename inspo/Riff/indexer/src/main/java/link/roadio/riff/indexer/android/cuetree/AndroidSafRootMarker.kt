package link.roadio.riff.indexer.android.cuetree

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import link.roadio.riff.storage.hash.nativeXxh3
import link.roadio.riff.storage.hash.readXxh3CanonicalLong
import link.roadio.riff.storage.hash.toXxh3CanonicalBytes
import java.io.FileOutputStream
import java.util.UUID

/** The source-root UUID is independent of the SAF URI used to access that root. */
internal class AndroidSafRootMarker(
    private val resolver: ContentResolver,
    private val newUuid: () -> UUID = UUID::randomUUID,
) {
    fun resolve(
        tree: Uri,
        writable: Boolean,
    ): String {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        readPublished(tree, root)?.let {
            Log.i(LOG_TAG, "source_root_marker=existing write_grant=$writable")
            return it
        }
        val fallback = readFallback(tree, root)
        // AI-INTENT: Use the unfiltered response, including hidden-only roots. Before UUID writes,
        // a new provider-issued document witnesses existence without guessing opaque child IDs.
        // An empty read-only root cannot supply that witness and is skipped as inconclusive.
        // https://wiki.271.pics/Riff/LATEST/saf-empty-listing-workaround#admission-before-marker-writes
        if (!hasAnyChild(tree, root) && fallback == null) {
            if (!writable) throw SafEmptyRootUnverifiedException()
            val active =
                try {
                    SafEmptyRootProbe(resolver).isListingBugActive(tree, root)
                } catch (error: Exception) {
                    Log.w(LOG_TAG, "source_root_diagnostic_inconclusive type=" + error.javaClass.simpleName)
                    throw SafEmptyRootUnverifiedException(error)
                }
            if (active) {
                Log.w(LOG_TAG, "source_root_listing_bug_active=true")
                throw SafListingBugActiveException()
            }
        }
        if (writable) {
            publish(tree, root)?.let {
                Log.i(LOG_TAG, "source_root_marker=created write_grant=true")
                return it
            }
        }
        fallback?.let {
            Log.i(LOG_TAG, "source_root_marker=fallback write_grant=$writable")
            return it
        }
        Log.i(LOG_TAG, "source_root_marker=zero write_grant=$writable")
        return ZERO_UUID
    }

    private fun hasAnyChild(
        tree: Uri,
        root: Uri,
    ): Boolean {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(root))
        return resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null).use { cursor ->
            requireCompleteSafListing(cursor).moveToFirst()
        }
    }

    private fun readPublished(
        tree: Uri,
        root: Uri,
    ): String? =
        runCatching {
            val directory = requireNotNull(findChild(tree, root, RIFF_DIRECTORY, DocumentsContract.Document.MIME_TYPE_DIR))
            val marker = requireNotNull(findChild(tree, directory, MARKER_NAME, BINARY_MIME))
            val sidecar = requireNotNull(findChild(tree, directory, SIDECAR_NAME, BINARY_MIME))
            val bytes = readBounded(marker, UUID_TEXT_BYTES)
            require(bytes.size == UUID_TEXT_BYTES)
            val checksum = readBounded(sidecar, Long.SIZE_BYTES)
            require(checksum.size == Long.SIZE_BYTES && nativeXxh3(bytes) == checksum.readXxh3CanonicalLong())
            canonicalUuidV4(bytes.toString(Charsets.UTF_8))
        }.getOrNull()

    private fun publish(
        tree: Uri,
        root: Uri,
    ): String? =
        runCatching {
            val directory =
                findChild(tree, root, RIFF_DIRECTORY, DocumentsContract.Document.MIME_TYPE_DIR)
                    ?: requireNotNull(
                        DocumentsContract.createDocument(
                            resolver,
                            root,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            RIFF_DIRECTORY,
                        ),
                    )
            val value = canonicalUuidV4(newUuid().toString())
            val bytes = value.toByteArray(Charsets.UTF_8)
            write(ensureFile(tree, directory, MARKER_NAME), bytes)
            write(ensureFile(tree, directory, SIDECAR_NAME), nativeXxh3(bytes).toXxh3CanonicalBytes())
            readPublished(tree, root).also { require(it == value) }
        }.onFailure { error ->
            Log.w(LOG_TAG, "source_root_marker_publish_failed type=${error.javaClass.simpleName}")
        }.getOrNull()

    private fun readFallback(
        tree: Uri,
        root: Uri,
    ): String? =
        runCatching {
            val directory = requireNotNull(findChild(tree, root, THUMBNAILS_DIRECTORY, DocumentsContract.Document.MIME_TYPE_DIR))
            val marker = requireNotNull(findChild(tree, directory, FALLBACK_NAME, BINARY_MIME))
            canonicalUuidV4(readBounded(marker, FALLBACK_MAX_BYTES).toString(Charsets.UTF_8).trim())
        }.getOrNull()

    private fun ensureFile(
        tree: Uri,
        directory: Uri,
        name: String,
    ): Uri =
        findChild(tree, directory, name, BINARY_MIME)
            ?: requireNotNull(DocumentsContract.createDocument(resolver, directory, BINARY_MIME, name))

    private fun findChild(
        tree: Uri,
        directory: Uri,
        name: String,
        mime: String,
    ): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(directory))
        return resolver.query(children, IDENTITY_PROJECTION, null, null, null).use { cursor ->
            val rows = requireNotNull(cursor)
            val matches =
                buildList {
                    while (rows.moveToNext()) {
                        val actual = rows.getString(2)
                        if (rows.getString(0) == name &&
                            (if (mime == BINARY_MIME) actual != DocumentsContract.Document.MIME_TYPE_DIR else actual == mime)
                        ) {
                            add(DocumentsContract.buildDocumentUriUsingTree(tree, rows.getString(1)))
                        }
                    }
                }
            require(matches.size <= 1) { "Duplicate source-root marker" }
            matches.singleOrNull()
        }
    }

    private fun readBounded(
        uri: Uri,
        maximum: Int,
    ): ByteArray =
        requireNotNull(resolver.openInputStream(uri)).use { input ->
            val bytes = ByteArray(maximum + 1)
            var count = 0
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read < 0) break
                if (read == 0) {
                    val byte = input.read()
                    if (byte < 0) break
                    bytes[count++] = byte.toByte()
                } else {
                    count += read
                }
            }
            require(count <= maximum) { "Source-root marker is oversized" }
            bytes.copyOf(count)
        }

    private fun write(
        uri: Uri,
        bytes: ByteArray,
    ) {
        requireNotNull(resolver.openFileDescriptor(uri, "rwt")).use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun canonicalUuidV4(value: String): String {
        val canonical = UUID.fromString(value).toString()
        require(value == canonical && canonical[14] == '4')
        return canonical
    }

    private companion object {
        const val LOG_TAG = "RiffSafMarker"
        const val ZERO_UUID = "00000000-0000-4000-8000-000000000000"
        const val RIFF_DIRECTORY = ".riff"
        const val MARKER_NAME = "riff_root_uuid"
        const val SIDECAR_NAME = "riff_root_uuid.xxh3"
        const val THUMBNAILS_DIRECTORY = ".thumbnails"
        const val FALLBACK_NAME = ".database_uuid"
        const val BINARY_MIME = "application/octet-stream"
        const val UUID_TEXT_BYTES = 36
        const val FALLBACK_MAX_BYTES = 128
        val IDENTITY_PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
    }
}
