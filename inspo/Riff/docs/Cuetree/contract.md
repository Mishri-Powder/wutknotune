# Cuetree library contract

## Authority and boundary

Cuetree is an independent Apache-2.0 Kotlin/JVM library for indexing a media library, retaining
unavailable entries, and producing ready Player catalogs. It builds from its own source export. Riff
integration uses a separate Android adapter in Riff; Cuetree remains independent and
source-exportable without Android or Riff dependencies. Persistence and attribute hashes use
unseeded XXH3-64. Injected production identity generators follow their owning unseeded XXH3-64
framing. The named whole-container MockMediaID scheme is reserved for host experiments;
[identity](/Cuetree/identity-access) defines its seeded low discriminator.

This contract and its children own Cuetree behavior. Riff formats are not Cuetree wire formats. The
user-authored `references/DIAGRAM_FLDR.md` remains byte-preserved. This section records the adopted
MVP and its implementation details; it does not change Riff runtime contracts.

- [Identity and source access](/Cuetree/identity-access)
- [Catalogs and lifetime](/Cuetree/catalogs)
- [Singles classification](/Cuetree/singles)
- [Filename ordinal evidence](/Cuetree/filename-evidence)
- [Typed attributes](/Cuetree/attributes)
- [Persistence](/Cuetree/persistence)
- [Public API](/Cuetree/API)
- [Build and verification](/Cuetree/build)

## Executable distribution

The CLI build provides one executable fat JAR containing Cuetree, its JVM runtime dependencies,
packaged FBS resources and required license notices. It runs with `java -jar` on Java 17+ without a
source checkout or an additional Java classpath. The normal assemble lifecycle builds it; a
dedicated fatJar task is also available. Compiler generation, the Java runtime and optional JSON
conversion share one tested FlatBuffers version declaration; generated bindings and runtime must be
upgraded together. Optional JSON conversion uses an external flatc selected by explicit
configuration or documented home/PATH discovery. The [build guide](/Cuetree/build) owns the version,
tool selection, commands and artifact naming. Compiler upgrades that preserve the schema and wire
semantics do not by themselves change Cuetree's format version.

## Metadata ownership

Use external [Cuegrain](/Cuegrain) through its [API](/Cuegrain/API) for container/tag/CUE/chapter
parsing, decoded gain observations, charset handling and text repair. Do not copy these parsers.
Cuetree owns observation selection, grouping, identities, persistence and access policy. The initial
implementation selects the first ordinary metadata source (ID3v2 before ID3v1); explicit CUE/TOC
observations directly create or update albums. Equal semantic observations converge even when their
source paths differ; distinct metadata variants remain separate albums.

## Execution

Indexing is synchronous and caller-scheduled. A scan constructs a new snapshot; the caller may
request a complete, validated intermediate snapshot at a completed media, resolved explicit-table,
or closed-directory checkpoint; cancellation or a fatal error must leave the previous snapshot
unchanged. Check cancellation during traversal, metadata parsing and identity reads. Provider I/O
failures retain affected prior records as unavailable with diagnostics; they must not replace
retained metadata with an empty successful result.

A scan completes L0 enumeration before L1 metadata/identity reads. Interim L0 snapshots retain
unvisited prior facts; new playable media need successful acquisition. Completed successful
MEDIA/METADATA bindings reuse successful stat/facts; equal FAILEDL1 records suppress failed
acquisition. No L1 completion flag or partial metadata-update pass exists.
[Persistence](/Cuetree/persistence) owns failure kinds/flags. Changed metadata fingerprint or
identity scheme requires complete recreation. Acquisition order is randomized inside directories,
independently of catalog position order.

A provider returns a complete directory listing or an error, never an ambiguous partial success. One
scan assumes directory membership and file contents remain stable until it completes. The root uses
the stat supplied by discovery, or is statted once when none was supplied; a child stat supplied by
its directory listing is the initial source observation. A provider without listed child stats
supplies one initial stat per child. Metadata and identity reads do not re-stat the source. When
both size and modification mark match a completed observation, reuse its ordinary metadata,
structural tables and identity without opening that source. New, changed or explicitly
force-refreshed successful inputs are acquired from the initial observation. The same bounded
ordered acquisition mechanism handles metadata and later AI/ReplayGain work; source enumeration
happens once per scan and passes admitted handles to analysis. mtime is an optimization hint, not
identity proof.

Returned catalogs contain ordered album membership and cached kind/access flags. Parent-to-children
indexes are computed in memory; they are not persisted. Rendering does not require walking
ancestors. Playback resolution may and must walk the source hierarchy.

Riff owns scan scheduling and publication cadence. Cuetree reports completed media and directory
counts and resolved explicit tables, and offers lazy snapshots at those checkpoints. A sidecar CUE
becomes eligible when its referenced media have been observed in the current scan; unrelated files
need not finish first. Cuetree does not keep timers or publish on its own. A valid prior Cuetree
snapshot is the previous database input.

## Documentation and verification

Document actual constructors, methods, ownership, failures and executable examples. Comments explain
invariants and extension points. Contract updates precede implementation; archive outgoing Wiki
editions verbatim. Verification covers POSIX access, opaque provider handles, collisions,
cancellation, TTL, explicit/heuristic grouping and persistence round trips.
