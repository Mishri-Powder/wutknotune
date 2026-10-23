package link.roadio.riff.player.artwork

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.ui.ArtworkOrigin
import link.roadio.riff.ui.PlaybackSourceRef
import link.roadio.riff.ui.PlaybackSourceSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ArtworkProviderRecoveryInstrumentedTest {
    @Test
    fun loadingSafListingRecoversToTheActualDirectoryImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "artwork-provider-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(directory, "audio").writeBytes(byteArrayOf(0))
            val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
            File(directory, "image").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val provider = Provider(directory)
            provider.attachInfo(context, ProviderInfo().apply { authority = "riff.artwork.fixture" })
            val resolver =
                PlayerArtworkResolver(ContentResolver.wrap(provider)) {
                    DocumentsContract.buildTreeDocumentUri("riff.artwork.fixture", "root").toString()
                }
            val source = PlaybackSourceRef(2, 1, listOf(PlaybackSourceSegment("track.mp3", "audio/mpeg")))
            val key = ArtworkKey(1, 1, 2, 1)
            PlayerArtworkLoader(resolver::resolveResult, retryDelayMs = 10).use { loader ->
                assertNull(loader.request(key, source).bitmap)
                provider.allowRecovery.countDown()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (loader.artworkFor(key)?.bitmap == null && System.nanoTime() < deadline) Thread.sleep(10)
                val artwork = requireNotNull(loader.artworkFor(key))
                assertNotNull(artwork.bitmap)
                assertEquals(ArtworkOrigin.DIRECTORY, artwork.origin)
                assertEquals(3, artwork.bitmap?.width)
                assertEquals(2, artwork.bitmap?.height)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private class Provider(
        private val directory: File,
    ) : ContentProvider() {
        private val queries = AtomicInteger()
        val allowRecovery = CountDownLatch(1)

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns = requireNotNull(projection)
            return MatrixCursor(columns).apply {
                if (queries.incrementAndGet() == 1) {
                    extras = Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, true) }
                } else {
                    check(allowRecovery.await(5, TimeUnit.SECONDS))
                    for ((id, name, mime) in listOf(
                        Triple("audio", "track.mp3", "audio/mpeg"),
                        Triple("image", "cover.png", "image/png"),
                    )) {
                        addRow(
                            columns.map {
                                when (it) {
                                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                                    DocumentsContract.Document.COLUMN_MIME_TYPE -> mime
                                    else -> null
                                }
                            },
                        )
                    }
                }
            }
        }

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor =
            ParcelFileDescriptor.open(File(directory, DocumentsContract.getDocumentId(uri)), ParcelFileDescriptor.MODE_READ_ONLY)

        override fun getType(uri: Uri) = "application/octet-stream"

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = throw UnsupportedOperationException()

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = throw UnsupportedOperationException()

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = throw UnsupportedOperationException()
    }
}
