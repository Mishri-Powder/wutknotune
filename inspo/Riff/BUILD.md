# Building Riff from Git

These instructions build the Android app from a source checkout. They do not require the project's
development machine, a particular testing device, or access to Wiki.js. The application version in
this checkout is 1.0.2; see [FAQ.md](FAQ.md) for user-facing limitations and [LICENSE](LICENSE) /
[NOTICE](NOTICE) for redistribution terms.

## 1. Get the repository

Install Git and clone the current public clean-cut source distribution:

```sh
git clone https://roadio.link/repo/riff.git riff
cd riff
git status --short
git rev-parse HEAD
```

The public repository contains the latest website source snapshot without development history. The
website release manifest identifies its APK and source edition; an installed development RC may be
newer than that separately published snapshot. Preserve the matching source archive or checkout if
reproducing a release.

The configured maintainer remote is `git@localhost:riff.git`. It requires the maintainer's local SSH
Git service and authorization, and is separate from the public clean-cut distribution.

The repository includes the compact_enc_det sources, xxHash, generated Kotlin and Python FlatBuffers
bindings, Unicode data, GNRE genre data, icons and the Level 2 model package. No submodule
initialization, Git LFS download, model training or taxonomy retrieval is needed for an ordinary
build. Use a full checkout: source folders without their binary assets are insufficient.

## 2. Install host prerequisites

The verified host is Linux x86-64 with OpenJDK 21 and Python 3.12+. JDK 17 is the minimum for AGP;
the Android sources target Java 17. Windows and macOS may use equivalent SDK/JDK installations, but
the shell examples and validation here are Linux-based. Android Studio is optional.

On Ubuntu with Python 3.12 or newer available in its repositories:

```sh
sudo apt-get update
sudo apt-get install git openjdk-21-jdk python3 python3-venv unzip zip xz-utils
java -version
python3 --version
```

If using multiple JDKs, set `JAVA_HOME` to the chosen JDK directory and prepend `"$JAVA_HOME/bin"`
to `PATH`. Ensure both `java` and `keytool` come from that JDK. The build's Python helpers need the
standard library, including SQLite and LZMA. Third-party Python packages are only needed for the
optional host tools/tests below.

| Component                    | Repository version |
| ---------------------------- | ------------------ |
| Gradle wrapper               | 9.8.1              |
| Android Gradle Plugin        | 9.4.1              |
| Compose compiler plugin      | 2.4.21             |
| Compile / target SDK         | 37.2 / 36          |
| Minimum device API           | 31 (Android 12)    |
| SDK Build Tools              | 36.0.0             |
| NDK                          | 29.0.14206865      |
| SDK CMake                    | 3.31.6             |
| Cuetree FlatBuffers compiler | 25.12.19           |

