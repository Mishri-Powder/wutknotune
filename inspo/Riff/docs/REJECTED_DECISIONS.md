# Rejected design options

The following options are excluded by the current contracts. Do not propose or implement them as
routine alternatives. Changing one requires an explicit change to its owning Wiki contract.

## Authority and persistence

- Making this repository catalog the requirements authority. The Wiki contract for each scope is
  authoritative. [Documentation governance](Riff/LATEST/documentation-governance.md)
- Adding pre-release data migrations, dual readers or compatibility shims. Current-format recovery
  and explicit root relocation retain their separate requirements.
  [Runtime architecture](Riff/LATEST/runtime-architecture.md)
- Using SHA-256 or another hash for first-party runtime identity, internal checksums, source
  revisions, model validation or root relocation. Use unseeded XXH3-64; external distribution and
  imported-source provenance may use their required SHA-256 evidence.
  [Internal hash policy](Riff/LATEST/runtime-architecture.md#internal-hash-policy)
- Publishing mixed-generation artifacts, reusing a generation within one database type or promoting
  a directory discovered by enumeration. Publish a validated cohort through its committed pointer.
  [Immutable publication](Riff/LATEST/immutable-publication.md),
  [Cuetree persistence](Cuetree/persistence.md)
- Treating inactive roots as writable replicas or recovery authority, merging roots, or silently
  falling back to Internal when the selected external root is unavailable.
  [Storage roots](Riff/LATEST/storage-roots.md)
- Semantically revalidating SQLite or immutable database contents during application-data
  relocation. Relocation verifies the complete byte inventory with path, type, size and XXH3;
  ordinary opening and recovery handle database validity.
  [Player storage](Riff/LATEST/player-storage.md)
- Guessing SD versus USB from a volume label or device identity. Use the supported storage APIs and
  stable volume identity. [Player storage](Riff/LATEST/player-storage.md)

## Indexing and metadata

- Building Album, Artist or Genre entities in Player, or querying SAF to determine their
  availability. Indexer publishes the ready projection; Player filters and renders it.
  [Player catalog membership](Riff/LATEST/player-catalog-membership.md),
  [Cuetree integration](Riff/LATEST/cuetree-integration.md)
- Using a SAF URI as the stable volume or media identity. The current locator is for access;
  identity follows the separate volume and MediaID rules.
  [Cuetree identity and access](Cuetree/identity-access.md)
- Copying container, tag, CUE, chapter, gain-decoding, charset or text-repair parsers into Cuetree,
  Indexer or Player. Cuegrain owns those observations. [Cuegrain contract](Cuegrain/contract.md),
  [Cuetree contract](Cuetree/contract.md)
- Rewalking the SAF tree for optional analysis, re-statting a media source after metadata/identity
  reads, or reopening unchanged media when the metadata dependency revision still matches. A changed
  revision reacquires all metadata without rehashing unchanged media identity. One scan uses listed
  source observations and passes admitted handles to analysis.
  [Cuetree contract](Cuetree/contract.md), [Cuetree integration](Riff/LATEST/cuetree-integration.md)
- Partially refreshing saved Genre/Artist metadata after a parser or GNRE revision, retaining old
  raw tags for incremental projection, or rehashing unchanged audio merely for metadata revision.
  [Genre processing](Riff/LATEST/genre-processing.md#gp-5-revision-and-conformance)
- Inferring chapter order from ID-sorted storage, or creating artist/genre-specific copies of the
  Singles album. Use the ordered ALBM membership and one typed Singles album.
  [Cuetree catalogs](Cuetree/catalogs.md)

- Collapsing declared equal-ID FILF positions in direct Album/Title Play or Add. Preserve their
  source order; deduplicate physical FILM copies and Artist/Genre/Folder aggregates separately.
  [Queue policy](Riff/LATEST/player-queues.md), [Cuetree albums](Cuetree/catalogs.md#albm)

## Analysis and playback

- Creating a separate AI analysis database alongside ReplayGain. MDRG is the single optional
  analysis artifact with its own generation series.
  [Cuetree analysis integration](Riff/LATEST/cuetree-analysis-integration.md)
- Replacing content-derived public AI output with synthetic values, mixing synthetic and inferred
  records, or silently accepting a missing or corrupt model package.
  [AI package](Riff/LATEST/indexer-ai-package.md)
- Sending audiobook identities to AI inference or excluding them from ReplayGain analysis.
  [ReplayGain analysis](Riff/LATEST/replaygain-analysis.md),
  [Cuetree analysis integration](Riff/LATEST/cuetree-analysis-integration.md)
- Treating a FILF as having its container FILM's Track Gain or substituting whole-file measurements
  for the fragment span. [ReplayGain acquisition](Riff/LATEST/replaygain-acquisition.md),
  [Cuetree analysis integration](Riff/LATEST/cuetree-analysis-integration.md)
- Synthesizing Album Gain or peaks from measured Track Gain, averaging conflicting Album Gain
  values, or using stored peaks to drive the current processor.
  [Cuetree analysis integration](Riff/LATEST/cuetree-analysis-integration.md),
  [ReplayGain playback](Riff/LATEST/replaygain-playback.md)
- Promoting training-only Mood or raw teacher metrics into production Level 2 output, MDRG, Player
  features or selection. [Level 2 training sidecars](Riff/LATEST/level2-training-data.md)

## Configuration and platform

- Adding per-consumer configuration parsers, runtime file watching, hot reload or a Settings skin
  editor. Both processes use the validated JVM schema and one immutable startup snapshot.
  [Project configuration](Riff/LATEST/project-configuration.md)
- Inferring AAOS motion safety from the hardware model or relaxing host restrictions through
  configuration. Follow the explicit application-mode and host evidence rules.
  [Application modes](Riff/LATEST/application-modes.md),
  [Project configuration](Riff/LATEST/project-configuration.md)
- Downloading, training or converting a Level 2 model during an ordinary APK build, or loading the
  entire packaged model into a heap copy. The build consumes a closed package; runtime maps its
  validated descriptor read-only. [AI model](Riff/LATEST/indexer-ai-model.md),
  [AI package](Riff/LATEST/indexer-ai-package.md)

## Operable controls and contrast

- Using disabled contrast or semantics for an operable Off/Ban control is prohibited.
- A translucent selection fill alone cannot identify selected state.
- Blur alone cannot establish the adjacent contrast background.

Owner: [Local UI color system](Riff/LATEST/ui-color-system.md) and
[shared contrast halo](Riff/LATEST/ui-contrast-halo.md).
