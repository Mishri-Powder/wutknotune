package link.roadio.riff.player.core.selection

import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHeatmapTest {
    private val style =
        AiHeatmapRenderStyle(
            topRight = 0xFF00FF00.toInt(),
            bottomRight = 0xFFFFFFFF.toInt(),
            bottomLeft = 0xFF0000FF.toInt(),
            topLeft = 0xFFFF0000.toInt(),
            alphaMap = listOf(0x3F, 0x5F, 0x7F, 0x9F, 0xBF, 0xDF, 0xFF),
        )

    @Test
    fun `same emotion retains its color across fitted viewports and rtl`() {
        val full = rasterizeAiHeatmap(AiHeatmap.fromCounts(1, 1, intArrayOf(256)), 401, 401, false, style)
        val fitted = AiHeatmap.fromCounts(1, 1, intArrayOf(256), AiAxisRange(.2f, .8f), AiAxisRange(.2f, .8f))
        val zoom = rasterizeAiHeatmap(fitted, 401, 401, false, style)
        val rtl = rasterizeAiHeatmap(fitted, 401, 401, true, style)
        assertEquals(full.pixel(300, 100), zoom.pixel(200, 200))
        assertEquals(zoom.pixel(200, 200), rtl.pixel(200, 200))
        assertEquals(zoom.pixel(100, 200), rtl.pixel(300, 200))
    }

    @Test
    fun `candidate counts use the adopted ceil log2 alpha buckets`() {
        val expectations =
            mapOf(
                0 to 0,
                1 to 1,
                8 to 1,
                9 to 2,
                16 to 2,
                17 to 3,
                32 to 3,
                33 to 4,
                64 to 4,
                65 to 5,
                128 to 5,
                129 to 6,
                Int.MAX_VALUE to 6,
            )

        expectations.forEach { (count, expectedIndex) ->
            assertEquals(expectedIndex, aiHeatmapAlphaIndex(count))
            assertEquals(style.alphaMap[expectedIndex], style.alphaForCandidateCount(count))
        }
    }

    @Test
    fun `render style owns an immutable alpha map snapshot`() {
        val alphaMap = mutableListOf(0x3F, 0x5F, 0x7F, 0x9F, 0xBF, 0xDF, 0xFF)
        val snapshot =
            AiHeatmapRenderStyle(
                topRight = 0,
                bottomRight = 0,
                bottomLeft = 0,
                topLeft = 0,
                alphaMap = alphaMap,
            )

        alphaMap[0] = 0

        assertEquals(0x3F, snapshot.alphaForCandidateCount(0))
    }

    @Test
    fun `builder counts each distinct eligible featured media id once`() {
        val candidates =
            listOf(
                candidate(1),
                candidate(1),
                candidate(2, playable = false),
                candidate(3, banned = true),
                candidate(4),
                candidate(5),
                candidate(6),
            )
        val point = AiFeaturePoint(MediaId(1), arousal = 0.25f, valence = -0.25f)
        val features =
            mapOf(
                MediaId(1L) to point,
                MediaId(2L) to point.copy(mediaId = MediaId(2)),
                MediaId(3L) to point.copy(mediaId = MediaId(3)),
                MediaId(5L) to point.copy(mediaId = MediaId(5)),
                MediaId(6L) to point.copy(mediaId = MediaId(6)),
            )

        val heatmap = AiHeatmapBuilder.build(candidates, features, valenceCells = 4, arousalCells = 4)

        assertEquals(3, heatmap.candidateCount(valence = -0.25f, arousal = 0.25f))
        assertEquals(
            3,
            (0 until 4).sumOf { arousal ->
                (0 until 4).sumOf { valence -> heatmap.candidateCount(valence, arousal) }
            },
        )
    }

    @Test
    fun `normalized boundaries map to expected cells and clamp positive endpoint`() {
        assertEquals(0, aiHeatmapCell(-1f, 4))
        assertEquals(1, aiHeatmapCell(-0.5f, 4))
        assertEquals(2, aiHeatmapCell(0f, 4))
        assertEquals(3, aiHeatmapCell(0.999f, 4))
        assertEquals(3, aiHeatmapCell(1f, 4))
    }

    @Test
    fun `raster places positive arousal at top and valence mirrors in rtl`() {
        val heatmap = AiHeatmap.fromCounts(2, 2, intArrayOf(1, 9, 17, 33))
        val ltr = rasterizeAiHeatmap(heatmap, width = 8, height = 8, rtl = false, style = style)
        val rtl = rasterizeAiHeatmap(heatmap, width = 8, height = 8, rtl = true, style = style)

        assertEquals(0x9F, ltr.alpha(1, 1))
        assertEquals(0xBF, ltr.alpha(5, 1))
        assertEquals(0x5F, ltr.alpha(1, 5))
        assertEquals(0x7F, ltr.alpha(5, 5))
        assertEquals(0xBF, rtl.alpha(1, 1))
        assertEquals(0x9F, rtl.alpha(5, 1))
    }

    @Test
    fun `empty cells retain 3f and four transparent corners form shared two by two nodes`() {
        val raster = rasterizeAiHeatmap(AiHeatmap.empty(2, 2), 8, 8, rtl = false, style = style)

        listOf(3 to 3, 4 to 3, 3 to 4, 4 to 4).forEach { (x, y) ->
            assertEquals(0, raster.pixel(x, y))
        }
        assertEquals(0x3F, raster.alpha(2, 3))
        assertEquals(0x3F, raster.alpha(3, 2))
        assertEquals(0x3F, raster.alpha(2, 2))
    }

    @Test
    fun `every cell samples one continuous canvas wide bilinear gradient`() {
        val heatmap = AiHeatmap.fromCounts(1, 1, intArrayOf(0))
        val ltr = rasterizeAiHeatmap(heatmap, 5, 5, rtl = false, style = style)
        val rtl = rasterizeAiHeatmap(heatmap, 5, 5, rtl = true, style = style)

        assertEquals(0x3F808080, ltr.pixel(2, 2))
        assertEquals(ltr.pixel(1, 2), rtl.pixel(3, 2))
    }

    @Test
    fun `proportional boundaries cover a nondivisible pixel extent`() {
        val raster = rasterizeAiHeatmap(AiHeatmap.fromCounts(3, 1, IntArray(3) { 1 }), 8, 3, false, style)

        assertTrue((0 until 8).all { x -> raster.alpha(x, 1) == 0x5F })
    }

    @Test
    fun `smpte placement is four by three with centered letterbox`() {
        assertEquals(AiSmptePlacement(0f, 50f, 400f, 300f), aiSmptePlacement(400f, 400f))
        assertEquals(AiSmptePlacement(0f, 87.5f, 300f, 225f), aiSmptePlacement(300f, 400f))
    }

    @Test
    fun `configuration requires an integer from one through 128`() {
        assertEquals(1, validateAiHeatmapCells(1))
        assertEquals(128, validateAiHeatmapCells(128L))
        listOf(0, 129, 32.0, "32").forEach { value ->
            assertTrue(runCatching { validateAiHeatmapCells(value) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `fitted range pads occupied values and preserves selection anchor`() {
        val range = fittedAiAxisRange(listOf(0.2f, 0.4f), selection = -0.3f)

        assertEquals(-0.335f, range.minimum, 0.00001f)
        assertEquals(0.435f, range.maximum, 0.00001f)
        assertTrue(range.minimum <= -0.3f)
        assertTrue(range.maximum >= 0.4f)
    }

    @Test
    fun `fitted range has deterministic minimum span and shifts at domain edge`() {
        val identical = fittedAiAxisRange(listOf(0.4f, 0.4f), selection = 0.4f)
        val upperEdge = fittedAiAxisRange(listOf(0.9f, 1f), selection = 1f)

        assertEquals(0.345f, identical.minimum, 0.00001f)
        assertEquals(0.455f, identical.maximum, 0.00001f)
        assertEquals(0.89f, upperEdge.minimum, 0.00001f)
        assertEquals(1f, upperEdge.maximum, 0f)
        assertEquals(AiAxisRange.FULL, fittedAiAxisRange(emptyList(), selection = 0.7f))
    }

    @Test
    fun `builder derives independent ranges from eligible points plus current selection`() {
        val heatmap =
            AiHeatmapBuilder.build(
                candidates = listOf(candidate(1), candidate(2)),
                featuresByMediaId =
                    mapOf(
                        MediaId(1L) to AiFeaturePoint(MediaId(1L), arousal = 0.2f, valence = -0.4f),
                        MediaId(2L) to AiFeaturePoint(MediaId(2L), arousal = 0.6f, valence = 0.2f),
                    ),
                valenceCells = 4,
                arousalCells = 4,
                selectionArousal = -0.2f,
                selectionValence = 0.5f,
            )

        assertEquals(-0.445f, heatmap.valenceRange.minimum, 0.00001f)
        assertEquals(0.545f, heatmap.valenceRange.maximum, 0.00001f)
        assertEquals(-0.24f, heatmap.arousalRange.minimum, 0.00001f)
        assertEquals(0.64f, heatmap.arousalRange.maximum, 0.00001f)
        assertEquals(1, heatmap.candidateCount(-0.4f, 0.2f))
        assertEquals(1, heatmap.candidateCount(0.2f, 0.6f))
    }

    private fun candidate(
        mediaId: Long,
        playable: Boolean = true,
        banned: Boolean = false,
    ) = AiQueueCandidate(MediaId(mediaId), mediaId + 100, playable, banned)

    private fun AiHeatmapRaster.pixel(
        x: Int,
        y: Int,
    ): Int = pixels[y * width + x]

    private fun AiHeatmapRaster.alpha(
        x: Int,
        y: Int,
    ): Int = pixel(x, y).ushr(24)
}
