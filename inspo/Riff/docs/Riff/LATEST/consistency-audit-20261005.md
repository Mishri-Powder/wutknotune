# Documentation and code consistency audit

> Supporting evidence, reviewed 2026-10-05. Requirements remain owned by the
> [registry](/Riff/LATEST/requirements) and its scoped descendants.

## Scope and method

The audit inventories 177 current Wiki pages, synchronizes all 150 exported contract references, and
checks current owners/anchors across 523 maintained Markdown, Kotlin and schema files. Its
first-party runtime/tool/build inventory covers 249 files, approximately 60,800 lines, excluding
history, immutable references, generated bindings, vendored code, research inputs and tests.

Focused manual review covers publication/recovery, nullable identities and SHRT joins, source
stamps/admission and CUE precedence, ordinary grouping/chapter expansion, shared acquisition,
AI/ReplayGain dependency and playback boundaries, probabilistic selection, SQLite queue/bookmark
identity, selected-root fences, startup configuration, mode gates and shared UI rendering. Static
scans and tests cover the wider inventory; this is not a line-by-line proof or a claim that all
defects are absent.

## Corrected implementation findings

| Finding                                                                     | Result and owning rule                                                                                                                                                                                                                                                                                                                                                 |
| --------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Cuetree cancellation could commit after pointer staging                     | Cancellation is checked after staging I/O immediately before CURRENT's atomic rename. Fault injection covers durable PREVIOUS and durable CURRENT candidates. [Persistence](/Cuetree/persistence)                                                                                                                                                                      |
| MDRG cancellation at the switch boundary could commit                       | A final check precedes the atomic CURRENT rename. The accepted analysis remains current on cancellation. [RG-6](/Riff/LATEST/replaygain-storage#rg-6-one-independent-mdrg-database)                                                                                                                                                                                    |
| Opening MDRG retained interrupted payloads, sidecars and pointer candidates | Startup/post-commit pruning keeps pointer-named data, generation authority and unrelated files. Enumeration does not promote candidates. [RG-6](/Riff/LATEST/replaygain-storage#rg-6-one-independent-mdrg-database)                                                                                                                                                    |
| Source observation overwrote DecoderHint before invalidation                | One reconciliation boundary applies dependency invalidation before workset selection. Tagged gain and AI remain independent; FILF cannot borrow whole-file Track Gain. [RG-5](/Riff/LATEST/replaygain-analysis#rg-5-measurement-and-dependencies)                                                                                                                      |
| Ordinary chapterized albums lost declared equal-ID FILF positions           | Construction, lifetime, validation, FlatBuffers and Indexer's ready projection retain declared FILF occurrences. [ALBM](/Cuetree/catalogs#albm)                                                                                                                                                                                                                        |
| Manual Play and Add disagreed about source deduplication                    | Shared ordering preserves direct Album/Title FILF positions and playlist occurrences, while physical FILM copies and Artist/Genre/Folder aggregates collapse at first occurrence. Selected-copy Play resolves its canonical index. This clarification was explicitly approved before implementation. [Queues](/Riff/LATEST/player-queues#queues-playlists-and-history) |
| Owner interruption could finish while acquisition still ran                 | The shared pipeline returns only after acquisition stops, preserving interruption. Host barrier timeout cannot authorize relocation with live source I/O. [Ordered acquisition](/Cuetree/API/Indexing#ordered-acquisition)                                                                                                                                             |

Storage and pipeline fault-injection cases were run against the defective implementation first, then
passed after correction. Source/schema versions remain unchanged; no data conversion or
compatibility reader is introduced.

## Documentation and maintenance rationale

The active API names hidden METADATA observations and current paired-buffer persistence methods.
Examples use strictly increasing publication generations. Catalog descriptions distinguish explicit
tables from ordinary grouping followed by untitled chapter expansion. Host MockMediaID's named
experimental discriminator is documented separately from shipped unseeded XXH3-64 hashing.

Current owner links replace retired references and a nonexistent schema section. Completed retention
work is removed from pending decisions. Normative reading budgets are met. Code uses informative
`AI-INTENT:` rationale with scoped owner links; documentation carries HTML comments at identity,
publication, configuration, analysis and bookmark boundaries. These comments explain maintenance
constraints without introducing requirements or narrating statements. Outgoing Wiki editions are
preserved verbatim in LEGACY; current handoff contains current evidence and pending work.

## Verification

551 JVM tests passed: Player 257, Indexer 129, configuration 24, Cuetree core 91/CLI 5, Cuegrain
core 40/CLI 3 and optional CED 2. Configuration checks, Kotlin formatting and Android lint passed.
Both libraries build/check offline from isolated source-only exports without Android or Riff source;
Cuegrain's optional native backend also builds with the SDK CMake. Final CLI checks include its
schema-shaped FlatBuffers JSON exporter.

39 focused HU tests passed on the disposable package, covering Player SQLite, startup recovery, Book
persistence and Bookmark spacing. The working release and user library were preserved. No new
release was installed. Markdown formatting/lint and current-reference validation pass. Exact API
read-back/public-route checks and the whole-Riff publication audit accompany the Wiki batch.
Evidence is under `status/consistency-audit-261005`.

## Follow-up implementation status — 2026-10-05

The storage-transfer contract now assigns relocation only the complete byte inventory; semantic
validity remains with ordinary open/recovery. Settings and playlist command gates use the current
mode/motion policy, with actual-AAOS host evidence still open. Cuetree's whole-metadata dependency
revision is implemented while eligible IDs survive. The optional header field remains within format
7; absent revision triggers a whole-metadata rebuild, with no compatibility reader. These changes
and their fresh verification are recorded in [worklog](/Riff/LATEST/worklog). Large-library and
real-media evidence remains in [open work](/Riff/LATEST/open-decisions).

Local geometry/contrast checks do not certify physical glare, audible output or supported
vehicle-host surfaces. Official sources reviewed on 2026-10-05:
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality) and
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles).
