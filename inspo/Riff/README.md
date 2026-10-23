# Riff

[User FAQ](FAQ.md) · [Build from Git](BUILD.md)

## License

Riff first-party source code is licensed under the GNU General Public License, version 3 or later
(GPL-3.0-or-later). See [LICENSE](LICENSE). Imported and generated components retain their
compatible upstream terms; see [NOTICE](NOTICE).

## Signed editions

Signed Alpha, Beta, RC and FINAL editions use the persistent signing key and are produced by
`python3 bin/build_release.py --output-dir releases/my-release-build`. It does not promise data,
file-format, or upgrade compatibility; pre-release users may need to rebuild local application data
after updating. The release package includes GPL and retained third-party notices plus the current
content-derived Level 2 model package. Its compile-time `ReleaseIdentity` renders the passive
release-edition mark on MAIN_SCREEN and applies the non-final lifetime policy.

Production Android project for an offline automotive audio player. The Wiki registry at
`/Riff/LATEST/requirements` is the source of truth; this README is only an operational map.

## Active tree

```text
app/       Player process, Cuetree-backed UI, playback and user state
indexer/   Dedicated :indexer process, Cuetree adapters, AI/ReplayGain and publication
references/ Immutable adopted inputs and the normative icon attachment
tools/     Reproducible generators and current device-side validation helpers
LEGACY/    Historical, candidate, and superseded project material
```

Cuetree builds immutable MDIA/ALBM/ARTS/GNRV cohorts for Player. Indexer decodes audio through the
shared acquisition pipeline and publishes optional AI/ReplayGain evidence in MDRG. Player receives
validated read-only descriptors through AIDL and consumes the packaged GNRE dictionary. The
[architecture map](https://wiki.271.pics/Riff/LATEST/architecture-decisions) links each decision to
its owning contract.

## Implementation sequence

The registry at `/Riff/LATEST/requirements` routes to the current scoped contracts. Engineering
gates live in `/Riff/LATEST/engineering-verification`; current implementation and measured evidence
live in TL;DR/worklog. This README does not restate requirements.

## Verification

```bash
./gradlew :indexer:testDebugUnitTest :indexer:ktlintCheck \
  :app:testDebugUnitTest :app:ktlintCheck :app:lintDebug :app:assembleDebug
```

See [BUILD.md](BUILD.md) for the SDK/NDK/CMake setup, signing and optional compiler setup and
schema-generation commands. Useful maintenance entry points are:

```bash
./gradlew verifyConfusables
python tools/media-id-benchmark/run_android.py MEDIA_FILE --serial SERIAL
```

The schema-v3 baseline is `app/src/main/config/ui_config.json`; only `skins.default` ships in the
APK. The shared `:configuration` module validates defaults at build time and generates immutable
Kotlin data, while the packaged JSON remains available for inspection. Exact adopted drawable XML
stays in `app/src/main/res/drawable` and uses standard Android rendering, independently of skins.

The optional `<active-data-root>/player/ui_config.json` is read once per process after selected-root
admission, off the main thread. Partial global settings and named skins use the registered
four-layer merge. Invalid files are rejected atomically under `RiffConfig`, with packaged defaults
and a bounded startup notice. File edits take effect on process restart. Theme/window changes select
cached values without file I/O. See the owning
[configuration contract](https://wiki.271.pics/Riff/LATEST/project-configuration) and
[documentation checks](tools/documentation/README.md).

The optional HU brightness workaround is disabled in packaged defaults. Its separate controller
requires WRITE_SETTINGS special access only when explicitly enabled. The
[HU provisioning procedure](tools/hu-backlight/README.md) installs a minimal Internal user file and
verifies startup deep merge after install or an explicit reset; other devices keep default behavior.

Idle backlight targets are `skins.<name>.behavior.dimBacklightPercent`: a finite percentage from 0
to 100, scalar or day/night, default day 30 and night 30. The percentage scales the independent
system setting only after timeout. It applies to the ordinary window path and optional system
workaround alike. A successful global lease leaves the window override unset, preventing platform
feedback between both controls; failure or unavailable access retains the window fallback. The
direct-HU provisioner sets day 15/night 1; its enable Boolean remains scalar.

Git history is written only on explicit request. Record authorized commit hashes and material
validation in `/Riff/LATEST/worklog`.
