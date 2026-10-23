@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.indexer.android.cuetree

import android.content.Context
import link.roadio.cuegrain.MediaInput
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.CuegrainMetadataReader
import link.roadio.cuetree.MetadataReader
import link.roadio.cuetree.Observation
import link.roadio.riff.indexer.android.level1.AdmittedAudio
import link.roadio.riff.indexer.android.level1.AndroidGenreMatcher
import link.roadio.riff.indexer.android.level1.AndroidSourceAdmission
import link.roadio.riff.indexer.android.library.AndroidGnreRepository
import link.roadio.riff.indexer.android.tags.AndroidCedEncodingDetector
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.indexer.level1.Level1ScanResult
import java.io.IOException

/** Source admission and metadata share one changed-source acquisition lifetime.
 * Contract: https://wiki.271.pics/Riff/LATEST/indexer-media-admission#device-derived-audio-admission
 */
internal class AndroidCuetreeMetadataReader(
    context: Context,
    private val delegate: MetadataReader = CuegrainMetadataReader(),
) : MetadataReader,
    AutoCloseable {
    private val admission = AndroidSourceAdmission(context)
    private val matcher = AndroidGenreMatcher.load(context, AndroidGnreRepository(context).installBundled().buffer)
    private val projection = CuetreeTagProjection(matcher, AndroidCedEncodingDetector)

    private val reader =
        AdmittedMetadataReader(
            MetadataReader { input, hint, cancellation -> projection.project(delegate.read(input, hint, cancellation)) },
            ::admit,
        )

    override fun read(
        input: MediaInput,
        hint: String,
        cancellation: Cancellation,
    ): Observation = reader.read(input, hint, cancellation)

    private fun admit(
        input: MediaInput,
        cancellation: Cancellation,
    ): AdmittedAudio {
        val signal =
            object : CancellationSignal {
                override val cancelled: Boolean
                    get() =
                        try {
                            cancellation.check()
                            false
                        } catch (_: java.util.concurrent.CancellationException) {
                            true
                        }

                override fun ensureAvailable() = cancellation.check()
            }
        return when (val result = admission.inspect(SourceMedia(input.id, input.id), signal)) {
            is Level1ScanResult.Success -> result.value
            Level1ScanResult.SourceUnavailable -> throw IOException("Audio source unavailable during admission")
            Level1ScanResult.ContentScanFailed -> throw IOException("Audio source is not handled by the playback stack")
        }
    }

    override fun close() = admission.close()
}
