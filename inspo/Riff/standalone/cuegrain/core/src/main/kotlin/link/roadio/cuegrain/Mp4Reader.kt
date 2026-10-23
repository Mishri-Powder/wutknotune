// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import java.math.BigInteger

/** Bounded ISO BMFF metadata reader, adapted from the Riff chapter reader. */
class Mp4Reader : FormatReader {
    override val name = "mp4"

    override fun matches(
        header: ByteArray,
        hint: String?,
    ) = header.size >= 8 &&
        header.copyOfRange(4, 8).toString(Charsets.ISO_8859_1) in setOf("ftyp", "moov", "free", "wide", "mdat")

    override fun read(context: ParseContext): List<Source> = Reader(context).read()

    private class Reader(
        val ctx: ParseContext,
    ) {
        data class Atom(
            val type: String,
            val start: Long,
            val payload: Long,
            val end: Long,
        )

        val out = mutableListOf<Source>()

        fun atoms(
            start: Long,
            end: Long,
        ): List<Atom> {
            val result = mutableListOf<Atom>()
            var pos = start
            while (pos < end) {
                ctx.check()
                require(end - pos >= 8)
                val c = Cursor(ctx.read(pos, 8))
                val small = c.u32()
                val type = c.latin(4)
                val header = if (small == 1L) 16 else 8
                val size =
                    when (small) {
                        0L -> end - pos
                        1L -> Cursor(ctx.read(pos + 8, 8)).i64()
                        else -> small
                    }
                require(size >= header && size <= end - pos) { "Invalid MP4 atom size" }
                result += Atom(type, pos, pos + header, pos + size)
                pos += size
            }
            return result
        }

        fun children(a: Atom) = atoms(a.payload, a.end)

        fun child(
            a: Atom,
            type: String,
        ) = children(a).firstOrNull { it.type == type }

        fun data(a: Atom): Cursor = Cursor(ctx.read(a.payload, ctx.boundedCount(a.end - a.payload, ctx.options.limits.metadataBytes)))

        fun read(): List<Source> {
            val top = atoms(0, ctx.input.length)
            val moov = top.firstOrNull { it.type == "moov" } ?: return emptyList()
            require(moov.end - moov.start <= ctx.options.limits.metadataBytes)
            val moovChildren = children(moov)
            for (meta in moovChildren.filter { it.type == "meta" } +
                moovChildren.filter { it.type == "udta" }.flatMap { children(it).filter { a -> a.type == "meta" } }) {
                optional("MP4_TAGS_INVALID") { ordinary(meta) }
            }
            moovChildren.filter { it.type == "udta" }.forEach { udta ->
                children(udta).filter { it.type == "chpl" }.forEach { a -> optional("MP4_NERO_INVALID") { nero(a) } }
            }
            if (top.any { it.type == "moof" } || moovChildren.any { it.type == "mvex" }) {
                ctx.diagnostic("MP4_FRAGMENTED", "QuickTime chapters in fragmented MP4 are unsupported")
            } else {
                val tracks = moovChildren.filter { it.type == "trak" }
                for (audio in tracks) {
                    optional("MP4_TRACK_INVALID") {
                        if (handler(audio) != "soun") return@optional
                        val chap = child(audio, "tref")?.let { child(it, "chap") } ?: return@optional
                        val c = data(chap)
                        require(c.remaining % 4 == 0)
                        while (c.remaining > 0) {
                            val id = c.u32()
                            val target = tracks.firstOrNull { trackId(it) == id }
                            if (target == null) {
                                ctx.diagnostic("MP4_CHAPTER_TRACK_MISSING", "Unknown chapter track $id")
                            } else {
                                optional("MP4_QUICKTIME_INVALID") { quickTime(target, moovChildren, id, trackId(audio)) }
                            }
                        }
                    }
                }
            }
            return out
        }

        fun nero(a: Atom) {
            val c = data(a)
            val version = c.u8()
            require(version in 0..1)
            c.skip(3)
            if (version == 1) c.skip(4)
            val count = c.u8()
            val entries =
                List(count) { i ->
                    val ticks = c.i64()
                    require(ticks >= 0)
                    val title = chapterText(c.bytes(c.u8()))
                    Entry("nero/$i", start = Position(ctx.input.id, MediaTime(ticks, 10000000)), tags = listOf(Tag("TITLE", title)))
                }
            require(c.remaining == 0)
            out += Source("mp4.nero", "${ctx.input.id}#nero/${a.start}", ctx.input.id, entries = entries)
        }

        fun quickTime(
            track: Atom,
            movie: List<Atom>,
            id: Long,
            audioId: Long,
        ) {
            require(handler(track) in setOf("text", "sbtl"))
            val mdia = requireNotNull(child(track, "mdia"))
            val scale = timeScale(data(requireNotNull(child(mdia, "mdhd"))))
            val table = children(requireNotNull(child(requireNotNull(child(mdia, "minf")), "stbl")))
            val sizeData = data(requireNotNull(table.firstOrNull { it.type == "stsz" }))
            sizeData.skip(4)
            val fixed = sizeData.u32()
            val count = ctx.boundedCount(sizeData.u32(), ctx.options.limits.entries)
            val sizes = List(count) { ctx.boundedCount(if (fixed == 0L) sizeData.u32() else fixed, ctx.options.limits.textBytes) }
            val desc = data(requireNotNull(table.firstOrNull { it.type == "stsd" }))
            desc.skip(4)
            require(desc.u32() == 1L)
            val descSize = desc.u32()
            require(descSize >= 8 && descSize - 8 <= desc.remaining)
            require(desc.latin(4) in setOf("text", "tx3g"))
            require(table.none { it.type == "ctts" }) { "Chapter composition offsets unsupported" }
            val times = data(requireNotNull(table.firstOrNull { it.type == "stts" }))
            times.skip(4)
            val runCount = ctx.boundedCount(times.u32(), count)
            val starts = mutableListOf<Pair<Long, Long>>()
            var time = 0L
            repeat(runCount) {
                val n = ctx.boundedCount(times.u32(), count - starts.size)
                val delta = times.u32()
                require(n > 0 && delta > 0)
                repeat(n) {
                    val end = Math.addExact(time, delta)
                    starts += time to end
                    time = end
                }
            }
            require(starts.size == count)
            val offsets = offsets(table, sizes)
            val edit = presentationEdit(track, movie)

            fun presentation(ticks: Long): MediaTime {
                val adjusted = Math.subtractExact(ticks, edit.second)
                require(adjusted >= 0)
                val delay = edit.first
                val gcd = BigInteger.valueOf(scale).gcd(BigInteger.valueOf(delay.timescale)).longValueExact()
                val common = Math.multiplyExact(scale / gcd, delay.timescale)
                val value =
                    BigInteger.valueOf(adjusted).multiply(BigInteger.valueOf(common / scale)) +
                        BigInteger.valueOf(delay.ticks).multiply(BigInteger.valueOf(common / delay.timescale))
                return MediaTime(value.longValueExact(), common)
            }
            val entries =
                sizes.indices.map { i ->
                    val c = Cursor(ctx.read(offsets[i], sizes[i]))
                    val title = chapterText(c.bytes(c.u16()))
                    Entry(
                        "quicktime/$id/$i",
                        start = Position(ctx.input.id, presentation(starts[i].first)),
                        end = Position(ctx.input.id, presentation(starts[i].second)),
                        endOrigin = "explicit",
                        tags = listOf(Tag("TITLE", title)),
                    )
                }
            out +=
                Source(
                    "mp4.quicktime",
                    "${ctx.input.id}#quicktime/$audioId/$id",
                    ctx.input.id,
                    entries = entries,
                    attributes = mapOf("chapterTrackId" to id.toString(), "audioTrackId" to audioId.toString()),
                )
        }

        fun offsets(
            table: List<Atom>,
            sizes: List<Int>,
        ): List<Long> {
            if (sizes.isEmpty()) return emptyList()
            val atom = requireNotNull(table.firstOrNull { it.type == "stco" || it.type == "co64" })
            val chunks = data(atom)
            chunks.skip(4)
            val count = ctx.boundedCount(chunks.u32(), sizes.size)
            val offsets = List(count) { if (atom.type == "co64") chunks.i64().also { require(it >= 0) } else chunks.u32() }
            val sc = data(requireNotNull(table.firstOrNull { it.type == "stsc" }))
            sc.skip(4)
            val runs = ctx.boundedCount(sc.u32(), count)
            val mappings =
                List(runs) {
                    val first = ctx.boundedCount(sc.u32(), count)
                    val n = ctx.boundedCount(sc.u32(), sizes.size)
                    require(first > 0 && n > 0 && sc.u32() == 1L)
                    first to n
                }
            require(mappings.isNotEmpty() && mappings.first().first == 1)
            require(mappings.zipWithNext().all { it.first.first < it.second.first })
            val result = mutableListOf<Long>()
            var run = 0
            offsets.forEachIndexed { index, position ->
                while (run + 1 < mappings.size && mappings[run + 1].first <= index + 1)run++
                var pos = position
                repeat(mappings[run].second) {
                    require(result.size < sizes.size)
                    val size = sizes[result.size]
                    checkRange(pos, size, ctx.input.length)
                    result += pos
                    pos = Math.addExact(pos, size.toLong())
                }
            }
            require(result.size == sizes.size)
            return result
        }

        fun presentationEdit(
            track: Atom,
            movie: List<Atom>,
        ): Pair<MediaTime, Long> {
            val atom = child(track, "edts")?.let { child(it, "elst") } ?: return MediaTime(0, 1) to 0
            val c = data(atom)
            val v = c.u8()
            require(v in 0..1)
            c.skip(3)
            val count = c.u32()
            require(count in 1..2)
            val movieScale = timeScale(data(requireNotNull(movie.firstOrNull { it.type == "mvhd" })))
            var delay = MediaTime(0, 1)
            var start: Long? = null
            repeat(count.toInt()) { i ->
                val duration = if (v == 1) c.i64() else c.u32()
                val media = if (v == 1) c.i64() else c.u32().toInt().toLong()
                require(c.u16() == 1 && c.u16() == 0)
                if (media == -1L) {
                    require(i == 0 && count == 2L)
                    delay = MediaTime(duration, movieScale)
                } else {
                    require(media >= 0 && start == null && i == count.toInt() - 1)
                    start = media
                }
            }
            return delay to requireNotNull(start)
        }

        fun timeScale(c: Cursor): Long {
            val v = c.u8()
            require(v in 0..1)
            c.skip(if (v == 1) 19 else 11)
            return c.u32().also { require(it > 0) }
        }

        fun trackId(a: Atom): Long = timeScale(data(requireNotNull(child(a, "tkhd"))))

        fun handler(a: Atom): String? {
            val h = child(a, "mdia")?.let { child(it, "hdlr") } ?: return null
            val c = data(h)
            c.skip(8)
            return c.latin(4)
        }

        fun chapterText(bytes: ByteArray): String =
            if (bytes.size >= 2 &&
                ((bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()) || (bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()))
            ) {
                decodeText(bytes, ctx, "UTF-16")
            } else {
                decodeText(bytes, ctx)
            }

        fun ordinary(meta: Atom) {
            val ilst = atoms(meta.payload + 4, meta.end).firstOrNull { it.type == "ilst" } ?: return
            val tags = mutableListOf<Tag>()
            val art = mutableListOf<Artwork>()
            val names =
                mapOf(
                    "©nam" to "TITLE",
                    "©alb" to "ALBUM",
                    "©ART" to "ARTIST",
                    "aART" to "ALBUMARTIST",
                    "©gen" to "GENRE",
                    "©day" to "DATE",
                    "©wrt" to "COMPOSER",
                    "©cmt" to "COMMENT",
                    "©lyr" to "LYRICS",
                    "trkn" to "TRACKNUMBER",
                    "disk" to "DISCNUMBER",
                )
            children(ilst).forEach { item ->
                val parts = children(item)

                fun fullText(type: String) =
                    parts.firstOrNull { it.type == type }?.let {
                        val c = data(it)
                        c.skip(4)
                        decodeText(c.bytes(c.remaining), ctx)
                    }
                val name = if (item.type == "----") fullText("name") ?: "----" else names[item.type] ?: item.type
                val domain = if (item.type == "----") fullText("mean") else null
                parts.filter { it.type == "data" }.forEach { d ->
                    val c = data(d)
                    val type = c.u32() and 0xffffff
                    c.u32()
                    val bytes = c.bytes(c.remaining)
                    if (item.type == "covr") {
                        val start = d.payload + 8
                        val count = bytes.size
                        art +=
                            Artwork(
                                "mp4/cover/${d.start}",
                                if (type ==
                                    13L
                                ) {
                                    "image/jpeg"
                                } else if (type == 14L) {
                                    "image/png"
                                } else {
                                    "application/octet-stream"
                                },
                                size = count.toLong(),
                                loader = {
                                    ctx.check()
                                    ctx.input.read(start, count)
                                },
                            )
                    } else {
                        val value =
                            when {
                                item.type == "trkn" || item.type == "disk" -> {
                                    val v = Cursor(bytes)
                                    v.skip(2)
                                    val number = v.u16()
                                    val total = v.u16()
                                    if (total > 0) "$number/$total" else number.toString()
                                }

                                item.type == "gnre" -> {
                                    require(bytes.size == 2)
                                    Cursor(bytes).u16().toString()
                                }

                                type == 1L -> {
                                    decodeText(bytes, ctx)
                                }

                                type == 2L -> {
                                    decodeText(bytes, ctx, "UTF-16BE")
                                }

                                type == 0L &&
                                    item.type == "----" &&
                                    (
                                        name.startsWith("REPLAYGAIN_", true) ||
                                            name.startsWith("R128_", true) ||
                                            name.startsWith("RIFF_", true) ||
                                            name.uppercase(java.util.Locale.ROOT) in setOf("ITUNNORM", "ITUNSMPB", "CUESHEET")
                                    ) -> {
                                    decodeText(bytes, ctx)
                                }

                                item.type == "stik" || type == 21L || type == 22L -> {
                                    require(bytes.size in 1..8)
                                    var n = if (type == 21L && bytes[0] < 0) -1L else 0L
                                    bytes.forEach { n = (n shl 8) or (it.toLong() and 255) }
                                    n.toString()
                                }

                                else -> {
                                    bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
                                }
                            }
                        tags += Tag(name, value, location = if (domain != null) "$domain:$name" else d.start.toString())
                    }
                }
            }
            val id = "${ctx.input.id}#mp4/${meta.start}"
            out += Source("mp4", id, ctx.input.id, tags, artwork = art, gains = Metadata.gains(tags, ctx))
            tags.filter { it.key.equals("CUESHEET", true) }.forEachIndexed {
                i,
                t,
                ->
                out += CueReader().parse(t.value, ctx, "$id/cue/$i", ctx.input.id)
            }
        }

        fun optional(
            code: String,
            block: () -> Unit,
        ) {
            try {
                block()
            } catch (
                e: IllegalArgumentException,
            ) {
                if (e is ParseFailure) throw e
                ctx.diagnostic(code, e.message ?: "Invalid MP4 structure", severity = "error")
            } catch (
                e: java.nio.BufferUnderflowException,
            ) {
                ctx.diagnostic(code, "Truncated MP4 structure", severity = "error")
            } catch (
                e: ArithmeticException,
            ) {
                ctx.diagnostic(code, "MP4 integer overflow", severity = "error")
            }
        }
    }
}
