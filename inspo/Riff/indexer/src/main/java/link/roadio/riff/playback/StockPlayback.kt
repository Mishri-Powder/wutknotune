package link.roadio.riff.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory

/** One stock playback configuration shared by Player and source-capability inspection. */
@UnstableApi
object StockPlayback {
    fun renderers(context: Context): DefaultRenderersFactory = DefaultRenderersFactory(context)

    fun sources(
        dataSource: DataSource.Factory,
        extractors: ExtractorsFactory = DefaultExtractorsFactory(),
    ): DefaultMediaSourceFactory = DefaultMediaSourceFactory(dataSource, extractors)
}
