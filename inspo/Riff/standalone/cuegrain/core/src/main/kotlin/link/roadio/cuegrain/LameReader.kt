// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

/** Reads only the first MPEG frame and its optional Xing/LAME info tag. */
internal object LameReader {
    fun read(
        ctx: ParseContext,
        offset: Long,
    ): Source? {
        val h = Cursor(ctx.read(offset, 4)).u32()
        if (h and 0xffe00000L != 0xffe00000L) return null
        val version = ((h ushr 19) and 3).toInt()
        val layer = ((h ushr 17) and 3).toInt()
        if (version == 1 || layer != 1) return null
        val mono = ((h ushr 6) and 3) == 3L
        val side =
            if (version == 3) {
                if (mono) {
                    17
                } else {
                    32
                }
            } else if (mono) {
                9
            } else {
                17
            }
        val start = offset + 4 + side
        if (ctx.input.length - start < 8) return null
        val xing = Cursor(ctx.read(start, 8))
        if (xing.latin(4) !in setOf("Xing", "Info")) return null
        val flags = xing.u32()
        var lame = start + 8
        if (flags and 1 != 0L) lame += 4
        if (flags and 2 != 0L) lame += 4
        if (flags and 4 != 0L) lame += 100
        if (flags and 8 != 0L) lame += 4
        if (ctx.input.length - lame < 27) return null
        val c = Cursor(ctx.read(lame, 27))
        val encoder = c.latin(9)
        if (!encoder.startsWith("LAME")) return null
        c.skip(2)
        val peak = c.u32()
        val gains = mutableListOf<Gain>()
        val origins = linkedMapOf<String, String>()
        repeat(2) { i ->
            val field = c.u16()
            val kind = (field ushr 13) and 7
            val origin = (field ushr 10) and 7
            if (kind in 1..2 && origin != 0) {
                val scope = if (kind == 1) "track" else "album"
                val value = (field and 511) / 10.0 * (if (field and 512 != 0) -1 else 1)
                gains += Gain("lame", scope, value, "dB", "gain$i", field.toString())
                origins["gain${i}Originator"] = origin.toString()
                if (peak != 0L) gains += Gain("lame", scope, peak / 8388608.0, "linear", "peak", peak.toString())
            }
        }
        return Source(
            "lame",
            "${ctx.input.id}#lame/$lame",
            ctx.input.id,
            gains = gains,
            attributes = origins + mapOf("encoder" to encoder.trimEnd('\u0000')),
        )
    }
}
