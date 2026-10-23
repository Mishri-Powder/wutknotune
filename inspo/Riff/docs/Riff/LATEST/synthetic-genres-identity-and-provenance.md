# Riff synthetic genre ID contract — 1. Identity and provenance

> Owner/scope: bounded section of Riff/LATEST/synthetic-genres

## 1. Identity and provenance

- A positive genre ID is the numeric part of a Wikidata Q-ID. A negative genre ID is a Riff
  synthetic genre ID. Project-added assignments are controlled only by
  `config/project.json.genreAdditions.mtgEvidence`; this contract defines their semantics.
- Synthetic assignments are permanent, append-only, and MUST remain stable across builds, model
  versions, GNRE generations, MDRG generations, dataset refreshes, and source disappearance. An
  assigned ID MUST NOT be renumbered, recycled, or silently reinterpreted.
- IDs `-1` through `-99999` are reserved for MusicBrainz metadata genres that have no accepted
  Wikidata identity. IDs `-100000` through `-199999` are reserved for the MTG-Jamendo
  classifier-label namespace. Other negative ranges remain unassigned.
- Source provenance remains evidence identity in persisted data. An MTG classifier ID and a metadata
  genre ID MUST remain independently inspectable and MUST NOT overwrite each other's confidence or
  evidence.
- Player-visible identity is canonical GNRE identity. When an MTG classifier label and a metadata
  variation resolve through RIFF-TAG-V4 normalization and the unambiguous GNRE GenreIndex to the
  same GNRE node, Player MUST merge their Album memberships into one UI genre. Display-string
  equality alone is prohibited.
- The precomputed GNRE mapping, not Player text handling, decides equivalence. AI rock, ID3 rock,
  Rock, and рок are one Player-visible genre only when their normalized GNRE variations resolve to
  the same canonical node.

- The MTG labels below are the exact lowercase ASCII class tokens from the official artist-disjoint
  `autotagging_genre` split-0. The adopted allocation is the explicit ordered configuration starting
  at `-100000`; runtime sorting and independent code or documentation tables are prohibited.

## 2. FlatBuffer representation and lookup

- The logical evidence ID is signed. MDRG AnalysisGenre.genre_id retains the MTG synthetic ID as its
  negative signed value. GNRE Genre.id stores the unchanged two's-complement 64-bit pattern.
- Every MTG ID in the table MUST resolve to exactly one GNRE Genre evidence node with the listed
  English label. MTG evidence nodes MUST NOT contribute names or aliases to GenreIndex.
- Every MTG assignment in section 5 MUST be emitted as its own stable negative GNRE evidence node
  and MUST NOT contribute names or aliases to GenreIndex.
- The explicit override table in section 6 has precedence over automatic RIFF-TAG-V4 matching. A
  Q-ID override MUST produce exactly one direct SameAs relation to that exact positive GNRE node; a
  declared standalone assignment MUST produce none. The GNRE build MUST fail if an explicit positive
  target is absent.
- For every MTG label not listed in section 6, GNRE generation MUST apply the exact RIFF-TAG-V4
  canonicalization and XXH3 sequence, using only non-MTG GenreIndex candidates. One unambiguous
  target produces exactly one direct SameAs relation; absent or ambiguous lookup leaves the MTG node
  standalone.
- Indexer follows only this direct generated SameAs edge for an MTG evidence node. It MUST reject an
  invalid target index or multiple SameAs targets for that node's Genre projection, while preserving
  valid scalar MDRG families. Indexer MUST NOT normalize strings, calculate alias hashes, or infer
  similarity at runtime.
- Other GNRE graph relations do not imply UI equality. No parent, child, based-on, fusion, facet, or
  approximate label relation may merge genre rows.

## 3. MDRG publication and presentation

- When the adopted Genre ensemble accepts labels, Indexer MUST serialize one AnalysisGenre entry per
  accepted MTG label with its stable synthetic evidence ID and finite confidence in [0,1]. It MUST
  NOT replace the classifier evidence ID with a same-named metadata ID.
- MDRG Genre evidence and Cuetree metadata evidence remain independently addressable in storage,
  diagnostics, export, and rebuild. UI coalescing does not combine confidence values, increase
  evidence counts, or rewrite either artifact.
- Player presents the localized preferred name of the canonical visible GNRE node with no Unicode,
  punctuation, color, wording, or accessibility provenance tag.
- Indexer groups metadata and inferred Album memberships by canonical visible GNRE ID. One row is
  selectable and owns reactions for that identity. A same-name pair whose GNRE normalized variants
  resolve to different nodes remains separate; a differently spelled or localized pair resolving to
  one node is merged.
- Genre rows use the existing deterministic localized-name ordering with canonical visible GNRE ID
  as the final tie-breaker. Source provenance MUST NOT create a second sorting group.
- Failure to project one Genre ID through GNRE is local to that Genre evidence and MUST NOT
  invalidate valid PTE, arousal, or valence from the same MDRG, nor the complete MDRG A/V heatmap.
- [Level 2 model](indexer-ai-model.md) continues to define the Genre ensemble, thresholds,
  calibration, and TFLite output contract. This page remains authority for stable evidence IDs and
  UI equivalence; it does not approve MTG-Jamendo licensing.

- Player then applies the file-only player.excludedGenreIds projection filter described by
  [Media integrity and identity](indexer-media-admission.md). It matches either retained evidence ID
  or canonical visible GNRE ID and removes matching evidence only from every user-visible Genre
  projection; it never removes or rewrites MDRG or GNRE evidence. The packaged default contains
  -100000 through -100003, so the four inferred decade identities remain diagnostically available
  but are hidden from users by default. An empty valid list exposes all identities; no Settings
  surface exists.

## 4. Existing MusicBrainz-only assignments

| Signed ID | English display name        | Source/provenance                                 |
| --------: | --------------------------- | ------------------------------------------------- |
|        -1 | `classic blues`             | MusicBrainz genre without accepted Wikidata match |
|        -2 | `urban contemporary gospel` | MusicBrainz genre without accepted Wikidata match |
|        -3 | `dance`                     | MusicBrainz genre without accepted Wikidata match |

These are the three negative assignments present in the current bundled GNRE asset. Future
MusicBrainz-only assignments MUST use the next unused ID in the MusicBrainz range and be added to
this table before publication.
