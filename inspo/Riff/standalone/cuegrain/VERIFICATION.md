# Cuegrain 0.1.0 verification

Date: 2026-09-22. All results below concern the standalone library, not a Riff integration or an
Android device release.

## Build and isolation

- Core: 37 JUnit tests, zero failures/errors/skips. One regression test also exercises 400
  deterministic malformed inputs.
- Optional CED: 2 JUnit tests, zero failures/errors/skips.
- Both compile-time configurations pass Gradle check and ktlint.
- Executable JAR: 3 additional CLI JUnit tests pass in each configuration.
- A source-only export, without build directories, private evidence or a parent project, builds and
  passes check with CED disabled and enabled.
- The base CLI runtime contains core, CLI, Kotlin stdlib 2.3.0 and JetBrains annotations 13.0. No
  CED classes, native libraries or JNA are present.
- Android D8 8.13.19 produces DEX at API 31 without warnings. The native CED library compiles with
  NDK 29 for arm64-v8a, armeabi-v7a and x86_64. No Android device runtime test or Riff integration
  has been performed.
- Tracked Riff files and Git history are unchanged; the implementation is a new independent
  directory.

## Supplied CUE corpus and internet cases

These corpus results are from the parser qualification run. The executable-JAR packaging check below
reuses the same parser; it does not claim a new full scan of private music collections.

The supplied archive contains 172 CUEs describing 2,585 tracks.

- Without CED, 130 archive CUEs decode deterministically; 42 require a charset.
- With CED, 159 decode automatically; 13 deliberately return ENCODING_REQUIRED because the legacy
  detector is not reliable.
- Explicit windows-1252 for those 13 allows all 172 CUEs and all 2,585 tracks to be parsed. This is
  a caller-supplied encoding, not a new automatic fallback.
- Four pinned internet fixtures are checked separately. The valid 99-track fixture parses; the other
  three intentionally expose missing INDEX 01, duplicate/malformed commands or absence of tracks and
  retain diagnostics.
- SHA-256 checks before/after parsing show unchanged CUE fixture bytes.

Provenance and source URLs are in [SOURCES.md](SOURCES.md). External fixtures are not bundled or
relicensed.

## Authorized real-media samples

Deterministic samples use a SHA-256 ordering of paths, with a per-format cap and all discovered
CUE/M4B files. These are samples, not a scan of every audio file.

| Collection         | Inputs parsed | Successful | Expected input/encoding errors          |
| ------------------ | ------------: | ---------: | --------------------------------------- |
| Music and Music.\* |         1,166 |      1,163 | 3 ambiguous-encoding CUEs               |
| Older NAS Music    |         1,072 |      1,058 | 13 ambiguous-encoding CUEs, 1 empty M4A |

Across these samples, all 2,008 nonempty selected audio files parse successfully: MP3, M4A/M4B and
FLAC. The additional 229 CUE inputs keep file-local timelines. Some CUE/media content is duplicated
between collections and the supplied archive; these counts are input paths, not deduplicated
recordings.

Title, artist and album observations match the independent Mutagen 1.47.0 oracle after accounting
for equivalent freeform tag aliases and independent ID3v1 sources. There are zero unexplained
differences in either sample. This comparison does not claim independent verification of every
possible field.

Real regressions now covered by synthetic tests include repeated UTF-16 BOMs between values,
oversized terminated CUESHEET text fields containing stale bytes, nonzero ID3 padding, binary MP4
Encoding Params, and format-specific PERFORMER meaning. RIFF feature tests cover CUE source/track
scope, ordered genre-confidence pairing, malformed/duplicate diagnostics, JSON typing, ID3 user
text, MP4 freeform fields, and the Vorbis-comment path shared by FLAC, Ogg Vorbis and Opus.
Compatible recovery is diagnosed; strict mode treats those recoveries as errors where applicable.

WMA content was never opened. Nineteen NAS filenames containing invalid UTF-8 bytes were excluded
from the JVM filesystem-CLI sample and counted separately. No names, permissions, tags or media
bytes were changed. Private paths, track inventories, artwork and full metadata results remain in
ignored local evidence; only aggregate results are published.

## Independent generated-media checks

Fifteen CLI/integration groups pass using independent FFmpeg-generated audio and Mutagen-authored
metadata: MP3, M4A, FLAC, Ogg Vorbis and Opus. They cover ordinary tags, gain observations, ID3
CHAP/CTOC, simultaneous FLAC sources, explicit picture bytes, external CUE references and
non-following of embedded CUE references. Hashes confirm unchanged generated media. Actual
collection samples contain no Ogg/Opus files, so those formats are qualified here through synthetic
fixtures.

The original synthetic chapter fixtures also assert exact starts of 0, 333 and 1,234 ms and explicit
ends where the source format supplies them. Nero-only unknown ends remain absent. CUE positions
retain 75-fps time, and FLAC CUESHEET positions retain exact sample offsets.

CLI success, help, version and failure output, corpus reports and both private sample reports
validate against JSON Schema version 1. No binary picture bytes are emitted unless explicitly
requested.

## Reproduction and retained evidence

### Executable JAR verification

The single-file build passes both configurations:

```sh
./gradlew --offline check :cli:fatJar
./gradlew --offline -PwithCed=true -PcmakeExecutable=/path/to/cmake check :cli:fatJar
python tools/check_cli.py --jar cli/build/libs/cuegrain-0.1.0-all.jar
python tools/check_schema.py --jar cli/build/libs/cuegrain-0.1.0-all.jar
```

The same integration and schema commands pass for the CED-enabled JAR. Each variant passes 15
generated-media/CLI integration groups and 5 schema-validated CLI invocations. Three JUnit tests
inspect archive contents and copy only the JAR into an isolated directory before invoking java -jar.
They verify JSON help/version/errors, exit codes, read-only UTF-8/legacy CUE handling, explicit
charset and optional native CED discovery without adjacent libraries.

The default JAR is 2,034,888 bytes, SHA-256
afff226e2e04bb4f244c2b0e34acdd17bf4c6d7b46b1bdb6cacdfa2a91f3c90a. The Linux x86_64 CED JAR is
2,176,117 bytes, SHA-256 c6a8942130cd655c098fffc7cc714de61bdd0dbdf8a3140de9478dec444a5716. Both are
under cli/build/libs, require Java 17+ and contain dependency notices. Runtime verification used
OpenJDK 21.0.11. A fresh source-only copy also passes check and builds a byte-identical default JAR.
Exact artifact/test details are recorded in evidence/fatjar-verification.json.

### Parser regression commands

Normal regression gate:

```sh
./gradlew check
./gradlew -PwithCed=true check
```

Optional integration tools:

```sh
python tools/check_corpus.py --archive /path/to/cue.tar.xz --fetch
python tools/check_cli.py --cli /path/to/cuegrain
python tools/check_schema.py --cli /path/to/cuegrain evidence/corpus-results.json
```

Integration tools require their named external test dependencies; they are not library/runtime
dependencies. The private Music/NAS harness additionally requires the owner's authorized read access
and is specific to that test environment.

Ignored evidence includes build-verification.json, corpus summaries for each configuration,
explicit-charset results, CLI integration summaries, sample summaries/reports and warning-free
Android D8 output. Independent source-export build logs are retained in the local implementation
status directory.
