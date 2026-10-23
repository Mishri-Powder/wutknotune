// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.Cuegrain
import link.roadio.cuegrain.Entry
import link.roadio.cuegrain.MediaInput
import link.roadio.cuegrain.MediaTime
import link.roadio.cuegrain.Metadata
import link.roadio.cuegrain.ParseOptions
import link.roadio.cuegrain.Source
import java.io.IOException
import java.math.BigInteger

data class Section(
    val reference: String?,
    val span: Span,
    val attributes: Attributes,
)

data class TableObservation(
    val key: String,
    val attributes: Attributes,
    val sections: List<Section>,
)

data class Observation(
    val attributes: Attributes,
    val tables: List<TableObservation>,
    val diagnostics: List<Diagnostic>,
    val gainSources: List<Source> = emptyList(),
    val sourceId: String? = null,
    val sourceEvidence: Attributes = emptyMap(),
    val sectionGainSources: Map<String, List<List<Source>>> = emptyMap(),
)

data class AcquiredObservation(
    val observation: Observation,
    val high: Long?,
)

fun interface MetadataReader {
    fun read(
        input: MediaInput,
        hint: String,
        cancellation: Cancellation,
    ): Observation

    fun acquire(
        input: MediaInput,
        hint: String,
        cancellation: Cancellation,
        high: ((MediaInput) -> Long)?,
    ): AcquiredObservation {
        val observation = read(input, hint, cancellation)
        cancellation.check()
        return AcquiredObservation(observation, high?.invoke(input))
    }
}

/** Selection and structural validation belong here; every format observation comes from Cuegrain. */
class CuegrainMetadataReader : MetadataReader {
    override fun read(
        input: MediaInput,
        hint: String,
        cancellation: Cancellation,
    ): Observation {
        val report =
            Cuegrain(
                ParseOptions(cancelled = {
                    cancellation.check()
                    false
                }),
            ).read(input, hint)
        if (report.diagnostics.any { it.code == "INPUT_IO" }) throw IOException("Cuegrain input read failed")
        val ordinary = report.sources.firstOrNull { it.kind in setOf("id3", "flac", "vorbis", "opus", "mp4", "id3v1") && it.tags.isNotEmpty() }
        val attrs =
            ordinary
                ?.let { source ->
                    val normalized = Metadata.normalized(source.tags, cue = false)
                    (if (source.kind == "mp4") normalized else normalized - "mediaKind") + riffFeatureAttributes(source.riffFeatures)
                }.orEmpty()
        val diagnostics = report.diagnostics.map { Diagnostic(it.code, input.id, it.message) }.toMutableList()
        val tables = mutableListOf<TableObservation>()
        val sectionGainSources = mutableMapOf<String, List<List<Source>>>()
        for ((i, source) in report.sources.withIndex()) {
            cancellation.check()
            if (source.entries.isEmpty()) continue
            try {
                // A malformed source cannot accidentally become an authoritative album.
                require(report.diagnostics.none { it.severity == "error" })
                val ordered = orderedEntries(source).filter { it.kind != "data" }
                require(ordered.size > 1)
                val files = source.files.associateBy { it.id }
                val entries =
                    ordered.map { entry ->
                        val start = requireNotNull(entry.start)
                        val reference = if (start.file == input.id) null else requireNotNull(files[start.file]).path
                        val end =
                            entry.end
                                ?.also { require(it.file == start.file) }
                                ?.time
                                ?.micros()
                        Section(
                            reference,
                            Span(start.time.micros(), end),
                            Metadata.normalized(entry.tags, cue = source.kind == "cue") + riffFeatureAttributes(entry.riffFeatures) +
                                listOfNotNull(entry.number?.let { "trackNumber" to listOf(it.toString()) }).toMap(),
                        )
                    }
                entries.groupBy { it.reference }.values.forEach { sections ->
                    require(sections.zipWithNext().all { (a, b) -> a.span.beginUs!! < b.span.beginUs!! && (a.span.endUs == null || a.span.endUs <= b.span.beginUs!!) })
                }
                tables += TableObservation("${source.kind}:$i", Metadata.normalized(source.tags, albumScope = source.kind == "cue"), entries)
                sectionGainSources["${source.kind}:$i"] =
                    ordered.map { entry ->
                        listOf(Source(source.kind, entry.id, source.location, tags = entry.tags, gains = Metadata.gains(entry.tags)))
                    }
            } catch (_: IllegalArgumentException) {
                diagnostics += Diagnostic("INVALID_STRUCTURE", input.id, "Not a valid multi-track structure: ${source.kind}")
            }
        }
        return Observation(attrs, tables, diagnostics, report.sources, input.id, sectionGainSources = sectionGainSources)
    }

    private fun orderedEntries(source: Source): List<Entry> {
        if (source.tables.isEmpty()) return source.entries
        val root = requireNotNull(source.tables.singleOrNull { it.root })
        require(root.ordered)
        val tables = source.tables.associateBy { it.id }
        val entries = source.entries.associateBy { it.id }
        require(tables.size == source.tables.size && entries.size == source.entries.size)
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Entry>()

        fun visit(id: String) {
            require(seen.add(id)) { "Repeated or cyclic TOC child" }
            entries[id]?.let {
                result += it
                return
            }
            val table = requireNotNull(tables[id])
            require(table.ordered)
            table.children.forEach(::visit)
        }
        visit(root.id)
        require(result.size == source.entries.size)
        return result
    }

    private fun MediaTime.micros(): Long =
        BigInteger
            .valueOf(ticks)
            .multiply(BigInteger.valueOf(1_000_000))
            .divide(BigInteger.valueOf(timescale))
            .longValueExact()
}
