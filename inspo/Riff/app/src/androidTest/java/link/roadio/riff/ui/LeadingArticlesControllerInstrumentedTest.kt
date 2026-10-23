package link.roadio.riff.ui

import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.core.settings.PlayerDataLocation
import link.roadio.riff.player.library.PlayerCuetreeClient
import link.roadio.riff.player.storage.PlayerProcessStore
import link.roadio.riff.storage.root.ApplicationDataStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LeadingArticlesControllerInstrumentedTest {
    @Test
    fun savedPolicyReordersPlaylistsWithoutARepositoryReloadAndSurvivesRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".instrumented"))
        check(ApplicationDataStorage(context).prepareForStartup())
        val config = UiConfig.load(context)
        val store =
            requireNotNull(
                PlayerProcessStore.open(
                    context,
                    config.long("player.recentHistoryGenerations"),
                    config.long("player.bookmarkMinSpacingSeconds"),
                ),
            )
        val profile = store.activeProfile()
        val settings = (1..3).associateWith { store.loadSettingsForProfile(it, PlayerDataLocation.HU) }
        var controller: RiffPlayerUiController? = null
        val playlistIds = mutableListOf<Long>()

        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

        fun isolatedController(): RiffPlayerUiController =
            RiffPlayerUiController(context).also { current ->
                // This test owns synthetic playlist members, not the process repository's catalog.
                val field = RiffPlayerUiController::class.java.getDeclaredField("libraryClient")
                field.isAccessible = true
                (field.get(current) as PlayerCuetreeClient).close()
            }

        fun awaitPolicy(
            enabled: Boolean,
            expected: List<Long>,
        ) {
            val deadline = System.nanoTime() + 10_000_000_000L
            var ready = false
            while (System.nanoTime() < deadline && !ready) {
                onMain {
                    val state = requireNotNull(controller).state
                    val ids =
                        state.root.children
                            .first { it.id == HierarchyRoot.PLAYLISTS.categoryId }
                            .children
                            .mapNotNull { it.playlistId }
                            .filter { it in playlistIds }
                    ready =
                        state.settings.ignoreLeadingArticles == enabled &&
                        state.root.ignoreLeadingArticles == enabled &&
                        ids == expected
                }
                if (!ready) Thread.sleep(20)
            }
            val snapshot = requireNotNull(controller).state
            val ids =
                snapshot.root.children
                    .flatMap { it.children }
                    .mapNotNull { it.playlistId }
            assertTrue(
                "Saved policy/order: settings=${snapshot.settings.ignoreLeadingArticles}, " +
                    "root=${snapshot.root.ignoreLeadingArticles}, ids=$ids, status=${snapshot.status}",
                ready,
            )
        }
        try {
            store.commitProfileSelection(1, settings.getValue(1).copy(ignoreLeadingArticles = true))
            val suffix = UUID.randomUUID().toString()
            playlistIds += store.createPlaylist("The Article $suffix", listOf(MediaId(10))).playlistId
            playlistIds += store.createPlaylist("Echo $suffix", listOf(MediaId(10))).playlistId
            onMain { controller = isolatedController() }
            awaitPolicy(true, playlistIds)
            onMain {
                controller!!.dispatch(PlayerUiAction.OpenMain)
                controller!!.dispatch(PlayerUiAction.OpenSettingsLongPress)
                controller!!.dispatch(PlayerUiAction.SetDraftIgnoreLeadingArticles(false))
                controller!!.dispatch(PlayerUiAction.CancelSettings)
            }
            awaitPolicy(true, playlistIds)
            onMain {
                controller!!.dispatch(PlayerUiAction.OpenSettingsLongPress)
                controller!!.dispatch(PlayerUiAction.SetDraftIgnoreLeadingArticles(false))
                controller!!.dispatch(PlayerUiAction.SaveSettings)
            }
            awaitPolicy(false, playlistIds.reversed())
            assertEquals(false, store.loadSettingsForProfile(1, PlayerDataLocation.HU).ignoreLeadingArticles)
            onMain {
                controller!!.close()
                controller = isolatedController()
            }
            awaitPolicy(false, playlistIds.reversed())
        } finally {
            onMain { controller?.close() }
            store.commitProfileSelection(1, settings.getValue(1))
            playlistIds.forEach(store::deletePlaylist)
            store.commitProfileSelection(profile, settings.getValue(profile))
        }
    }
}
