# Cuetree API — persistence

## FlatCatalogs

`FlatCatalogs.encode(snapshot): FlatCatalogs` validates and serializes MDIA, ALBM, ARTS and GNRV.
`FlatCatalogs.read(files: Map<String, ByteArray>): FlatCatalogs` copies and validates all four
buffers. Unknown identifiers/versions, mixed headers, malformed vectors and invalid references fail
with IOException when decoding. Riff consumes this exact current cohort format through its Android
adapter.

Encoding canonicalizes table order and remaps occurrence indexes. A hand-built snapshot with an
empty attribute dictionary is interned automatically; a supplied dictionary must satisfy all
references. Diagnostics are scan results and are not persisted.

Methods:

| Method                                | Result                                                                      |
| ------------------------------------- | --------------------------------------------------------------------------- |
| snapshot()                            | Allocated model for further indexing                                        |
| mdia(), albm(), arts(), gnrv()        | Generated random-access root accessors                                      |
| buffer(name)                          | New read-only ByteBuffer cursor                                             |
| roots()                               | Root node indexes derived from parent IDs                                   |
| childrenOf(index)                     | Runtime child index; -1 selects roots                                       |
| sources(MediaId)                      | Matching MDIA occurrence indexes via binary high range and exact/SHRT match |
| album/artist/genre/attribute(MediaId) | Generated accessor or null, using exact common-ID lookup                    |
| bytes(name)                           | Independent byte-array copy                                                 |

