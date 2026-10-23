package link.roadio.riff.indexer.android.library

import android.content.Context
import android.os.ParcelFileDescriptor
import com.google.flatbuffers.kotlin.ReadWriteBuffer
import link.roadio.riff.config.ProjectFileVersions
import link.roadio.riff.config.ProjectLanguages
import link.roadio.riff.indexer.core.ipc.IndexerArtifactKind
import link.roadio.riff.indexer.core.ipc.IndexerArtifactMetadata
import link.roadio.riff.indexer.storage.gnre.GnreResource
import link.roadio.riff.indexer.storage.gnre.ReadOnlyByteBuffer
import link.roadio.riff.indexer.storage.gnre.requireAudiobookGenre
import link.roadio.riff.storage.hash.nativeXxh3
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

data class PublishedGnreArtifact(
    val offset: Long,
    val metadata: IndexerArtifactMetadata,
    val buffer: ReadWriteBuffer,
)

/** Owns the immutable, uncompressed APK GNRE asset without extracting it. */
class AndroidGnreRepository(
    context: Context,
) {
    private val context = context.applicationContext

    @Volatile
    private var installed: PublishedGnreArtifact? = null

    @Volatile
    var publicationListener: ((PublishedGnreArtifact) -> Unit)? = null

    @Synchronized
    fun installBundled(): PublishedGnreArtifact {
        installed?.let { return it }
        val mapped = mapAsset()
        val buffer = ReadOnlyByteBuffer(mapped.bytes)
        val resource = GnreResource.inspect(buffer)
        require(resource.generation == ProjectLanguages.gnreGeneration) {
            "Expected GNRE generation ${ProjectLanguages.gnreGeneration}, got ${resource.generation}"
        }
        requireAudiobookGenre(buffer)
        val published =
            PublishedGnreArtifact(
                offset = mapped.offset,
                metadata =
                    IndexerArtifactMetadata(
                        kind = IndexerArtifactKind.GNRE,
                        volumeId = GENRE_VOLUME_ID,
                        generation = resource.generation.toLong(),
                        length = mapped.length,
                        flatBufferSchemaVersion = ProjectFileVersions.Gnre.flatBufferSchema,
                        semanticVersion = ProjectFileVersions.Gnre.semantic,
                        xxh3 = nativeXxh3(mapped.bytes),
                    ),
                buffer = buffer,
            )
        installed = published
        publicationListener?.invoke(published)
        return published
    }

    fun current(): PublishedGnreArtifact? = installed

    fun openDescriptor(published: PublishedGnreArtifact): ParcelFileDescriptor =
        context.assets.openFd(ASSET_PATH).use { asset ->
            require(asset.startOffset == published.offset && asset.length == published.metadata.length) {
                "GNRE APK asset range changed"
            }
            requireContained(asset.startOffset, asset.length, asset.parcelFileDescriptor.statSize)
            ParcelFileDescriptor.dup(asset.fileDescriptor)
        }

    private fun mapAsset(): MappedAsset =
        context.assets.openFd(ASSET_PATH).use { asset ->
            val offset = asset.startOffset
            val length = asset.length
            require(length in 1..Int.MAX_VALUE.toLong()) {
                "GNRE length is outside Kotlin FlatBuffer bounds"
            }
            requireContained(offset, length, asset.parcelFileDescriptor.statSize)
            val descriptor = ParcelFileDescriptor.dup(asset.fileDescriptor)
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                MappedAsset(
                    offset,
                    length,
                    input.channel.map(FileChannel.MapMode.READ_ONLY, offset, length),
                )
            }
        }

    private fun requireContained(
        offset: Long,
        length: Long,
        descriptorSize: Long,
    ) {
        require(offset >= 0 && length > 0 && descriptorSize >= 0) { "Invalid GNRE descriptor range" }
        require(offset <= descriptorSize && length <= descriptorSize - offset) {
            "GNRE asset range is outside the APK descriptor"
        }
    }

    private data class MappedAsset(
        val offset: Long,
        val length: Long,
        val bytes: ByteBuffer,
    )

    companion object {
        const val ASSET_PATH = "gnre/gnre.fb"
        const val GENRE_VOLUME_ID = "genre"
    }
}
