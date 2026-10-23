package link.roadio.riff.player.playback

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.settings.PlayerReplayGainMode
import link.roadio.riff.player.core.settings.PlayerReplayGainTarget
import link.roadio.riff.ui.ArtworkUiState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class PlaybackEngineState(
    val queueRevisionId: Long? = null,
    val occurrenceId: Long? = null,
    val mediaId: MediaId? = null,
    val chosenBindingId: Long? = null,
    val title: String = "Nothing played yet",
    val artist: String = "",
    val album: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val playbackState: Int = 0,
    val artwork: ArtworkUiState? = null,
    val error: String? = null,
)

internal object PlaybackMetadata {
    const val URI_SCHEME = "riff-occurrence"
    const val EXTRA_QUEUE_REVISION_ID = "link.roadio.riff.extra.QUEUE_REVISION_ID"
    const val EXTRA_OCCURRENCE_ID = "link.roadio.riff.extra.OCCURRENCE_ID"
    const val EXTRA_MEDIA_ID_HIGH = "link.roadio.riff.extra.MEDIA_ID_HIGH"
    const val EXTRA_MEDIA_ID_LOW = "link.roadio.riff.extra.MEDIA_ID_LOW"
    const val EXTRA_MEDIA_ID_LOW_PRESENT = "link.roadio.riff.extra.MEDIA_ID_LOW_PRESENT"
    const val EXTRA_BINDING_ID = "link.roadio.riff.extra.BINDING_ID"

    fun mediaItemId(occurrenceId: Long): String = "riff-occurrence:${occurrenceId.toULong().toString(16)}"
}

internal fun Bundle.putMediaId(mediaId: MediaId) {
    putLong(PlaybackMetadata.EXTRA_MEDIA_ID_HIGH, mediaId.high)
    putLong(PlaybackMetadata.EXTRA_MEDIA_ID_LOW, mediaId.low)
    putBoolean(PlaybackMetadata.EXTRA_MEDIA_ID_LOW_PRESENT, mediaId.lowPresent)
}

internal fun Bundle?.mediaIdOrNull(): MediaId? =
    this
        ?.takeIf {
            it.containsKey(PlaybackMetadata.EXTRA_MEDIA_ID_HIGH) &&
                it.containsKey(PlaybackMetadata.EXTRA_MEDIA_ID_LOW) &&
                it.containsKey(PlaybackMetadata.EXTRA_MEDIA_ID_LOW_PRESENT)
        }?.let {
            MediaId(
                it.getLong(PlaybackMetadata.EXTRA_MEDIA_ID_HIGH),
                it.getLong(PlaybackMetadata.EXTRA_MEDIA_ID_LOW),
                it.getBoolean(PlaybackMetadata.EXTRA_MEDIA_ID_LOW_PRESENT),
            )
        }

/** Same-process UI bridge into the service-owned queue and shared transport dispatcher. */
internal object PlayerPlaybackBridge {
    private val main = Handler(Looper.getMainLooper())
    private var owner: Any? = null
    private var reload: (() -> Unit)? = null
    private var refreshLibrary: (() -> Unit)? = null
    private var previous: (() -> Unit)? = null
    private var next: (() -> Unit)? = null
    private var checkpoint: (() -> Unit)? = null
    private var flush: (((Boolean) -> Unit) -> Unit)? = null
    private var quiesce: ((() -> Unit) -> Unit)? = null
    private var replayGainSettings: ((PlayerReplayGainMode, PlayerReplayGainTarget) -> Unit)? = null
    private var audiobookParametersRefresh: (() -> Unit)? = null
    private var pendingAudiobookParametersRefresh = false
    private var pendingReload = false
    private var pendingLibraryRefresh = false
    private var pendingReplayGainSettings: Pair<PlayerReplayGainMode, PlayerReplayGainTarget>? = null
    private var artworkLookup: ((Long?, Long?, Long?) -> ArtworkUiState?)? = null

