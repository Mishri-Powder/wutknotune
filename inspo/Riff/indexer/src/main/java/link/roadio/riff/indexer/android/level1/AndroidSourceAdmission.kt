package link.roadio.riff.indexer.android.level1

import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultRendererCapabilitiesList
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.inspector.MetadataRetriever
import link.roadio.riff.indexer.android.media.AndroidDecoderHint
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.indexer.level1.Level1ScanResult
import link.roadio.riff.indexer.level1.Level1SourceAdmission
import link.roadio.riff.playback.StockPlayback
import link.roadio.riff.storage.hash.nativeXxh3
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class AdmittedAudio(
    val decoderHint: Long,
    val durationUs: Long? = null,
)

/** Actual-container/track inspection; the decoder inventory is never an admission table. */
@UnstableApi
class AndroidSourceAdmission(
    private val context: Context,
    private val timeoutMillis: Long = 30_000L,
    private val maxReadBytes: Long = 64L * 1024 * 1024,
) : Level1SourceAdmission,
    AutoCloseable {
    init {
        require(timeoutMillis > 0 && maxReadBytes > 0)
    }

    private val workerHolder: Lazy<HandlerThread> = lazy { HandlerThread("RiffSourceCapabilities").apply { start() } }
    private val worker: HandlerThread by workerHolder
    private val rendererHolder: Lazy<DefaultRendererCapabilitiesList> =
        lazy {
            DefaultRendererCapabilitiesList.Factory(StockPlayback.renderers(context)).createRendererCapabilitiesList()
        }
    private val renderers: DefaultRendererCapabilitiesList by rendererHolder
    val revision: String by lazy {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
        val decoders = codecs.map { it.name + ":" + it.supportedTypes.sorted().joinToString(",") }.sorted()
        val audioTypes = codecs.flatMap { it.supportedTypes.toList() }.filter(MimeTypes::isAudio).toSortedSet()
        Log.i("RiffIndexer", "audio_decoder_inventory=" + audioTypes.joinToString(",").take(2048))
        val identity = (listOf(Build.FINGERPRINT, MediaLibraryInfo.VERSION) + decoders).joinToString("\n")
        val digest = nativeXxh3(identity.toByteArray(Charsets.UTF_8))
        "SOURCE-CAPABILITIES-V3/" +
            java.lang.Long
                .toUnsignedString(digest, 16)
                .padStart(16, '0')
    }

    override fun check(
        source: SourceMedia,
        cancellation: CancellationSignal,
    ): Level1ScanResult<Long> =
        when (val result = inspect(source, cancellation)) {
            is Level1ScanResult.Success -> Level1ScanResult.Success(result.value.decoderHint)
            Level1ScanResult.SourceUnavailable -> Level1ScanResult.SourceUnavailable
            Level1ScanResult.ContentScanFailed -> Level1ScanResult.ContentScanFailed
        }

    fun inspect(
        source: SourceMedia,
        cancellation: CancellationSignal,
    ): Level1ScanResult<AdmittedAudio> {
        check(Looper.myLooper() != Looper.getMainLooper())
        cancellation.ensureAvailable()
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        val task = FutureTask { checkOnWorker(source, cancellation, deadline) }
        check(Handler(worker.looper).post(task))
        return try {
            awaitResult(task, cancellation, deadline)
        } catch (error: IOException) {
            cancellation.ensureAvailable()
            Level1ScanResult.SourceUnavailable
        } finally {
            task.cancel(false)
        }
    }

    private fun checkOnWorker(
        source: SourceMedia,
        cancellation: CancellationSignal,
        deadline: Long,
    ): Level1ScanResult<AdmittedAudio> {
        val stopped = AtomicBoolean()
        val bytes = AtomicLong()
        val upstream = DefaultDataSource.Factory(context)
        val factory =
            DataSource.Factory {
                ProbeDataSource(upstream.createDataSource(), cancellation, stopped, bytes, deadline, maxReadBytes)
            }
        // Player also exposes an opaque occurrence URI and resolves the actual SAF URI at open.
        val resolvingFactory = ResolvingDataSource.Factory(factory) { spec -> spec.withUri(Uri.parse(source.locator)) }
        val retriever =
            MetadataRetriever
                .Builder(context, MediaItem.fromUri("riff-inspect://source"))
                .setMediaSourceFactory(StockPlayback.sources(resolvingFactory, BudgetedAdmissionExtractors()))
                .build()
        val future = retriever.retrieveTrackGroups()
        try {
            val groups = awaitResult(future, cancellation, deadline)
            for (groupIndex in 0 until groups.length) {
                val group = groups[groupIndex]
                for (trackIndex in 0 until group.length) {
                    val format = group.getFormat(trackIndex)
                    if (!MimeTypes.isAudio(format.sampleMimeType)) continue
                    if (renderers.rendererCapabilities.any { renderer ->
                            renderer.trackType == C.TRACK_TYPE_AUDIO &&
                                RendererCapabilities.getFormatSupport(renderer.supportsFormat(format)) == C.FORMAT_HANDLED
                        }
                    ) {
                        // AI-INTENT: Duration shares this probe and its budget. Missing duration
                        // cannot turn supported audio into an admission failure.
                        // https://wiki.271.pics/Riff/LATEST/indexer-media-admission
                        val duration =
                            try {
                                awaitResult(retriever.retrieveDurationUs(), cancellation, deadline).takeIf { it > 0 }
                            } catch (_: Exception) {
                                cancellation.ensureAvailable()
                                null
                            }
                        return Level1ScanResult.Success(AdmittedAudio(AndroidDecoderHint.from(format), duration))
                    }
                }
            }
            return Level1ScanResult.ContentScanFailed
        } catch (error: Exception) {
            cancellation.ensureAvailable()
            val causes = generateSequence<Throwable>(error) { it.cause }.toList()
            return if (causes.any { it is ParserException }) {
                Level1ScanResult.ContentScanFailed
            } else if (causes.any { it is IOException || it is SecurityException }) {
                Level1ScanResult.SourceUnavailable
            } else {
                Level1ScanResult.ContentScanFailed
            }
        } finally {
            stopped.set(true)
            future.cancel(false)
            retriever.close()
        }
    }

    private fun <T> awaitResult(
        future: java.util.concurrent.Future<T>,
        cancellation: CancellationSignal,
        deadline: Long,
    ): T {
        while (true) {
            cancellation.ensureAvailable()
            if (SystemClock.elapsedRealtime() >= deadline) throw InterruptedIOException("Audio probe timed out")
            try {
                return future.get(50, TimeUnit.MILLISECONDS)
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            } catch (_: TimeoutException) {
                // Poll cancellation while Media3 prepares on its own worker.
            }
        }
    }

    override fun close() {
        if (!workerHolder.isInitialized()) return
        val task =
            FutureTask {
                try {
                    if (rendererHolder.isInitialized()) renderers.release()
                } finally {
                    worker.quitSafely()
                }
            }
        check(Handler(worker.looper).post(task))
        task.get(30, TimeUnit.SECONDS)
    }

    private class ProbeDataSource(
        private val delegate: DataSource,
        private val cancellation: CancellationSignal,
        private val stopped: AtomicBoolean,
        private val bytes: AtomicLong,
        private val deadline: Long,
        private val maxBytes: Long,
    ) : DataSource {
        private fun checkAvailable() {
            if (stopped.get() || cancellation.cancelled || SystemClock.elapsedRealtime() >= deadline) {
                throw InterruptedIOException("Audio probe stopped")
            }
        }

        override fun open(dataSpec: DataSpec): Long {
            checkAvailable()
            return delegate.open(dataSpec)
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            checkAvailable()
            val remaining = maxBytes - bytes.get()
            if (remaining <= 0) throw InterruptedIOException("Audio probe read budget exhausted")
            val read = delegate.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (read > 0) bytes.addAndGet(read.toLong())
            return read
        }

        override fun getUri(): Uri? = delegate.uri

        override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

        override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)

        override fun close() = delegate.close()
    }
}
