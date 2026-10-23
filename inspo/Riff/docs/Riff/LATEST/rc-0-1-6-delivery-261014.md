# Riff 0.1.6-rc — committed playback layout release

**0.1.6-rc**, versionCode **9**, **RC 261014** is installed on phone and HU. The website publishes
the same signed APK and matching clean source edition, with 12 new phone screenshots. The user
explicitly requested the version increment, commit, merge, push, Wiki and website update.

## Source and adopted behavior

Implementation **6c7126f47970afbf8c7750fe0c4e82b2df4f78f8** contains 24 reviewed
app/configuration/test and current documentation files. Branch codex/playback-layout-rc-0-1-6 is
merged into main as **2a19027f7c24d2536de6e6ffcb03ca9d356af0e1** and pushed before signing. The
signed build and public source cut use that exact clean release commit/tree. No signing material,
caches, builds, local evidence or device dumps are committed. A final documentation-only follow-up
records delivery separately in worklog.

[Playback transport](/Riff/LATEST/player-transport) owns equal 120dp controls and safe-bottom
anchoring. Portrait has REW/FWD, then Shuffle/Play/Repeat, the slider and stable reaction slots;
landscape retains its five-button transport row. Commands and audiobook speed/Bookmark slots remain.
[Status](/Riff/LATEST/player-transport#playback-diagnostic-status) is the common zero-gap physical
top-left system-safe underlay on MAIN/PLAYBACK, fixed during scrolling with no reserved slot,
input/focus or accessibility content. Current RC status remains enabled. The
[future FINAL default](/Riff/LATEST/project-configuration-file-only-values#final-status-default)
requires packaged default-off while preserving explicit user-file enabling. A-Z RGB subtraction 32,
file-only settings and final-breadcrumb parent behavior remain. Cuetree format 10 and Player schema
15 are unchanged.

## Verification and device delivery

Fresh checks pass 29 configuration and 297 Player JVM cases, shared Kotlin checks, Android lint,
disposable instrumentation assembly, 15 HU Android cases and six phone Android cases. The four
layout/status tests pass on both devices, including orientation variants, RTL, 200% font scale,
120dp targets, safe bounds, fixed top-left status, hidden-state geometry, input pass-through,
semantics absence, artwork precedence, command/seek and audiobook slots. Two older phone-only
fixtures clipped when run with the real phone in landscape; rerunning their six-case suite in its
supported portrait orientation passes. Initial failure logs are retained; no product fix or test
source change was introduced for this release. Local tests do not establish vehicle-host
conformance.

Commands use ANDROID_HOME=~/Android/Sdk:

```sh
./gradlew :configuration:test :configuration:check :app:testDebugUnitTest \
  :app:ktlintCheck :app:lintDebug :app:assembleInstrumentedAndroidTest
```

Explicit-serial runners install only the disposable package/runner. The ordinary signing helper then
builds releases/261014-rc-0.1.6/riff-0.1.6-rc-261014.apk from clean main and verifies
distribution/model/license/alignment/signature policy. APK size is 106,978,512 bytes. APK SHA-256:
**398c4d7c7172a16775374333c42ff98106235fed5ecf9d0d004353a84ee91875**. Certificate SHA-256:
**8f1dce0e9f5ae50fbead23ddb3d60182f9af4b3a584481eeee1f3df5c499e67d**. Model SHA-256 remains
**8819c2b3723d448dc63e92c12135a57eab00a3f867697439e4eb327bc6f32813**.

Both working-app updates use adb install -r. Exact pulled APK bytes, version/code, persistent
certificate, UID, first-install time and exact persisted grants agree. Both launch and remain alive
without matching bounded startup FATAL EXCEPTION. HU Player backup succeeds privately; full private
phone backup remains unavailable to unprivileged ADB. No uninstall, data clear, format change or
manual reset occurs.

## Website and phone screenshots

[APK](https://roadio.link/downloads/riff-0.1.6-rc-261014-398c4d7c7172.apk) and
[matching source archive](https://roadio.link/downloads/riff-source-0.1.6-rc-261014-6c7a6ba563f9.tar.gz)
are published with [metadata](https://roadio.link/downloads/apk-release.json),
[source inventory metadata](https://roadio.link/downloads/snapshot.json) and
[checksums](https://roadio.link/downloads/SHA256SUMS). Public source cut
**ce5f4aea7ee0f46ca35e749a9b4f5d35591c0bb8**, tree **51ca5f9b076b26bdbe34cd33ca64633527d0e8b7**,
contains one new root commit and **1,304 files** from release main. The APK, source archive,
read-only Git export and metadata describe the same edition; canonical development history is
preserved. Source excludes private state/history/builds while retaining current dependencies,
assets, generators, tests and required notices.

At the user’s follow-up direction, landscape is the primary website interface. The gallery replaces
its older HU presentation with actual 2316×1080 phone captures of MAIN, PLAYBACK, Albums, A-Z,
Settings and AI, in Day and Night. All 12 PNGs are exact adb screencap bytes from installed 0.1.6;
content-named URLs and bilingual captions/dialog dimensions agree. No synthetic UI, pixel edits or
altered playback/queue/reaction actions are used. Theme is temporarily switched through ordinary
Settings and restored to Auto; original accelerometer/user-rotation values are restored exactly. The
original playback surface is returned. The landscape-first gallery layout retains the full frame.
Existing RU/EN articles and immutable assets/downloads/Git packs are preserved, with a private
rollback archive.

All **1,395 origin files** and cache headers match staged bytes. A real origin clone has one commit,
the exact tree and source inventory. All new PNGs and APK/source downloads are verified over public
HTTPS. 25/31 selected ordinary public responses match. Older cached routes: index.html,
source/index.html, downloads/apk-release.json, downloads/snapshot.json, downloads/SHA256SUMS,
sitemap.xml. Manual Cloudflare clearing remains with the user; no credential access or purge is
performed. Public HTTPS Git clone still receives an earlier cached snapshot; exact latest-tree
verification passes at origin.

Private commands, source/staged patch, build/test/install/phone captures, screenshot hashes,
site/source inventories, rollback, HTTP and clone evidence remain under status/rc-0.1.6-261014/.
Outgoing Wiki editions are archived verbatim; exact API/public-route readbacks and whole-Riff
publication audit accompany delivery.
