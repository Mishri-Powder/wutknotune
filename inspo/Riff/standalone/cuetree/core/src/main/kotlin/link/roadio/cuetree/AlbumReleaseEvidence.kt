// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Independently coherent overlapping local sequences are alternatives, not extra discs. */
internal class AlbumReleaseEvidence(
    private val evidence: AlbumEvidence,
    sources: List<Int>,
) {
    private data class Context(
        val members: List<Int>,
        val slots: Map<Int, List<Int>>,
        val coherent: Boolean,
        val single: Boolean,
    )

    private val contexts =
        sources
            .groupBy {
                evidence.facts
                    .getValue(it)
                    .node.parentId
            }.mapValues { (_, indexes) ->
                val f = indexes.map(evidence.facts::getValue)
                val slots = indexes.filter { evidence.facts.getValue(it).track != null }.groupBy { evidence.facts.getValue(it).track!! }
                val numbering = f.size >= 2 && f.all { it.track != null && !it.boundsInvalid } && slots.values.all { it.size == 1 }
                val artists = f.map { it.aa.ifEmpty { it.artist } }.distinct()
                Context(indexes, slots, numbering && ((artists.size == 1 && artists.single().isNotEmpty()) || f.all { it.total != null }), f.size == 1 && f.single().track == 1 && f.single().total == 1)
            }

    fun relevant(index: Int): Boolean =
        contexts
            .getValue(
                evidence.facts
                    .getValue(index)
                    .node.parentId,
            ).let { it.coherent || it.single }

    fun blocks(
        a: Int,
        b: Int,
    ): Boolean {
        val x = evidence.facts.getValue(a)
        val y = evidence.facts.getValue(b)
        if (x.node.parentId == y.node.parentId) return false
        if (evidence.families.same(a, b) && evidence.disc(a) != evidence.disc(b)) return false
        val left = contexts.getValue(x.node.parentId)
        val right = contexts.getValue(y.node.parentId)
        if (left.single && right.single && x.title.isNotEmpty() && y.title.isNotEmpty() && x.title != y.title && x.node.id != y.node.id) return true
        if (!left.coherent || !right.coherent) return false
        var overlaps = 0
        for ((track, indexes) in left.slots) {
            val other = right.slots[track] ?: continue
            if (indexes.any { i ->
                    other.any { j ->
                        overlap(i, j) &&
                            evidence.facts
                                .getValue(i)
                                .node.id !=
                            evidence.facts
                                .getValue(j)
                                .node.id
                    }
                }
            ) {
                overlaps++
                if (overlaps >= 2) return true
            }
        }
        return false
    }

    private fun overlap(
        a: Int,
        b: Int,
    ): Boolean {
        val x = evidence.disc(a)
        val y = evidence.disc(b)
        return x == null || y == null || x == y
    }
}
