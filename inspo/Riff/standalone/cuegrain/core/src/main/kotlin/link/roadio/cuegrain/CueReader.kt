// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

class CueReader : FormatReader {
    override val name = "cue"

    override fun matches(
        header: ByteArray,
        hint: String?,
    ): Boolean = hint?.lowercase() == "cue"

    override fun read(context: ParseContext): List<Source> {
        val count = context.boundedCount(context.input.length, context.options.limits.metadataBytes)
        val decoded = decodeCue(context.read(0, count), context)
        return listOf(
            parse(decoded.text, context, context.input.id).copy(
                attributes =
                    mapOf("charset" to decoded.charset, "encodingBasis" to decoded.basis) +
                        (decoded.detection?.let { mapOf("encodingReliable" to it.isReliable.toString(), "encodingBytesConsumed" to it.bytesConsumed.toString()) } ?: emptyMap()),
            ),
        )
    }

    fun parse(
        text: String,
        ctx: ParseContext,
        sourceId: String,
        embeddedFile: String? = null,
    ): Source {
        val tags = mutableListOf<Tag>()
        val files = mutableListOf<FileReference>()
        val tracks = mutableListOf<Track>()
        var track: Track? = null
        var file: FileReference? = null
        var afterFile = false
        text.lineSequence().forEachIndexed { index, original ->
            ctx.check()
            val lineNumber = (index + 1).toString()
            if (original.length > ctx.options.limits.textBytes) ctx.fail("LIMIT_TEXT", "CUE line is too long")
            val line = original.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEachIndexed
            val split = line.indexOfFirst(Char::isWhitespace).let { if (it < 0) line.length else it }
            val rawCommand = line.substring(0, split)
            val command = rawCommand.uppercase(java.util.Locale.ROOT)
            val args = line.substring(split).trim()
            if (command != rawCommand) ctx.oddity("CUE_COMMAND_CASE", "Lowercase command accepted", lineNumber)
            try {
                when (command) {
                    "FILE" -> {
                        file = null
                        val match =
                            Regex("^(.*)\\s+(\\S+)$").matchEntire(args)
                                ?: throw IllegalArgumentException("FILE needs a path and type")
                        val path = value(match.groupValues[1], ctx, lineNumber)
                        require(path.isNotBlank()) { "Empty FILE path" }
                        val type = match.groupValues[2].uppercase(java.util.Locale.ROOT)
                        if (type !in setOf("WAVE", "MP3", "AIFF", "BINARY", "MOTOROLA")) {
                            ctx.oddity("CUE_FILE_TYPE", "Nonstandard FILE type: $type", lineNumber)
                        }
                        file = FileReference("$sourceId/file/${files.size}", path, type)
                        files += file
                        afterFile = true
                    }

                    "TRACK" -> {
                        track = null
                        val parts = args.split(Regex("\\s+"))
                        require(parts.size == 2) { "TRACK needs number and type" }
                        val number = parts[0].toInt()
                        require(number > 0) { "Invalid track number" }
                        if (number > 99) ctx.oddity("CUE_EXTENDED_TRACK_NUMBER", "Track number exceeds CD range", lineNumber)
                        if (tracks.any { it.number == number }) {
                            ctx.diagnostic(
                                "CUE_DUPLICATE_TRACK",
                                "Duplicate track number",
                                lineNumber,
                                "error",
                            )
                        }
                        if (tracks.lastOrNull()?.number?.let { number <= it } == true) {
                            ctx.diagnostic("CUE_TRACK_ORDER", "Track numbers are not increasing", lineNumber, "error")
                        }
                        track = Track(number, parts[1].uppercase(java.util.Locale.ROOT), lineNumber)
                        tracks += track
                        afterFile = false
                        ctx.boundedCount(tracks.size.toLong(), ctx.options.limits.entries)
                    }

                    "INDEX" -> {
                        val active = requireNotNull(track) { "INDEX without TRACK" }
                        val parts = args.split(Regex("\\s+"))
                        require(parts.size == 2)
                        val number = parts[0].toInt()
                        require(number in 0..99)
                        val time = parseTime(parts[1])
                        val current =
                            file ?: embeddedFile?.let {
                                FileReference("$sourceId/file/0", it, resolvedInput = it).also { f ->
                                    files += f
                                    file = f
                                }
                            } ?: throw IllegalArgumentException("INDEX without FILE")
                        if (active.indexes.any { it.number == number }) {
                            ctx.diagnostic(
                                "CUE_DUPLICATE_INDEX",
                                "Duplicate index",
                                lineNumber,
                                "error",
                            )
                        }
                        if (afterFile && active.indexes.isNotEmpty()) {
                            ctx.oddity("CUE_CROSS_FILE_INDEX", "Track indexes span FILE declarations", lineNumber)
                        }
                        active.indexes += CueIndex(number, Position(current.id, time))
                        afterFile = false
                    }

                    "PREGAP", "POSTGAP" -> {
                        val active = requireNotNull(track) { "$command without TRACK" }
                        val time = parseTime(args)
                        if (command == "PREGAP") active.pregap = time else active.postgap = time
                    }

                    else -> {
                        val target = track?.tags ?: tags
                        if (command == "REM") {
                            val key = args.takeWhile { !it.isWhitespace() }
                            val rest = args.drop(key.length).trim()
                            target += Tag(key.ifEmpty { "REM" }, value(rest, ctx, lineNumber), location = lineNumber)
                        } else {
                            if (command !in setOf("TITLE", "PERFORMER", "SONGWRITER", "CATALOG", "ISRC", "FLAGS", "CDTEXTFILE")) {
                                ctx.oddity("CUE_EXTENSION", "Preserved extension $command", lineNumber)
                            }
                            target += Tag(command, value(args, ctx, lineNumber), location = lineNumber)
                        }
                        ctx.boundedCount(tags.size.toLong() + tracks.sumOf { it.tags.size }, ctx.options.limits.fields)
                    }
                }
            } catch (e: NumberFormatException) {
                ctx.diagnostic("CUE_NUMBER_INVALID", e.message ?: "Invalid number", lineNumber, "error")
            } catch (e: ArithmeticException) {
                ctx.diagnostic("CUE_TIME_OVERFLOW", "Time exceeds signed 64-bit range", lineNumber, "error")
            } catch (e: IllegalArgumentException) {
                if (e is ParseFailure) throw e
                ctx.diagnostic("CUE_SYNTAX", e.message ?: "Invalid CUE syntax", lineNumber, "error")
            }
        }
        val previous = mutableMapOf<String, MediaTime>()
        for (t in tracks) {
            if (t.indexes.none { it.number == 1 }) {
                ctx.diagnostic(
                    "CUE_INDEX01_MISSING",
                    "Track ${t.number} has no INDEX 01",
                    t.line,
                    "error",
                )
            }
            t.indexes.forEach { i ->
                previous[i.position.file]?.let {
                    if (i.position.time < it) {
                        ctx.diagnostic("CUE_TIME_ORDER", "Indexes decrease within one file", t.line, "error")
                    }
                }
                previous[i.position.file] = i.position.time
            }
            if (t.indexes.zipWithNext().any { it.first.number >= it.second.number }) {
                ctx.diagnostic("CUE_INDEX_ORDER", "Indexes are not increasing", t.line, "error")
            }
        }
        val entries =
            tracks.mapIndexed { i, t ->
                val start = t.indexes.firstOrNull { it.number == 1 }?.position
                val next =
                    tracks
                        .getOrNull(i + 1)
                        ?.indexes
                        ?.firstOrNull()
                        ?.position
                val end = next?.takeIf { start != null && it.file == start.file && it.time > start.time }
                Entry(
                    "$sourceId/track/$i",
                    t.number,
                    if (t.mode == "AUDIO") "track" else "data",
                    start,
                    end,
                    if (end != null) "next-index" else null,
                    t.tags,
                    t.indexes,
                    t.pregap,
                    t.postgap,
                    mapOf("trackMode" to t.mode),
                )
            }
        if (tracks.isEmpty()) ctx.diagnostic("CUE_NO_TRACKS", "No tracks found", severity = "error")
        return Source(
            "cue",
            sourceId,
            ctx.input.id,
            tags,
            entries,
            files = files,
            gains = Metadata.gains(tags, ctx),
            attributes = embeddedFile?.let { mapOf("embeddedIn" to it) } ?: emptyMap(),
        )
    }

    private class Track(
        val number: Int,
        val mode: String,
        val line: String,
    ) {
        val tags = mutableListOf<Tag>()
        val indexes = mutableListOf<CueIndex>()
        var pregap: MediaTime? = null
        var postgap: MediaTime? = null
    }

    private fun value(
        raw: String,
        ctx: ParseContext,
        line: String,
    ): String {
        if (!raw.startsWith('"')) return raw
        if (raw.length >= 2 && raw.endsWith('"')) return raw.substring(1, raw.length - 1)
        ctx.oddity("CUE_UNCLOSED_QUOTE", "Unclosed string quote", line)
        return raw.drop(1)
    }

    private fun parseTime(text: String): MediaTime {
        val parts = text.split(':')
        require(parts.size == 3) { "Expected MM:SS:FF" }
        val minutes = parts[0].toLong()
        val seconds = parts[1].toInt()
        val frames = parts[2].toInt()
        require(minutes >= 0 && seconds in 0..59 && frames in 0..74) { "Invalid CUE time" }
        val ticks = Math.addExact(Math.multiplyExact(minutes, 4500), seconds * 75L + frames)
        return MediaTime(ticks, 75)
    }
}
