package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryReloadScreenPolicyTest {
    @Test
    fun `library-independent screens survive a reload without a hierarchy path`() {
        val independent =
            setOf(
                PlayerScreen.PLAYBACK,
                PlayerScreen.MAIN,
                PlayerScreen.AI,
                PlayerScreen.AI_ACTIONS,
                PlayerScreen.SETTINGS,
                PlayerScreen.SAF_GRANTS,
                PlayerScreen.CALIBRATION,
                PlayerScreen.SCREEN_DIAGONAL,
            )

        independent.forEach { screen ->
            assertEquals(screen, libraryReloadDestination(screen, hierarchyPathValid = false))
        }
    }

    @Test
    fun `hierarchy-owned screens fall back only when their path disappears`() {
        listOf(PlayerScreen.HIERARCHY, PlayerScreen.AZ_SEARCH, PlayerScreen.ROW_ACTIONS).forEach { screen ->
            assertEquals(screen, libraryReloadDestination(screen, hierarchyPathValid = true))
            assertEquals(PlayerScreen.MAIN, libraryReloadDestination(screen, hierarchyPathValid = false))
        }
    }

    @Test
    fun `playlist selector keeps AI source but repairs a missing hierarchy source`() {
        assertEquals(
            PlayerScreen.PLAYLIST_SELECTOR,
            libraryReloadDestination(
                PlayerScreen.PLAYLIST_SELECTOR,
                hierarchyPathValid = false,
                aiPlaylistSelector = true,
            ),
        )
        assertEquals(
            PlayerScreen.PLAYLIST_SELECTOR,
            libraryReloadDestination(PlayerScreen.PLAYLIST_SELECTOR, hierarchyPathValid = true),
        )
        assertEquals(
            PlayerScreen.MAIN,
            libraryReloadDestination(PlayerScreen.PLAYLIST_SELECTOR, hierarchyPathValid = false),
        )
    }
}
