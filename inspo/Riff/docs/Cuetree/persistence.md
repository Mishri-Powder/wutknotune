# Cuetree persistence

Cuetree writes independent FlatBuffers roots MDIA, ALBM, ARTS and GNRV under its own namespace.
Schemas live in the independent source export. Riff consumes this cohort as its library/catalog
format. GNRE is a separately packaged dictionary.

The four roots agree on exact version, generation, identity scheme and optional metadata revision.
Successful MEDIA/METADATA kind implies completed L1 and a successful stat. FAILEDL1 is node kind 4
and FAILEDL2 is bit 10 on MEDIA. No completion flag, header Boolean, scan epoch or backend
identifier exists. A changed metadata fingerprint/scheme is incompatible and requires complete
recreation, never a partial update of the old cohort.

Changed publications advance ordinary TTL; unchanged passes advance nothing. L0 retains unvisited
prior facts/availability; new playable sources need L1 identity. Flags use bit 0 ACSS, bit 1 RESL,
bit 2 SHRT, bit 3 IDHI, bit 10 FAILEDL2 and bits 13–15 kind. Other reserved bits stay zero. FAILEDL1
has SourceInfo and a directory parent. A new failed binding has a high-only technical ID with IDHI
and no SHRT/RESL; it enters no media bucket or projection. A previously admitted failed binding
keeps its known Media.ID/SHRT/RESL and historical facts as unavailable evidence; retained references
may resolve it but playback cannot. Seen failed bindings refresh generation, including unavailable
historical evidence; absence follows ordinary TTL. FAILEDL2 does not imply ACSS and cannot suppress
another copy. It is cleared on changed source observation or explicit analysis reset. Riff's
[source-failure contract](/Riff/LATEST/indexer-source-failures) owns Android policy.

Each retained record stores one generation inline and packs its availability into flags. Album kind
shares its flag word; other nonmedia catalog records carry IDHI and their availability bit. Do not
serialize a nested lifetime table, separate observed-at/first-missing timestamps, or individual
boolean flags. Nullable scalar presence uses the FlatBuffers vtable; a fragment span is implied by F
kind. Computed boolean accessors are permitted: they do not introduce stored boolean fields.

MDIA has no separate Structure root. It contains hidden metadata source records for CUE and
source-local structural table observations on media records. These records are factual input
evidence, never Player tracks or Folder rows. It retains D/M/F and hidden metadata facts
independently of Player projections. Ordered ALBM membership preserves source order independently of
ID sorting; indexes remain snapshot-local. Schema sources use multiline declarations, one field per
line and two-space indentation.

Serialization preserves attributes, ordered album membership and lifetime evidence needed to resume
indexing without inventing IDs. Readers reject unsupported versions, mixed generations/schemes,
invalid references, malformed identity/span/parent data and corrupt/truncated payloads.

Publication uses a fresh immutable directory per snapshot and one atomic current-pointer replacement
after all files are complete. Failed or cancelled publication leaves the previous current pointer
valid; abandoned staging directories are not current snapshots. Readers read the pointer once and
keep that cohort. The MANIFEST records each binary member name, byte length, and a 16-digit
lowercase hexadecimal unseeded XXH3-64 checksum. Readers require this exact digest form and verify
all four lengths and checksums before decoding; incomplete or corrupted files fail validation. No
in-place mutation of a published buffer is allowed.

After a committed switch, CURRENT and PREVIOUS name at most the two latest complete, validated
cohorts. The store removes every older cohort and abandoned staging directory; it never chooses a
replacement by enumerating directories. A write in progress may temporarily add one candidate. If
CURRENT fails validation, a valid PREVIOUS remains readable. Readers that already hold file
descriptors keep those exact immutable bytes after directory removal. Cleanup failure cannot undo a
committed CURRENT and is retried on the next store opening or publication.

The API can expose generated FlatBuffers for immediate random-access display. Decoding a snapshot
for further indexing may allocate retained model objects; Player consumers need not rebuild a tree.
Concurrent writers require external serialization. A store instance does not own a background thread
or Android lifecycle.

## MVP format

The current format version is **11**. This version fences personal Singles kinds and the
filename/duration evidence semantics; unchanged table layout does not make earlier versions
compatible. Readers require that exact version; incompatible stores require complete index
recreation. No conversion, migration or compatibility reader is provided. Readers reject invalid or
incompatible publications with a typed validation failure; they never implicitly delete a store. The
Riff host's automatic discard/recreation policy is owned by
[its library lifecycle](/Riff/LATEST/indexer-library-lifecycle#automatic-library-recreation). All
record tables remain sorted by common signed ID. Sorting remaps ALBM occurrence indexes, preserving
their position order. MDIA attribute dictionaries and common ID references remain unchanged.

Node stores flags, generation, id, optional parent_id, optional source, nullable begin_us/end_us,
attribute references, optional parsed structural tables and separate source_evidence references.
Source evidence is an opaque consumer extension attached only to media facts; it is not ordinary
track metadata and is removed from the Player cohort. A table retains its key, table attributes and
ordered sections; each section retains its relative FILE reference, nullable bounds and attributes.
Hidden metadata nodes have their own high-only ID, SourceInfo stat and parsed CUE tables, but no
audio-derived identity, fragment span or Player row. D/M/hidden-metadata source information is a
separate SourceInfo table containing name, nullable modified/size, and root-only provider/locator.
It has no separate ID or TTL. F implies a span, even when both bounds are absent. There is no Node
name, root, base_id, origin, parent index or children vector. Root lists and children are runtime
indexes derived from parent IDs. Album has no origin; its ordered source references preserve the
metadata context of each explicit position.

Validation covers typed parents, acyclic directory hierarchy, root source configuration, optional
scalar presence, source/fragment shape, sortedness, attributes, source references and liveness.
Lookup and runtime children work directly with verified FlatBuffers; they do not query providers.

## Optional JSON companions

The POSIX CLI accepts `--json` to additionally export MDIA.json, ALBM.json, ARTS.json and GNRV.json
beside the corresponding binary files in the same immutable cohort. JSON is the schema-shaped
representation of those exact encoded buffers, including defaults, ordered vectors, ID references,
nullable scalar presence and exact decimal 64-bit integers. The result must be valid strict JSON:
escape control characters in strings without changing their decoded values or rounding IDs. It is
not the allocated JVM model or an expanded convenience view. Use the packaged FBS schemas and the
pinned FlatBuffers converter.

All requested JSON companions must be generated and durably written before CURRENT switches.
Conversion failure leaves the previous cohort current. JSON is optional diagnostic output, not an
input to store recovery; the binary manifest and reader remain authoritative and unchanged. Without
the option, no JSON files or converter processes are required. The CLI build guide owns converter
installation and invocation details.

Album kinds in flag bits 13–15 are ORDINARY=0, EXPLICIT=1, SINGLES=2 and ARTIST_SINGLES=3. The
latter carries its synthetic owner components in ALBM Album Artist, not in a new wire field.
Directory listing size uses the documented extension attribute; observed positive duration uses
`cuetree.durationUs` in MEDIA source evidence. Root Book intent uses root MediaKind=2.
