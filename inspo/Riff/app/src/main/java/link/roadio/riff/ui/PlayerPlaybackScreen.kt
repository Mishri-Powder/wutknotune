@file:Suppress("FunctionName")

package link.roadio.riff.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import link.roadio.riff.player.core.playback.AudiobookSpeed

@Composable
internal fun PlaybackScreen(
    state: PlayerUiState,
    night: Boolean,
) {
    val palette = LocalRiffPalette.current
    val config = LocalRiffConfig.current
    val dispatch = LocalRiffDispatch.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(palette.background))
            .backgroundFlipTap { dispatch(PlayerUiAction.TogglePlaybackSurface) }
            .testTag("screen:PLAYBACK"),
    ) {
        val wide = config.wide
        // AI-INTENT: Only local playback row packing follows viewport aspect; tokens remain width-resolved.
        // https://wiki.271.pics/Riff/LATEST/player-transport#playback-surface-and-transport
        val portrait = maxHeight > maxWidth
        val scroll = rememberScrollState()
        val seekHeight = PLAYBACK_SEEK_HEIGHT_DP.dp
        val safeContent =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .padding(uiDimension("screenEdgeMargin"))
        RiffStatusOverlay(playbackStatusLine(state.playbackDiagnostics), artworkBackdrop = true)
        val artworkModifier =
            if (wide) {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .align(Alignment.CenterEnd)
            } else {
                Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                    .padding(uiDimension("targetSpacingMin"))
                    .align(Alignment.TopCenter)
                    .padding(top = (TOP_BAR_DP + 24f).dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
            }
        Artwork(
            bitmap = state.nowPlaying.artwork.bitmap,
            night = night,
            alpha = config.int("behavior.artworkAlpha") / 255f,
            modifier = artworkModifier,
        )
        BoxWithConstraints(safeContent.testTag("playback:safe")) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll, reverseScrolling = true)
                    .heightIn(min = maxHeight),
            ) {
                RiffTopBar(
                    leading =
                        listOf(
                            TopActionSpec(
                                "flip",
                                "Switch to library",
                                action = PlayerUiAction.TogglePlaybackSurface,
                            ),
                        ),
                    wide = wide,
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                    ) {
                        RiffText(
                            state.nowPlaying.title,
                            uiTextSize("textTitlePlayback"),
                            palette.text,
                            medium = true,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            softWrap = false,
                            artworkBackdrop = true,
                        )
                        Spacer(Modifier.height(PLAYBACK_TITLE_ARTIST_GAP_DP.dp))
                        RiffText(
                            state.nowPlaying.artist,
                            44f,
                            palette.primary,
                            medium = true,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            softWrap = false,
                            artworkBackdrop = true,
                        )
                        Spacer(Modifier.height(PLAYBACK_ARTIST_ALBUM_GAP_DP.dp))
                        RiffText(
                            state.nowPlaying.album,
                            32f,
                            palette.muted,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            softWrap = false,
                            artworkBackdrop = true,
                        )
                        state.nowPlaying.chapter?.let { chapter ->
                            Spacer(Modifier.height(PLAYBACK_ALBUM_CHAPTER_GAP_DP.dp))
                            RiffText(
                                chapter,
                                uiTextSize("textSecondary"),
                                palette.muted,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                softWrap = false,
                                artworkBackdrop = true,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (portrait) {
                    PlaybackTransportRow(state, wide, listOf(1, 3))
                    Spacer(Modifier.height(uiDimension("targetSpacingMin")))
                    PlaybackTransportRow(state, wide, listOf(0, 2, 4))
                } else {
                    PlaybackTransportRow(state, wide, listOf(0, 1, 2, 3, 4))
                }
                RiffSeekBar(
                    value = state.nowPlaying.positionFraction,
                    onSeek = { dispatch(PlayerUiAction.Seek(it)) },
                    modifier = Modifier.fillMaxWidth().height(seekHeight).testTag("playback:seek"),
                )
                val currentMediaId = state.nowPlaying.mediaId
                val reaction =
                    currentMediaId?.let { state.reactions[trackReactionKey(it)] } ?: ReactionState()
                FullWidthButtonRow(
                    buttonCount = 3,
                    modifier = Modifier.fillMaxWidth().height(uiDimension("playbackButtonHeight")),
                ) { index, modifier ->
                    if (index == 0 && state.contentMode == ContentMode.AUDIOBOOK && state.nowPlaying.effectiveBook) {
                        RiffIconButton(
                            "ban",
                            "Audiobook speed ${state.audiobookSpeed.label}; change to ${state.audiobookSpeed.next().label}",
                            text = state.audiobookSpeed.label,
                            enabled = state.selectedStorageAvailable && currentMediaId != null,
                            wide = wide,
                            artworkBackdrop = true,
                            onClick = { dispatch(PlayerUiAction.AdvanceAudiobookSpeed) },
                            onLongClick = { dispatch(PlayerUiAction.ResetAudiobookSpeed) },
                            onLongClickLabel = "Reset audiobook speed to ${AudiobookSpeed.NORMAL.label}",
                            modifier = modifier,
                        )
                        return@FullWidthButtonRow
                    }
                    if (state.nowPlaying.bookTitleContext || !state.nowPlaying.aiReactionEligible) {
                        if (index == 2 && state.nowPlaying.bookTitleContext) {
                            RiffIconButton(
                                "bookmark_add",
                                "Bookmark current position",
                                enabled = state.bookmarkMarkAllowed && currentMediaId != null,
                                wide = wide,
                                artworkBackdrop = true,
                                onClick = { dispatch(PlayerUiAction.MarkBookmark) },
                                modifier = modifier,
                            )
                        } else {
                            Spacer(modifier)
                        }
                        return@FullWidthButtonRow
                    }
                    when (index) {
                        0 -> {
                            RiffIconButton(
                                if (reaction.ownBanned) "ban_filled" else "ban",
                                "Ban current track",
                                selected = reaction.ownBanned,
                                enabled = currentMediaId != null,
                                wide = wide,
                                artworkBackdrop = true,
                                onClick = {
                                    dispatch(PlayerUiAction.SetCurrentReaction(toggleBan = true))
                                },
                                modifier = modifier,
                            )
                        }

                        1 -> {
                            RiffIconButton(
                                if (reaction.preference == Preference.DISLIKED) {
                                    "dislike_filled"
                                } else {
                                    "dislike"
                                },
                                "Dislike current track",
                                selected = reaction.preference == Preference.DISLIKED,
                                enabled = currentMediaId != null,
                                wide = wide,
                                artworkBackdrop = true,
                                onClick = {
                                    dispatch(
                                        PlayerUiAction.SetCurrentReaction(
                                            preference = Preference.DISLIKED,
                                        ),
                                    )
                                },
                                modifier = modifier,
                            )
                        }

                        else -> {
                            RiffIconButton(
                                if (reaction.preference == Preference.LIKED) {
                                    "like_filled"
                                } else {
                                    "like"
                                },
                                "Like current track",
                                selected = reaction.preference == Preference.LIKED,
                                enabled = currentMediaId != null,
                                wide = wide,
                                artworkBackdrop = true,
                                onClick = {
                                    dispatch(
                                        PlayerUiAction.SetCurrentReaction(
                                            preference = Preference.LIKED,
                                        ),
                                    )
                                },
                                modifier = modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaybackTransportRow(
    state: PlayerUiState,
    wide: Boolean,
    indices: List<Int>,
) {
    val dispatch = LocalRiffDispatch.current
    FullWidthButtonRow(
        buttonCount = indices.size,
        modifier = Modifier.fillMaxWidth().height(uiDimension("playbackButtonHeight")),
    ) { index, modifier ->
        when (indices[index]) {
            0 -> {
                RiffIconButton(
                    "shuffle",
                    "Shuffle ${if (state.nowPlaying.shuffle) "on" else "off"}",
                    selected = state.nowPlaying.shuffle,
                    wide = wide,
                    artworkBackdrop = true,
                    onClick = { dispatch(PlayerUiAction.ToggleShuffle) },
                    modifier = modifier,
                )
            }

            1 -> {
                RiffIconButton(
                    "rew",
                    "Previous",
                    wide = wide,
                    artworkBackdrop = true,
                    onClick = { dispatch(PlayerUiAction.Previous) },
                    modifier = modifier,
                )
            }

            2 -> {
                RiffIconButton(
                    if (state.nowPlaying.playing) "pause" else "play",
                    if (state.nowPlaying.playing) "Pause" else "Play",
                    wide = wide,
                    artworkBackdrop = true,
                    onClick = { dispatch(PlayerUiAction.TogglePlayback) },
                    modifier = modifier,
                )
            }

            3 -> {
                RiffIconButton(
                    "fwd",
                    "Next",
                    wide = wide,
                    artworkBackdrop = true,
                    onClick = { dispatch(PlayerUiAction.Next) },
                    modifier = modifier,
                )
            }

            else -> {
                RiffIconButton(
                    "repeat",
                    "Repeat ${if (state.nowPlaying.repeat) "on" else "off"}",
                    selected = state.nowPlaying.repeat,
                    wide = wide,
                    artworkBackdrop = true,
                    onClick = { dispatch(PlayerUiAction.ToggleRepeat) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
internal fun RiffStatusOverlay(
    value: String,
    artworkBackdrop: Boolean = false,
) {
    // AI-INTENT: Status is viewport-fixed at the physical safe corner, independent of padded/scrolled controls.
    // https://wiki.271.pics/Riff/LATEST/player-transport#playback-diagnostic-status
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .clipToBounds()
            .testTag("status:viewport"),
    ) {
        RiffStatusLine(value, artworkBackdrop, Modifier.align(AbsoluteAlignment.TopLeft))
    }
}

@Composable
internal fun RiffStatusLine(
    value: String,
    artworkBackdrop: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val config = LocalRiffConfig.current
    if (!config.boolean("behavior.enableStatusLines") || value.isEmpty()) return
    RiffText(
        value,
        config.float("behavior.statusLineTextSp"),
        LocalRiffPalette.current.muted,
        monospace = true,
        textAlign = TextAlign.Left,
        modifier = modifier.fillMaxWidth().testTag("status-line").clearAndSetSemantics {},
        maxLines = 1,
        softWrap = false,
        artworkBackdrop = artworkBackdrop,
    )
}

@Composable
internal fun Artwork(
    bitmap: Bitmap?,
    night: Boolean,
    alpha: Float,
    modifier: Modifier,
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Track artwork",
            contentScale = ContentScale.Crop,
            modifier = modifier.alpha(alpha.coerceIn(0f, 1f)),
        )
    } else {
        RiffDrawable(
            drawableName = if (night) "ic_default_artwork_night" else "ic_default_artwork_day",
            contentDescription = "Track artwork",
            modifier = modifier.alpha(alpha.coerceIn(0f, 1f)),
        )
    }
}

@Composable
internal fun FullWidthButtonRow(
    buttonCount: Int,
    modifier: Modifier,
    button: @Composable (Int, Modifier) -> Unit,
) {
    val target = uiDimension("touchTargetMin")
    val gap = uiDimension("targetSpacingMin")
    BoxWithConstraints(modifier) {
        val minimum = buttonCount * target.value + (buttonCount - 1) * gap.value
        val fits = maxWidth.value >= minimum
        Row(
            Modifier
                .fillMaxSize()
                .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState())),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(buttonCount) { index ->
                if (index > 0) Spacer(Modifier.width(uiDimension("targetSpacingMin")))
                button(
                    index,
                    if (fits) {
                        Modifier.weight(1f).fillMaxHeight()
                    } else {
                        Modifier.width(uiDimension("touchTargetMin")).fillMaxHeight()
                    },
                )
            }
        }
    }
}

@Composable
internal fun RiffSeekBar(
    value: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier,
) {
    val palette = LocalRiffPalette.current
    val config = LocalRiffConfig.current
    val normalized = value.coerceIn(0f, 1f)
    Canvas(
        modifier
            .contrastHalo(if (config.night) Color.Black else Color.White)
            .semantics {
                contentDescription = "Playback position"
                progressBarRangeInfo = ProgressBarRangeInfo(normalized, 0f..1f)
                setProgress {
                    onSeek(it.coerceIn(0f, 1f))
                    true
                }
            }.pointerInput(onSeek) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }.pointerInput(onSeek) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    },
                ) { change, _ ->
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                    change.consume()
                }
            }.focusable(),
    ) {
        val y = size.height / 2f
        val trackStart = 12.dp.toPx()
        val trackEnd = size.width - 12.dp.toPx()
        val progressX = trackStart + (trackEnd - trackStart) * normalized
        drawLine(
            Color(palette.outline),
            Offset(trackStart, y),
            Offset(trackEnd, y),
            4.dp.toPx(),
        )
        drawLine(
            Color(palette.primary),
            Offset(trackStart, y),
            Offset(progressX, y),
            6.dp.toPx(),
        )
        drawCircle(Color(palette.primary), 12.dp.toPx(), Offset(progressX, y))
    }
}

internal const val PLAYBACK_SEEK_HEIGHT_DP = 64f
