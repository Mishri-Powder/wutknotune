// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

/** Stable JSON output without a runtime serialization dependency. */
object Json {
    fun encode(value: Any?): String = buildString { appendValue(value) }

    fun report(
        r: Report,
        includeArtwork: Boolean = false,
    ): Map<String, Any?> =
        linkedMapOf(
            "schemaVersion" to r.schemaVersion,
            "input" to r.input,
            "status" to r.status,
            "sources" to r.sources.map { source(it, includeArtwork) },
            "diagnostics" to
                r.diagnostics.map {
                    linkedMapOf(
                        "code" to it.code,
                        "severity" to it.severity,
                        "source" to it.source,
                        "location" to it.location,
                        "message" to it.message,
                    )
                },
        )

    private fun time(t: MediaTime?) = t?.let { mapOf("ticks" to it.ticks, "timescale" to it.timescale) }

    private fun pos(p: Position?) = p?.let { mapOf("file" to it.file, "time" to time(it.time)) }

    private fun tags(values: List<Tag>) =
        values.map {
            mapOf(
                "key" to it.key,
                "value" to it.value,
                "original" to it.original,
                "formatKey" to it.formatKey,
                "location" to it.location,
            )
        }

    private fun gains(values: List<Gain>) =
        values.map {
            mapOf(
                "kind" to it.kind,
                "scope" to it.scope,
                "value" to it.value,
                "unit" to it.unit,
                "key" to it.key,
                "raw" to it.raw,
                "reference" to it.reference,
                "referenceUnit" to it.referenceUnit,
            )
        }

    private fun metric(value: RiffMetric?) = value?.let { mapOf("value" to it.value, "confidence" to it.confidence) }

    private fun riffFeatures(value: RiffFeatures) =
        mapOf(
            "arousal" to metric(value.arousal),
            "valence" to metric(value.valence),
            "pte" to metric(value.pte),
            "genres" to value.genres.map { mapOf("value" to it.value, "confidence" to it.confidence) },
        )

    private fun art(
        a: Artwork,
        bytes: Boolean,
    ): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "id" to a.id,
            "mimeType" to a.mimeType,
            "type" to a.type,
            "description" to a.description,
            "size" to a.size,
            "externalUrl" to a.externalUrl,
            "frameIndex" to a.frameIndex,
        ).also {
            if (bytes && a.externalUrl == null) {
                it["base64"] =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(a.readBytes())
            }
        }

    private fun source(
        s: Source,
        bytes: Boolean,
    ): Map<String, Any?> =
        linkedMapOf(
            "kind" to s.kind,
            "id" to s.id,
            "location" to s.location,
            "tags" to tags(s.tags),
            "metadata" to Metadata.normalized(s.tags, s.kind == "cue"),
            "riffFeatures" to riffFeatures(s.riffFeatures),
            "gains" to gains(s.gains),
            "files" to
                s.files.map {
                    mapOf(
                        "id" to it.id,
                        "path" to it.path,
                        "declaredType" to it.declaredType,
                        "resolvedInput" to it.resolvedInput,
                    )
                },
            "artwork" to s.artwork.map { art(it, bytes) },
            "attributes" to s.attributes,
            "entries" to
                s.entries.map { e ->
                    linkedMapOf(
                        "id" to e.id,
                        "number" to e.number,
                        "kind" to e.kind,
                        "start" to pos(e.start),
                        "end" to pos(e.end),
                        "endOrigin" to e.endOrigin,
                        "tags" to tags(e.tags),
                        "metadata" to Metadata.normalized(e.tags, cue = s.kind == "cue"),
                        "riffFeatures" to riffFeatures(e.riffFeatures),
                        "gains" to gains(Metadata.gains(e.tags)),
                        "indexes" to e.indexes.map { mapOf("number" to it.number, "position" to pos(it.position)) },
                        "pregap" to time(e.pregap),
                        "postgap" to time(e.postgap),
                        "attributes" to e.attributes,
                        "artwork" to e.artwork.map { art(it, bytes) },
                    )
                },
            "tables" to
                s.tables.map {
                    mapOf(
                        "id" to it.id,
                        "ordered" to it.ordered,
                        "root" to it.root,
                        "children" to it.children,
                        "tags" to tags(it.tags),
                    )
                },
        )

    private fun StringBuilder.appendValue(v: Any?) {
        when (v) {
            null -> {
                append("null")
            }

            is String -> {
                append('"')
                v.forEach { c ->
                    when (c) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> if (c < ' ' || c.isSurrogate()) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
                    }
                }
                append('"')
            }

            is Boolean, is Byte, is Short, is Int, is Long -> {
                append(v.toString())
            }

            is Double -> {
                require(v.isFinite())
                append(v.toString())
            }

            is Float -> {
                require(v.isFinite())
                append(v.toString())
            }

            is Map<*, *> -> {
                append('{')
                v.entries.forEachIndexed { i, e ->
                    if (i > 0) append(',')
                    appendValue(e.key.toString())
                    append(':')
                    appendValue(e.value)
                }
                append('}')
            }

            is Iterable<*> -> {
                append('[')
                v.forEachIndexed { i, e ->
                    if (i > 0) append(',')
                    appendValue(e)
                }
                append(']')
            }

            else -> {
                error("Unsupported JSON value ${v.javaClass}")
            }
        }
    }
}
