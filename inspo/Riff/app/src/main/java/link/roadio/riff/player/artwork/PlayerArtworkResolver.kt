package link.roadio.riff.player.artwork

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.util.Log
import link.roadio.riff.player.playback.PlayerMediaUriResolver
import link.roadio.riff.ui.ArtworkUiState
import link.roadio.riff.ui.PlaybackSourceRef
import java.io.IOException

/** Resolves artwork from the exact selected SAF occurrence without modifying provider data. */
class PlayerArtworkResolver(
    private val resolver: ContentResolver,
    private val treeUriForRoot: (Long) -> String?,
) {
    private val sourceResolver = PlayerMediaUriResolver(resolver, treeUriForRoot)

    fun resolve(
        source: PlaybackSourceRef?,
        cancellation: CancellationSignal,
    ): ArtworkUiState = resolveResult(source, cancellation).artwork

    internal fun resolveResult(
        source: PlaybackSourceRef?,
        cancellation: CancellationSignal,
    ): ArtworkResolution {
        if (source == null || source.segments.isEmpty()) return ArtworkResolution(ArtworkUiState(source?.bindingId))
        return runCatching {
            cancellation.throwIfCanceled()
            val location =
                sourceResolver.resolveLocation(source, cancellation)
                    ?: throw IOException("Source is not uniquely available")
            val embedded = runCatching { readEmbedded(location.media, cancellation) }
            cancellation.throwIfCanceled()
            val folderImages =
                if (embedded.getOrNull() ==
                    null
                ) {
                    readDirectoryImages(location.tree, location.parent, cancellation)
                } else {
                    emptyList()
                }
            val choice = chooseArtwork(embedded.getOrNull(), folderImages)
            val artwork = ArtworkUiState(source.bindingId, choice.value, choice.origin)
            Log.i(LOG_TAG, "artwork_resolved origin=${artwork.origin} binding_id=${source.bindingId.toULong().toString(16)}")
            ArtworkResolution(artwork, retryable = choice.value == null && embedded.isFailure)
        }.getOrElse { error ->
            Log.w(LOG_TAG, "artwork_default error=${error.javaClass.simpleName}")
            ArtworkResolution(ArtworkUiState(source.bindingId), retryable = true)
        }
    }

    private fun readEmbedded(
        media: Uri,
        cancellation: CancellationSignal,
    ): Bitmap? =
        run {
            requireNotNull(resolver.openFileDescriptor(media, "r", cancellation)).use { descriptor ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(descriptor.fileDescriptor)
                    retriever.embeddedPicture?.let(::decodeSampled)
                } finally {
                    retriever.release()
                }
            }
        }

    private fun readDirectoryImages(
        tree: Uri,
        directory: Uri,
        cancellation: CancellationSignal,
    ): List<Bitmap> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(directory))
        val imageUris = mutableListOf<Uri>()
        resolver.query(children, IDENTITY_PROJECTION, null, null, null, cancellation).use { cursor ->
            requireNotNull(cursor) { "Document provider returned a null cursor" }
            if (cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING)) throw IOException("Document listing is still loading")
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getString(mimeColumn).startsWith(IMAGE_MIME_PREFIX)) {
                    imageUris += DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn))
                }
            }
        }
        val decoded = mutableListOf<Bitmap>()
        imageUris.forEach { uri ->
            cancellation.throwIfCanceled()
            decodeDocument(uri, cancellation)?.let(decoded::add)
            if (decoded.size > 1) return decoded
        }
        return decoded
    }

    private fun decodeDocument(
        uri: Uri,
        cancellation: CancellationSignal,
    ): Bitmap? =
        run {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            requireNotNull(resolver.openFileDescriptor(uri, "r", cancellation)).use { descriptor ->
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
            requireNotNull(resolver.openFileDescriptor(uri, "r", cancellation)).use { descriptor ->
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
            }
        }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun sampleSize(
        width: Int,
        height: Int,
    ): Int {
        var sample = 1
        while (width / sample > MAX_DIMENSION || height / sample > MAX_DIMENSION) sample *= 2
        return sample
    }

    private companion object {
        const val LOG_TAG = "RiffArtwork"
        const val IMAGE_MIME_PREFIX = "image/"
        const val MAX_DIMENSION = 2048
        val IDENTITY_PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
    }
}
