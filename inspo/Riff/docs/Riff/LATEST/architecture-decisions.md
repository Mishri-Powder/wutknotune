# Riff architecture boundaries

This map helps contributors locate controlling contracts. Each linked owner defines the exact rule.

## Source and catalog ownership

- [Cuetree integration](/Riff/LATEST/cuetree-integration): Indexer owns library facts and ready
  catalog projections; Player consumes them.
- [Identity](/Cuetree/identity-access#short-id-alias): nullable low values, lazy collision
  resolution and retained SHRT aliases preserve durable references.
- [Publication](/Riff/LATEST/immutable-publication#publication-cadence): changed snapshots use
  strictly increasing generations; each publication consumes TTL.
- [Grouping](/Cuetree/catalogs#albm): retained random Album IDs, explicit CUE ownership, ordinary
  grouping before chapter expansion, followed by [Singles classification](/Cuetree/singles) with one
  global basket and qualified personal baskets. [Filename evidence](/Cuetree/filename-evidence)
  supplies conservative pre-group ordering evidence.
  [Album formation V2](/Riff/LATEST/album-grouping) owns global metadata grouping, mixed disc-folder
  spellings, corroborated Album disc suffixes, positions, release guards and singleton admission.
  [Persistence](/Cuetree/persistence#mvp-format) owns the format-version fence; deployment requires
  automatic full index recreation without migration.
- [Cuegrain integration](/Riff/LATEST/cuegrain-integration): parsing and conservative repair have
  one independent library owner.
- [Metadata revision](/Riff/LATEST/genre-processing#gp-5-revision-and-conformance): a changed
  parser, processing or GNRE revision requires complete index recreation; partial upgrades are
  forbidden.
- [Ordered acquisition](/Cuetree/API/Indexing#ordered-acquisition): metadata and audio analysis
  share a bounded pipeline; analysis uses scan-admitted sources.
- [Analysis](/Riff/LATEST/cuetree-analysis-integration): MDRG combines AI and ReplayGain and owns an
  independent generation series.

- [SAF empty-listing workaround](/Riff/LATEST/saf-empty-listing-workaround): a provider-issued
  temporary document diagnoses false-empty listings; inconclusive read-only roots remain inactive.
  No opaque descendant ID is fabricated and no diagnostic locator becomes identity authority.

## Player and platform ownership

- [Progressive A-Z navigation](/Riff/LATEST/player-az-navigation): physical keypad rows are packed
  before availability filtering; the final double-width Backspace row always remains. That owner
  also controls explicit Backspace/breadcrumb/Play filter saves and search return snapshots.
- [Playback transport](/Riff/LATEST/player-transport): local portrait/landscape packing, common
  playback-control height, safe-bottom block and decorative non-reserving status; the
  [MAIN owner](/Riff/LATEST/main-screen-scope-and-platform-boundary#status-line) owns its status
  placement.
- [Audiobook speed](/Riff/LATEST/player-audiobook-speed): one profile preference shared by all
  books; Media3 executes the mode/category policy and the existing action component owns the
  control.
- [Queues](/Riff/LATEST/player-queues) and [bookmarks](/Riff/LATEST/player-bookmarks): active queues
  are frozen Player-owned state; library availability controls eligibility.
- [Listening history](/Riff/LATEST/player-listening-history) owns actual-start accounting;
  [audio focus](/Riff/LATEST/player-audio-focus) owns interruption/restoration policy.
- [Selection](/Riff/LATEST/player-automatic-selection): probabilistic selection and Like/Dislike/Ban
  use the adopted profile model.
- [ReplayGain playback](/Riff/LATEST/replaygain-playback): queue-wide external Album Gain
  eligibility, per-track fallback, N/I/F audio paths and limiter behavior.
- [Selected storage](/Riff/LATEST/storage-roots): one authoritative application-data root,
  acknowledged relocation fences and explicit recovery choices.
  [Relocation](/Riff/LATEST/player-storage) verifies byte-faithful transfer; ordinary opening owns
  semantic validity.
- [Internal hashes](/Riff/LATEST/runtime-architecture#internal-hash-policy): XXH3-64 for every
  first-party product hash; external provenance remains separate.
- [Configuration](/Riff/LATEST/project-configuration): one immutable startup snapshot per process;
  file-only settings share the JVM schema.
- [Application modes](/Riff/LATEST/application-modes): motion restrictions and host boundaries are
  independent of device identity.
- [Shared modal dialogs](/Riff/LATEST/ui-modal-dialogs): every existing and future app-owned modal
  uses one component and the shared Playlist chrome typography, including bootstrap recovery.
- [Icons](/Riff/LATEST/icon-system): adopted APK resources and standard Android rendering.

Implementation comments cite the owning rule at the point where its invariant is enforced.

The 2026-10-08 audit decisions retain profile identity across queued writes, make accepted playlist
saves non-cancellable under [queues](/Riff/LATEST/player-queues), keep Settings persistence off main
under [Settings](/Riff/LATEST/settings-screen-scope-and-rows), and use the packaged mode only when
no valid bootstrap mode exists under
[startup recovery](/Riff/LATEST/application-modes-selected-storage-startup-recovery). Desktop and
Android publication share the core [Cuetree comparison](/Cuetree/API/Indexing).

## Release-audit corrections — 2026-10-10

The user approved every finding in the [release audit](/Riff/LATEST/release-audit-261010) and public
documentation. [UI foundation](/Riff/LATEST/ui-foundation) owns one artwork pipeline, bounded
transient recovery and exact result identity;
[listening history](/Riff/LATEST/player-listening-history) owns attempt-profile continuity. Other
corrections reconcile existing contracts. All seven corrections and fresh HU/host verification are
recorded in the audit report; this decision map adds no independent requirements.

Boolean Yes/No preference presentation is owned by
[UI foundation](/Riff/LATEST/ui-foundation#boolean-preference-controls); the shared icon renderer
owns canonical `ic_*` name resolution. Settings, profile persistence and motion gates retain their
separate owners.

## Staged indexing ownership

[L0/L1/L2](/Riff/LATEST/indexer-staging#5-l0-l1-l2-and-common-publication-clock) use strict barriers
and one clock. [Cuegrain](/Cuegrain/API/MediaIdentity) owns portable Media.ID; Riff owns the Android
adapter. [Source failures](/Riff/LATEST/indexer-source-failures) use the FAILEDL1 kind and FAILEDL2
bit 10, without a separate L1 completion flag, Boolean or backend discriminator.
[Library lifecycle](/Riff/LATEST/indexer-library-lifecycle#automatic-library-recreation) owns
automatic full recreation of incompatible/invalid derived facts after storage admission. Desktop
identity equivalence is not required.

## Audit correction ownership — 2026-10-13

[Source failure recovery](/Riff/LATEST/indexer-source-failures) includes every physical identity
reread and carries checkpoint-discovered failures into the active scan. The
[IPC owner](/Riff/LATEST/reliable-player-indexer-ipc) bounds source keys independently of filenames.
[Player queues](/Riff/LATEST/player-queues) captures command store/profile ownership and defines
physical-open fallback before occurrence unavailability. [Artwork](/Riff/LATEST/ui-foundation) keeps
provider cancellation outside critical callers and refreshes buffered occurrences after generation
changes. [Copy positions](/Riff/LATEST/album-grouping-positions) retains uniquely assignable partial
metadata without inventing factual tags.

These corrections reuse existing storage formats. The shared acquisition boundary, command context,
SAF resolver and source-opening wrapper consolidate policy at its actual owner. Validated unchanged
cohort models are reused while grant resolution remains fresh. Catalog membership/ID indexes replace
repeated whole-catalog scans. This map does not introduce independent requirements.

## Specification audit — 2026-10-18

[Language evidence](/Riff/LATEST/catalog-language-evidence) is selected at build time. Cuetree
receives the same vocabulary for scans and TTL reconstruction; GNRE identities carry genre roles.
The independent library has no built-in EN/RU fallback. [Storage fences](/Riff/LATEST/storage-roots)
require actual worker completion, and [state recovery](/Riff/LATEST/player-state) distinguishes
unreadable storage from corrupt bytes. [Audit evidence](/Riff/LATEST/spec-code-audit-261018) records
corrections and verification; [complexity review](/Riff/LATEST/complexity-review-261018) compares
alternatives without authorizing a redesign.
