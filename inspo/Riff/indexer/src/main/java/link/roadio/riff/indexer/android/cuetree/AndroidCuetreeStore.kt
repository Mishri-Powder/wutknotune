package link.roadio.riff.indexer.android.cuetree

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.FlatCatalogs
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.validate
import link.roadio.riff.indexer.android.ipc.ValidatedAndroidArtifact
import link.roadio.riff.indexer.android.level0.isStandardAudiobooksTree
import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot
import link.roadio.riff.indexer.core.ipc.IndexerArtifactKind
import link.roadio.riff.indexer.core.ipc.IndexerArtifactMetadata
import link.roadio.riff.shared.config.AndroidProjectConfiguration
import link.roadio.riff.storage.hash.nativeXxh3
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Publishes and exposes only one validated four-file Cuetree cohort. */
class AndroidCuetreeStore(
    directory: File,
    context: Context,
) {
    private val root = directory.toPath()

    private val playerRoot = root.resolve("player")
    private val store =
        CuetreePublicationStore(
            root,
            AndroidProjectConfiguration
                .load(context)
                .snapshot
                .number("indexer.retentionGenerations")
                .toLong(),
        )
    private val analysisStore = AndroidCuetreeAnalysisStore(root.resolve("analysis"))
    private val genres = AndroidCuetreeGenres.load(context)
    private val displayGenres = AndroidCuetreeGenres.displayMap(context)
    private val identityScheme = AndroidCuetreeIdentity(context).scheme
    private val metadataRevision = AndroidCuetreeMetadataRevision.current
    private var current: Cohort? = null

    var publicationListener: ((IndexerArtifactMetadata) -> Unit)? = null

    init {
        store.initialize(::isCurrentMetadata) { project(it, readAnalysis()) }
        current = loadCohort()?.takeIf { store.readPlayerSnapshot()?.let(::isCurrentMetadata) == true }
    }

    private fun isCurrentMetadata(snapshot: Snapshot): Boolean =
        snapshot.identityScheme == identityScheme && snapshot.metadataRevision == metadataRevision

    @Synchronized
    fun read(): Snapshot? = store.read()

    @Synchronized
    fun readAnalysis(): AnalysisSnapshot? = runCatching { analysisStore.read() }.getOrNull()

    @Synchronized
    fun nextAnalysisGeneration(): Long = analysisStore.nextGeneration()

    @Synchronized
    fun nextLibraryGeneration(): Long = store.nextGeneration()

    @Synchronized
    fun rememberGenerations() {
        store.rememberGeneration()
        analysisStore.rememberGeneration()
    }

    @Synchronized
    fun publishAnalysis(
        snapshot: AnalysisSnapshot,
        cancellation: Cancellation = Cancellation(),
    ) {
        val started = System.nanoTime()
        snapshot.validate()
        val metadata = analysisStore.publish(snapshot, cancellation)
        val storedMillis = (System.nanoTime() - started) / 1_000_000L
        publicationListener?.invoke(metadata)
        val listenerMillis = (System.nanoTime() - started) / 1_000_000L - storedMillis
        refreshProjection(cancellation)
        val totalMillis = (System.nanoTime() - started) / 1_000_000L
        Log.i(
            "RiffCuetree",
            "analysis_publication_ms=$totalMillis mdrg=$storedMillis listener=$listenerMillis refresh=${totalMillis - storedMillis - listenerMillis}",
        )
    }

    @Synchronized
    fun refreshProjection(cancellation: Cancellation = Cancellation()) {
        cancellation.check()
        if (store.read()?.let(::isCurrentMetadata) != true) return
        val analysis = readAnalysis()
        if (store.refresh(cancellation) { project(it, analysis) }) {
            current = requireNotNull(loadCohort())
            publicationListener?.invoke(current!!.members.getValue("MDIA").metadata)
        }
    }

    @Synchronized
    fun publish(
        snapshot: Snapshot,
        cancellation: Cancellation,
    ) {
        val analysis = readAnalysis()
        require(isCurrentMetadata(snapshot)) { "Cuetree publication has a stale metadata revision" }
        store.publish(snapshot, cancellation) { project(it, analysis) }
        current = requireNotNull(loadCohort())
        publicationListener?.invoke(current!!.members.getValue("MDIA").metadata)
    }

    @Synchronized
    fun currentArtifact(kind: IndexerArtifactKind): ValidatedAndroidArtifact? {
        if (kind == IndexerArtifactKind.MDRG) return runCatching { analysisStore.currentArtifact() }.getOrNull()
        val name = NAMES[kind] ?: return null
        val cohort = current ?: return null
        val member = cohort.members.getValue(name)
        return ValidatedAndroidArtifact(
            ParcelFileDescriptor.open(member.path.toFile(), ParcelFileDescriptor.MODE_READ_ONLY),
            member.metadata,
            cohortId = cohort.id,
        )
    }

    private fun project(
        snapshot: Snapshot,
        analysis: AnalysisSnapshot?,
    ): Snapshot {
        val retainedGenres =
            store
                .readPlayerSnapshot()
                ?.takeIf(::isCurrentMetadata)
                ?.genres
                .orEmpty()
        val started = System.nanoTime()
        val projected =
            projectWithOptionalAnalysis(
                analysis = analysis,
                project = { evidence ->
                    CuetreePlayerProjection.build(
                        snapshot,
                        genres,
                        evidence,
                        displayGenres,
                        retainedGenres,
                        ::isStandardAudiobooksTree,
                    )
                },
                onFailure = { Log.w("RiffCuetree", "optional_projection_failed type=${it.javaClass.simpleName}") },
            )
        Log.i(
            "RiffCuetree",
            "projection_summary nodes=${snapshot.nodes.size} attributes=${projected.attributeCatalog.size} " +
                "analysis=${analysis?.records?.size ?: 0} elapsed_ms=${(System.nanoTime() - started) / 1_000_000L}",
        )
        return projected
    }

    private fun loadCohort(): Cohort? {
        val (id, flat) = store.readPlayerCohort() ?: return null
        val snapshot = requireNotNull(store.readPlayerSnapshot())
        val path = playerRoot.resolve(id)
        val kinds = listOf(IndexerArtifactKind.CUMD, IndexerArtifactKind.CUAL, IndexerArtifactKind.CUAR, IndexerArtifactKind.CUGR)
        val members =
            FlatCatalogs.names.zip(kinds).associate { (name, kind) ->
                val file = path.resolve(name)
                val bytes = flat.bytes(name)
                require(Files.size(file) == bytes.size.toLong())
                name to
                    Member(
                        file,
                        IndexerArtifactMetadata(
                            kind,
                            VOLUME,
                            snapshot.generation,
                            bytes.size.toLong(),
                            FlatCatalogs.FORMAT_VERSION.toInt(),
                            1,
                            nativeXxh3(bytes),
                        ),
                    )
            }
        return Cohort(id, members)
    }

    private data class Member(
        val path: Path,
        val metadata: IndexerArtifactMetadata,
    )

    private data class Cohort(
        val id: String,
        val members: Map<String, Member>,
    )

    companion object {
        const val VOLUME = "cuetree"
        val NAMES =
            mapOf(
                IndexerArtifactKind.CUMD to "MDIA",
                IndexerArtifactKind.CUAL to "ALBM",
                IndexerArtifactKind.CUAR to "ARTS",
                IndexerArtifactKind.CUGR to "GNRV",
            )
    }
}

/** A bad optional contribution cannot poison the factual Player cohort on this or a later startup. */
internal fun <T : Any> projectWithOptionalAnalysis(
    analysis: T?,
    project: (T?) -> Snapshot,
    onFailure: (Exception) -> Unit = {},
): Snapshot {
    if (analysis == null) return project(null).also(::validate)
    return try {
        project(analysis).also(::validate)
    } catch (error: Exception) {
        onFailure(error)
        project(null).also(::validate)
    }
}
