# Riff code and contract review — 2026-10-08

This is the non-normative audit baseline preceding the user-approved corrections. The user requested
code errors, implementation/documentation drift and contract conflicts, with user decisions before
reconciling conflicts. No production source, existing contract, tracked documentation or Git history
was changed by this review. The working HU RC was not replaced.

## Scope and baseline

Source base is `007d8cdb2a68e29964416dff62a764d285529811` plus the current uncommitted shared-modal
change. The review includes that change, playlist/controller/store/profile boundaries, Settings save
paths, mode/bootstrap resolution, Android Cuetree publication and desktop CLI/JSON export. A live
snapshot of 184 current Riff/Cuetree/Cuegrain Wiki pages was compared with repository exports; one
currently referenced experiment was then added for reference validation. Link rewriting, Markdown
wrapping and table padding were distinguished from substantive drift. Historical pages were not used
as current authority.

The following are confirmed findings. Priority P1 means the profile-isolation bug should be fixed
before the next release; P2 means a reproducible behavior or contract problem requiring correction
or an explicit product decision. The user approved the proposed remedies on 2026-10-08; their owning
contracts and implementation evidence are recorded in the current worklog.

## R1 — P1 — Queued playlist creation can write into another User

Reproduction on the disposable HU target: request a new playlist under User 1, pause the existing
controller worker before it starts the write, confirm OK, cancel the still-open dialog, open
Settings and save User 2, then release the worker. The playlist is created under User 2. The probe
uses production dispatch and Settings profile selection; it does not directly switch the database
profile to manufacture the race.

`confirmPlaylistName` freezes name and track IDs but does not freeze the source profile.
`PlayerStateStore.createPlaylist` chooses `activeProfileId(db)` when the delayed transaction runs.
The Settings path uses another executor and may commit the new profile before that transaction. This
conflicts with the profile ownership in [Player state](/Riff/LATEST/player-state) and
[queues](/Riff/LATEST/player-queues).

Source: `/home/riff-adb/riff/app/src/main/java/link/roadio/riff/ui/RiffPlayerUiController.kt:1998`,
`/home/riff-adb/riff/app/src/main/java/link/roadio/riff/player/storage/PlayerStateStore.kt:683`, and
`/home/riff-adb/riff/app/src/main/java/link/roadio/riff/ui/RiffPlayerUiController.kt:1143`.

Recommendation: bind every queued profile-owned mutation to the accepted profile/session and use
that identity transactionally. A stale completion must not change a later screen/profile. Review
sibling queued operations under the same ownership rule rather than fixing only this caller.

## R2 — P2 — Accepted Cancel does not cancel the queued playlist write

The second deterministic HU probe holds the worker before its write, presses OK then Cancel, and
observes that the dialog disappears immediately while the playlist is later created and the UI
navigates to it. Cancel only clears the UI request; the captured worker closure remains scheduled.
New/Rename keep Cancel and outside/Back dismissal enabled while the write is busy. Delete uses the
same cancellation pattern; its analogous race was identified in source, not separately reproduced.

Source: `/home/riff-adb/riff/app/src/main/java/link/roadio/riff/ui/PlayerComposeUi.kt:3953`,
`/home/riff-adb/riff/app/src/main/java/link/roadio/riff/ui/RiffPlayerUiController.kt:700`, and
`/home/riff-adb/riff/app/src/main/java/link/roadio/riff/ui/RiffPlayerUiController.kt:2010`.

The [queue contract](/Riff/LATEST/player-queues) and [modal contract](/Riff/LATEST/ui-modal-dialogs)
state that cancellation makes no mutation. They do not clearly define the cancellation boundary
after OK. A user decision is needed: either make accepted writes explicitly non-cancellable and
disable Cancel/Back/outside until the result, or support cancellation up to a defined transaction
boundary. Do not accept a visible Cancel while silently proceeding with the write. The recommended
simple policy is non-cancellable save after OK, explicitly recorded in the owning contracts. R1
still needs explicit profile ownership.

## R3 — P2 — Name validation errors are invisible and inaccessible

A duplicate-name create fails correctly and preserves the draft, but `playlistMutationFailed` places
its error only in the general `status` field. `PlaylistNameDialog` renders the title and input, with
no error. The surrounding playlist selector does not render that status either. The fresh HU probe
reaches the real duplicate-name error and verifies that its message is absent from both text and
content-description semantics while the modal remains visible. The user sees an apparently
ineffective OK and receives no reason from TalkBack.

