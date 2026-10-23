# Riff system requirements registry

This page only routes authority. It MUST NOT contain feature, wire-format, UI or indexing procedure
detail. Normative keywords retain RFC 2119 meanings. Change the owning child contract before
implementation; implementation status and evidence belong in TL;DR/worklog.

For a short explanation of the system and a reading route, start with the non-normative
[architecture reading guide](/Riff/LATEST/architecture-reading-guide).

## Primary ownership

- [Player requirements](/Riff/LATEST/player-requirements) — UI, playback, queues, profiles and
  Player-owned storage.
- [Indexer requirements](/Riff/LATEST/indexer-requirements) — admission, scanning, metadata,
  analysis and immutable publication.
- [Data formats](/Riff/LATEST/fbs-schema) — wire schemas and identifiers only; no procedures.

## Shared contracts

- [Documentation governance](/Riff/LATEST/documentation-governance)
- [Runtime architecture](/Riff/LATEST/runtime-architecture)
- [Distribution and licensing](/Riff/LATEST/distribution)
- [Project configuration](/Riff/LATEST/project-configuration)
- [UI tokens](/Riff/LATEST/ui-tokens)
- [Shared modal dialogs](/Riff/LATEST/ui-modal-dialogs)
- [Typography and colors](/Riff/LATEST/ui-typography-colors)
- [Local UI color system](/Riff/LATEST/ui-color-system)
- [Shared contrast halo](/Riff/LATEST/ui-contrast-halo)
- [Third-party dependency security](/Riff/LATEST/dependency-security)
- [Engineering verification](/Riff/LATEST/engineering-verification)

## External library contracts

- [Cuegrain](/Cuegrain) — independent metadata library contract and public API.
- [Cuetree](/Cuetree/contract) — independent library contract;
  [Riff integration](/Riff/LATEST/cuetree-integration) owns the Android boundary.
- [Cuegrain integration in Riff](/Riff/LATEST/cuegrain-integration) — application-side dependency
  boundary.

## Size rule

A normative page MUST be narrowly owned and target at most 1,200 counted words (about five minutes
at 240 words/minute). Registries contain links and scope only. Evidence, experiments, changelogs and
implementation status are separate and non-normative. If a contract exceeds the limit, split it by
ownership or responsibility; do not append another amendment.
