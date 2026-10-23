# Riff engineering verification

> Owner/scope: cross-system diagnostics, experiment budget, rebuild references and verification

## Experiment budget

Before a bulk media experiment, run a bounded pilot, estimate wall time and select the smallest
representative sample that can answer the question. An experiment not explicitly approved as
long-running has a 30-minute wall-clock budget. If the projection exceeds it, stop and request
approval with the estimate and a cheaper sampling alternative. Do not decode an entire library
merely to improve file filtering or census completeness when the requested statistic can be obtained
from a representative sample. Report the planned and actual wall time, sample rule, exclusions and
stopping condition.

## Active Cuetree diagnostic export

The [Cuetree CLI JSON export](../../Cuetree/build.md) supplies schema-shaped host output for the
persisted Cuetree cohort. Android consumers validate the exact format owned by
[Cuetree persistence](../../Cuetree/persistence.md). An Android diagnostic exporter requires its own
bounded, privacy-aware scope.

## Audio-analysis and index rebuild commands

The two independently scoped AU and IA commands and their reset effects are specified only in
[ReplayGain rebuild](replaygain-lifecycle.md). AU resets MDRG and its analysis work; IA resets
Cuetree library data. [Settings](settings-screen.md) owns draft, navigation and submit-once
behavior; the storage contract owns cancellation and immutable-safe publication.

## Deferred metadata inference

**MAY-INDEXER-01.** Indexer MAY in the future derive missing Album and Artist/Author from contextual
evidence. Before implementation, separately specify inference, precedence, grouping, provenance,
identity stability and contradictions. This permission supplies no current fallback, tag write or
external lookup. It does not govern
[explicit CUE/TOC grouping](album-grouping-ag-1-ownership-and-inputs.md), whose declared membership
and order require no missing-tag inference.

## Performance and verification

Distinct Media.id count determines tiers: through 125,000 all performance requirements without
cardinality-specific UI waits; 125,001–200,000 preserve correctness, safety and explicit playback
latency with bounded background work; above 200,000 best effort without cardinality-only
rejection/truncation. Large builds use bounded file-backed/mapped construction, not duplicate heap
graphs. Validate exactly 125,000, 125,001, 200,000 and 200,001 items.

On QCM6125, each conforming cold process or post-power-cycle start reaches Media3 playback readiness
within 15,000 ms from earliest Player application entry; active-request audio also begins within it
when focus/media permit. Preconditions are readable current data root, intact validated
generation/AI data/queue/checkpoint and eligible binding/focus. Restore only bounded needed state;
no scan, all-library heap decode, large-queue heap load, selection rebuild or reanalysis may block
readiness. Every run at or below 200,000, including small/maximum queues at 125,000 and 200,000,
must pass; averages/warm starts do not excuse failure. Record monotonic
restoration/binding/Media3/request/first-audio milestones and maximum duration. Recovery conditions
are reported separately.

Verification traces to this contract or its registered children and covers deterministic business
logic, Android adapters/instrumentation, real HU, fault injection/death,
checksums/schema/references, partial publication, descriptor lifetime/retention, SQLite/recovery,
storage-move interruption, delayed mount, exact-volume return, blocked service admission and
confirmed fresh Internal reset, cold restore on available storage, UI resize/density corruption/font
scaling through 2.0/RTL/accessibility, bounded rendering and exact sampling invariants. No
NaN/infinity/overflow, banned/unavailable draw, duplicate-induced probability or censored-dislike
learning is permitted. Interrupted work on available storage retains the last valid generation and
durable Player state; accepted loss of the backing medium has no preservation guarantee.
