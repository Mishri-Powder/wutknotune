package com.example.musicapp.ui.wall

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.PointerVelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFrameClock
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.musicapp.data.Song
import com.example.musicapp.ui.ArtworkLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Infinite poster wall — a port of the Iris Music PosterWall feature.
 *
 * Everything that made that wall feel good is here:
 *  - deterministic mosaic packing (mix of 1x1 / 2x1 / 1x2 / 2x2 cards, no gaps)
 *  - the playing card expands to 2x2 and neighbors physically make way (solveWall)
 *  - four-directional infinite tiling with fling inertia (vsync-aligned decay)
 *  - pan + pinch "camera" zoom on a single graphicsLayer (zero relayout per frame)
 *  - row-bucketed visibility culling so dragging never re-computes the whole library
 *  - tile move/size animations (eased tween), active-card progress bar with drag-seek
 *  - tap a card to play it; haptic tick every quarter-cell while dragging the wall
 */

private val CELL_BASE = 118.dp
private const val CAM_MIN = 0.6f
private const val CAM_MAX = 1.9f
private const val WARM_MAX = 240
private const val VISIBLE_ROW_PAD = 2

/** Stable identity for one rendered tile (tile + replica offset); data-class equals()
 *  keeps derivedStateOf from producing a new list identity unless membership changed. */
private data class VisibleTile(val tile: WallTile, val rx: Int, val ry: Int)

/** Public stand-in for Compose's private awaitEachGesture (same semantics):
 *  runs [block] once per gesture; when the block returns, consumes all remaining
 *  pointers so the next gesture starts clean. */
private suspend fun AwaitPointerEventScope.awaitEachGestureWall(block: suspend AwaitPointerEventScope.() -> Unit) {
    while (true) {
        block()
        var event = currentEvent
        while (event.changes.any { it.pressed }) {
            event.changes.forEach { if (it.pressed) it.consume() }
            event = awaitPointerEvent()
        }
        // No pointers down anymore — loop back and wait for the next gesture.
    }
}

