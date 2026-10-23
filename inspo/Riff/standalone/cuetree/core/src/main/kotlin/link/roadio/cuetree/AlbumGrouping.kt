// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Global candidates are resolved before provisional artist/folder groups can be published. */
internal class AlbumGrouping(
    private val evidence: AlbumEvidence,
    private val positions: LogicalAlbumPositions,
) {
    private val facts = evidence.facts
    private val cancellation = evidence.cancellation

    private data class Candidate(
        val sources: List<Int>,
        val strength: Int,
    )

    fun groups(): List<OrdinaryAlbumGroup> =
        positions.representatives
            .filter { facts.getValue(it).album.isNotBlank() }
            .groupBy(evidence::key)
            .values
            .flatMap { candidate ->
                cancellation.check()
                resolve(candidate).map { sources ->
                    val ordered = sources.sortedWith(evidence.order)
                    OrdinaryAlbumGroup(ordered, evidence.title(ordered), evidence.agreed(ordered))
                }
            }

    private fun resolve(input: List<Int>): List<List<Int>> {
        val releases = AlbumReleaseEvidence(evidence, input)
        val residual = AlbumResidualEvidence(evidence, releases)

        fun proof(group: List<Int>): Int {
            cancellation.check()
            if (group.isEmpty() || residual.releaseConflict(group)) return -1
            val f = group.map(facts::getValue)
            val aa = f.map { it.aa }.filter { it.isNotEmpty() }.distinct()
            if (structural(group, residual)) return 4
            if (f.any { it.placeholderAlbum }) return if (residual.allPairs(group) && group.size > 1) 0 else -1
            if (aa.size > 1) return -1
            if (aa.size == 1 && f.all { it.aa == aa.single() }) return 3
            if (f.size >= 2 && f.all { it.track == null && evidence.disc(it.index) == null && it.year != null } && f.map { it.year }.distinct().size == 1) return 2
            if (f.size >= 2 && f.map { it.artist }.distinct().let { it.size == 1 && it.single().isNotEmpty() }) return 1
            return if (group.size > 1 && residual.allPairs(group)) 0 else -1
        }
        if (input.size == 1) return listOf(input)
        if (proof(input) >= 0) return listOf(input)

        val seeds = mutableListOf<List<Int>>()
        val collisions = residual.colliding(input)
        val clean = input.filterNot { it in collisions }
        seeds += listOf(clean)
        seeds += input.groupBy { facts.getValue(it).aa }.filterKeys { it.isNotEmpty() }.values
        seeds += input.groupBy { facts.getValue(it).artist }.filterKeys { it.isNotEmpty() }.values
        seeds += input.filter { facts.getValue(it).track == null && evidence.disc(it) == null && facts.getValue(it).year != null }.groupBy { facts.getValue(it).year }.values
        seeds += input.groupBy { facts.getValue(it).node.parentId }.values
        var candidates =
            seeds.distinctBy { it.toSet() }.mapNotNull { group ->
                val strength = proof(group)
                group.takeIf { strength >= 0 && (group.size > 1 || singleton(group.single(), input, releases)) }?.let { Candidate(it, strength) }
            }
        candidates =
            candidates.filter { candidate ->
                candidates.none { other -> other !== candidate && other.strength >= candidate.strength && other.sources.size > candidate.sources.size && other.sources.containsAll(candidate.sources) }
            }
        // Resolve overlapping evidence as a batch; enumeration order cannot select a partition.
        val assignments =
            input.associateWith { index ->
                val available = candidates.filter { index in it.sources }
                val best = available.maxOfOrNull { it.strength }
                available.filter { it.strength == best }
            }
        val groups =
            candidates
                .mapNotNull { candidate ->
                    val retained = candidate.sources.filter { assignments.getValue(it).size == 1 && assignments.getValue(it).single() == candidate }
                    retained.takeIf { it.isNotEmpty() && proof(it) >= 0 && (it.size > 1 || singleton(it.single(), input, releases)) }
                }.toMutableList()
        val claimed = groups.flatten().toSet()
        val pending = input.filterNot { it in claimed }
        val destinations =
            pending.associateWith { index ->
                groups.indices.filter { group -> proof(groups[group] + index) >= 0 }
            }
        for (g in groups.indices) {
            val attachments = pending.filter { destinations.getValue(it) == listOf(g) }
            if (attachments.isEmpty()) continue
            val combined = groups[g] + attachments
            if (proof(combined) >= 0) {
                groups[g] = combined
            } else {
                val conflicting = residual.colliding(combined)
                val coherent = attachments.filterNot { it in conflicting }
                if (coherent.isNotEmpty() && proof(groups[g] + coherent) >= 0) groups[g] = groups[g] + coherent
            }
        }
        val used = groups.flatten().toSet()
        val leftover = input.filterNot { it in used }
        for (cluster in completeLinkageGroups(leftover, cancellation, residual::score)) {
            if (cluster.size > 1 && proof(cluster) >= 0) {
                groups += cluster
            } else if (cluster.size == 1 && singleton(cluster.single(), input, releases)) {
                groups += cluster
            }
        }
        return groups
    }

    private fun structural(
        group: List<Int>,
        residual: AlbumResidualEvidence,
    ): Boolean {
        if (group.size < 2 || group.any { facts.getValue(it).boundsInvalid } || residual.colliding(group).isNotEmpty()) return false
        val f = group.map(facts::getValue)
        if (f.any { it.track == null } || f.mapNotNull { it.discs }.distinct().size > 1) return false
        for (disc in group.groupBy(evidence::disc).values) {
            val totals = disc.mapNotNull { facts.getValue(it).total }.distinct()
            if (totals.size > 1) return false
            if (totals.isNotEmpty() && disc.any { facts.getValue(it).track!! > totals.single() }) return false
        }
        val declaredTotal = f.all { it.total != null }
        val declaredDiscs = f.all { it.discs != null && evidence.disc(it.index) != null && evidence.disc(it.index)!! <= it.discs!! }
        return (declaredTotal || declaredDiscs) && group.map { evidence.disc(it) to facts.getValue(it).track }.distinct().size >= 2
    }

    private fun singleton(
        index: Int,
        input: List<Int>,
        releases: AlbumReleaseEvidence,
    ): Boolean {
        val f = facts.getValue(index)
        if (f.placeholderAlbum) return false
        val independent = f.aa.isNotEmpty() || (f.track == 1 && f.total == 1 && !f.boundsInvalid)
        if (!independent) return false
        // Another compatible artist assignment still makes the singleton provisional.
        return input.filter { it != index }.none { other ->
            val x = facts.getValue(other)
            !releases.blocks(index, other) && ((f.aa.isNotEmpty() && f.aa == x.aa) || (f.artist.isNotEmpty() && f.artist == x.artist))
        }
    }
}
