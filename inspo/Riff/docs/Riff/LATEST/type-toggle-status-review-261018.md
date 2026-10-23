# Classification toggles and release status — 2026-10-18 review

## Authorized change and official review

The user requests two mutually exclusive Music/Book toggles, Auto represented by no selection,
transport-height targets, exact existing icons and complete wrapping messages below the controls.
The FINAL status default returns to true. This review is informative; the hierarchy, content-mode,
icon and configuration contracts own behavior.

Reviewed 2026-10-18:

- [Automotive sizing](https://developers.google.com/cars/design/automotive-os/design-system/sizing):
  retain centered icons and at least 76dp touch targets; the shared 120dp transport default exceeds
  that minimum and is not reduced to fit text.
- [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality): respect
  system-bar/cutout insets and preserve applicable media/host restrictions. Use existing safe
  content and bounded scrolling for large text rather than overlapping or clipped controls.
- [AAOS media integration](https://developer.android.com/training/cars/media/automotive-os): actual
  AAOS media remains host-mediated; the local control change does not replace supported host UI.

One local renderer and one draft transition rule serve phone and direct automotive surfaces. Actual
AAOS and projected Android Auto retain supported host surfaces and existing policy gates. No new
mode branch, Settings entry, IME, long press or motion bypass is introduced. Disabled choices must
be nonfunctional, not merely dimmed. The passive status remains outside input/focus and keeps its
existing placement and foreground precedence.

## Verification plan

Verify AUTO → MUSIC → AUTO → BOOK → AUTO, disabled opposing input, pending commits, existing
Artist/Genre read-only behavior and playlist absence. Exercise exact icons and transport height,
long cause/advisory layout, narrow/wide windows and font scale 2.0. Preserve the one-time draft
commit on deliberate exit and current queue. Verify the shared packaged status default and explicit
true/false file overrides, then build debug/instrumented/release artifacts. Record actual results in
worklog; local tests do not establish live vehicle-host conformance.

## Implemented and verified

The uncommitted working tree implements the two-toggle state model on the shared ROW_ACTIONS
renderer for Album, Folder, Track, Artist and Genre. Artist/Genre remain read-only and playlist
containers omit the selector. Both texts follow the controls and wrap at natural height inside the
bounded scrollable action body. Icons reuse the adopted resources without changing their bytes. The
one packaged default is true for all channels; obsolete diagnostic/release document selection was
removed. Explicit user false/true and one-read startup behavior remain tested.

A new controller regression found a preexisting commit-completion defect: `Map.put` returns the
previous entry, so nullable chaining plus Elvis removal deleted a first insertion from the UI cache
immediately after the database write. Re-dispatching the exit then repeatedly committed the draft.
Explicit put/remove branches now retain the first chosen type and let the exit continue; Auto
deletes the sparse value. The regression reproduces the old timeout and now passes without changing
the active queue.

Fresh checks: configuration 30 JVM cases and Player 302 JVM cases, no failures/errors/skips; Kotlin
style, Android lint, instrumented/release APK assembly and release distribution checks pass. The
actual instrumented and release APKs each contain one configuration resource with status true. Two
renderer tests and one controller/store test pass on both HU and the Samsung phone (six Android case
executions). They cover toggle transitions, disabled input, all applicable row kinds, pending
commits, playlist absence, transport height, long complete cause/advisory text at 360dp/fontScale 2,
first override commit, Auto deletion and unchanged active queue.

Gradle's connected-test wrapper returned failure while its XML reported two passed tests and zero
failures; its logged instrumentation command also dropped the second comma-separated class filter.
The same freshly built target/test APKs were therefore verified directly with AndroidJUnitRunner,
one selector per invocation: `OK (2 tests)` and `OK (1 test)` on each device. This runner issue
remains separate from the product checks. Only disposable instrumented packages were installed and
removed; working-package UID, initial install time and version remained unchanged on both devices.

Evidence: `~/riff/status/type-toggle-status-261018`. The implementation-verification checkpoint left
the update uncommitted and not deployed. The subsequent user-authorized
[local phone/HU installation](/Riff/LATEST/type-toggle-install-261018) records the exact signed APK,
installed-byte hashes and working-package checks. The public website artifact remains the previous
edition. Local UI verification does not establish live vehicle-host conformance.
