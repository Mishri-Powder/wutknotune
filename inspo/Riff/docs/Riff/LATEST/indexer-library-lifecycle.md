# Indexer library lifecycle

> Owner/scope: Indexer library lifecycle and immutable-artifact coordination

## Indexing and immutable data

Cuetree is the sole source of indexed media, album, artist and genre projections. Its synchronized
source/catalog cohort has a strictly increasing publication generation. The optional
[MDRG](/Riff/LATEST/format-mdrg) artifact combines AI and ReplayGain, with its own strictly
increasing generation series. Other databases need not share any generation. Every changed
publication consumes Cuetree TTL directly, including checkpoints within one traversal; an unchanged
pass does not publish. Indexer alone produces the Player projection, including accepted AI genres.
Player does not rebuild albums, artists or genres.

Publication follows [Shared FlatBuffer publication](/Riff/LATEST/immutable-publication). Producers
validate identifiers, versions, bounds, ordering, references and checksums before activation. A
complete immutable artifact and its checksum sidecar are committed atomically; the current reader
receives a validated read-only descriptor. Optional MDRG failure never invalidates a Cuetree media
snapshot.

After the selected-root startup gate succeeds, at custom-UI startup a successful query proving that
the package has zero persisted read tree grants MUST open SETTINGS_SCREEN and immediately its Media
Folders grant selector with Add available. Player MUST NOT request or bind Indexer until the first
read grant is persisted. Playback service remains available without library work. Cancellation
leaves the empty-library UI stable. Failure to query persisted permissions is not evidence of zero
grants and MUST NOT trigger this route.

A Media Folders child OK that changes the persisted grant set is a cross-process barrier whenever
Indexer is already bound. Player first calls Indexer through Binder while the old grant set remains
unchanged. Indexer rejects/coalesces further reruns, lets the currently active pipeline finish
through its ordinary final immutable publication and callback delivery, becomes idle, and
acknowledges quiescence only after that success. A failed barrier leaves grants unchanged. After
acknowledgement, Player persists staged additions before staged removals, then resumes Indexer and
requests exactly one new full pipeline. That pipeline starts with Cuetree source discovery and
observes only the committed new grant set. A no-change child OK performs no barrier and requests no
pipeline. If grant mutation fails after quiescence, Player resumes Indexer against the actually
retained grant set and reports failure; it never leaves Indexer quiesced. The first grant committed
from the proven zero-grant startup state has no preceding active pipeline: Player commits it without
a barrier, then performs the first Indexer bind, whose initial full pipeline starts with Cuetree
source discovery.

Indexer MUST treat zero active grants or zero currently accessible grants as a successful no-work
input. When no valid Cuetree snapshot has ever been published, that pass publishes one empty
source/catalog cohort; no unchanged pass republishes it. MDRG remains optional. When a valid prior
library exists, temporary source absence follows normal cached unavailability and
publication-generation TTL retention and MUST NOT immediately replace it with an empty library. No
zero-source condition may produce an error status or crash loop.

Metadata and AI/ReplayGain use the same bounded ordered acquisition engine. File-only
`indexer.pipelineDepth` defaults to 3 and includes the consumed item. One producer prepares input;
the caller owns mutable integration and publication. Cancellation is checked before acquisition and
before handing a completed result to the consumer. Strict
[L0/L1/L2 stages](/Riff/LATEST/indexer-staging#5-l0-l1-l2-and-common-publication-clock) share one
publication clock. Completed unchanged media/CUE reuse successful stat, metadata, identities and
structures. MEDIA/METADATA kind implies successful L1; FAILEDL1 suppresses unchanged failed inputs.
No completion flag or partial metadata-upgrade workflow exists.
[Source failures](/Riff/LATEST/indexer-source-failures) owns quarantine and reset; acquisition order
is randomized independently of catalog order. Analysis receives the current scan's admitted handles.
[Analysis procedures](/Riff/LATEST/indexer-analysis-procedures) owns decoder and inference
lifetimes. Source failure preserves valid retained facts under ordinary availability/TTL.

GNRE is exactly the uncompressed APK asset `assets/gnre/gnre.fb`, opened through its explicit asset
offset/length and read-only mmap. Do not extract, decompress, heap-copy the complete payload or make
a private writable authority. Bounded validation/hash scratch is allowed. Validate its range,
schema, generation and integrity. The [GNRE format](/Riff/LATEST/format-gnre) owns its current
generation; [project additions](/Riff/LATEST/project-genre-additions) owns required identities and
aliases. Rebuild and data-root relocation do not mutate GNRE.

## Automatic library recreation

A format, metadata fingerprint or identity-scheme mismatch, or a persisted cohort that fails
structural/integrity validation with no valid PREVIOUS, automatically schedules one full AU+IA
rebuild on the serialized Indexer worker. Reject incompatible facts before exposing artifacts; never
import, convert or partially refresh old data. Clear the old factual/Player cohorts, MDRG and
transient attempt journal through the same scoped reset path as Rebuild DB, preserve both
independent generation watermarks, and start the normal full L0/L1/L2 pipeline with existing grants.

This policy applies after selected-root admission and after explicit root activation. It never
changes Player state, profiles, playlists, source files, grants, GNRE, storage selection or
unrelated files. A PREVIOUS is valid only under the exact adopted version and compatibility rules;
an older-format PREVIOUS cannot avoid the full reset. A recovered compatible PREVIOUS, an empty new
store, missing selected storage, operational I/O failure, source-local failure or failed publication
must not trigger automatic deletion. The reader exposes a typed invalid-publication error rather
than conflating corruption with filesystem unavailability. A failed recreation reports the ordinary
pipeline error and does not loop.

Omit the transient Music library needs rebuilding message. Existing clearing and pipeline progress
statuses describe the actual work; no confirmation or new setting is added. Explicit AU/IA choices
retain their independent scopes for user-requested compatible-library maintenance.
