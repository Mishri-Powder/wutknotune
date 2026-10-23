// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

internal object Vorbis {
    fun comments(
        bytes: ByteArray,
        ctx: ParseContext,
        id: String,
    ): List<Source> {
        val c = Cursor(bytes, true)
        val vendor = decodeText(c.bytes(ctx.boundedCount(c.u32(), ctx.options.limits.textBytes)), ctx)
        val count = ctx.boundedCount(c.u32())
        val tags = mutableListOf<Tag>()
        val artwork = mutableListOf<Artwork>()
        repeat(count) { index ->
            ctx.check()
            val raw = decodeText(c.bytes(ctx.boundedCount(c.u32(), ctx.options.limits.metadataBytes)), ctx)
            val separator = raw.indexOf('=')
            if (separator < 1) {
                ctx.diagnostic("VORBIS_FIELD_INVALID", "Missing field name or equals sign", "$id/$index")
                return@repeat
            }
            val key = raw.substring(0, separator)
            val value = raw.substring(separator + 1)
            if (key.any { it.code !in 0x20..0x7d || it == '=' }) {
                ctx.diagnostic("VORBIS_KEY_INVALID", "Invalid comment field name", "$id/$index", "error")
            }
            if (key.equals("METADATA_BLOCK_PICTURE", true)) {
                try {
                    val binary =
                        java.util.Base64
                            .getDecoder()
                            .decode(value)
                    artwork += picture(binary, ctx, "$id/picture/$index")
                } catch (e: IllegalArgumentException) {
                    ctx.diagnostic("PICTURE_INVALID", e.message ?: "Invalid picture", "$id/$index")
                }
            } else {
                tags += Tag(key, value, location = "$id/$index")
            }
        }
        val sources =
            mutableListOf(
                Source(
                    "vorbis",
                    id,
                    ctx.input.id,
                    tags,
                    chapters(tags, ctx),
                    artwork = artwork,
                    gains = Metadata.gains(tags, ctx),
                    attributes = mapOf("vendor" to vendor),
                ),
            )
        tags.filter { it.key.equals("CUESHEET", true) }.forEachIndexed { i, t ->
            sources += CueReader().parse(t.value, ctx, "$id/cue/$i", ctx.input.id)
        }
        return sources
    }

    private fun chapters(
        tags: List<Tag>,
        ctx: ParseContext,
    ): List<Entry> {
        val entries = mutableListOf<Entry>()
        tags.forEach { tag ->
            val m = Regex("CHAPTER([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(tag.key) ?: return@forEach
            try {
                val parts = tag.value.split(':')
                require(parts.size == 3)
                val hours = parts[0].toLong()
                val minutes = parts[1].toInt()
                val second = java.math.BigDecimal(parts[2])
                require(hours >= 0 && minutes in 0..59 && second >= java.math.BigDecimal.ZERO && second < java.math.BigDecimal(60))
                require(second.scale() in 0..9)
                val scale =
                    java.math.BigInteger.TEN
                        .pow(second.scale())
                        .longValueExact()
                val whole = Math.addExact(Math.multiplyExact(hours, 3600), minutes * 60L)
                val ticks = Math.addExact(Math.multiplyExact(whole, scale), second.movePointRight(second.scale()).longValueExact())
                val chapterTags =
                    tags.filter { it.key.equals(tag.key + "NAME", true) }.map { it.copy(key = "TITLE") } +
                        tags.filter { it.key.equals(tag.key + "URL", true) }.map { it.copy(key = "URL") }
                entries +=
                    Entry(
                        tag.key,
                        number = m.groupValues[1].toInt(),
                        start = Position(ctx.input.id, MediaTime(ticks, scale)),
                        tags = chapterTags,
                    )
            } catch (
                e: IllegalArgumentException,
            ) {
                ctx.diagnostic("CHAPTER_TIME_INVALID", "Invalid chapter time", tag.location, "error")
            } catch (
                e: ArithmeticException,
            ) {
                ctx.diagnostic("CHAPTER_TIME_INVALID", "Chapter time overflow", tag.location, "error")
            }
        }
        ctx.boundedCount(entries.size.toLong(), ctx.options.limits.entries)
        return entries
    }

    fun picture(
        bytes: ByteArray,
        ctx: ParseContext,
        id: String,
        loader: ((Int, Int) -> ByteArray)? = null,
    ): Artwork {
        val c = Cursor(bytes)
        val type = ctx.boundedCount(c.u32(), Int.MAX_VALUE)
        val mime = c.latin(ctx.boundedCount(c.u32(), ctx.options.limits.textBytes))
        val description = decodeText(c.bytes(ctx.boundedCount(c.u32(), ctx.options.limits.textBytes)), ctx)
        repeat(4) { c.u32() }
        val count = ctx.boundedCount(c.u32(), ctx.options.limits.metadataBytes)
        val start = c.position
        require(count <= c.remaining)
        return if (mime == "-->") {
            Artwork(id, mime, type, description, externalUrl = decodeText(c.bytes(count), ctx))
        } else {
            Artwork(id, mime, type, description, count.toLong(), loader = {
                ctx.check()
                loader?.invoke(start, count)
                    ?: bytes.copyOfRange(start, start + count)
            })
        }
    }
}
