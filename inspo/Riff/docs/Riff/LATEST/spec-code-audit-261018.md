# Specification and code audit — 2026-10-18

This report records the authorized development-tree reconciliation. It does not declare a new
release, approve redesign proposals or replace functional contracts. Main remains based on
`cc597dd1da79ed25fc448d312c107304d7ece2cf`; all audit changes are uncommitted. Preexisting website
copy/governance work is preserved. No working application was installed, reset or uninstalled and no
public site/source publication or cache purge occurred.

## Coverage and limits

The current registry export was refreshed for 236 nonhistorical pages. Focused manual comparison
covered the registered catalog/identity/metadata/publication contracts; queues, history, focus,
state recovery, storage relocation, startup configuration, AI selection and their call boundaries.
UI owners were read for surrounding constraints; no renderer or layout policy changed. The count is
an inventory, not a claim that every sentence or every executable path received exhaustive review.

| Area                    | Evidence inspected                                                                                           | Result                                                                                                            |
| ----------------------- | ------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| Album/Singles/FILF/TTL  | Cuetree grouping, source/copy ownership, filename inference, classification, persistence and expiry          | Fixed filename/suffix defects and configurable language inputs; retained ordinary weights and explicit ownership. |
| Storage/startup         | Recovery, process store, Activity/playback/Indexer entry points, relocation fences and publisher cleanup     | Fixed false corruption detection and premature writer-quiescence acknowledgment.                                  |
| IPC                     | Session/sequence handling, asynchronous apply and cached completion                                          | Snapshot accepted message fields instead of retaining a mutable caller list.                                      |
| Player policy           | Frozen queues, alternate-binding open, actual-start ownership, focus, AI candidate selection and checkpoints | Clarified owner links and architectural comments; no new playback/UI policy adopted.                              |
| Configuration and build | Shared startup snapshot, build generator, GNRE verifier, source export and compatibility fingerprint         | Build-selected languages/genre roles verified; independent core retains no Android/Riff dependency.               |
| Documentation           | Current owner routes, stale handoff statements, duplicated storage prose and API signatures                  | Split queue/history/focus; removed stale external-Wiki exposure task; corrected no-regrouping API claim.          |

This audit does not close the existing full phone UI-fixture failures, live vehicle-host acceptance,
representative listening/model-quality or large-library time/memory budgets. Only HU was connected
for this run. No exhaustive defect-free or platform-conformance claim is made.

## Corrected defects

| Priority | Trigger and previous outcome                                                                                                                                                 | Correction and regression                                                                                                                                                                                                                                                     |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| P1       | A storage-migration cancellation returned from publication-worker close while the writer could still access the old root; an interrupted waiter also forgot its pending job. | Close waits for real executor termination and preserves interruption. An interrupted await retains the job until completion. Tests cover delayed cleanup and rejected overlapping submission. The host can time out its own fence, but cannot falsely acknowledge quiescence. |
| P1       | Reading an inaccessible SQLite header failed, and catch-all logic classified it as corrupt bytes. Recovery could quarantine valid live state.                                | Propagate access failure. Only successfully read invalid/truncated magic is a corruption observation. Header tests and 27 isolated Android state/recovery tests pass.                                                                                                         |
| P2       | The sender mutated its list while accepted IPC work was pending; application data and duplicate comparison could change after acceptance.                                    | Capture packet fields before asynchronous apply and copy completion fields before caching. Attachments retain their existing ownership.                                                                                                                                       |
| P2       | A complete numbered MP4 audio folder could not supply filename ordinals even though ordinary admission supports MP4.                                                         | Include MP4 among media-like listing names. Unsupported media-like entries still participate in completeness proof.                                                                                                                                                           |
| P2       | Embedded `Track=1/0` or `1/-2` was treated as a declared total and vetoed otherwise complete filename evidence.                                                              | Invalid totals remain neutral, consistent with factual-number parsing. Inference never edits authored tags or invents Total Tracks.                                                                                                                                           |
| P2       | `Work (Radio Edit]` and similar malformed suffixes were stripped as version evidence.                                                                                        | Require matching brackets and reject bracket fragments in dash descriptors; protect meaningful numbered-work suffixes.                                                                                                                                                        |
| P2       | Production Singles/grouping used independent EN/RU lists, bypassing configured languages and GNRE aliases.                                                                   | Build-generated `CatalogVocabulary` supplies literal lexical markers and GNRE role IDs to scans and TTL reconstruction. Generic-credit and disc-marker duplication is removed. Cuetree's empty default has no implicit language.                                              |

