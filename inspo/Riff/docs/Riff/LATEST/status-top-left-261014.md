# Passive status top-left correction — delivered 2026-10-14

Dated 0.1.5 same-version development delivery, superseded by the committed
[current release](/Riff/LATEST/rc-0-1-6-delivery-261014). Claims below describe that earlier
artifact.

The user rejects the slider-relative/bottom placement after device evaluation and requests physical
top-left, zero-gap status plus a future FINAL default-off rule. The correction is implemented and
signed 0.1.5-rc/versionCode 8/RC 261014 is installed in place on phone and HU. Source remains
uncommitted on main based on e8eaaada3e981f0707f10b53a0544dea4db35f5d.

## Adopted behavior

[Playback status](/Riff/LATEST/player-transport#playback-diagnostic-status) and
[MAIN status](/Riff/LATEST/main-screen-scope-and-platform-boundary#status-line) share one decorative
overlay at the physical top-left system-safe corner. Its layout box has no additional gap or 24dp
screen-edge margin; system-bar/cutout insets remain. Physical left stays left in RTL. The viewport
anchor is independent of scrolling, transport geometry, font size and orientation. The overlay
paints below artwork/foreground, reserves no slot and has no input/focus/accessibility behavior.

The slider/font/bottom offset and scroll translation are removed. MAIN supplies the same underlay
before SafeContent's existing padded foreground; other surfaces retain their measured safe geometry.
Text uses the existing 20sp monospace, palette, shaping/ellipsis and playback halo, with physical
left alignment. The accepted 120dp button layout, audiobook slots and commands remain unchanged.

The
[file-only configuration owner](/Riff/LATEST/project-configuration-file-only-values#final-status-default)
requires a future FINAL distribution to package `behavior.enableStatusLines=false`. Current RC and
other non-FINAL builds retain true. The mechanism remains, with explicit user-file enabling through
the ordinary startup merge:

```json
{ "skins": { "default": { "behavior": { "enableStatusLines": true } } } }
```

This is the requested conditional rule, not a current channel switch or a claim that a FINAL APK was
built. Current signed build policy remains RC; no runtime channel gate, Settings control, parser or
reload mechanism is added. The configuration registry routes its per-field table/defaults to a
bounded child contract; its original anchors remain valid. Scoped owners remain within the
reading-size budget. The
[official-source review](/Riff/LATEST/aaos-conformance#passive-status-top-left-review) records
common phone/direct-Automotive behavior and unchanged host/motion boundaries.

## Fresh checks and delivery

297 Player JVM cases, 15 HU Android cases and six phone Android cases pass with no failures/errors.
Kotlin checks, Android lint, disposable APK builds and signed distribution/model/alignment/signature
checks pass. Existing configuration code/default JSON is unchanged by this correction; the preceding
29 configuration cases are dated evidence, not newly rerun here.

The real-screen fixture measures exact top/left overlay bounds in portrait/landscape, WIDE portrait,
NARROW landscape, LTR/RTL and fontScale 1.0/2.0. It verifies fixed position during foreground
scroll, no semantic text/actions/focus/live region, background pointer pass-through, hidden-state
geometry, visible exposed status pixels, opaque artwork precedence, ordinary transport/seek
commands, stable book speed/Bookmark slots and full-height controls in a short window. MAIN resize,
AI availability, release warnings and shared SafeContent/top-bar/Settings policy regressions also
pass. These local tests do not establish vehicle-host conformance.

Commands with ANDROID_HOME set to ~/Android/Sdk:

```sh
./gradlew :app:ktlintFormat
./gradlew :app:testDebugUnitTest :app:ktlintCheck :app:lintDebug \
  :app:assembleInstrumented :app:assembleInstrumentedAndroidTest
```

The isolated device runners record exact class lists. The signed helper then builds
`releases/261014-rc-status-top-left/riff-0.1.5-rc-261014.apk` (106,978,512 bytes).

- APK SHA-256: `02ccc27988cd6246864b4fcdfa0edad7ccffca6ac2dd0de36a7b67aa3fcf2236`.
- Certificate SHA-256: `8f1dce0e9f5ae50fbead23ddb3d60182f9af4b3a584481eeee1f3df5c499e67d`.
- Packaged model SHA-256: `8819c2b3723d448dc63e92c12135a57eab00a3f867697439e4eb327bc6f32813`;
  unchanged.

Both installations use explicit-serial `adb install -r`. Exact pulled bytes match the manifest;
certificate, UID, first-install time and exact persisted grants survive. Both Activities launch and
remain alive without a matching bounded startup FATAL EXCEPTION. HU Player backup succeeds (740,864
bytes); full private phone backup remains unavailable to unprivileged ADB. No uninstall, app-data
clear, manual reset or format change occurs. Actual working playback captures are checked without
media/playback/seek/queue commands, using only the ordinary surface Flip when needed.

Private source/build/test/install/real-screen evidence remains under
`status/status-top-left-261014/` on the dev box. The preceding
[120dp layout delivery](/Riff/LATEST/playback-layout-261014) owns its older same-version artifact
and status position. No Git history or website changes are requested. Outgoing Wiki editions are
archived verbatim, followed by exact API/public-route readbacks and the whole-Riff publication
audit.
