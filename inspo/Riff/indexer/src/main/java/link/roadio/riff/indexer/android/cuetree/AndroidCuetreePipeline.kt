package link.roadio.riff.indexer.android.cuetree

import android.content.Context
import android.os.SystemClock
import android.util.Log
import link.roadio.cuetree.AcquisitionGuard
import link.roadio.cuetree.AcquisitionMode
import link.roadio.cuetree.AcquisitionOutcome
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.Indexer
import link.roadio.cuetree.MediaId
import link.roadio.cuetree.ScanCheckpointKind
import link.roadio.cuetree.ScanOptions
import link.roadio.cuetree.ScanProgress
import link.roadio.cuetree.ScanStage
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.SourceAcquisition
import link.roadio.cuetree.UnguardedAcquisition
import link.roadio.riff.indexer.android.process.IndexerProgressReporter
import link.roadio.riff.indexer.android.process.IndexerProgressStage
import link.roadio.riff.indexer.android.process.IndexerSourceStatus
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.indexer.core.pipeline.PipelineProgressRates
import link.roadio.riff.indexer.core.pipeline.PipelinePublicationClock
import link.roadio.riff.indexer.level1.Level1ReplayGainMetadata
import link.roadio.riff.shared.config.AndroidProjectConfiguration

/** Cuetree owns L0/L1 facts; this adapter owns one publication clock through optional L2. */
class AndroidCuetreePipeline(
    private val context: Context,
    private val store: AndroidCuetreeStore,
    private val status: (String) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val safListingIssues: (List<String>, List<String>) -> Unit = { _, _ -> },
    private val acquisitionGuard: AcquisitionGuard = UnguardedAcquisition,
) {
    fun run(signal: CancellationSignal) {
        signal.ensureAvailable()
        val started = clock()
        val progressRates = PipelineProgressRates(clock, started)
        val reporter = IndexerProgressReporter(progressRates)
        val sourceStatus = IndexerSourceStatus(reporter, status)
        val scanGuard =
            object : AcquisitionGuard by acquisitionGuard {
                override fun begin(
                    source: SourceAcquisition,
                    mode: AcquisitionMode,
                    cancellation: Cancellation,
                ) {
                    acquisitionGuard.begin(source, mode, cancellation)
                    if (mode == AcquisitionMode.ISOLATED) sourceStatus.beginSuspect(IndexerProgressStage.ACQUISITION, source.name)
                }

                override fun finish(
                    source: SourceAcquisition,
                    outcome: AcquisitionOutcome,
                ) {
                    acquisitionGuard.finish(source, outcome)
                    sourceStatus.endSuspect(IndexerProgressStage.ACQUISITION, source.name)
                }

                override fun failed(source: SourceAcquisition) {
                    acquisitionGuard.failed(source)
                    sourceStatus.failed(IndexerProgressStage.ACQUISITION, source.name)
                }
            }
        val publicationClock =
            PipelinePublicationClock(clock, started) { at ->
                val period = progressRates.committed(at)
                Log.i("RiffCuetree", "progress_period elapsed_ms=" + period.elapsedMillis + " completed=" + period.completed)
            }
        reporter.observe(IndexerProgressStage.DISCOVERY, 0)
        status(reporter.text(IndexerProgressStage.DISCOVERY))
        Log.i("RiffCuetree", "pipeline_start stage=L0")
        val identity =
            AndroidCuetreeIdentity(context) { detail ->
                status(sourceStatus.text(IndexerProgressStage.ACQUISITION, detail))
            }
        val provider = AndroidSafProvider(context.contentResolver)
        val discovery = provider.discover()
        (acquisitionGuard as? AndroidScanAcquisitionGuard)?.loadRecovery()
        signal.ensureAvailable()
        safListingIssues(discovery.listingBugFolders, discovery.unverifiedFolders)
        val previous = store.read() ?: Snapshot.empty(identity.scheme)
        val ttl =
            AndroidProjectConfiguration
                .load(context)
                .snapshot
                .number("indexer.retentionGenerations")
                .toLong()
        val cancellation = Cancellation { signal.cancelled }
        val genres = AndroidCuetreeGenres.load(context)
        var lastStatusAt = started
        var assessedFactsRevision = -1L
        var last = previous
        val observedGain = mutableMapOf<Pair<MediaId?, String>, Level1ReplayGainMetadata>()
        val observedHints = mutableMapOf<Pair<MediaId?, String>, Long?>()
        val admittedSources = mutableMapOf<Pair<MediaId?, String>, SourceMedia>()
        val complete =
            AndroidCuetreeMetadataReader(context).use { metadata ->
                Indexer(
                    identity,
                    ttl,
                    listOf(provider),
                    metadata = metadata,
                    genres = genres,
                    vocabulary = AndroidCatalogVocabulary.value,
                ).scan(
                    roots = discovery.roots,
                    previous = previous,
                    generation = store.nextLibraryGeneration(),
                    options =
                        ScanOptions(
                            metadataRevision = AndroidCuetreeMetadataRevision.current,
                            pipelineDepth =
                                AndroidProjectConfiguration
                                    .load(context)
                                    .snapshot
                                    .number("indexer.pipelineDepth")
                                    .toInt(),
                        ),
                    cancellation = cancellation,
                    onCheckpoint = { progress, snapshot ->
                        val now = clock()
                        val reportStage = reporter.observe(progress)
                        if (progress.kind == ScanCheckpointKind.STAGE) {
                            Log.i(
                                "RiffCuetree",
                                "stage_start stage=L1 folders=" + progress.completedDirectories + " media=" + progress.totalMedia,
                            )
                        }
                        if (progress.kind == ScanCheckpointKind.STAGE || now - lastStatusAt >= 1_000L) {
                            status(sourceStatus.text(reportStage))
                            lastStatusAt = now
                        }
                        if (publicationClock.due() && progress.factsRevision != assessedFactsRevision) {
                            val candidate = snapshot()
                            assessedFactsRevision = progress.factsRevision
                            if (!candidate.hasSameContentAs(last)) {
                                store.publish(candidate, cancellation)
                                last = candidate
                                publicationClock.committed()
                                Log.i("RiffCuetree", "pipeline_publication stage=" + progress.stage + " generation=" + candidate.generation)
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    },
                    acquisitionGuard = scanGuard,
                    onSource = { node, sourceProvider, handle ->
                        val key = node.parentId to node.name
                        admittedSources[key] = SourceMedia((sourceProvider as AndroidSafProvider).documentUri(handle), node.name)
                        AndroidCuetreeSourceEvidence.gain(node.sourceEvidence)?.let { observedGain[key] = it }
                        if (node.sourceEvidence.isNotEmpty()) observedHints[key] = AndroidCuetreeSourceEvidence.hint(node.sourceEvidence)
                    },
                )
            }
        (acquisitionGuard as? AndroidScanAcquisitionGuard)?.clearStage("L1")
        cancellation.check()
        // AI-INTENT: An L1 boundary does not flush/reset the clock. L2 publishes this candidate first
        // when a common deadline is due, or final pipeline completion flushes it once.
        // Authority: https://wiki.271.pics/Riff/LATEST/immutable-publication#publication-cadence
        var pendingFactual: Snapshot? = complete
        val factualGate = Any()

        fun markFailedL2(physical: link.roadio.cuetree.Node) {
            var newlyFailed = false
            synchronized(factualGate) {
                val baseline = pendingFactual ?: store.read() ?: complete
                val updated =
                    baseline.nodes.map { node ->
                        if (node.kind == link.roadio.cuetree.NodeKind.MEDIA &&
                            node.parentId == physical.parentId &&
                            node.name == physical.name &&
                            node.source?.stamp == physical.source?.stamp
                        ) {
                            if (node.identityFlags and link.roadio.cuetree.Flags.FAILEDL2 == 0) newlyFailed = true
                            node.copy(identityFlags = node.identityFlags or link.roadio.cuetree.Flags.FAILEDL2)
                        } else {
                            node
                        }
                    }
                pendingFactual = baseline.copy(nodes = updated)
            }
            if (newlyFailed) sourceStatus.failed(IndexerProgressStage.ANALYSIS, physical.name)
        }

        fun publishFactual(): Boolean =
            synchronized(factualGate) {
                val candidate = pendingFactual ?: return@synchronized false
                val current = store.read()
                val changed = current == null || !candidate.hasSameContentAs(current)
                if (changed) store.publish(candidate.copy(generation = store.nextLibraryGeneration()), cancellation)
                pendingFactual = null
                changed
            }
        Log.i("RiffCuetree", "stage_start stage=L2")
        val analysisFailure =
            runOptionalCuetreeAnalysis(signal) {
                AndroidCuetreeAnalysisPipeline(context, store, status, elapsed = clock).run(
                    complete,
                    observedGain,
                    observedHints,
                    admittedSources,
                    signal,
                    publicationClock = publicationClock,
                    progressReporter = reporter,
                    publishFactual = ::publishFactual,
                    markFailedSource = ::markFailedL2,
                    acquisitionGuard = acquisitionGuard as? AndroidScanAcquisitionGuard,
                    sourceStatus = sourceStatus,
                )
            }
        analysisFailure?.let { Log.w("RiffCuetree", "optional_analysis_failed type=" + it.javaClass.simpleName) }
        cancellation.check()
        if (publishFactual()) publicationClock.committed()
        store.refreshProjection(cancellation)
        (acquisitionGuard as? AndroidScanAcquisitionGuard)?.clearStage("L2")
        store.read()?.let { snapshot ->
            val albums = snapshot.albums.filter { it.sources.isNotEmpty() }
            Log.i(
                "RiffCuetree",
                "catalog_summary format=" + link.roadio.cuetree.FlatCatalogs.FORMAT_VERSION +
                    " media=" + snapshot.nodes.count { it.mediaId != null } +
                    " duration_known=" +
                    snapshot.nodes.count {
                        it.sourceEvidence[link.roadio.cuetree.SOURCE_DURATION_US]?.singleOrNull()?.toLongOrNull()?.let { value ->
                            value >
                                0
                        } ==
                            true
                    } +
                    " albums=" + albums.size +
                    " ordinary=" + albums.count { it.kind == link.roadio.cuetree.AlbumKind.ORDINARY } +
                    " explicit=" + albums.count { it.kind == link.roadio.cuetree.AlbumKind.EXPLICIT } +
                    " artist_singles=" + albums.count { it.kind == link.roadio.cuetree.AlbumKind.ARTIST_SINGLES } +
                    " global_singles=" + albums.filter { it.kind == link.roadio.cuetree.AlbumKind.SINGLES }.sumOf { it.sources.size },
            )
        }
        Log.i("RiffCuetree", "pipeline_complete elapsed_ms=" + (clock() - started))
        status("")
    }
}

internal fun IndexerProgressReporter.observe(progress: ScanProgress): IndexerProgressStage {
    val stage =
        if (progress.stage == ScanStage.DISCOVERY) {
            observe(IndexerProgressStage.DISCOVERY, progress.completedDirectories)
            IndexerProgressStage.DISCOVERY
        } else {
            observe(IndexerProgressStage.ACQUISITION, progress.completedMedia, progress.totalMedia)
            IndexerProgressStage.ACQUISITION
        }
    return stage
}

/** Optional analysis never invalidates factual acquisition; cancellation still fences the whole pipeline. */
internal fun runOptionalCuetreeAnalysis(
    signal: CancellationSignal,
    action: () -> Unit,
): Exception? =
    try {
        signal.ensureAvailable()
        action()
        signal.ensureAvailable()
        null
    } catch (error: Exception) {
        signal.ensureAvailable()
        if (error is ScanJournalUnavailable) throw error
        error
    }
