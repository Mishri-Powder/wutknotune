# Cuegrain integration in Riff

> Owner/scope: Riff as a consumer of the external Cuegrain library.

[Cuegrain](/Cuegrain) owns its [library contract](/Cuegrain/contract) and
[public API](/Cuegrain/API). This page owns only Riff adapters, source selection and persistence
policy; physical co-location in the repository does not make Cuegrain an application module.

## Single implementation owner

Cuegrain MUST own every capability already collected in the library: container/tag/CUE/chapter
parsing, gain representation decoding, artwork descriptors, charset detection and conservative
mojibake repair. Riff MUST call its API. Copies, fallback parsers for the same supported formats and
renamed reimplementations are prohibited. Missing extraction support is added and tested in
Cuegrain, preserving its independent JVM build and Android-free core.

Riff owns source admission/decoder capability, SAF handles, cancellation propagation, binding
choice, source priority, canonical attribute identity, genre lookup, Album grouping, structural
observations, ReplayGain reference/priority policy, persistence and Player behavior. These are
projections or application policy, not format readers. Cuegrain also owns portable Media.ID V3.2 and
typed acquisition. Riff supplies Android AU/random access/hash adapters; decoded-audio analysis
remains Riff-owned. Desktop/Android identity equivalence and backend identifiers are not required.

## Acquisition and source selection

After full L0 enumeration, L1 reads metadata through Cuegrain once per changed/new/incomplete or
invalidated source attempt and shares the report with ordinary metadata, chapter/media-kind and
ReplayGain projections. No extra LAME or chapter file pass is allowed. Admission still uses the
actual playback stack; library format support is not a decoder admission table. Unsupported metadata
containers may use Android's ordinary-tag fallback; supported formats never use a competing parser.
Technical duration can use the platform until Cuegrain exposes an authoritative technical-duration
API.

Raw sources stay independent. For the current scalar ordinary-metadata projection, use the first
nonempty primary container source, preferring ID3v2 over ID3v1. Repeated text values preserve source
order in the shared list projection. Embedded CUE/chapters never overwrite the container's ordinary
tags. Apple `stik=2` remains the only positive MediaKind evidence from that field.

Cuetree retains validated structural table observations and derives FILF with nullable microsecond
bounds. External CUE supplies Album membership/order and per-field precedence; embedded tables
expand their containing media under [Cuetree catalogs](/Cuetree/catalogs#albm). Independent source
tables preserve their own metadata and ordering. Invalid optional structures retain diagnostics
while ordinary tags remain usable.

Build independent ReplayGain candidates from each Cuegrain source; never fill missing fields from
another source. Riff applies the priority, completeness, reference and range policy in
[ReplayGain acquisition](/Riff/LATEST/replaygain-acquisition). Format syntax, Q7.8/Q9.23 and Sound
Check conversion remain in Cuegrain. Opus header gain is not added again to R128 observations.

## Safety, versioning and extension

The adapter owns descriptor offset/length bounds, handle closure and cancellation. Access loss or
`INPUT_IO` retains the normal unavailable binding outcome, not an empty metadata replacement.
Malformed optional metadata retains typed bounded diagnostics. No artwork bytes are requested by the
current metadata projection. URLs remain metadata. Cuetree resolves admitted relative CUE references
inside the selected source root.

Riff uses the shared repair implementation on the ordered text group prescribed by its metadata
contract; it then applies its own canonical normalization. Never repair the same values twice. CED's
optional Android bridge and native sources come directly from Cuegrain, with one packaged JNI
implementation. The default standalone library/CLI continues to build without CED.

Changes to extraction or gain policy require matching tests and a derived-data rebuild. Source
identity and wire versions change under their own owning contracts. Cover binary fixtures,
independent source selection, malformed input, cancellation/I/O, structural tables, gain policy,
Android packaging and independent library builds. New format observations extend Cuegrain; Riff
policy belongs to its registered contracts.
