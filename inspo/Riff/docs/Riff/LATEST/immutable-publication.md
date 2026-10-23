# Immutable artifact publication

> Owner/scope: cross-process immutable publication and descriptor delivery

## Cuetree library cohort

The active library is one [Cuetree cohort](/Cuetree/persistence): MDIA, ALBM, ARTS and GNRV.
`SnapshotStore` writes immutable files and a manifest of byte lengths and XXH3-64 checksums, then
atomically replaces CURRENT only after all four members are durable. Player accepts only the
complete, validated cohort named by CURRENT; it never mixes members from different generations.
Indexer serves read-only descriptors for the exact validated files. A pending, corrupt or incomplete
candidate leaves the previously accepted cohort active. The factual and Player stores each retain
only their CURRENT and PREVIOUS complete cohorts after a committed publication; open read-only
descriptors remain valid when an older directory is removed. An invalid CURRENT can fall back only
to its validated PREVIOUS, never to a directory found by enumeration. See
[Cuetree persistence](/Cuetree/persistence).

An existing valid Cuetree snapshot is served first at startup, before a new scan publishes anything.
The previous snapshot is the sole retained scan input. A missing Cuetree snapshot starts from empty
state.

## Publication cadence

The [stage owner](/Riff/LATEST/indexer-staging#5-l0-l1-l2-and-common-publication-clock) defines
strict L0/L1/L2 barriers. One monotonic clock starts at actual L0 processing and spans Cuetree and
MDRG. The first changed complete intermediate batch is due at 30 seconds; later batches are due 60
seconds after the previous successful batch. Entering L1/L2 does not spend an unelapsed first
deadline, create a timer or force a phase-boundary flush.

At a due completed directory/media/table, handled-failure/failed-skip or analysis-item boundary,
publish each changed validated artifact. Unchanged streams retain pointer/generation. Each stream
keeps an independent strictly increasing generation. A long item finishes before publication;
partial acquisition is never exposed. Overdue deadlines yield one latest batch, not a catch-up
burst. Final pipeline completion, including handled optional-analysis failure, immediately flushes
remaining changed valid state. Cancellation/systemic failure retains accepted cohorts. Changed
Cuetree publications consume TTL.

Before authoritative L0 enumeration, retain unvisited previous facts and availability. Successful
physical MEDIA/METADATA kind implies completed L1 and its successful stat; FAILEDL1 records
completed errors under [source failures](/Riff/LATEST/indexer-source-failures). There is no L1
completion flag, header Boolean or metadata-upgrade pass. L0 cannot publish new playable sources
with provisional Media.ID. Old facts for an observed changed binding are retained, but the binding
is pending rather than confirmed access to old content. Identity collisions and structural relations
validate in every offered cohort. An unchanged pass publishes nothing.

Player retains its cohort while validating exact-inode replacements. Optional MDRG failure never
blocks factual publication. The shared clock does not combine formats/generations into one
transaction.

<!-- AI-INTENT: CURRENT is the acceptance boundary. A failure before the switch preserves accepted input; cleanup after the switch cannot undo a valid publication. -->
