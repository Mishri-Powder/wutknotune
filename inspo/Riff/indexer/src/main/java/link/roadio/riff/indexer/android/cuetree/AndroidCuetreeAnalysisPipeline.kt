package link.roadio.riff.indexer.android.cuetree

import android.content.Context
import android.os.SystemClock
import android.util.Log
import link.roadio.cuetree.AcquisitionMode
import link.roadio.cuetree.Flags
import link.roadio.cuetree.MediaId
import link.roadio.cuetree.Node
import link.roadio.cuetree.NodeKind
import link.roadio.cuetree.Snapshot
import link.roadio.riff.indexer.android.level0.isStandardAudiobooksTree
import link.roadio.riff.indexer.android.level2.AndroidAudioExcerptDecoder
import link.roadio.riff.indexer.android.level2.DecodedLevel2Audio
import link.roadio.riff.indexer.android.level2.Level2AudioFeatures
import link.roadio.riff.indexer.android.level2.Level2ModelPackage
import link.roadio.riff.indexer.android.level2.fullLoudnessAnalysisStatus
import link.roadio.riff.indexer.android.process.IndexerProgressReporter
import link.roadio.riff.indexer.android.process.IndexerProgressStage
import link.roadio.riff.indexer.android.process.IndexerSourceStatus
import link.roadio.riff.indexer.android.replaygain.AndroidFullTrackLoudnessAnalyzer
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.analysis.AiSourceFeatures
import link.roadio.riff.indexer.core.analysis.AnalysisGainSource
import link.roadio.riff.indexer.core.analysis.AnalysisGenre
import link.roadio.riff.indexer.core.analysis.AnalysisRecord
import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot
import link.roadio.riff.indexer.core.analysis.AnalysisState
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.indexer.core.pipeline.BoundedPublicationWorker
import link.roadio.riff.indexer.core.pipeline.PipelinePublicationClock
import link.roadio.riff.indexer.core.pipeline.processBoundedPipeline
import link.roadio.riff.indexer.core.replaygain.ReplayGainMath
import link.roadio.riff.indexer.level1.Level1ReplayGainMetadata
import link.roadio.riff.shared.config.AndroidProjectConfiguration
import link.roadio.riff.storage.hash.nativeXxh3
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** One independent MDRG stream from Cuetree occurrences, including individual FILF spans. */
internal class AndroidCuetreeAnalysisPipeline(
    private val context: Context,
    private val store: AndroidCuetreeStore,
    private val status: (String) -> Unit,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
) {
    fun run(
        snapshot: Snapshot,
        observedGain: Map<Pair<MediaId?, String>, Level1ReplayGainMetadata>,
        observedHints: Map<Pair<MediaId?, String>, Long?>,
        admittedSources: Map<Pair<MediaId?, String>, SourceMedia>,
        cancellation: CancellationSignal,
        publicationClock: PipelinePublicationClock,
        progressReporter: IndexerProgressReporter,
        publishFactual: () -> Boolean,
        markFailedSource: (Node) -> Unit = {},
        acquisitionGuard: AndroidScanAcquisitionGuard? = null,
        sourceStatus: IndexerSourceStatus? = null,
    ) {
        val previous = store.readAnalysis()
        val oldRecords = previous?.records.orEmpty()
        val oldById = oldRecords.associateBy(AnalysisRecord::mediaId)
        val indices =
            snapshot.nodes.indices
                .filter { snapshot.nodes[it].kind == NodeKind.MEDIA || snapshot.nodes[it].kind == NodeKind.FRAGMENT }
                .groupBy { snapshot.nodes[it].id }
        val projected =
            CuetreePlayerProjection.build(
                snapshot,
                AndroidCuetreeGenres.load(context),
                isAudiobooksRoot = ::isStandardAudiobooksTree,
            )
        val books =
            projected.nodes
                .filter {
                    it.attributes["automaticBook"]?.singleOrNull() == "1"
                }.mapTo(mutableSetOf()) { it.id }
        projected.albums
            .filter { it.attributes["automaticBook"]?.singleOrNull() == "1" }
            .flatMapTo(books) { it.tracks }

        fun observe(
            recordBefore: AnalysisRecord,
            index: Int,
            base: Int,
        ): AnalysisRecord {
            val physical = snapshot.nodes[base]
            val physicalKey = physical.parentId to physical.name
            val observed =
                reconcileAnalysisSource(
                    recordBefore,
                    snapshot.nodes[index].kind,
                    observedGain[physicalKey],
                    physicalKey in observedGain,
                    observedHints[physicalKey],
                    GAIN_REVISION,
                )
            return if (snapshot.nodes[index].kind == NodeKind.FRAGMENT) {
                reconcileFragmentGain(observed, AndroidCuetreeSourceEvidence.gain(snapshot.nodes[index].attributes), GAIN_REVISION)
            } else {
                observed
            }
        }

        val failedPhysical = ConcurrentHashMap.newKeySet<Pair<MediaId?, String>>()

        fun sourceFailureState(
            error: Throwable?,
            alternatives: List<Pair<Int, Int>>,
        ): AnalysisState =
            if (alternatives.any { (_, base) ->
                    val physical = snapshot.nodes[base]
                    (physical.parentId to physical.name) !in failedPhysical &&
                        physical.identityFlags and Flags.FAILEDL2 == 0
                }
            ) {
                AnalysisState.RETRYABLE_FAILURE
            } else {
                errorState(error)
            }

        fun candidates(occurrences: List<Int>): List<Pair<Int, Int>> =
            occurrences.filter { snapshot.nodes[it].playable }.flatMap { index ->
                val node = snapshot.nodes[index]
                (if (node.kind == NodeKind.FRAGMENT) snapshot.baseSources(node) else listOf(index))
                    .filter { base ->
                        val physical = snapshot.nodes[base]
                        val key = physical.parentId to physical.name
                        if (!physical.playable ||
                            physical.identityFlags and Flags.FAILEDL2 != 0 ||
                            key in failedPhysical
                        ) {
                            return@filter false
                        }
                        val document = admittedSources[key]?.locator ?: return@filter false
                        if (acquisitionGuard?.modeForL2(physical, document, node.span) == AcquisitionMode.QUARANTINE) {
                            failedPhysical += key
                            markFailedSource(physical)
                            return@filter false
                        }
                        true
                    }.map { base -> index to base }
            }

        val sourceFeatures = mutableMapOf<MediaId, AiSourceFeatures>()
        val aiRevisions = mutableMapOf<MediaId, String>()
        val genreResolver = AndroidCuetreeGenres.load(context)
        val records = linkedMapOf<MediaId, AnalysisRecord>()
        val work = mutableListOf<Pair<MediaId, List<Int>>>()
        indices.entries.sortedWith(compareBy({ it.key.hi }, { it.key.lo })).forEach { (id, occurrences) ->
            val alias = MediaId(id.hi)
            val shortAnchor =
                id.lo != null &&
                    occurrences.any {
                        snapshot.nodes[it].identityFlags and Flags.SHRT != 0
                    }
            val prior = oldById[id] ?: if (shortAnchor) oldById[alias] else null
            val key = prior?.mediaId ?: id
            val retained = prior ?: AnalysisRecord(key)
            records[key] =
                if (occurrences.any { snapshot.nodes[it].id in books }) {
                    retained.withoutAi(AnalysisState.PENDING, null)
                } else {
                    retained
                }
            val selected = candidates(occurrences).firstOrNull()
            if (selected != null) {
                records[key] = observe(records.getValue(key), selected.first, selected.second)
                val source =
                    if (id in
                        books
                    ) {
                        AiSourceFeatures()
                    } else {
                        AiSourceFeatures.read(snapshot.nodes[selected.first].attributes, genreResolver)
                    }
                val revision = source.revision(Level2ModelPackage.MODEL_VERSION, ::nativeXxh3)
                sourceFeatures[key] = source
                aiRevisions[key] = revision
                if (id !in books) records[key] = source.reconcile(records.getValue(key), revision)
                val record = records.getValue(key)
                if (record.needsGain(GAIN_REVISION) || (id !in books && record.needsAi(aiRevisions.getValue(key)))) {
                    work += key to occurrences
                }
            }
        }

        fun hintFor(job: Pair<MediaId, List<Int>>): Long =
            job.second
                .asSequence()
                .flatMap { index ->
                    val node = snapshot.nodes[index]
                    (if (node.kind == NodeKind.FRAGMENT) snapshot.baseSources(node) else listOf(index)).asSequence()
                }.mapNotNull { base ->
                    val node = snapshot.nodes[base]
                    observedHints[node.parentId to node.name]
                }.firstOrNull() ?: records.getValue(job.first).decoderHint ?: Long.MAX_VALUE

        fun directoryFor(job: Pair<MediaId, List<Int>>): MediaId? =
            job.second.firstNotNullOfOrNull { index ->
                val node = snapshot.nodes[index]
                (if (node.kind == NodeKind.FRAGMENT) snapshot.baseSources(node) else listOf(index))
                    .firstOrNull()
                    ?.let { snapshot.nodes[it].parentId }
            }

        val randomCohorts =
            work.groupBy(::hintFor).values.shuffled().flatMap { cohort ->
                cohort
                    .groupBy(::directoryFor)
                    .values
                    .shuffled()
                    .flatMap { directory -> directory.shuffled() }
            }
        work.clear()
        work.addAll(randomCohorts)
        val firstGeneration = store.nextAnalysisGeneration()
        val firstStarted = elapsed()
        var publications = 0
        var lastPublished = oldRecords
        var publisher: BoundedPublicationWorker? = null
        val publicationMillis = AtomicLong()

        fun publish(force: Boolean) {
            if (!force && !publicationClock.due()) return
            if (publisher?.available(force) == false) return
            val current = records.values.sortedWith(compareBy({ it.mediaId.hi }, { it.mediaId.lo }))
            if (current == lastPublished) {
                if (publishFactual()) publicationClock.committed()
                return
            }
            cancellation.ensureAvailable()
            val candidate = AnalysisSnapshot(Math.addExact(firstGeneration, publications.toLong()), current)
            val write = {
                val started = elapsed()
                try {
                    publishFactual()
                    store.publishAnalysis(candidate, link.roadio.cuetree.Cancellation { cancellation.cancelled })
                    publicationClock.committed()
                } finally {
                    publicationMillis.addAndGet(elapsed() - started)
                }
            }
            publisher?.submit(write) ?: write()
            lastPublished = current
            publications++
        }

        val fullAnalysisDetail = AtomicReference<String?>()

        fun progress(completed: Int) {
            progressReporter.observe(IndexerProgressStage.ANALYSIS, completed.toLong(), work.size.toLong())
            status(
                sourceStatus?.text(IndexerProgressStage.ANALYSIS, fullAnalysisDetail.get())
                    ?: progressReporter.text(IndexerProgressStage.ANALYSIS, fullAnalysisDetail.get()),
            )
        }

        if (work.isEmpty()) {
            publish(true)
            return
        }
        val needsAi =
            work.any { (key, occurrences) ->
                occurrences.none { snapshot.nodes[it].id in books } && records.getValue(key).needsAi(aiRevisions.getValue(key))
            }
        var model =
            if (needsAi) {
                runCatching { Level2ModelPackage.load(context) }
                    .onFailure { Log.w("RiffAnalysis", "AI model unavailable", it) }
                    .getOrNull()
            } else {
                null
            }
        val analyzer = AndroidFullTrackLoudnessAnalyzer(context)
        val workItems =
            work.map { (key, occurrences) ->
                val record = records.getValue(key)
                val eligible =
                    candidates(occurrences).sortedBy { (candidate, base) ->
                        val physical = snapshot.nodes[base]
                        val source = admittedSources[physical.parentId to physical.name]
                        if (source != null &&
                            acquisitionGuard?.modeForL2(physical, source.locator, snapshot.nodes[candidate].span) ==
                            AcquisitionMode.ISOLATED
                        ) {
                            0
                        } else {
                            1
                        }
                    }
                val isolated =
                    eligible.any { (candidate, base) ->
                        val physical = snapshot.nodes[base]
                        val source = admittedSources[physical.parentId to physical.name]
                        source != null &&
                            acquisitionGuard?.modeForL2(physical, source.locator, snapshot.nodes[candidate].span) ==
                            AcquisitionMode.ISOLATED
                    }
                val item = eligible.firstOrNull()?.let { snapshot.nodes[it.first] }
                AnalysisWork(
                    key = key,
                    record = record,
                    candidates = eligible,
                    isolated = isolated,
                    sourceFeatures = sourceFeatures.getValue(key),
                    aiRevision = aiRevisions.getValue(key),
                    gainNeeded = record.needsGain(GAIN_REVISION),
                    aiNeeded =
                        item != null &&
                            item.id !in books &&
                            record.needsAi(aiRevisions.getValue(key)) &&
                            model != null,
                )
            }
        var recordsCompleted = 0
        var preparedMillis = 0L
        var decodedMillis = 0L
        var decodedCount = 0
        var frontendMillis = 0L
        var inferenceMillis = 0L
        var aiCount = 0

        fun logTiming() {
            if (recordsCompleted == 0) return
            Log.i(
                "RiffAnalysis",
                "analysis_timing_summary completed=$recordsCompleted " +
                    "mean_prepare_ms=${preparedMillis / recordsCompleted} " +
                    "decoded=$decodedCount mean_decode_ms=${decodedMillis / decodedCount.coerceAtLeast(1)} " +
                    "ai=$aiCount mean_frontend_ms=${frontendMillis / aiCount.coerceAtLeast(1)} " +
                    "mean_inference_ms=${inferenceMillis / aiCount.coerceAtLeast(1)} " +
                    "publication_ms=${publicationMillis.get()} elapsed_ms=${elapsed() - firstStarted}",
            )
        }

        publisher = BoundedPublicationWorker("RiffCuetreePublish")
        try {
            progress(0)

            fun runBatch(
                items: List<AnalysisWork>,
                depth: Int,
            ) {
                if (items.isEmpty()) return
                if (items.any(AnalysisWork::aiNeeded) && model == null) model = Level2ModelPackage.load(context)
                try {
                    AndroidAudioExcerptDecoder(context).use { decoder ->
                        processBoundedPipeline(
                            workset = items,
                            maxInFlight = depth,
                            cancellation = cancellation,
                            producerThreadName = "RiffCuetreeDecode",
                            prepare = { item, producerCancellation ->
                                val preparedStarted = elapsed()
                                var record = item.record
                                var failure: Throwable? = null
                                var accepted: DecodeAttempt? = null
                                if (item.gainNeeded || item.aiNeeded) {
                                    for ((candidate, baseCandidate) in item.candidates) {
                                        producerCancellation.ensureAvailable()
                                        val node = snapshot.nodes[candidate]
                                        val physical = snapshot.nodes[baseCandidate]
                                        val physicalKey = physical.parentId to physical.name
                                        if (physicalKey in failedPhysical) continue
                                        val source = admittedSources[physicalKey] ?: continue
                                        val hint =
                                            observedHints[physical.parentId to physical.name]
                                                ?: record.decoderHint
                                                ?: continue
                                        record = record.copy(decoderHint = hint)
                                        val mode =
                                            acquisitionGuard?.modeForL2(physical, source.locator, node.span) ?: AcquisitionMode.NORMAL
                                        if (mode == AcquisitionMode.QUARANTINE) {
                                            failedPhysical += physicalKey
                                            markFailedSource(physical)
                                            continue
                                        }
                                        val token = acquisitionGuard?.beginL2(physical, source.locator, node.span, mode)
                                        if (mode ==
                                            AcquisitionMode.ISOLATED
                                        ) {
                                            sourceStatus?.beginSuspect(IndexerProgressStage.ANALYSIS, physical.name)
                                        }
                                        try {
                                            val decoded =
                                                decoder.decode(
                                                    source,
                                                    hint,
                                                    item.gainNeeded,
                                                    item.aiNeeded,
                                                    producerCancellation,
                                                    node.span,
                                                )
                                            var fullLoudness: Double? = null
                                            var fullFailure: Throwable? = null
                                            if (item.gainNeeded && decoded.integratedLufs == null && node.id !in books) {
                                                val detail = fullLoudnessAnalysisStatus(source.relativePath)
                                                fullAnalysisDetail.set(detail)
                                                status(
                                                    sourceStatus?.text(IndexerProgressStage.ANALYSIS, detail)
                                                        ?: progressReporter.text(IndexerProgressStage.ANALYSIS, detail),
                                                )
                                                try {
                                                    fullLoudness = analyzer.analyze(source, producerCancellation, node.span)
                                                } catch (error: Throwable) {
                                                    producerCancellation.ensureAvailable()
                                                    if (error is CancellationException || error is ScanJournalUnavailable) throw error
                                                    fullFailure = error
                                                } finally {
                                                    fullAnalysisDetail.set(null)
                                                    status(
                                                        sourceStatus?.text(IndexerProgressStage.ANALYSIS)
                                                            ?: progressReporter.text(IndexerProgressStage.ANALYSIS),
                                                    )
                                                }
                                            }
                                            accepted =
                                                DecodeAttempt(
                                                    work = item,
                                                    record = record,
                                                    decoded = decoded,
                                                    fullTrackLufs = fullLoudness,
                                                    fullTrackFailure = fullFailure,
                                                    physical = physical,
                                                    token = token,
                                                )
                                            break
                                        } catch (error: Throwable) {
                                            producerCancellation.ensureAvailable()
                                            if (error is CancellationException || error is ScanJournalUnavailable) throw error
                                            token?.let { acquisitionGuard.finishL2(it) }
                                            sourceStatus?.endSuspect(IndexerProgressStage.ANALYSIS, physical.name)
                                            failedPhysical += physicalKey
                                            markFailedSource(physical)
                                            failure = error
                                        }
                                    }
                                }
                                (accepted ?: DecodeAttempt(work = item, record = record, failure = failure))
                                    .copy(prepareMillis = elapsed() - preparedStarted)
                            },
                            consume = { attempt ->
                                try {
                                    cancellation.ensureAvailable()
                                    val item = attempt.work
                                    var record = attempt.record
                                    val decoded = attempt.decoded
                                    preparedMillis += attempt.prepareMillis
                                    if (decoded != null) {
                                        decodedCount++
                                        decodedMillis += decoded.decodeMillis
                                        if (item.gainNeeded) {
                                            val measurement =
                                                runCatching {
                                                    (decoded.integratedLufs ?: attempt.fullTrackLufs)
                                                        ?.let { ReplayGainMath.q8(-18.0 - it) }
                                                }
                                            record =
                                                when {
                                                    attempt.fullTrackFailure != null || measurement.isFailure -> {
                                                        cancellation.ensureAvailable()
                                                        attempt.physical?.let {
                                                            failedPhysical += it.parentId to it.name
                                                            markFailedSource(it)
                                                        }
                                                        record.copy(
                                                            gainState =
                                                                sourceFailureState(
                                                                    attempt.fullTrackFailure ?: measurement.exceptionOrNull(),
                                                                    item.candidates,
                                                                ),
                                                            gainSource = AnalysisGainSource.NONE,
                                                            trackGainQ8 = null,
                                                            trackPeak = null,
                                                            gainRevision = GAIN_REVISION,
                                                        )
                                                    }

                                                    measurement.getOrNull() != null -> {
                                                        record.copy(
                                                            gainState = AnalysisState.AVAILABLE,
                                                            gainSource = AnalysisGainSource.MEASURED,
                                                            trackGainQ8 = requireNotNull(measurement.getOrNull()),
                                                            trackPeak = null,
                                                            gainRevision = GAIN_REVISION,
                                                        )
                                                    }

                                                    else -> {
                                                        record.copy(
                                                            gainState = AnalysisState.COMPLETED_NO_RESULT,
                                                            gainSource = AnalysisGainSource.NONE,
                                                            trackGainQ8 = null,
                                                            trackPeak = null,
                                                            gainRevision = GAIN_REVISION,
                                                        )
                                                    }
                                                }
                                        }
                                        if (item.aiNeeded) {
                                            if (!decoded.aiUsable) {
                                                record =
                                                    item.sourceFeatures.finish(
                                                        record.withoutAi(AnalysisState.COMPLETED_NO_RESULT, item.aiRevision),
                                                        AnalysisState.COMPLETED_NO_RESULT,
                                                        item.aiRevision,
                                                    )
                                            } else {
                                                var itemFrontendMillis = 0L
                                                var itemInferenceMillis = 0L
                                                val inference =
                                                    runCatching {
                                                        val frontendStarted = elapsed()
                                                        val features = Level2AudioFeatures.computeLogMel(decoded.pcm)
                                                        itemFrontendMillis = elapsed() - frontendStarted
                                                        val raw = requireNotNull(model).infer(features)
                                                        itemInferenceMillis = raw.inferenceMillis
                                                        requireNotNull(model)
                                                            .postprocess(
                                                                link.roadio.riff.indexer.core.media.MediaId(
                                                                    item.key.hi,
                                                                    item.key.lo ?: 0L,
                                                                    item.key.lo != null,
                                                                ),
                                                                raw,
                                                            ).record
                                                    }
                                                if (itemInferenceMillis > 0L) {
                                                    aiCount++
                                                    frontendMillis += itemFrontendMillis
                                                    inferenceMillis += itemInferenceMillis
                                                }
                                                record =
                                                    inference.fold(
                                                        onSuccess = { result ->
                                                            item.sourceFeatures.finish(
                                                                record.copy(
                                                                    aiState = AnalysisState.AVAILABLE,
                                                                    pte = result.pte,
                                                                    pteConfidence = result.pteConfidence,
                                                                    arousal = result.arousal,
                                                                    arousalConfidence = result.arousalConfidence,
                                                                    valence = result.valence,
                                                                    valenceConfidence = result.valenceConfidence,
                                                                    genres =
                                                                        result.genres
                                                                            .map {
                                                                                AnalysisGenre(it.genreId, it.confidence)
                                                                            }.sortedBy(AnalysisGenre::genreId),
                                                                    modelRevision = item.aiRevision,
                                                                ),
                                                                AnalysisState.AVAILABLE,
                                                                item.aiRevision,
                                                            )
                                                        },
                                                        onFailure = { error ->
                                                            cancellation.ensureAvailable()
                                                            if (error is CancellationException ||
                                                                error is ScanJournalUnavailable
                                                            ) {
                                                                throw error
                                                            }
                                                            attempt.physical?.let {
                                                                failedPhysical += it.parentId to it.name
                                                                markFailedSource(it)
                                                            }
                                                            val failureState = sourceFailureState(error, item.candidates)
                                                            item.sourceFeatures.finish(
                                                                record.withoutAi(failureState, item.aiRevision),
                                                                failureState,
                                                                item.aiRevision,
                                                            )
                                                        },
                                                    )
                                            }
                                        }
                                    } else if (item.gainNeeded || item.aiNeeded) {
                                        val state = errorState(attempt.failure)
                                        if (item.gainNeeded) {
                                            record =
                                                record.copy(
                                                    gainState = state,
                                                    gainRevision = GAIN_REVISION,
                                                    gainSource = AnalysisGainSource.NONE,
                                                    trackGainQ8 = null,
                                                    trackPeak = null,
                                                )
                                        }
                                        if (item.aiNeeded) {
                                            record =
                                                item.sourceFeatures.finish(record.withoutAi(state, item.aiRevision), state, item.aiRevision)
                                        }
                                        Log.w(
                                            "RiffAnalysis",
                                            "analysis_failed type=" +
                                                (attempt.failure?.javaClass?.simpleName ?: "SOURCE_UNAVAILABLE"),
                                        )
                                    }
                                    records[item.key] = record.validate()
                                    progress(++recordsCompleted)
                                    publish(false)
                                    if (recordsCompleted % 50 == 0) logTiming()
                                } finally {
                                    attempt.token?.let { acquisitionGuard?.finishL2(it) }
                                    attempt.physical?.let { sourceStatus?.endSuspect(IndexerProgressStage.ANALYSIS, it.name) }
                                }
                            },
                        )
                    }
                } finally {
                    model?.close()
                    model = null
                }
            }
            workItems.filter(AnalysisWork::isolated).forEach { item ->
                publisher.await()
                runBatch(listOf(item), 1)
                publisher.await()
            }
            runBatch(
                workItems.filterNot(AnalysisWork::isolated),
                AndroidProjectConfiguration
                    .load(context)
                    .snapshot
                    .number("indexer.pipelineDepth")
                    .toInt(),
            )
            publish(true)
            publisher.await()
            logTiming()
            status("")
        } finally {
            model?.close()
            publisher.close()
        }
    }

    private fun errorState(error: Throwable?): AnalysisState =
        if (error == null ||
            error is IOException ||
            error is SecurityException ||
            (
                error is android.media.MediaCodec.CodecException &&
                    (error.isTransient || error.isRecoverable)
            )
        ) {
            AnalysisState.RETRYABLE_FAILURE
        } else {
            AnalysisState.DETERMINISTIC_FAILURE
        }

    private data class AnalysisWork(
        val key: MediaId,
        val record: AnalysisRecord,
        val candidates: List<Pair<Int, Int>>,
        val isolated: Boolean,
        val sourceFeatures: AiSourceFeatures,
        val aiRevision: String,
        val gainNeeded: Boolean,
        val aiNeeded: Boolean,
    )

    private data class DecodeAttempt(
        val work: AnalysisWork,
        val record: AnalysisRecord,
        val decoded: DecodedLevel2Audio? = null,
        val fullTrackLufs: Double? = null,
        val fullTrackFailure: Throwable? = null,
        val failure: Throwable? = null,
        val physical: Node? = null,
        val token: AndroidScanAcquisitionGuard.StageToken? = null,
        val prepareMillis: Long = 0L,
    )

    private companion object {
        const val GAIN_REVISION =
            "REPLAYGAIN-METADATA-V5/CUEGRAIN-0.1.0/LIBEBUR128-1.2.6-67b33ab-I-W20-R2-V4/PCM-AU-TIMELINE-V1/CUETREE-FILF-V2"
    }
}