Source: `/home/riff-adb/riff/app/src/main/java/link/roadio/riff/ui/RiffPlayerUiController.kt:2119`
and `/home/riff-adb/riff/app/src/main/java/link/roadio/riff/ui/PlayerComposeUi.kt:3944`.

Recommendation: carry a scoped validation/write error in the dialog state and display/announce it
through the shared modal text component while retaining the draft. Keep duplicate/length validation
in its existing owner. This needs no different typography or copied dialog shell.

## R4 — P2 — Settings save performs disk writes on the main thread

`SaveSettings` and `SaveMoreSettings` start on `profileWorkers`, then call `updateState`, which
posts the entire transform to the main Handler. The transform invokes `saveSettings` or
`saveMoreSettings`; durable SharedPreferences commits and SQLite mutations therefore run on main.
The fresh HU StrictMode probe records five disk-write violations during More Save. Its captured
stack includes `SharedPreferencesImpl.EditorImpl.commit`, `saveMoreSettings:1186`, `dispatch:412`,
`updateState:2935`, and the main Handler.

Source: `/home/riff-adb/riff/app/src/main/java/link/roadio/riff/ui/RiffPlayerUiController.kt:407`,
`:1186`, `:1191`, and `:2932`; primary Settings also commits at `:1143` through the same posting
mechanism. Slower storage or contention can block input/rendering. This audit did not measure an ANR
or claim a particular delay.

Recommendation: snapshot and validate the command, perform the durable work on its serial worker,
then publish only the immutable result on main. Recheck access/session identity at the appropriate
commit boundary and preserve the documented transaction semantics.

## C1 — P2 — Cuetree CLI publishes an unchanged pass

[Cuetree persistence](/Cuetree/persistence) says an unchanged pass must not publish or advance
generation. The CLI unconditionally calls `store.write` after `Indexer.scan`. Two fresh `--json`
runs against one unchanged synthetic empty directory produce generations `1791495670857` and
`1791495671044`, switch CURRENT, and create distinct cohorts. All four parsed JSON catalogs are
identical after removing generation fields. This is not a converter-field mismatch: conversion still
uses the actual binary catalogs and packaged schemas.

Source:
`/home/riff-adb/riff/standalone/cuetree/cli/src/main/kotlin/link/roadio/cuetree/cli/Main.kt:50`. The
Android adapter already has an explicit semantic comparison in
`/home/riff-adb/riff/indexer/src/main/java/link/roadio/riff/indexer/android/cuetree/AndroidCuetreePipeline.kt:154`.

Decision needed: bring desktop publication into line with the shared contract, or explicitly adopt a
desktop exception and describe its generation/retention meaning. Recommendation: obey the common
no-op rule and use one shared semantic comparator, not a copied Android comparator in the CLI. The
effect is avoidable rewriting, JSON conversion, generation advancement and cohort rotation.

## C2 — P2 — Bootstrap fallback conflicts with the packaged default description

The [mode owner](/Riff/LATEST/application-modes-modes) and
[configuration owner](/Riff/LATEST/project-configuration) specify the configurable packaged default
as phone. The
[startup-recovery owner](/Riff/LATEST/application-modes-selected-storage-startup-recovery) still
specifies falling back to packaged automotive. Code implements the latter literally:
`cachedApplicationMode()` defaults to automotive, and `MainActivity.storageAccess` also resolves an
unrecognized cached value to automotive.

Source:
`/home/riff-adb/riff/indexer/src/main/java/link/roadio/riff/storage/root/ApplicationDataStorage.kt:78`
and `/home/riff-adb/riff/app/src/main/java/link/roadio/riff/MainActivity.kt:223`.

This matters when the selected root cannot be admitted and a valid cached mode is absent: a non-AAOS
Phone installation gets recovery-hold behavior rather than Phone tap confirmation. Ordinary starts
with a valid cached phone value are unaffected. The audit does not claim this condition is present
on the working HU.

Decision needed: use the packaged configuration's mode as bootstrap fallback, currently phone, or
retain automotive as an explicit conservative bootstrap exception and reconcile the wording.
Recommendation: use the packaged mode, preserving actual-AAOS precedence and a valid cached
override.

## D1 — P2/P3 — Current supporting documentation is stale

