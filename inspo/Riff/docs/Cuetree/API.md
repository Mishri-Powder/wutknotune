# Cuetree API

Package: `link.roadio.cuetree`. JVM 17+. The API is experimental and independent of Android and
Riff. The [build guide](/Cuetree/build) explains external dependency selection and executable
distribution.

## Class index

| Reference                                                                     | Public types                                                                                                                                                                                                                               |
| ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [Indexing](/Cuetree/API/Indexing) and [acquisition](/Cuetree/API/Acquisition) | Indexer, ScanOptions, Cancellation, AlbumGroupingPolicy, AlbumGroupingInput, OrdinaryAlbumGroup, DefaultAlbumGroupingPolicy, CatalogVocabulary, MetadataReader, CuegrainMetadataReader, Observation, TableObservation, Section, Diagnostic |
| [Progress](/Cuetree/API/Progress)                                             | ScanProgress, ScanCheckpointKind; lazy intermediate snapshot callback                                                                                                                                                                      |
| [Identity](/Cuetree/API/Identity)                                             | MediaIdGenerator, MockMediaID, MediaId, Span, IdOrder, IdAllocator, RandomIds                                                                                                                                                              |
| [Access](/Cuetree/API/Access)                                                 | SourceProvider, SourceHandle, OpenMedia, PosixProvider, SourceStat, Stamp, Child, RootSpec, SourceResolver, ResolvedSource                                                                                                                 |
| [Catalogs](/Cuetree/API/Catalogs)                                             | Snapshot, Node, SourceInfo, NodeKind, Flags, Life, Album, AlbumKind, Artist, Genre, GenreRef, GenreResolver, Attributes                                                                                                                    |
| [Attributes](/Cuetree/API/Attributes)                                         | AttributeKind, AttributeValue, AttributeRecord; interned dictionary                                                                                                                                                                        |
| [Persistence](/Cuetree/API/Persistence)                                       | FlatCatalogs, SnapshotStore, validate; generated MDIA/ALBM/ARTS/GNRV accessors                                                                                                                                                             |

Data classes also provide ordinary Kotlin value/copy operations. Do not mutate backing collections
of a snapshot or feed a caller-modified object to a concurrent reader. Each scan owns a separate
working set and returns read-only collection interfaces. Generated FlatBuffers accessors receive
separate read-only ByteBuffer cursors.

## Dependency direction

Files and Cuegrain observations update MDIA D/M/F first. Validated CUE/TOC/CHAP observations
immediately produce explicit ALBM candidates; ordinary grouping handles the remaining media,
followed by ARTS/GNRV. Player catalog membership never controls source existence, availability,
generation or TTL. Prior catalogs retain their own IDs and presentation during reconciliation;
removing them does not alter factual indexing. No Structure entity is persisted.

## Complete example

```kotlin
import java.nio.file.Path
import link.roadio.cuetree.*

val generator = MockMediaID()
val store = SnapshotStore(Path.of("/tmp/music-catalog"))
val previous = store.read() ?: Snapshot.empty(generator.scheme)
val ttl = 30L * 24 * 60 * 60 * 1000 // This host uses milliseconds and 30-day retention.
val snapshot = Indexer(generator, ttl).scan(
    roots = listOf(RootSpec("posix", "/srv/music", "Music")),
    previous = previous,
    generation = maxOf(System.currentTimeMillis(), Math.addExact(previous.generation, 1)),
)
store.write(snapshot)

val flat = requireNotNull(store.readFlat())
val mdia = flat.mdia()
val rootIndex = flat.roots().first()
for (index in flat.childrenOf(rootIndex)) {
    val child = requireNotNull(mdia.nodes(index))
    println(child.source()!!.name())
}
```

Generation is the last observation mark, supplied by the caller; TTL uses the same scale. The
runnable consumer in `examples/cuetree` demonstrates indexing, direct catalog access, source
resolution, projection reconstruction from readable inputs, root loss/return and store reload with
this API.

The store directory is outside the media roots. Indexing and publication are separate calls so the
host controls scheduling and acceptance. See individual references for cancellation, I/O failures
and platform assumptions. The implementation evidence includes compiled use cases and independent
source-export verification.
