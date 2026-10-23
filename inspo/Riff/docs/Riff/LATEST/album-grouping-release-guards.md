# Riff Album grouping contract — AG-11 Release guards

> Owner/scope: RIFF-ALBUM-V2 competing releases and weak placeholder evidence.

## Competing releases

Apply these guards before artist and residual rules. They refine the existing requirement to retain
equally supported incompatible alternatives; a high score is not an exemption.

Credible different edition identities or explicit independent ownership remain boundaries. In
ordinary grouping, independently coherent overlapping tracklists are competing release candidates
when each has structural or credible edition support and multiple overlapping declared positions
contain unequal Media.ID. A source-folder partition can expose such evidence but cannot establish a
boundary solely because paths differ. Equivalent copies of the same positions are handled first
under [AG-10](/Riff/LATEST/album-grouping-positions).

Do not merge those competing tracklists using only equal Album, Artist/Album Artist, generic Year,
or a residual score. Their unequal Media.ID values establish alternatives, not proof that the audio
recordings themselves differ. Do not invent a remaster, recording identifier or edition year.
Unknown Disc must be checked for overlap with all credible known disc scopes; it is not an
additional virtual disc that removes collisions. Valid distinct disc-family scopes remain available
under AG-8.

Independent one-track releases are also protected: two candidates with their own consistent
single-track declaration, different nonempty recording titles and unequal Media.ID remain separate
when no explicit ownership or unambiguous multi-disc evidence unites them. Equal Album, Album Artist
and generic Year do not merge two separately titled concert recordings. Their single-track admission
remains subject to AG-3.

When evidence cannot determine whether occurrences are copies, variants or different editions, do
not manufacture a partition from traversal order. Preserve independently admitted groups and use the
existing ambiguity fallback for remaining occurrences. These are conservative grouping rules, not a
guarantee of perfect release identification.

## Placeholder evidence

Use an exact, narrow set after the shared canonical normalization:

| Field        | Neutral placeholder values |
| ------------ | -------------------------- |
| Album        | album                      |
| Track Artist | artist                     |
| Album Artist | artist; album artist       |

The values remain stored and displayed as factual tags; this is not parsing, repair or deletion. An
artist component set is wholly placeholder only when every component is in that field's set. Do not
remove individual components from mixed sets or reinterpret punctuation.

Placeholder equality contributes zero match score. Placeholder Album cannot by itself enable the
artist or same-title/year compilation heuristic; it still permits candidate inspection and
independently supported structural/edition grouping. A wholly placeholder artist set supplies no
substantive artist proof. Missing evidence likewise remains neutral.

Do not classify every short/common title as a placeholder: Mix, Greatest Hits and Live Album are not
in this set. Generic Various Artists-equivalent values retain the compilation semantics and separate
scoring already owned by AG-7/AG-9. No fuzzy artist repair, language guessing, new stored
discriminator or user setting is introduced.
