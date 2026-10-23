# Cuegrain API reference

Javadoc-style reference for Cuegrain 0.1.0: declarations, constructors/defaults, parameter meaning,
member behavior, results, errors and executable examples. Signatures use Kotlin because this is a
Kotlin/JVM library. Java callers must account for Kotlin defaults and companion/object access; no
Java overload facade is promised.

[Library home](/Cuegrain) · [Method index](/Cuegrain/API/methods) · [Examples](/Cuegrain/examples) ·
[Build and CLI](/Cuegrain/build-and-cli)

## Packages

| Package                       | Artifact and responsibility                                            |
| ----------------------------- | ---------------------------------------------------------------------- |
| `link.roadio.cuegrain`        | Core: parsing, input, model and projections.                           |
| `link.roadio.cuegrain.repair` | Core: conservative grouped-text repair and detector protocol.          |
| `link.roadio.cuegrain.ced`    | Optional CED: native encoding detector; not in the default core build. |

## Reading and configuration

- [Cuegrain](/Cuegrain/API/Cuegrain) — Entry point for bounded metadata reading. Returns independent
  source observations without selecting a winner, opening paths or applying playback policy.
- [MediaInput](/Cuegrain/API/MediaInput) — Caller-owned, position-independent byte source. Implement
  this interface for descriptors, archives or application storage without coupling Cuegrain to that
  storage system.
- [ByteArrayInput](/Cuegrain/API/ByteArrayInput) — MediaInput backed by a caller-supplied byte
  array, useful for CUE text, fixtures and already-buffered metadata.
- [FileInput](/Cuegrain/API/FileInput) — Read-only random-access file implementation of MediaInput
  and java.io.Closeable.
- [FileResolver](/Cuegrain/API/FileResolver) — Functional interface for explicitly resolving a CUE
  file reference to a caller-owned MediaInput.
- [ParseOptions](/Cuegrain/API/ParseOptions) — Immutable configuration values for a read. Supply
  options to Cuegrain; each input receives a fresh ParseContext.
- [ParseMode](/Cuegrain/API/ParseMode) — Enum selecting treatment of recoverable syntax oddities.
- [Limits](/Cuegrain/API/Limits) — Positive per-input safety budgets. These bound parser work and
  reads; they do not establish that metadata is semantically valid.

- [Media identity](/Cuegrain/API/MediaIdentity) — Optional typed acquisition and injected AU/hash
  interfaces, independent of Android.

## Result model

- [Report](/Cuegrain/API/Report) — Top-level result for one input. Constructed by Cuegrain.read;
  data does not close or own its input.
- [Diagnostic](/Cuegrain/API/Diagnostic) — One bounded parser observation. Codes support
  programmatic handling; messages are explanatory text.
- [Source](/Cuegrain/API/Source) — One independent container, CUE sheet or chapter representation.
  Sources coexist; Cuegrain does not choose an application winner.
- [Tag](/Cuegrain/API/Tag) — One raw textual field with preserved original evidence and optional
  format identity.
- [Entry](/Cuegrain/API/Entry) — Track or chapter within one Source. Does not manufacture a playable
  media item, a global timeline or an album.
- [FileReference](/Cuegrain/API/FileReference) — A file declared by CUE, with optional
  caller-supplied input resolution.
- [MediaTime](/Cuegrain/API/MediaTime) — Exact nonnegative rational time in ticks/timescale. File
  identity belongs to Position, not this value.
- [Position](/Cuegrain/API/Position) — Pairs an input/file-reference identity with an exact time in
  that file.
- [CueIndex](/Cuegrain/API/CueIndex) — One CUE INDEX statement, preserving its number and individual
  file-local position.
- [TableOfContents](/Cuegrain/API/TableOfContents) — Flat TOC graph node, notably ID3 CTOC. Kept
  separate from the chronological Entry sequence.
- [Gain](/Cuegrain/API/Gain) — A decoded gain, peak or reference observation with its original field
  evidence. Zero is a valid observation.
- [Artwork](/Cuegrain/API/Artwork) — Picture descriptor with optional deferred access to embedded
  bytes. External URLs remain descriptors and are never fetched.

## Projections and analysis fields

- [Metadata](/Cuegrain/API/Metadata) — Stateless shared projections for decoded tag lists. Use this
  object directly; it has no constructor.
- [Json](/Cuegrain/API/Json) — Dependency-free JSON projection and serialization. This is an object,
  not an instantiable writer.
- [RiffFeatures](/Cuegrain/API/RiffFeatures) — Typed RIFF analysis observations belonging to one
  Source or Entry. These field names describe interoperable metadata, not a dependency on the Riff
  application.
- [RiffMetric](/Cuegrain/API/RiffMetric) — One finite analysis metric observation and optional
  confidence. Parsing validates finite decimal values; construction is a plain data-model operation.
- [RiffGenre](/Cuegrain/API/RiffGenre) — One ordered RIFF_GENRE text observation and optional
  adjacent confidence.

## Reader extension

- [FormatReader](/Cuegrain/API/FormatReader) — Extension interface for a metadata format reader.
  Cuegrain uses the first matching configured reader; independent representations returned by that
  reader remain separate.
- [ParseContext](/Cuegrain/API/ParseContext) — Per-input parser state supplied to FormatReader.read.
  The constructor is internal; application clients obtain reports through Cuegrain.
- [CueReader](/Cuegrain/API/CueReader) — Standalone/embedded textual CUE with file references,
  tracks, indexes, gaps and scoped tags.
