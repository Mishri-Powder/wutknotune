package link.roadio.riff.ui

import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.player.core.settings.PlayerDataLocation
import link.roadio.riff.player.storage.PlayerProcessStore
import link.roadio.riff.player.storage.StoredContentMode
import link.roadio.riff.storage.root.ApplicationDataStorage
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercises the production controller with the isolated test application's Player store. */
class BooksPersistenceInstrumentedTest {
    @Test
    fun booksSurvivesControllerRecreationAndSettingsProfileRoundTrip() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
        val originalProfile = store.activeProfile()
        val modes = (1..3).associateWith { store.loadContentMode(it) }
        var controller: RiffPlayerUiController? = null

        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

        fun await(
            expected: ContentMode,
            profile: Int,
        ) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (System.nanoTime() < deadline) {
                var ready = false
                onMain { ready = controller!!.state.contentMode == expected && controller!!.state.settings.profileId == profile }
                if (ready) return
                Thread.sleep(20)
            }
            onMain {
                assertEquals(expected, controller!!.state.contentMode)
                assertEquals(profile, controller!!.state.settings.profileId)
            }
        }

        fun select(profile: Int) {
            onMain {
                controller!!.dispatch(PlayerUiAction.OpenMain)
                controller!!.dispatch(PlayerUiAction.OpenSettingsLongPress)
                controller!!.dispatch(PlayerUiAction.SetDraftProfile(profile))
                controller!!.dispatch(PlayerUiAction.SaveSettings)
            }
        }
        try {
            store.commitProfileSelection(1, store.loadSettingsForProfile(1, PlayerDataLocation.HU))
            store.setContentMode(StoredContentMode.MUSIC, 1)
            store.setContentMode(StoredContentMode.MUSIC, 2)
            onMain {
                controller = RiffPlayerUiController(context)
                controller!!.dispatch(PlayerUiAction.OpenMain)
                controller!!.dispatch(PlayerUiAction.ToggleContentMode)
            }
            await(ContentMode.AUDIOBOOK, 1)
            onMain {
                controller!!.close()
                controller = RiffPlayerUiController(context)
            }
            await(ContentMode.AUDIOBOOK, 1)
            select(2)
            await(ContentMode.MUSIC, 2)
            select(1)
            await(ContentMode.AUDIOBOOK, 1)
            onMain {
                controller!!.close()
                controller = RiffPlayerUiController(context)
            }
            await(ContentMode.AUDIOBOOK, 1)
        } finally {
            onMain { controller?.close() }
            modes.forEach { (profile, mode) -> store.setContentMode(mode, profile) }
            store.commitProfileSelection(originalProfile, store.loadSettingsForProfile(originalProfile, PlayerDataLocation.HU))
        }
    }
}
