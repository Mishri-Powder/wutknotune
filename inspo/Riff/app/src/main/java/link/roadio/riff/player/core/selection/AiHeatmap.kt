package link.roadio.riff.player.core.selection

import link.roadio.riff.indexer.core.media.MediaId
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

const val AI_HEATMAP_DEFAULT_CELLS = 32
const val AI_HEATMAP_MIN_CELLS = 1
const val AI_HEATMAP_MAX_CELLS = 128
const val AI_HEATMAP_ALPHA_LEVELS = 7

data class AiFeaturePoint(
    val mediaId: MediaId,
    val arousal: Float,
    val valence: Float,
    val arousalConfidence: Float = 1f,
    val valenceConfidence: Float = 1f,
) {
    init {
        require(arousal.isFinite() && arousal in -1f..1f)
        require(valence.isFinite() && valence in -1f..1f)
        require(arousalConfidence.isFinite() && arousalConfidence in 0f..1f)
        require(valenceConfidence.isFinite() && valenceConfidence in 0f..1f)
    }
}

data class AiAxisRange(
    val minimum: Float,
    val maximum: Float,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite())
        require(minimum >= -1f && maximum <= 1f && minimum < maximum)
    }

    val span: Float
        get() = maximum - minimum

    fun fraction(value: Float): Float {
        require(value.isFinite())
        return ((value - minimum) / span).coerceIn(0f, 1f)
    }

    fun valueAt(fraction: Float): Float {
        require(fraction.isFinite())
        return minimum + fraction.coerceIn(0f, 1f) * span
    }

    companion object {
        val FULL = AiAxisRange(-1f, 1f)
    }
}

/**
 * Toolkit-neutral density projection over the currently usable AI candidates.
 *
 * Counts use logical A/V coordinates; RTL is applied only by rasterization. The fitted ranges are part
 * of the model so pointer conversion and rendering always use the same coordinate system.
 */
class AiHeatmap private constructor(
    val valenceCells: Int,
    val arousalCells: Int,
    val valenceRange: AiAxisRange,
    val arousalRange: AiAxisRange,
    private val candidateCounts: IntArray,
) {
    init {
        validateAiHeatmapCells(valenceCells)
        validateAiHeatmapCells(arousalCells)
        require(candidateCounts.size == valenceCells * arousalCells)
        require(candidateCounts.all { it >= 0 })
    }

    fun candidateCount(
        valenceCell: Int,
        arousalCell: Int,
    ): Int {
        require(valenceCell in 0 until valenceCells)
        require(arousalCell in 0 until arousalCells)
        return candidateCounts[arousalCell * valenceCells + valenceCell]
    }

    fun candidateCount(
        valence: Float,
        arousal: Float,
    ): Int =
        candidateCount(
            aiHeatmapCell(valence, valenceCells, valenceRange),
            aiHeatmapCell(arousal, arousalCells, arousalRange),
        )

    companion object {
        fun empty(
            valenceCells: Int = AI_HEATMAP_DEFAULT_CELLS,
            arousalCells: Int = AI_HEATMAP_DEFAULT_CELLS,
        ): AiHeatmap {
            validateAiHeatmapCells(valenceCells)
            validateAiHeatmapCells(arousalCells)
            return AiHeatmap(
                valenceCells,
                arousalCells,
                AiAxisRange.FULL,
                AiAxisRange.FULL,
                IntArray(valenceCells * arousalCells),
            )
        }

        internal fun fromCounts(
            valenceCells: Int,
            arousalCells: Int,
            counts: IntArray,
            valenceRange: AiAxisRange = AiAxisRange.FULL,
            arousalRange: AiAxisRange = AiAxisRange.FULL,
        ): AiHeatmap {
            validateAiHeatmapCells(valenceCells)
            validateAiHeatmapCells(arousalCells)
            require(counts.size == valenceCells * arousalCells)
            return AiHeatmap(
                valenceCells,
                arousalCells,
                valenceRange,
                arousalRange,
                counts.clone(),
            )
        }
    }
}

class AiHeatmapRenderStyle(
    val topRight: Int,
    val bottomRight: Int,
    val bottomLeft: Int,
    val topLeft: Int,
    alphaMap: List<Int>,
) {
    val alphaMap: List<Int> = alphaMap.toList()

    init {
        require(this.alphaMap.size == AI_HEATMAP_ALPHA_LEVELS)
        require(this.alphaMap.all { it in 0..255 })
    }

    fun alphaForCandidateCount(count: Int): Int = alphaMap[aiHeatmapAlphaIndex(count)]
}

