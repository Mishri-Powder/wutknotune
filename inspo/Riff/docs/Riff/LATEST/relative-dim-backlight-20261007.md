# Relative day/night idle brightness — RC 261007

## Adopted behavior

The user adopts `skins.<name>.behavior.dimBacklightPercent`, a finite percentage in 0..100, scalar
or day/night. Packaged defaults are day 30 / night 30 on every installation. It scales the
independent Android system setting only after the existing idle timeout, using the resolved rendered
theme. Baseline 128 and 10% produce 12.8 units on the 0..255 scale, approximately 5% of maximum.
Input, lifecycle, applicability, blocked-flow and fullscreen gates retain their
[inactivity owner](/Riff/LATEST/player-inactivity). The shared startup loader owns validation,
four-layer deep merge and the single process read in the
[configuration contract](/Riff/LATEST/project-configuration).

The independent `systemBrightnessWorkaroundEnabled` remains a scalar Boolean, packaged false, with
no theme variants. Only the explicitly selected direct HU receives this partial Internal user file
and WRITE_SETTINGS special access; the working phone retains its ordinary window path:

```json
{
  "skins": {
    "default": {
      "behavior": {
        "systemBrightnessWorkaroundEnabled": true,
        "dimBacklightPercent": { "day": 15, "night": 1 }
      }
    }
  }
}
```

