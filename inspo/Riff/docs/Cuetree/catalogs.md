# Cuetree catalogs and lifetime

## MDIA and direct folder display

MDIA retains D/M/F and hidden metadata records, source attributes, parsed structural tables,
last-seen generation, one parent ID, and cached kind and availability flags. A CUE metadata record
retains its own stat and parsed table evidence. An unchanged CUE refreshes its derived facts without
reopening it; a missing CUE does not. Folders renders MDIA through a runtime parent-to-children
index.

Physical directory/media availability is reconciled top-down without probing descendants of an
unavailable directory. Returning directories permit individual checks. Fragments may use the first
available matching base copy as specified in [identity and access](/Cuetree/identity-access). ACSS
caches effective availability for rendering.

Generation is caller-supplied, nonnegative and strictly increases on every committed snapshot,
including checkpoints and analysis republications. Equal generations are rejected; each publication
consumes TTL. D/M update generation when available; F updates when its valid section is observed
with accessible base audio. Missing sources retain generation. Albums, artists, genres and
attributes update generation whenever retained MDIA facts refer to them, even when the referring
media is unavailable. Availability and reference liveness are independent: a referenced unavailable
album can have current generation and ACSS. Reads by the UI do not count as references or
observations.

One required project-wide TTL uses the same units. An unreferenced unavailable record expires when
`currentGeneration - generation >= TTL`. Repeated absence does not restart TTL. Source records use
their last available observation. Projection and attribute records may therefore survive source
deletion for their own remaining TTL. Orphan projections retain identity/presentation and have empty
member vectors; they cannot retain dangling references or keep each other alive. MDIA facts are the
liveness roots. Reappearance preserves identity only before removal.

Removing a physical parent removes dependent D/M descendants. A fragment may survive through another
equivalent base copy; unresolvable references must not be published. Rebuilding projections must not
change MDIA facts.

## ALBM

ALBM is a retained Player catalog with random stable Album IDs, ordered Media IDs, attributes, kind
(ordinary, explicit, global singles, artist singles), generation and availability/lifetime state.

A valid multi-track external CUE/TOC or independently titled embedded table defines Album membership
and order without a heuristic threshold. Invalid or one-track tables do not qualify. Independent
explicit tables do not concatenate. Resolve references through the provider; missing/unreadable
dependencies retain prior explicit evidence until normal expiry. A section title never names the
Album.

An embedded CUE/TOC/CHAP table without its own album/title defines an ordered chapter expansion of
its containing MEDIA, not an Album boundary. The MEDIA remains eligible for ordinary grouping even
when its audio is represented by FILF. Apply `AlbumGroupingPolicy` to base MEDIA first; expand each
member that has one unambiguous chapter table into its ordered FILF when building ALBM. Preserve
base-member order, then section order, including repeated FILF identities at declared positions.
Nonchapterized members remain unchanged. Equivalent physical copies of one logical base and
identical tables must not duplicate positions. Distinct or competing chapter-table variants remain
separate explicit candidates rather than being arbitrarily chosen. If no ordinary group claims a
chapterized base, its table forms one fallback explicit Album titled from the containing MEDIA
Album, then Title, then `[[UNTITLED]]`; do not also put that base or its fragments in Singles.

Cuegrain observations determine MDIA facts; those facts and grouping rules determine ALBM. Retained
ALBM preserves identity/presentation without refreshing MDIA.

During each validated table observation, admit ordered FILF facts independently of ALBM. Retain the
transient table-to-base/section relationship until catalog construction; never infer it from
flattened fragment IDs or source paths. Explicit and fallback candidates preserve their declared
positions, including equal audio IDs with different metadata. F records retain Fragment and
TotalFragments. Readable inputs recreate expansion; retained ALBM preserves temporarily missing
membership.

Explicit candidates converge when album title/attributes and ordered positions (MediaID plus each
position's exact attributes) coincide. Ignore source paths, random catalog IDs, availability and
generation in this equality. Repeated equal MediaIDs at different positions remain repeated
positions. Different metadata variants remain different albums. Reuse retained Album IDs for exact
matches; unmatched retained membership remains until ordinary source/reference TTL, then empty
orphan retention.

After explicit ownership, Cuetree passes remaining base MEDIA occurrences, including bases with
expandable embedded chapters, to an injected `AlbumGroupingPolicy`. Explicitly owned bases and FILF
are not eligible. It MUST return nonempty ordinary groups with nonblank titles and unique eligible
occurrence indices; an occurrence may belong to at most one group. Cuetree validates the result
before publication, applies [Singles classification](/Cuetree/singles) after ordinary grouping, and
retains control of Album IDs, lifetime, reconciliation and ARTS/GNRV derivation. A policy error
aborts the attempted snapshot; it cannot replace the previously committed cohort or alter MDIA
facts. Explicit albums never enter this policy.

The bundled `DefaultAlbumGroupingPolicy` follows [RIFF-ALBUM-V2](/Riff/LATEST/album-grouping):
global Album-tag candidates, structural and artist rules, accepted unnumbered compilations,
corroborated disc suffixes, copy/position resolution, release guards and explicit singleton
admission. Unresolved occurrences enter Singles; folder-local groups do not finalize before global
comparison. This imports grouping evidence only, not Android dependencies, Riff identities or
storage. Metadata canonical comparison uses NFKC, whitespace normalization and case folding;
parsing/repair remains Cuegrain-owned. Album display attributes are deterministic agreed factual
values, not the complete attribute map of the first track. Reconcile surviving groups to prior Album
IDs by shared source membership with deterministic one-to-one assignment; ambiguous splits/merges
are new admissions. Projection preserves admitted positions under
[AG-10](/Riff/LATEST/album-grouping-positions). Policy injection is constructor-scoped;
scripting/reload is excluded.

One stable global `[[SINGLES]]` and qualifying personal Singles baskets follow the separate
[Singles owner](/Cuetree/singles). These partition assigned positions without copies. Never publish
a chapterized base alongside its FILF in another album or Singles. Ownership and expansion use
logical base MediaID across physical copies, not only a selected occurrence.

## ARTS and genres

ARTS retains random stable Artist IDs and direct Album ID lists. Track Artist and Album Artist
components independently link the entire album. Artist comparison keys do not replace display
labels. No new Artist parser or repair implementation is introduced.

GNRE remains the external static genre dictionary. A caller-supplied resolver maps found text to
dictionary IDs; unrecognized text remains a typed text genre. GNRV contains only genres found in
retained media, with unique whole Album ID references whenever a member track has that genre. It
does not clone or filter albums.

An album is unavailable when all its members are unavailable; an artist when all its albums are.
Alternative accessible sources of the same Media ID count as available. Catalog generation follows
the MDIA-reference rule above, independently of ACSS. Offline retained metadata must not dissolve
album membership into Singles. Projections and MDIA belong to one coherent snapshot.

## Fragment ordering

Fragment is the one-based ordinal within its source CUE/TOC, stored as a numeric attribute;
TotalFragments is that validated table's audio-section count. Counts are not availability-derived.
Explicit observations supply ordered album candidates; expandable embedded observations supply
ordered sections for their grouped base. Preserve base order followed by section order in ALBM;
never use the ID-sorted storage order as chapter order. Begin/end are nullable microsecond fields;
zero is a present bound. Own section metadata overrides table metadata, which overrides the
referenced file metadata. Selecting an equivalent audio provider does not replace those attributes.

[Filename evidence](/Cuetree/filename-evidence) supplies transient ordinals before ordinary
grouping. Publication source expiry reclassifies affected retained catalogs under the same Singles
policy; availability alone never shrinks their logical membership.
