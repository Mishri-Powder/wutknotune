# Riff architecture reading guide

This is an explanatory map, not a second specification. The
[requirements registry](/Riff/LATEST/requirements) and its scoped owners remain authoritative. Read
this page to understand the data flow, then follow the owner for the behavior you are changing.

## From a file to a playing occurrence

1. **Admit the selected application-data root.** It holds Player and Indexer state in separate owner
   subtrees. A missing selected volume causes waiting, before either process opens that data.
2. **Discover sources (L0).** Indexer enumerates granted media folders. A file location is a
   physical binding; it is not the identity of the recording.
3. **Acquire facts (L1).** Cuegrain parses changed sources; the Android adapter checks actual audio
   support and supplies identity. Unchanged successful observations reuse retained facts.
4. **Build catalogs.** Cuetree resolves explicit structures, copies and ordinary albums, expands
   chapters and classifies Singles. Album, Artist and Genre catalogs refer to the same factual
   cohort.
5. **Analyze optional features (L2).** Gain and Music AI share admitted sources. MDRG is
   independently published; its absence never makes ordinary playback or the library disappear.
6. **Publish a complete snapshot.** A reader accepts one validated immutable cohort. A failed
   candidate leaves the previous accepted cohort usable.
7. **Accept Play or Add.** Player freezes an ordered queue revision in its own store. Media3
   executes those occurrences. A rescan or reaction changes future choices, not that active
   revision.

Owners: [runtime](/Riff/LATEST/runtime-architecture), [stages](/Riff/LATEST/indexer-staging),
[publication](/Riff/LATEST/immutable-publication), [queues](/Riff/LATEST/player-queues).

## Terms that must stay distinct

| Term             | Meaning and consequence                                                                                                         |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Physical binding | One accessible file occurrence. Another copy can provide fallback audio.                                                        |
| MediaID          | Logical audio identity. An absent low component differs from a present zero.                                                    |
| Album position   | One declared position, with its own metadata. The same MediaID can legitimately occur twice.                                    |
| FILF             | A playable fragment with bounds and metadata, backed by a base file. The container does not become an extra album/Singles item. |
| Album            | A retained catalog entity with a stable ID and ordered membership. Its visible name is not its identity.                        |
| Queue occurrence | One position in a frozen playback revision. It survives later catalog changes and can repeat a MediaID.                         |
| Availability     | Whether retained evidence currently has playable access. It does not mean deletion.                                             |
| TTL expiry       | Removal after the owning clock reaches its boundary. Only then can missing factual positions stop protecting album composition. |

## Album grouping and Singles

Build-time [language evidence](/Riff/LATEST/catalog-language-evidence) supplies lexical markers;
GNRE supplies language-independent genre identities. The library has no fixed EN/RU fallback.

Filename numbering supplies conservative missing-ordinal evidence before ordinary grouping. It does
not write tags, invent Total Tracks or turn a folder into an album. Existing weighted grouping and
release guards then assemble ordinary candidates globally. Title bases classify versions within an
already assembled release; they do not fuzzy-match or merge different releases.

Explicit tables and chapter expansion retain priority. Books and chapterized groups are protected.
The Singles predicates and artist-qualification thresholds live only in
[Singles classification](/Cuetree/singles); this guide intentionally does not duplicate their
numbers. Every selected position is routed once, to a qualified artist basket or the global basket.

A two-track local album fragment can be Singles. A complete twelve-position album with ten
temporarily unavailable positions remains assembled until those factual sources expire. Newly added
tracks can promote a short release out of Singles. Publication-only expiry must perform the same
reclassification while retaining chapter ownership.

Player changes only the technical display names to `Artist ◇ Singles` and `◇ Singles`; stored IDs,
membership and comparison names retain their owners. A browse-root admission threshold is a separate
UI rule and does not prove that an album is absent from the catalog.

## Three different lifetimes

| State                        | Clock and owner                                                                                                                  |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Cuetree sources and catalogs | Publication generation, [catalog lifetime](/Cuetree/catalogs). A changed publication consumes TTL; an unchanged pass does not.   |
| Album/Title resume contexts  | Player store cycles, [saved contexts](/Riff/LATEST/player-contexts). Temporary contexts have a shorter lifetime.                 |
| Listening events             | Listening-history generations, [history](/Riff/LATEST/player-listening-history). Retention does not reset lifetime start counts. |

These clocks are not wall time and are not interchangeable. Bookmarks freeze a complete Title queue;
they are separate from automatic resume contexts.

## Failure boundaries

An access error is not evidence of corrupt SQLite bytes. Requesting cancellation is not evidence
that a worker has stopped. A storage move must wait for real owner quiescence, including publication
workers. A timed-out fence reports failure and keeps the current authority.

IPC acknowledgments describe accepted application effects. Session identity and cached replies stop
retries from duplicating those effects. They do not make commands durable across both processes
dying. Configuration is another separate boundary: each process reads one admitted startup snapshot;
UI changes select from it without rereading the user file.

## Reading routes

- Catalog changes: [Cuetree](/Cuetree/contract), [grouping](/Riff/LATEST/album-grouping),
  [Android integration](/Riff/LATEST/cuetree-integration).
- Playback/state changes: [queues](/Riff/LATEST/player-queues), [state](/Riff/LATEST/player-state),
  [chapters](/Riff/LATEST/player-chapters), [bookmarks](/Riff/LATEST/player-bookmarks),
  [audio focus](/Riff/LATEST/player-audio-focus).
- Storage/startup: [Player storage](/Riff/LATEST/player-storage),
  [process fences](/Riff/LATEST/storage-roots), [configuration](/Riff/LATEST/project-configuration).
- UI changes: [foundation](/Riff/LATEST/ui-foundation), affected screen owner and
  [application modes](/Riff/LATEST/application-modes). Local tests do not establish host
  conformance.

Verification evidence and unresolved acceptance belong in [the handoff](/Riff/LATEST/worklog) and
[open work](/Riff/LATEST/open-decisions), not inside the functional contracts.
