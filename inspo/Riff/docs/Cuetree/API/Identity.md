# Cuetree API — identity

## MediaIdGenerator

Interface members:

```kotlin
val scheme: String
fun high(input: MediaInput, span: Span?, cancellation: Cancellation): Long
fun low(input: MediaInput, span: Span?, cancellation: Cancellation): Long
```

The scheme is a stable algorithm/version identifier, persisted in every cohort. High is required for
every admitted media/fragment; every initial short record carries SHRT. Low is requested lazily for
both original and contender in a collision bucket. A previously resolved record refreshed under the
same high may reacquire low to validate its complete identity. Both methods must be deterministic,
honor cancellation and leave input closure to the caller. The span is null for a whole media source.
Android will implement this interface through MediaExtractor; no such implementation is bundled
here.

## MockMediaID

Construct with `MockMediaID(chunkBytes: Int = 65_536)`; allowed buffer size is 1 through 16 MiB. The
scheme is `cuetree.mock.xxh3-file.v1`. Whole-file high is seed-zero XXH3-64 over every byte. Low
uses seed `0x43554554524545`. Reads are bounded and cancellation is checked between chunks.

Fragment hashing adds a versioned domain, file length, effective begin (absent means zero), explicit
end-presence and end, then all file bytes. It is a reproducible experimental identity, not
encoded-audio Media.ID. Changing tags can change identity; a standalone track and an equal audio
segment need not match.

```kotlin
val mock = MockMediaID()
provider.open(handle).use { input ->
    val high = mock.high(input, null, Cancellation())
    println(MediaId(high))
}
```

Production callers normally let Indexer schedule low acquisition; this example only demonstrates
direct generator usage.

## MediaId and Span

`MediaId(hi: Long, lo: Long? = null)` carries 64-bit bit patterns, represented as signed JVM Long.
Zero is valid; null alone means that low has not been acquired. Equal complete IDs may have multiple
physical/fragment sources. Snapshot-local indexes distinguish those source occurrences.

`Span(beginUs: Long? = null, endUs: Long? = null)` preserves absent bounds independently from
explicit zero. A present begin is nonnegative; a present end is strictly after the effective begin
(begin or zero). Null begin imposes no explicit lower bound; playback begins at zero. Null end
imposes no explicit upper bound. There are no -1 sentinels. An F node always has a Span, even when
both bounds are absent; a whole MEDIA has no Span.

## IdAllocator and RandomIds

`IdAllocator.next(): Long` supplies random admission IDs for folders, albums, artists and genre
views. `RandomIds` is the default object, using UUID-derived 64-bit values. These namespaces are
separate. The indexer retries duplicate allocations and fails after repeated duplicates. A
deterministic allocator is useful in tests:

```kotlin
var nextId = 100L
val indexer = Indexer(MockMediaID(), ttl = 100, ids = IdAllocator { nextId++ })
```

Allocation is not a media hash and does not create permanent per-source media IDs.

## IdOrder

`IdOrder : Comparator<MediaId>` compares signed high, then absent low before any present signed low.
`range(size, idAt, high): IntRange` performs lower/upper binary search over an ID-sorted table.
`matches(stored, flags, requested)` accepts an exact pair or equal high with requested absent low
and stored SHRT. IDHI records always use exact high-only IDs. See the complete
[collision and short-reference lifecycle](../identity-access.md).
