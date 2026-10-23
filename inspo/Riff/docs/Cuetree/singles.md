# Cuetree Singles classification

> Owner/scope: post-group classification and synthetic Singles catalogs; ordinary grouping keeps
> RIFF-ALBUM-V2.

## Order and protected ownership

Run the existing global ordinary grouping, including every AG-9 weight, threshold 65,
complete-linkage check and release guard, before Singles classification. Never finalize short
provisional artist or folder groups early. Explicit CUE/TOC/titled tables, untitled chapter
expansion and fallback keep precedence. A group containing FILF or chapter tables is protected;
neither its containers nor its fragments enter Singles. Recognized Book/speech sources, M4B and
`RootSpec.book` descendants are protected. Profile overrides do not rerun grouping.

Classify retained logical positions after copy resolution, including unavailable facts until source
TTL. Do not count only currently playable files or infer a declared total from observed membership.

## Candidate predicates

An ordinary unprotected group enters Singles when any predicate holds:

- Short local group: one to three positions, each with known positive `cuetree.durationUs` strictly
  below 600,000,000 microseconds. Missing/nonconsecutive numbers or a larger declared Total Tracks
  do not veto it. This intentionally includes local fragments of albums.
- Versions: four to sixteen positions, one noncontradictory disc, complete unique ordinals 1..N,
  every present Total Tracks equal to N, common substantive whole artist credit, at most three exact
  meaningful title bases and at least one recognized trailing version descriptor.
- Set recording: common substantive artist credit and an explicit standalone DJ set, DJ mix,
  continuous mix or mixset marker in the group Album; alternatively every member has both an
  Electronic/electronica genre component and a Live/live recording genre component. This is an
  accepted metadata heuristic for the experimental release, not proof of an official release type.
  Live alone, duration alone and an artist's name are insufficient. No artist allowlist or runtime
  online lookup is used.

Duration is positive observed source evidence, not a tag guess; unknown duration blocks the short
predicate only. Books and explicit ownership win before every predicate.

Title-base extraction uses existing canonical comparison and exact equality, with no Levenshtein,
fuzzy artist repair or new Unicode normalization. Strip only terminal bracketed or dash-separated
descriptors containing a whole-word mix/remix/edit/version/instrumental/dub/acoustic/extended
marker. Preserve arbitrary parentheses, edition labels and protected
part/volume/chapter/disc/movement/act numbers. Empty and generic Track/Untitled/Audio titles supply
no version proof. Short bases such as Go are allowed. Different part-number signatures cannot become
one family. Do not alter factual Title/Album or use bases to merge different ordinary release
candidates.

## Basket qualification and routing

A complete canonical artist component set qualifies when it owns an independently assembled,
non-Singles musical album with at least seven positions, or at least four positions and a known sum
of at least thirty minutes, with coherent complete numbering; alternatively it owns at least three
independently admitted candidate release groups. A studio/full album is not mandatory.
Unknown/generic artist credits and placeholder albums provide no anchor. Unclaimed positions do not
invent releases to meet the threshold. Fully offline retained anchors count until normal TTL.

Calculate qualification for the whole snapshot before routing. Prefer common Album Artist, then
common Track Artist; compare complete component sets without splitting literal comma/ampersand
credits. A unique qualifying common whole credit may supply the destination; equally supported
alternatives remain global. Every owner must have an actual common credit witness on every member.
Move selected groups intact, preserving internal order and factual metadata. Route existing
unclaimed positions individually. Do not duplicate assigned positions across baskets.

Exactly one global kind SINGLES, title `[[SINGLES]]`, has a stable random ID. Each qualifying owner
with members has one kind ARTIST_SINGLES, title `<Artist>: singles`, synthetic ALBM Album Artist
components carrying that owner's display credit, and a stable random ID keyed by its whole canonical
owner set. This presentation does not rewrite MDIA tags. Basket order is deterministic across
unchanged inputs; preserve each selected release's internal order. ARTS/GNRV link whole published
albums under existing rules.

## Identity, lifetime and verification

Basket IDs never reconcile with ordinary Album IDs. A personal basket with no members is an
unavailable orphan subject to normal projection TTL; only the global empty Singles remains live.
Added tracks can promote a group to an ordinary album. Temporary unavailability retains composition
and qualification; source expiry permits reclassification. Recompute after ordinary scan grouping
and whenever publication-generation expiry removes factual sources, including analysis-triggered
publications. Failures/cancellation retain the preceding coherent cohort.

Verify fragments, versions, generic/ambiguous credits, global fallback, ordinary promotion,
fully/partly offline retention, source and orphan TTL, stable basket IDs, physical copies, FILF
protection, deterministic ordering, cancellation and binary/Player projection round trips.

## Configured language evidence

The caller supplies one immutable `CatalogVocabulary` selected from its build configuration, for
both `Indexer` scans and `advancePublication` reclassification. The independent library has no
built-in language inventory; the empty default supplies no lexical evidence. Language-neutral
structural rules, duration rules and explicit Book/FILF protection still apply. Test vocabularies
are fixtures, never production defaults.

Generic credits, placeholder albums/titles, version words, protected numbered-work words and set
markers come from that vocabulary. Genre roles use supplied dictionary IDs and the existing
`GenreResolver`, not translated strings embedded in Kotlin. Riff builds this input from its
[language configuration](/Riff/LATEST/catalog-language-evidence). Preserve source spelling and
attributes. Structural snapshot validation does not interpret a host's vocabulary.
