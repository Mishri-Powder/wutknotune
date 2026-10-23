# Release audit — 2026-10-10

All seven user-approved corrections are implemented on the existing speed-control working tree. The
owning contracts were updated before implementation. Fresh results and limits follow; the dated
baseline below preserves why each correction was needed. This evidence page is non-normative.

## Implemented corrections and fresh verification

| Finding       | Implemented behavior                                                                                                                                                                                                                                                                                                                                                    | Regression evidence                                                                                                                                                                                                                                                    |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| R01 artwork   | RiffPlaybackService owns one bounded PlayerArtworkLoader cache; UI reads it. Queue revision, occurrence, exact binding and source generation identify each result. Current artwork survives prefetch; late/cancelled results are discarded. A transient failure gets at most two deferred retries; confirmed absence is cached. Initial readiness times out to default. | HU checks cover real SAF EXTRA_LOADING recovery to decoded PNG, delayed obsolete providers, queue/generation/binding replacement, prefetch eviction, timeout/recovery, retry bound and repeated cache reads. Unresolved binding cannot retain a former source's image. |
| R02 queue     | Context traversal runs once; immutable plan, metadata and shuffle indices are prepared on a worker. Main installs only a current result. Replacement, storage quiescence and service destruction cancel pending work; store/model/readiness checks reject stale completion.                                                                                             | A 20,000-position HU preparation preserves identities, preferred binding and shuffle permutation. Host projection is measured below.                                                                                                                                   |
| R03 session   | Both item-change and item-set commands are withheld; callback methods reject external additions/replacements.                                                                                                                                                                                                                                                           | Actual HU MediaController command availability tests pass; supported transport tests remain green.                                                                                                                                                                     |
| R04 history   | Starts are observed at settled actual playback, including seamless transitions and Repeat; pause/resume does not duplicate starts. Completion uses old terminal position. Explicit cross-item seeks and final Next record USER_SKIPPED; Stop/technical termination is censored using actual position. Every end event retains the first start's profile.                | Production service-listener tests cover transitions, Repeat, pause/resume, in-file/technical skips and terminal Next/censor positions; isolated SQLite verifies both event ownership and absence from the other profile.                                               |
| R05 callbacks | All notification types use one protected RemoteCallbackList snapshot, finished before invoking recipients; individual RemoteExceptions unregister the failed recipient.                                                                                                                                                                                                 | Controlled overlapping broadcasts and dead-recipient/healthy-recipient delivery pass on HU.                                                                                                                                                                            |
| R06 A-Z       | Explicit retained appearance is independent of disabled input; ordinary disabled buttons retain existing treatment.                                                                                                                                                                                                                                                     | Pending and ready preview pixels/layout pass in Day and Night; shared color-system tests pass.                                                                                                                                                                         |
| R07 AI        | Scores are centered before division by temperature; a zero random ticket cannot select a zero-weight candidate.                                                                                                                                                                                                                                                         | Tiny positive values through Double.MAX_VALUE, repeated sampling without duplicates and the underflow boundary pass. Accepted configuration range is unchanged.                                                                                                        |

Fresh verification totals: **441 JVM cases passed (Player 284, Indexer 130, configuration 27); HU
Player 167 passed / 5 skipped and Indexer 37 passed / 5 skipped, with zero failures. Kotlin checks
passed; Android lint reports zero errors and 25 warnings. Five release-helper Python tests and the
unsigned release build/distribution verifier passed**. All app and Indexer instrumentation uses the
disposable package on HU 172.16.2.1:7088. Phone remains unavailable. Skipped device cases require
explicit startup/audio probes, representative media or suitable brightness conditions; they are not
reported as passes.

The initial post-fix full app run exposed a test-fixture defect: the repository loaded a real
catalog that did not contain a synthetic playlist member, then correctly expired that playlist. The
ordering test now detaches its synthetic controller from catalog notifications. Settings-save tests
compare the intended profile/settings transaction instead of a global revision that playback
checkpoints can also advance. The Indexer inventory assertion now requires the current V3 prefix.

The host probe uses the same production traversal and the same synthetic item counts as the
baseline, excluding SQLite, SAF and Media3 installation:

| Queue items |      Before |   After |
| ----------: | ----------: | ------: |
|       1,000 |    120.9 ms | 18.0 ms |
|       5,000 |  1,680.7 ms |  5.6 ms |
|      10,000 |  6,665.4 ms | 22.9 ms |
|      20,000 | 26,639.6 ms | 34.2 ms |

