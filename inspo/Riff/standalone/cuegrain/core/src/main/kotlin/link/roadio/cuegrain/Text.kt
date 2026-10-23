// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal fun strictText(
    bytes: ByteArray,
    charset: String,
): String =
    Charset
        .forName(charset)
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

internal fun decodeText(
    bytes: ByteArray,
    ctx: ParseContext,
    prescribed: String? = null,
): String {
    if (bytes.size > ctx.options.limits.textBytes) ctx.fail("LIMIT_TEXT", "Text field exceeds limit")
    try {
        return strictText(bytes, prescribed ?: "UTF-8").trimEnd('\u0000')
    } catch (e: CharacterCodingException) {
        ctx.diagnostic("TEXT_ENCODING_INVALID", "Invalid ${prescribed ?: "UTF-8"} text", severity = "error")
        return Charset
            .forName(prescribed ?: "UTF-8")
            .decode(ByteBuffer.wrap(bytes))
            .toString()
            .trimEnd('\u0000')
    }
}

internal data class DecodedCue(
    val text: String,
    val charset: String,
    val basis: String,
    val detection: EncodingDetection? = null,
)

internal fun decodeCue(
    bytes: ByteArray,
    ctx: ParseContext,
): DecodedCue {
    val bom =
        when {
            bytes.take(4) == listOf(0.toByte(), 0.toByte(), 0xfe.toByte(), 0xff.toByte()) -> "UTF-32BE" to 4
            bytes.take(4) == listOf(0xff.toByte(), 0xfe.toByte(), 0.toByte(), 0.toByte()) -> "UTF-32LE" to 4
            bytes.take(3) == listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) -> "UTF-8" to 3
            bytes.take(2) == listOf(0xff.toByte(), 0xfe.toByte()) -> "UTF-16LE" to 2
            bytes.take(2) == listOf(0xfe.toByte(), 0xff.toByte()) -> "UTF-16BE" to 2
            else -> null
        }
    if (bom != null) return DecodedCue(strictText(bytes.copyOfRange(bom.second, bytes.size), bom.first), bom.first, "bom")
    ctx.options.charset?.let { return DecodedCue(strictText(bytes, it), it, "explicit") }
    try {
        return DecodedCue(strictText(bytes, "UTF-8"), "UTF-8", "valid-utf8")
    } catch (
        _: CharacterCodingException,
    ) {
        // Detection is only used after deterministic decoding.
    }
    val sample = bytes.copyOf(minOf(bytes.size, 65536))
    val detection = ctx.options.detector?.detect(sample)
    if (detection != null && detection.isReliable && detection.charset != null && detection.bytesConsumed in 0..sample.size) {
        return DecodedCue(strictText(bytes, detection.charset), detection.charset, "ced", detection)
    }
    ctx.fail(
        "ENCODING_REQUIRED",
        "Legacy CUE encoding is unknown; supply charset or a reliable detector" +
            (detection?.let { " (candidate=${it.charset}, reliable=${it.isReliable}, bytesConsumed=${it.bytesConsumed})" } ?: ""),
    )
}

object Metadata {
    private val aliases =
        mapOf(
            "TIT2" to "title",
            "TT2" to "title",
            "TITLE" to "title",
            "TALB" to "album",
            "TAL" to "album",
            "ALBUM" to "album",
            "TPE1" to "artist",
            "TP1" to "artist",
            "ARTIST" to "artist",
            "PERFORMER" to "performer",
            "TPE2" to "albumArtist",
            "TP2" to "albumArtist",
            "ALBUMARTIST" to "albumArtist",
            "ALBUM ARTIST" to "albumArtist",
            "TRCK" to "trackNumber",
            "TRK" to "trackNumber",
            "TRACKNUMBER" to "trackNumber",
            "TRACKTOTAL" to "trackTotal",
            "TOTALTRACKS" to "trackTotal",
            "TPOS" to "discNumber",
            "TPA" to "discNumber",
            "DISCNUMBER" to "discNumber",
            "DISCTOTAL" to "discTotal",
            "TOTALDISCS" to "discTotal",
            "TCON" to "genre",
            "TCO" to "genre",
            "GENRE" to "genre",
            "GNRE" to "genre",
            "STIK" to "mediaKind",
            "ID3V1_GENRE" to "genre",
            "TDRC" to "date",
            "TYER" to "date",
            "DATE" to "date",
            "YEAR" to "date",
            "TCOM" to "composer",
            "COMPOSER" to "composer",
            "SONGWRITER" to "composer",
            "TSRC" to "isrc",
            "ISRC" to "isrc",
            "COMMENT" to "comment",
            "COMM" to "comment",
        )

