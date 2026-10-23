package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleNavigationPolicyTest {
    @Test
    fun `stopped playback idles to main`() {
        assertEquals(PlayerScreen.MAIN, idleDestination(playbackActive = false))
    }

    @Test
    fun `active playback idles to player`() {
        assertEquals(PlayerScreen.PLAYBACK, idleDestination(playbackActive = true))
    }

    @Test
    fun `non looping completion stops while loop completion stays active`() {
        assertEquals(
            NowPlayingUiState(playing = false, positionFraction = 1f),
            completedPlayback(NowPlayingUiState(playing = true)),
        )
        assertEquals(
            NowPlayingUiState(playing = true, repeat = true),
            completedPlayback(NowPlayingUiState(playing = true, repeat = true)),
        )
    }

    @Test
    fun `idle timeout accepts only integral configured range`() {
        assertEquals(1L, UiConfig.validateIdleTimeoutSeconds(1))
        assertEquals(86_400L, UiConfig.validateIdleTimeoutSeconds(86_400L))
        listOf(0, 86_401, 30.5, "30").forEach { value ->
            assertTrue("Expected rejection for $value", runCatching { UiConfig.validateIdleTimeoutSeconds(value) }.isFailure)
        }
    }

    @Test
    fun `recent history generations accepts only integral configured range`() {
        assertEquals(1L, UiConfig.validateRecentHistoryGenerations(1))
        assertEquals(10_000L, UiConfig.validateRecentHistoryGenerations(10_000L))
        listOf(0, 10_001, 30.5, "30").forEach { value ->
            assertTrue(
                "Expected rejection for $value",
                runCatching { UiConfig.validateRecentHistoryGenerations(value) }.isFailure,
            )
        }
    }

    @Test
    fun `rew previous thresholds require exact category keys and integral ranges`() {
        val valid = org.json.JSONObject(mapOf("music" to 3, "audiobook" to 15))
        assertEquals(
            mapOf("music" to 3L, "audiobook" to 15L),
            UiConfig.validateRewindPreviousThresholdSeconds(valid),
        )
        listOf(
            org.json.JSONObject(mapOf("music" to 3)),
            org.json.JSONObject(mapOf("music" to 3, "audiobook" to 15, "other" to 4)),
            org.json.JSONObject(mapOf("music" to 0, "audiobook" to 15)),
            org.json.JSONObject(mapOf("music" to 3, "audiobook" to 61)),
            org.json.JSONObject(mapOf("music" to 3.5, "audiobook" to 15)),
            3,
        ).forEach { value ->
            assertTrue(
                "Expected rejection for $value",
                runCatching { UiConfig.validateRewindPreviousThresholdSeconds(value) }.isFailure,
            )
        }
    }

    @Test
    fun `screen diagonal choices require sixteen increasing values and fixed endpoints`() {
        val valid =
            listOf(
                6.8,
                7.0,
                8.0,
                8.8,
                9.0,
                10.1,
                10.25,
                10.4,
                11.0,
                11.6,
                12.3,
                12.4,
                12.95,
                13.3,
                14.1,
                15.1,
            )
        assertEquals(16, UiConfig.validateScreenDiagonalChoices(valid).size)

        listOf(
            listOf(6.8, 15.1),
            List(15) { 6.8 } + 15.1,
            valid.dropLast(1) + 15.0,
        ).forEach { value ->
            assertTrue(
                runCatching { UiConfig.validateScreenDiagonalChoices(value) }.isFailure,
            )
        }
    }
}
