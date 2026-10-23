package link.roadio.riff.player.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkActionPolicyTest {
    @Test
    fun `only strict AAOS requires proven unrestricted motion for Mark`() {
        for (mode in ApplicationMode.entries) {
            for (motion in MotionRestriction.entries) {
                assertEquals(
                    mode != ApplicationMode.AAOS || motion == MotionRestriction.UNRESTRICTED,
                    BookmarkActionPolicy(mode, motion).allowed,
                )
            }
        }
    }
}