- [Id3Reader](/Cuegrain/API/Id3Reader) — MP3 ID3v2.2/2.3/2.4 and ID3v1 fields, CHAP/CTOC, embedded
  CUE and bounded LAME observations.
- [Id3MetadataBudget](/Cuegrain/API/Id3MetadataBudget) — portable validation before ID3 body
  allocation.
- [FlacReader](/Cuegrain/API/FlacReader) — FLAC metadata blocks including STREAMINFO timing, native
  CUESHEET, Vorbis comments and artwork.
- [OggReader](/Cuegrain/API/OggReader) — Ogg Vorbis/Opus comment metadata and independent Opus
  header gain.
- [Mp4Reader](/Cuegrain/API/Mp4Reader) — ISO BMFF iTunes-style metadata plus independent QuickTime
  and Nero chapters.

## Encoding and conservative repair

- [EncodingDetector](/Cuegrain/API/EncodingDetector) — Pluggable byte-encoding detector used only
  after deterministic CUE decoding and optionally by conservative text repair.
- [EncodingDetection](/Cuegrain/API/EncodingDetection) — Result of a caller-supplied encoding
  detector. Reliability and consumed-byte evidence remain explicit.
- [MojibakeEncoding](/Cuegrain/API/MojibakeEncoding) — Small encoding classification used by the
  conservative repair algorithm, separate from the general EncodingDetection charset string.
- [MojibakeEncodingDetection](/Cuegrain/API/MojibakeEncodingDetection) — Bounded detection evidence
  supplied to MojibakeRepair.
- [MojibakeEncodingDetector](/Cuegrain/API/MojibakeEncodingDetector) — Detector contract for the
  grouped-text repair API; deliberately independent of an Android or native backend.
- [MojibakeRepair](/Cuegrain/API/MojibakeRepair) — Stateless object that makes one conservative
  decision for an ordered group of related text fields.
- [MojibakeRepairResult](/Cuegrain/API/MojibakeRepairResult) — Per-field result of the grouped
  conservative repair decision.
- [CedEncodingDetector](/Cuegrain/API/CedEncodingDetector) — Optional native Compact Encoding
  Detection backend implementing EncodingDetector. Located in the separate CED artifact; absent from
  default core/CLI builds.

## Alphabetical class index

[Artwork](/Cuegrain/API/Artwork) · [ByteArrayInput](/Cuegrain/API/ByteArrayInput) ·
[CedEncodingDetector](/Cuegrain/API/CedEncodingDetector) · [CueIndex](/Cuegrain/API/CueIndex) ·
[CueReader](/Cuegrain/API/CueReader) · [Cuegrain](/Cuegrain/API/Cuegrain) ·
[Diagnostic](/Cuegrain/API/Diagnostic) · [EncodingDetection](/Cuegrain/API/EncodingDetection) ·
[EncodingDetector](/Cuegrain/API/EncodingDetector) · [Entry](/Cuegrain/API/Entry) ·
[FileInput](/Cuegrain/API/FileInput) · [FileReference](/Cuegrain/API/FileReference) ·
[FileResolver](/Cuegrain/API/FileResolver) · [FlacReader](/Cuegrain/API/FlacReader) ·
[FormatReader](/Cuegrain/API/FormatReader) · [Gain](/Cuegrain/API/Gain) ·
[Id3Reader](/Cuegrain/API/Id3Reader) · [Json](/Cuegrain/API/Json) · [Limits](/Cuegrain/API/Limits) ·
[MediaInput](/Cuegrain/API/MediaInput) · [MediaTime](/Cuegrain/API/MediaTime) ·
[Metadata](/Cuegrain/API/Metadata) · [MojibakeEncoding](/Cuegrain/API/MojibakeEncoding) ·
[MojibakeEncodingDetection](/Cuegrain/API/MojibakeEncodingDetection) ·
[MojibakeEncodingDetector](/Cuegrain/API/MojibakeEncodingDetector) ·
[MojibakeRepair](/Cuegrain/API/MojibakeRepair) ·
[MojibakeRepairResult](/Cuegrain/API/MojibakeRepairResult) · [Mp4Reader](/Cuegrain/API/Mp4Reader) ·
[OggReader](/Cuegrain/API/OggReader) · [ParseContext](/Cuegrain/API/ParseContext) ·
[ParseMode](/Cuegrain/API/ParseMode) · [ParseOptions](/Cuegrain/API/ParseOptions) ·
[Position](/Cuegrain/API/Position) · [Report](/Cuegrain/API/Report) ·
[RiffFeatures](/Cuegrain/API/RiffFeatures) · [RiffGenre](/Cuegrain/API/RiffGenre) ·
[RiffMetric](/Cuegrain/API/RiffMetric) · [Source](/Cuegrain/API/Source) ·
[TableOfContents](/Cuegrain/API/TableOfContents) · [Tag](/Cuegrain/API/Tag)

## Reading this reference

Data classes expose Kotlin-generated value operations in addition to the members documented here.
Their constructors usually store observations; they are not full semantic validators. Times, input
ranges and Limits have specific validation described on their pages. Object declarations
(`Metadata`, `Json`, `MojibakeRepair`) are used directly without construction. `ParseContext` has an
internal constructor and is supplied to extensions.

A parse is synchronous. Keep caller-owned sources open through deferred artwork reads, retain
independent sources and diagnostics, and handle cancellation separately from a failed report. The
library does not create media-library identities, albums, queues or playback policy.

The repair package also exports `const val CED_MAX_INPUT_BYTES: Int = 65_536`; see
[MojibakeEncodingDetector](/Cuegrain/API/MojibakeEncodingDetector).
