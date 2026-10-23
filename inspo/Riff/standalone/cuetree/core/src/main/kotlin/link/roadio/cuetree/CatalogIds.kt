// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

/** Every catalog uses signed high/low ordering; absent low precedes numeric low, including zero. */
object IdOrder : Comparator<MediaId> {
    override fun compare(
        a: MediaId,
        b: MediaId,
    ): Int =
        a.hi.compareTo(b.hi).takeIf { it != 0 } ?: when {
            a.lo == null -> if (b.lo == null) 0 else -1
            b.lo == null -> 1
            else -> a.lo.compareTo(b.lo)
        }

    fun range(
        size: Int,
        idAt: (Int) -> MediaId,
        high: Long,
    ): IntRange {
        fun bound(upper: Boolean): Int {
            var left = 0
            var right = size
            while (left < right) {
                val mid = left + (right - left) / 2
                val value = idAt(mid).hi
                if (value < high || (upper && value == high)) left = mid + 1 else right = mid
            }
            return left
        }
        return bound(false) until bound(true)
    }

    fun matches(
        stored: MediaId,
        flags: Int,
        requested: MediaId,
    ): Boolean = stored == requested || (stored.hi == requested.hi && requested.lo == null && flags and Flags.SHRT != 0)
}

/** Sorting storage does not reorder tracks or chapter ordinals; all occurrence indexes are remapped. */
internal fun Snapshot.ordered(): Snapshot {
    val order =
        nodes.indices.sortedWith { a, b ->
            val x = nodes[a]
            val y = nodes[b]
            IdOrder.compare(x.id, y.id).takeIf { it != 0 }
                ?: x.kind.compareTo(y.kind).takeIf { it != 0 }
                ?: x.parentId
                    .toString()
                    .compareTo(y.parentId.toString())
                    .takeIf { it != 0 }
                ?: x.source
                    .toString()
                    .compareTo(y.source.toString())
                    .takeIf { it != 0 }
                ?: x.attributes
                    .toSortedMap()
                    .toString()
                    .compareTo(y.attributes.toSortedMap().toString())
        }
    val mapping = IntArray(nodes.size)
    order.forEachIndexed { index, old -> mapping[old] = index }
    return copy(
        nodes = order.map { nodes[it] },
        albums = albums.map { it.copy(sources = it.sources.map { i -> mapping[i] }) }.sortedBy { it.id },
        artists = artists.sortedBy { it.id },
        genres = genres.sortedBy { it.id },
        attributeCatalog = attributeCatalog.sortedWith(compareBy(IdOrder) { it.id }),
    )
}
