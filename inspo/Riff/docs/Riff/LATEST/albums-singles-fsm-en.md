# Albums and Singles: the implemented FSM

Riff 1.0.0 includes weighted album grouping, personal and global Singles, filename evidence, FILF
ownership and retention. The signed FINAL application and its matching source snapshot are published
together. Singles remains a catalog classification; it does not create a synthetic genre.

Player shows personal baskets as `Artist ◇ Singles` and the general basket as `◇ Singles`. These are
text labels in the existing rows. Catalog identities, membership, order and A-Z/search inputs remain
unchanged; this display update does not require resetting the library database.

## Model scope

Singles is a presentation category for the local library. It includes short releases, local album
fragments, bundles of versions of one or several works, and recordings selected by explicit DJ-set
evidence. Every decision does not require an online lookup of the official release type.

The model contains two extended finite state machines: classifier `C` and publication machine `P`.
Catalog data lives in registers, making this an EFSM rather than a state for every possible album.
Availability is orthogonal to classification.

Cuetree determines membership, Indexer publishes a coherent catalog, and Player reads it. Evaluation
starts again when coherent facts change, including after IA. Existing identity, competing-edition,
copy-resolution and position-ordering rules continue to apply.

**Existing weighted grouping is retained in full.** State `G` invokes existing global RIFF-ALBUM-V2
grouping: structural and artist rules, release barriers, the AG-9 weighted residual score and
complete-linkage checks. Singles predicates do not replace the threshold of 65 or AG-9 weights.
Grouping first assembles the full admissible membership; `C` then decides whether that finished
group is an album card or belongs in a basket. Short provisional subgroups cannot enter Singles
before global grouping finishes.

For an iTunes library, all existing tag, directory and numbering evidence remains available.
Recognizing `0 01 …` filenames adds evidence for `G` when a tag ordinal is missing or invalid; it is
not a new prerequisite for grouping. An incomplete filename sequence does not disable the existing
score. If no new Singles predicate is proven, an album assembled by the existing algorithm remains
an album. The change concerns classification and presentation of its result.

