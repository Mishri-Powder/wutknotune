package link.roadio.riff.indexer.core.analysis

import com.google.flatbuffers.kotlin.ArrayReadWriteBuffer
import com.google.flatbuffers.kotlin.FlatBufferBuilder
import link.roadio.cuetree.MediaId
import link.roadio.riff.indexer.analysis.fbs.AnalysisGenreOffsetArray
import link.roadio.riff.indexer.analysis.fbs.AnalysisRecordOffsetArray
import link.roadio.riff.indexer.analysis.fbs.AnalysisGainSource as WireSource
import link.roadio.riff.indexer.analysis.fbs.AnalysisGenre as WireGenre
import link.roadio.riff.indexer.analysis.fbs.AnalysisMediaId as WireId
import link.roadio.riff.indexer.analysis.fbs.AnalysisRecord as WireRecord
import link.roadio.riff.indexer.analysis.fbs.AnalysisState as WireState
import link.roadio.riff.indexer.analysis.fbs.CuetreeAnalysis as WireDatabase

object CuetreeAnalysisCodec {
    const val VERSION = "MDRG-DB-V6"
    const val SCHEMA_VERSION = 4
    const val SEMANTIC_VERSION = 6

    fun encode(snapshot: AnalysisSnapshot): ByteArray {
        val value = snapshot.validate()
        val builder = FlatBufferBuilder()
        builder.forceDefaults(true)
        val records =
            AnalysisRecordOffsetArray(value.records.size) { index ->
                val record = value.records[index]
                val id = WireId.createAnalysisMediaId(builder, record.mediaId.hi, record.mediaId.lo)
                val genres =
                    WireRecord.createGenresVector(
                        builder,
                        AnalysisGenreOffsetArray(record.genres.size) { i ->
                            WireGenre.createAnalysisGenre(builder, record.genres[i].genreId, record.genres[i].confidence)
                        },
                    )
                WireRecord.createAnalysisRecord(
                    builder,
                    id,
                    WireState(record.gainState.ordinal.toUByte()),
                    WireSource(record.gainSource.ordinal.toUByte()),
                    record.trackGainQ8,
                    record.trackPeak,
                    record.albumGainQ8,
                    record.albumPeak,
                    WireSource(record.albumSource.ordinal.toUByte()),
                    WireState(record.aiState.ordinal.toUByte()),
                    record.pte,
                    record.pteConfidence,
                    record.arousal,
                    record.arousalConfidence,
                    record.valence,
                    record.valenceConfidence,
                    genres,
                    builder.createString(record.modelRevision.orEmpty()),
                    builder.createString(record.gainRevision.orEmpty()),
                    record.decoderHint,
                )
            }
        val root =
            WireDatabase.createCuetreeAnalysis(
                builder,
                builder.createString(VERSION),
                value.generation,
                WireDatabase.createRecordsVector(builder, records),
            )
        WireDatabase.finishCuetreeAnalysisBuffer(builder, root)
        return builder.sizedByteArray()
    }

    fun decode(bytes: ByteArray): AnalysisSnapshot {
        require(bytes.size >= 12)
        val buffer = ArrayReadWriteBuffer(bytes)
        require(WireDatabase.CuetreeAnalysisBufferHasIdentifier(buffer))
        val root = WireDatabase.asRoot(buffer)
        require(root.databaseVersion == VERSION)
        return AnalysisSnapshot(
            root.generation,
            List(root.recordsLength) { index ->
                val record = requireNotNull(root.records(index))
                val id = requireNotNull(record.mediaId)
                AnalysisRecord(
                    mediaId = MediaId(id.hi, id.lo),
                    gainState = AnalysisState.entries[record.gainState.value.toInt()],
                    gainSource = AnalysisGainSource.entries[record.gainSource.value.toInt()],
                    trackGainQ8 = record.trackGainQ8,
                    trackPeak = record.trackPeak,
                    albumGainQ8 = record.albumGainQ8,
                    albumPeak = record.albumPeak,
                    albumSource = AnalysisGainSource.entries[record.albumSource.value.toInt()],
                    aiState = AnalysisState.entries[record.aiState.value.toInt()],
                    pte = record.pte,
                    pteConfidence = record.pteConfidence,
                    arousal = record.arousal,
                    arousalConfidence = record.arousalConfidence,
                    valence = record.valence,
                    valenceConfidence = record.valenceConfidence,
                    genres =
                        List(record.genresLength) { i ->
                            val genre = requireNotNull(record.genres(i))
                            AnalysisGenre(genre.genreId, genre.confidence)
                        },
                    modelRevision = record.modelRevision?.takeIf(String::isNotEmpty),
                    gainRevision = record.gainRevision?.takeIf(String::isNotEmpty),
                    decoderHint = record.decoderHint,
                )
            },
        ).validate()
    }
}
