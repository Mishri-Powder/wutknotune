package link.roadio.riff.storage

import link.roadio.riff.storage.hash.Xxh3Factory
import link.roadio.riff.storage.hash.Xxh3Stream

internal object TestHashing : Xxh3Factory {
    override fun create(): Xxh3Stream = Stream()

    fun hash(bytes: ByteArray): Long =
        create().use { stream ->
            stream.update(bytes)
            stream.digest()
        }

    private class Stream : Xxh3Stream {
        private var hash = FNV_OFFSET

        override fun update(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            repeat(length) { index ->
                hash = hash xor (bytes[offset + index].toLong() and 0xffL)
                hash *= FNV_PRIME
            }
        }

        override fun digest(): Long = hash

        override fun close() = Unit
    }

    private const val FNV_OFFSET = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
}
