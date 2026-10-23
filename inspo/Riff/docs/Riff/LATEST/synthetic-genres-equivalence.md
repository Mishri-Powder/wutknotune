# Riff synthetic genre equivalence and verification

> Owner/scope: delegated by
> [synthetic-genres-mtg-jamendo-classifier-assignments](synthetic-genres-mtg-jamendo-classifier-assignments.md).

## 6. Explicit MTG-to-GNRE overrides

The optional `sameAs` member of each configured MTG evidence row is the only explicit-override
source and has precedence over label lookup. The four decade rows `-100000` through `-100003`
contain explicit `null`, remain standalone negative identities and are excluded from packaged
user-visible Genre surfaces by default. Every configured Q-ID target MUST produce exactly one direct
SameAs edge to that positive node. A row without `sameAs` uses the unambiguous automatic
RIFF-TAG-V4/GenreIndex mapping defined by the identity contract.

No code constant or documentation table may duplicate these assignments.

## 7. Verification

Verification MUST prove all current negative assignments, signed/unsigned bit equivalence,
keyed-vector ordering, direct evidence-node lookup, MTG exclusion from GenreIndex, RIFF-TAG-V4
build-time mapping, unique generated SameAs relations, absence/ambiguity behavior, retention of
MusicBrainz metadata lookup, independent persisted evidence/confidence, canonical UI coalescing
across aliases/locales, no U+2727 presentation, one merged selection/reaction identity, localized
deterministic ordering, isolation of Genre projection failure from A/V/PTE and AI_SCREEN readiness,
and the file-only exclusion boundary: packaged four-decade omission, empty-list exposure,
raw-evidence/canonical-ID matching, retained MDRG/GNRE evidence, complete invalid-override
rejection, and no Settings surface.
