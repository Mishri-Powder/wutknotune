// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Exact complete linkage with linear working storage; no retained pair matrix. */
internal fun completeLinkageGroups(
    tracks: List<Int>,
    cancellation: Cancellation,
    score: (Int, Int) -> Double,
): List<List<Int>> {
    val clusters = tracks.map { mutableListOf(it) }.toMutableList()

    fun edge(
        a: Int,
        b: Int,
    ): Double {
        var minimum = Double.POSITIVE_INFINITY
        for (x in clusters[a]) {
            for (y in clusters[b]) {
                cancellation.check()
                minimum = minOf(minimum, score(x, y))
            }
        }
        return minimum
    }
    while (true) {
        val blocked = mutableSetOf<Int>()
        var selected: Pair<Int, Int>? = null
        while (selected == null) {
            var best = Double.NEGATIVE_INFINITY
            for (a in clusters.indices) {
                for (b in a + 1 until clusters.size) {
                    if (a !in blocked && b !in blocked) best = maxOf(best, edge(a, b))
                }
            }
            if (best < 65) break
            for (a in clusters.indices) {
                if (a in blocked) continue
                for (b in a + 1 until clusters.size) {
                    if (b in blocked || edge(a, b) != best) continue
                    val neighbors =
                        clusters.indices.filter {
                            it !in blocked && (it == a || it == b || edge(a, it) == best || edge(b, it) == best)
                        }
                    if (neighbors.indices.all { x -> (x + 1 until neighbors.size).all { y -> edge(neighbors[x], neighbors[y]) >= 65 } }) {
                        selected = a to b
                        break
                    }
                }
                if (selected != null) break
            }
            if (selected == null) {
                // Freeze the ambiguous component, then continue with unrelated candidates.
                val ambiguous = mutableSetOf<Int>()
                for (a in clusters.indices) {
                    for (b in a + 1 until clusters.size) {
                        if (a !in blocked && b !in blocked && edge(a, b) == best) {
                            ambiguous += a
                            ambiguous += b
                        }
                    }
                }
                blocked.addAll(ambiguous)
            }
        }
        if (selected == null) break
        clusters[selected.first].addAll(clusters[selected.second])
        clusters.removeAt(selected.second)
    }
    return clusters
}
