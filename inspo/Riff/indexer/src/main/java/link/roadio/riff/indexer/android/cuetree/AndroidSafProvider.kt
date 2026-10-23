package link.roadio.riff.indexer.android.cuetree

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import link.roadio.cuetree.Child
import link.roadio.cuetree.OpenMedia
import link.roadio.cuetree.RootSpec
import link.roadio.cuetree.SourceHandle
import link.roadio.cuetree.SourceProvider
import link.roadio.cuetree.SourceStat
import link.roadio.cuetree.Stamp
import link.roadio.riff.indexer.android.level0.isStandardAudiobooksTree
import link.roadio.riff.indexer.core.media.isAudioCandidateMime
import java.io.EOFException
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer

/** SAF document IDs are opaque; discovered descendants come from provider listings or provider-issued creation URIs. */
class AndroidSafProvider(
    private val resolver: ContentResolver,
) : SourceProvider {
    override val id = "saf"
    private val marker = AndroidSafRootMarker(resolver)

    private data class Handle(
        val tree: Uri,
        val document: Uri,
        val displayName: String? = null,
    ) : SourceHandle

    data class Grant(
        val tree: Uri,
        val writable: Boolean,
    )

    data class Discovery(
        val roots: List<RootSpec>,
        val listingBugFolders: List<String>,
        val unverifiedFolders: List<String>,
    )

    fun roots(publishMarkers: Boolean = true): List<RootSpec> = discover(publishMarkers).roots

    fun discover(publishMarkers: Boolean = true): Discovery =
        discover(
            resolver.persistedUriPermissions.filter { it.isReadPermission }.map { Grant(it.uri, it.isWritePermission) },
            publishMarkers,
        )

    // AI-INTENT: An unusable grant stays persisted and follows ordinary unavailable-source TTL.
    // Omitting it from this pass must not create a new identity, stop other roots or exit Player.
    // https://wiki.271.pics/Riff/LATEST/saf-empty-listing-workaround#scan-retention-and-warning
    internal fun discover(
        grants: List<Grant>,
        publishMarkers: Boolean = true,
    ): Discovery {
        val failedFolders = mutableListOf<String>()
        val unverifiedFolders = mutableListOf<String>()
        val roots =
            grants.distinctBy { it.tree }.sortedBy { it.tree.toString() }.mapNotNull { grant ->
                runCatching {
                    val tree = grant.tree
                    val h = root(tree.toString()) as Handle
                    val (name, mime, modified, size) = details(h)
                    if (name.startsWith('.')) {
                        null
                    } else {
                        val identity =
                            try {
                                marker.resolve(tree, publishMarkers && grant.writable)
                            } catch (_: SafListingBugActiveException) {
                                failedFolders += name.ifBlank { "Media folder" }
                                return@runCatching null
                            } catch (_: SafEmptyRootUnverifiedException) {
                                unverifiedFolders += name.ifBlank { "Media folder" }
                                return@runCatching null
                            }
                        RootSpec(
                            id,
                            tree.toString(),
                            name.ifBlank {
                                "Media folder"
                            },
                            identity,
                            sourceStat(mime, modified, size),
                            book = isStandardAudiobooksTree(tree.toString()),
                        )
                    }
                }.getOrNull()
            }
        return Discovery(roots, failedFolders, unverifiedFolders)
    }

    fun documentUri(handle: SourceHandle): String = checked(handle).document.toString()

    override fun bindingId(handle: SourceHandle): Long =
        link.roadio.riff.storage.hash.nativeXxh3(
            DocumentsContract.getDocumentId(checked(handle).document).toByteArray(Charsets.UTF_8),
        )

    override fun root(locator: String): SourceHandle {
        val tree = Uri.parse(locator)
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return Handle(tree, document)
    }

    override fun stat(handle: SourceHandle): SourceStat {
        val (_, mime, modified, size) = details(checked(handle))
        return sourceStat(mime, modified, size)
    }

    override fun children(directory: SourceHandle): List<Child> {
        val h = checked(directory)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(h.tree, DocumentsContract.getDocumentId(h.document))
        return io {
            resolver.query(childrenUri, CHILD_COLUMNS, null, null, null).use { cursor ->
                val rows = requireCompleteSafListing(cursor)
                buildList {
                    while (rows.moveToNext()) {
                        val name = rows.getString(0) ?: throw IOException("SAF child has no name")
                        val documentId = rows.getString(1) ?: throw IOException("SAF child has no ID")
                        if (name.startsWith('.')) continue
                        // One directory observation supplies the scan's stat; avoid a Binder query per child.
                        // https://wiki.271.pics/Riff/LATEST/cuetree-integration#source-evidence
                        val mime = rows.getString(2) ?: ""
                        val modified = rows.getLongOrNull(3)
                        val size = rows.getLongOrNull(4)
                        val uri = DocumentsContract.buildDocumentUriUsingTree(h.tree, documentId)
                        add(Child(name, Handle(h.tree, uri, name), sourceStat(mime, modified, size)))
                    }
                }
            }
        }
    }

    override fun open(media: SourceHandle): OpenMedia {
        val h = checked(media)
        return io {
            val asset = resolver.openAssetFileDescriptor(h.document, "r") ?: throw IOException("SAF media unavailable")
            try {
                val input = FileInputStream(asset.fileDescriptor)
                val channel = input.channel
                val base = asset.startOffset
                val size = asset.declaredLength.takeIf { it >= 0 } ?: (channel.size() - base)
                if (size < 0) throw IOException("SAF media length unavailable")
                object : AndroidDescriptorMedia {
                    override val id = h.document.toString()
                    override val length = size
                    override val displayName = h.displayName ?: "Current track"
                    override val descriptor = asset.fileDescriptor
                    override val offset = base

                    override fun read(
                        offset: Long,
                        count: Int,
                    ): ByteArray {
                        if (offset < 0 ||
                            count < 0 ||
                            offset > length ||
                            count.toLong() > length - offset
                        ) {
                            throw EOFException("Read outside source")
                        }
                        val bytes = ByteArray(count)
                        val buffer = ByteBuffer.wrap(bytes)
                        var position = base + offset
                        while (buffer.hasRemaining()) {
                            val read = channel.read(buffer, position)
                            if (read <= 0) throw EOFException("SAF source truncated")
                            position += read
                        }
                        return bytes
                    }

                    override fun close() {
                        input.close()
                        asset.close()
                    }
                }
            } catch (error: Throwable) {
                asset.close()
                throw error
            }
        }
    }

    private fun sourceStat(
        mime: String,
        modified: Long?,
        size: Long?,
    ): SourceStat =
        SourceStat(
            mime == DocumentsContract.Document.MIME_TYPE_DIR,
            Stamp(modified ?: Long.MIN_VALUE, size ?: 0L),
            mediaCandidate = isAudioCandidateMime(mime),
        )

    private fun details(h: Handle): Details =
        io {
            resolver.query(h.document, STAT_COLUMNS, null, null, null).use { cursor ->
                val row = cursor ?: throw IOException("SAF document unavailable")
                if (!row.moveToFirst()) throw IOException("SAF document missing")
                Details(row.getString(0) ?: "", row.getString(1) ?: "", row.getLongOrNull(2), row.getLongOrNull(3))
            }
        }

    private fun android.database.Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private fun checked(handle: SourceHandle): Handle = handle as? Handle ?: throw IOException("Foreign SAF handle")

    private inline fun <T> io(block: () -> T): T =
        try {
            block()
        } catch (error: SecurityException) {
            throw IOException("SAF grant unavailable", error)
        } catch (error: RuntimeException) {
            throw IOException("SAF provider operation failed", error)
        }

    private data class Details(
        val first: String,
        val second: String,
        val third: Long?,
        val fourth: Long?,
    )

    private companion object {
        val STAT_COLUMNS =
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            )
        val CHILD_COLUMNS =
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            )
    }
}
