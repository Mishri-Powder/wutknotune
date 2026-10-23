package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusLanguageTest {
    private fun state(
        selectedStorageAvailable: Boolean = true,
        indexerStatus: String = "",
        libraryLoading: Boolean = false,
        playableTrackCount: Int? = 1,
    ) = PlayerUiState(
        screen = PlayerScreen.MAIN,
        root = BrowserItem("root", "Fixture", BrowserKind.FOLDER),
        nowPlaying = NowPlayingUiState(),
        selectedStorageAvailable = selectedStorageAvailable,
        indexerStatus = indexerStatus,
        libraryLoading = libraryLoading,
        libraryPlayableTrackCount = playableTrackCount,
    )

    @Test
    fun `main status uses the adopted priority`() {
        assertEquals(
            "Selected application-data storage is unavailable",
            mainStatusLine(
                state(
                    selectedStorageAvailable = false,
                    indexerStatus = "Analyzing audio: 1 / 2 tracks | 1.0 tracks/s",
                    libraryLoading = true,
                    playableTrackCount = 0,
                ),
            ),
        )
        assertEquals(
            "Analyzing audio: 1 / 2 tracks | 1.0 tracks/s",
            mainStatusLine(
                state(
                    indexerStatus = "Analyzing audio: 1 / 2 tracks | 1.0 tracks/s",
                    libraryLoading = true,
                    playableTrackCount = 0,
                ),
            ),
        )
        assertEquals("Loading updated music library…", mainStatusLine(state(libraryLoading = true)))
        assertEquals("Preparing music library…", mainStatusLine(state(playableTrackCount = null)))
        assertEquals(
            "No playable tracks found in selected media folders",
            mainStatusLine(state(playableTrackCount = 0)),
        )
        assertEquals("", mainStatusLine(state(playableTrackCount = 42)))
    }

    @Test
    fun `full loudness filename from Indexer is shown verbatim`() {
        assertEquals(
            "Full loudness analysis: track.flac",
            mainStatusLine(state(indexerStatus = "Full loudness analysis: track.flac")),
        )
    }
}
