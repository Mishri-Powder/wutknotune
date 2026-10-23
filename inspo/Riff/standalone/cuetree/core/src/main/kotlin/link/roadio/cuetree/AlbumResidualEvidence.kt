// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Only facts actually present in MDIA contribute; no extraction is added to fill the score table. */
internal class AlbumResidualEvidence(
    private val evidence: AlbumEvidence,
    private val releases: AlbumReleaseEvidence,
) {
    fun score(
        a: Int,
        b: Int,
    ): Double {
        val x = evidence.facts.getValue(a)
        val y = evidence.facts.getValue(b)
        if (releases.blocks(a, b) || (x.aa.isNotEmpty() && y.aa.isNotEmpty() && x.aa != y.aa)) return Double.NEGATIVE_INFINITY
        var value = if (x.placeholderAlbum || y.placeholderAlbum) 0.0 else 40.0
        if (x.aa.isNotEmpty() && x.aa == y.aa) {
            value += 20
        } else if (x.rawAa.isNotEmpty() && x.rawAa == y.rawAa) {
            value += 3
        }
        if (x.artist.isNotEmpty() && x.artist == y.artist) value += 6
        if (x.year != null && y.year != null) value += if (x.year == y.year) 6 else -12
        if (x.node.parentId == y.node.parentId || evidence.families.same(a, b)) value += 25
        if ((x.genre + y.genre).isNotEmpty()) value += 2.0 * x.genre.intersect(y.genre).size / (x.genre + y.genre).size
        if (x.comment.isNotEmpty() && y.comment.isNotEmpty()) value += if (x.comment == y.comment) 3 else -2
        var positive = 0
        val da = evidence.disc(a)
        val db = evidence.disc(b)
        if (da == db) {
            if (x.total != null && y.total != null) {
                if (x.total == y.total) positive += 8 else value -= 25
            }
            if (x.track != null && y.track != null) {
                if (x.track == y.track && x.node.id != y.node.id) {
                    value -= 10
                } else if (x.track != y.track && x.total != null && x.total == y.total && x.track <= x.total && y.track <= x.total) {
                    positive += 4
                }
            }
        }
        if (x.discs != null && y.discs != null) {
            if (x.discs == y.discs) positive += 5 else value -= 20
            if (da != null && db != null && da != db && x.discs == y.discs && maxOf(da, db) <= x.discs) positive += 4
        }
        if (x.boundsInvalid || y.boundsInvalid) value -= 35
        return value + minOf(positive, 15)
    }

    /** Linear-space lower bound proves common large folders without visiting every pair. */
    fun lowerBound(group: List<Int>): Double {
        val facts = group.map(evidence.facts::getValue)
        var value = if (facts.any { it.placeholderAlbum }) 0.0 else 40.0
        val aa = facts.map { it.aa }.distinct()
        if (aa.size == 1 && aa.single().isNotEmpty()) {
            value += 20
        } else if (aa.count { it.isNotEmpty() } > 1) {
            return Double.NEGATIVE_INFINITY
        }
        val generic = facts.map { it.rawAa }.distinct()
        if (aa.all { it.isEmpty() } && generic.size == 1 && generic.single().isNotEmpty()) value += 3
        if (facts.map { it.artist }.distinct().let { it.size == 1 && it.single().isNotEmpty() }) value += 6
        val years = facts.mapNotNull { it.year }.distinct()
        if (years.size > 1) {
            value -= 12
        } else if (years.size == 1 && facts.all { it.year != null }) {
            value += 6
        }
        if (facts.map { it.node.parentId }.distinct().size == 1) value += 25
        val comments = facts.map { it.comment }.filter { it.isNotEmpty() }.distinct()
        if (comments.size > 1) {
            value -= 2
        } else if (comments.size == 1 && facts.all { it.comment.isNotEmpty() }) {
            value += 3
        }
        val totals = group.groupBy(evidence::disc).values
        if (totals.any { indexes -> indexes.mapNotNull { evidence.facts.getValue(it).total }.distinct().size > 1 }) value -= 25
        if (facts.mapNotNull { it.discs }.distinct().size > 1) value -= 20
        if (facts.any { it.boundsInvalid }) value -= 35
        if (colliding(group).isNotEmpty()) value -= 10
        return value
    }

    fun colliding(group: List<Int>): Set<Int> {
        val numbered = group.filter { evidence.facts.getValue(it).track != null }
        val result = mutableSetOf<Int>()
        for (slot in numbered.groupBy { evidence.disc(it) to evidence.facts.getValue(it).track }.values) {
            if (slot
                    .map {
                        evidence.facts
                            .getValue(it)
                            .node.id
                    }.distinct()
                    .size > 1
            ) {
                result.addAll(slot)
            }
        }
        val byTrack = numbered.groupBy { evidence.facts.getValue(it).track }
        for (slot in byTrack.values) {
            val unknown = slot.filter { evidence.disc(it) == null }
            val known = slot.filter { evidence.disc(it) != null }
            if (unknown.isNotEmpty() &&
                known.isNotEmpty() &&
                (unknown + known)
                    .map {
                        evidence.facts
                            .getValue(it)
                            .node.id
                    }.distinct()
                    .size > 1
            ) {
                result.addAll(slot)
            }
        }
        return result
    }

    fun allPairs(group: List<Int>): Boolean {
        if (lowerBound(group) >= 65 && !releaseConflict(group)) return true
        for (a in group.indices) {
            for (b in a + 1 until group.size) {
                evidence.cancellation.check()
                if (score(group[a], group[b]) < 65) return false
            }
        }
        return true
    }

    fun releaseConflict(group: List<Int>): Boolean {
        val folders =
            group
                .groupBy {
                    evidence.facts
                        .getValue(it)
                        .node.parentId
                }.values
                .map { it.first() }
                .filter(releases::relevant)
        for (a in folders.indices) {
            for (b in a + 1 until folders.size) {
                evidence.cancellation.check()
                if (releases.blocks(folders[a], folders[b])) return true
            }
        }
        return false
    }
}
