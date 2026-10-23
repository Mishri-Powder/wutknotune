package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistRootNavigationTest {
    private fun fixture(
        mode: ContentMode,
        hierarchy: HierarchyRoot = HierarchyRoot.PLAYLISTS,
    ): PlayerUiState {
        val track = BrowserItem("track", "Track", BrowserKind.TRACK)
        val playlist = BrowserItem("playlist", "Collection", BrowserKind.PLAYLIST, children = listOf(track))
        val category = BrowserItem(hierarchy.categoryId, "Category", BrowserKind.CATEGORY, children = listOf(playlist))
        return PlayerUiState(
            root = BrowserItem("root", "Root", BrowserKind.ROOT, children = listOf(category)),
            nowPlaying = NowPlayingUiState(),
            contentMode = mode,
            screen = PlayerScreen.HIERARCHY,
            browserPath = listOf(category.id),
            hierarchyRoot = hierarchy,
            selectorIndex = 2,
            selectorOffset = 13,
            focusedItemId = playlist.id,
            azSteps = listOf(AzGroup.LATIN_AC),
        )
    }

    @Test
    fun mainCollectionLauncherRestoresRootMemoryAndRetainsChildMemory() {
        for (mode in ContentMode.entries) {
            val navigation = PlayerNavigationState()
            val list = fixture(mode)
            navigation.rememberHierarchy(list)
            val child =
                navigation.navigate(list, "playlist").copy(
                    selectorIndex = 7,
                    selectorOffset = 29,
                    focusedItemId = "track",
                    azSteps = listOf(AzGroup.LATIN_DF),
                )
            navigation.rememberHierarchy(child)
            val reopened = navigation.openCategory(child.openMain(), HierarchyRoot.PLAYLISTS.categoryId)
            assertEquals(list.browserPath, reopened.browserPath)
            assertEquals(list.azSteps, reopened.azSteps)
            assertEquals(list.selectorIndex, reopened.selectorIndex)
            assertEquals(list.selectorOffset, reopened.selectorOffset)
            assertEquals(list.focusedItemId, reopened.focusedItemId)
            val revisited = navigation.navigate(reopened, "playlist")
            assertEquals(child.browserPath, revisited.browserPath)
            assertEquals(child.azSteps, revisited.azSteps)
            assertEquals(child.selectorIndex, revisited.selectorIndex)
            assertEquals(child.selectorOffset, revisited.selectorOffset)
            assertEquals(child.focusedItemId, revisited.focusedItemId)
        }
    }

    @Test
    fun firstCreatedCollectionDoesNotBecomeMainShortcut() {
        val navigation = PlayerNavigationState()
        val child = fixture(ContentMode.MUSIC).copy(browserPath = listOf(HierarchyRoot.PLAYLISTS.categoryId, "playlist"))
        navigation.rememberHierarchy(child)
        val reopened = navigation.openCategory(child.openMain(), HierarchyRoot.PLAYLISTS.categoryId)
        assertEquals(listOf(HierarchyRoot.PLAYLISTS.categoryId), reopened.browserPath)
        assertEquals(emptyList<AzGroup>(), reopened.azSteps)
        assertEquals(0, reopened.selectorIndex)
        assertEquals(0, reopened.selectorOffset)
        assertNull(reopened.focusedItemId)
    }

    @Test
    fun otherCategoriesRetainTheirLastChildPath() {
        val navigation = PlayerNavigationState()
        val child =
            fixture(
                ContentMode.MUSIC,
                HierarchyRoot.FOLDERS,
            ).copy(browserPath = listOf(HierarchyRoot.FOLDERS.categoryId, "playlist"))
        navigation.rememberHierarchy(child)
        val reopened = navigation.openCategory(child.openMain(), HierarchyRoot.FOLDERS.categoryId)
        assertEquals(child.browserPath, reopened.browserPath)
        assertEquals(child.azSteps, reopened.azSteps)
    }
}
