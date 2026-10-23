package link.roadio.riff.ui

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiUiConfigTest {
    @Test
    fun `alpha map accepts exactly seven two digit hexadecimal bytes`() {
        assertArrayEquals(
            intArrayOf(0x3F, 0x5F, 0x7F, 0x9F, 0xBF, 0xDF, 0xFF),
            UiConfig.validateAiHeatmapAlphaMap(listOf("3F", "5f", "7F", "9f", "BF", "df", "FF")),
        )
    }

    @Test
    fun `alpha map rejects wrong size and malformed bytes`() {
        listOf(
            listOf("3F"),
            listOf("3F", "5F", "7F", "9F", "BF", "DF", "FFF"),
            listOf("3F", "5F", "7F", "9F", "BF", "DG", "FF"),
        ).forEach { value ->
            assertTrue(runCatching { UiConfig.validateAiHeatmapAlphaMap(value) }.isFailure)
        }
    }

    @Test
    fun `selection model accepts exact deep merge object and preserves weights`() {
        val model = UiConfig.validateAiSelectionModel(JSONObject(SELECTION_MODEL))

        assertEquals(42, model.pteMinimumGroupSize)
        assertEquals(0.5, model.directoryDecay, 0.0)
        assertEquals(0.25, model.weights.genreLikeDeficit, 0.0)
        assertEquals(1.0, model.weights.playHistoryPenalty, 0.0)
    }

    @Test
    fun `selection weights support recursive partial deep merge`() {
        val model =
            UiConfig
                .fromUserText(
                    """{"ai":{"selectionModel":{"weights":{"genreLikeDeficit":0.75}}}}""",
                ).aiSelectionModel()

        assertEquals(0.75, model.weights.genreLikeDeficit, 0.0)
        assertEquals(1.0, model.weights.arousalDistance, 0.0)
    }

    @Test
    fun `selection model rejects unknown missing and unsafe values`() {
        val extra = JSONObject(SELECTION_MODEL).put("extra", 1)
        val missingWeight = JSONObject(SELECTION_MODEL).also { it.getJSONObject("weights").remove("pteDistance") }
        val invalidDecay = JSONObject(SELECTION_MODEL).put("historyDecay", 1.0)
        val fractionalGroup = JSONObject(SELECTION_MODEL).put("pteMinimumGroupSize", 42.5)

        listOf(extra, missingWeight, invalidDecay, fractionalGroup).forEach { value ->
            assertTrue(runCatching { UiConfig.validateAiSelectionModel(value) }.isFailure)
        }
    }

    private companion object {
        val SELECTION_MODEL =
            """
            {
              "temperature": 1.0,
              "axisMinimumSpan": 0.1,
              "pteMinimumGroupSize": 42,
              "directoryDecay": 0.5,
              "historyDecay": 0.5,
              "weights": {
                "arousalDistance": 1.0,
                "valenceDistance": 1.0,
                "pteDistance": 1.0,
                "trackLikeDeficit": 1.0,
                "trackDislikePenalty": 1.0,
                "directoryLikeDeficit": 0.5,
                "directoryDislikePenalty": 0.5,
                "albumLikeDeficit": 0.5,
                "albumDislikePenalty": 0.5,
                "artistLikeDeficit": 0.5,
                "artistDislikePenalty": 0.5,
                "genreLikeDeficit": 0.25,
                "genreDislikePenalty": 0.25,
                "playHistoryPenalty": 1.0
              }
            }
            """.trimIndent()
    }
}
