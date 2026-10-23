# Playback layout and passive status — verified 2026-10-14

Dated delivery before the top-left status correction. [Current delivery](/Riff/LATEST/tldr) owns the
latest installed phone/HU bytes and status position; this report retains the preceding artifact.

The user-approved local playback layout is implemented in the uncommitted `main` working tree based
on `e8eaaada3e981f0707f10b53a0544dea4db35f5d`. At that follow-up request, its signed APK was
installed in place on phone and HU. Identity remains 0.1.5-rc/versionCode 8/RC 261014; the bytes and
feature set now differ from the preceding same-version release. Website publication retains that
preceding committed-source artifact.

## Behavior and owners at this delivery

[Playback transport](/Riff/LATEST/player-transport#playback-surface-and-transport) owns the portrait
REW/FWD row, 24dp gap, Shuffle/Play/Repeat row, unchanged slider and stable Ban/Dislike/Like slots.
Landscape keeps its original five-button transport row. The complete block sits at the safe bottom.
One shared action renderer preserves commands, eligibility, selected state and audiobook slot
substitution. Short windows scroll at full control heights with the initial view at the bottom.

The new file-only `skins.default.geometry.playbackButtonHeight` defaults to 120dp. The shared JVM
schema admits finite responsive geometry at or above 76dp and the resolved common target minimum. It
uses the ordinary immutable startup configuration, with no Settings row. Hierarchy/list and Settings
target dimensions retain their own tokens.

[Status ownership](/Riff/LATEST/player-transport#playback-diagnostic-status) and
[MAIN](/Riff/LATEST/main-screen-scope-and-platform-boundary#status-line) define one shared
decorative status object. Both old 36dp reserved slots are removed. MAIN paints status at the safe
bottom below the grid/chrome. PLAYBACK paints it before artwork/foreground, one resolved status-font
height above the existing 64dp seek interaction area's upper edge; user font scaling participates.
The overlay follows the control block when scrolling. Hidden/empty status reserves no space.
Descendant text semantics are cleared; there is no status action, focus or input handler. Existing
status vocabulary, font, colors, ellipsis, visibility setting and playback halo are retained.

The explicit viewport-aspect exception applies only to local playback row packing. General
WIDE/NARROW, artwork, physical density and per-mode gates are unchanged. The
[official-source review](/Riff/LATEST/aaos-conformance#playback-controls-and-passive-status-layering-review)
was published before implementation; actual AAOS/projected hosts retain their supported media UI.

## Fresh verification

| Check                                                   | Result                                 |
| ------------------------------------------------------- | -------------------------------------- |
| Configuration JVM                                       | 29 passed, zero failures/errors/skips  |
| Player JVM                                              | 297 passed, zero failures/errors/skips |
| Kotlin checks, Android lint and disposable APK assembly | Passed                                 |
| HU Android 13/API 33                                    | 15 passed                              |
| Phone Android 16/API 36                                 | 6 passed                               |
| Maintained Markdown and current owner/anchor checks     | Passed                                 |

The four new UI cases exercise 600×1400 and 900×1400 portrait, 1200×800 and 700×600 landscape,
LTR/RTL, Day/Night, fontScale 1.0/2.0, full equal target heights, safe bottom/24dp row gap, actual
scaled status offset, no status text/actions in accessibility, exposed-background and underlying
control pointer pass-through, status-hidden geometry independence, opaque artwork precedence,
transport/seek commands, audiobook/Bookmark slot continuity and a vertically constrained 600×450
window with reachable full-height controls. Existing speed short/long-press, Book reaction absence,
MAIN resize/AI readiness/release warning and top-bar/Settings policy regressions also pass on HU.
Phone additionally repeats speed and MAIN resize cases. Saved synthetic portrait/landscape and MAIN
captures were visually inspected; these are local rendering/interaction checks, not an installed
release or vehicle-host acceptance claim.

Initial test failures were fixture expectations: nonlinear Android font scaling, distinguishing Play
from background Flip, applying real Samsung bars once before synthetic windows, and sampling exposed
MAIN background outside its disabled centered final tile. These fixes required no product behavior
changes. Format/check tasks were then run sequentially to avoid checking pre-format input. All final
device cases use the corrected same fixture source.

From the dev-box checkout with ANDROID_HOME set to ~/Android/Sdk:

```sh
./gradlew :app:ktlintFormat :configuration:ktlintFormat
./gradlew :configuration:test :configuration:ktlintCheck \
  :app:testDebugUnitTest :app:ktlintCheck :app:lintDebug \
  :app:assembleInstrumented :app:assembleInstrumentedAndroidTest
```

`status/playback-layout-261014/run_android.py` and `run_phone.py` install only
`link.roadio.riff.instrumented` and its runner, then execute the exact recorded class lists with
explicit serials. Logs, device results, full source patch/inventory, initial failures and snapshots
remain under `status/playback-layout-261014/` on the dev box. The subsequent explicitly authorized
working-device installation is recorded below. No working-app uninstall, data clear, version bump,
Git commit/merge/push or website update was performed.

## Signed working-device installation — 2026-10-14

The follow-up request authorizes installing the accepted layout on both devices. The ordinary
`bin/build_release.py` builds from the exact recorded uncommitted source and verifies distribution,
model packaging, alignment and signature. Its new, non-overwriting output is
`releases/261014-rc-playback-layout-120/riff-0.1.5-rc-261014.apk` (106,978,512 bytes).

- APK SHA-256: `ca4b077cbcb66061d808c8c98203a4fadf3d8df1280a7a4266d1be8d19e086bc`.
- Signing-certificate SHA-256: `8f1dce0e9f5ae50fbead23ddb3d60182f9af4b3a584481eeee1f3df5c499e67d`.
- Level 2 model SHA-256: `8819c2b3723d448dc63e92c12135a57eab00a3f867697439e4eb327bc6f32813`;
  unchanged.

Both updates use `adb install -r` with explicit serials. Preflight/incoming/installed certificates
agree. Exact pulled installed APK bytes equal the manifest on both devices; version, UID,
first-install time and exact persisted grants are retained. Both Activities report successful
launch, remain alive and have no FATAL EXCEPTION in the bounded startup logs. No uninstall, app-data
clear, format change or manual reset occurred. The HU Player private backup succeeds (757,248
bytes); a complete private phone backup remains unavailable to unprivileged ADB.

The actual working phone and HU playback surfaces are also checked through UIAutomator and saved
screenshots. Phone portrait shows two transport rows; HU landscape retains its single transport row.
Visible transport/reaction controls have equal measured heights, and diagnostic status is absent
from accessibility. Only the surface Flip is invoked where needed; no playback, seek, queue or
media-write command is issued. The shared implementation's preceding focused device tests own exact
dp, safe-bottom, font-scale, hidden-status geometry and full input checks.

Build/install provenance, complete earlier source patch, continuity/backup/startup receipts and real
working-screen evidence are under `status/playback-layout-261014/delivery/`. Source remains
uncommitted; no Git history or website action was requested.

Outgoing Wiki editions are archived verbatim. Exact API/public-route readbacks and the whole-Riff
publication audit accompany this handoff. Existing host/performance gaps remain in
[open work](/Riff/LATEST/open-decisions).
