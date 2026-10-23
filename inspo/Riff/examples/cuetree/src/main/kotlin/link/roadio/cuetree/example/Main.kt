// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree.example

import link.roadio.cuetree.AlbumKind
import link.roadio.cuetree.Flags
import link.roadio.cuetree.FlatCatalogs
import link.roadio.cuetree.Indexer
import link.roadio.cuetree.MediaId
import link.roadio.cuetree.MockMediaID
import link.roadio.cuetree.NodeKind
import link.roadio.cuetree.PosixProvider
import link.roadio.cuetree.RootSpec
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.SnapshotStore
import link.roadio.cuetree.SourceResolver
import java.nio.file.Path

fun main(args: Array<String>) {
    when {
        args.contentEquals(arrayOf("--help")) -> println(USAGE)
        args.isEmpty() || args.first() == "--demo" -> {
            require(args.size <= 2) { USAGE }
            demo(Path.of(args.getOrNull(1) ?: "build/demo"))
        }
        args.size >= 2 && !args.first().startsWith("--") -> {
            val storePath = Path.of(args.first()).toAbsolutePath().normalize()
            val roots = args.drop(1).map { Path.of(it).toAbsolutePath().normalize() }
            require(roots.none(storePath::startsWith)) { "Keep the catalog store outside the media roots." }
            val session = LibrarySession(storePath)
            val rootSpecs = roots.map { RootSpec("posix", it.toString(), it.fileName?.toString() ?: it.toString()) }
            val result = session.scan(rootSpecs, "Media scan")
            showCatalogs(session.store)
            showResolvedSource(result)
        }
        else -> error(USAGE)
    }
}

private fun demo(base: Path) {
    val directory = base.toAbsolutePath().normalize()
    val music = DemoMedia.createIfAbsent(directory)
    val session = LibrarySession(directory.resolve("catalog"))
    val roots = listOf(RootSpec("posix", music.toString(), "Demo music"))
    println("Demo directory: $directory")
    val scanned = session.scan(roots, "Initial scan")
    val acceptedCohort = session.store.readFlatWithCohort()?.first
    session.scan(roots, "Unchanged scan")
    check(acceptedCohort == session.store.readFlatWithCohort()?.first) { "Unchanged input published a cohort" }
    // Catalog loss is downstream: rebuilding projections cannot govern retained source facts.
    val initial =
        session.scan(
            roots,
            "Catalogs rebuilt from readable inputs",
            scanned.copy(albums = emptyList(), artists = emptyList(), genres = emptyList()),
        )
    check(scanned.nodes.map { it.copy(life = it.life.copy(generation = initial.generation)) } == initial.nodes)
    println("Catalogs rebuilt from current observations; D/M/F preserved independently.")
    showCatalogs(session.store)
    showResolvedSource(initial)

    // Withdrawing configured roots exercises retained availability without changing media files.
    val offline = session.scan(emptyList(), "Roots withdrawn")
    check(offline.nodes.all { !it.life.available })
    check(offline.nodes.map { it.life.generation } == initial.nodes.map { it.life.generation })
    check(offline.albums.all { it.life.generation == offline.generation })
    check(offline.attributeCatalog.all { it.life.generation == offline.generation })
    println("Unavailable MDIA references refresh catalog and attribute generations.")
    println("All source records have ACSS: ${offline.nodes.all { it.flags and Flags.ACSS != 0 }}")

    val returned = session.scan(roots, "Roots restored")
    check(returned.nodes.all { it.life.available && it.life.generation == returned.generation })
    check(initial.nodes.map { it.folderId } == returned.nodes.map { it.folderId })
    check(initial.nodes.map { it.mediaId } == returned.nodes.map { it.mediaId })
    check(initial.albums.map { it.id } == returned.albums.map { it.id })
    println("Folder, media and album identities preserved; TTL reset.")
    println("Run the example again to load this catalog and observe this library again.")
}

