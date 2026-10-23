// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import com.dynatrace.hash4j.hashing.Hashing
import link.roadio.cuegrain.MediaInput

/** Scheme versions prevent mock, encoded-audio and future identity algorithms from being mixed. */
interface MediaIdGenerator {
    val scheme: String

    /** Return null when this generator must acquire source bytes for fragment components. */
    fun fragmentComponent(
        contentComponent: Long,
        span: Span,
        low: Boolean,
        cancellation: Cancellation,
    ): Long? = null

    fun high(
        input: MediaInput,
        span: Span?,
        cancellation: Cancellation,
    ): Long

    fun low(
        input: MediaInput,
        span: Span?,
        cancellation: Cancellation,
    ): Long
}

/** Whole-container hashing is intentionally a test identity, not an audio-equivalence claim. */
class MockMediaID(
    private val chunkBytes: Int = 64 * 1024,
) : MediaIdGenerator {
    init {
        require(chunkBytes in 1..16 * 1024 * 1024)
    }

    override val scheme = "cuetree.mock.xxh3-file.v1"

    override fun high(
        input: MediaInput,
        span: Span?,
        cancellation: Cancellation,
    ) = hash(input, span, 0, cancellation)

    override fun low(
        input: MediaInput,
        span: Span?,
        cancellation: Cancellation,
    ) = hash(input, span, 0x43554554524545L, cancellation)

    private fun hash(
        input: MediaInput,
        span: Span?,
        seed: Long,
        cancellation: Cancellation,
    ): Long {
        require(input.length >= 0)
        val stream = Hashing.xxh3_64(seed).hashStream()
        if (span != null) {
            stream
                .putBytes("CUETREE-MOCK-FRAGMENT-V1".toByteArray(Charsets.US_ASCII))
                .putLong(input.length)
                .putLong(span.beginUs ?: 0)
                .putBoolean(span.endUs != null)
                .putLong(span.endUs ?: 0)
        }
        var offset = 0L
        while (offset < input.length) {
            cancellation.check()
            val count = minOf(chunkBytes.toLong(), input.length - offset).toInt()
            val data = input.read(offset, count)
            check(data.size == count) { "Input returned a short read" }
            stream.putBytes(data)
            offset += count
        }
        cancellation.check()
        return stream.asLong
    }
}