    fun attach(
        newOwner: Any,
        reloadAction: () -> Unit,
        refreshLibraryAction: () -> Unit,
        previousAction: () -> Unit,
        nextAction: () -> Unit,
        checkpointAction: () -> Unit,
        flushAction: ((Boolean) -> Unit) -> Unit,
        quiesceAction: ((() -> Unit) -> Unit),
        replayGainSettingsAction: (PlayerReplayGainMode, PlayerReplayGainTarget) -> Unit,
        audiobookParametersRefreshAction: () -> Unit,
        artworkLookupAction: (Long?, Long?, Long?) -> ArtworkUiState? = { _, _, _ -> null },
    ) {
        main.post {
            artworkLookup = artworkLookupAction
            owner = newOwner
            reload = reloadAction
            refreshLibrary = refreshLibraryAction
            previous = previousAction
            next = nextAction
            checkpoint = checkpointAction
            flush = flushAction
            quiesce = quiesceAction
            replayGainSettings = replayGainSettingsAction
            audiobookParametersRefresh = audiobookParametersRefreshAction
            if (pendingAudiobookParametersRefresh) {
                pendingAudiobookParametersRefresh = false
                audiobookParametersRefreshAction()
            }
            if (pendingReload) {
                pendingReload = false
                reloadAction()
            }
            if (pendingLibraryRefresh) {
                pendingLibraryRefresh = false
                refreshLibraryAction()
            }
            pendingReplayGainSettings?.let { (mode, target) ->
                pendingReplayGainSettings = null
                replayGainSettingsAction(mode, target)
            }
        }
    }

    fun detach(oldOwner: Any) {
        main.post {
            if (owner === oldOwner) {
                owner = null
                reload = null
                refreshLibrary = null
                previous = null
                next = null
                checkpoint = null
                flush = null
                quiesce = null
                replayGainSettings = null
                audiobookParametersRefresh = null
                artworkLookup = null
            }
        }
    }

    fun artworkFor(
        queueRevisionId: Long?,
        occurrenceId: Long?,
        bindingId: Long?,
    ): ArtworkUiState? {
        check(Looper.myLooper() == Looper.getMainLooper())
        return artworkLookup?.invoke(queueRevisionId, occurrenceId, bindingId)
    }

    fun requestQueueReload() {
        main.post {
            val action = reload
            if (action == null) pendingReload = true else action()
        }
    }

    fun requestLibraryRefresh() {
        main.post {
            val action = refreshLibrary
            if (action == null) pendingLibraryRefresh = true else action()
        }
    }

    fun requestAudiobookParametersRefresh() {
        main.post {
            val action = audiobookParametersRefresh
            if (action == null) pendingAudiobookParametersRefresh = true else action()
        }
    }

    fun requestPrevious() {
        main.post { previous?.invoke() }
    }

    fun requestNext() {
        main.post { next?.invoke() }
    }

    fun requestCheckpoint() {
        main.post { checkpoint?.invoke() }
    }

    fun requestReplayGainSettings(
        mode: PlayerReplayGainMode,
        target: PlayerReplayGainTarget,
    ) {
        main.post {
            val action = replayGainSettings
            if (action == null) pendingReplayGainSettings = mode to target else action(mode, target)
        }
    }

    /** Called off main before replacing a context or switching the active user. */
    fun flushForContextChange(): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper())
        val complete = CountDownLatch(1)
        var succeeded = false
        main.post {
            val action = flush
            if (action == null) {
                succeeded = true
                complete.countDown()
            } else {
                action { result ->
                    succeeded = result
                    complete.countDown()
                }
            }
        }
        return complete.await(5, TimeUnit.SECONDS) && succeeded
    }

    fun quiesceForStorageChange(timeoutSeconds: Long = 5L): Boolean {
        val complete = CountDownLatch(1)
        main.post {
            val action = quiesce
            if (action == null) complete.countDown() else action(complete::countDown)
        }
        return complete.await(timeoutSeconds, TimeUnit.SECONDS)
    }
}