@Composable
fun PosterWall(
    songs: List<Song>,
    currentId: Long?,
    isPlaying: Boolean,
    positionProvider: () -> Pair<Long, Long>,   // (positionMs, durationMs) — read only by the active tile
    seekProvider: (Float) -> Unit,              // fraction 0..1 -> player.seekTo
    onSelect: (Song) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No local songs found", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
        return
    }

    val density = LocalDensity.current
    val view = LocalView.current

    // ---- camera state (floats written per-frame during drag, read only in graphicsLayer)
    val offsetX = remember { mutableFloatStateOf(0f) }
    val offsetY = remember { mutableFloatStateOf(0f) }
    val camScale = remember { mutableFloatStateOf(1f) }
    val camPivotX = remember { mutableFloatStateOf(0f) }
    val camPivotY = remember { mutableFloatStateOf(0f) }

    val step = remember { with(density) { CELL_BASE.toPx() } }
    val gap = with(density) { 6.dp.toPx() }

    var viewW by remember { mutableFloatStateOf(0f) }
    var viewH by remember { mutableFloatStateOf(0f) }

    // ---- base layout: rebuilt only when the song list changes (stable seed!)
    val packed = remember(songs) {
        packWall(count = songs.size, cols = 7, seed = 0x9E3779B97F4A7C15uL.toLong())
    }
    val allTiles = packed.first
    val rows = max(packed.second, 4)
    val cols = max(packed.third, 4)

    val currentIndex = songs.indexOfFirst { it.id == currentId }

    // ---- solver runs off the main thread; UI shows base geometry until results land
    var solvedMap by remember { mutableStateOf(emptyMap<WallTile, SolvedTile>()) }
    LaunchedEffect(allTiles, currentIndex) {
        if (currentIndex < 0) { solvedMap = emptyMap(); return@LaunchedEffect }
        val out = withContext(Dispatchers.Default) {
            solveWall(allTiles, cols, rows, currentIndex)
        }
        solvedMap = out.associateBy { it.tile }
    }

    val canvasW = cols * step
    val canvasH = rows * step

    // center the camera on the playing tile once when its identity changes
    var centeredOn by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(currentId, canvasW, canvasH, viewW, viewH) {
        if (currentId == null || centeredOn == currentId || canvasW <= 0f || viewW <= 0f) return@LaunchedEffect
        val tile = allTiles.firstOrNull { it.index == currentIndex } ?: return@LaunchedEffect
        val cx = (tile.col + tile.spanW / 2f) * step
        val cy = (tile.row + tile.spanH / 2f) * step
        if (canvasW > viewW) offsetX.floatValue = (viewW / 2f - cx).coerceIn(viewW - canvasW, 0f)
        else offsetX.floatValue = (viewW - canvasW) / 2f
        if (canvasH > viewH) offsetY.floatValue = (viewH / 2f - cy).coerceIn(viewH - canvasH, 0f)
        else offsetY.floatValue = (viewH - canvasH) / 2f
        centeredOn = currentId
    }

    // ---- quantized camera for visibility culling (recompute on quantum crossings only)
    val qox = remember { mutableFloatStateOf(0f) }
    val qoy = remember { mutableFloatStateOf(0f) }
    val qcam = remember { mutableFloatStateOf(1f) }
    val qpivX = remember { mutableFloatStateOf(0f) }
    val qpivY = remember { mutableFloatStateOf(0f) }
    val visibleQuantum = step * 2f
    LaunchedEffect(visibleQuantum, step) {
        launch {
            snapshotFlow {
                floor(offsetX.floatValue / visibleQuantum) * visibleQuantum to
                        floor(offsetY.floatValue / visibleQuantum) * visibleQuantum
            }.distinctUntilChanged().collect {
                qox.floatValue = it.first; qoy.floatValue = it.second
            }
        }
        snapshotFlow {
            Triple(
                floor(camScale.floatValue / 0.05f) * 0.05f,
                floor(camPivotX.floatValue / step) * step,
                floor(camPivotY.floatValue / step) * step
            )
        }.distinctUntilChanged().collect {
            qcam.floatValue = max(it.first, 0.05f)
            qpivX.floatValue = it.second
            qpivY.floatValue = it.third
        }
    }

    // ---- two-level visibility index: stable tiles bucketed by base row, moved tiles listed
    val (rowBuckets, displacedTiles) = remember(allTiles, solvedMap, rows, cols) {
        val buckets = Array(rows.coerceAtLeast(1)) { ArrayList<WallTile>(cols * 2) }
        val disp = ArrayList<WallTile>(64)
        for (t in allTiles) {
            val g = solvedMap[t]
            if (g != null && (g.col != t.col || g.row != t.row)) disp.add(t)
            else buckets[t.row.coerceIn(0, rows - 1)].add(t)
        }
        buckets to disp
    }

    val visible by remember(allTiles, solvedMap, canvasW, canvasH, viewW, viewH) {
        derivedStateOf {
            val ox = qox.floatValue
            val oy = qoy.floatValue
            val s = qcam.floatValue
            val invS = 1f / s.coerceAtLeast(0.01f)
            val px = qpivX.floatValue
            val py = qpivY.floatValue
            val loX = px * (1f - invS)
            val hiX = px + (viewW - px) * invS
            val loY = py * (1f - invS)
            val hiY = py + (viewH - py) * invS
            val out = ArrayList<VisibleTile>(48)
            if (canvasW > 0f && canvasH > 0f && viewW > 0f) {
                val margin = step * 3f
                val effW = (hiX - loX) + 2f * margin
                val effH = (hiY - loY) + 2f * margin
                val replicasX = ceil(effW / canvasW).toInt() + 1
                val replicasY = ceil(effH / canvasH).toInt() + 1
                val baseX = floor((-ox + loX - margin) / canvasW).toInt()
                val baseY = floor((-oy + loY - margin) / canvasH).toInt()

                fun test(t: WallTile, cx0: Float, cy0: Float): Boolean {
                    val g = solvedMap[t]
                    val gw = g?.spanW ?: t.spanW
                    val gh = g?.spanH ?: t.spanH
                    if (gw <= 0 || gh <= 0) return false       // dropped tile: no render, no phantom taps
                    val gc = g?.col ?: t.col
                    val gr = g?.row ?: t.row
                    val l1 = cx0 + gc * step
                    val t1 = cy0 + gr * step
                    val vis1 = l1 + gw * step - gap > loX - margin && l1 < hiX + margin &&
                            t1 + gh * step - gap > loY - margin && t1 < hiY + margin
                    val l2 = cx0 + t.col * step
                    val t2 = cy0 + t.row * step
                    val vis2 = l2 + t.spanW * step - gap > loX - margin && l2 < hiX + margin &&
                            t2 + t.spanH * step - gap > loY - margin && t2 < hiY + margin
                    return vis1 || vis2                          // both animation endpoints count
                }

                for (rx in baseX..baseX + replicasX) {
                    for (ry in baseY..baseY + replicasY) {
                        val cx0 = rx * canvasW + ox
                        val cy0 = ry * canvasH + oy
                        if (cx0 + canvasW <= loX - margin || cx0 >= hiX + margin ||
                            cy0 + canvasH <= loY - margin || cy0 >= hiY + margin
                        ) continue
                        val localTop = floor((loY - margin - cy0) / step).toInt() - VISIBLE_ROW_PAD - 1
                        val localBot = floor((hiY + margin - cy0) / step).toInt() + VISIBLE_ROW_PAD + 1
                        val rLo = localTop.coerceAtLeast(0)
                        val rHi = min(localBot, rows - 1)
                        var r = rLo
                        while (r <= rHi) {
                            val bucket = rowBuckets[r]
                            for (t in bucket) if (test(t, cx0, cy0)) out.add(VisibleTile(t, rx, ry))
                            r++
                        }
                        for (t in displacedTiles) if (test(t, cx0, cy0)) out.add(VisibleTile(t, rx, ry))
                    }
                }
            }
            out
        }
    }

    // ---- artwork warm-up: nearest-first around the anchor, capped, IO-parallel
    LaunchedEffect(allTiles) {
        val anchorTile = allTiles.firstOrNull { it.index == currentIndex }
        val ax = anchorTile?.let { it.col + it.spanW / 2f } ?: (cols / 2f)
        val ay = anchorTile?.let { it.row + it.spanH / 2f } ?: (rows / 2f)
        val ordered = allTiles.map { t ->
            val dx = (t.col + t.spanW / 2f) - ax
            val dy = (t.row + t.spanH / 2f) - ay
            Pair(dx * dx + dy * dy, songs[t.index].path)
        }.sortedBy { it.first }.map { it.second }.distinct()
        val capped = if (ordered.size > WARM_MAX) ordered.subList(0, WARM_MAX) else ordered
        val io = Dispatchers.IO.limitedParallelism(4)
        coroutineScope { capped.map { async(io) { ArtworkLoader.load(it) } }.awaitAll() }
    }

    val scope = rememberCoroutineScope()
    val selRef = rememberUpdatedState(onSelect)
    val frameClock = LocalFrameClock.current

    // whole-wall fade+scale when the base layout rebuilds (hides hard re-create cuts)
    val wallAnim = remember { Animatable(1f) }
    var wallInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(allTiles) {
        if (!wallInitialized) { wallInitialized = true; return@LaunchedEffect }
        wallAnim.snapTo(0f)
        wallAnim.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 360
                0.85f at 120 using FastOutSlowInEasing
            }
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                alpha = wallAnim.value
                scaleX = 0.96f + 0.04f * wallAnim.value
                scaleY = 0.96f + 0.04f * wallAnim.value
            }
            .pointerInput(songs) {
                // pan + pinch-zoom + custom fling: cancellable Job, vsync-aligned
                // exponential decay (Animatable.stop() can't stop a spring mid-flight,
                // a Job.cancel() always can — this avoids the multi-finger rubber-band bug)
                var flingJob: Job? = null
                var lastHapticQ = 0
                fun startFling(vx: Float, vy: Float) {
                    flingJob?.cancel()
                    if (abs(vx) < 80f && abs(vy) < 80f) return
                    flingJob = scope.launch {
                        var vX = vx; var vY = vy
                        var lastFrame = 0L
                        while (true) {
                            val now = frameClock.withFrameNanos { it }
                            if (lastFrame != 0L) {
                                val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                                val cs = camScale.floatValue
                                offsetX.floatValue += (vX * dt) / cs
                                offsetY.floatValue += (vY * dt) / cs
                                val damp = exp((-dt) * 4.2).toFloat()
                                vX *= damp; vY *= damp
                                if (abs(vX) < 12f && abs(vY) < 12f) break
                            }
                            lastFrame = now
                        }
                    }
                }
                awaitEachGestureWall {
                    awaitFirstDown(requireUnconsumed = false)
                    var pastTouchSlop = false
                    var gestureZooming = false
                    var pan = Offset.Zero
                    var zoom = 1f
                    val velTracker = PointerVelocityTracker()
                    val touchSlop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (!pastTouchSlop) {
                            zoom *= zoomChange
                            pan += panChange
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            val zoomMotion = abs(1 - zoom) * centroidSize
                            val panMotion = pan.getDistance()
                            if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                pastTouchSlop = true
                                gestureZooming = zoomMotion > touchSlop
                            }
                        }
                        if (pastTouchSlop) {
                            flingJob?.cancel()
                            if (gestureZooming || event.changes.size > 1) {
                                val cen = event.calculateCentroid(useCurrent = true)
                                val sNew = (camScale.floatValue * zoomChange).coerceIn(CAM_MIN, CAM_MAX)
                                if (cen.x.isFinite() && cen.y.isFinite() && sNew.isFinite()) {
                                    camScale.floatValue = sNew
                                    camPivotX.floatValue = cen.x
                                    camPivotY.floatValue = cen.y
                                }
                            } else {
                                val cs = camScale.floatValue
                                offsetX.floatValue += panChange.x / cs
                                offsetY.floatValue += panChange.y / cs
                                // haptic tick every quarter cell dragged
                                val q = ((offsetX.floatValue + offsetY.floatValue) / (step * 0.25)).toInt()
                                if (q != lastHapticQ) {
                                    lastHapticQ = q
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                }
                            }
                            event.changes.forEach { it.consume() }
                            velTracker.addMovement(event)
                        }
                        if (!event.changes.any { it.pressed }) break
                    }
                    if (pastTouchSlop && !gestureZooming) {
                        val v = velTracker.calculateVelocity()
                        val maxV = 9000f
                        startFling(
                            v.x.coerceIn(-maxV, maxV),
                            v.y.coerceIn(-maxV, maxV)
                        )
                    }
                }
            }
    ) {
        // camera layer: ALL drag/zoom translation lives on this single graphicsLayer
        // (draw-phase only — tiles never re-measure/re-layout per frame)
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    viewW = it.width.toFloat()
                    viewH = it.height.toFloat()
                }
                .graphicsLayer {
                    val s = camScale.floatValue
                    scaleX = s
                    scaleY = s
                    translationX = offsetX.floatValue * s
                    translationY = offsetY.floatValue * s
                    transformOrigin = TransformOrigin(
                        camPivotX.floatValue / viewW.coerceAtLeast(1f),
                        camPivotY.floatValue / viewH.coerceAtLeast(1f)
                    )
                }
        ) {
            val contentScale = 1f
            visible.forEach { vt ->
                val t = vt.tile
                val song = songs[t.index]
                val g = solvedMap[t] ?: return@forEach
                val isActive = g.active
                val left = vt.rx * canvasW + g.col * step
                val top = vt.ry * canvasH + g.row * step
                val w = g.spanW * step - gap
                val h = g.spanH * step - gap

                key(t.col, t.row, vt.rx, vt.ry) {
                    val interaction = remember { MutableInteractionSource() }
                    PosterTile(
                        song = song,
                        active = isActive,
                        isPlaying = isPlaying,
                        positionProvider = positionProvider,
                        seekProvider = seekProvider,
                        baseLeft = left,
                        baseTop = top,
                        renderW = w,
                        renderH = h,
                        contentScale = contentScale,
                        interaction = interaction,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (!isActive) selRef.value(song) else onToggle()
                        }
                    )
                }
            }
        }
        Text(
            "Drag to roam · pinch to zoom · tap a poster to play",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}