private class LibrarySession(
    storePath: Path,
) {
    // Android can inject another generator/provider; the scheme is part of every persisted cohort.
    private val identity = MockMediaID()

    // This host chooses milliseconds; Cuetree only requires TTL and generation to share a scale.
    private val indexer = Indexer(identity, ttl = 30L * 24 * 60 * 60 * 1000)
    val store = SnapshotStore(storePath)

    fun scan(
        roots: List<RootSpec>,
        label: String,
        previous: Snapshot = store.read() ?: Snapshot.empty(identity.scheme),
    ): Snapshot {
        val next =
            indexer.scan(
                roots = roots,
                previous = previous,
                generation = maxOf(System.currentTimeMillis(), Math.addExact(previous.generation, 1)),
            )
        val accepted = store.read()
        if (accepted == null || !next.hasSameContentAs(accepted)) store.write(next)
        val reloaded = requireNotNull(store.read())
        println(
            "$label: generation ${previous.generation} -> ${reloaded.generation}; " +
                "available sources ${reloaded.nodes.count { it.life.available }}/${reloaded.nodes.size}",
        )
        next.diagnostics.forEach { diagnostic ->
            println("  diagnostic ${diagnostic.code}: ${diagnostic.source}: ${diagnostic.message}")
        }
        return reloaded
    }
}

private fun showCatalogs(store: SnapshotStore) {
    val flat = requireNotNull(store.readFlat())
    val mdia = flat.mdia()
    for (rootIndex in flat.roots()) {
        showFolder(flat, rootIndex)
        for (index in flat.childrenOf(rootIndex)) {
            val node = requireNotNull(mdia.nodes(index))
            if (node.flags() ushr Flags.KIND_SHIFT == NodeKind.DIRECTORY.ordinal) showFolder(flat, index)
        }
    }

    val albums = flat.albm()
    println("ALBM:")
    for (i in 0 until albums.albumsLength()) {
        val album = requireNotNull(albums.albums(i))
        println("  ${album.title()} [${AlbumKind.entries[album.flags() ushr Flags.KIND_SHIFT]}]: ${album.tracksLength()} tracks")
        for (track in 0 until album.tracksLength()) {
            val id = requireNotNull(album.tracks(track))
            val candidates = flat.sources(MediaId(id.hi(), if (id.hasLo()) id.lo() else null))
            println(
                "    hi=0x${java.lang.Long.toUnsignedString(id.hi(), 16)}, " +
                    "lo=${if (id.hasLo()) "0x" + java.lang.Long.toUnsignedString(id.lo(), 16) else "null"}; " +
                    "matching sources=${candidates.size}",
            )
        }
    }
    println("Typed attribute dictionary: ${mdia.attributesLength()} values")
    val artists = flat.arts()
    println("ARTS:")
    for (i in 0 until artists.artistsLength()) {
        val artist = requireNotNull(artists.artists(i))
        println("  ${artist.name()}: ${artist.albumsLength()} albums")
    }
    val genres = flat.gnrv()
    println("GNRV:")
    for (i in 0 until genres.genresLength()) {
        val genre = requireNotNull(genres.genres(i))
        println("  ${genre.name()}: ${genre.albumsLength()} albums")
    }
}

private fun showFolder(
    flat: FlatCatalogs,
    folderIndex: Int,
) {
    val mdia = flat.mdia()
    val folder = requireNotNull(mdia.nodes(folderIndex))
    println("MDIA folder ${folder.source()!!.name()}:")
    // Children are a runtime index; availability is read directly from each node.
    for (index in flat.childrenOf(folderIndex)) {
        val child = requireNotNull(mdia.nodes(index))
        val kind = NodeKind.entries[child.flags() ushr Flags.KIND_SHIFT]
        val unavailable = child.flags() and Flags.ACSS != 0
        println("  ${child.source()!!.name()} [$kind] unavailable=$unavailable")
    }
}

private fun showResolvedSource(snapshot: Snapshot) {
    val preferred =
        snapshot.nodes.firstOrNull { it.kind == NodeKind.FRAGMENT && it.life.available }
            ?: snapshot.nodes.firstOrNull { it.kind == NodeKind.MEDIA && it.life.available }
            ?: return
    val id = requireNotNull(preferred.mediaId)
    // A source index is local to this snapshot. MediaId remains the logical cross-catalog reference.
    val sourceIndex = snapshot.sources(id).first { snapshot.nodes[it].life.available }
    val resolved = SourceResolver(listOf(PosixProvider())).resolve(snapshot, sourceIndex)
    resolved.open().use { input ->
        val header = input.read(0, minOf(12L, input.length).toInt())
        println(
            "Resolved source[$sourceIndex]: provider=${resolved.provider.id}, span=${resolved.span}, " +
                "container prefix=${header.joinToString(" ") { "%02x".format(it) }}",
        )
    }
    println("The decoder receives the opened container plus the span; this example does not play audio.")
}

private const val USAGE =
    "Usage: cuetree-example [--demo [directory]] | <catalog-directory> <media-root> [more-roots...]"
