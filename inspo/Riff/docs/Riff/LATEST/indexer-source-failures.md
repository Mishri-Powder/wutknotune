# Indexer source failures and crash recovery

> Owner/scope: source-local failed acquisition, suppression and interrupted-attempt recovery

## Failed source records

[Cuetree persistence](/Cuetree/persistence) owns the wire meanings. Every completed unsuccessful L1
source attempt, including access, unsupported-content and handled allocation failure, creates or
updates a physical FAILEDL1 binding. It retains parent, name, observed size/mtime and generation. A
new binding has a high-only technical source ID, never a fabricated Media.ID. The Android adapter
uses unseeded XXH3-64 of the opaque provider document ID without a file open; a POSIX adapter
supplies its own source ID. No cross-platform equality is required. Reconciliation uses parent/name,
not that hash alone. A prior successful binding retains its known Media.ID, aliases and factual
evidence as unavailable historical evidence; it cannot supply playable audio.

FAILEDL2 is a bit on an admitted physical MEDIA binding. A completed unsuccessful L2 attempt sets it
without hiding playable L1 facts or discarding valid gain/AI outputs. It suppresses further L2
source work through that binding; equivalent copies remain independently eligible. A failed analysis
result stays retryable while an equivalent physical copy remains, so the next pass may use that
copy. Accepted tags and valid shared results still require no source I/O. A no-result completed
analysis is not itself an error.

L0 observes both kinds without media opens. Equal observed size/mtime suppresses further work;
changed observations permit a new attempt and clear applicable suppression. Unknown provider values
have a stable missing-value representation, never a per-process synthetic change mark. Disappearance
retains the last observed generation and follows ordinary TTL; a still-present failed binding
refreshes its observation mark. Cancellation and IPC interruption do not create a source failure. A
completed access error does suppress the source until changed attributes or a full applicable
rebuild. IA recreates all factual bindings; AU clears FAILEDL2 and analysis attempt recovery
together with MDRG. Neither command changes user media or Player mutable state.

Every consumed candidate, including failure and an unchanged failed-record skip, advances checked
progress and offers a checkpoint boundary. The existing common 30/60-second publication clock
remains authoritative. Never publish a partly acquired successful MEDIA.

## Attempt journal

Physical acquisition includes collision-tail and fallback fragment-identity rereads. These reads use
the same acknowledged attempt lifecycle and source-local failure policy as initial metadata
acquisition. A checkpoint projection must not bypass that lifecycle or lose a failure when the
working scan resumes. No source I/O begins merely to resolve an already suppressed physical binding.

Before actual L1/L2 source work, Indexer registers an attempt and waits for the application ACK
under [reliable IPC](/Riff/LATEST/reliable-player-indexer-ipc). Player keeps only an opaque
in-memory journal: source key, observed stat, stage, optional span, attempt reference and
NORMAL/ISOLATED mode. The key is independent of computed Media.ID and addresses the exact physical
source. START ACK is issued after journal mutation. END identifies the exact attempt and is
acknowledged after removal. Duplicate START returns its original decision; a duplicate END cannot
remove a newer attempt.

An unfinished attempt from a confirmed dead Indexer is suspicion, not immediate failure. After full
L0, matching suspected sources are checked outside normal pipelining, one at a time, with
preparation, decode/inference and publication workers quiescent. Release decoder/model resources
before returning to normal work. Emit bounded recovery status after the progress prefix. A
successful isolated attempt resolves suspicion; a returned failure sets the applicable failure
record. If an ISOLATED attempt again dies with Indexer, its next matching observation is marked
FAILEDL1/FAILEDL2 before any source open. The accepted policy tolerates two independent process
terminations on the same source/stamp/stage. A timeout or dropped IPC packet never counts as death.
Changed/missing source observations discard obsolete suspicion. Explicit rebuild clears its
applicable journal scope.

A matching isolated retry displays `Suspect file: <filename>` for its active source work. A newly
committed FAILEDL1 or FAILEDL2 failure, including quarantine before opening, displays
`FERR file: <filename>`. FERR is the diagnostic name for those existing kind/flag states, not a new
persisted bit or field. Do not repeat this diagnostic for unchanged already-failed fast skips. Both
details use the same bounded display-name formatter as full loudness analysis, follow the current
stage's cumulative counts and publication-period speed, and contain no path/URI/document ID. The
entire Binder status uses one 120-character limit. Preserve counts/rate and trim a long detail at a
character boundary with ellipsis before it can overflow the delivery contract. Cancellation and IPC
errors produce no FERR diagnostic. Ordinary progress resumes without any artificial delay or change
to publication cadence.

Player does not scan, parse or produce facts. If both processes die, the journal is lost and work
restarts normally; no durable IPC transaction log is required.

## Order and bounds

Each pass randomizes actual file acquisition within each directory. Analysis randomizes directory
jobs within decoder-hint cohorts and cohort order, retaining bounded decoder reuse. Isolated
recovery is also randomized but strictly sequential. Catalog/track/chapter order remains owned by
ordinary catalog rules, independent of acquisition arrival. Failures are per physical source, never
a whole-Media.ID ban. Tests cover loss/duplicate ACK, death before and after START, isolated second
death, cancellation, source changes, aliases, hidden CUE, retained fragments, TTL and zero-I/O
skips.
