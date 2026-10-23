package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PageableSettingsTest {
    @Test
    fun `media folders heading has exact visible text`() {
        assertEquals("Media folders…", MEDIA_FOLDERS_HEADING)
    }

    @Test
    fun `primary settings and more settings follow the contract order`() {
        assertEquals(
            listOf(
                SettingsItem.USER,
                SettingsItem.LONG_PRESS,
                SettingsItem.AUTO_SEARCH,
                SettingsItem.IGNORE_LEADING_ARTICLES,
                SettingsItem.THEME,
                SettingsItem.MORE,
            ),
            settingsItems(),
        )
        assertEquals(
            listOf(
                SettingsItem.MEDIA_FOLDERS,
                SettingsItem.DIAGONAL,
                SettingsItem.DATA_LOCATION,
                SettingsItem.DIM_BACKLIGHT,
                SettingsItem.REPLAY_GAIN,
                SettingsItem.REBUILD_DB,
            ),
            moreSettingsItems(),
        )
    }

    @Test
    fun `SAF grant editor remains usable while selected data root is unavailable`() {
        listOf(
            PlayerUiAction.OpenSafGrantSelector,
            PlayerUiAction.OpenSafDocumentTree,
            PlayerUiAction.SafGrantPicked("content://grant/new", android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
            PlayerUiAction.SaveSafGrantSelector,
            PlayerUiAction.CancelSafGrantSelector,
            PlayerUiAction.RequestSafGrantRemoval("content://grant"),
            PlayerUiAction.ConfirmSafGrantRemoval,
        ).forEach { action -> assertFalse(action.requiresAvailableStorage()) }
    }
}