The informative implementation section of
[Settings storage](/Riff/LATEST/settings-screen-save-data-on-and-data-storage-selector) says
ReplayGain defaults to N and lists More rows without Dim Backlight. The actual code and the
[ReplayGain owner](/Riff/LATEST/replaygain-settings) use F/23; the current
[Settings row owner](/Riff/LATEST/settings-screen-scope-and-rows) includes Dim Backlight.

The informative section of
[startup recovery](/Riff/LATEST/application-modes-selected-storage-startup-recovery) says general
Settings and playlist restrictions still need live wiring. Actual-AAOS settings already use the
shared policy fed by `AaosUxRestrictionMonitor` in
`/home/riff-adb/riff/app/src/main/java/link/roadio/riff/ui/RiffPlayerUiController.kt:184` and
`:283`. The missing direct-AOSP motion adapter and incomplete vehicle-host integration remain real
separate gaps; they should be described precisely instead of implying all wiring is absent.

The current [TL;DR](/Riff/LATEST/tldr) also calls all 303 modal-delivery JVM checks fresh, whereas
its [worklog](/Riff/LATEST/worklog) correctly distinguishes 276 freshly run Player cases from 27
reused configuration results. This is an evidence wording error, not an additional failed test.

Recommendation: preserve the current owning contracts and implementation, remove/replace these stale
informative statements, and link to the scoped owners instead of duplicating defaults/status. User
confirmation is requested before reconciling these contract/documentation discrepancies.

## Verification and limits

- The requested Gradle JVM stage succeeds. Fresh tasks executed 269 cases: Indexer 130, Cuetree core
  93, Cuetree CLI 6 and Cuegrain core 40. Configuration 27 and Player 276 are UP-TO-DATE reuse,
  giving 572 current passing results with no failures/errors/skips. No claim of 572 fresh executions
  is made.
- Four newly written audit-only HU probes pass by asserting the unwanted behaviors in R1–R4. These
  are reproductions of bugs, not evidence that the application satisfies those behaviors correctly.
  They reuse the existing isolated controller fixture and are injected by an ignored Gradle init
  script; production/test source files in the working tree are unchanged.
- The first attempt did not include the Kotlin probe class because its source directory was attached
  to the Java source set. That harness error is preserved separately; the corrected Kotlin source
  set produced the final four successful reproductions.
- The CLI no-op reproduction uses only a synthetic empty directory and its task-local catalog. No
  user media is indexed. JSON companions are parsed and compared structurally.
- Pinned documentation lint and current references pass: 184 maintained Markdown files and 551
  maintained files against 185 current Wiki pages. Passing syntax/link checks do not detect the
  semantic discrepancies above. Git whitespace checks pass.
- The working HU package path remains identical before/after; its APK SHA-256 still exactly matches
  delivered RC 261008. The task never installs, uninstalls or clears that working package. Both
  disposable instrumentation packages are absent after the tests. Source status is identical to the
  pre-review shared-modal handoff.
- Existing [open work](/Riff/LATEST/open-decisions) for large-library/deadline verification,
  real-media FILF coverage and actual AAOS/projected-host acceptance remains open. No new complete
  library benchmark, audible-output acceptance or vehicle-host certification is claimed.

Canonical evidence directory: `/home/riff-adb/riff/status/consistency-review-261008`. It contains
live Wiki snapshots, normalized comparison data, JVM logs/XML totals, the isolated probe source/init
script and final HU XML/logcat, CLI logs/structural comparison, documentation checks and
source/package preservation records. No screenshots or user metadata are included in this public
report.

## User decision and publication

The user explicitly approved publication of this complete report and the proposed corrections on
2026-10-08. The findings describe the audited baseline; correction contracts and final
implementation evidence are recorded separately in the current [worklog](/Riff/LATEST/worklog). The
earlier automated publication rejection was resolved by this explicit authorization.

## Correction outcome — 2026-10-08

The approved corrections are implemented and verified; see the
[correction worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#audit-corrections-verified-2026-10-08)
for final behavior, exact test scope, initial test failures and successful reruns. The findings
above remain the dated audit baseline. During implementation, another discrepancy was found in
`appendToPlaylist`: calling `distinct()` removed repeated source positions. The user explicitly
approved preserving all positions; store/queue/reopen and idempotence regressions now cover that
correction. The working HU RC remains the prior delivery; the corrected source has been exercised
using disposable instrumentation.