Use `./gradlew`; do not install an unrelated system Gradle. AGP provides built-in Kotlin support.
The NDK and CMake are selected by `indexer/build.gradle`.
[AGP compatibility reference](https://developer.android.com/build/releases/agp-9-4-0-release-notes).

## 3. Install the Android SDK

Download **Command line tools only — Linux** from the
[official Android downloads](https://developer.android.com/studio#command-tools), accept its license
and verify the archive against the published checksum. Set `RIFF_COMMAND_TOOLS_ZIP` below to the
downloaded file. For a new SDK directory:

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
RIFF_COMMAND_TOOLS_ZIP="$HOME/Downloads/REPLACE_WITH_DOWNLOADED_COMMAND_TOOLS.zip"
mkdir -p "$ANDROID_HOME/cmdline-tools"
unzip "$RIFF_COMMAND_TOOLS_ZIP" -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-37.2" "build-tools;36.0.0" \
  "ndk;29.0.14206865" "cmake;3.31.6"
```

For an existing SDK, retain its command-line-tools installation and install only the missing
packages. Do not overwrite an existing `latest` directory. Keep SDK environment variables
consistent. `local.properties` may point Gradle at an SDK, but the signing helper uses
`ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then `~/Android/Sdk`; exporting the same SDK path is the
simplest way to keep both aligned.
[Official sdkmanager instructions](https://developer.android.com/tools/sdkmanager).

Internet access is required for initial SDK installation and Gradle/Maven dependency resolution.
Builds do not retrieve media, genre taxonomy or training data. `./gradlew --offline ...` works only
after the required dependencies are cached.

## 4. Build and verify

From the checkout root:

```sh
./gradlew --version
./gradlew :configuration:test :configuration:check \
  :indexer:testDebugUnitTest :indexer:ktlintCheck \
  :app:testDebugUnitTest :app:ktlintCheck :app:lintDebug \
  :app:assembleDebug :app:verifyDistributionRelease
python3 -m unittest discover -s bin -p 'test_*.py'
```

For the broader Gradle verification and isolated instrumentation APKs:

```sh
./gradlew check :app:assembleInstrumented :app:assembleInstrumentedAndroidTest :indexer:assembleDebugAndroidTest
```

The build validates the complete schema-v3 default configuration through the shared JVM registry,
compiles it into immutable Kotlin data, validates packaged GNRE, Unicode data and Kotlin-only
FlatBuffers, packages ordinary VectorDrawable APK resources independently of UI configuration, and
packages the Cuetree core and its pinned FlatBuffers Java runtime. The release verifier checks
licenses, model inventory/hashes and uncompressed model packaging.

| Output                            | Path                                                                              |
| --------------------------------- | --------------------------------------------------------------------------------- |
| Debug APK                         | `app/build/outputs/apk/debug/app-debug.apk`                                       |
| Unsigned release APK              | `app/build/outputs/apk/release/app-release-unsigned.apk`                          |
| Disposable instrumentation target | `app/build/outputs/apk/instrumented/app-instrumented.apk`                         |
| App instrumentation APK           | `app/build/outputs/apk/androidTest/instrumented/app-instrumented-androidTest.apk` |
| JVM test reports                  | `app/build/reports/tests/` and `indexer/build/reports/tests/`                     |
| App lint report                   | `app/build/reports/lint-results-debug.html`                                       |

`:app:testDebugUnitTest` is a compatibility alias for the instrumented variant's JVM tests. Its
debug Android-test aliases also route to the disposable target. These aliases protect user
application data from Gradle's connected-test uninstall.

For documentation changes, run the pinned [Markdown checks](tools/documentation/README.md). Use
`:app:testInstrumentedUnitTest --tests 'PATTERN'` to filter JVM tests; the `:app:testDebugUnitTest`
forwarding task does not accept `--tests`.

## 5. Install locally or run device tests

Use any compatible Android 12+ device or emulator with debugging enabled and ADB authorization.
There are no trusted devices, model exceptions or privileged test serials. Select a serial solely to
route ADB traffic:

```sh
adb devices -l
export ANDROID_SERIAL='REPLACE_WITH_A_SERIAL_FROM_ADB_DEVICES'
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedInstrumentedAndroidTest :indexer:connectedDebugAndroidTest
```

Debug installs as `link.roadio.riff.debug`; the disposable app target is
`link.roadio.riff.instrumented`; the signed distribution is `link.roadio.riff`. Connected tests may
uninstall their disposable packages and erase their data. Never repoint `testBuildType` to a working
user package. Device test reports are under each module's `build/reports/androidTests/connected/`.

The Indexer correctness suite supplies its own document provider and PCM audio fixture. It needs no
pre-existing media-folder grant or earlier benchmark output. Five corpus-dependent decoder/AU
experiments are explicitly skipped unless `riffMediaVolume` is supplied; their harnesses under
`tools/*benchmark` select the corpus and required parameters. A skipped experiment is not a passed
performance measurement. To measure real media instead, run:

```sh
python3 tools/media-id-benchmark/run_android.py /path/to/audio-file \
  --serial "$ANDROID_SERIAL" --output /path/to/new-result.json
```

The cold headless configuration probe is deliberately isolated: run
`:app:connectedInstrumentedAndroidTest` with
`-Pandroid.testInstrumentationRunnerArguments.class=link.roadio.riff.player.playback.ConfigurationStartupInstrumentedTest`
and `-Pandroid.testInstrumentationRunnerArguments.riffConfigStartupProbe=true`. It creates a
temporary user configuration only in the disposable target, verifies playback startup and no runtime
reread, then removes that file. The ordinary suite explicitly skips this probe to avoid
process-cache leakage between unrelated tests.

Full-stream collision analysis accepts an explicit on-device benchmark JSON path through the
instrumentation argument `riffCollisionInput`. Without it, the test builds its own correctness
fixture; this is not a real-library benchmark.

The ReplayGain physical-output probe is also isolated from the ordinary suite. Its Android test
skips unless the external harness explicitly supplies `riffReplayGainScope=true`; use
[`tools/replaygain-output-experiment/README.md`](tools/replaygain-output-experiment/README.md) only
when the selected speaker output is connected to a VXI-11 oscilloscope through a safe dummy load.
Absence of that equipment is not a failed application test.

A separate APK-free experiment measures frequency and Android system-volume response through
`tinyplay` or a temporary native AAudio helper; see
[`tools/audio-output-response-experiment/README.md`](tools/audio-output-response-experiment/README.md).
It is a manually invoked hardware measurement and is never part of the Gradle test graph.

An emulator run does not establish head-unit performance, physical calibration, power-loss
resilience or vehicle-host conformance. Record device/API/window details as evidence; device
identity never changes the pass criteria.

## 6. Create a signed release for your distribution

The maintainer's private key is not in Git. To establish a new, independent signing identity on a
fresh checkout:

```sh
python3 bin/create_release_key.py
```

This creates owner-only signing material in ignored `.secrets/release-signing/` and refuses to
replace existing state. Keep an encrypted independent backup of the entire directory. For an
existing distribution, securely restore its original signing state instead of generating another
key. A newly generated key cannot update an installation signed by someone else's certificate.

Then build into a new directory:

```sh
python3 bin/build_release.py --output-dir releases/my-release-build
```

The helper runs `:app:verifyDistributionRelease`, aligns and signs the APK, verifies its
certificate, and writes `release-manifest.json` with APK/model hashes. Existing APKs and manifests
are never overwritten. Without `--output-dir` it uses the edition directory under `releases/`.

The edition date is intentionally fixed, not today's build date. When declaring a new edition, set
`releaseDate` and `releaseChannel` (ALPHA, BETA, RC or FINAL) in `config/project.json`. Both Gradle
and `bin/build_release.py` consume those project-wide values. Version/package metadata in the helper
must also match `app/build.gradle`. Rebuilding the same edition keeps its date.

Verify a produced APK independently, replacing the filename with the actual output:

```sh
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -p 4 releases/my-release-build/RIFF_APK_NAME.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  releases/my-release-build/RIFF_APK_NAME.apk
```

Distribute the verified APK with its corresponding source and required notices. Building or signing
does not upload, publish or install it.

## 7. Optional maintenance and host tests

Ordinary builds use checked-in data. Regenerate only when deliberately updating the associated
schemas/data and review all resulting changes.

For GNRE generator tests, use the Python environment described by its requirements:

```sh
python3 -m venv "$HOME/venv/riff-build"
"$HOME/venv/riff-build/bin/python" -m pip install \
  -r tools/genre-retrieval/requirements.txt
"$HOME/venv/riff-build/bin/python" -m unittest discover \
  -s tools/genre-retrieval
python3 -m unittest discover -s tools/az-icons -p 'test_*.py'
```

For Cuetree schema changes, use the compiler and regeneration commands in
[standalone/cuetree/README.md](standalone/cuetree/README.md). Cuetree pins flatc and its Java
runtime to 25.12.19. The Android GNRE and MDRG consumers use the vendored Kotlin runtime and
compiler 25.12.19. Regenerate their Kotlin bindings and the GNRE Python binding with:

```sh
python3 tools/flatbuffers/generate_kotlin.py --flatc /path/to/flatc-25.12.19
```

Host Cuetree JSON export and source-only builds are documented in the independent library README.

Explicit genre regeneration uses `./gradlew buildGnre`. Supply a contactable identity through
`RIFF_GENRE_CONTACT`; set `-PgenreVenv=...` and `-PgenreCacheDir=...` if changing the defaults. The
retriever manages its declared Python environment/cache; ordinary builds do not call it.
`-PrefreshGenres=true` explicitly refreshes remote data. `./gradlew verifyConfusables` checks the
existing table; `refreshConfusables` is the explicit network-backed refresh.

## Troubleshooting

- **SDK not found / conflicting SDK paths:** export the same SDK location above and remove or
  correct a conflicting ignored `local.properties`.
- **NDK/CMake/license error:** install the exact packages and accept their licenses.
- **Missing model, GNRE, icons or generated bindings:** restore them from the same Git revision. Do
  not substitute a different model or fabricate data to satisfy checks.
- **Dependency download failure:** check access to Google Maven, Maven Central, Gradle Plugin Portal
  and the Gradle distribution service.
- **Signature mismatch on install:** use the original signing key or a separate debug package.
  Uninstalling the existing app removes its app-owned data.
- **Release output already exists:** choose another `--output-dir`; preserve the existing release
  and its manifest.
- **No device / multiple devices:** use `adb devices -l` and set `ANDROID_SERIAL`.
- **Stale generated archive:** read the reported schema/binding mismatch, regenerate the matching
  bindings if needed, then rebuild. A clean build needs no copied cache.
