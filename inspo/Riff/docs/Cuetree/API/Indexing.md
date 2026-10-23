# Cuetree API — indexing

## Indexer

Construct with `Indexer(identity, ttl, providers, metadata, genres, ids, grouping, vocabulary)`:

| Parameter  | Type                       | Default                                      |
| ---------- | -------------------------- | -------------------------------------------- |
| identity   | MediaIdGenerator           | Required                                     |
| ttl        | Long                       | Required; nonnegative, in generation units   |
| providers  | List&lt;SourceProvider&gt; | One PosixProvider                            |
| metadata   | MetadataReader             | CuegrainMetadataReader()                     |
| genres     | GenreResolver              | Canonicalized text genres                    |
| ids        | IdAllocator                | RandomIds                                    |
| grouping   | AlbumGroupingPolicy        | DefaultAlbumGroupingPolicy                   |
| vocabulary | CatalogVocabulary          | CatalogVocabulary.EMPTY; no lexical evidence |

`scan(roots, previous, generation, options, cancellation, onCheckpoint, onObservation, onSource): Snapshot`
is synchronous. `roots: List<RootSpec>` and `generation: Long` are required; `previous` defaults to
an empty snapshot for the generator's scheme. Options and cancellation default to their
constructors; `onCheckpoint` defaults to null. The checkpoint callback receives progress and a lazy
validated snapshot supplier. It returns true only after a checkpoint was committed; that advances
the publication generation. The optional onObservation(node, observation) callback receives
transient Cuegrain gain sources from a changed metadata read. The optional onSource(node, provider,
handle) callback receives every admitted available media source, including unchanged sources.
Handles remain scan-local and must not be persisted.
[Progress and checkpoints](/Cuetree/API/Progress) defines its event kinds, readiness and failure
behavior. A completed full scan separately returns its final snapshot.

