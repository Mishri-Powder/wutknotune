# Cuetree

Independent Apache-2.0 Kotlin/JVM media-library indexing with retained source state and FlatBuffers
catalogs. Cuetree keeps its own independent build and source export; Riff consumes its core through
a separate Android adapter.

- [Contract](https://wiki.271.pics/Cuetree/contract)
- [API reference](https://wiki.271.pics/Cuetree/API)
- [Build guide](https://wiki.271.pics/Cuetree/build)
- [Contributor instructions](AGENTS.md)

## Build

Requires JDK 17+, FlatBuffers compiler **25.12.19**, and external Cuegrain **0.1.0**. The wrapper
pins Gradle; other dependency versions are in the build files.

For development, explicitly select a Cuegrain checkout:

```sh
./gradlew -PcuegrainDir=/path/to/cuegrain check assemble :cli:installDist
```

An exported Cuetree directory can instead consume the published Cuegrain artifact from a Maven
repository. No sibling checkout is required:

```sh
./gradlew -PdependencyRepo=/path/to/maven-repository check assemble
```

Coordinates are `link.roadio.cuegrain:cuegrain-core:0.1.0`. This source edition does not claim that
Cuegrain has been released to Maven Central. Supply a repository containing that artifact or select
its checkout explicitly. Use `--offline` after dependencies have been cached. Override the compiler
location with `-Pflatc=/path/to/flatc`. The build selects `-Pflatc`, then `CUETREE_FLATC`, then an
executable `~/bin/flatc`, then `flatc` on PATH. The shared compiler/runtime version is declared in
`gradle.properties`.

FlatBuffers Java runtime 25.12.19 is not published to Maven Central. The build compiles its
unmodified official release sources in `third_party/flatbuffers`, with the upstream license and
SHA-256 provenance manifest. The `:flatbuffers-runtime:verifyUpstream` task verifies the imported
file set and hashes. Source-only exports include this module; its publication coordinate is
`link.roadio.cuetree.thirdparty:flatbuffers-java:25.12.19`. The core library's Maven publication
must be distributed together with this runtime artifact.

The library JAR is `core/build/libs/cuetree-core-0.1.0-SNAPSHOT.jar`. Generated Java bindings are
build outputs; the maintained wire sources are under `core/src/main/fbs`.

## Language evidence

Cuetree has no built-in language list. Supply a `CatalogVocabulary` generated from the consumer's
build configuration to both `Indexer(..., vocabulary = vocabulary)` and
`snapshot.advancePublication(generation, ttl, vocabulary = vocabulary)`. Its literal word sets
control generic credits, title/version/disc markers; genre-role sets contain dictionary IDs resolved
through `GenreResolver`. Construction snapshots these inputs. An omitted vocabulary contributes no
lexical clues; structural, duration and explicit Book/FILF rules still apply. The standalone CLI
uses this neutral default. Test vocabularies are explicit fixtures and are not packaged defaults.

## Executable fat JAR

`assemble` also builds the CLI fat JAR. To build only this executable (with a development Cuegrain
checkout):

```sh
./gradlew -PcuegrainDir=/path/to/cuegrain :cli:fatJar
```

Output: `cli/build/libs/cuetree-0.1.0-SNAPSHOT-all.jar`. It contains the CLI, core, Cuegrain, Kotlin
and other JVM runtime dependencies, the JSON export schemas and bundled license notices. Copy this
one JAR to another directory or host with Java 17+; no extra Java classpath or source checkout is
required:

```sh
java -jar cuetree-0.1.0-SNAPSHOT-all.jar --help
java -jar cuetree-0.1.0-SNAPSHOT-all.jar /tmp/cuetree-catalog /srv/music
java -jar cuetree-0.1.0-SNAPSHOT-all.jar --json /tmp/cuetree-catalog /srv/music
```

Only `--json` additionally requires flatc 25.12.19, selected as described below. The fat JAR does
not bundle a JVM or native flatc. Artifact-based builds use the same task with
`-PdependencyRepo=...` in place of the checkout override. The existing application distribution
remains available.

The task preserves dependency resources and multi-release classes, strips input manifests,
signatures and module descriptors, fails on unexpected duplicate entries, and uses deterministic
archive ordering/timestamps. Licenses are under `META-INF/cuetree` and `META-INF/flatbuffers`.

## Index a POSIX library

```kotlin
val generator = MockMediaID()
val store = SnapshotStore(Path.of("/tmp/my-cuetree-catalog"))
val accepted = store.read()
val previous = accepted ?: Snapshot.empty(generator.scheme)
val ttl = 30L * 24 * 60 * 60 * 1000 // This host chooses milliseconds and a 30-day retention window.
val result = Indexer(generator, ttl).scan(
    roots = listOf(RootSpec("posix", "/srv/music", "Music")),
    previous = previous,
    generation = maxOf(System.currentTimeMillis(), Math.addExact(previous.generation, 1)),
)
if (accepted == null || !result.hasSameContentAs(previous)) store.write(result)
```

Imports are `link.roadio.cuetree.*` and `java.nio.file.Path`. Indexing is synchronous; the caller
supplies scheduling, cancellation and strictly increasing publication generations. Each committed
checkpoint and final publication advances generation and consumes TTL; source observations and
catalog references retain their appropriate marks. The one project-wide TTL is required when
creating Indexer and uses the same scale; the library assumes no clock unit or default duration. An
empty or changed root list is an actual observation of absence, not a request to skip a scan.

The equivalent development command is:

```sh
cli/build/install/cuetree/bin/cuetree /tmp/my-cuetree-catalog /srv/music
```

The store must be outside media roots. Retain its entire directory: `CURRENT` identifies one
immutable MDIA/ALBM/ARTS/GNRV cohort, while `PREVIOUS` identifies the preceding validated cohort.
The manifest checks each binary member with XXH3-64; the store removes older and abandoned cohorts.
The [current format](https://wiki.271.pics/Cuetree/persistence#mvp-format) stores common IDs, hidden
CUE source records and parsed structural tables, sorted record arrays, an interned typed attribute
dictionary, one generation per record and packed flags; nullable scalars use field presence. Format
changes require complete index recreation; no migrations or compatibility readers are provided.

## Optional JSON output

To also save schema-shaped JSON alongside each binary catalog:

```sh
cli/build/install/cuetree/bin/cuetree --json /tmp/cuetree-catalog /srv/music
```

`--json` requires **flatc 25.12.19 at runtime**. The CLI selects `CUETREE_FLATC`, then an executable
`~/bin/flatc`, then `flatc` on PATH. A wrong selected version fails with its path and version in the
error; it is never silently replaced. To select a compiler explicitly:

```sh
CUETREE_FLATC="$HOME/bin/flatc" java -jar cli/build/libs/cuetree-0.1.0-SNAPSHOT-all.jar --json /tmp/cuetree-catalog /srv/music
```

The CLI JAR includes the exact FBS schemas; no source checkout is needed to run the installed
distribution. `--help` lists the option; `--` ends option parsing for paths beginning with a dash.

The cohort selected by `/tmp/cuetree-catalog/CURRENT` contains `MDIA.json`, `ALBM.json`, `ARTS.json`
and `GNRV.json` beside `MDIA`, `ALBM`, `ARTS` and `GNRV`. Conversion reads the actual encoded
buffers using strict JSON, schema defaults and readable UTF-8. Field names, ID references, ordered
vectors and optional scalar presence follow FBS. Signed 64-bit values are exact decimal JSON
numbers; consumers must preserve integer precision. JSON does not add runtime children or expand
attribute references into a different model.

All eight files are completed before CURRENT changes. Converter failure leaves the previous
publication current and fails the command. Store readers still verify/read the binary catalogs; JSON
is an optional companion. Without `--json`, changed content creates a binary-only cohort without
running flatc. An unchanged scan preserves CURRENT, PREVIOUS and generation; repeated `--json` also
skips conversion. Adding JSON to an existing binary-only publication creates a complete new cohort,
leaving the previous directory immutable. Store readers require the exact current format.

## Identity and access

`MediaIdGenerator` is injected. `MockMediaID` hashes whole file bytes with XXH3-64 for host
experiments; metadata edits can change that identity. A distinct seeded hash supplies nullable `lo`
only on a same-high collision, for both original and contender. Every new unresolved file or
fragment carries SHRT. Numeric zero is a valid low; null means uncomputed. Fragment mock identities
include the complete file and the span; they do not establish audio equivalence with separately
encoded standalone tracks.

Riff injects its Android encoded-audio identity and SAF provider through these independent
boundaries. The core builds and tests without Android dependencies. Provider handles are transient;
only root locators and descendant names are persisted. POSIX scanning does not follow symbolic
links.

## Catalogs

MDIA stores facts with one parent MediaId and separate physical SourceInfo; children are runtime
indexes. Fragment begin/end bounds are nullable. There is no persisted origin or selected physical
parent index. Files and Cuegrain observations determine D/M/F existence, availability and
generation; Player catalogs are built afterward. No Structure entity or persistent parse-cache table
is stored. Removing prior Player catalogs cannot change source refresh or expiry. ALBM, ARTS and
GNRV contain ready ordered membership; GNRE is an external dictionary supplied through
`GenreResolver`. Valid external or independently titled multitrack tables define explicit albums.
Untitled embedded tables expand base MEDIA after ordinary grouping, with an explicit fallback for
ungrouped bases. Declared FILF positions survive equal identities. Exact explicit copies converge by
album and ordered position metadata; different metadata variants remain separate. All equivalent
base-file copies are excluded from additional ordinary/Singles tracks. Remaining tagged media use
structural/weighted grouping, followed by the scoped
[Singles classifier](https://wiki.271.pics/Cuetree/singles). Qualified owners receive stable
`<Artist>: singles` baskets; one typed `[[SINGLES]]` album receives the remaining loose positions.
Complete filename sequences supply missing ordering evidence without rewriting authored tags.

Unavailable D/M/F records preserve their generation. Catalogs and attributes refresh generation from
retained MDIA references even while unavailable. Orphans preserve their last reference mark; they
expire when the difference reaches the shared TTL. A return updates generation and clears ACSS.
Availability propagates top-down. Rendering reads materialized state; playback uses `SourceResolver`
to look up actual provider children from the root. Fragments reference base MediaId through parentId
and retain their own nullable span and metadata; the first available matching file can supply
playback after another copy disappears. Fragment refresh requires a current valid section
observation; accessible base audio alone cannot refresh a removed chapter. Fragment/TotalFragments
attributes and ordered ALBM source references preserve CUE/TOC positions independently of table
sorting. Rebuilding an explicit album after discarding ALBM requires readable structure input; flat
F facts alone cannot recover arbitrary groups. Directory renames are new admissions with old missing
records retained until TTL; Cuetree does not guess continuity from equal names, sizes or timestamps.

See [LICENSE](LICENSE) and [NOTICE](NOTICE).
