package link.roadio.riff.indexer.storage.gnre

import com.google.flatbuffers.kotlin.ReadBuffer
import com.google.flatbuffers.kotlin.ReadWriteBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ReadOnlyBufferException

/** FlatBuffers Kotlin adapter over a mapped read-only byte range. */
class ReadOnlyByteBuffer(
    source: ByteBuffer,
) : ReadWriteBuffer {
    private val buffer = source.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)

    override val limit: Int = 0
    override val capacity: Int get() = buffer.limit()
    override val writeLimit: Int = 0
    override var writePosition: Int
        get() = 0
        set(value) = readOnly()

    override fun findFirst(
        value: Byte,
        start: Int,
        end: Int,
    ): Int {
        val boundedStart = start.coerceAtLeast(0)
        val boundedEnd = end.coerceIn(boundedStart, capacity)
        return (boundedStart until boundedEnd).firstOrNull { buffer.get(it) == value } ?: -1
    }

    override fun getBoolean(index: Int): Boolean = get(index) != 0.toByte()

    override fun get(index: Int): Byte = buffer.get(index)

    override fun getUByte(index: Int): UByte = get(index).toUByte()

    override fun getShort(index: Int): Short = buffer.getShort(index)

    override fun getUShort(index: Int): UShort = getShort(index).toUShort()

    override fun getInt(index: Int): Int = buffer.getInt(index)

    override fun getUInt(index: Int): UInt = getInt(index).toUInt()

    override fun getLong(index: Int): Long = buffer.getLong(index)

    override fun getULong(index: Int): ULong = getLong(index).toULong()

    override fun getFloat(index: Int): Float = buffer.getFloat(index)

    override fun getDouble(index: Int): Double = buffer.getDouble(index)

    override fun getString(
        start: Int,
        size: Int,
    ): String {
        val bytes = ByteArray(size)
        getBytes(bytes, start, size)
        return bytes.decodeToString()
    }

    override fun getBytes(
        array: ByteArray,
        start: Int,
        length: Int,
    ) {
        require(length <= array.size) { "Destination is smaller than requested byte range" }
        requireRange(start, length)
        buffer.duplicate().apply { position(start) }.get(array, 0, length)
    }

    override fun data(): ByteArray = throw UnsupportedOperationException("Mapped GNRE cannot be materialized as one heap array")

    override fun slice(
        start: Int,
        size: Int,
    ): ReadBuffer = sliceBuffer(start, size)

    override fun writeSlice(
        offset: Int,
        size: Int,
    ): ReadWriteBuffer = sliceBuffer(offset, size)

    private fun sliceBuffer(
        start: Int,
        size: Int,
    ): ReadOnlyByteBuffer {
        requireRange(start, size)
        val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        duplicate.position(start)
        duplicate.limit(start + size)
        return ReadOnlyByteBuffer(duplicate.slice().order(ByteOrder.LITTLE_ENDIAN))
    }

    private fun requireRange(
        start: Int,
        length: Int,
    ) {
        require(start >= 0 && length >= 0 && start <= capacity - length) {
            "Byte range is outside mapped GNRE"
        }
    }

    private fun readOnly(): Nothing = throw ReadOnlyBufferException()

    override fun clear() = readOnly()

    override fun requestCapacity(
        capacity: Int,
        copyAtEnd: Boolean,
    ): Int = readOnly()

    override fun put(value: Boolean) = readOnly()

    override fun put(
        value: ByteArray,
        start: Int,
        length: Int,
    ) = readOnly()

    override fun put(
        value: ReadBuffer,
        start: Int,
        length: Int,
    ) = readOnly()

    override fun put(value: Byte) = readOnly()

    override fun put(value: UByte) = readOnly()

    override fun put(value: Short) = readOnly()

    override fun put(value: UShort) = readOnly()

    override fun put(value: Int) = readOnly()

    override fun put(value: UInt) = readOnly()

    override fun put(value: Long) = readOnly()

    override fun put(value: ULong) = readOnly()

    override fun put(value: Float) = readOnly()

    override fun put(value: Double) = readOnly()

    override fun put(
        value: CharSequence,
        encodedLength: Int,
    ): Int = readOnly()

    override fun set(
        dstIndex: Int,
        src: ByteArray,
        srcStart: Int,
        srcLength: Int,
    ) = readOnly()

    override fun set(
        dstIndex: Int,
        src: ReadBuffer,
        srcStart: Int,
        srcLength: Int,
    ) = readOnly()

    override fun set(
        index: Int,
        value: Boolean,
    ) = readOnly()

    override fun set(
        index: Int,
        value: Byte,
    ) = readOnly()

    override fun set(
        index: Int,
        value: UByte,
    ) = readOnly()

    override fun set(
        index: Int,
        value: Short,
    ) = readOnly()

    override fun set(
        index: Int,
        value: UShort,
    ) = readOnly()

    override fun set(
        index: Int,
        value: Int,
    ) = readOnly()

    override fun set(
        index: Int,
        value: UInt,
    ) = readOnly()

    override fun set(
        index: Int,
        value: Long,
    ) = readOnly()

    override fun set(
        index: Int,
        value: ULong,
    ) = readOnly()

    override fun set(
        index: Int,
        value: Float,
    ) = readOnly()

    override fun set(
        index: Int,
        value: Double,
    ) = readOnly()

    override fun fill(
        value: Byte,
        start: Int,
        end: Int,
    ) = readOnly()

    override fun moveWrittenDataToEnd(capacity: Int): Int = readOnly()
}
