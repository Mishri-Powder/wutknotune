# Cuegrain

Read-only audio metadata and chapter extraction for Kotlin/JVM and Android consumers. Apache-2.0.
Version 0.1.0; JSON schema version 1.

Cuegrain keeps every metadata source separate. A CUE and an MP3's ID3 chapters, or FLAC comments and
its binary CUESHEET, are independent records, not competing versions of a merged result. It does not
decode audio, apply gain, modify files, fetch artwork URLs or choose a player's source-priority
policy.

Optional [media identity acquisition](https://wiki.271.pics/Cuegrain/API/MediaIdentity) pairs
metadata with an injected MDIA-ID-V3.2 computation over one caller-owned input. AU/random-access,
cancellation and XXH3 are supplied through independent adapters; the core needs no Android
dependency. Ordinary metadata reading does not start identity computation.

## Build

Requires JDK 17 or later. The included Gradle wrapper builds this directory as an independent
project; no Riff checkout, Android SDK or native toolchain is needed for the default build.

```sh
./gradlew check :core:assemble :cli:installDist
cli/build/install/cuegrain/bin/cuegrain --help
```

Core has Kotlin stdlib (and its annotations dependency) only at runtime. JUnit, ktlint, FFmpeg and
Mutagen are test/development tools, not library dependencies. No artifacts have been published to a
public package repository. Install optional Python check dependencies with
`python -m pip install -r tools/requirements.txt` in a virtual environment.

Opt into the vendored Google Compact Encoding Detection backend at compile time:

```sh
./gradlew -PwithCed=true check :cli:installDist
# To select a non-default CMake binary:
./gradlew -PwithCed=true -PcmakeExecutable=/path/to/cmake :cli:installDist
```

This additionally needs CMake, a C++ compiler and JDK JNI headers. With the default `withCed=false`,
the CED Gradle project, native build and binaries are absent. The enabled CLI discovers its backend
through ServiceLoader. The generated CED JAR contains the **build host's** native library, not a
universal native binary. There is no JNA dependency.

## CLI: JSON only

### Single executable JAR

```sh
./gradlew :cli:fatJar
java -jar cli/build/libs/cuegrain-0.1.0-all.jar album.cue track.mp3
```

This JAR includes the CLI, core, Kotlin runtime, schema and dependency notices. It can be copied
anywhere and needs only Java 17+; no adjacent lib directory, Gradle or separate classpath is
required. The default JAR does not include CED.

To include the optional CED backend in the same single file:

```sh
./gradlew -PwithCed=true :cli:fatJar
java -jar cli/build/libs/cuegrain-0.1.0-ced-linux-x86_64-all.jar album.cue
```

The CED filename includes the build host's OS/architecture; the example above is for the Linux
x86_64 dev box. The native library and ServiceLoader provider are inside the JAR. This is not a
multi-platform native binary. The existing directory-based CLI distribution remains available. All
CLI options below also work after java -jar JAR.

### Options

```sh
cuegrain album.cue track.mp3 book.m4b
cuegrain --charset windows-1251 album.cue
cuegrain --strict album.cue
cuegrain --follow-cue album.cue
cuegrain --repair --artwork track.flac
cuegrain -- --filename-starting-with-dash.mp3
```

Every normal invocation emits one JSON document on stdout, including help, version, invalid
arguments and input failures. See [schema.json](schema.json). The result envelope has
`schemaVersion`, `ced` and `results`; each report has `input`, `status`, `sources` and
`diagnostics`. Exit codes are 0 for success, 1 for any input/parse failure (including partial
reports), and 2 for invalid arguments. Warnings alone do not fail a compatible-mode parse.

- `--strict`: report supported nonconforming oddities as errors while retaining recovered values.
  Compatible mode is the default.
- `--charset NAME`: explicit charset for standalone CUE bytes without a BOM. Format-defined
  ID3/MP4/Vorbis encodings and CUE BOMs take precedence.
- `--repair`: opt-in conservative source-wide mojibake repair, preserving original tag values.
  Requires a detector; without one it leaves text alone.
- `--artwork`: explicitly include embedded picture bytes as base64.
- `--follow-cue`: explicitly read files named by external CUE FILE directives, relative to the CUE
  directory. Windows path separators are normalized on the CLI host. No extension guessing, search
  or network fetching occurs. Only use this on trusted CUE files: references can name files outside
  that directory. Without the option, references are returned but never followed.
- `--help`, `--version`: JSON descriptions.

Multiple inputs produce multiple independent reports. Following a CUE does not merge the referenced
files' tags into its tracks. A missing referenced file is an input error, not an empty successful
tag record.

## Kotlin API

```kotlin
import java.io.File
import link.roadio.cuegrain.*

FileInput(File("album.cue")).use { input ->
    val report = Cuegrain().read(input, "cue")
    println(Json.encode(Json.report(report)))
}
```

`MediaInput` is a random-access, length-known read interface. Implement it over an Android file
descriptor or a caller-owned storage abstraction; core does not depend on Android or Media3. Reads
must return exactly the requested byte count or throw an I/O exception. The caller owns input
lifetime. Keep inputs open until all requested `Artwork.readBytes()` calls finish.

`Cuegrain.readGroup` returns independent reports. `readWithReferences` accepts an explicit
`FileResolver`, binds CUE file references to returned input IDs and returns additional independent
reports. Core never opens referenced paths by itself, and never closes handles supplied by a
resolver.

An embedded CUE records its container as `attributes.embeddedIn`. Explicit FILE names remain
unresolved unless the caller binds them; only a missing FILE is implicitly bound to the containing
input. The CLI follows external sheets only.

To add a format, implement `FormatReader` and provide it in the constructor's `readers` list
alongside the desired built-in readers. Readers use the shared bounded `ParseContext` and return the
same source model. Matroska is not implemented in this version.

## Source and timing model

Source kinds include `cue`, `id3`, `id3v1`, `lame`, `mp4`, `mp4.quicktime`, `mp4.nero`, `flac`,
`flac.cuesheet`, `flac.picture`, `vorbis` and `opus`. An array permits more than one instance of
each kind; `id` and `location` retain provenance.

Tags preserve order, duplicates, original keys, original text and locations. `metadata` is a
convenience view mapping common fields to arrays of values. Unknown text fields remain available in
`tags`. Binary tag interpretation is format-specific; this is not a lossless container/frame
archival API.

`riffFeatures` is the typed view of `RIFF_AROUSAL`, `RIFF_VALENCE`, `RIFF_PTE`, their optional
`_CONFIDENCE` partners, and ordered repeatable `RIFF_GENRE` values. A genre confidence must
immediately follow its genre, which prevents a missing confidence from shifting later pairings. Only
finite locale-independent decimal floats are accepted. Missing confidence is `null`; values are not
clamped or rounded. CUE `REM` fields before `TRACK` belong to the CUE source and fields inside a
track belong to that entry. Raw fields remain in `tags` even when typed validation fails.

Positions are `{file, time: {ticks, timescale}}`: CUE frames use 75 ticks/sec, ID3 chapters use
milliseconds, and FLAC uses its sample rate. Compare rational values, not rounded display
milliseconds. Values are signed 64-bit integers in the API; JSON consumers needing exact values
above 2^53 must use a lossless integer parser. File-local times are not an invented concatenated
timeline.

CUE INDEX 00, INDEX 01, additional indexes, PREGAP and POSTGAP remain distinct. Each index has its
own file reference, including EAC cross-file pregaps. Unknown ends are null; inferred ends have
`endOrigin`. Separate-file CUE tracks can all start at zero without overlapping on a synthetic
global timeline. Non-audio tracks are represented but do not imply playback support.

ID3 CTOC relationships are preserved as an ID graph; consumers can flatten them if appropriate.
QuickTime and Nero chapters are emitted separately, even when both occur in one file. Chained Ogg
streams retain their own serial-number identity and timeline.

## ReplayGain, R128 and pictures

Gain observations retain kind, scope, unit, raw text and explicit reference information. A valid
zero is retained, and peaks greater than one are valid. ReplayGain gain/peak, R128 Q7.8 gain, Opus
header gain, LAME gain and iTunes SoundCheck observations are not combined. SoundCheck conversion
uses the first gain-word pair; opaque original words are retained. LAME observations retain
originator information. The library does not infer a player target or reinterpret a reference absent
from the metadata. In particular, it does not add Opus header gain to R128 gain.

Artwork descriptors are returned by default. Bytes are exposed only through `readBytes()` or CLI
`--artwork`; external URLs are never fetched. Container metadata blocks may already contain picture
bytes while parsing (ID3, MP4 and Vorbis comments), so this is deferred extraction, not a guarantee
of zero picture-byte I/O.

## Encoding and limits

BOM, explicit charset and strict UTF-8 work without CED. With CED enabled, detection uses at most 64
KiB, QUERY_CORPUS, no language/encoding/URL/HTTP hints, and `ignore_7bit_mail_encodings=true`.
Charset, reliability and bytes consumed are exposed in CUE source attributes or failure diagnostics.
An unreliable or unsupported legacy candidate produces `ENCODING_REQUIRED`; supply the known charset
instead of relying on silent fallback.

`ParseOptions` exposes compatible/strict mode, detector, charset, repair, cancellation and `Limits`.
Defaults: 32 MiB per metadata read, 128 MiB aggregate read budget, 10,000 entries, 65,536 fields, 1
MiB per bounded text field, 16 nesting levels, 1,000 diagnostics and 100,000 parser elements.
Budgets are per input and are safety bounds, not a validity guarantee. Cancellation propagates as
`CancellationException`.

Readers cover ID3v2.2/2.3/2.4 and ID3v1; compressed/encrypted frames are diagnosed rather than
decoded. MP4 ordinary tags cover iTunes-style `ilst`, not arbitrary `mdta` key tables. QuickTime
chapter support is for referenced text tracks with supported sample tables and simple edits, not
fragmented MP4. Ogg CRC validation and audio-stream validation are outside scope. CUE charset choice
does not override encodings prescribed by a binary format.

## Android consumers

This build uses Kotlin 2.3. Android consumers need a compatible D8/R8 version; 8.13.19 was used for
the dex check. See the
[official Kotlin compatibility table](https://developer.android.com/build/kotlin-support).

Core is a JVM 17 JAR intended for D8/desugaring in an Android app; it has no manifest, permission or
lifecycle dependency. Riff consumes core as a Gradle composite source dependency and compiles the
optional CED bridge/native sources for Android. Current integration rules and device verification
belong in the [Riff integration contract](https://wiki.271.pics/Riff/LATEST/cuegrain-integration).
The independent [Cuegrain documentation](https://wiki.271.pics/Cuegrain) includes the
[class and method API reference](https://wiki.271.pics/Cuegrain/API) and runnable examples.

For CED, build the native library per ABI with the Android NDK and package the result as
`src/main/jniLibs/<ABI>/libcuegrain_ced.so` in the consuming Android module. Use the CED JVM classes
with core, retain their JNI names using [ced/consumer-rules.pro](ced/consumer-rules.pro), and
instantiate `CedEncodingDetector` explicitly or preserve its service provider.

```sh
cmake -S ced/native -B ced/build/android/arm64-v8a \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-31 \
  -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
cmake --build ced/build/android/arm64-v8a
```

The library uses `System.loadLibrary` on Android; host-JVM extraction is not used there. Android
native packaging is explicit, not an automatically generated multi-ABI AAR. The tested build matrix
is recorded in [VERIFICATION.md](VERIFICATION.md).

## Tests, provenance and license

`./gradlew check` runs deterministic synthetic regression and malformed-input tests. Optional
integration scripts in `tools/` use external private corpora or FFmpeg/Mutagen as independent test
oracles. These are not runtime dependencies. Large private media and downloaded third-party examples
are ignored under `evidence/` and excluded from source distributions.

See [CONTRACT.md](CONTRACT.md), [SOURCES.md](SOURCES.md), [NOTICE](NOTICE) and [LICENSE](LICENSE).
The vendored CED source retains its original Apache-2.0 notices. User media and third-party test
corpora are not relicensed by this project's license.
