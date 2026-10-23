package link.roadio.riff.storage.hash

import java.nio.ByteBuffer

/** Creates independent incremental XXH3-64 streams. */
fun interface Xxh3Factory {
    fun create(): Xxh3Stream
}

/** Incremental, constant-memory XXH3-64 state. */
interface Xxh3Stream : AutoCloseable {
    fun update(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    )

    fun digest(): Long
}

/** Pinned upstream xxHash implementation accessed through the Riff JNI boundary. */
object NativeXxh3Factory : Xxh3Factory {
    override fun create(): Xxh3Stream = NativeXxh3Stream()
}

private class NativeXxh3Stream : Xxh3Stream {
    private var handle = NativeXxh3.nativeCreate()

    override fun update(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        check(handle != 0L) { "XXH3 stream is closed" }
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
            "Invalid byte range: offset=$offset, length=$length, size=${bytes.size}"
        }
        NativeXxh3.nativeUpdate(handle, bytes, offset, length)
    }

    override fun digest(): Long {
        check(handle != 0L) { "XXH3 stream is closed" }
        return NativeXxh3.nativeDigest(handle)
    }

    override fun close() {
        val current = handle
        if (current != 0L) {
            handle = 0L
            NativeXxh3.nativeDestroy(current)
        }
    }
}

internal object NativeXxh3 {
    init {
        System.loadLibrary("riff_xxh3")
    }

    external fun nativeCreate(): Long

    external fun nativeUpdate(
        handle: Long,
        bytes: ByteArray,
        offset: Int,
        length: Int,
    )

    external fun nativeDigest(handle: Long): Long

    external fun nativeHash(bytes: ByteArray): Long

    external fun nativeDestroy(handle: Long)
}

fun nativeXxh3(bytes: ByteArray): Long = NativeXxh3.nativeHash(bytes)

/** Hashes a read-only view in bounded chunks without materializing the complete payload. */
fun nativeXxh3(buffer: ByteBuffer): Long {
    val source = buffer.asReadOnlyBuffer()
    val scratch = ByteArray(minOf(64 * 1024, source.remaining().coerceAtLeast(1)))
    NativeXxh3Factory.create().use { stream ->
        while (source.hasRemaining()) {
            val count = minOf(source.remaining(), scratch.size)
            source.get(scratch, 0, count)
            stream.update(scratch, 0, count)
        }
        return stream.digest()
    }
}

/** XXH canonical form is the unsigned hash rendered as eight big-endian bytes. */
fun Long.toXxh3CanonicalBytes(): ByteArray =
    ByteBuffer
        .allocate(Long.SIZE_BYTES)
        .putLong(this)
        .array()

fun ByteArray.readXxh3CanonicalLong(): Long {
    require(size == Long.SIZE_BYTES) { "XXH3 sidecar must be exactly eight bytes" }
    return ByteBuffer.wrap(this).long
}
