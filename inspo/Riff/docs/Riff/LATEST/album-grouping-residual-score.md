# Riff Album grouping contract — AG-9 Residual score

> Owner/scope: RIFF-ALBUM-V2 residual evidence; stronger positive rules do not need this threshold.

Candidates satisfy the common Album boundary or the scoped AG-8 disc-suffix extension.
[AG-11](/Riff/LATEST/album-grouping-release-guards) applies before scoring: release barriers are not
overridden by a threshold, and placeholder equality contributes no positive evidence. Only actually
exposed admissible observations contribute. Missing on either or both sides, invalid or unsupported
fields score zero; no absence penalty exists. Do not add new extraction, media I/O or external
lookup solely to fill this table.

| Evidence                                          | Equal / different or other effect                   |
| ------------------------------------------------- | --------------------------------------------------- |
| Album                                             | +40                                                 |
| Substantive Album Artist                          | +20 / -5, subject to the weak-rule separation guard |
| Generic Various Artists-equivalent set            | +3                                                  |
| Nonempty Track Artist component set               | +6 / 0                                              |
| Credible edition year                             | +12 / merge prohibited                              |
| Generic four-digit Year                           | +6 / -12                                            |
| Genre sets                                        | +2 times Jaccard overlap                            |
| Nonempty Comment                                  | +3 / -2                                             |
| Same physical folder or valid numeric family      | +25 once, mutually exclusive                        |
| Same-disc Total Tracks                            | +8 / -25                                            |
| Total Discs                                       | +5 / -20                                            |
| Distinct valid Track positions in an agreed range | +4                                                  |
| Distinct valid Disc positions in an agreed range  | +4                                                  |
| Position outside credible corresponding total     | -35                                                 |
| Same slot with proven different recordings        | -45                                                 |
| Unresolved slot collision                         | -10                                                 |
| Artwork                                           | +6 / -3                                             |
| Catalog-number-and-label pair                     | +15 / -25                                           |
| Explicit edition qualifier                        | +8 / -30                                            |
| Tagging/encoding-source profile                   | at most +3 / -3                                     |
| Codec, bitrate, sample rate                       | 0                                                   |

Supported credible edition-ID equality/difference takes precedence subject to whole-group
consistency. Recording IDs and release-group IDs are not edition IDs. Do not invent unavailable
recording proof, Comment, cover or edition evidence. Unequal MediaID alone is an unresolved
collision, not proof of different audio recordings; equal MediaID does not conflict with itself.

Positive numbering contribution is capped at +15; combined positive weak artwork/Comment/encoder
contribution is capped at +8. Do not count one contradiction twice or multiply evidence by member
count. Unknown Disc is not Disc 1; valid folder ordinals supply only the scoped transient evidence
owned by [AG-8](/Riff/LATEST/album-grouping-disc-folders).

Residual merge requires at least 65 for every cross-pair between merging groups (complete linkage).
A chain of individually positive edges does not suffice. Scores below 65 do not support residual
merging. Equally strong incompatible alternatives are unresolved, not chosen by scan/source order.

Strong known edition contradictions and established competing-release barriers remain prohibitions
whatever the score. Different substantive Album Artist retains the guard in
[AG-7](/Riff/LATEST/album-grouping-evidence-rules#artist-grouping). The score is a fallback and must
not reintroduce a folder prerequisite or override the accepted structural and unnumbered-compilation
rules. Unresolved residual singletons still require the separate
[admission rule](/Riff/LATEST/album-grouping-ag-1-ownership-and-inputs#ag-3-uncertainty-and-singleton-admission).