/** One poster tile: animated position/size, artwork, gradient scrim, title, progress. */
@Composable
private fun PosterTile(
    song: Song,
    active: Boolean,
    isPlaying: Boolean,
    positionProvider: () -> Pair<Long, Long>,
    seekProvider: (Float) -> Unit,
    baseLeft: Float,
    baseTop: Float,
    renderW: Float,
    renderH: Float,
    contentScale: Float,
    interaction: MutableInteractionSource,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape((12 * contentScale).dp)
    val moveSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
    val animLeft by animateFloatAsState(baseLeft, moveSpec, label = "tileLeft")
    val animTop by animateFloatAsState(baseTop, moveSpec, label = "tileTop")
    val animW by animateFloatAsState(renderW, moveSpec, label = "tileW")
    val animH by animateFloatAsState(renderH, moveSpec, label = "tileH")

    Box(
        Modifier
            .zIndex(if (active) 1f else 0f)
            .offset { IntOffset(animLeft.roundToInt(), animTop.roundToInt()) }
            .size(with(density) { animW.toDp() }, with(density) { animH.toDp() })
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
    ) {
        TileArtwork(song.path)
        // bottom scrim for text legibility
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (active) 0.65f else 0.35f)
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding((8 * contentScale).dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                song.displayTitle,
                color = Color.White,
                fontSize = if (active) 14.sp else 11.sp,
                maxLines = if (active) 2 else 1,
                lineHeight = if (active) 18.sp else 13.sp
            )
            if (active) {
                Text(
                    song.artist,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
                ActiveProgressBar(positionProvider, seekProvider, contentScale)
            }
        }
        if (active && isPlaying) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding((8 * contentScale).dp)
                    .size((8 * contentScale).dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            )
        }
    }
}

