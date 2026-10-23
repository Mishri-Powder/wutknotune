// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

/** Position-independent read contract, also implementable over Android file descriptors. */
interface MediaInput {
    val id: String
    val length: Long

    fun read(
        offset: Long,
        count: Int,
    ): ByteArray
}

class ByteArrayInput(
    private val data: ByteArray,
    override val id: String = "memory",
) : MediaInput {
    override val length: Long get() = data.size.toLong()

    override fun read(
        offset: Long,
        count: Int,
    ): ByteArray {
        checkRange(offset, count, length)
        return data.copyOfRange(offset.toInt(), offset.toInt() + count)
    }
}

class FileInput(
    file: File,
) : MediaInput,
    Closeable {
    private val handle = RandomAccessFile(file, "r")
    override val id = file.path
    override val length = handle.length()

    @Synchronized override fun read(
        offset: Long,
        count: Int,
    ): ByteArray {
        checkRange(offset, count, length)
        return ByteArray(count).also {
            handle.seek(offset)
            handle.readFully(it)
        }
    }

    override fun close() = handle.close()
}

/** Resolution is explicit; core never follows paths, URLs or sidecars itself. */
fun interface FileResolver {
    fun resolve(
        cueSource: String,
        reference: FileReference,
    ): MediaInput?
}

internal fun checkRange(
    offset: Long,
    count: Int,
    length: Long,
) {
    if (offset < 0 || count < 0 || offset > length || count.toLong() > length - offset) {
        throw EOFException("Requested byte range is outside source")
    }
}

internal class ParseFailure(
    val code: String,
    message: String,
) : IllegalArgumentException(message)

class ParseContext internal constructor(
    val input: MediaInput,
    val options: ParseOptions,
) {
    val diagnostics = mutableListOf<Diagnostic>()
    private var consumed = 0L
    private var elements = 0

    fun check() {
        if (options.cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
        if (++elements > options.limits.elements) fail("LIMIT_ELEMENTS", "Too many parser elements")
    }

    fun read(
        offset: Long,
        count: Int,
    ): ByteArray {
        check()
        if (count < 0 ||
            count > options.limits.metadataBytes ||
            count.toLong() > options.limits.totalReadBytes - consumed
        ) {
            fail("LIMIT_BYTES", "Metadata read budget exceeded")
        }
        consumed += count
        return input.read(offset, count).also {
            if (it.size != count) throw EOFException("MediaInput returned a short read")
        }
    }

    fun diagnostic(
        code: String,
        message: String,
        location: String? = null,
        severity: String = "warning",
    ) {
        if (diagnostics.size >= options.limits.diagnostics) {
            diagnostics[diagnostics.lastIndex] =
                Diagnostic(
                    "LIMIT_DIAGNOSTICS",
                    "error",
                    input.id,
                    message = "Further diagnostics omitted after the configured limit",
                )
            return
        }
        diagnostics += Diagnostic(code, severity, input.id, location, message)
    }

    fun oddity(
        code: String,
        message: String,
        location: String? = null,
    ) {
        diagnostic(code, message, location, if (options.mode == ParseMode.STRICT) "error" else "warning")
    }

    fun fail(
        code: String,
        message: String,
    ): Nothing = throw ParseFailure(code, message)

    fun boundedCount(
        count: Long,
        max: Int = options.limits.fields,
    ): Int {
        if (count !in 0..max.toLong()) fail("LIMIT_COUNT", "Invalid or excessive count: $count")
        return count.toInt()
    }
}

internal class Cursor(
    val data: ByteArray,
    val little: Boolean = false,
    start: Int = 0,
    val limit: Int = data.size,
) {
    init {
        require(start in 0..limit && limit <= data.size)
    }

    var position = start
    val remaining get() = limit - position

    fun bytes(n: Int): ByteArray {
        require(n >= 0 && n <= remaining) { "Truncated metadata" }
        return data.copyOfRange(position, position + n).also { position += n }
    }

    fun view(n: Int): Cursor {
        require(n >= 0 && n <= remaining) { "Truncated metadata" }
        return Cursor(data, little, position, position + n).also { position += n }
    }

    fun skip(n: Int) {
        require(n >= 0 && n <= remaining)
        position += n
    }

    fun u8() = bytes(1)[0].toInt() and 255

    fun u16(): Int {
        val a = u8()
        val b = u8()
        return if (little) a or (b shl 8) else (a shl 8) or b
    }

    fun u32(): Long {
        var v = 0L
        repeat(4) {
            val b = u8().toLong()
            v = if (little) v or (b shl (8 * it)) else (v shl 8) or b
        }
        return v
    }

    fun i64(): Long = ByteBuffer.wrap(bytes(8)).order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN).long

    fun syncsafe(): Int {
        var n = 0
        repeat(4) {
            val b = u8()
            require(b < 128)
            n = (n shl 7) or b
        }
        return n
    }

    fun latin(n: Int) = bytes(n).toString(Charsets.ISO_8859_1)

    fun zero(): String {
        val end = data.indexOfFrom(0, position)
        require(end >= 0 && end < limit) { "Missing string terminator" }
        return latin(end - position).also { skip(1) }
    }
}

internal fun ByteArray.indexOfFrom(
    value: Byte,
    start: Int,
): Int {
    for (i in start until size) if (this[i] == value) return i
    return -1
}
