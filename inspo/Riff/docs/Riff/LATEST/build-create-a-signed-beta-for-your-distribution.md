# Building Riff: signed release and maintenance

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
`releaseDate` and `releaseChannel` (ALPHA, BETA or RC) in `config/project.json`. Both Gradle and
`bin/build_release.py` consume those project-wide values. Version/package metadata in the helper
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
[Cuetree build](/Cuetree/build). Cuetree pins flatc and its Java runtime to 25.12.19. The Android
GNRE and MDRG consumers use the vendored Kotlin runtime and compiler 25.12.19. Regenerate their
Kotlin bindings and the GNRE Python binding with:

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