/** Builds a transient viewport; it never changes queue eligibility or persists model output. */
object AiHeatmapBuilder {
    fun build(
        candidates: List<AiQueueCandidate>,
        featuresByMediaId: Map<MediaId, AiFeaturePoint>,
        valenceCells: Int,
        arousalCells: Int,
        selectionArousal: Float = 0f,
        selectionValence: Float = 0f,
    ): AiHeatmap {
        validateAiHeatmapCells(valenceCells)
        validateAiHeatmapCells(arousalCells)
        require(selectionArousal.isFinite() && selectionArousal in -1f..1f)
        require(selectionValence.isFinite() && selectionValence in -1f..1f)
        val seen = HashSet<MediaId>(candidates.size)
        val points =
            candidates.mapNotNull { candidate ->
                if (!candidate.playable || candidate.effectivelyBanned || !seen.add(candidate.mediaId)) {
                    return@mapNotNull null
                }
                featuresByMediaId[candidate.mediaId]
            }
        val valenceRange =
            fittedAiAxisRange(points.map(AiFeaturePoint::valence), selectionValence)
        val arousalRange =
            fittedAiAxisRange(points.map(AiFeaturePoint::arousal), selectionArousal)
        val counts = IntArray(valenceCells * arousalCells)
        points.forEach { point ->
            val valenceCell = aiHeatmapCell(point.valence, valenceCells, valenceRange)
            val arousalCell = aiHeatmapCell(point.arousal, arousalCells, arousalRange)
            counts[arousalCell * valenceCells + valenceCell] += 1
        }
        return AiHeatmap.fromCounts(
            valenceCells,
            arousalCells,
            counts,
            valenceRange,
            arousalRange,
        )
    }
}

internal fun fittedAiAxisRange(
    values: List<Float>,
    selection: Float,
): AiAxisRange {
    require(selection.isFinite() && selection in -1f..1f)
    if (values.isEmpty()) return AiAxisRange.FULL
    require(values.all { it.isFinite() && it in -1f..1f })
    // Keep the selected point in range when a live MDRG refresh moves the data-derived viewport.
    val anchored = values + selection
    val rawMinimum = anchored.min()
    val rawMaximum = anchored.max()
    val baseSpan = (rawMaximum - rawMinimum).coerceAtLeast(AI_VIEWPORT_MINIMUM_BASE_SPAN)
    val displaySpan = (baseSpan * AI_VIEWPORT_PADDING_FACTOR).coerceAtMost(2f)
    val center = (rawMinimum + rawMaximum) / 2f
    var minimum = center - displaySpan / 2f
    var maximum = center + displaySpan / 2f
    if (minimum < -1f) {
        maximum += -1f - minimum
        minimum = -1f
    }
    if (maximum > 1f) {
        minimum -= maximum - 1f
        maximum = 1f
    }
    return AiAxisRange(minimum.coerceAtLeast(-1f), maximum.coerceAtMost(1f))
}

data class AiHeatmapRaster(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
)

/** Rasterizes the immutable projection outside Compose so recomposition never owns binning semantics. */
internal fun rasterizeAiHeatmap(
    heatmap: AiHeatmap,
    width: Int,
    height: Int,
    rtl: Boolean,
    style: AiHeatmapRenderStyle,
): AiHeatmapRaster {
    require(width > 0 && height > 0)
    val pixels = IntArray(width * height)
    repeat(heatmap.arousalCells) { arousalCell ->
        val physicalRow = heatmap.arousalCells - 1 - arousalCell
        val top = proportionalBoundary(physicalRow, height, heatmap.arousalCells)
        val bottom = proportionalBoundary(physicalRow + 1, height, heatmap.arousalCells)
        repeat(heatmap.valenceCells) { valenceCell ->
            val physicalColumn = if (rtl) heatmap.valenceCells - 1 - valenceCell else valenceCell
            val left = proportionalBoundary(physicalColumn, width, heatmap.valenceCells)
            val right = proportionalBoundary(physicalColumn + 1, width, heatmap.valenceCells)
            val alpha = style.alphaForCandidateCount(heatmap.candidateCount(valenceCell, arousalCell))
            for (y in top until bottom) {
                for (x in left until right) {
                    val isCellCorner =
                        (x == left || x == right - 1) &&
                            (y == top || y == bottom - 1)
                    if (!isCellCorner) {
                        pixels[y * width + x] =
                            interpolateAiGradientPixel(style, x, y, width, height, rtl, alpha, heatmap.valenceRange, heatmap.arousalRange)
                    }
                }
            }
        }
    }
    return AiHeatmapRaster(width, height, pixels)
}

