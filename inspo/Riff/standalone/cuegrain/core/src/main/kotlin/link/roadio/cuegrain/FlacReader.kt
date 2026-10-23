// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

class FlacReader : FormatReader {
    override val name = "flac"

    override fun matches(
        header: ByteArray,
        hint: String?,
    ) = header.take(4).toByteArray().toString(Charsets.US_ASCII) == "fLaC"

    override fun read(context: ParseContext): List<Source> {
        val ctx = context
        val sources = mutableListOf<Source>()
        var offset = 4L
        var sampleRate = 0L
        var totalSamples = 0L
        var last = false
        var ordinal = 0
        while (!last) {
            ctx.check()
            val h = Cursor(ctx.read(offset, 4))
            val flags = h.u8()
            last = flags and 128 != 0
            val type = flags and 127
            val size = (h.u8() shl 16) or (h.u8() shl 8) or h.u8()
            offset += 4
            checkRange(offset, size, ctx.input.length)
            when (type) {
                0 -> {
                    require(size == 34)
                    val data = ctx.read(offset, size)
                    val c = Cursor(data)
                    c.skip(10)
                    val packed = c.i64()
                    sampleRate = (packed ushr 44) and 0xfffff
                    totalSamples = packed and 0xfffffffffL
                    require(sampleRate > 0)
                    sources +=
                        Source(
                            "flac",
                            "${ctx.input.id}#streaminfo",
                            ctx.input.id,
                            attributes =
                                mapOf(
                                    "sampleRate" to sampleRate.toString(),
                                    "totalSamples" to totalSamples.toString(),
                                    "channels" to (((packed ushr 41) and 7) + 1).toString(),
                                    "bitsPerSample" to (((packed ushr 36) and 31) + 1).toString(),
                                ),
                        )
                }

                4 -> {
                    sources += Vorbis.comments(ctx.read(offset, size), ctx, "${ctx.input.id}#vorbis/$ordinal")
                }

                5 -> {
                    sources += cuesheet(ctx.read(offset, size), ctx, sampleRate, totalSamples, ordinal)
                }

                6 -> {
                    val base = offset
                    val picture =
                        Vorbis.picture(
                            ctx.read(offset, size),
                            ctx,
                            "flac/picture/$ordinal",
                        ) { start, count -> ctx.input.read(base + start, count) }
                    sources += Source("flac.picture", "${ctx.input.id}#picture/$ordinal", ctx.input.id, artwork = listOf(picture))
                }
            }
            offset += size
            ordinal++
        }
        return sources
    }

    private fun cuesheet(
        bytes: ByteArray,
        ctx: ParseContext,
        rate: Long,
        total: Long,
        ordinal: Int,
    ): Source {
        require(rate > 0) { "CUESHEET requires STREAMINFO" }
        val c = Cursor(bytes)
        val catalog = c.latin(128).trimEnd('\u0000')
        val leadIn = c.i64()
        require(leadIn >= 0)
        val isCd = c.u8() and 128 != 0
        c.skip(258)
        val count = c.u8()
        require(count >= 1 && (!isCd || count <= 100))
        val entries = mutableListOf<Entry>()
        var leadOut: Long? = null
        var previousOffset = -1L
        val numbers = mutableSetOf<Int>()
        repeat(count) { n ->
            ctx.check()
            val trackOffset = c.i64()
            require(trackOffset >= 0)
            val number = c.u8()
            val isrc = c.latin(12).trimEnd('\u0000')
            require(number != 0 && numbers.add(number)) { "Invalid/duplicate CUESHEET track number" }
            require(trackOffset >= previousOffset) { "Decreasing CUESHEET track offsets" }
            previousOffset = trackOffset
            val flags = c.u8()
            c.skip(13)
            val indexCount = c.u8()
            val indexes =
                List(indexCount) {
                    val relative = c.i64()
                    require(relative >= 0)
                    val idx = c.u8()
                    c.skip(3)
                    CueIndex(idx, Position(ctx.input.id, MediaTime(Math.addExact(trackOffset, relative), rate)))
                }
            if (n == count - 1) {
                require(indexCount == 0 && number == if (isCd) 170 else 255) { "Invalid CUESHEET lead-out" }
                leadOut = trackOffset
            } else {
                require(indexCount > 0 && (!isCd || number in 1..99))
                require(
                    indexes.first().number in 0..1 &&
                        indexes.zipWithNext().all {
                            it.second.number == it.first.number + 1 && it.second.position.time >= it.first.position.time
                        },
                ) { "Invalid CUESHEET index ordering" }
                if (indexes.none { it.number == 1 }) ctx.diagnostic("FLAC_INDEX01_MISSING", "Track has no INDEX 01", severity = "error")
                entries +=
                    Entry(
                        "flac/$ordinal/track/$number",
                        number,
                        if (flags and 128 != 0) "data" else "track",
                        start = indexes.firstOrNull { it.number == 1 }?.position,
                        tags = if (isrc.isNotEmpty()) listOf(Tag("ISRC", isrc)) else emptyList(),
                        indexes = indexes,
                        attributes = mapOf("preEmphasis" to (flags and 64 != 0).toString(), "trackOffsetSamples" to trackOffset.toString()),
                    )
            }
        }
        require(c.remaining == 0)
        val completed =
            entries.mapIndexed { i, e ->
                val boundary =
                    entries
                        .getOrNull(i + 1)
                        ?.indexes
                        ?.firstOrNull()
                        ?.position
                        ?: if (i == entries.lastIndex) leadOut?.let { Position(ctx.input.id, MediaTime(it, rate)) } else null
                if (total > 0 &&
                    e.start
                        ?.time
                        ?.ticks
                        ?.let { it >= total } == true
                ) {
                    ctx.diagnostic("FLAC_CUE_RANGE", "Track is outside stream duration", severity = "error")
                }
                e.copy(
                    end = boundary,
                    endOrigin =
                        if (boundary != null) {
                            if (i == entries.lastIndex) {
                                "lead-out"
                            } else {
                                "next-index"
                            }
                        } else {
                            null
                        },
                )
            }
        return Source(
            "flac.cuesheet",
            "${ctx.input.id}#cuesheet/$ordinal",
            ctx.input.id,
            tags = if (catalog.isNotEmpty()) listOf(Tag("CATALOG", catalog)) else emptyList(),
            entries = completed,
            attributes = mapOf("isCd" to isCd.toString(), "leadInSamples" to leadIn.toString()),
        )
    }
}