FlatCatalogs.FORMAT_VERSION follows the exact [format owner](/Cuetree/persistence#mvp-format);
readers require that version and writers explicitly store it. Adoption/deployment status is recorded
in [Riff open work](/Riff/LATEST/open-decisions), not inferred from this API requirement. Headers
agree on version, generation, scheme and metadata fingerprint. A fingerprint/scheme change requires
complete recreation. Successful kinds imply completed L1; FAILEDL1 and FAILEDL2 use the existing
flags word under [persistence](/Cuetree/persistence). There is no L1 completion flag or partial
metadata upgrade. Unsupported formats require complete index recreation. No conversion, migration or
compatibility reader is provided. The core rejects invalid data without implicitly deleting or
recreating the store; the Riff host owns
[automatic full recreation](/Riff/LATEST/indexer-library-lifecycle#automatic-library-recreation).

`FlatCatalogs.names` lists the four identifiers. Generated classes are in `link.roadio.cuetree.fbs`;
maintain schemas under `core/src/main/fbs`, not generated Java.

MDIA stores parent IDs; root and children indexes are built lazily in memory from verified
FlatBuffers. Neither roots nor children vectors are persisted. MDIA retains source-local parsed
tables and hidden CUE metadata records for unchanged-stat reuse. ALBM carries ordered MediaIds and
ARTS/GNRV direct Album IDs. All record arrays are sorted by common ID. Every record ID and
ID-bearing reference uses the same MediaId table, including high-only folder/catalog/attribute IDs.
MDIA owns the typed attribute dictionary; record attributes are ID references. A fragment parentId
references its base media for equivalent-copy resolution. Physical D/M/METADATA names, modified/size
and root access data live in SourceInfo; F has no physical source record. MDIA additionally contains
a sorted high-to-source index: `mdia.mediaByKey(hi)` returns a bucket, whose source indexes can be
checked for exact low or SHRT. This avoids a full scan for MediaId references. Nullable low uses
explicit vtable presence, not a zero sentinel. Generated hasLo(), hasBeginUs(), hasEndUs(),
SourceInfo.hasModified()/hasSize(), hasValueLong() and hasDictionaryId() accessors inspect that
presence; they do not represent stored boolean fields. Numeric zero remains a valid low identity,
modification mark and dictionary ID. Fragment spans follow F kind. Every record exposes generation()
inline; node/album flags contain kind and ACSS, and nonmedia records also carry IDHI.
Unknown/reserved bits are rejected. Album kind is decoded as AlbumKind.entries[album.flags() ushr
Flags.KIND_SHIFT].

```kotlin
val mdia = flat.mdia()
for (index in flat.childrenOf(folderIndex)) {
    val node = requireNotNull(mdia.nodes(index))
    val unavailable = node.flags() and Flags.ACSS != 0
    println("${node.source()!!.name()}: unavailable=$unavailable")
}
```

This directory display loop reads generated accessors using the shared runtime child index. Initial
store validation still allocates a verified model; repeated display does not decode it again.

## SnapshotStore

Construct with `SnapshotStore(directory: Path, maxFileBytes: Long = 268_435_456)`. Each root file is
bounded by the configured limit; valid limits are 12 through Int.MAX_VALUE.

`read(): Snapshot?` and `readFlat(): FlatCatalogs?` return null when neither pointer exists. They
validate the immutable cohort named by CURRENT, falling back only to a valid PREVIOUS. The manifest
records each binary name, byte length, and 16-digit lowercase hexadecimal XXH3-64 checksum. If no
named cohort validates, reading fails; files from different cohorts are never combined.
`InvalidPublicationException : IOException` distinguishes invalid pointer/manifest, missing named
members, bounds/checksum failure and incompatible or malformed encoded facts from operational
filesystem failures. A valid PREVIOUS still wins. If no cohort is usable and any attempt failed
because of operational I/O, propagate ordinary IOException rather than permitting a destructive
corruption recovery. The library never deletes or recreates a rejected store implicitly.

`readFlatWithCohort(): Pair<String, FlatCatalogs>?` returns the accepted cohort UUID with its
verified buffers under the same recovery rule.

`writeFlat(flat, snapshot, cancellation = Cancellation(), json = null)` publishes already encoded
buffers, avoiding a second encode. The caller must supply the validated model and buffers from the
same encoding gate; header generation, scheme and metadata revision are checked. The model supplies
publication ordering and scheme checks. Do not pair independently constructed inputs.

`write(snapshot, cancellation = Cancellation(), json = null)` encodes a validated snapshot, takes a
writer lock, writes/fsyncs a new UUID directory, durably replaces PREVIOUS with the accepted
CURRENT, and then atomically replaces CURRENT after all four files and the manifest are durable. The
method returns the new cohort UUID. Existing stores require the same scheme/metadata fingerprint and
a strictly greater publication generation. Equal and backward generations fail. The UUID directory
identifies the immutable cohort; it does not permit generation reuse. Callers serialize scans and
publication to avoid stale rewrites. Filesystem support for atomic moves and directory fsync is
required.

The optional `json: ((FlatCatalogs) -> Map<String, String>)?` callback receives the same encoded
buffers that will be published. It must return exactly the four catalog names (MDIA, ALBM, ARTS,
GNRV) mapped to their JSON text; the store adds `.json` and writes UTF-8. Callback failure or an
incomplete name set aborts before publication. The exporter owns schema conversion; the core adds no
converter dependency or JSON reader. The CLI's `--json` implementation uses its packaged schemas and
flatc. JSON companions are durable before CURRENT replacement but do not change the binary manifest
or recovery input.

Cancellation before CURRENT replacement leaves the accepted CURRENT valid. A committed write keeps
only its CURRENT and PREVIOUS cohorts; abandoned candidates and older cohorts are pruned under the
writer lock after publication or on the next store opening. An I/O error after pointer replacement
can report a durability failure even though the new pointer is visible, so read back before retrying
a failed publication.

## validate

`validate(snapshot: Snapshot)` checks semantic invariants and throws IllegalArgumentException on
invalid data. Indexing and encoding invoke the same gate. It covers tree parents/kinds, unique
folder/catalog identities, lifetime bounds, explicit-album source order, media references and
catalog availability, fragment base references/ordinals and dictionary references. The wire reader
additionally checks sortedness and dictionary hashes/types. Generated accessors are for already
verified cohorts.

<!-- AI-INTENT: Keep the model and encoded buffers paired through publication. Generated accessors consume verified immutable bytes; source indexes remain cohort-local. -->
