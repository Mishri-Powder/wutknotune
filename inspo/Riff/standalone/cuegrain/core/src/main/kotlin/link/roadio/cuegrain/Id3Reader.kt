// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

/** ID3 frame decoding has no dependency on Android or Media3. */
class Id3Reader : FormatReader {
    override val name = "id3"

    override fun matches(
        header: ByteArray,
        hint: String?,
    ) = header.take(3).toByteArray().toString(Charsets.ISO_8859_1) == "ID3" ||
        hint?.lowercase() == "mp3" ||
        (header.size >= 4 && header[0] == 0xff.toByte() && (header[1].toInt() and 0xe6) == 0xe2)

    override fun read(context: ParseContext): List<Source> {
        val ctx = context
        val sources = mutableListOf<Source>()
        var audioOffset = 0L
        if (ctx.input.length >= 10 && ctx.read(0, 3).toString(Charsets.ISO_8859_1) == "ID3") {
            val header = ctx.read(0, 10)
            val size =
                try {
                    Id3MetadataBudget.bodySize(header, maximumTagBytes = ctx.options.limits.metadataBytes, remainingSourceBytes = ctx.input.length)
                } catch (error: IllegalArgumentException) {
                    val code = if (error.message?.contains("exceeds") == true) "LIMIT_BYTES" else "ID3_HEADER_INVALID"
                    ctx.fail(code, error.message ?: "Invalid ID3 header")
                }
            val version = header[3].toInt() and 255
            val flags = header[5].toInt() and 255
            audioOffset = 10L + size + (if (version == 4 && flags and 16 != 0) 10 else 0)
            if (version == 2 && flags and 64 != 0) ctx.fail("ID3_COMPRESSION_UNSUPPORTED", "Compressed ID3v2.2 tag")
            var bytes = ctx.read(10, size)
            if (version < 4 && flags and 128 != 0) bytes = unsync(bytes)
            val body = Cursor(bytes)
            if (version >= 3 && flags and 64 != 0) {
                val length = if (version == 4) body.syncsafe() - 4 else ctx.boundedCount(body.u32(), bytes.size)
                body.skip(length)
            }
            val parsed = frames(body, version, ctx, 0, "id3", version == 4 && flags and 128 != 0)
            val id = "${ctx.input.id}#id3v2"
            val allIds = parsed.entries.map { it.id } + parsed.tables.map { it.id }
            if (allIds.distinct().size !=
                allIds.size
            ) {
                ctx.diagnostic("ID3_DUPLICATE_ELEMENT", "Duplicate CHAP/CTOC element IDs", severity = "error")
            }
            parsed.tables.forEach { t ->
                t.children.filter { it !in allIds }.forEach {
                    ctx.diagnostic("ID3_DANGLING_CTOC", "Unknown CTOC child: $it")
                }
            }
            sources +=
                Source(
                    "id3",
                    id,
                    ctx.input.id,
                    parsed.tags,
                    parsed.entries,
                    parsed.tables,
                    artwork = parsed.artwork,
                    gains = Metadata.gains(parsed.tags, ctx),
                    attributes = mapOf("version" to "2.$version"),
                )
            parsed.tags.filter { it.key.equals("CUESHEET", true) }.forEachIndexed { i, t ->
                sources += CueReader().parse(t.value, ctx, "$id/cue/$i", ctx.input.id)
            }
        }
        if (ctx.input.length - audioOffset >= 4) LameReader.read(ctx, audioOffset)?.let { sources += it }
        if (ctx.input.length >= 128) {
            val tail = ctx.read(ctx.input.length - 128, 128)
            if (tail.copyOfRange(0, 3).toString(Charsets.ISO_8859_1) == "TAG") {
                fun field(
                    start: Int,
                    count: Int,
                ) = tail
                    .copyOfRange(start, start + count)
                    .toString(Charsets.ISO_8859_1)
                    .trimEnd('\u0000', ' ')
                val fields =
                    listOf(
                        Tag("TITLE", field(3, 30)),
                        Tag("ARTIST", field(33, 30)),
                        Tag("ALBUM", field(63, 30)),
                        Tag("DATE", field(93, 4)),
                        Tag("COMMENT", field(97, if (tail[125] == 0.toByte() && tail[126] != 0.toByte()) 28 else 30)),
                    )
                val tags = fields.filter { it.value.isNotEmpty() }.toMutableList()
                if (tail[125] == 0.toByte() && tail[126] != 0.toByte()) tags += Tag("TRACKNUMBER", (tail[126].toInt() and 255).toString())
                tags += Tag("ID3V1_GENRE", (tail[127].toInt() and 255).toString())
                sources += Source("id3v1", "${ctx.input.id}#id3v1", ctx.input.id, tags)
            }
        }
        return sources
    }

    private data class Frames(
        val tags: MutableList<Tag> = mutableListOf(),
        val entries: MutableList<Entry> = mutableListOf(),
        val tables: MutableList<TableOfContents> = mutableListOf(),
        val artwork: MutableList<Artwork> = mutableListOf(),
    )