/** Cover art for one tile: instant memory hit, then async load; crossfade on swap. */
@Composable
private fun TileArtwork(path: String) {
    var bmp by remember(path) { mutableStateOf(ArtworkLoader.peek(path)) }
    LaunchedEffect(path) {
        if (bmp == null) bmp = ArtworkLoader.load(path)
    }
    val alpha by animateFloatAsState(if (bmp != null) 1f else 0f, tween(200), label = "artFade")
    androidx.compose.foundation.Canvas(Modifier.matchParentSize().graphicsLayer { this.alpha = alpha }) {
        val image = bmp ?: return@Canvas
        drawImage(image)
    }
}

/** Thin seekable progress bar on the active card. Polls the provider at 250ms. */
@Composable
private fun ActiveProgressBar(
    positionProvider: () -> Pair<Long, Long>,
    seekProvider: (Float) -> Unit,
    contentScale: Float
) {
    val dragPos = remember { mutableFloatStateOf(-1f) }
    val progress by produceState(initialValue = 0f) {
        while (true) {
            val (pos, dur) = positionProvider()
            value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }
    val shown = if (dragPos.floatValue >= 0f) dragPos.floatValue else progress
    Box(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragPos.floatValue = (it.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragPos.floatValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        seekProvider(dragPos.floatValue.coerceIn(0f, 1f))
                        dragPos.floatValue = -1f
                    },
                    onDragCancel = { dragPos.floatValue = -1f }
                )
            }
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height((3 * contentScale).dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .height((3 * contentScale).dp)
                    .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(2.dp))
            )
        }
    }
}