[AG-9: existing weights and guards](https://wiki.271.pics/Riff/LATEST/album-grouping-residual-score).

### Filename evidence runs before grouping

A complete successful listing must contain at least two direct audio names forming the unique
sequence `1..N`, for example `01 …` / `02 …` or iTunes `0 01 …` / `0 02 …`. Unsupported WMA and
failed audio candidates still count as physical siblings; this does not make them playable. Artwork,
text and playlist files are neutral. Nested folders, CUE/TOC, opaque unknown children, failed
observations or an incomplete listing cannot prove completeness.

The retained members must also fit the sequence, all usable Track tags must agree, present totals
must not conflict, substantive Album tags must not disagree, and chapter structures block this
inference. Retained missing members still count. New children awaiting metadata cannot prematurely
prove completeness. A derived ordinal supplies only missing/invalid ordering evidence: it is never
written into Track or Total Tracks, and a folder does not invent Album or Artist. When this proof
fails, the existing grouping still works with its other evidence.

## Inputs and predicates

For generation `k`, apply ordinary retention and TTL first. Input `R(k)` contains all successfully
observed, still-retained facts, including unavailable ones. `N(g)` counts logical positions after
equivalent copies are resolved. It is not the number of accessible files.

| Symbol         | Exact meaning in the implementation                                                                                                                                                                                                                                           |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `explicit(g)`  | An admitted valid multi-track CUE/TOC or independently titled embedded table, including retained explicit ownership before normal expiry.                                                                                                                                     |
| `chapters(g)`  | A base MEDIA has an admitted chapter table without its own album title. Competing tables follow the existing explicit path.                                                                                                                                                   |
| `grouped(g)`   | Ordinary grouping independently admitted a group under existing rules. A folder name does not repair a missing Album tag.                                                                                                                                                     |
| `protected(g)` | A recognized Book/speech group, M4B or a descendant of a standard Audiobooks root; protected ordinary groups bypass the new Singles predicates. Explicit structures and FILF take earlier branches.                                                                           |
| `short(g)`     | `1 <= N(g) <= 3`, every duration known and `0 < duration < 600 s`. Incomplete numbering or a larger declared Total Tracks does not prevent admission.                                                                                                                         |
| `versions(g)`  | `4 <= N(g) <= 16`; one consistent disc; unique positions `1..N`; every present Total Tracks equals `N`; common substantive artist credit; at most three meaningful exact title bases and at least one recognized version marker. Protected part-number signatures must agree. |
| `djset(g)`     | A common substantive artist credit and a DJ set, DJ mix, continuous mix or mixset marker in Album; alternatively every member has both an Electronic/electronica and a Live/live-recording genre component.                                                                   |
| `candidate(g)` | `short(g) OR versions(g) OR djset(g)`, evaluated only after protective branches.                                                                                                                                                                                              |
| `owner(g)`     | One unambiguous substantive artist credit shared by every position: Album Artist first, then Track Artist. Unknown/placeholder values cannot create an owner.                                                                                                                 |

Missing positive evidence makes its predicate false. Unknown duration blocks `short`, not an
independently proven `versions` result. Contradictions are not repaired by majority vote. Numbers in
titles retain their meaning.

Duration comes from the existing bounded Media3 source-admission probe. Missing duration does not
reject supported audio and blocks only `short`. `djset` is the implemented experimental metadata
heuristic, not a declaration of an official release type. Live alone, a long duration or an artist's
name does not satisfy it. No artist allowlist or runtime Internet lookup is used.

## Classifier C

`C = (Q, Σ, D, δ, E, O)`, where `Q = {E, G, H, C, R, X, A, P, S, Z}`, `Σ = {evaluate, reset}`, and
`D` holds the facts and predicates above. `E` is the initial state. `O = {X, A, P, S, Z}` are
outcomes: explicit album, ordinary album, artist Singles, global Singles and no members. The state
`P` inside `C` means an artist basket, not the publication machine.

![FSM: explicit ownership, ordinary grouping, chapters, Singles classification and basket routing](https://roadio.link/assets/singles-fsm-en.90fcc8034f77.svg)

[Open the diagram separately](https://roadio.link/assets/singles-fsm-en.90fcc8034f77.svg). The
diagram is an overview; the table defines every transition. For each state, take the first matching
row. “Otherwise” is the complement of all preceding guards for that state.

| From        | Guard for `evaluate`              | To   | Action                                                                                     |
| ----------- | --------------------------------- | ---- | ------------------------------------------------------------------------------------------ |
| `E`         | No retained positions             | `Z`  | Create no nonempty record; old empty projections follow their own TTL.                     |
| `E`         | `explicit(g)`                     | `X`  | Preserve declared FILF order; exclude the corresponding base MEDIA and their copies.       |
| `E`         | Otherwise                         | `G`  | Run existing global base-MEDIA grouping, including AG-9 and its weights.                   |
| `G`         | Chapters present                  | `H`  | Resolve base membership, then expand chapters.                                             |
| `G`         | `grouped(g)`, no chapters         | `C`  | Pass resolved logical positions.                                                           |
| `G`         | Otherwise                         | `R`  | Route each unclaimed position separately; do not invent releases from global Singles.      |
| `H`         | `grouped(g)`                      | `A`  | Replace containers with ordered FILF; retain other positions.                              |
| `H`         | Otherwise                         | `X`  | Make the table's explicit fallback album; route neither container nor chapters to Singles. |
| `C`         | `protected(g)`                    | `A`  | Preserve the ordinary album.                                                               |
| `C`         | `candidate(g)`                    | `R`  | Move the entire group, preserving original tags and internal order.                        |
| `C`         | Otherwise                         | `A`  | Preserve the ordinary album.                                                               |
| `R`         | `destination(g)` is unique        | `P`  | Add to the artist basket, shown as `<Artist> ◇ Singles`.                                   |
| `R`         | Otherwise                         | `S`  | Add to the global basket, shown as `◇ Singles`.                                            |
| `X/A/P/S/Z` | Any `evaluate` without new inputs | Same | Keep the computed outcome.                                                                 |
| Any         | `reset` with new coherent input   | `E`  | Recompute membership from the new snapshot.                                                |

The last row takes precedence for `reset`. Other unspecified events are self-loops with no register
updates. The builder establishes explicit ownership across the whole snapshot first; physical copies
cannot send the same logical container down different branches. This machine decides membership; its
state is not a catalog record ID.

## Title bases and artist baskets

The base parser removes only recognized trailing version descriptors, such as `Song (Radio Edit)`,
`Song (Artist Remix)` or `Song - Instrumental`. Bases compare exactly using the existing canonical
comparison. No new normalization or Levenshtein distance is added. Short bases such as `Go` are
valid. Arbitrary parentheses, part/volume numbers or a shared concert suffix do not prove a common
work. Base extraction does not change factual Title tags or merge different albums. Recognized
whole-word markers come from the build configuration; English examples include mix, remix, edit and
instrumental. Generic Track/Untitled/Audio titles provide no version proof. A dash suffix is taken
from the last separator: `Dream - Radio Day - Club Mix` retains the base `Dream - Radio Day`.

Language evidence is selected at build time from `genreLocales` and `catalogVocabulary` in
`config/project.json`. This edition selects English, Spanish, German, Russian and Portuguese.
Cuetree receives those values from its host; its default vocabulary supplies no language evidence.
Spoken, live and electronic roles use configured GNRE identities, not a second hardcoded genre-name
list. Ambiguous genre aliases remain neutral. The same vocabulary applies during scanning and TTL
publication. Changing it invalidates derived metadata through the normal indexing lifecycle.

Before routing, compute one fixed `eligible(artist)` table for the whole snapshot:

- At least one independently assembled musical album outside Singles has seven or more logical
  positions, or four or more positions with a known total duration of at least 30 minutes. The
  anchor must have complete coherent numbering, consistent disc coverage and matching present
  totals; placeholder Album names or contradictory structure cannot qualify it.
- **Or** the artist owns at least three independently identified `candidate` groups. Three tracks
  from one release count as one group. Unclaimed individual positions do not invent releases to
  reach the threshold.

These conditions are alternatives: a full or studio album is not required. Retained unavailable
groups count; a basket cannot qualify itself. Routing does not change `eligible` during traversal.
Complete credit components use existing comparison rules; commas or ampersands do not authorize
splitting a collaboration credit. Ambiguous ownership routes to `S`. Artist and global baskets never
receive duplicate copies of the same assigned positions.

### Deterministic routing and stable identities

Let `anchors` be the owners qualified by an independent complete album, and `eligible` include those
owners plus owners of three candidate releases. The common witnesses are whole Album Artist or Track
Artist component sets present on every member. The effective owner prefers common Album Artist, then
common Track Artist. Check these guards in order:

1. Use the effective owner if it is in `anchors`.
2. Otherwise use a unique common anchor. Multiple common anchors go to global Singles.
3. Otherwise use an eligible effective owner.
4. Otherwise use a unique common eligible credit; if none is unique, use global Singles.

This determines `destination(g)` before moving positions. Existing unclaimed positions are routed
individually and do not count as invented release groups; recognized loose book sources cannot
create a personal music basket. A selected candidate group moves intact.

Personal kind `ARTIST_SINGLES` uses `<Artist>: singles` and synthetic Album Artist components.
Global kind `SINGLES` retains `[[SINGLES]]`. Each basket keeps its own stable random ID; MDIA tags
stay factual. Personal empty baskets become unavailable orphans and expire normally. The empty
global basket alone stays live. An ordinary album promoted out of Singles receives an ordinary album
identity, never the basket ID.

The names above are stored ALBM titles. Player chooses display text from `AlbumKind`, joining the
complete artist credit with a semicolon and a space before the diamond. It also uses these display
labels in album-context track/queue metadata. Real ordinary or explicit albums with similar literal
titles are unchanged. Existing undecorated titles still supply sorting ties and A-Z keys.

## Publication and lifetime

Publication machine: `Qp = {IDLE, BUILD, VALIDATE, COMMIT}`, initially `IDLE`. Register `accepted`
points to the last accepted coherent catalog; `pending` is isolated. Events not admitted in the
current state do not change `accepted`.

| From                    | Event / guard                           | To         | Action                                                                    |
| ----------------------- | --------------------------------------- | ---------- | ------------------------------------------------------------------------- |
| `IDLE`                  | Coherent inputs changed                 | `BUILD`    | Apply TTL; compute `C` for all groups, then baskets and related catalogs. |
| `IDLE`                  | Inputs unchanged                        | `IDLE`     | No publication and no TTL consumption.                                    |
| `BUILD`                 | Computation complete                    | `VALIDATE` | Check membership, references, order, IDs and coherence of the full set.   |
| `VALIDATE`              | Checks pass                             | `COMMIT`   | Prepare activation of the new set.                                        |
| `COMMIT`                | Atomic activation succeeds              | `IDLE`     | Replace `accepted` and commit the new generation.                         |
| `BUILD/VALIDATE/COMMIT` | Error or cancellation before activation | `IDLE`     | Discard `pending`; preserve `accepted`.                                   |

TTL uses successful publication generations, not minutes or scan counts. Changed intermediate
publications also consume TTL. Generation advancement and fact expiry commit with the snapshot; a
failed attempt does not age the accepted catalog. If publication-generation expiry removes source
facts, the surviving membership is classified again, including on an analysis-triggered publication
without another scan. Current chapter expansions survive unrelated source expiry.

Availability machine `V = {AVAILABLE, OFFLINE}` moves to `AVAILABLE` on each refresh if at least one
retained position is accessible, otherwise to `OFFLINE`. It never changes the outcome of `C`. Album
availability is separate: at least one position is accessible, or none is. An entirely unavailable
retained record is absent from Player's available catalog, but its membership still participates in
classification and artist qualification until retention expires.

## Verifiable scenarios and invariants

| Event                                                             | Expected outcome                                                              |
| ----------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| Two short positions out of a declared twelve are first discovered | Singles, even with positions 4/12 and 9/12.                                   |
| The other ten distinct works are added                            | The entire group becomes its own album if no other Singles predicate applies. |
| Only two of twelve known positions remain accessible              | Keep the album until the missing positions reach TTL.                         |
| Ten positions actually expire                                     | Recompute the remainder; it may become Singles.                               |
| All twelve are inaccessible before TTL                            | Retain membership; expose no available card.                                  |
| One container has ten valid chapters                              | ALBM contains FILF; file count does not make it a one-track single.           |
| An explicit table has two fragments                               | Keep its explicit album; the short-group rule does not apply.                 |
| An artist has only three confirmed sets                           | An artist basket is possible without a studio album.                          |
| Building a new catalog is cancelled                               | Keep the previous accepted coherent catalog.                                  |

Each ordinary assigned position has one outcome `A/P/S`; explicit tables retain declared repetitions
and independent variants. Global Media.ID deduplication is forbidden. A container never appears
alongside its own FILF in albums or Singles. Availability alone never shrinks retained membership. A
basket ID never becomes the ID of an album promoted out of that basket.

Implementation owners: [Singles classification](https://wiki.271.pics/Cuetree/singles),
[filename evidence](https://wiki.271.pics/Cuetree/filename-evidence),
[Cuetree catalogs](https://wiki.271.pics/Cuetree/catalogs),
[grouping](https://wiki.271.pics/Riff/LATEST/album-grouping) and
[lifetime](https://wiki.271.pics/Riff/LATEST/indexer-library-lifecycle). This article explains the
implemented mechanism; the linked scoped contracts remain authoritative.

The original Singles validation passed 147 core and six CLI cases, nine isolated Android admission
cases, Android/JVM build checks and an independent source-only build. These cover membership,
version bases, filename ordering, promotion, TTL, FILF and failure/cancellation boundaries. They do
not imply that all device-library analysis or live automotive-host verification has completed.

The 2026-10-18 audit added language, filename and failure-boundary regressions. Its 661 JVM cases,
15 Python cases and 27 isolated HU storage/recovery cases passed before release preparation. These
are dated verification results, not a promise that all libraries or devices have been tested.
