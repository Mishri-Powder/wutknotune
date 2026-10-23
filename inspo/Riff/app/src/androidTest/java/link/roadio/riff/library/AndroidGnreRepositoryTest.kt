package link.roadio.riff.library

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.config.ProjectLanguages
import link.roadio.riff.indexer.android.library.AndroidGnreRepository
import link.roadio.riff.indexer.storage.gnre.GnreResource
import link.roadio.riff.indexer.storage.gnre.ReadOnlyByteBuffer
import link.roadio.riff.storage.hash.nativeXxh3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.channels.FileChannel

@RunWith(AndroidJUnit4::class)
class AndroidGnreRepositoryTest {
    @Test
    fun bundledAssetPublishesConfiguredGenerationAndExactReadOnlyDescriptor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AndroidGnreRepository(context)
        var publications = 0
        repository.publicationListener = { publications++ }

        val published = repository.installBundled()
        assertEquals(ProjectLanguages.gnreGeneration.toLong(), published.metadata.generation)
        assertEquals(ProjectLanguages.gnreGeneration, GnreResource.inspect(published.buffer).generation)
        assertSame(published, repository.current())
        assertSame(published, repository.installBundled())
        assertEquals(1, publications)

        ParcelFileDescriptor.AutoCloseInputStream(repository.openDescriptor(published)).use { input ->
            val mapped = input.channel.map(FileChannel.MapMode.READ_ONLY, published.offset, published.metadata.length)
            assertTrue(mapped.isReadOnly)
            assertEquals(published.metadata.xxh3, nativeXxh3(mapped))
            assertEquals(ProjectLanguages.gnreGeneration, GnreResource.inspect(ReadOnlyByteBuffer(mapped)).generation)
        }
    }
}