    private fun frames(
        c: Cursor,
        v: Int,
        ctx: ParseContext,
        depth: Int,
        path: String,
        tagUnsync: Boolean = false,
    ): Frames {
        require(depth <= ctx.options.limits.nesting) { "ID3 nesting limit" }
        val result = Frames()
        var count = 0
        while (c.remaining >= if (v == 2) 6 else 10) {
            ctx.check()
            count++
            ctx.boundedCount(count.toLong())
            val location = "$path/${c.position}"
            val id = c.latin(if (v == 2) 3 else 4)
            if (id.first() == '\u0000') {
                if ((c.position - id.length until c.limit).any { c.data[it] != 0.toByte() }) {
                    ctx.oddity("ID3_PADDING_NONZERO", "Ignored nonzero bytes after the first padding marker", location)
                }
                break
            }
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) {
                ctx.diagnostic("ID3_FRAME_ID_INVALID", "Stopped at invalid ID3 frame ID", location, "error")
                break
            }
            val size =
                if (v == 2) {
                    (c.u8() shl 16) or (c.u8() shl 8) or c.u8()
                } else if (v == 4) {
                    c.syncsafe()
                } else {
                    ctx.boundedCount(c.u32(), ctx.options.limits.metadataBytes)
                }
            val flags = if (v == 2) 0 else c.u16()
            val frame = c.view(size)
            if ((v == 3 && flags and 0xc0 != 0) || (v == 4 && flags and 0x0c != 0)) {
                ctx.diagnostic("ID3_FRAME_UNSUPPORTED", "Compressed/encrypted frame $id", location)
                continue
            }
            val p = if (v == 4 && (tagUnsync || flags and 2 != 0)) Cursor(unsync(frame.bytes(frame.remaining))) else frame
            val payloadStart = p.position
            if ((v == 3 && flags and 0x20 != 0) || (v == 4 && flags and 0x40 != 0)) p.skip(1)
            if (v == 4 && flags and 1 != 0) p.syncsafe()
            try {
                when {
                    id == "CHAP" -> {
                        val element = p.zero()
                        val start = p.u32()
                        val end = p.u32()
                        val startOffset = p.u32()
                        val endOffset = p.u32()
                        val nested = frames(p.view(p.remaining), v, ctx, depth + 1, location)
                        val attrs = linkedMapOf<String, String>()
                        if (startOffset != 0xffffffffL) attrs["startOffsetBytes"] = startOffset.toString()
                        if (endOffset != 0xffffffffL) attrs["endOffsetBytes"] = endOffset.toString()
                        if (end != 0xffffffffL &&
                            end <= start
                        ) {
                            ctx.diagnostic("ID3_CHAPTER_RANGE", "Chapter end is not after start", location, "error")
                        }
                        result.entries +=
                            Entry(
                                element,
                                start = Position(ctx.input.id, MediaTime(start, 1000)),
                                end = end.takeIf { it != 0xffffffffL }?.let { Position(ctx.input.id, MediaTime(it, 1000)) },
                                endOrigin = if (end != 0xffffffffL) "explicit" else null,
                                tags = nested.tags,
                                attributes = attrs,
                                artwork = nested.artwork,
                            )
                    }

                    id == "CTOC" -> {
                        val element = p.zero()
                        val tableFlags = p.u8()
                        val childCount = p.u8()
                        val children = List(childCount) { p.zero() }
                        val nested = frames(p.view(p.remaining), v, ctx, depth + 1, location)
                        result.tables += TableOfContents(element, tableFlags and 1 != 0, tableFlags and 2 != 0, children, nested.tags)
                    }

                    id == "TXXX" || id == "TXX" -> {
                        val enc = p.u8()
                        val description = terminated(p, enc, ctx)
                        textValues(p.bytes(p.remaining), enc, ctx, v == 4, location).forEach {
                            result.tags += Tag(description, it, location = location)
                        }
                    }

                    id.startsWith("T") -> {
                        val enc = p.u8()
                        textValues(p.bytes(p.remaining), enc, ctx, true, location).forEach { result.tags += Tag(id, it, location = location) }
                    }

                    id == "COMM" || id == "COM" || id == "USLT" -> {
                        val enc = p.u8()
                        val language = p.latin(3)
                        val desc = terminated(p, enc, ctx)
                        result.tags +=
                            Tag(
                                desc.ifEmpty { if (id == "USLT") "LYRICS" else "COMMENT" },
                                text(p.bytes(p.remaining), enc, ctx),
                                location = "$location/$language",
                            )
                    }

                    id == "----" -> {
                        val enc = p.u8()
                        val domain = p.zero()
                        val description = terminated(p, enc, ctx)
                        result.tags += Tag(description, text(p.bytes(p.remaining), enc, ctx), location = "$domain:$description")
                    }

                    id.startsWith("W") -> {
                        val desc = if (id == "WXXX" || id == "WXX") terminated(p, p.u8(), ctx) else id
                        result.tags += Tag(desc, p.latin(p.remaining).trimEnd('\u0000'), location = location, formatKey = id)
                    }

                    id == "APIC" || id == "PIC" -> {
                        val enc = p.u8()
                        val mime =
                            if (id == "PIC") {
                                when (val format = p.latin(3)) {
                                    "PNG" -> "image/png"
                                    "JPG" -> "image/jpeg"
                                    else -> format
                                }
                            } else {
                                p.zero()
                            }
                        val type = p.u8()
                        val desc = terminated(p, enc, ctx)
                        val pictureStart = p.position
                        if (mime == "-->") {
                            result.artwork += Artwork(location, mime, type, desc, externalUrl = p.latin(p.remaining), frameIndex = count - 1)
                        } else {
                            result.artwork +=
                                Artwork(
                                    location,
                                    mime,
                                    type,
                                    desc,
                                    p.remaining.toLong(),
                                    frameIndex = count - 1,
                                    loader = {
                                        ctx.check()
                                        p.data.copyOfRange(pictureStart, p.limit)
                                    },
                                )
                        }
                    }

                    else -> {
                        if (p.limit - payloadStart <= ctx.options.limits.textBytes) {
                            result.tags +=
                                Tag(
                                    "BINARY_BASE64:$id",
                                    java.util.Base64
                                        .getEncoder()
                                        .encodeToString(p.data.copyOfRange(payloadStart, p.limit)),
                                    location = location,
                                )
                        } else {
                            ctx.diagnostic("ID3_UNKNOWN_FRAME_LARGE", "Unknown frame $id exceeds preserved-field limit", location)
                        }
                    }
                }
            } catch (e: IllegalArgumentException) {
                if (e is ParseFailure) throw e
                ctx.diagnostic("ID3_FRAME_INVALID", "$id: ${e.message}", location, "error")
            }
            ctx.boundedCount(result.entries.size.toLong(), ctx.options.limits.entries)
            ctx.boundedCount(result.tags.size.toLong())
        }
        return result
    }

    private fun textValues(
        bytes: ByteArray,
        encoding: Int,
        ctx: ParseContext,
        multiple: Boolean,
        location: String,
    ): List<String> {
        if (bytes.size > ctx.options.limits.textBytes) ctx.fail("LIMIT_TEXT", "ID3 text field exceeds limit")
        val width = if (encoding == 1 || encoding == 2) 2 else 1
        var start = 0
        var order = "UTF-16BE"
        val values = mutableListOf<String>()
        while (start < bytes.size) {
            ctx.check()
            var end = start
            while (end + width <= bytes.size && !(bytes[end] == 0.toByte() && (width == 1 || bytes[end + 1] == 0.toByte()))) end += width
            if (end + width > bytes.size) end = bytes.size
            val part = bytes.copyOfRange(start, end)
            if (part.isNotEmpty()) {
                val bom = part.size >= 2 && ((part[0] == 0xff.toByte() && part[1] == 0xfe.toByte()) || (part[0] == 0xfe.toByte() && part[1] == 0xff.toByte()))
                if (encoding == 1 && bom) order = if (part[0] == 0xff.toByte()) "UTF-16LE" else "UTF-16BE"
                values += if (encoding == 1 && !bom) decodeText(part, ctx, order) else text(part, encoding, ctx)
                ctx.boundedCount(values.size.toLong())
            }
            start = minOf(bytes.size, end + width)
            if (!multiple) {
                if (bytes.drop(start).any { it != 0.toByte() }) ctx.oddity("ID3_TEXT_TRAILING_DATA", "Ignored bytes after the terminated ID3v2.3 text value", location)
                break
            }
        }
        return values.ifEmpty { listOf("") }
    }

    private fun text(
        bytes: ByteArray,
        encoding: Int,
        ctx: ParseContext,
    ): String =
        decodeText(
            bytes,
            ctx,
            when (encoding) {
                0 -> "ISO-8859-1"
                1 -> "UTF-16"
                2 -> "UTF-16BE"
                3 -> "UTF-8"
                else -> throw IllegalArgumentException("Invalid ID3 text encoding")
            },
        )

    private fun terminated(
        p: Cursor,
        encoding: Int,
        ctx: ParseContext,
    ): String {
        val width = if (encoding == 1 || encoding == 2) 2 else 1
        var end = p.position
        while (end + width <= p.limit) {
            if (p.data[end] == 0.toByte() && (width == 1 || p.data[end + 1] == 0.toByte())) {
                val out = text(p.bytes(end - p.position), encoding, ctx)
                p.skip(width)
                return out
            }
            end += width
        }
        throw IllegalArgumentException("Unterminated ID3 string")
    }

    private fun unsync(input: ByteArray): ByteArray {
        val result = ByteArray(input.size)
        var n = 0
        var i = 0
        while (i < input.size) {
            val b = input[i++]
            result[n++] = b
            if (b == 0xff.toByte() && i < input.size && input[i] == 0.toByte()) i++
        }
        return result.copyOf(n)
    }
}
