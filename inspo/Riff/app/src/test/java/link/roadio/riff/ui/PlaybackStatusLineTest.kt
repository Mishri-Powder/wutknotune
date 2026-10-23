package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.selection.AiFeaturePoint
import link.roadio.riff.player.library.PlayerPteFeature
import link.roadio.riff.player.library.PlayerReplayGainValue
import link.roadio.riff.player.library.PlayerTopInferredGenre
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStatusLineTest {
    @Test
    fun `formatter emits the exact locale-independent line and explicit placeholders`() {
        assertEquals(
            "A 0.3 V -0.4 PTE 0.1 RG -6.0 0.5/Disco",
            playbackStatusLine(
                PlaybackDiagnosticsUiState(
                    arousal = 0.34f,
                    valence = -0.44f,
                    pte = 0.14f,
                    replayGainDb = -6f,
                    genre = PlaybackGenreDiagnostic(0.54f, "Disco"),
                ),
            ),
        )
        assertEquals(
            "A -- V -- PTE -- RG -- --/--",
            playbackStatusLine(PlaybackDiagnosticsUiState()),
        )
        assertEquals(
            "A -- V -- PTE -- RG 0.0 --/--",
            playbackStatusLine(PlaybackDiagnosticsUiState(replayGainDb = 0f)),
        )
    }

    @Test
    fun `projection reacts to current media and accepted MDRG values`() {
        val features = mapOf(MediaId(1L) to AiFeaturePoint(MediaId(1L), 0.25f, -0.5f, 0.75f, 0.5f))
        val pte = mapOf(MediaId(1L) to PlayerPteFeature(0.1f, 0.9f))
        val genres = mapOf(MediaId(1L) to PlayerTopInferredGenre(-100_022L, "Disco", 0.8f))

        val present = playbackDiagnosticsFor(MediaId(1L), emptyList(), features, pte, emptyMap(), genres)
        val absent = playbackDiagnosticsFor(MediaId(2L), emptyList(), features, pte, emptyMap(), genres)

        assertEquals(0.25f, present.arousal)
        assertEquals(-0.5f, present.valence)
        assertEquals(0.1f, present.pte)
        assertEquals(PlaybackGenreDiagnostic(0.8f, "Disco"), present.genre)
        assertEquals(PlaybackDiagnosticsUiState(), absent)
    }

    @Test
    fun `projection uses common album gain otherwise current track gain`() {
        val gains =
            mapOf(
                MediaId(1L) to PlayerReplayGainValue(trackGainQ8 = (-256).toShort(), albumGainQ8 = 384),
                MediaId(2L) to PlayerReplayGainValue(trackGainQ8 = 128, albumGainQ8 = 384),
            )

        assertEquals(
            1.5f,
            playbackDiagnosticsFor(MediaId(1L), listOf(1L, 1L, 2L).map(::MediaId), emptyMap(), emptyMap(), gains, emptyMap()).replayGainDb,
        )

        val differentAlbum = gains + (MediaId(2L) to PlayerReplayGainValue(trackGainQ8 = 128, albumGainQ8 = 385))
        assertEquals(
            -1f,
            playbackDiagnosticsFor(
                MediaId(1L),
                listOf(1L, 2L).map(::MediaId),
                emptyMap(),
                emptyMap(),
                differentAlbum,
                emptyMap(),
            ).replayGainDb,
        )

        val missingAlbum = gains + (MediaId(2L) to PlayerReplayGainValue(trackGainQ8 = 0))
        assertEquals(
            0f,
            playbackDiagnosticsFor(
                MediaId(2L),
                listOf(1L, 2L).map(::MediaId),
                emptyMap(),
                emptyMap(),
                missingAlbum,
                emptyMap(),
            ).replayGainDb,
        )
        assertEquals(
            null,
            playbackDiagnosticsFor(MediaId(3L), listOf(1L, 3L).map(::MediaId), emptyMap(), emptyMap(), gains, emptyMap()).replayGainDb,
        )
    }
}
