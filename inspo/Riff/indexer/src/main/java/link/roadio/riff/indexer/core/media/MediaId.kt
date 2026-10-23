package link.roadio.riff.indexer.core.media

/** Public MDIA-ID-V3.2 key; ordering is the canonical signed FlatBuffer order. */
data class MediaId(
    val high: Long,
    val low: Long = 0L,
    val lowPresent: Boolean = true,
) : Comparable<MediaId> {
    init {
        require(lowPresent || low == 0L) { "Absent Media.ID low word must have zero storage" }
    }

    override fun compareTo(other: MediaId): Int {
        val highOrder = high.compareTo(other.high)
        if (highOrder != 0) return highOrder
        val presenceOrder = lowPresent.compareTo(other.lowPresent)
        return if (presenceOrder != 0) presenceOrder else low.compareTo(other.low)
    }

    fun hex(): String =
        high.toULong().toString(16).padStart(16, '0') +
            if (lowPresent) low.toULong().toString(16).padStart(16, '0') else ""
}
