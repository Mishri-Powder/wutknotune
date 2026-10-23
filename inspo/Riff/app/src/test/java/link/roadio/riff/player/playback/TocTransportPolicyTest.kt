package link.roadio.riff.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TocTransportPolicyTest {
    @Test
    fun singletonTocIsIgnored() {
        assertNull(TocTransportPolicy.nextTarget(listOf(0L), 0L))
        assertNull(TocTransportPolicy.previousTarget(listOf(0L), 20_000L, 10_000L))
    }

    @Test
    fun malformedMarkerOrderIsRejectedInsteadOfRepaired() {
        assertNull(TocTransportPolicy.nextTarget(listOf(30_000L, 0L, 60_000L), 0L))
        assertNull(TocTransportPolicy.nextTarget(listOf(0L, 30_000L, 30_000L), 0L))
        assertNull(TocTransportPolicy.nextTarget(listOf(0L, -1L, 30_000L), 0L))
    }

    @Test
    fun nextUsesStrictlyLaterMarker() {
        val markers = listOf(0L, 30_000L, 60_000L)
        assertEquals(30_000L, TocTransportPolicy.nextTarget(markers, 0L))
        assertEquals(60_000L, TocTransportPolicy.nextTarget(markers, 30_000L))
        assertNull(TocTransportPolicy.nextTarget(markers, 60_000L))
    }

    @Test
    fun previousItemEntryUsesLastMarkerOnlyForUsableToc() {
        assertEquals(60_000L, TocTransportPolicy.previousItemEntryTarget(listOf(0L, 30_000L, 60_000L)))
        assertEquals(0L, TocTransportPolicy.previousItemEntryTarget(listOf(30_000L)))
        assertEquals(0L, TocTransportPolicy.previousItemEntryTarget(listOf(30_000L, 30_000L)))
    }

    @Test
    fun previousRestartsOrMovesAcrossChapterAndFileBoundary() {
        val markers = listOf(15_000L, 45_000L)
        assertEquals(45_000L, TocTransportPolicy.previousTarget(markers, 58_000L, 10_000L))
        assertEquals(15_000L, TocTransportPolicy.previousTarget(markers, 48_000L, 10_000L))
        assertEquals(0L, TocTransportPolicy.previousTarget(markers, 18_000L, 10_000L))
        assertNull(TocTransportPolicy.previousTarget(markers, 8_000L, 10_000L))
    }
}
