# Albums and Singles: a formal FSM

A design for the next Riff change: assembling albums, collecting short releases into Singles and
preserving membership while files are temporarily unavailable. This is a formal model of proposed
behavior, not a feature already shipped. The new baskets and thresholds still need implementation.

## Model scope

Singles is a presentation category for the local library. It includes short releases, local album
fragments, bundles of versions of one or several works, and positively identified DJ sets. Every
decision does not require an online lookup of the official release type.

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
Recognizing `0 01 …` filenames adds evidence for `G` when a tag ordinal is missing; it is not a new
prerequisite for grouping. An incomplete filename sequence does not disable the existing score. If
no new Singles predicate is proven, an album assembled by the existing algorithm remains an album.
The change concerns classification and presentation of its result.

[AG-9: existing weights and guards](https://wiki.271.pics/Riff/LATEST/album-grouping-residual-score).

## Inputs and predicates

For generation `k`, apply ordinary retention and TTL first. Input `R(k)` contains all successfully
observed, still-retained facts, including unavailable ones. `N(g)` counts logical positions after
equivalent copies are resolved. It is not the number of accessible files.

| Symbol         | Exact meaning in this proposed model                                                                                                                                                                                         |
| -------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `explicit(g)`  | An admitted valid multi-track CUE/TOC or independently titled embedded table, including retained explicit ownership before normal expiry.                                                                                    |
| `chapters(g)`  | A base MEDIA has an admitted chapter table without its own album title. Competing tables follow the existing explicit path.                                                                                                  |
| `grouped(g)`   | Ordinary grouping independently admitted a group under existing rules. A folder name does not repair a missing Album tag.                                                                                                    |
| `protected(g)` | Positively identified speech/book material; these groups stay outside the new Singles heuristics. Explicit tables and chapters take earlier branches.                                                                        |
| `short(g)`     | `1 <= N(g) <= 3`, every duration known and `0 < duration < 600 s`. Incomplete numbering or a larger declared Total Tracks does not prevent admission.                                                                        |
| `versions(g)`  | `4 <= N(g) <= 16`; one consistent disc; positions `1..N`; every present Total Tracks equals `N`; coherent artist ownership; meaningful titles produce at most three exact bases with at least one recognized version marker. |
| `djset(g)`     | Positive evidence of a DJ set independent of the artist's name. Unknown means false; Live or a long duration alone is insufficient.                                                                                          |
| `candidate(g)` | `short(g) OR versions(g) OR djset(g)`, evaluated only after protective branches.                                                                                                                                             |
| `owner(g)`     | One unambiguous substantive artist credit shared by every position: Album Artist first, then Track Artist. Unknown/placeholder values cannot create an owner.                                                                |

Missing positive evidence makes its predicate false. Unknown duration blocks `short`, not an
independently proven `versions` result. Contradictions are not repaired by majority vote. Numbers in
titles retain their meaning.

`djset` explicitly marks unfinished design work: the research used reviewed annotations; a general
production recognizer has not been specified. Until it exists, recordings cannot all become sets
because their artist is a DJ. The FSM is deterministic for supplied predicate values; unconfirmed
cases follow the other rules.

## Classifier C

`C = (Q, Σ, D, δ, E, O)`, where `Q = {E, G, H, C, R, X, A, P, S, Z}`, `Σ = {evaluate, reset}`, and
`D` holds the facts and predicates above. `E` is the initial state. `O = {X, A, P, S, Z}` are
outcomes: explicit album, ordinary album, artist Singles, global Singles and no members. The state
`P` inside `C` means an artist basket, not the publication machine.

![FSM: explicit ownership, ordinary grouping, chapters, Singles classification and basket routing](https://roadio.link/assets/singles-fsm-en.f92113f847f6.svg)

[Open the diagram separately](https://roadio.link/assets/singles-fsm-en.f92113f847f6.svg). The
diagram is an overview; the table defines every transition. For each state, take the first matching
row. “Otherwise” is the complement of all preceding guards for that state.

| From        | Guard for `evaluate`                   | To   | Action                                                                                     |
| ----------- | -------------------------------------- | ---- | ------------------------------------------------------------------------------------------ |
| `E`         | No retained positions                  | `Z`  | Create no nonempty record; old empty projections follow their own TTL.                     |
| `E`         | `explicit(g)`                          | `X`  | Preserve declared FILF order; exclude the corresponding base MEDIA and their copies.       |
| `E`         | Otherwise                              | `G`  | Run existing global base-MEDIA grouping, including AG-9 and its weights.                   |
| `G`         | Chapters present                       | `H`  | Resolve base membership, then expand chapters.                                             |
| `G`         | `grouped(g)`, no chapters              | `C`  | Pass resolved logical positions.                                                           |
| `G`         | Otherwise                              | `R`  | Route each unclaimed position separately; do not invent releases from global Singles.      |
| `H`         | `grouped(g)`                           | `A`  | Replace containers with ordered FILF; retain other positions.                              |
| `H`         | Otherwise                              | `X`  | Make the table's explicit fallback album; route neither container nor chapters to Singles. |
| `C`         | `protected(g)`                         | `A`  | Preserve the ordinary album.                                                               |
| `C`         | `candidate(g)`                         | `R`  | Move the entire group, preserving original tags and internal order.                        |
| `C`         | Otherwise                              | `A`  | Preserve the ordinary album.                                                               |
| `R`         | Unique `owner(g)` satisfies `eligible` | `P`  | Add to `<Artist>: singles`.                                                                |
| `R`         | Otherwise                              | `S`  | Add to global `Singles`.                                                                   |
| `X/A/P/S/Z` | Any `evaluate` without new inputs      | Same | Keep the computed outcome.                                                                 |
| Any         | `reset` with new coherent input        | `E`  | Recompute membership from the new snapshot.                                                |

The last row takes precedence for `reset`. Other unspecified events are self-loops with no register
updates. The builder establishes explicit ownership across the whole snapshot first; physical copies
cannot send the same logical container down different branches. This machine decides membership; its
state is not a catalog record ID.

## Title bases and artist baskets

The base parser removes only recognized trailing version descriptors, such as `Song (Radio Edit)`,
`Song (Artist Remix)` or `Song - Instrumental`. Bases compare exactly using the existing canonical
comparison. No new normalization or Levenshtein distance is added. Short bases such as `Go` are
valid. Arbitrary parentheses, part/volume numbers or a shared concert suffix do not prove a common
work. Base extraction does not change factual Title tags or merge different albums. The allowed
suffix vocabulary must be closed and testable.

Before routing, compute one fixed `eligible(artist)` table for the whole snapshot:

- At least one independently assembled musical album outside Singles has seven or more logical
  positions, or four or more positions with a known total duration of at least 30 minutes.
  Contradictory structure cannot provide this evidence.
- **Or** the artist owns at least three independently identified `candidate` groups. Three tracks
  from one release count as one group. Unclaimed individual positions do not invent releases to
  reach the threshold.

These conditions are alternatives: a full or studio album is not required. Retained unavailable
groups count; a basket cannot qualify itself. Routing does not change `eligible` during traversal.
Complete credit components use existing comparison rules; commas or ampersands do not authorize
splitting a collaboration credit. Ambiguous ownership routes to `S`. Artist and global baskets never
receive duplicate copies of the same assigned positions.

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
failed attempt does not age the accepted catalog.

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

Filename ordinals are auxiliary evidence only for a complete unique `1..N` sequence among direct
audio children, without tag conflicts or replacing FILF structure. Recognize `01 …` and `0 01 …`
forms separately. This is observed numbering, not declared Total Tracks; retained missing members do
not vanish from historical membership.

Current owners: [Cuetree catalogs](https://wiki.271.pics/Cuetree/catalogs),
[grouping](https://wiki.271.pics/Riff/LATEST/album-grouping) and
[lifetime](https://wiki.271.pics/Riff/LATEST/indexer-library-lifecycle). Artist baskets, these
thresholds and new predicates are a proposed change. Reconcile their normative contracts before
code. This article changes no data format, application or existing user catalog.
