# Riff synthetic genre ID contract — 5. MTG-Jamendo classifier assignments

> Owner/scope: bounded section of Riff/LATEST/synthetic-genres

## 5. MTG-Jamendo classifier assignments

The complete current assignment is `config/project.json.genreAdditions.mtgEvidence`; it is the only
authoritative assignment list and is not duplicated here. It contains the 87 exact lowercase ASCII
class tokens from the official artist-disjoint `autotagging_genre` split-0, with permanent IDs
`-100000` through `-100086` in their adopted order.

Every future class MUST be appended with the next unused descending MTG ID. Existing rows MUST NOT
be reordered, renumbered, removed, recycled or silently reinterpreted. Generator validation MUST
reject a duplicate ID or label, a gap/reorder in the adopted sequence, or an invalid explicit
`sameAs` target.

See [Exact GNRE equivalence and verification](synthetic-genres-equivalence.md) for mapping and
verification rules.