The user's language finding extended the audit scope. The initially considered synthetic Singles
genre was explicitly rejected; none was added. Source genres, technical basket names and display
rendering remain separate. The Riff processing revision is now V5 and includes an XXH3-64 vocabulary
fingerprint. A future installation of this edition follows the existing full derived-index
recreation policy; no device reset was performed during this audit.

## Documentation and comments

The architecture reading guide explains file-to-occurrence flow, source identity versus album
position, Singles examples, three independent lifetime clocks and failure boundaries. Queue,
listening-history and focus contracts now have separate owners. Storage process obligations link to
one owner for recovery text and relocation semantics. Current handoff statements no longer describe
old experimental/RC states as pending work. Outgoing editions are archived verbatim.

Handwritten English comments explain ownership, cancellation fences, non-destructive recovery,
immutable configuration, complete-release classification, copy/FILF authority and frozen AI/queue
inputs. They do not narrate individual code lines. Nonobvious maintenance invariants retain
`AI-INTENT` and scoped owner links.

## Verification

- Configuration: **29** JVM cases, zero failures/errors/skips.
- Indexer: **171** JVM cases, zero failures/errors/skips.
- Player: **301** JVM cases, zero failures/errors/skips.
- Cuetree core: **154** JVM cases, zero failures/errors/skips.
- Cuetree CLI: **6** JVM cases, zero failures/errors/skips.
- Genre/configuration Python suite: **15** cases, all pass.
- HU disposable package: **27** Player state/recovery instrumentation cases, all pass.
- Fresh complete root run: configuration checks, Indexer/Player unit suites, Kotlin style, Android
  lint, instrumented APK/test assembly and release-distribution verification all pass.
- Independent Cuetree check/assemble passes; a fresh source-only copy builds and tests with an
  explicitly selected external Cuegrain source dependency, without Riff source imports.
- Pinned Markdown formatting/lint and current-reference verification are publication gates.

The initial bug regressions were observed failing before their corrections. The invalid-total
fixture was refined to inspect raw inference input, then re-proved against original source. The
worker cleanup fixture was made tolerant of repeated cancellation interrupts before final runs.
Language tests cover an additional arbitrary language, incomplete build input, literal escaping,
caller mutation, configured generic names, persisted numeric roles and publication-only expiry. The
Riff integration tests use the actual GNRE alias index for Spanish, Portuguese, German and Russian.
In this corpus `música electrónica` is ambiguous (Q9778/Q627906); it remains text and does not
invent an Electronic role. Unambiguous `electrónica` resolves normally. This preserves the existing
ambiguity rule rather than maintaining another alias list.

Canonical evidence directory: `~/riff/status/spec-code-audit-261018`.

| Evidence                                                                                 | Scope                                                                   |
| ---------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `regressions-before-*.log`, `regressions-before/`                                        | Negative regression evidence, including final filename reproof.         |
| `final-all.log`, `final-ipc-bound.log`, `final-test-counts.json`                         | Fresh root checks and exact JVM counts.                                 |
| `language-cuetree-final.log`, `source-only-build.log`                                    | Independent core/CLI and source-export verification.                    |
| `language-python-final.log`                                                              | Build-language validation and packaged GNRE roles.                      |
| `hu-player-state-tests.log`, `hu-working-before.json`, `hu-working-after.json`           | 27 storage tests; working-package version/install metadata unchanged.   |
| `docs-lint.log`, `references.log`, `publication-receipts.json`, `publication-audit.json` | Documentation checks, exact API readback and anonymous internal routes. |
| `preexisting.patch`, `preexisting-status.txt`                                            | Preserved incoming working-tree scope.                                  |

## Complexity assessment

The [20-row comparison](/Riff/LATEST/complexity-review-261018) uses primary Android/Media3, SQLite,
FlatBuffers, beets, Kodi, MusicBrainz, MPD and ReplayGain sources. Its engineering judgments are
labeled as inferences. Weighted grouping, FILF, source retention, durable queues and process
isolation address actual adopted constraints. The strongest measurement-led simplification
candidates are general IPC retransmission, repeated full-model materialization and calibration cost
for small analysis worksets. Skin inheritance is optional product scope; it cannot be removed as a
bug fix. Large controllers and duplicated policy prose are maintenance debt, not proof that the
requested functionality is unnecessary.
