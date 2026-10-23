package link.roadio.riff.ui

import link.roadio.riff.indexer.core.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerControlPolicyTest {
    @Test
    fun `forward stops at queue end unless repeat is active`() {
        assertEquals(1, nextQueueIndex(queueSize = 3, currentIndex = 0, repeat = false))
        assertNull(nextQueueIndex(queueSize = 3, currentIndex = 2, repeat = false))
        assertEquals(0, nextQueueIndex(queueSize = 3, currentIndex = 2, repeat = true))
        assertNull(nextQueueIndex(queueSize = 0, currentIndex = 0, repeat = true))
    }

    @Test
    fun `reaction identity is the exact Media ID`() {
        assertEquals("track:ffffffffffffffff0000000000000000", trackReactionKey(MediaId(-1L)))
        assertEquals("track:000000000000002a0000000000000000", trackReactionKey(MediaId(42L)))
    }

    @Test
    fun `reaction reducer changes only preference and ban state`() {
        val banned = nextReactionState(ReactionState(), requested = null, toggleBan = true)
        assertEquals(ReactionState(ownBanned = true), banned)

        val disliked = nextReactionState(banned, Preference.DISLIKED, toggleBan = false)
        assertEquals(ReactionState(Preference.DISLIKED, ownBanned = true), disliked)

        val liked = nextReactionState(disliked, Preference.LIKED, toggleBan = false)
        assertEquals(ReactionState(Preference.LIKED, ownBanned = true), liked)

        val neutral = nextReactionState(liked, Preference.LIKED, toggleBan = false)
        assertEquals(ReactionState(Preference.NEUTRAL, ownBanned = true), neutral)
    }
}
