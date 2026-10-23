# Cuegrain build and CLI

> Owner/scope: standalone distribution and command-line protocol.

Cuegrain remains an independent Apache-2.0 project in `standalone/cuegrain`. Java 17 or later is
required. Build from that directory with `./gradlew check :cli:fatJar`; run
`java -jar cli/build/libs/cuegrain-0.1.0-all.jar album.cue track.flac`. No public package repository
publication is implied. The single JAR contains the CLI, core, Kotlin runtime, schema and notices.

The default build excludes CED and native build tools. Opt in with `-PwithCed=true`; optionally set
`-PcmakeExecutable=/path/to/cmake`. The CED JAR filename identifies the build host OS/architecture.
It is not a universal native binary. Android consumers compile/package Cuegrain's JNI sources per
ABI, preserve its JNI names and use `System.loadLibrary`; desktop extraction resources do not enter
the Android package.

Every normal CLI invocation emits exactly one UTF-8 JSON document, including help, version, argument
errors and input failures. Exit codes: 0 success, 1 input/parse failure including partial reports, 2
invalid arguments. Compatible-mode warnings alone do not fail. The envelope contains
`schemaVersion`, `ced` and `results`; `schema.json` is the machine-readable owner of JSON shape.

Options:

- `--strict`: promote specified recoverable oddities to errors.
- `--charset NAME`: explicit standalone CUE encoding when no BOM is present.
- `--repair`: opt into conservative repair; without a detector text remains unchanged.
- `--artwork`: include explicitly extracted embedded bytes as base64.
- `--follow-cue`: resolve external CUE FILE paths relative to the sheet, normalize Windows
  separators, and return independent reports. References may leave that directory; use on trusted
  sheets only. No extension guessing, search or network access is added.
- `--`: stop option parsing; following arguments are input paths.
- `--help` and `--version`: JSON descriptions.

Following a CUE never merges referenced files' tags into tracks. Missing referenced inputs are
errors. Embedded sheets are not automatically followed. Multiple inputs remain independent.

Run deterministic tests in both CED configurations, CLI isolated-JAR checks, generated-media
integration and JSON Schema validation. External FFmpeg/Mutagen and private corpora are verification
oracles, not runtime dependencies. Exact commands, counts and artifact hashes belong in the current
worklog/report. Repository README and API comments point to this section rather than create
competing library requirements.

## Using core as an external dependency

Build artifacts from the standalone project: `./gradlew :core:jar`. The Maven publication is named
`link.roadio.cuegrain:cuegrain-core:0.1.0`; no public repository hosting is implied. Consumers can
use a locally published artifact or an explicit Gradle composite substitution.

Example consumer `settings.gradle` (adjust the local path):

```groovy
includeBuild('../cuegrain') {
    dependencySubstitution {
        substitute module('link.roadio.cuegrain:cuegrain-core') using project(':core')
    }
}
```

Example consumer `build.gradle`:

```groovy
dependencies {
    implementation 'link.roadio.cuegrain:cuegrain-core:0.1.0'
}
```

The consumer owns its Kotlin/JVM or Android toolchain. Core targets JVM 17. The optional
`link.roadio.cuegrain:cuegrain-ced:0.1.0` publication requires enabling CED and packaging its
host-native resources; Android compiles the original bridge/native sources for its ABIs and retains
JNI names with `ced/consumer-rules.pro`.

For API usage start with [Cuegrain](API/Cuegrain.md) and [FileInput](API/FileInput.md). For native
detection see [CedEncodingDetector](API/CedEncodingDetector.md).