internal fun aiHeatmapAlphaIndex(candidateCount: Int): Int {
    require(candidateCount >= 0)
    if (candidateCount == 0) return 0
    val log2Ceiling = Int.SIZE_BITS - (candidateCount - 1).countLeadingZeroBits()
    return if (log2Ceiling <= 3) 1 else (log2Ceiling - 2).coerceAtMost(AI_HEATMAP_ALPHA_LEVELS - 1)
}

data class AiSmptePlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal fun aiSmptePlacement(
    viewportWidth: Float,
    viewportHeight: Float,
): AiSmptePlacement {
    require(viewportWidth > 0f && viewportHeight > 0f)
    val scale = min(viewportWidth / 4f, viewportHeight / 3f)
    val width = 4f * scale
    val height = 3f * scale
    return AiSmptePlacement(
        left = (viewportWidth - width) / 2f,
        top = (viewportHeight - height) / 2f,
        width = width,
        height = height,
    )
}

internal fun aiHeatmapCell(
    value: Float,
    cells: Int,
    range: AiAxisRange = AiAxisRange.FULL,
): Int {
    require(value.isFinite() && value in -1f..1f)
    validateAiHeatmapCells(cells)
    return floor(range.fraction(value) * cells)
        .toInt()
        .coerceIn(0, cells - 1)
}

private const val AI_VIEWPORT_MINIMUM_BASE_SPAN = 0.10f
private const val AI_VIEWPORT_PADDING_FACTOR = 1.10f

internal fun validateAiHeatmapCells(value: Any): Int {
    require(value is Int || value is Long) { "AI heatmap cell count must be an integer" }
    val cells = (value as Number).toLong()
    require(cells in AI_HEATMAP_MIN_CELLS.toLong()..AI_HEATMAP_MAX_CELLS.toLong()) {
        "AI heatmap cell count must be from $AI_HEATMAP_MIN_CELLS through $AI_HEATMAP_MAX_CELLS"
    }
    return cells.toInt()
}

private fun interpolateAiGradientPixel(
    style: AiHeatmapRenderStyle,
    physicalX: Int,
    physicalY: Int,
    width: Int,
    height: Int,
    rtl: Boolean,
    alpha: Int,
    valenceRange: AiAxisRange,
    arousalRange: AiAxisRange,
): Int {
    val physicalHorizontal = if (width == 1) 0f else physicalX / (width - 1f)
    val viewportHorizontal = if (rtl) 1f - physicalHorizontal else physicalHorizontal
    val viewportVertical = if (height == 1) 0f else physicalY / (height - 1f)
    // A fixed emotion keeps its hue when the viewport zooms or pans; only its placement changes.
    // Authority: https://wiki.271.pics/Riff/LATEST/ai-screen-arousal-valence-canvas
    val logicalHorizontal = (valenceRange.valueAt(viewportHorizontal) + 1f) / 2f
    val vertical = (1f - arousalRange.valueAt(1f - viewportVertical)) / 2f
    val top = interpolateRgb(style.topLeft, style.topRight, logicalHorizontal)
    val bottom = interpolateRgb(style.bottomLeft, style.bottomRight, logicalHorizontal)
    return (alpha shl 24) or (interpolateRgb(top, bottom, vertical) and 0x00FFFFFF)
}

private fun interpolateRgb(
    start: Int,
    end: Int,
    fraction: Float,
): Int {
    fun channel(shift: Int): Int =
        (((start ushr shift) and 0xFF) + (((end ushr shift) and 0xFF) - ((start ushr shift) and 0xFF)) * fraction)
            .roundToInt()
            .coerceIn(0, 255)
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

private fun proportionalBoundary(
    index: Int,
    extent: Int,
    cells: Int,
): Int = (index.toLong() * extent / cells).toInt()
