// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree.cli

import link.roadio.cuetree.*
import java.nio.file.Files
import java.nio.file.Path

private const val USAGE = "Usage: cuetree [--json] [--] <store-directory> <media-root> [more-roots...]"

fun main(args: Array<String>) {
    val paths = mutableListOf<String>()
    var json = false
    var options = true
    for (arg in args) {
        when {
            options && arg == "--" -> {
                options = false
            }

            options && arg == "--json" -> {
                json = true
            }

            options && arg in setOf("--help", "-h") -> {
                println("$USAGE\n--json  Also write schema-shaped JSON beside each FlatBuffer (requires flatc ${Flatc.version}; CUETREE_FLATC, ~/bin/flatc, then PATH).")
                return
            }

            options && arg.startsWith("-") -> {
                error("Unknown option: $arg\n$USAGE")
            }

            else -> {
                paths += arg
            }
        }
    }
    require(paths.size >= 2) { USAGE }
    val exporter = if (json) JsonExport().also { it.checkVersion() } else null
    val store = SnapshotStore(Path.of(paths[0]))
    val generator = MockMediaID()
    val accepted = store.readFlatWithCohort()
    val previous = accepted?.second?.snapshot() ?: Snapshot.empty(generator.scheme)
    val roots =
        paths.drop(1).map {
            RootSpec(
                "posix",
                Path
                    .of(it)
                    .toAbsolutePath()
                    .normalize()
                    .toString(),
            )
        }
    require(
        roots.none {
            Path
                .of(paths[0])
                .toAbsolutePath()
                .normalize()
                .startsWith(Path.of(it.locator))
        },
    ) { "Store must be outside media roots" }
    val snapshot = Indexer(generator, 30L * 24 * 60 * 60 * 1000).scan(roots, previous, maxOf(System.currentTimeMillis(), Math.addExact(previous.generation, 1)))
    val missingJson =
        json &&
            accepted?.first?.let { cohort ->
                FlatCatalogs.names.any { !Files.isRegularFile(Path.of(paths[0]).resolve(cohort).resolve("$it.json")) }
            } != false
    val changed = accepted == null || !snapshot.hasSameContentAs(previous) || missingJson
    if (changed) store.write(snapshot, json = exporter?.let { it::convert })
    val generation = if (changed) snapshot.generation else previous.generation
    println("generation=$generation sources=${snapshot.nodes.size} albums=${snapshot.albums.size} artists=${snapshot.artists.size} genres=${snapshot.genres.size}")
    println("identity=${snapshot.identityScheme}")
    snapshot.diagnostics.forEach { System.err.println("${it.code}: ${it.source}: ${it.message}") }
}
