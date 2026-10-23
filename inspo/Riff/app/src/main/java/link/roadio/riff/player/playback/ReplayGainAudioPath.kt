@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.content.Context
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.settings.PlayerReplayGainMode
import link.roadio.riff.player.core.settings.PlayerReplayGainTarget
import link.roadio.riff.player.library.PlayerReplayGainValue

/**
 * Stream and target revisions are separate because only a stream change resets the limiter.
 * A target-only change ramps gain on the existing path. Future playback controls must choose the
 * appropriate boundary rather than reuse queue callbacks that can arrive before audible PCM.
 */
internal class ReplayGainStreamGains {
    private val lock = Any()
    private var gainsByMediaId: Map<MediaId, PlayerReplayGainValue> = emptyMap()
    private var queueMediaIds: List<MediaId> = emptyList()
    private var selectedGainByMediaId: Map<MediaId, Short> = emptyMap()
    private val gainByOffset = linkedMapOf<Long, Int?>()
    private var activeRevision = 0L
    private var targetRevision = 0L
    private var activeCanonicalGainQ8: Int? = null
    private var target = PlayerReplayGainTarget.LUFS_23

    fun replace(values: Map<MediaId, PlayerReplayGainValue>) {
        synchronized(lock) {
            gainsByMediaId = values
            selectQueueGains()
        }
    }

    fun setQueue(mediaIds: List<MediaId>) {
        synchronized(lock) {
            queueMediaIds = mediaIds.toList()
            selectQueueGains()
            gainByOffset.clear()
            activate(null)
        }
    }

    fun setTarget(nextTarget: PlayerReplayGainTarget) {
        synchronized(lock) {
            if (target == nextTarget) return
            target = nextTarget
            targetRevision++
        }
    }

    fun register(
        positionOffsetUs: Long,
        mediaId: MediaId?,
    ) {
        synchronized(lock) {
            val gainQ8 = mediaId?.let(selectedGainByMediaId::get)?.toInt()
            gainByOffset.remove(positionOffsetUs)
            gainByOffset[positionOffsetUs] = gainQ8
            while (gainByOffset.size > MAX_REGISTERED_STREAMS) {
                gainByOffset.remove(gainByOffset.keys.first())
            }
        }
    }

    fun activate(positionOffsetUs: Long) {
        synchronized(lock) {
            activate(gainByOffset[positionOffsetUs])
        }
    }

    fun active(): ReplayGainActiveStream =
        synchronized(lock) {
            ReplayGainActiveStream(
                revision = activeRevision,
                targetRevision = targetRevision,
                gainQ8 = effectiveGainQ8(activeCanonicalGainQ8, target),
            )
        }

    fun clearOffsets() {
        synchronized(lock) {
            gainByOffset.clear()
            activate(null)
        }
    }

    private fun selectQueueGains() {
        selectedGainByMediaId = selectReplayGainQ8ByMediaId(queueMediaIds, gainsByMediaId)
    }

    private fun activate(gainQ8: Int?) {
        activeRevision++
        activeCanonicalGainQ8 = gainQ8
    }

    private companion object {
        const val MAX_REGISTERED_STREAMS = 32
    }
}

internal data class ReplayGainActiveStream(
    val revision: Long,
    val targetRevision: Long,
    val gainQ8: Int,
)

internal fun effectiveGainQ8(
    canonicalGainQ8: Int?,
    target: PlayerReplayGainTarget,
): Int =
    when {
        target == PlayerReplayGainTarget.ZERO -> 0
        canonicalGainQ8 == null -> 0
        else -> Math.addExact(canonicalGainQ8, requireNotNull(target.canonicalOffsetQ8))
    }

/** One active-queue policy shared by the audio path and passive Player diagnostics. */
internal fun selectReplayGainQ8ByMediaId(
    queueMediaIds: List<MediaId>,
    gainsByMediaId: Map<MediaId, PlayerReplayGainValue>,
): Map<MediaId, Short> {
    // Album gain is a property of the complete frozen queue; partial agreement would change levels at a boundary.
    // https://wiki.271.pics/Riff/LATEST/cuetree-analysis-integration
    val albumGains = queueMediaIds.map { gainsByMediaId[it]?.albumGainQ8 }
    val commonAlbumGain =
        albumGains
            .takeIf { it.isNotEmpty() && it.none { value -> value == null } }
            ?.filterNotNull()
            ?.distinct()
            ?.singleOrNull()
    return queueMediaIds
        .distinct()
        .mapNotNull { mediaId ->
            val selected = commonAlbumGain ?: gainsByMediaId[mediaId]?.trackGainQ8
            selected?.let { mediaId to it }
        }.toMap()
}

internal class ReplayGainRenderersFactory(
    context: Context,
    private val mode: PlayerReplayGainMode,
    private val streamGains: ReplayGainStreamGains,
) : DefaultRenderersFactory(context) {
    init {
        require(mode != PlayerReplayGainMode.N)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParameters: Boolean,
    ): AudioSink {
        val replayGain =
            ReplayGainAudioProcessor(
                if (mode == PlayerReplayGainMode.I) ReplayGainOutputMode.INTEGER else ReplayGainOutputMode.FLOAT,
                streamGains,
            )
        val delegate =
            DefaultAudioSink
                .Builder(context)
                // ReplayGain must be last: Media3's silence/sonic processors accept PCM16,
                // while mode F deliberately changes the final encoding to PCM_FLOAT.
                .setAudioProcessorChain(ReplayGainAudioProcessorChain(replayGain))
                .setEnableFloatOutput(false)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
                .build()
        return ReplayGainAudioSink(delegate, streamGains)
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        out +=
            ReplayGainAudioRenderer(
                context,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                audioSink,
                streamGains,
            )
    }
}

/** Bridges Media3's active renderer stream boundary into the processor's gain state. */
private class ReplayGainAudioSink(
    delegate: AudioSink,
    private val streamGains: ReplayGainStreamGains,
) : ForwardingAudioSink(delegate) {
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        streamGains.activate(outputStreamOffsetUs)
        super.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }
}

private class ReplayGainAudioProcessorChain(
    replayGain: ReplayGainAudioProcessor,
) : AudioProcessorChain {
    private val standard = DefaultAudioSink.DefaultAudioProcessorChain()
    private val processors = standard.audioProcessors + replayGain

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
        standard.applyPlaybackParameters(playbackParameters)

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = standard.applySkipSilenceEnabled(skipSilenceEnabled)

    override fun getMediaDuration(playoutDuration: Long): Long = standard.getMediaDuration(playoutDuration)

    override fun getSkippedOutputFrameCount(): Long = standard.skippedOutputFrameCount
}

private class ReplayGainAudioRenderer(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink,
    private val streamGains: ReplayGainStreamGains,
) : MediaCodecAudioRenderer(
        context,
        mediaCodecSelector,
        enableDecoderFallback,
        eventHandler,
        eventListener,
        audioSink,
    ) {
    private val period = Timeline.Period()
    private val window = Timeline.Window()

    override fun onStreamChanged(
        formats: Array<Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        streamGains.register(offsetUs, mediaId(mediaPeriodId))
        super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)
    }

    private fun mediaId(mediaPeriodId: MediaSource.MediaPeriodId): MediaId? =
        runCatching {
            val timeline = timeline
            timeline.getPeriodByUid(mediaPeriodId.periodUid, period)
            timeline.getWindow(period.windowIndex, window)
            window.mediaItem.mediaMetadata.extras
                .mediaIdOrNull()
        }.getOrNull()
}
