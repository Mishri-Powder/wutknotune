# Indexer staging and SAF coordination

> Owner/scope: Indexer durable staging, SAF rescan boundary and verification

## 5. L0-L1-L2 and common publication clock

A full pipeline has strict barriers: complete L0, then L1, then L2. L0 enumerates the complete
current SAF/POSIX tree and obtains provider name, MIME/candidacy, size, modification mark and
availability. It never opens media for container tags, identity or decoded audio. Provider listings
supply child stats; no separate per-child SAF stat is required.

L0 publications retain previous facts and availability for not-yet-observed sources, including files
that physically changed or disappeared without yet being observed. Only authoritative enumeration
establishes absence; provider failure retains ordinary unavailable-source diagnostics and TTL, not
an invented successful empty listing. New media without computed identity stay in the transient
workset, not playable projections. After observed change, a retained old identity is not confirmed
current content of that physical binding.

L1 acquires file metadata and Media.ID for new/changed inputs. Physical MEDIA and METADATA records
contain successful L1 facts and their successful stat; unprocessed new sources remain transient.
Equal successful observations reuse metadata, structures, identity and evidence. Completed failed
acquisition uses FAILEDL1 under [source failures](/Riff/LATEST/indexer-source-failures). No
L1_COMPLETE, completion field or per-binding metadata migration exists. Any changed format, metadata
dependency or identity scheme requires complete index recreation; incompatible old data is not
partially refreshed or imported. Ordinary changed-file acquisition in a compatible index retains
unavailable previous evidence until replacement/TTL. Collision rereads remain L1 work. Cuegrain owns
portable Media.ID; Riff injects Android access. Desktop/Android equivalence is not required.

L2 uses admitted handles and retained/new evidence without another tree walk. Its union workset
contains unresolved gain and Music AI; valid results for equal MediaIDs are reused. Interrupted work
remains eligible; completed source-local failures suppress their physical binding through FAILEDL2.
[Source failures](/Riff/LATEST/indexer-source-failures) owns retry/reset. Usable Track Gain
suppresses measurement independently of AI. A full usable retained tag set suppresses every L2
source open, audio read, decode and inference. Completed unchanged bindings need no L1 source open
either. New/changed bindings require L1 acquisition to establish tags and identity. Accepted RIFF
value/confidence pairs suppress their corresponding AI outputs. Missing/invalid pairs remain
unresolved; confidence is never invented. Automatic Books ignore every AI tag and are vetoed before
AI I/O; gain remains eligible. FILM and each FILF span have independent evidence/results.

One monotonic clock starts with actual L0 processing and spans all stages and both artifact streams.
The first changed publication is due at 30 seconds; later batches are due 60 seconds after the
previous successful batch. Phase transitions neither reset deadlines nor force intermediate flushes.
Publish changed, complete validated artifacts at safe item/directory boundaries. Cuetree and MDRG
retain independent generations. Every changed Cuetree publication consumes ordinary TTL. Final
pipeline completion immediately flushes changed valid state. No timer-only unchanged publication or
catch-up burst is permitted. [Immutable publication](/Riff/LATEST/immutable-publication) owns
durability/cadence; optional-analysis failure never invalidates completed factual acquisition.

Zero active or accessible grants remain no-work input under
[library lifecycle](/Riff/LATEST/indexer-library-lifecycle).

### Progress and period rates

Indexer reports cumulative completed counts and each known total independently of the current speed.
L0 omits an unknown denominator and measures folders/s; L1 measures media/s; L2 measures tracks/s.
Do not reset the displayed counts at publication.

Speed is completed units since the preceding successful generation-publication batch divided by
monotonic seconds since that same commit. The first period starts with actual L0 work in the current
pipeline. All stages and artifact streams share the period boundary. Only a successful publication
resets the time and per-stage counter baselines; unchanged offers, failed/cancelled writes, stage
changes and UI status updates do not. An asynchronous write closes the period at successful commit,
using the then-current completed counters rather than submission time. Zero elapsed time reports a
finite zero rate.

Counts and speed precede any bounded long-work detail, so full Media.ID or loudness diagnostics
cannot replace the progress report. Rates and baselines remain transient; no persisted rate,
completion Boolean/integer or new storage format is introduced.

## 6. SAF draft and rescan boundary

The Media Folders completion barrier waits for the whole active pipeline. It is distinct from
storage relocation's cancellation fence; no grant may be mutated under an active worker.

Player snapshots persisted read-tree grants when the Media Folders child opens. DocumentsUI returns
only a temporary candidate; Player persists staged additions and releases staged removals only after
explicit child OK. Cancel changes neither set. A no-change child OK requests no Indexer work. Grant
additions persist only the read and optional write flags actually returned by DocumentsUI;
reselecting an existing read-only tree can persist a newly offered write flag. The new capability is
a changed draft and observes the same quiescence barrier and one-scan rule as a changed URI set.
Removing a tree releases every persisted flag for that tree. When Indexer is already bound, Player
issues a synchronous Binder quiesce request before a changed draft mutates any persisted permission.
Indexer suppresses queued reruns, lets the current pipeline complete its ordinary final publication
and callback delivery using only the old grants, becomes idle, and acknowledges only after success.
Player then persists additions before removals, resumes Indexer and requests exactly one full
pipeline starting with Cuetree source discovery against the resulting committed grants. Barrier
failure leaves grants unchanged. Grant-mutation failure resumes Indexer against the actually
retained grant set and is not success. Proven zero grants at first custom-UI startup opens the
selector and defers initial Indexer binding until the first nonempty set is committed; because no
pipeline exists to drain, that first commit needs no barrier, and the first bind starts the initial
full Cuetree pipeline. Permission-query failure is not zero.

Indexer probes each persisted tree before admitting it to the Cuetree scan.
[Empty-listing admission](/Riff/LATEST/saf-empty-listing-workaround) owns the temporary-file
diagnostic, inconclusive read-only admission, active-failure exclusion and separate Player notice. A
missing removable tree is inactive for that pass and its unseen sources follow availability/TTL;
remaining grants continue. Complete URIs, document IDs and raw locators never enter UI status or
unbounded logs.

## 7. Required verification

Verify process ownership, exact-inode descriptors, generation and checksum rejection, retention,
file-boundary 30/60-second timing, twelve-hour-file deferral, no-work suppression, final flush,
crash/resume, coherent intermediate/final MDIA/catalog cohorts, zero-source publication, NoAccess
TTL and cancellation fences. Exercise multiple external roots, duplicate system descriptions, stable
IDs, writable probes and absence of SD/USB inference. Fault-inject every migration phase with and
without an existing target; require one active authority, source integrity during migration on
available storage, target rollback/replacement, no unrelated deletion, and exactly one full Level-0
pipeline after successful root change. Verify delayed mount and restart preserve selection and all
unopened state, wrong-volume return stays blocked, exact-volume return starts once, Cancel is
lossless, and confirmed reset is crash-idempotent and fresh. Verify no service/mapping starts while
waiting. Live removal is accepted user risk, including process termination and all-data loss. Verify
SAF OK/Cancel staging, old-grant completion/publication before mutation, idle acknowledgement,
exactly one new full Cuetree pipeline after addition or removal, and resume after mutation failure.
