# Cuetree usage example

A runnable Kotlin/JVM consumer of the [Cuetree library](../../standalone/cuetree/README.md). This
project has its own Gradle build and uses Cuetree's public API.

## Run the self-contained demo

Requires JDK 17+ and `flatc` 25.12.19, as specified in the
[Cuetree build guide](https://wiki.271.pics/Cuetree/build).

```sh
cd ~/riff/examples/cuetree
./gradlew run
```

The first run creates `build/demo/music` with valid silent WAV files and a two-track CUE, plus a
separate store in `build/demo/catalog`. It shows:

1. POSIX indexing with an injected `MockMediaID`.
2. Saving a snapshot, reading it through `SnapshotStore`, and preserving its cohort on an unchanged
   scan.
3. Rebuilding ALBM/ARTS/GNRV after discarding their previous contents, with the same MDIA facts.
4. Runtime MDIA child indexes and ALBM, ARTS and GNRV membership through generated FlatBuffers.
5. A source lookup by MediaId and actual file opening through `SourceResolver`.
6. Root withdrawal and return, materialized ACSS, stable identities and TTL reset.

This host example supplies Unix milliseconds as generation and one 30-day TTL to the indexer. Source
records keep their last available generation; catalogs and attributes refresh generation while
retained MDIA records reference them, even when those sources are unavailable. Each changed
publication uses a strictly greater generation. Core callers can choose another monotonic scale with
TTL in the same units. The current
[Cuetree persistence format](https://wiki.271.pics/Cuetree/persistence) stores common sorted IDs and
typed attribute references with inline generations and packed flags. Physical source fields live in
SourceInfo; parent MediaIds connect D/M/F, and nullable bounds describe fragments. Fragment order
comes from explicit candidates and is independent of storage order; exact copies converge while
different metadata variants remain distinct.

The explicit album is `Cuetree Demo`; the unrelated WAV belongs to the typed `[[SINGLES]]` album.
Artists and genres come from CUE metadata through Cuegrain. WAV itself produces `FORMAT_UNSUPPORTED`
metadata diagnostics: its identity and source access still work, and the CUE provides the
album/track observations. No audio is played.

Run again to reuse the fixture and catalog. Unchanged input keeps its generation/cohort; the demo
then changes availability explicitly through withdrawal/return. Existing fixture files are never
regenerated. A demo will refuse to replace an existing unrecognized music/catalog directory. Stores
from earlier experimental formats must be rebuilt in a fresh directory. To keep demo data outside
Gradle's disposable build directory:

```sh
./gradlew run --args='--demo /tmp/cuetree-example'
```

## Use your own media

```sh
./gradlew run --args='/tmp/my-cuetree-catalog /srv/music'
```

The first argument is the writable catalog directory; the remaining arguments are media roots. Keep
the catalog outside every media root. This mode only scans and displays results; it does not
generate fixtures or withdraw roots. Media files are read-only. Reuse the same catalog path for
subsequent scans. Supply all configured roots on every run: omitted roots become unavailable.

Paths with spaces can be quoted inside the Gradle argument string:

```sh
./gradlew run --args='"/tmp/my catalog" "/srv/My Music"'
```

`MockMediaID` is whole-container XXH3 for experiments. It is not Android encoded-audio Media.ID. The
returned fragment span accompanies the container; it is not a byte offset or a pre-cut stream.

## Read the example

- [Main.kt](src/main/kotlin/link/roadio/cuetree/example/Main.kt) contains the client lifecycle,
  FlatBuffers display and playback-source resolution.
- [DemoMedia.kt](src/main/kotlin/link/roadio/cuetree/example/DemoMedia.kt) creates the optional
  synthetic input with the JDK's WAV writer.
- [Cuetree API](https://wiki.271.pics/Cuetree/API) documents the library's classes and contracts.

The example uses `FlatCatalogs.sources(MediaId)` for direct binary lookup and displays the shared
attribute dictionary size. `Snapshot.sources(MediaId)` provides the same exact/SHRT matching in the
allocated model; a high alone is not proof of a full identity match.

## Build

```sh
./gradlew check installDist
build/install/cuetree-example/bin/cuetree-example --help
```

Use `--offline` after dependencies are cached. The wrapper matches the standalone Cuetree wrapper.
Settings substitute the library coordinates with the adjacent standalone Cuetree and Cuegrain
builds; the Android/Riff root build is not included.

This example does not add a second indexer, metadata parser, resolver or persistence implementation.
It uses the Apache-2.0 license and notices from [Cuetree](../../standalone/cuetree/LICENSE).
