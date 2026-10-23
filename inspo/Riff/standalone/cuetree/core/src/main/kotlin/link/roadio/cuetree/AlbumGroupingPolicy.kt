// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Factual occurrences eligible for ordinary albums in this snapshot only. */
data class AlbumGroupingInput(
    val snapshot: Snapshot,
    val eligibleSources: List<Int>,
    val cancellation: Cancellation,
    val vocabulary: CatalogVocabulary = CatalogVocabulary.EMPTY,
)

/** One ordered ordinary-album proposal; source indices are snapshot-local occurrences. */
data class OrdinaryAlbumGroup(
    val sources: List<Int>,
    val title: String,
    val attributes: Attributes,
)

/** Chooses ordinary album membership without owning source facts or catalog identities. */
fun interface AlbumGroupingPolicy {
    fun group(input: AlbumGroupingInput): List<OrdinaryAlbumGroup>
}

/** The bundled Album-tag, structural and weighted grouping rules. */
object DefaultAlbumGroupingPolicy : AlbumGroupingPolicy {
    override fun group(input: AlbumGroupingInput): List<OrdinaryAlbumGroup> {
        val evidence = AlbumEvidence(input.snapshot, input.eligibleSources, input.cancellation, input.vocabulary)
        return AlbumGrouping(evidence, LogicalAlbumPositions(evidence)).groups()
    }
}
