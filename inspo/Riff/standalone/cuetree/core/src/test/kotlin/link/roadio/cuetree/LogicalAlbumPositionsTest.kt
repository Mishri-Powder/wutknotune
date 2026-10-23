package link.roadio.cuetree

import org.junit.Assert.*
import org.junit.Test

class LogicalAlbumPositionsTest {
    private fun positions(vararg metadata: Attributes): LogicalAlbumPositions {
        val root = Node(NodeKind.DIRECTORY, MediaId(-1), source = SourceInfo("root", provider = "memory", locator = "/"), life = Life(1))
        val common = mapOf("album" to listOf("Album"), "title" to listOf("Title"), "artist" to listOf("Band"))
        val nodes =
            listOf(root) +
                metadata.mapIndexed { i, attrs ->
                    Node(NodeKind.MEDIA, MediaId(10, 20), root.id, SourceInfo("$i.mp3"), attributes = common + attrs, life = Life(1))
                }
        return LogicalAlbumPositions(AlbumEvidence(Snapshot(1, "test", nodes), metadata.indices.map { it + 1 }, Cancellation()))
    }

    private fun numbered(
        disc: String? = null,
        track: String? = "1",
        total: String? = null,
    ): Attributes =
        listOf("discNumber" to disc, "trackNumber" to track, "trackTotal" to total)
            .mapNotNull { (key, value) -> value?.let { key to listOf(it) } }
            .toMap()

    @Test fun missingOptionalTotalAttachesToUniqueDeclaredPosition() {
        val copies = positions(numbered("1", total = "9"), numbered("1"))
        assertEquals(listOf(1), copies.representatives)
        assertEquals(setOf(1, 2), copies.claimed(1).toSet())
    }

    @Test fun missingDiscCannotBridgeTwoKnownDiscs() {
        val copies = positions(numbered("1"), numbered("2"), numbered())
        assertEquals(3, copies.representatives.size)
    }

    @Test fun differentTrackRemainsAnIntentionalRepeat() {
        assertEquals(2, positions(numbered("1", "1"), numbered("1", "2")).representatives.size)
    }

    @Test fun contradictoryOptionalTotalsRemainSeparate() {
        assertEquals(3, positions(numbered("1", total = "8"), numbered("1", total = "9"), numbered("1")).representatives.size)
    }

    @Test fun partialChainHasOneDeclaredCarrierWithoutLosingItsPosition() {
        val copies = positions(numbered("1", total = "9"), numbered("1"), numbered())
        assertEquals(listOf(1), copies.representatives)
        assertEquals(setOf(1, 2, 3), copies.claimed(1).toSet())
    }
}
