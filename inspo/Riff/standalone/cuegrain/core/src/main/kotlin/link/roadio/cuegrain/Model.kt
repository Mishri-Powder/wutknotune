// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import java.math.BigInteger

/** Exact file-local time. No floating-point value is authoritative. */
data class MediaTime(
    val ticks: Long,
    val timescale: Long,
) : Comparable<MediaTime> {
    init {
        require(ticks >= 0 && timescale > 0)
    }

    override fun compareTo(other: MediaTime): Int =
        BigInteger.valueOf(ticks).multiply(BigInteger.valueOf(other.timescale)).compareTo(
            BigInteger.valueOf(other.ticks).multiply(BigInteger.valueOf(timescale)),
        )

    fun milliseconds(): Long =
        BigInteger
            .valueOf(ticks)
            .multiply(BigInteger.valueOf(1000))
            .divide(BigInteger.valueOf(timescale))
            .longValueExact()
}

data class Position(
    val file: String,
    val time: MediaTime,
)

data class CueIndex(
    val number: Int,
    val position: Position,
)

data class Tag(
    val key: String,
    val value: String,
    val original: String = value,
    val location: String? = null,
    val formatKey: String? = null,
)

data class FileReference(
    val id: String,
    val path: String,
    val declaredType: String? = null,
    val resolvedInput: String? = null,
)

data class Entry(
    val id: String,
    val number: Int? = null,
    val kind: String = "chapter",
    val start: Position? = null,
    val end: Position? = null,
    val endOrigin: String? = null,
    val tags: List<Tag> = emptyList(),
    val indexes: List<CueIndex> = emptyList(),
    val pregap: MediaTime? = null,
    val postgap: MediaTime? = null,
    val attributes: Map<String, String> = emptyMap(),
    val artwork: List<Artwork> = emptyList(),
) {
    val riffFeatures: RiffFeatures get() = Metadata.riffFeatures(tags)
}

data class TableOfContents(
    val id: String,
    val ordered: Boolean,
    val root: Boolean,
    val children: List<String>,
    val tags: List<Tag> = emptyList(),
)

/** Gain observations stay independent; zero is a valid value, never an absence sentinel. */
data class Gain(
    val kind: String,
    val scope: String,
    val value: Double,
    val unit: String,
    val key: String,
    val raw: String,
    val reference: Double? = null,
    val referenceUnit: String? = null,
)

data class Artwork(
    val id: String,
    val mimeType: String,
    val type: Int? = null,
    val description: String? = null,
    val size: Long? = null,
    val externalUrl: String? = null,
    internal val loader: (() -> ByteArray)? = null,
    val frameIndex: Int? = null,
) {
    /** The caller owns the source lifetime. URLs are descriptors and are never fetched. */
    fun readBytes(): ByteArray = requireNotNull(loader) { "External artwork has no embedded bytes" }.invoke()
}

data class Source(
    val kind: String,
    val id: String,
    val location: String,
    val tags: List<Tag> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val tables: List<TableOfContents> = emptyList(),
    val files: List<FileReference> = emptyList(),
    val artwork: List<Artwork> = emptyList(),
    val gains: List<Gain> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
) {
    val metadata: Map<String, List<String>> get() = Metadata.normalized(tags, kind == "cue")
    val riffFeatures: RiffFeatures get() = Metadata.riffFeatures(tags)
}

data class Diagnostic(
    val code: String,
    val severity: String,
    val source: String,
    val location: String? = null,
    val message: String,
)

data class Report(
    val input: String,
    val sources: List<Source>,
    val diagnostics: List<Diagnostic>,
    val status: String,
    val schemaVersion: Int = 1,
)

enum class ParseMode { COMPATIBLE, STRICT }

data class Limits(
    val metadataBytes: Int = 32 * 1024 * 1024,
    val totalReadBytes: Long = 128L * 1024 * 1024,
    val entries: Int = 10000,
    val fields: Int = 65536,
    val textBytes: Int = 1024 * 1024,
    val nesting: Int = 16,
    val diagnostics: Int = 1000,
    val elements: Int = 100000,
) {
    init {
        require(
            metadataBytes > 0 &&
                totalReadBytes > 0 &&
                entries > 0 &&
                fields > 0 &&
                textBytes > 0 &&
                nesting > 0 &&
                diagnostics > 0 &&
                elements > 0,
        )
    }
}

data class EncodingDetection(
    val charset: String?,
    val isReliable: Boolean,
    val bytesConsumed: Int,
)

interface EncodingDetector {
    fun detect(bytes: ByteArray): EncodingDetection
}

data class ParseOptions(
    val mode: ParseMode = ParseMode.COMPATIBLE,
    val charset: String? = null,
    val detector: EncodingDetector? = null,
    val repairMojibake: Boolean = false,
    val limits: Limits = Limits(),
    val cancelled: () -> Boolean = { false },
)
