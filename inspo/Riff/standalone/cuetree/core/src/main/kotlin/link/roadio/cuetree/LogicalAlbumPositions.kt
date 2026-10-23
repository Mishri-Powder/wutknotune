// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Copy classes are scan-local; known different positions never disappear through audio-ID equality. */
internal class LogicalAlbumPositions(
    val evidence: AlbumEvidence,
) {
    private val aliases = mutableMapOf<Int, List<Int>>()
    val representatives: List<Int>

    init {
        val result = mutableListOf<Int>()
        for (bucket in evidence.facts.keys
            .groupBy { evidence.snapshot.nodes[it].id }
            .values) {
            evidence.cancellation.check()
            val exact = bucket.groupBy { position(it) }.values.map { it.sortedWith(evidence.stable) }
            // Choose independently declared maximal positions, never a transitive missing-field bridge.
            val known =
                exact.filter { candidate ->
                    exact.none { other -> other !== candidate && extends(candidate.first(), other.first()) }
                }
            val joined = mutableSetOf<Int>()
            val extra = mutableMapOf<Int, MutableList<Int>>()
            for (indexes in exact) {
                if (indexes in known) continue
                val targets = known.filter { extends(indexes.first(), it.first()) }
                if (targets.size == 1) {
                    extra.getOrPut(targets.single().first()) { mutableListOf() }.addAll(indexes)
                    joined.addAll(indexes)
                }
            }
            for (indexes in exact) {
                if (indexes.first() in joined) continue
                val copies = (indexes + extra[indexes.first()].orEmpty()).sortedWith(evidence.stable)
                val representative = indexes.first()
                result += representative
                aliases[representative] = copies
            }
        }
        representatives = result.sortedWith(evidence.stable)
    }

    fun claimed(index: Int): List<Int> = aliases[index] ?: listOf(index)

    private fun position(index: Int): List<Any?> {
        val f = evidence.facts.getValue(index)
        return listOf(evidence.key(index), evidence.disc(index), f.track, f.title, f.artist, f.rawAa, f.total, f.discs)
    }

    private fun extends(
        partial: Int,
        declared: Int,
    ): Boolean {
        val x = position(partial)
        val y = position(declared)
        return x.indices.all { i ->
            if (i == 1 || i == 2 || i == 6 || i == 7) x[i] == null || x[i] == y[i] else x[i] == y[i]
        }
    }
}
