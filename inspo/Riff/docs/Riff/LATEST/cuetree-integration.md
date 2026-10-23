# Cuetree integration in Riff

> Owner/scope: Android adapter between independent Cuetree and Riff Indexer/Player.

Indexer is the only producer of library facts and ready Album, Artist and Genre projections. The
Android Indexer owns SAF discovery, Cuetree scanning, source identity adapter, persistence and
publication. Player consumes one immutable Cuetree MDIA/ALBM/ARTS/GNRV cohort and may apply profile
filters, sorting and navigation layout to those ready records; it must not invent catalog entities
or run grouping algorithms. The [Cuetree persistence contract](/Cuetree/persistence) owns the exact
library format.

At startup, Indexer first exposes a committed, compatible Cuetree cohort when one exists, then
starts observation. The retained Indexer database keeps unavailable source and catalog records for
TTL; its published Player cohort keeps the complete MDIA hierarchy with automatic Book evidence and
contains ALBM/ARTS/GNRV containers with at least one available member, retaining every ordered
member of each published Album/Title even when a member is temporarily unavailable. Artist and Genre
links include only published containers. The Android SAF provider excludes every dot-prefixed child
at every depth, including directories, media and CUE files. Cached availability, not a new SAF
query, controls publication and Folder rows.

For each readable persisted SAF tree, Indexer resolves the granted media-root UUID separately from
the SAF URI. It accepts `.riff/riff_root_uuid` only when canonical UUIDv4 text and the
`riff_root_uuid.xxh3` eight-byte XXH3 sidecar agree. If absent or invalid and the persisted grant
allows writing, Indexer attempts to create/recreate the marker through SAF and accepts it only after
write, sync and read-back succeed. If publication fails, a readable UUIDv4 in
`.thumbnails/.database_uuid` supplies the fallback; otherwise use
`00000000-0000-4000-8000-000000000000`. Failure to publish a marker does not fail scanning. Before
marker writes, [SAF empty-listing admission](/Riff/LATEST/saf-empty-listing-workaround) checks the
unfiltered root listing and applies the provider-issued temporary-file diagnostic; an active
platform failure excludes that root for the pass. UUID/XXH3 identity validation remains unchanged.
The resolved UUID is the root identity used for Cuetree reconciliation and Player binding IDs; SAF
URI is only the current access locator. `.riff` and all other dot-prefixed children remain excluded
from the media tree. This source-root UUID is separate from app-data storage identity and Media.ID.

[Immutable publication](/Riff/LATEST/immutable-publication) owns the first-30-second and
subsequent-60-second publication cadence. The Indexer sends bounded folder/media progress through
its existing Binder status channel independently of artifact publication; Player displays that text
verbatim. At a due checkpoint, Indexer publishes one complete available-only Player cohort, so ready
Album, Artist and Genre lists update together while the scan continues. The selected app-data root
and SAF grant barriers remain authoritative. Riff uses Cuetree's bundled
`DefaultAlbumGroupingPolicy` on eligible base MEDIA. Valid external CUE/TOC and independently titled
tables remain explicit. Untitled embedded chapter tables expand the base files after ordinary
grouping, so a multi-file book can be one ALBM/Book with ordered FILF. Riff injects its bundled GNRE
matcher as Cuetree's separate genre resolver: unambiguous matches carry the dictionary ID, while
unmatched or ambiguous names remain text references. GNRE is not part of the four-file Cuetree
cohort. The current Android configuration does not load a user grouping file or JavaScript engine. A
rebuild clears its selected derived-data scope and starts a fresh Cuetree scan while retaining
generation watermarks.

FILF is a playable track whose Cuetree parent points to a media source, whose nullable microsecond
begin/end delimit playback, and whose metadata comes from the fragment record. Playback resolves the
first available equivalent base media source in Cuetree source order, retaining that order for
fallback. Its queue/checkpoint position is relative to the fragment. A copied source with the same
MediaID is an additional binding, not automatically another logical position.
[AG-10](/Riff/LATEST/album-grouping-positions) owns copy/position resolution before projection. The
Android projection and direct Album queues preserve admitted positions, including repeated IDs; they
never independently deduplicate ALBM by Media.ID. Metadata and preferred binding come from the
selected ALBM occurrence. The current [Player state schema](/Riff/LATEST/player-state) records
whether `MediaID.lo` is present, so a short ID and a full ID with a zero low word never collapse.
Equivalent copied FLAC files each expose the fragment in their Folder branch and bind playback to
that physical copy. The [UI color contract](/Riff/LATEST/ui-typography-colors) owns the optional
file-only FILF tint; color never determines playback identity.

[Cuetree analysis integration](/Riff/LATEST/cuetree-analysis-integration) owns the shared
AI/ReplayGain pipeline and one MDRG artifact. Indexer is the only producer of AI-derived genre
projections as well as ordinary Cuetree catalogs. The Player consumes the resulting ready
projections and never groups inferred genres itself. The AU analysis rebuild and IA Cuetree-index
rebuild are independent choices. Short MediaID references in Player state and MDRG resolve through
the retained Cuetree SHRT alias to the original full identity; they are not rewritten on collision.

Cuetree stays independently buildable and has no Riff or Android source dependency. Riff supplies
Android providers and any platform identity implementation through Cuetree’s injection points.

## Source evidence

A scan enumerates each SAF tree once and assumes its contents remain stable until the scan
completes. Each child-listing query requests display name, document ID, MIME, LAST_MODIFIED and SIZE
together. The Android provider passes each row's source stat to Cuetree, so a child has no separate
initial SAF stat query. Root discovery supplies its already queried stat to Cuetree, so the scan
does not query the same root again. Cuetree does not re-stat media after metadata or identity reads.
Only after complete L0 does L1 open new/changed media/CUE; equal successful observations reuse MDIA
and equal FAILEDL1 observations suppress source I/O. Changed metadata/identity policy requires
complete recreation, not a partial update. Successful stat and facts commit together. The admitted
source handles are handed to optional AI/ReplayGain work without a second SAF tree walk. Hidden CUE
metadata records do not enter Player Folder or track projections. CUE album/track fields take
per-field priority over file tags, with file tags filling absent CUE fields.

Riff stores selected external gain, opaque DecoderHint and positive observed `cuetree.durationUs` in
Cuetree media source evidence. The same bounded Media3 admission retriever supplies duration;
unknown is absent. A GNRE-confirmed Book source supplies `cuetree.contentKind=book`. Static
standard-Audiobooks root intent is supplied through `RootSpec.book`. This allows an unchanged MDIA
source to supply analysis inputs after restart without reopening its metadata. The Player cohort
omits this internal evidence.

The strict [stages](/Riff/LATEST/indexer-staging#5-l0-l1-l2-and-common-publication-clock) share one
publication clock. Cuegrain owns portable Media.ID hashing through consumer adapters;
Desktop/Android equivalence and backend identifiers are not required. Android uses provider MIME
candidacy and the stock Media3 actual-source capability probe during changed-source acquisition. A
successful probe supplies the opaque DecoderHint retained in source evidence. The optional-analysis
stage consumes that hint directly.

After unchanged ordinary grouping, Cuetree applies [Singles classification](/Cuetree/singles).
Player renders personal baskets as ready ordinary browse rows and never reruns that classifier.

Player applies the
[technical Singles display](/Riff/LATEST/player-catalog-membership#technical-singles-display) to the
ready catalog kinds only. Cuetree's stored titles and identities remain untouched.
