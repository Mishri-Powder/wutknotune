// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import link.roadio.cuegrain.repair.MojibakeEncoding
import link.roadio.cuegrain.repair.MojibakeEncodingDetection
import link.roadio.cuegrain.repair.MojibakeEncodingDetector
import link.roadio.cuegrain.repair.MojibakeRepair

internal fun repairSource(
    source: Source,
    detector: EncodingDetector?,
): Source {
    if (detector == null) return source
    val adapter =
        object : MojibakeEncodingDetector {
            override val revision = "cuegrain-detector"

            override fun detect(input: ByteArray): MojibakeEncodingDetection {
                val d = detector.detect(input)
                val encoding =
                    when (d.charset?.lowercase()) {
                        "utf-8", "utf8" -> MojibakeEncoding.UTF8
                        "windows-1251", "cp1251" -> MojibakeEncoding.WINDOWS_1251
                        else -> MojibakeEncoding.OTHER
                    }
                return MojibakeEncodingDetection(encoding, d.isReliable, d.bytesConsumed)
            }
        }

    fun eligible(t: Tag) =
        Metadata.normalized(listOf(t)).keys.any {
            it in
                setOf("title", "album", "artist", "albumArtist", "performer", "genre", "composer", "comment")
        }
    val fields =
        source.tags.filter(::eligible) + source.entries.flatMap { it.tags.filter(::eligible) } +
            source.tables.flatMap { it.tags.filter(::eligible) }
    val repairs = MojibakeRepair.repairTrack(fields.map { it.value }, adapter)
    var index = 0

    fun tags(values: List<Tag>) = values.map { if (eligible(it)) it.copy(value = repairs[index++].value) else it }
    val sourceTags = tags(source.tags)
    val entries = source.entries.map { it.copy(tags = tags(it.tags)) }
    val tables = source.tables.map { it.copy(tags = tags(it.tags)) }
    return source.copy(tags = sourceTags, entries = entries, tables = tables)
}
