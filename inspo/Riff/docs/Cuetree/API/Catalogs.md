# Cuetree API — catalog model

## Snapshot

`Snapshot(generation, identityScheme, nodes, albums, artists, genres, diagnostics, attributeCatalog, metadataRevision = null)`
contains one coherent publication. Lists after nodes default to empty. `Snapshot.empty(scheme)`
creates generation zero. Source indexes identify occurrences within one snapshot; never persist them
in a playlist. Scanned/decoded record tables are sorted by the common ID comparator. Source
occurrence indexes are remapped; parent IDs are unchanged; album track order retains its semantic
meaning.

| Member                | Meaning                                                                     |
| --------------------- | --------------------------------------------------------------------------- |
| children              | Lazy runtime parent-to-child index                                          |
| parentOf(index)       | Directory or first available matching base occurrence; null for roots       |
| childrenOf(index)     | Direct children; -1 selects roots                                           |
| sources(MediaId)      | Exact/SHRT lookup returning all matching occurrences                        |
| baseSources(fragment) | Matching MEDIA occurrences for an F parentId                                |
| available(MediaId)    | Whether any matching occurrence is available                                |
| attributeCatalog      | Sorted retained typed values; see [attributes API](/Cuetree/API/Attributes) |

`Attributes` is the convenient JVM `Map<String, List<String>>` view. Repeated values survive round
trips; on disk these maps become dictionary ID references. Exact stored text remains separate from
canonical grouping keys.

`Snapshot.advancePublication(generation, ttl, grouping = DefaultAlbumGroupingPolicy, vocabulary = CatalogVocabulary.EMPTY)`
advances a retained snapshot without source I/O. When sources actually expire, it rebuilds catalog
membership and Singles classification with the supplied policy/vocabulary, reusing retained chapter
expansions. Temporary unavailability alone preserves membership. The supplied generation must be
strictly greater and TTL nonnegative. It expires unavailable source records and dependent hierarchy
entries, preserves SHRT only on equivalent survivors, remaps retained ordered album occurrences,
ages orphan catalogs and attributes, and validates the result. It never refreshes source observation
marks. Hosts use it for changed projection-only publications; unchanged projections do not call it
or create a generation.

## Node, Life and Flags

`Node(kind, id, parentId = null, source = null, span = null, attributes = emptyMap(), life, identityFlags = ...)`
stores one common ID and one optional parent ID. DIRECTORY receives a random high-only ID; MEDIA and
FRAGMENT receive computed IDs. FAILEDL1 uses a technical source ID or retains its prior known media
identity as unavailable evidence. A root directory has no parent. Other D/M records reference a
directory; F references a base MEDIA identity. Fragment nesting is rejected. There is no stored
parent index, baseId, root object, origin or children vector.

`SourceInfo(name, modified: Long? = null, size: Long? = null, provider: String? = null, locator: String? = null)`
groups physical D/M/METADATA/FAILEDL1 observations. Size must be nonnegative when present. Only a
root source contains provider/locator; its name is the display label. A root may also carry the
typed `sourceRootIdentity` attribute supplied by RootSpec; it is separate from Media.ID and
SourceInfo.locator. `stamp` returns a `Stamp` only when both modified and size are present. F has no
SourceInfo: its own metadata and nullable Span describe the section. `Node.name` is a computed
convenience using physical name, then title, then Fragment ordinal. `folderId`, `mediaId`, `isRoot`
and `flags` are computed views. Fragment/TotalFragments attributes preserve source-table positions.

`Life(generation: Long, flags: Int = 0)` accepts nonnegative generation and ACSS only; `available`
is computed from ACSS. D/M generation records effective source availability; F generation requires a
current valid section observation and accessible base audio. Catalog/attribute generation records
retained factual references, even while unavailable. Orphan records keep their last generation until
TTL. A return or renewed reference refreshes the appropriate mark. Reading UI state does not refresh
anything. FlatBuffers stores generation inline and merges ACSS into flags.

Flags are ACSS=bit 0, RESL=bit 1, SHRT=bit 2, IDHI=bit 3, FAILEDL2=bit 10 and kind=bits 13–15.
FAILEDL1 is kind 4. Successful physical kinds imply completed L1; metadata-revision mismatch
requires full recreation. [Persistence](/Cuetree/persistence) owns new high-only technical failed
IDs versus retained known MediaIDs and analysis failure flags. No completion bit or partial update
remains.

## Album

`Album(id, kind, title, tracks, sources, attributes, life)` is retained ALBM state. Its JVM Long ID
is a random high-only common ID on the wire. `AlbumKind` is ORDINARY, EXPLICIT or SINGLES. Tracks
are ordered MediaIds and sources are snapshot-local occurrence indexes. External or independently
titled tables emit explicit membership directly. Untitled embedded chapters expand grouped base
MEDIA into ordered FILF, or form a fallback explicit Album when ungrouped. Repeated FILF identities
remain repeated where a source table declares positions, including untitled tables expanded within
an ordinary group. Physical-copy and logical-position equivalence follow
[AG-10](/Riff/LATEST/album-grouping-positions); Media.ID alone is not a position key. Source
references preserve the exact metadata of each ordered position. Source facts are independent of
prior Player projections. Discarding projections discards their own random IDs; rebuilding them
cannot reconstruct those IDs from MDIA.

Stable reconciliation uses one-to-one source overlap for ordinary albums, exact album
title/attributes plus ordered (MediaID, position attributes) for explicit albums, and role for
Singles. Equal explicit copies converge regardless of path; different metadata variants remain
separate. Rebuilding exact explicit groups requires readable inputs or retained ALBM membership.
Ambiguous split/merge continuity is not guessed. Unreferenced albums retain ID and presentation with
empty membership until TTL. The single SINGLES album has title `[[SINGLES]]`; recognize its kind,
never its text. Empty Singles remains available. Album.flags combines ACSS, IDHI and kind. Other
albums are available when any member identity has an available source.

## Artist and Genre

`Artist(id, key, name, albums, life)` stores a random high ID, canonical key, display name and
direct Album IDs. Computed `attributes` exposes its artist display value. Track Artist and Album
Artist independently link whole albums.

`GenreRef(dictionaryId: Long? = null, text: String? = null)` requires exactly one variant and
nonblank text. `Genre(ref, name, albums, life, id = RandomIds.next())` has a retained random high ID
distinct from the optional external dictionary ID. The indexer uses its injected allocator. Computed
`attributes` contains GenreText and, for known dictionary entries, numeric Genre.
`GenreResolver.resolve(text): GenreRef` maps observed text to the external GNRE dictionary or text:

```kotlin
val dictionary = mapOf("rock" to 12L)
val resolver = GenreResolver { text ->
    dictionary[text.lowercase()]?.let { GenreRef(dictionaryId = it) }
        ?: GenreRef(text = text)
}
val indexer = Indexer(MockMediaID(), ttl = 100, genres = resolver)
```

Artist and genre flags contain IDHI/ACSS. Availability follows linked albums; generations follow
factual membership. Orphans retain empty lists until TTL. There are no per-artist or per-genre
copies of Singles, and Cuetree never modifies GNRE.

<!-- AI-INTENT: Preserve source occurrence order until the source-specific queue policy applies.
MediaID equality identifies audio; it does not identify a declared chapter position. -->
