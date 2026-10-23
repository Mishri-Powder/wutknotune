# Riff 1.0.2 — release and delivery evidence

## Authorized scope and official UI review — 2026-10-18

The user authorizes removing the complete classification-explanation element, its typography and
supporting code, retaining the static AI advisory and existing toggle/persistence behavior. They
also authorize the next version, commit, merge, push, signed APK, available-device installation and
website publication with matching source. Version 1.0.2 (16), FINAL, keeps the declared 2026-10-18
date and existing signing identity. No Cloudflare purge is needed.

Official sources reviewed:

- [Automotive sizing](https://developers.google.com/cars/design/automotive-os/design-system/sizing):
  retain transport-height controls and centered icons.
- [AAOS media integration](https://developer.android.com/training/cars/media/automotive-os): retain
  supported host-owned media surfaces and existing centralized application/motion policy.

The same simpler local page serves phone and direct Automotive. Removing passive explanation text
adds no input, gesture, host substitute, policy exception or mode branch. The static AI advisory
continues to wrap and scroll without shrinking targets. Music/Book retain direct switching and
selected-again Auto; commit-before-exit and queue preservation remain. MAIN Playlists/Bookmarks
continues opening the collection root. Local verification cannot establish vehicle-host acceptance.

## Implementation verified; publication pending

The explanation view, 28sp typography, formatter, tests and unused `BrowserItem.audiobookCause`
field/producers are removed. The menu retains two transport-height icon toggles and the static,
complete wrapped AI advisory. Direct switching, selected-again Auto, commit-before-exit, queue
preservation, the first-override cache fix and MAIN collection-root navigation remain.

Fresh
`./gradlew check :app:lintDebug :app:verifyDistributionRelease :app:assembleInstrumented :app:assembleInstrumentedAndroidTest`
passed. Reports contain 304 Player, 30 configuration and 171 Indexer JVM cases (505 total), zero
failures/errors/skips. Unchanged tasks may use Gradle's verified up-to-date results. Five
release-helper Python cases passed. Direct AndroidJUnitRunner runs passed four cases on each of
phone and HU: shared toggle/layout cases, commit/Auto-deletion integration and first-playlist
creation followed by MAIN opening the collection list. Layout checks retain the full advisory at
360dp/fontScale 2 without shrinking transport targets. Only disposable packages were
installed/removed for those tests.

The existing dependency review remains due 2026-11-15; declarations/model/runtime graph are
unchanged. Logs and reviewed source scope are under `~/riff/status/1.0.2-261018`. Git
commit/merge/push, signing, working-device installation and matching public-site/source publication
are next. No Cloudflare purge is needed; stale edge responses will be distinguished from exact
origin bytes.
