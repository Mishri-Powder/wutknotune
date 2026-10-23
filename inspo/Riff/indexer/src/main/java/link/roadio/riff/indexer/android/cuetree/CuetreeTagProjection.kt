package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuegrain.repair.MojibakeEncodingDetector
import link.roadio.cuegrain.repair.MojibakeRepair
import link.roadio.cuetree.Observation
import link.roadio.riff.indexer.core.tags.genre.GenreParser
import link.roadio.riff.indexer.core.tags.list.TagValueListParser
import link.roadio.riff.indexer.level1.GenreMatcher

/** Applies Riff's one track-wide repair and shared list parser before Cuetree admits metadata. */
internal class CuetreeTagProjection(
    private val matcher: GenreMatcher,
    private val detector: MojibakeEncodingDetector,
) {
    fun project(observation: Observation): Observation {
        val maps =
            buildList {
                add(observation.attributes)
                observation.tables.forEach { table ->
                    add(table.attributes)
                    table.sections.forEach { section -> add(section.attributes) }
                }
            }
        val slots =
            maps.flatMapIndexed { mapIndex, attributes ->
                TEXT_FIELDS.flatMap { key ->
                    attributes[key].orEmpty().mapIndexedNotNull { valueIndex, value ->
                        value.takeIf(String::isNotBlank)?.let { TextSlot(mapIndex, key, valueIndex, it) }
                    }
                }
            }
        val evidence = slots.map(TextSlot::value) + auxiliaryChapterText(observation)
        val repaired = MojibakeRepair.repairTrack(evidence, detector)
        val replacements =
            slots.indices.associate { index ->
                val slot = slots[index]
                Triple(slot.mapIndex, slot.key, slot.valueIndex) to repaired[index].value
            }
        val processed =
            maps.mapIndexed { mapIndex, attributes ->
                val values =
                    attributes
                        .mapValues { (key, originals) ->
                            originals.mapIndexed { valueIndex, original ->
                                replacements[Triple(mapIndex, key, valueIndex)] ?: original
                            }
                        }.toMutableMap()
                for (key in listOf("artist", "albumArtist")) {
                    if (key in values) {
                        val components = TagValueListParser.splitExplicit(values.getValue(key), TagValueListParser.DelimiterPolicy.ARTIST)
                        if (components.isEmpty()) values.remove(key) else values[key] = components
                    }
                }
                if ("genre" in values) {
                    val components = GenreParser.parse(values.getValue("genre"), matcher)
                    values.remove("genre")
                    values.remove("genreId")
                    if (components.isNotEmpty()) {
                        values["genre"] = components.map { it.text }
                        val ids = components.flatMap { it.genreIds }.distinct().sorted()
                        if (ids.isNotEmpty()) values["genreId"] = ids.map(Long::toString)
                    }
                }
                values
            }
        var next = 1
        return observation.copy(
            attributes = processed.first(),
            tables =
                observation.tables.map { table ->
                    val tableAttributes = processed[next++]
                    table.copy(
                        attributes = tableAttributes,
                        sections = table.sections.map { section -> section.copy(attributes = processed[next++]) },
                    )
                },
        )
    }

    private fun auxiliaryChapterText(observation: Observation): List<String> {
        val admitted = observation.tables.mapTo(mutableSetOf()) { it.key }
        return observation.gainSources
            .withIndex()
            .flatMap { (index, source) ->
                if ("${source.kind}:$index" !in admitted) return@flatMap emptyList()
                source.entries.flatMap { entry ->
                    entry.tags.filter { it.key == "TIT3" }.map { it.value } +
                        entry.artwork.mapNotNull { it.description }
                } +
                    source.tables.sortedBy { it.id }.flatMap { table ->
                        table.tags.filter { it.key == "TIT2" || it.key == "TITLE" }.map { it.value }
                    }
            }.filter(String::isNotBlank)
    }

    private data class TextSlot(
        val mapIndex: Int,
        val key: String,
        val valueIndex: Int,
        val value: String,
    )

    private companion object {
        val TEXT_FIELDS = listOf("title", "album", "artist", "albumArtist", "genre")
    }
}
