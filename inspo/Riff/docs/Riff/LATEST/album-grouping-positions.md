# Riff Album grouping contract — AG-10 Copies and positions

> Owner/scope: RIFF-ALBUM-V2 physical-copy equivalence, logical positions and local conflicts.

## Copy and position ownership

Cuetree alone determines logical ALBM membership and positions before the Android projection. Retain
every physical binding in MDIA under normal source lifetime. A representative ALBM source does not
remove alternative bindings or add a persisted copy-group identifier.

Resolve equivalent physical occurrences before evaluating structural completeness, collisions and
residual evidence. Apply the existing Media.ID exact/SHRT identity rules; equality of a partial hash
alone is not copy proof. Copy equivalence additionally requires the same declared logical position
and compatible position metadata. Equal explicit positions in different independently owned tables
are not globally deduplicated.

For ordinary candidates, distinct known Disc/Track positions remain distinct even with equal
Media.ID. Known conflicting title or artist metadata is not silently replaced by a representative.
Unknown position information can attach to an equivalence class only if the assignment is unique
among all competing positions. Absence cannot bridge two declared positions. A fully unnumbered
equivalent occurrence requires equal canonical title/artist context and no competing position. Do
not turn transitive pairwise compatibility through missing fields into copy equivalence.

Equivalent copies of one position count once for numbering and completeness. Retain a factual
carrier of the class's declared position metadata; among equally informative carriers prefer
available sources and then stable source ordering. A copy with missing Disc, Track or optional
totals may join exactly one maximal compatible declared position. Competing positions retain the
ambiguous copy separately; complementary partial records without a declared carrier are not combined
by inventing tags. Keep all alternate physical bindings for playback. Genre or other non-position
metadata remains factual; copy collapse is not permission to rewrite tags. Each selected
representative enters the ordinary ordered ALBM vector once for that logical position. Equivalent
bindings of an admitted position are also claimed; they must not reappear as extra Singles positions
or singleton albums merely because only one binding represents the position.

Unequal Media.ID is not proof of different recordings and never authorizes deleting one occurrence
because title, artist or Track happens to match another. Retain unresolved encodings/variants as
distinct candidates until ordinary membership or ambiguity rules resolve them. Do not perform
additional audio reads or fingerprinting to make this decision.

Explicit CUE/TOC membership and order, untitled chapter expansion, and their repeated declared
positions retain their existing owner. Selecting an alternative physical binding never replaces a
declared position with a different one.

## Local numbering conflicts

A slot conflict invalidates that structural proof; it does not invalidate all other evidence or
abort grouping of the whole same-Album candidate. Identify conflicting positions and competing
release alternatives before finalizing membership. Continue evaluating unaffected candidates and
their coherent remainder under the strongest applicable rule. Do not stop all clustering because one
highest-score alternative is ambiguous.

For each conflicting occurrence, test membership against every admissible competing group. Exactly
one independently supported assignment may join that group even if its number conflicts; retain the
factual number and order the resulting tie deterministically. Multiple equally supported assignments
remain unresolved. A numbering error never creates an inferred replacement Track, Disc or total, or
a scan-failure flag.

If choosing which occurrence to exclude produces equally supported incompatible groups, do not
choose by traversal order, path order, Media.ID or a first-found folder. Retain membership common to
all supported alternatives where that remainder independently forms an admissible group. Unresolved
nonchapterized occurrences use the existing Singles fallback; a singleton album still needs the
independent AG-3 admission proof. No new unresolved/variant catalog kind is introduced.

Contradictory optional totals invalidate the affected structural proof, not otherwise independently
established membership. Do not repair a minority tag by majority vote.

## Consumers and publication

The Android factual-to-Player projection preserves Cuetree's admitted position multiplicity, order
and metadata. It may apply its owned container-availability and automatic-Book projection, but must
not independently deduplicate ALBM by Media.ID or rerun grouping.

Direct Album/Title Play and Add consume those declared ordered positions, including repeated
Media.ID where positions differ. Artist/Genre/recursive-Folder aggregate queue deduplication retains
the separate [queue contract](/Riff/LATEST/player-queues). Explicit playlist duplicates remain
intentional.

[Catalog lifetime](/Cuetree/catalogs) and immutable publication retain their owners. Cancellation or
validation failure leaves the prior accepted cohort intact. No new stored field or flag is required.
