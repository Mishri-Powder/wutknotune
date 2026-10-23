// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import java.io.ByteArrayOutputStream

/** Walks page headers; audio packet payloads are skipped, not decoded. */
class OggReader : FormatReader {
    override val name = "ogg"

    override fun matches(
        header: ByteArray,
        hint: String?,
    ) = header.take(4).toByteArray().toString(Charsets.US_ASCII) == "OggS"

    private class Stream {
        var sequence: Long? = null
        var packet = 0
        var codec: String? = null
        val pending = ByteArrayOutputStream()
    }

    override fun read(context: ParseContext): List<Source> {
        val ctx = context
        var offset = 0L
        val streams = linkedMapOf<Long, Stream>()
        val result = mutableListOf<Source>()
        while (offset < ctx.input.length) {
            ctx.check()
            val c = Cursor(ctx.read(offset, 27), true)
            require(c.latin(4) == "OggS" && c.u8() == 0) { "Invalid Ogg page" }
            val flags = c.u8()
            c.i64()
            val serial = c.u32()
            val sequence = c.u32()
            c.u32()
            val laces = ctx.read(offset + 27, c.u8())
            val payloadStart = offset + 27 + laces.size
            val payloadSize = laces.sumOf { it.toInt() and 255 }
            checkRange(payloadStart, payloadSize, ctx.input.length)
            val stream =
                streams.getOrPut(serial) {
                    ctx.boundedCount(streams.size.toLong() + 1, ctx.options.limits.entries)
                    if (flags and 2 == 0) ctx.diagnostic("OGG_BOS_MISSING", "Missing beginning-of-stream flag", offset.toString(), "error")
                    Stream()
                }
            stream.sequence?.let {
                if (sequence != ((it + 1) and 0xffffffffL)) {
                    ctx.diagnostic("OGG_SEQUENCE", "Discontinuous page sequence", offset.toString(), "error")
                }
            }
            stream.sequence = sequence
            if (stream.packet < 2 && (flags and 1 != 0) != (stream.pending.size() > 0)) {
                ctx.fail("OGG_CONTINUATION", "Invalid continued metadata packet")
            }
            var position = payloadStart
            for (raw in laces) {
                val length = raw.toInt() and 255
                if (stream.packet < 2) {
                    if (stream.pending.size().toLong() + length > ctx.options.limits.metadataBytes) {
                        ctx.fail("LIMIT_PACKET", "Ogg metadata packet exceeds limit")
                    }
                    stream.pending.write(ctx.read(position, length))
                }
                position += length
                if (length < 255) {
                    if (stream.packet < 2) {
                        val bytes = stream.pending.toByteArray()
                        stream.pending.reset()
                        packet(bytes, stream, ctx, serial, result)
                    }
                    stream.packet++
                }
            }
            offset = payloadStart + payloadSize
        }
        if (streams.values.any { it.pending.size() > 0 || it.packet < 2 }) {
            ctx.diagnostic("OGG_TRUNCATED", "Incomplete metadata packets", severity = "error")
        }
        if (result.isEmpty()) ctx.diagnostic("OGG_CODEC_UNSUPPORTED", "Only Vorbis and Opus metadata are supported", severity = "error")
        return result
    }

    private fun packet(
        bytes: ByteArray,
        s: Stream,
        ctx: ParseContext,
        serial: Long,
        out: MutableList<Source>,
    ) {
        val opus = bytes.size >= 8 && bytes.copyOfRange(0, 8).toString(Charsets.US_ASCII) == "OpusHead"
        val vorbis = bytes.size >= 7 && bytes[0] == 1.toByte() && bytes.copyOfRange(1, 7).toString(Charsets.US_ASCII) == "vorbis"
        if (s.packet == 0) {
            if (opus) {
                s.codec = "opus"
                require(bytes.size >= 19)
                val c = Cursor(bytes, true)
                c.skip(8)
                val version = c.u8()
                require(version < 16)
                val channels = c.u8()
                val preSkip = c.u16()
                val rate = c.u32()
                val rawGain = c.u16().toShort().toInt()
                out +=
                    Source(
                        "opus",
                        "${ctx.input.id}#opus/$serial",
                        ctx.input.id,
                        gains = listOf(Gain("opus-header", "output", rawGain / 256.0, "dB", "OpusHead.outputGain", rawGain.toString())),
                        attributes =
                            mapOf(
                                "streamSerial" to serial.toString(),
                                "channels" to channels.toString(),
                                "preSkip" to preSkip.toString(),
                                "inputSampleRate" to rate.toString(),
                            ),
                    )
            } else if (vorbis) {
                s.codec = "vorbis"
            }
        } else if (s.packet == 1) {
            val prefix = if (s.codec == "opus") 8 else 7
            val valid =
                if (s.codec == "opus") {
                    bytes.size >= 8 && bytes.copyOfRange(0, 8).toString(Charsets.US_ASCII) == "OpusTags"
                } else {
                    s.codec == "vorbis" &&
                        bytes.size >= 7 &&
                        bytes[0] == 3.toByte() &&
                        bytes.copyOfRange(1, 7).toString(Charsets.US_ASCII) == "vorbis"
                }
            if (s.codec != null && !valid) ctx.fail("OGG_COMMENT_HEADER", "Expected codec comment packet")
            if (valid) {
                out +=
                    Vorbis.comments(bytes.copyOfRange(prefix, bytes.size), ctx, "${ctx.input.id}#${s.codec}/$serial/comments").map {
                        val streamId = "${ctx.input.id}#ogg-stream/$serial"
                        it.copy(
                            attributes = it.attributes + mapOf("codec" to s.codec!!, "streamSerial" to serial.toString()),
                            files = it.files + FileReference(streamId, ctx.input.id, s.codec),
                            entries =
                                if (it.kind ==
                                    "vorbis"
                                ) {
                                    it.entries.map { e ->
                                        e.copy(start = e.start?.copy(file = streamId), end = e.end?.copy(file = streamId))
                                    }
                                } else {
                                    it.entries
                                },
                        )
                    }
            }
        }
    }
}
