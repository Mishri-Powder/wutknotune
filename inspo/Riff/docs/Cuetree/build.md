# Cuetree build and POSIX CLI

Cuetree is a separate build under `standalone/cuetree`. Riff consumes its core through an explicit
composite source dependency; the independent build has no Riff or Android dependency.

## Requirements

JDK 17+, flatc **25.12.19**, Gradle wrapper 9.8.1, Kotlin 2.4.21 and ktlint plugin 14.2.0 with
engine 1.8.0. The build verifies the compiler version before generating bindings. Cuegrain 0.1.0 and
hash4j 0.31.0 remain external artifacts. FlatBuffers Java 25.12.19 is compiled from the unmodified
upstream runtime sources under `third_party/flatbuffers` by `:flatbuffers-runtime`: this release has
not published a Java artifact to Maven Central. The module carries its upstream license and a
per-file SHA-256 provenance manifest, and is included in source-only exports and the fat JAR. Its
publication coordinate is `link.roadio.cuetree.thirdparty:flatbuffers-java:25.12.19`, not an
assertion that an official Central artifact exists. When distributing the core's Maven publication,
publish this runtime artifact alongside it. The host mock uses
[hash4j's XXH3 implementation](https://github.com/dynatrace-oss/hash4j).

## Development build

```sh
cd standalone/cuetree
./gradlew -PcuegrainDir=/absolute/path/to/cuegrain check assemble :cli:installDist
```

The Cuegrain path is an explicit Gradle composite override. The default build never reaches into a
sibling checkout. To use a published dependency instead:

```sh
./gradlew -PdependencyRepo=/absolute/path/to/maven-repository check assemble
```

That repository must contain `link.roadio.cuegrain:cuegrain-core:0.1.0`. No Maven Central release of
Cuegrain is implied by these instructions. Use `--offline` after caching dependencies and
`-Pflatc=/path/to/flatc` for a build compiler override.

The shared version is declared in `gradle.properties` and packaged into the CLI; the Java runtime
and generator check use that same value. Build selection is `-Pflatc`, then `CUETREE_FLATC`, then an
executable `~/bin/flatc`, then `flatc` on PATH. Runtime JSON selection uses `CUETREE_FLATC`, then
executable `~/bin/flatc`, then PATH. An explicitly selected wrong version fails with the actual
path/version in the error; it is never silently replaced. The home candidate is relative to the user
running Gradle/Java. No system compiler or shell PATH is modified.

`:core` builds `core/build/libs/cuetree-core-0.1.0-SNAPSHOT.jar` and a sources JAR. `:cli` builds an
application distribution containing runtime dependencies. The generated FlatBuffers Java bindings
remain under core/build and are not maintained source.

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

Only `--json` additionally requires the selected flatc 25.12.19. The fat JAR does not bundle a JVM
or native flatc. Artifact-based builds use the same task with `-PdependencyRepo=...` in place of the
checkout override. The existing application distribution remains available.

The task preserves dependency resources and multi-release classes, strips input manifests,
signatures and module descriptors, fails on unexpected duplicate entries, and uses deterministic
archive ordering/timestamps. Licenses are under `META-INF/cuetree` and `META-INF/flatbuffers`.

## POSIX experiment

```sh
cli/build/install/cuetree/bin/cuetree /tmp/cuetree-catalog /srv/music
```

Additional arguments are additional media roots. The store belongs outside the media roots. The CLI
restores the previous snapshot, scans with MockMediaID, and publishes changed content. Standard
output reports generation and catalog counts; diagnostics go to standard error. This CLI chooses
Unix milliseconds for generation and one 30-day TTL. The core itself has no clock unit or default
TTL. The [current format](/Cuetree/persistence#mvp-format) requires complete index recreation after
a format change; no migration or compatibility reader is provided.

To also save schema-shaped JSON alongside each binary catalog:

```sh
cli/build/install/cuetree/bin/cuetree --json /tmp/cuetree-catalog /srv/music
```

`--json` requires **flatc 25.12.19 at runtime**, selected as described above. The CLI JAR includes
the exact FBS schemas; no source checkout is needed to run the installed distribution. `--help`
lists the option; `--` ends option parsing for paths beginning with a dash.

The cohort selected by `/tmp/cuetree-catalog/CURRENT` contains `MDIA.json`, `ALBM.json`, `ARTS.json`
and `GNRV.json` beside `MDIA`, `ALBM`, `ARTS` and `GNRV`. Conversion reads the actual encoded
buffers using strict JSON, schema defaults and readable UTF-8. Field names, ID references, ordered
vectors and optional scalar presence follow FBS. Signed 64-bit values are exact decimal JSON
numbers; consumers must preserve integer precision. JSON does not add runtime children or expand
attribute references into a different model.

All eight files are completed before CURRENT changes. Converter failure leaves the previous
publication current and fails the command. Store readers still verify/read the binary catalogs; JSON
is an optional companion. Without `--json`, changed content produces a binary-only cohort without
running flatc. Unchanged content retains the accepted cohort, including any existing JSON
companions. Binary readers require the current version owned by [persistence](/Cuetree/persistence).

A separate runnable consumer lives in `examples/cuetree`:

```sh
cd examples/cuetree
./gradlew run
```

It creates silent WAV/CUE fixtures under build/demo, stores catalogs beside the music directory,
rebuilds Player catalogs from readable files and their observed MDIA facts, reads direct
FlatBuffers, opens a resolved source, and exercises root withdrawal/return. Rerun to reload the
store. Use --demo with a fresh directory, or pass a current-format catalog directory and media
roots. The example's README documents these modes.

## Verification and export

Run `ktlintFormat` before `check` if source formatting is needed. Tests use ordinary POSIX temporary
files and a fake provider with opaque, nonconcatenable handles; no Android target is needed. An
exported source directory needs the same external artifacts/compiler, without Riff source. Exclude
.gradle, .kotlin and module build directories from an export.

[Current evidence](/Cuetree/worklog) records actual test results, independent artifact-based export
verification and CLI runs. No Alpha build, HU deployment or Riff integration belongs to this library
implementation step.

## Unchanged input

The CLI uses the core semantic comparison and leaves CURRENT/PREVIOUS and generation unchanged when
a scan changes no catalog content. Repeated --json also skips conversion. Requesting JSON for a
binary-only accepted cohort is an explicit output change: publish a complete new cohort with all
four companions before CURRENT switches; never add files to a published directory in place.
