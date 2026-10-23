# Audiobook speed and source refactor review — 2026-10-09

This is implementation evidence, not an additional contract. The
[speed owner](/Riff/LATEST/player-audiobook-speed), [UI foundation](/Riff/LATEST/ui-foundation) and
[Player state](/Riff/LATEST/player-state) control behavior.

## Delivered source changes

The `speed-control` working branch starts at `77bb8e9beaf7dc0a85e18d0ac8e04672f29e0ed2`. Its
verified source changes remain uncommitted. The following signed RC 261009 delivery to phone and HU
is recorded in the
[installation worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#audiobook-tenths-and-suffix-label-delivered-2026-10-09).
The speed preference is one fixed-value enum per profile, stored transactionally in Player SQLite
and restored at profile activation/reopen. Ordered worker commands freeze profile and store
ownership. Media3 applies the current profile's preference only for an effective Book occurrence in
Books mode; pitch stays at unity, silence skipping is enabled, and Music returns to ordinary
parameters. Queue occurrences, order, provenance and checkpoints are not rewritten by a speed
change. Host arbitrary speed/pitch commands are unavailable through the transport session.

The speed text uses the existing RiffIconButton, including the Ban allocation and gesture/contrast
path. Its common text renderer supplies bold monospace and normal font scaling. The fixed numeric
label bypasses the ordinary user-string length limit; accessible current/next/reset names remain
complete. There is no duplicate speed button or font renderer.

`PlayerComposeUi.kt` is reduced from 4,054 to 146 lines. It retains composition, routing and the
shared safe-content boundary. Screen implementations move into PlayerMainScreen,
PlayerHierarchyScreen, PlayerPlaybackScreen, PlayerAiScreen, PlayerSettingsScreen,
PlayerStorageScreens and PlayerRowActionsScreen. Dialog routing, bounded selectors and common
Compose components have their own files. Each implementation exists once; shared modal and row
renderers remain shared.

`RiffPlayerUiController.kt` is reduced from 3,413 to 2,614 lines. PlayerNavigationState owns
remembered paths/search return and navigation helpers; PlayerPlaylistActions owns accepted
playlist/bookmark workflows; PlayerSafGrantEditor owns the staged grant transaction and Indexer
acknowledgement; SettingsUiTransition owns typed UI settings transitions; PlayerControllerPolicies
owns pure action and queue helpers. Narrow host interfaces retain store/profile capture,
cancellation and main-thread publication in the coordinator. No second state store or inheritance
hierarchy is introduced.

## Remaining large-file review

The candidate inventory covers 449 source/tool/test files, excluding generated output, history,
immutable references and vendored encoding detection. Independent library/import sources are listed
for size awareness, not adopted as Riff refactor work. Line count prioritizes inspection; ownership,
transaction boundaries and change coupling determine whether extraction is useful.

| File or group                                                                                           |                                         Lines | Assessment and next useful boundary                                                                                                                                                                      |
| ------------------------------------------------------------------------------------------------------- | --------------------------------------------: | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| RiffPlayerUiController.kt                                                                               |                                         2,614 | High priority for a later AI projection/activation coordinator; keep one typed UI-state publication owner. The navigation, playlists, SAF and settings-transition extraction is complete in this change. |
| PlayerStateStore.kt                                                                                     |                                         2,090 | High priority: separate profile/settings, queue/history and playlist/bookmark SQL helpers while retaining one atomic transaction/state_revision authority and one recovery owner.                        |
| RiffPlaybackService.kt                                                                                  |                                         1,193 | High priority: source/execution-plan and checkpoint/history helpers. Preserve source/state executor ordering and main-thread Media3 ownership.                                                           |
| tools/audio-output-response-experiment/run.py                                                           |                                         1,124 | Medium: separate ADB/capture, signal generation/analysis and rendering behind the existing CLI.                                                                                                          |
| MediaIdV32.kt                                                                                           |                                           890 | Cohesive identity algorithm. Extract container-specific readers only with byte-exact identity regressions; size alone does not justify splitting.                                                        |
| PlayerUiContract.kt                                                                                     |                                           868 | Medium: separate UI state, actions and navigation/settings models without changing the typed public shape.                                                                                               |
| PlayerAiScreen.kt                                                                                       |                                           811 | Now a bounded screen file. A later heatmap/interaction component can be extracted if responsibilities diverge.                                                                                           |
| CalibrationScreenView.kt                                                                                |                                           759 | Medium: measurement/paint helpers only; retain its sole adopted raw-pixel View surface.                                                                                                                  |
| RiffComposeComponents.kt                                                                                |                                           739 | Intentional common renderer, typography and control ownership; a later controls/text split must keep one style resolver.                                                                                 |
| AndroidDecoderAutotuner.kt                                                                              |                                           735 | Medium: extract its codec callback session while retaining hardware probe ownership.                                                                                                                     |
| tools/genre-retrieval/wikidata_indx.py, musicbrainz.py, wikidata_musicbrainz_merge.py, wikidata_i18n.py |                         702 / 650 / 642 / 524 | Medium: separate fetch/cache/transform stages per tool, sharing only identical mechanics. No pipeline behavior changes were needed for speed.                                                            |
| AndroidAudioExcerptDecoder.kt                                                                           |                                           693 | Codec callback/plumbing boundary may be extracted with Android decoder regressions; do not split ordered decode lifecycle by line count.                                                                 |
| MainActivity.kt                                                                                         |                                           609 | Medium: startup/recovery/brightness binding helpers, preserving selected-root admission before config/store access.                                                                                      |
| standalone/cuetree/core/Indexer.kt, Grouping.kt                                                         |                                     762 / 507 | Independent Cuetree ownership; any extraction needs its own contract/API and publication regressions. Unchanged here.                                                                                    |
| standalone/cueqr/src/qrMatrix.c                                                                         |                                           588 | Imported library source, unchanged; not a handwritten Riff coordinator candidate.                                                                                                                        |
| Large test fixtures                                                                                     | 862 / 723 / 675 / 634 / 562 / 553 / 550 / 536 | Store, Cuegrain, codec, playlist/controller and identity tests. Possible behavior-based fixture separation; not production complexity. Shared setup must retain isolation.                               |

The exact sorted inventory is retained under `status/speed-control-261009/size-inventory.json`. This
review does not assert that every large file needs refactoring or that the code is free of bugs.

## Verification and limits

Player JVM results: 281 tests, zero failures/errors/skips. Kotlin style and Android lint pass; lint
retains 23 warnings and zero errors. The final disposable main/test APKs build successfully. All 44
feature/store/controller and 32 UI/navigation/Settings/modal regression tests pass on HU
`172.16.2.1:7088`, without skips. Fixtures cover WIDE/NARROW, both themes, font scale 2, RTL,
nondefault chrome fonts, a one-character user-string limit, real short/long gestures, accessible
reset, disabled storage, profile isolation/reopen, queued advance/reset, stale store/profile
results, queue preservation and actual ExoPlayer parameters in ReplayGain N/I/F.

Initial HU failures were test-only: a new fixture placed behavior at the global instead of skin
level, and an older service permission expectation omitted the already shipped WRITE_SETTINGS. Both
expectations were corrected; the complete affected 44-case batch then passed.

The feature-verification run removed its temporary instrumented packages and confirmed the then
working HU APK was unchanged. The subsequent user-requested signed RC 261009 installation updates
both working devices, with signature, byte identity, grants and startup receipts in the installation
worklog linked above. Source remains uncommitted. The user's subsequent speed report exposed a real
regression: the UI selected Books but the service retained Music when the cached cohort did not
change. The corrected service activates its cached current projection. A fixed-catalog
production-service test fails before the fix and passes afterward. Both HU and phone now pass 46
focused cases each after the adopted range/format change, including all nine labels/cycle/reset,
stored integer tenths and actual muted WAV AudioTrack playout at 0.7x/1.5x with silence removal
across N/I/F. The cached mode-projection fix, current-profile selection and Music reset remain
verified. The latest signed RC replaces both working installations and performs the state owner's
expected clean reset for the new storage schema. Fresh 281 Player JVM results, Kotlin style, lint
and release verification pass. The original feature run checked parameters but did not establish
rendered tempo. Physical audible pitch/output, representative audiobooks, the full FILF corpus and
actual AAOS/projected-host acceptance remain unverified under
[open work](/Riff/LATEST/open-decisions) and [AAOS conformance](/Riff/LATEST/aaos-conformance).

Player state now follows its owning schema and existing pre-release clean-reset rule. Installing
this source over an older Player schema will reset that Player database; there is no compatibility
migration. The initial and latest storage-changing signed RC installations perform the expected
Player reset on both devices; Cuetree, file-only configuration and grants retain independent
ownership.
