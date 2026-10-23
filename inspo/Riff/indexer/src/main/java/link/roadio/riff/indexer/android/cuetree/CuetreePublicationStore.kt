package link.roadio.riff.indexer.android.cuetree

import android.util.Log
import link.roadio.cuetree.Cancellation
import link.roadio.cuetree.FlatCatalogs
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.SnapshotStore
import link.roadio.cuetree.advancePublication
import java.nio.file.Path

/** Keep the accepted Player cohort until a complete replacement has passed both format gates. */
internal class CuetreePublicationStore(
    root: Path,
    private val ttl: Long,
) {
    private val generation = PublicationGeneration(root)
    private val full = SnapshotStore(root)
    private val player = SnapshotStore(root.resolve("player"))

    // This writer is process-serialized. Startup validates disk once; later reads reuse committed
    // immutable values, rather than decoding four files repeatedly at each MDRG publication.
    private var factual = full.read()
    private var projected = player.readFlatWithCohort()
    private var projectedSnapshot = projected?.second?.snapshot()

    fun read(): Snapshot? = factual

    fun nextGeneration(): Long = Math.addExact(maxOf(generation.last(), factual?.generation ?: 0L, projectedSnapshot?.generation ?: 0L), 1L)

    fun rememberGeneration() = generation.remember(nextGeneration() - 1L)

    fun readPlayer(): FlatCatalogs? = projected?.second

    fun readPlayerSnapshot(): Snapshot? = projectedSnapshot

    fun readPlayerCohort(): Pair<String, FlatCatalogs>? = projected

    fun initialize(
        eligible: (Snapshot) -> Boolean = { true },
        project: (Snapshot) -> Snapshot,
    ) {
        if (projected != null) return
        factual?.takeIf(eligible)?.let { snapshot ->
            val candidate = project(snapshot)
            val flat = FlatCatalogs.encode(candidate)
            val normalized = flat.snapshot()
            projected = player.writeFlat(flat, normalized) to flat
            projectedSnapshot = normalized
        }
    }

    fun publish(
        snapshot: Snapshot,
        cancellation: Cancellation,
        project: (Snapshot) -> Snapshot,
    ) {
        val flat = FlatCatalogs.encode(project(snapshot))
        publishPrepared(snapshot, flat.snapshot(), flat, cancellation)
    }

    private fun publishPrepared(
        snapshot: Snapshot,
        candidate: Snapshot,
        flat: FlatCatalogs,
        cancellation: Cancellation,
    ) {
        // Validate both before either committed pointer changes. The same encoded projection is
        // retained for later comparisons and descriptor metadata after the Player commit.
        cancellation.check()
        require(snapshot.generation >= nextGeneration()) { "Non-increasing Cuetree generation" }
        generation.remember(snapshot.generation)
        val fullStarted = System.nanoTime()
        full.write(snapshot, cancellation)
        factual = snapshot
        val fullMillis = (System.nanoTime() - fullStarted) / 1_000_000L
        val playerStarted = System.nanoTime()
        projected = player.writeFlat(flat, candidate, cancellation) to flat
        projectedSnapshot = candidate
        runCatching {
            Log.i(
                "RiffCuetree",
                "publication_write_ms factual=$fullMillis player=${(System.nanoTime() - playerStarted) / 1_000_000L}",
            )
        }
    }

    fun refresh(
        cancellation: Cancellation = Cancellation(),
        project: (Snapshot) -> Snapshot,
    ): Boolean {
        val snapshot = factual ?: return false
        val next = nextGeneration()
        val advanced = snapshot.advancePublication(next, ttl, vocabulary = AndroidCatalogVocabulary.value)
        val flat = FlatCatalogs.encode(project(advanced))
        val candidate = flat.snapshot()
        val existing = projectedSnapshot
        if (existing != null && comparable(candidate) == comparable(existing)) return false
        publishPrepared(advanced, candidate, flat, cancellation)
        return true
    }

    private fun comparable(snapshot: Snapshot): Snapshot =
        snapshot.copy(
            generation = 0,
            nodes = snapshot.nodes.map { it.copy(life = it.life.copy(generation = 0)) },
            albums = snapshot.albums.map { it.copy(life = it.life.copy(generation = 0)) },
            artists = snapshot.artists.map { it.copy(life = it.life.copy(generation = 0)) },
            genres = snapshot.genres.map { it.copy(life = it.life.copy(generation = 0)) },
            attributeCatalog = emptyList(),
            diagnostics = emptyList(),
        )
}
