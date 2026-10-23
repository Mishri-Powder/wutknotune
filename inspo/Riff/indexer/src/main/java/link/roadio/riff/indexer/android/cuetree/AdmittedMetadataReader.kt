package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuegrain.MediaInput
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.MetadataReader
import link.roadio.cuetree.Observation
import link.roadio.cuetree.SOURCE_CONTENT_KIND
import link.roadio.cuetree.SOURCE_DURATION_US
import link.roadio.riff.indexer.android.level1.AdmittedAudio
import link.roadio.riff.indexer.android.level1.AndroidReplayGainMetadata

/**
 * CUE describes other sources and cannot be admitted as playable audio.
 * Contract: https://wiki.271.pics/Riff/LATEST/indexer-media-admission#device-derived-audio-admission
 */
internal class AdmittedMetadataReader(
    private val metadata: MetadataReader,
    private val admit: (MediaInput, Cancellation) -> AdmittedAudio,
) : MetadataReader {
    override fun read(
        input: MediaInput,
        hint: String,
        cancellation: Cancellation,
    ): Observation {
        cancellation.check()
        if (hint == "cue") return withFragmentGain(metadata.read(input, hint, cancellation))
        val admitted = admit(input, cancellation)
        cancellation.check()
        val observation = withFragmentGain(metadata.read(input, hint, cancellation))
        val gain = AndroidReplayGainMetadata.select(observation.gainSources)
        val duration =
            admitted.durationUs
                ?.takeIf { it > 0 }
                ?.let { mapOf(SOURCE_DURATION_US to listOf(it.toString())) }
                .orEmpty()
        val content =
            if ("106833" in
                observation.attributes["genreId"].orEmpty()
            ) {
                mapOf(SOURCE_CONTENT_KIND to listOf("book"))
            } else {
                emptyMap()
            }
        return observation.copy(
            sourceEvidence =
                observation.sourceEvidence + AndroidCuetreeSourceEvidence.encode(gain, admitted.decoderHint) + duration + content,
        )
    }

    private fun withFragmentGain(observation: Observation): Observation =
        observation.copy(
            tables =
                observation.tables.map { table ->
                    table.copy(
                        sections =
                            table.sections.mapIndexed { ordinal, section ->
                                val gain =
                                    AndroidReplayGainMetadata.select(
                                        observation.sectionGainSources[table.key]?.getOrNull(ordinal).orEmpty(),
                                    )
                                section.copy(attributes = section.attributes + AndroidCuetreeSourceEvidence.encode(gain, null))
                            },
                    )
                },
        )
}
