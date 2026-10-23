# Riff Album grouping contract — AG-1 Ownership and inputs

> Owner/scope: RIFF-ALBUM-V2 ownership, global resolution and ordinary publication.

## AG-1 Ownership and inputs

Cuetree DefaultAlbumGroupingPolicy groups coherent factual MDIA base-media occurrences. Indexer
publishes catalogs; Player never groups them. Grouping uses acquired repaired metadata without
opening audio, querying SAF, reading artwork or running audio analysis. New FAILEDL1 placeholders
provide no successful grouping evidence. Previously successful retained facts keep their ordinary
unavailable-reference lifetime under [persistence](/Cuetree/persistence); FAILEDL2 does not
invalidate successful L1 facts. Profiles do not change ordinary grouping; static Book evidence
protects the later Singles classification.

Use shared canonical comparison: NFKC, whitespace normalization and case folding. Do not remove
edition qualifiers, soundtrack labels or volume numbers, and do not use fuzzy title matching.
Artist/Album Artist equality compares complete canonical component sets independently of order;
overlap is not equality. Cuegrain owns parsing and repair. Grouping does not rewrite factual tags.

Explicit ownership follows [Cuetree catalogs](/Cuetree/catalogs#albm). Valid multi-track external
CUE/TOC and independently titled embedded tables define membership and order without Album tags or
heuristic thresholds. Invalid or one-track tables do not qualify. Untitled embedded chapters expand
after ordinary base grouping; an unclaimed chapterized base retains the explicit fallback.
Heuristics never regroup explicit albums or discard their repeated positions.

## AG-2 Global candidates and resolution

After explicit ownership, consider all occurrences with the same nonempty canonical Album together,
across folders and selected roots. The only cross-title extension is the corroborated terminal disc
suffix rule in [AG-8](/Riff/LATEST/album-grouping-disc-folders#album-disc-suffixes). Equal
comparison keys are candidate boundaries, not unconditional identity. Different paths, volumes,
codecs, bitrates and sample rates do not themselves separate albums. Without Album, ordinary
grouping is unavailable; unclaimed nonchapterized media follow [Singles routing](/Cuetree/singles).
Never infer Album from a directory name.

Evaluate the whole candidate before finalizing ordinary groups. Folder-local and single-artist
subgroups remain provisional and can be extended or absorbed by stronger global evidence. No early
folder finalization removes members from later global comparison.

Evidence priority is explicit structure, supported credible edition identity, unambiguous numbering
structure, substantive Album Artist, the unnumbered-compilation rule, Track Artist, then residual
score including folder evidence. [Evidence rules](/Riff/LATEST/album-grouping-evidence-rules) and
[disc families](/Riff/LATEST/album-grouping-disc-folders) define applicability. Weak evidence cannot
override credible strong incompatibility. Equally strong incompatible alternatives remain
unresolved; scan order, source ID ordering or first-found directory MUST NOT decide membership.

An incomplete-metadata track can join a group when its observed evidence supports exactly one
assignment under the strongest applicable rule. Mere absence of contradiction does not select
between competing groups. Unknown fields MUST NOT bridge groups separated by known contradictions.
Residual merging uses complete linkage, not transitive connectivity of pairwise-positive edges.

Recompute for each changed coherent snapshot; no ordinary membership is permanently frozen across
snapshots. L0 unvisited prior facts, publication cadence, availability and TTL retain their owners.
An offline retained source does not by itself dissolve its album. Failed/cancelled grouping leaves
the previously committed cohort intact.

## AG-3 Uncertainty and singleton admission

Missing, invalid and unsupported fields contribute neither a match nor a missing-value penalty.
Ordinary four-digit Year is not a credible edition-year field. Its disagreement is weak evidence
except where a particular positive rule requires a common Year. Strong credible edition conflicts
cannot be bridged by absence. Never invent unsupported edition, cover or recording-proof evidence.

Do not turn every unresolved track into a separate ordinary album. A one-track ordinary album is
permitted when it is the only logical track in the Album candidate, or has an independent
unambiguous basis: substantive Album Artist, credible edition identity, or a consistent declared
single-track album structure. It must not remain an ambiguous candidate for another group. Different
Track Artist or directory alone does not establish independent singleton albums.

Other unresolved nonchapterized occurrences follow [Singles routing](/Cuetree/singles), retaining
factual Album tags. Introduce no failure flag or unresolved bucket. The explicit chapter fallback
retains precedence over Singles.

## AG-4 Published identity, attributes and ordering

[Cuetree catalogs](/Cuetree/catalogs#albm) owns retained random IDs, occurrence membership,
one-to-one identity reconciliation, explicit convergence and lifetime. Ambiguous splits/merges are
new admissions rather than guessed reuse. [AG-10](/Riff/LATEST/album-grouping-positions) owns
equivalent physical copies, logical positions and local conflicts. Global MediaID deduplication is
not allowed.

Ordinary display titles come from repaired Album text with deterministic representative spelling,
except the corroborated common-base display title explicitly permitted by AG-8. Do not copy all
album attributes from the first enumerated track. Agreed factual values may describe the album;
conflicting values remain track-local. Do not promote one compilation member's Track Artist to Album
Artist or synthesize Various Artists. File counts never create declared totals.

Order ordinary members by known Disc (including valid transient folder position), then known Track;
unknown values follow known ones. Resolve remaining ties through stable text/source identity, never
enumeration order. Chapter expansion preserves base order then declared section order. ARTS/GNRV
link whole albums; they do not clone an album per artist or genre. Root admission remains owned by
[Player catalog membership](/Riff/LATEST/player-catalog-membership).

V2 requires the exact current [Cuetree format](/Cuetree/persistence#mvp-format), with a full format
version bump and automatic
[index recreation](/Riff/LATEST/indexer-library-lifecycle#automatic-library-recreation). Do not use
a metadata-only refresh, partial catalog rebuild, old-data conversion or compatibility reader. New
synthetic kinds and scoped evidence are owned by Cuetree; no backend discriminator is introduced.

The unchanged weighted grouping precedes [Singles classification](/Cuetree/singles).
[Filename ordinals](/Cuetree/filename-evidence) are transient evidence, not factual tag repair.