Supply a nonnegative generation strictly greater than the preceding publication. A committed
checkpoint consumes one generation; subsequent changed checkpoints and the final publication use
successively greater values. An unchanged pass publishes nothing. `Snapshot.hasSameContentAs(other)`
is the shared semantic comparison: it ignores publication/record generations and diagnostics,
preserving all other fields and occurrence order. Android and desktop callers use it before
publication. The caller serializes publication; a failed checkpoint commit aborts the scan.
[Catalog lifetime](/Cuetree/catalogs#mdia-and-direct-folder-display) owns observation/reference
marks, availability and publication-based TTL for every record.

Omitted roots are observed as unavailable. Duplicate root locators within one provider, an
unconfigured provider, scheme mismatch and non-increasing generation are rejected. A partial root
list never means “skip these roots.”

CancellationException and fatal errors leave the previous snapshot unchanged. Provider IOException
becomes a diagnostic and unavailable retained evidence. Handles opened by the scan are closed. The
caller serializes scans; the whole L1 workset uses one bounded transient preparation worker after
complete L0. The caller consumes prepared results in randomized acquisition order. The input tree is
assumed stable throughout one scan. Each root uses its supplied `RootSpec.initialStat` when present,
or receives one initial stat; children use the stat carried by their directory listing when
available, or one initial stat per child when the provider omits it.

An unchanged size/mtime and matching metadata revision reuse ordinary metadata, identity and stored
structural tables without opening the source. External CUE has a hidden MDIA metadata record with
its own stat and table evidence; referenced separate tracks retain their own ordinary metadata,
while CUE supplies album membership and order. For each field, CUE track metadata overrides CUE
album metadata, which overrides the referenced file tag; a field omitted by CUE falls back to the
file. Derived FILF can be refreshed from unchanged retained observations. A changed metadata
fingerprint rejects the previous nonempty snapshot and requires complete recreation; it never
selectively reacquires the old cohort. A force refresh reacquires both. Identity uses actual
generator output; mtime is never an identity key. Metadata and identity reads use the initial source
stamp without post-read stat checks. Source changes during the scan are outside its input contract;
the next scan observes changed stamps. Portable renames are new admission; the old missing
occurrence remains until TTL. No move continuity is inferred.

## AlbumGroupingPolicy

`AlbumGroupingPolicy.group(input: AlbumGroupingInput): List<OrdinaryAlbumGroup>` is the synchronous
ordinary-album policy boundary.
`AlbumGroupingInput(snapshot, eligibleSources, cancellation, vocabulary = CatalogVocabulary.EMPTY)`
contains the factual snapshot and snapshot-local base MEDIA position representatives left after
explicit ownership and shared [copy resolution](/Riff/LATEST/album-grouping-positions). Other
physical bindings remain MDIA facts and are claimed with an admitted position, not emitted again as
Singles merely because they are not representatives. A base with one unambiguous embedded chapter
table remains eligible; FILF do not. The policy reads immutable metadata/access facts and checks
cancellation during substantial work. `OrdinaryAlbumGroup(sources, title, attributes)` names and
orders one proposed ordinary album; all source indices must be unique, eligible and used at most
once across results. Empty groups and blank titles are rejected. After grouping, Cuetree expands
chapterized members into FILF. An unassigned chapterized base forms one fallback explicit Album;
other unclaimed logical positions enter Singles. Equivalent physical bindings are claimed with their
admitted position under [AG-10](/Riff/LATEST/album-grouping-positions), not emitted again as
unassigned copies. Failures abort the attempted scan or checkpoint without replacing the previously
committed cohort.

`DefaultAlbumGroupingPolicy` is the built-in ordinary-album policy; no scripting/reload API exists.
[Catalog policy](/Cuetree/catalogs) imports the adopted grouping contract.
[Open work](/Riff/LATEST/open-decisions) records remaining verification. `GenreResolver` maps
observed genres to GNRE IDs/text references before catalog linking.

## Ordered acquisition

[Acquisition API](/Cuetree/API/Acquisition) owns ScanOptions, cancellation, failure suppression,
ordered/isolated work, MetadataReader and Cuegrain observation projection. Failure kinds and
historical identity references follow [persistence](/Cuetree/persistence).

## ScanOptions and Cancellation

See the [acquisition owner](/Cuetree/API/Acquisition#ordered-source-work).

## MetadataReader and CuegrainMetadataReader

See the [metadata reader API](/Cuetree/API/Acquisition#metadatareader-and-cuegrainmetadatareader).

## Singles and derived evidence

`RootSpec(..., book = false)` accepts static root Book intent, persisted as root MediaKind=2. It
protects descendants from Singles classification and is independent of Player profile overrides.
`cuetree.durationUs` is optional positive microsecond source evidence on an admitted MEDIA; the
caller supplies it from its existing source probe. Unknown duration is absent. Optional
`cuetree.contentKind=book` supplies caller-confirmed source Book evidence. Source evidence remains
omitted from Player metadata.

`Snapshot.advancePublication(generation, ttl, grouping = DefaultAlbumGroupingPolicy)` also rebuilds
ready catalogs if factual expiry removes nodes; callers using a custom ordinary policy supply the
same policy. It preserves explicit ownership, source metadata and ordinary identity reconciliation.
[Singles](/Cuetree/singles) and [filename evidence](/Cuetree/filename-evidence) own classification
and ordinal rules.

## CatalogVocabulary

The constructor accepts string sets `genericArtists`, `placeholderArtists`,
`placeholderAlbumArtists`, `placeholderAlbums`, `genericTitles`, `versionWords`, `protectedWords`,
`discWords` and `setMarkers`, plus dictionary-ID sets `spokenGenreIds`, `liveGenreIds` and
`electronicGenreIds`. Each defaults to empty. Construction snapshots the collections and prepares
comparison matchers; source spelling and factual attributes remain unchanged. Terms are literal
strings, not regular expressions. Genre names resolve through the existing `GenreResolver`; retained
numeric `genreId` facts also supply evidence after persistence.

A consumer supplies its build-generated vocabulary explicitly. Use the same value in `Indexer` and
`Snapshot.advancePublication`; omitting it deliberately disables lexical clues, not structural or
duration-based grouping. The CLI's default constructor has no implicit EN/RU profile. Riff's
build-generated adapter is external to Cuetree. See
[configured evidence](/Cuetree/singles#configured-language-evidence).
