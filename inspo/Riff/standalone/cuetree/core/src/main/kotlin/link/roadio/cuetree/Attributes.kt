// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import com.dynatrace.hash4j.hashing.Hashing
import java.nio.ByteBuffer

/** Wire values are stable; EXTENSION preserves supported consumer metadata outside the MVP kinds. */
enum class AttributeKind(
    val code: Int,
    val key: String,
    val numeric: Boolean = false,
) {
    UNKNOWN(0, ""),
    MIME_TYPE(1, "mimeType"),
    TITLE(2, "title"),
    TRACK(3, "trackNumber", true),
    TOTAL_TRACKS(4, "trackTotal", true),
    ALBUM(5, "album"),
    ARTIST(6, "artist"),
    ALBUM_ARTIST(7, "albumArtist"),
    GENRE(8, "genreId", true),
    YEAR(9, "year", true),
    DISC(10, "discNumber", true),
    TOC(11, "toc"),
    GENRE_TEXT(12, "genre"),
    TOTAL_DISCS(13, "discTotal", true),
    ALBUM_GROUP(14, "albumGroup", true),
    ALBUM_TOTAL_TRACKS(15, "albumTotalTracks", true),
    MEDIA_KIND(16, "mediaKind", true),
    FRAGMENT(17, "fragment", true),
    TOTAL_FRAGMENTS(18, "totalFragments", true),
    REPLAY_GAIN(32, "replayGain"),
    EXTENSION(127, ""),
}

data class AttributeValue(
    val kind: AttributeKind,
    val text: String? = null,
    val number: Long? = null,
) {
    init {
        require(kind != AttributeKind.UNKNOWN)
        require(if (kind.numeric) number != null && text == null else text != null && number == null)
    }

    val id: MediaId get() {
        val bytes = text?.toByteArray(Charsets.UTF_8) ?: ByteBuffer.allocate(8).putLong(number!!).array()
        val frame =
            ByteBuffer
                .allocate(5 + bytes.size)
                .put(kind.code.toByte())
                .putInt(bytes.size)
                .put(bytes)
                .array()
        return MediaId(Hashing.xxh3_64().hashBytesToLong(frame))
    }

    internal fun entry(): Pair<String, String> {
        if (kind != AttributeKind.EXTENSION) return kind.key to (text ?: number.toString())
        val colon = text!!.indexOf(':')
        require(colon > 0)
        val length = text.substring(0, colon).toInt()
        require(length > 0 && length <= text.length - colon - 1)
        return text.substring(colon + 1, colon + 1 + length) to text.substring(colon + 1 + length)
    }
}

data class AttributeRecord(
    val value: AttributeValue,
    val life: Life,
) {
    val id: MediaId get() = value.id
    val flags: Int get() = Flags.IDHI or life.flags
}

internal fun Attributes.typed(): List<AttributeValue> =
    toSortedMap().flatMap { (key, values) ->
        values.map { value ->
            val kind = AttributeKind.entries.firstOrNull { it.key == key && it != AttributeKind.UNKNOWN }
            val number = value.toLongOrNull()
            when {
                kind == null || (kind.numeric && (number == null || number.toString() != value)) -> {
                    AttributeValue(AttributeKind.EXTENSION, "${key.length}:$key$value")
                }

                kind.numeric -> {
                    AttributeValue(kind, number = number)
                }

                else -> {
                    AttributeValue(kind, value)
                }
            }
        }
    }

/** A section's source GenreText and derived GNRE IDs replace the preceding scope together. */
internal fun Attributes.overlaidBy(override: Attributes): Attributes = (if ("genre" in override) this - "genre" - "genreId" else this) + override

internal fun normalizeAttributes(attributes: Attributes): Attributes {
    val result = attributes.toMutableMap()
    for ((key, total) in listOf("trackNumber" to "trackTotal", "discNumber" to "discTotal")) {
        attributes[key]?.let { values ->
            result[key] =
                values.map { value ->
                    value
                        .substringBefore('/')
                        .trim()
                        .toLongOrNull()
                        ?.takeIf { it > 0 }
                        ?.toString() ?: value
                }
            if (total !in result) {
                values
                    .firstOrNull()
                    ?.substringAfter('/', "")
                    ?.trim()
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { result[total] = listOf(it.toString()) }
            }
        }
    }
    attributes
        .first("date")
        ?.take(4)
        ?.toLongOrNull()
        ?.let { result["year"] = listOf(it.toString()) }
    return result
}

/** Re-intern all referenced values after a consumer adds projection-only attributes. */
fun Snapshot.withCompleteAttributeCatalog(): Snapshot = withAttributes(this, Long.MAX_VALUE)

internal fun Snapshot.withAttributes(
    previous: Snapshot,
    ttl: Long,
): Snapshot {
    val prior = previous.attributeCatalog.associateBy { it.id }
    val values = linkedMapOf<MediaId, AttributeValue>()
    val referred = mutableSetOf<MediaId>()
    val available = mutableSetOf<MediaId>()

    fun collect(
        attrs: Attributes,
        used: Boolean,
        access: Boolean,
    ) {
        for (value in attrs.typed()) {
            val id = value.id
            require(values.putIfAbsent(id, value)?.let { it == value } != false) { "Attribute hash collision" }
            require(prior[id]?.let { it.value == value } != false) { "Retained attribute hash collision" }
            if (used) referred += id
            if (used && access) available += id
        }
    }
    nodes.forEach { node ->
        collect(node.attributes, true, node.life.available)
        collect(node.sourceEvidence, true, node.life.available)
        node.tables.forEach { table ->
            collect(table.attributes, true, node.life.available)
            table.sections.forEach { section -> collect(section.attributes, true, node.life.available) }
        }
    }
    albums.forEach { collect(it.attributes, it.sources.isNotEmpty(), it.life.available) }
    artists.forEach { collect(it.attributes, it.albums.isNotEmpty(), it.life.available) }
    genres.forEach { collect(it.attributes, it.albums.isNotEmpty(), it.life.available) }
    val records =
        values
            .map { (id, value) ->
                val life = (prior[id]?.life ?: Life(generation)).referenced(id in referred, id in available, generation)
                id to AttributeRecord(value, life)
            }.toMutableList()
    prior.forEach { (id, record) ->
        if (id !in values) {
            val life = record.life.observe(false, generation)
            if (!life.expired(generation, ttl)) records += id to record.copy(life = life)
        }
    }
    return copy(attributeCatalog = records.sortedWith(compareBy(IdOrder) { it.first }).map { it.second })
}
