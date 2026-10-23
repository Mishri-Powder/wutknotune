// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

/** Exact-range cache: no read-ahead or full-file copy; caller still owns the underlying input. */
class CachedMediaInput(
    val input: MediaInput,
    private val maximumBytes: Int = 262_144,
) : MediaInput {
    private data class Range(
        val offset: Long,
        val bytes: ByteArray,
    )

    private val entries = java.util.ArrayDeque<Range>()
    private var retainedBytes = 0

    init {
        require(maximumBytes > 0)
    }

    override val id: String get() = input.id
    override val length: Long get() = input.length

    @Synchronized
    override fun read(
        offset: Long,
        count: Int,
    ): ByteArray {
        checkRange(offset, count, length)
        if (count == 0) return byteArrayOf()
        val cached = entries.firstOrNull { offset >= it.offset && offset - it.offset <= it.bytes.size.toLong() - count }
        if (cached != null) {
            entries.remove(cached)
            entries.addLast(cached)
            val start = (offset - cached.offset).toInt()
            return cached.bytes.copyOfRange(start, start + count)
        }
        val data = input.read(offset, count)
        if (data.size != count) throw java.io.EOFException("Short media read")
        if (count <= maximumBytes) {
            while (retainedBytes > maximumBytes - count) retainedBytes -= entries.removeFirst().bytes.size
            entries.addLast(Range(offset, data.copyOf()))
            retainedBytes += count
        }
        return data
    }
}
