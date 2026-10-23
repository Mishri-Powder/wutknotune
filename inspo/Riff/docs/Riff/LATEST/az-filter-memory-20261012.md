# A-Z saved-filter update — 2026-10-12

Backspace and breadcrumb-prefix selection now save both the active and remembered filter, including
an empty Albums/root prefix. Accepted contextual Play saves the source filter and preview anchor
before preparing its queue. The updated signed RC is installed on the local tablet and the reported
Home/re-entry problem is resolved in its working catalog.

## Adopted behavior and implementation

The [A-Z owner](/Riff/LATEST/player-az-navigation) and
[hierarchy search owner](/Riff/LATEST/hierarchy-screen-row-actions-and-audiobook-classification#a-z-search)
were updated before implementation. The original behavior agreed with the previous contract but lost
the user's explicit filter deletion when Home cancelled its draft. The new contract retains those
changes at an explicit navigation save point. Detailed Unicode classification and the unchanged
group table are routed to the bounded [matching owner](/Riff/LATEST/player-az-matching).

One PlayerNavigationState operation saves the filter, destination container memory and cancellation
return snapshot together. Backspace and current/ancestor breadcrumb selection keep A-Z open and save
without requiring Auto search. Subsequent uncommitted group choices remain cancellable. Accepted
manual and bookmark Play use this same save before worker queue preparation. Play from ROW_ACTIONS
uses the opening browser's position rather than the action menu's list position; a failed queue
operation retains the navigation save and existing failure presentation.

Pending filter recomputation retains the last complete operable preview only for the exact same
source. Replaced-source snapshots remain ineligible. Name/filter/count preparation stays off the
main and playback-critical threads. Saves remain process-memory navigation state; queue membership,
Player persistence schema, media, profile preferences and model data are unchanged.

The [official-source review](/Riff/LATEST/aaos-conformance#a-z-saved-filter-interaction-review)
records one shared Phone/direct-Automotive implementation. Actual AAOS/projected Android Auto keep
supported host browsing and search. No new control, gesture, layout or mode exception was added.

## Verification

Fresh verification passes 296 Player and 27 configuration JVM cases, with zero failures, errors or
skips. Eight new navigation cases parameterize root/content-mode saves, immediate Home before worker
completion, nonempty/empty Backspace, current/ancestor breadcrumb, independent child memory, later
draft cancellation, accepted Play, ROW_ACTIONS browser anchors, failure retention, disabled
Backspace and replaced-source rejection.

Twenty-six disposable UI cases pass on the local tablet. Four new cases activate actual Backspace,
empty-root breadcrumb, track-body Play and More/Play controls through the common renderer and the
production navigation owner. Their playback handoff is synthetic; they never start user media or
write a production queue. Existing A-Z chrome, pending-preview, physical-row, alpha, protected-gap
and scrolling cases also pass. NARROW fixtures now derive density from display width to produce a
640dp viewport instead of assuming that fixed density 2 always selects NARROW. The constrained
fixture cannot expose a complete More tier in its preview viewport, so the dedicated More/Play
activation case uses WIDE; NARROW breadcrumb and keypad interaction remain exercised.

Commands:

```sh
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew --offline --console=plain \
  :app:testDebugUnitTest :configuration:test :app:ktlintCheck :app:lintRelease
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew --offline --console=plain \
  :app:assembleInstrumented :app:assembleInstrumentedAndroidTest
~/venv/bin/python bin/build_release.py --output-dir releases/261012-rc-az-filter-memory
```

The explicit AndroidJUnitRunner class list and serial routing are retained in local command logs.
Both disposable packages were removed. Release lint, Kotlin checks, signed distribution
verification, independent APK alignment/signature and certificate continuity pass. These checks
establish local navigation behavior, not vehicle-host or physical-audio acceptance.

## Signed tablet delivery and working-catalog check

The local tablet receives an in-place update to package link.roadio.riff, version 0.1.2-rc,
versionCode 5, immutable identity RC: 261012. The APK is
`releases/261012-rc-az-filter-memory/riff-0.1.2-rc-261012.apk`, 106749136 bytes, SHA-256
`5320c562deb9d1820c2a69e234b0dc7090e82940c82c853bb63281ad496f8cce`. Its certificate remains
`8f1dce0e9f5ae50fbead23ddb3d60182f9af4b3a584481eeee1f3df5c499e67d`. Installed APK bytes match
exactly. UID, first-install identity and exact persisted SAF grants survive. The old signed APK is
retained as a local rollback artifact.

In the working catalog, each live check first commits ДЕЖЗ, goes Home and re-enters Albums to prove
the saved starting filter. Backspace → Home → Albums then retains an empty filter. A separate Albums
root breadcrumb → Home → Albums check also retains an empty filter with Backspace disabled. No
intermediate A-Z apply/close is needed. The device is left at MAIN with the Albums filter empty.
Live validation did not start playback; Play filter/anchor restoration has JVM and synthetic UI
coverage.

Source remains main at base 3981b1bc7779cdd20fce5cd072d468abef9fa326 plus existing authorized
uncommitted work and this change. No Git history operation was requested. This delivery updates only
the tablet; HU retains signed RC 261012 versionCode 4, and the separately published 0.1.1 website
edition is unchanged. Device identity and raw catalog labels remain private local evidence under
`~/riff/status/az-filter-memory-261012`, together with authority snapshots, source diffs, initial
and final test outputs, signing/badging, installation preservation, UI stage dumps and final
summaries.