    fun normalized(
        tags: List<Tag>,
        albumScope: Boolean = false,
        cue: Boolean = albumScope,
    ): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        tags.forEach { t ->
            aliases[t.key.uppercase(java.util.Locale.ROOT)]?.let { normalized ->
                val field = if (cue && normalized == "performer") "artist" else normalized
                val key =
                    if (albumScope) {
                        when (field) {
                            "title" -> "album"
                            "artist" -> "albumArtist"
                            else -> field
                        }
                    } else {
                        field
                    }
                // MP4's numeric genre is one-based; ID3 and consumer genre dictionaries are zero-based.
                val value =
                    if (t.key == "gnre") {
                        t.value
                            .toIntOrNull()
                            ?.takeIf { it in 1..256 }
                            ?.minus(1)
                            ?.toString()
                    } else {
                        t.value
                    }
                if (value != null) result.getOrPut(key) { mutableListOf() } += value
            }
        }
        return result
    }

    /** Returns only typed RIFF analysis fields; raw tags remain authoritative evidence and are never removed. */
    fun riffFeatures(tags: List<Tag>): RiffFeatures = RiffFeatureParser.parse(tags).value

    internal fun diagnoseRiffFeatures(
        tags: List<Tag>,
        ctx: ParseContext,
        owner: String,
    ) {
        RiffFeatureParser.parse(tags).issues.forEach { issue ->
            ctx.diagnostic(issue.code, "$owner: ${issue.message}", issue.location)
        }
    }

    fun gains(
        tags: List<Tag>,
        ctx: ParseContext? = null,
    ): List<Gain> =
        buildList {
            for (t in tags) {
                val key = t.key.uppercase(java.util.Locale.ROOT)
                val rg = Regex("REPLAYGAIN_(TRACK|ALBUM)_(GAIN|PEAK)").matchEntire(key)
                val r128 = Regex("R128_(TRACK|ALBUM)_GAIN").matchEntire(key)
                if (rg != null) {
                    val isGain = rg.groupValues[2] == "GAIN"
                    val raw = t.value.trim()
                    val number = (if (isGain) raw.replace(Regex("(?i)\\s*dB$"), "") else raw).toDoubleOrNull()
                    if (number == null || !number.isFinite() || (!isGain && number < 0)) {
                        ctx?.diagnostic("GAIN_INVALID", "Invalid $key", t.location)
                        continue
                    }
                    add(Gain("replaygain", rg.groupValues[1].lowercase(), number, if (isGain) "dB" else "linear", t.key, t.value))
                } else if (r128 != null) {
                    val value =
                        t.value
                            .trim()
                            .takeIf { Regex("[+-]?[0-9]+").matches(it) }
                            ?.toIntOrNull()
                    if (value == null || value !in -32768..32767) {
                        ctx?.diagnostic("GAIN_INVALID", "Invalid $key", t.location)
                        continue
                    }
                    add(Gain("r128", r128.groupValues[1].lowercase(), value / 256.0, "dB", t.key, t.value, -23.0, "LUFS"))
                } else if (key == "ITUNNORM") {
                    val words = t.value.trim().split(Regex("\\s+"))
                    if (words.size == 10 && words.all { Regex("[0-9a-fA-F]{8}").matches(it) }) {
                        val scale = maxOf(words[0].toLong(16), words[1].toLong(16))
                        if (scale > 0) {
                            add(Gain("soundcheck", "track", -10.0 * kotlin.math.log10(scale / 1000.0), "dB", t.key, t.value))
                        } else {
                            ctx?.diagnostic("SOUNDCHECK_INVALID", "Zero Sound Check scale", t.location)
                        }
                    } else {
                        ctx?.diagnostic("SOUNDCHECK_INVALID", "Expected ten hexadecimal Sound Check words", t.location)
                    }
                } else if (key == "REPLAYGAIN_REFERENCE_LOUDNESS" || key == "R128_REFERENCE_LOUDNESS") {
                    val match = Regex("([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*(dB|LUFS)?", RegexOption.IGNORE_CASE).matchEntire(t.value.trim())
                    val value = match?.groupValues?.get(1)?.toDoubleOrNull()
                    if (value != null &&
                        value.isFinite()
                    ) {
                        add(
                            Gain(
                                "reference",
                                if (key.startsWith("R128")) "r128" else "replaygain",
                                value,
                                match.groupValues[2].ifEmpty {
                                    "unspecified"
                                },
                                t.key,
                                t.value,
                            ),
                        )
                    } else {
                        ctx?.diagnostic("GAIN_INVALID", "Invalid $key", t.location)
                    }
                }
            }
        }
}
