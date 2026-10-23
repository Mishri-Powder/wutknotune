// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import java.io.IOException
import java.util.concurrent.CancellationException

interface FormatReader {
    val name: String

    fun matches(
        header: ByteArray,
        hint: String?,
    ): Boolean

    fun read(context: ParseContext): List<Source>
}

class Cuegrain(
    val options: ParseOptions = ParseOptions(),
    val readers: List<FormatReader> = listOf(CueReader(), Id3Reader(), FlacReader(), OggReader(), Mp4Reader()),
) {
    fun read(
        input: MediaInput,
        hint: String? = null,
    ): Report {
        val ctx = ParseContext(input, options)
        val sources = mutableListOf<Source>()
        try {
            val header = ctx.read(0, minOf(32L, input.length).toInt())
            val reader = readers.firstOrNull { it.matches(header, hint) }
            if (reader == null) {
                ctx.diagnostic("FORMAT_UNSUPPORTED", "No supported metadata reader", severity = "error")
            } else {
                sources += reader.read(ctx)
                // Central validation keeps every container reader on the same feature-tag contract.
                sources.forEach { source ->
                    Metadata.diagnoseRiffFeatures(source.tags, ctx, source.id)
                    source.entries.forEach { entry ->
                        Metadata.diagnoseRiffFeatures(entry.tags, ctx, entry.id)
                    }
                }
            }
        } catch (
            e: CancellationException,
        ) {
            throw e
        } catch (
            e: IOException,
        ) {
            ctx.diagnostic("INPUT_IO", e.message ?: "Read failed", severity = "error")
        } catch (
            e: IllegalArgumentException,
        ) {
            ctx.diagnostic(
                if (e is ParseFailure) e.code else "METADATA_INVALID",
                e.message ?: "Invalid metadata",
                severity = "error",
            )
        } catch (
            e: java.nio.BufferUnderflowException,
        ) {
            ctx.diagnostic("METADATA_TRUNCATED", "Truncated structure", severity = "error")
        } catch (
            e: ArithmeticException,
        ) {
            ctx.diagnostic("METADATA_OVERFLOW", "Integer overflow", severity = "error")
        }
        val repaired = if (options.repairMojibake) sources.map { repairSource(it, options.detector) } else sources
        val hasError = ctx.diagnostics.any { it.severity == "error" }
        return Report(
            input.id,
            repaired,
            ctx.diagnostics.toList(),
            if (hasError) {
                if (sources.isEmpty()) "error" else "partial"
            } else {
                "ok"
            },
        )
    }

    /** Optional identity formation shares the caller's input and never changes plain metadata read. */
    fun acquire(
        input: MediaInput,
        hint: String? = null,
        identity: (MediaInput) -> link.roadio.cuegrain.identity.MediaIdentity,
    ): link.roadio.cuegrain.identity.MediaAcquisition {
        val shared = if (input is CachedMediaInput) input else CachedMediaInput(input)
        val report = read(shared, hint)
        if (report.diagnostics.any { it.code == "INPUT_IO" }) throw IOException("Media acquisition input failed")
        return link.roadio.cuegrain.identity
            .MediaAcquisition(report, identity(shared))
    }

    /** A group is a set of independent reports; no source priority is imposed. */
    fun readGroup(inputs: List<Pair<MediaInput, String?>>): List<Report> = inputs.map { read(it.first, it.second) }

    /** Resolved handles belong to the caller; this method never closes caller-owned inputs. */
    fun readWithReferences(
        input: MediaInput,
        hint: String? = null,
        resolver: FileResolver,
    ): List<Report> {
        val primary = read(input, hint)
        val related = linkedMapOf<String, Report>()
        val sources =
            primary.sources.map { source ->
                if (source.kind != "cue") {
                    source
                } else {
                    source.copy(
                        files =
                            source.files.map { file ->
                                if (file.resolvedInput != null) {
                                    file
                                } else {
                                    val resolved = resolver.resolve(source.location, file)
                                    if (resolved == null) {
                                        file
                                    } else {
                                        if (resolved.id != input.id && resolved.id !in related) {
                                            related[resolved.id] = read(resolved, resolved.id.substringAfterLast('.', ""))
                                        }
                                        file.copy(resolvedInput = resolved.id)
                                    }
                                }
                            },
                    )
                }
            }
        return listOf(primary.copy(sources = sources)) + related.values
    }
}