These single host samples include warm-up effects. Complete synthetic queue preparation measured 985
ms on HU in the first post-fix run. Neither measurement establishes a full-library cold-start
deadline or bounded total library memory; those remain in [open work](/Riff/LATEST/open-decisions).

New evidence is under status/release-fixes-261010: final-checks.log, release-verification.log,
performance.txt, first-full, edges, final-results, documentation-final.log,
reference-check-final.log and final-publication-audit.json. The command ledger and exact totals
belong to
[worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#release-audit-corrections-verified-2026-10-10).
No signed RC or working-app installation was performed in this correction task.

## Baseline findings before the corrections

The user requested a review before the first release, with approval before corrections, and added an
intermittent missing-artwork report: the default is shown until navigating forward/back. Review
covers the current `speed-control` working tree based on `77bb8e9beaf7dc0a85e18d0ac8e04672f29e0ed2`,
including its existing uncommitted changes. Baseline work, probes and receipts are on the
development server under `status/release-audit-261010/`.

Two artwork-state failures are reproduced on HU. Their exact spontaneous trigger in the user's
session has not been captured. The broader review also establishes the findings below. Release
readiness is not established by the passing JVM tests alone.

## Baseline defects and approved resolutions

### R01 — P2: Artwork fallback can overwrite a valid cover and remain cached

`RiffPlayerUiController.applyPlaybackEngineState` takes any non-null `engine.artwork` ahead of the
UI result, including an `ArtworkUiState` whose bitmap is null. The service publishes that default
through `PlayerPlaybackBridge`; subsequent playback snapshots repeat it every 500 ms. Automatic UI
lookup only starts when `engine.artwork == null`, and `lastArtworkRequestKey` also suppresses a
second request for the same Media ID/binding. A transient failed lookup can therefore remain the
visible default and erase a cover independently resolved by the UI.

The separate asynchronous `refreshArtwork` completion checks the old request's binding against its
own result, but never checks the binding currently selected for playback. Two bindings of the same
Media ID can race: the old binding's result replaces the new binding's cover.

Temporary HU tests using the real controller reproduce both failures:

- `cachedDefaultDoesNotEraseSuccessfulSameBindingArtwork`: a valid current bitmap becomes null after
  a same-binding cached-default engine snapshot.
- `staleBindingResultDoesNotReplaceCurrentArtwork`: selected binding 902 is overwritten by a
  completed lookup for binding 901.

`PlayerArtworkResolver` collapses missing data, cancellation, provider/read errors and decode
failure into the same default representation. Some inner failures have no diagnostic reason.
`PlayerPlaybackBridge` additionally retains only one artwork slot even though Media3 may resolve a
future occurrence in advance. These choices prevent reliable ownership and retry decisions.

**Approved resolution:** one shared resolution/cache owner, keyed by queue/occurrence, exact binding
and accepted source version; explicit resolved-absent versus retryable-failure states; request
tokens checked on completion; bounded retry after transient failure; prefetch separated from the
current occurrence. Keep readiness bounded and the default available immediately. Never substitute
another binding's or an obsolete source version's cover. Add tests for delayed completion,
timeout/recovery, prefetch, queue replacement and UI recreation. Recovery semantics were adopted in
the [artwork owner](/Riff/LATEST/ui-foundation#physical-ui-and-artwork) before implementation; its
existing embedded/exactly-one-directory-image/default selection rules remain controlling.

### R02 — P1: Queue preparation performs quadratic work on the main thread

`RiffPlaybackService.reloadIfReady` requires the main looper and calls
`HierarchyQueueProjection.queueTrackForOccurrence` once per occurrence. For an ordinary Folder/Album
queue, that helper repeatedly locates and flattens the same context. A queue with N tracks therefore
performs approximately N complete traversals.

A probe invoking the actual compiled production classes measured only this repeated projection on
the development server, excluding SQLite, SAF and Media3 installation:

| Queue items | Projection time |
| ----------: | --------------: |
|       1,000 |        120.9 ms |
|       5,000 |      1,680.7 ms |
|      10,000 |      6,665.4 ms |
|      20,000 |     26,639.6 ms |

These are host measurements, not HU timings. The main-thread call site creates a concrete freeze and
ANR risk. **Approved resolution:** resolve the context once, prepare an indexed immutable execution
plan away from main, and validate/activate it on main. Preserve exact FILF occurrence order and
source-specific deduplication. Verify large queues and stale/cancelled plan rejection under the
[runtime owner](/Riff/LATEST/runtime-architecture) and
[queue owner](/Riff/LATEST/player-queues#queues-playlists-and-history).

### R03 — P1: MediaSession exposes queue replacement outside durable READY state

The session callback removes `COMMAND_CHANGE_MEDIA_ITEMS` but retains the distinct
`COMMAND_SET_MEDIA_ITEM`. The actual HU MediaController reports that command available. The pinned
[Media3 1.10.1 default callback](https://github.com/androidx/media/blob/1.10.1/libraries/session/src/main/java/androidx/media3/session/MediaSession.java)
accepts items that already contain local configuration. An admitted controller can therefore ask
Media3 to replace its item outside Riff's durable queue operation.

The temporary `sessionRejectsQueueReplacementCommand` test fails on this advertised permission; it
does not perform a destructive queue replacement. **Approved resolution:** expose an explicit
transport command set and reject external item addition/replacement. Test both commands and
callbacks while retaining supported transport. This reconciles implementation with the
[durable queue contract](/Riff/LATEST/player-queues#queues-playlists-and-history).

### R04 — P2: Listening events lose attempt boundaries, positions and profile ownership

Four related problems exist in `RiffPlaybackService.PlayerEvents` and
`PlayerStateStore.recordListeningEvent`:

1. Start recording happens only on `onIsPlayingChanged(true)`. A seamless automatic transition or
   Repeat can keep `isPlaying` true, so the following attempt receives no `PLAY_STARTED` and no
   lifetime-start increment.
2. User skips are recognized by `DISCONTINUITY_REASON_SKIP`, while the actual Next/Previous
   dispatcher uses seek operations. Media3 distinguishes explicit seek from a skipped period/ad;
   normal user navigation can miss `USER_SKIPPED`.
3. Automatic completion records `lastKnownPositionMs`, updated on playback state changes rather than
   continuous position. A fully buffered item can finish with an old near-start position even though
   the discontinuity callback contains the previous item's final position.
4. End events use the active profile at transaction time rather than the owner of the existing
   attempt. A controlled HU test starts under profile 1, switches to profile 2, completes that same
   attempt and finds its completion under profile 2.

The first three follow from the service handlers and the pinned
[ExoPlayer event implementation](https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java).
The [Player API](https://developer.android.com/reference/androidx/media3/common/Player) defines the
separate discontinuity reasons. The fourth is reproduced by `completionRetainsAttemptProfile`
against a temporary isolated Player database.

**Approved resolution:** one occurrence/attempt lifecycle for actual start, semantic user
navigation, automatic completion and technical censoring; use the old-position callback for
completion and retain the attempt's profile identity. Keep pause/resume/focus restoration idempotent
and make Repeat a new attempt. The user approved the profile-ownership interpretation; it was
adopted in the [history owner](/Riff/LATEST/player-queues#queues-playlists-and-history).

### R05 — P2: Concurrent Indexer notifications can abort publication

`AndroidIndexerArtifactBinder.publishStatus`, `publishSafListingIssues` and
`notifyArtifactAvailable` call `beginBroadcast()` on the same `RemoteCallbackList` without a shared
serialization mechanism. Pipeline status and bounded publication have separate worker threads. The
temporary HU probe overlaps two broadcasts and receives
`IllegalStateException: beginBroadcast() called while already in a broadcast`. Individual remote
callback failures are also not isolated.

**Approved resolution:** one protected recipient-snapshot path and per-recipient remote-exception
handling, with controlled overlap and dead-recipient tests. Preserve progress/artifact publication
semantics. The
[Android callback implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/RemoteCallbackList.java)
requires callers to prevent overlapping broadcasts. This is a reproduced controlled interleaving,
not a claim that the user's current indexing run crashed.

### R06 — P2: Pending A-Z search changes the retained preview's colors

The existing HU test
`AzLivePreviewInstrumentedTest.pendingRetainsBlocksAndCompletedResultsChangeWholeScripts` fails its
pixel comparison. `PlayerHierarchyScreen.AzGroupRow` preserves `appearanceEnabled` while disabling
input during recomputation, but `RiffComposeComponents.RiffIconButton` gives `!enabled` higher tint
priority and changes those retained keys to the disabled color.

**Approved resolution:** make the shared component's explicit retained-appearance option independent
of input admission, without changing ordinary disabled-button appearance. Check pending/ready states
in both themes against [A-Z navigation](/Riff/LATEST/player-az-navigation).

### R07 — P3: A valid tiny positive AI temperature fails generation

The configuration accepts every finite positive temperature. The actual production generator accepts
`1e-308` and `Double.MIN_VALUE` but fails with `IllegalStateException` after score division produces
infinities and softmax subtraction produces NaN. Default temperature 1.0 succeeds in the same probe.
This affects an extreme file-only setting, not the shipped default.

**Approved resolution:** stabilize the calculation before division, retaining the current accepted
range and sampling semantics. Add finite-result and limiting-distribution cases. A narrower
configuration range would instead require an explicit contract decision. Evidence:
`player-pure-probe.log`.

## Baseline verification and test maintenance

| Check                                         | Baseline result                                                   |
| --------------------------------------------- | ----------------------------------------------------------------- |
| Configuration JVM                             | 27 passed                                                         |
| Player JVM                                    | 281 passed                                                        |
| Indexer JVM                                   | 130 passed                                                        |
| Cuetree core / desktop CLI                    | 95 / 6 passed                                                     |
| Cuegrain core / desktop CLI                   | 40 / 3 passed                                                     |
| Total JVM                                     | 582 passed; no failures, errors or skips                          |
| Release Python tests                          | 5 passed                                                          |
| Root check, Android lint, release verifier    | Passed; lint has 0 errors, 25 warnings across app/indexer         |
| Standalone Cuetree / Cuegrain builds          | Passed, including CLI packaging                                   |
| Cuegrain external verification                | 15 FFmpeg/Mutagen checks and 5 schema/document validations passed |
| Bounded parser mutation probe                 | 12,000 generated small mutations; no uncaught parser exceptions   |
| Full HU app instrumentation                   | 157 cases: 148 passed, 4 failed, 5 skipped                        |
| Full HU indexer instrumentation               | 42 cases: 36 passed, 1 failed, 5 skipped                          |
| Focused retry of three ambiguous app failures | 3 passed                                                          |
| Temporary defect probes on HU                 | 5 expected assertion failures confirming R01, R03, R04 and R05    |

XML counts are authoritative; the Gradle console's final totals double-count skipped cases in this
run. The parser probe covers generated small MP3/M4A/FLAC/OGG/Opus/CUE inputs under a bounded heap;
it is not an exhaustive malformed-input or security proof.

The full app run's remaining three failures are the saved leading-article ordering case and two
Settings admission/revision cases. All pass when run together in isolation. The Settings tests
assert a global state revision while a previously started playback service may write a checkpoint.
These observations establish test-order dependence, not a confirmed Settings product defect.
**Approved test work:** isolate synthetic controller/catalog state, assert the intended settings
transaction independently of unrelated checkpoints, and repeat the combined suite. The exact
leading-article trigger and successful correction are recorded above.

The Indexer failure is a stale expectation: `actualDeviceInventoryRevisionIsStable` still requires
`SOURCE-CAPABILITIES-V2/`, while production emits V3. The preceding same-device revision-equality
assertion passes. Update the expected current revision after checking its owner; no runtime
algorithm change is justified by this failure.

Skipped instrumentation requires opt-in startup/output probes, representative media or brightness
conditions. Phone is unavailable to ADB; no phone results are claimed. Large-library HU readiness,
physical audiobook pitch/output capture, representative listening and vehicle-host acceptance remain
separately [open](/Riff/LATEST/open-decisions).

## Evidence and scope boundaries

Main receipts under `status/release-audit-261010/`:

- `authority.json` and `wiki/`: controlling-page snapshots.
- `gradle-checks.log`, `test-summary.json`, standalone and Python logs: host verification.
- `ParserMutationProbe.java`, `parser-mutation.log`, `PlayerPureProbe.java`,
  `player-pure-probe.log`: executable host probes and measured results.
- `hu-connected-tests.log`, `hu-indexer-tests.log`, `hu-full-results/`: original full HU results.
- `hu-focused-rerun.log`: three isolated passing retries.
- `android-probes/`, `audit-probes.init.gradle`, `hu-audit-probes.log`, `hu-probe-results/`:
  temporary tests, source-set injection and device proof.

The initial read-only review left production code and normative contracts unchanged. Temporary tests
are ignored status artifacts and use the disposable `.instrumented` application; they do not replace
or clear the working app. No new signed RC, commit, push, merge or working-app installation is part
of this audit. Existing uncommitted feature/theme work remains intact. Corrections were approved and
verified on 2026-10-10; fresh evidence is recorded above and in worklog.