The operator provisioner preserves valid existing user keys and validates both widths/themes through
the production owner. Other leaves inherit, including idle timeout 30 seconds, pipeline depth 3 and
bookmark spacing 15 seconds. Two disposable reset/provision cycles and the working install/update
verify this actual partial-file merge. Install/update or an explicit data reset uses the same
operator procedure before cold startup; there is no device-identity exception in product startup.
Strict AAOS excludes the workaround. The
[official-source review](/Riff/LATEST/aaos-conformance#relative-daynight-idle-brightness-review)
retains local/host limits.

## Corrected interaction between controls

The first working-HU probe exposed feedback when both the global setting and window override were
applied. At night a requested system zero became 1, losing lease ownership and the independent
baseline. Longer checks therefore supersede the initial short passing fixture. Successful
manual-mode system control now leaves the window override at `BRIGHTNESS_OVERRIDE_NONE`; denied
access, automatic mode, unavailable baseline and write/persistence failure retain the window
fallback. Worker acknowledgements cannot override a newer input/lifecycle release. System brightness
mode changes are observed as well as brightness changes.

A second system-only probe kept the public setting at zero while the panel retained its preceding
level. The user rejected a special minimum/zero correction and chose a positive night target of 1%
for HU provisioning. The final global transport is simply round(baseline × percent / 100), including
zero; there is no clamp, zero-specific branch or device-identity calculation. At the working HU
baseline 102, 1% naturally rounds to 1. The configuration still accepts zero; the observed platform
limitation is documented rather than hidden by product code. No root, hidden API, sysfs write, MCU
command or new HU RE was used. Known panel-driver reads and standard Android APIs served deployment
verification.

## Fresh verification

Final source checks pass 27 configuration and 276 Player JVM cases, zero failures/errors/skips,
ktlint, Android lint with zero errors and 23 existing warnings, disposable assembly and signed
distribution validation. Prettier and Markdownlint pass 189 maintained/exported documents;
current-reference checks pass 548 maintained files against 184 scoped current Wiki pages. Release
permissions are exactly four, including the previously approved WRITE_SETTINGS declaration. Five
build-tool Python tests also passed during this task; the build tooling itself was unchanged.

Six distinct HU device cases pass: packaged-default window, themed window, enabled/default system,
themed system, denied access and automatic mode. Two phone cases pass: packaged default and themed
window. The system-theme fixture now holds the night request for two seconds, then checks 15% → 1% →
15%, external baseline 180, input restoration, background release and resume grace. Final
plain-calculation source reruns all six HU and both phone cases, alongside JVM/style/lint/ release
checks; the earlier short/raw-zero/minimum-candidate results are superseded.

Both working installations pass the shipping 30-second timeout, draft-preview theme changes while
idle, input release and original profile-theme preservation. The temporary helper dispatches
existing Riff commands and reads its own classes; it does not save theme changes. Final known-driver
and window observations are:

| Phase                     | HU system setting | HU panel driver | Phone system setting | Phone window override |
| ------------------------- | ----------------: | --------------: | -------------------: | --------------------: |
| active                    |               102 |             102 |                   80 |           -1.00000000 |
| normal-timeout            |                15 |              14 |                   80 |            0.09411765 |
| opposite-theme-while-idle |                 1 |               1 |                   80 |            0.09411765 |
| original-theme-while-idle |                15 |              14 |                   80 |            0.09411765 |
| input-restores            |               102 |             102 |                   80 |           -1.00000000 |

HU uses no window override throughout this cycle and preserves manual mode. Its day result is
round(102 × 15%) = 15; its night 1% naturally rounds to setting 1 and reaches panel level 1. Phone
applies 80 × 30% / 255 = 0.09411765 in both themes, leaves its system preference 80 and automatic
mode unchanged, and returns its window to -1 on input. Display brightness reason confirms the window
override on phone. Driver granularity and Android transition timing are observations, not a
photometric calibration.

The ingress HU snapshot captured 34 while the preceding Riff edition was already dimmed. Its saved
window request 0.13333334 and preceding one-third policy identify the independent baseline as 102,
also recorded by the preceding delivery. Tests restore 102 rather than that owned dim value. Both
final probes leave no recovery lease. Working UIDs, first-install timestamps and exact persisted
media-grant lists match the preserved pre-install metadata: HU one grant, phone two. Neither working
app was cleared or uninstalled. Disposable target/test and temporary helper packages are removed;
final system preferences, rotation policies and selected application profile are preserved.

## Signed delivery and canonical evidence

Both working devices run RC 261007, release date 2026-10-07, package `link.roadio.riff`, version
`0.1.0-rc`, with installed bytes matching this final artifact:

- Artifact:
  `/home/riff-adb/riff/releases/261007-rc-relative-backlight-15-1/riff-0.1.0-rc-261007.apk`.
- Bytes: 106699984.
- APK SHA-256: `bbb776ea1aab95230b36bd2a39b1a7ddd268ca0f60828d35318a6471fab4f0ca`.
- Unchanged certificate SHA-256: `8f1dce0e9f5ae50fbead23ddb3d60182f9af4b3a584481eeee1f3df5c499e67d`.
- Unchanged Level 2 model SHA-256:
  `8819c2b3723d448dc63e92c12135a57eab00a3f867697439e4eb327bc6f32813`.

The initial dual-control APK and raw-zero configuration are superseded by this delivery. No Git
commit, push, merge or tag was performed; unrelated working files remain intact.

All task evidence stays on the canonical dev box under `status/dim-backlight-percent-20261007`:
`jvm-results.json`, `plain-build-final.log`, `signed-release-plain.log`, device case outputs,
`hu-runtime-results.json`, `phone-runtime-results.json`, known-driver phase dumps,
`working-hu-provision-plain/result.json` and `final-preservation.json`. The failed probes are
retained in separate diagnostic folders. Standard build command:

```sh
ANDROID_HOME=/home/riff-adb/Android/Sdk ./gradlew --offline --console=plain \
  :app:testInstrumentedUnitTest :configuration:check :app:ktlintCheck :app:lintDebug \
  :app:assembleInstrumented :app:assembleInstrumentedAndroidTest :app:verifyDistributionRelease
```

Device runners use the explicitly selected HU/phone serials; final runs are
`run_device_tests.py hu 172.16.2.1:7088` and `run_device_tests.py phone R5CW2116W4M`. Signed build
uses `bin/build_release.py --output-dir releases/261007-rc-relative-backlight-15-1`.

The three owning contract editions were staged before implementation; initial live publication was
blocked by approval review, then explicitly authorized by the user. The controller refinement and
final user-selected HU 15%/1% contract were published before their code changes. The
minimum-correction candidate was rejected and removed; its artifact was never installed. All
outgoing editions are archived verbatim. Publication uses explicit public flags, exact API readback,
unauthenticated HTTP 200/title checks and a complete Riff tree audit. Receipts are
`contracts-verification.json`, `controller-verification.json`, `plain-verification.json` and
`delivery-verification.json`. Route checks use the known direct Wiki endpoint because freki cannot
resolve the canonical hostname; canonical HTTPS reachability is not claimed.

Abrupt process termination can still leave an owned global dim value until the next Activity startup
recovers it; external changes and revoked access retain their existing recovery limits. Earlier
interruption evidence is separately dated. This task does not repeat the old temporary-brightness
collision, measure optical luminance, or establish vehicle-host/physical night-glare acceptance.
