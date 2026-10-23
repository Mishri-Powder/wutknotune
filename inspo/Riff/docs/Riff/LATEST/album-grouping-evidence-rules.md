# Riff Album grouping contract — AG-7 Evidence rules

> Owner/scope: RIFF-ALBUM-V2 positive grouping rules and ambiguity.

## Structural grouping

Compatible numbering may group different artists across directories. A standalone structural merge
requires at least two distinct observed positions plus consistent declared structure: for example, a
common positive Total Tracks in one disc scope, or compatible Disc/Total Discs with track numbering.
Positions must fit credible bounds. Full coverage 1..N strengthens evidence but is not required;
partial albums remain albums. Bare distinct Track numbers without totals or another confirming
feature do not establish an arbitrary compilation.

Total Tracks belongs to its disc; different discs may declare different totals. With all Disc values
unknown, one anonymous numbering scope may be evaluated without storing Disc=1. With multiple known
discs, do not assign an unknown Disc arbitrarily. Album membership can still be unambiguous even
when a member's disc is unknown. [Folder evidence](/Riff/LATEST/album-grouping-disc-folders) can
supply a transient scope without changing source facts.

Unexplained slot collisions prevent treating structure as an unambiguous proof. Equal MediaID does
not conflict with itself; unequal MediaID alone does not prove different recordings. Do not mark a
file failed because grouping is ambiguous. Known different recordings and unresolved collisions have
separate [residual treatment](/Riff/LATEST/album-grouping-residual-score). Local conflict handling
follows [AG-10](/Riff/LATEST/album-grouping-positions#local-numbering-conflicts); a single collision
does not stop unrelated unambiguous grouping.

Explicit structure or unambiguous global numbering can override Artist/Album Artist differences in a
compilation. Equal title alone cannot. Contradictory optional totals invalidate that structural
proof, not otherwise independently established membership.

## Artist grouping

Equal nonempty substantive Album Artist plus equal Album independently supports grouping without
numbering or shared folders, subject to credible conflicts and competing edition alternatives.
Generic Various Artists-equivalent values indicate a compilation, not unique edition identity.
[AG-11](/Riff/LATEST/album-grouping-release-guards) defines competing-release barriers and neutral
placeholder values. These guards apply before artist or residual merging.

When substantive Album Artist is absent, equal nonempty Track Artist supports cross-folder grouping
without Year, Disc or totals. Different Track Artist does not itself split a compilation. Different
substantive Album Artist separates weak-rule candidates; only explicit ownership or unambiguous
structural evidence can cross that difference.

A subgroup based only on Track Artist or physical folder remains provisional. It MUST NOT prevent an
otherwise admissible global compilation from absorbing all its members.

## Unnumbered compilations

Tracks without usable numbering may form one compilation when Album is equal and nonempty, at least
two distinct tracks have the same valid Year, no member admitted by this rule has a different known
Year, substantive Album Artist is absent/generic or agrees, and no stronger evidence distinguishes
competing editions. Different Track Artist and physical folders are allowed. Genre, Comment and
artwork are not required.

Tracks missing Year may join only through the unique-assignment rule in
[AG-2](/Riff/LATEST/album-grouping-ag-1-ownership-and-inputs#ag-2-global-candidates-and-resolution).
They cannot bridge known different-year compilations. Numbered evidence still takes precedence when
it establishes or contradicts structure.

This is an explicitly accepted heuristic: different compilations with the same Album and Year and no
distinguishing observed metadata may merge. No claim of perfect release reconstruction is made. An
artist-only subgroup such as three tracks by one soundtrack contributor does not split an otherwise
admissible whole soundtrack.

## Unknown evidence and alternatives

Absence is neutral, not universal compatibility. Compare each assignment against all stronger
competing groups before committing it. Do not silently select an equally supported incompatible
partition. Retain unambiguous groups and leave genuinely ambiguous members for the existing Singles
fallback, subject to explicit chapter ownership. A final singleton requires the independently
specified admission rule; algorithmic leftovers do not imply album identity.
