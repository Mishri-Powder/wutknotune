# Cuegrain library contract

Cuegrain is an independent Apache-2.0, read-only Kotlin/JVM metadata library. This contract owns
library behavior; consumer policy belongs to each application. The [API reference](/Cuegrain/API)
describes concrete types and calls, and [build/CLI](/Cuegrain/build-and-cli) owns artifact usage.

## Inputs and sources

Supported readers cover external and embedded textual CUE, MP3 ID3v2.3/v2.4 CHAP/CTOC and ordinary
ID3 tags, M4A/M4B QuickTime and Nero chapters and ordinary MP4 tags, FLAC CUESHEET and Vorbis
comments, and Ogg Vorbis/Opus comments. Sources coexist independently, including multiple CUEs, both
MP4 chapter representations and embedded CUE text. No source wins, fills or overwrites another. New
formats implement the same reader interface.

Explicit FILE names in embedded CUE remain unresolved references: embedding a sheet does not prove
that every declared file is the containing audio file. The containing input is recorded separately;
only an omitted FILE can inherit that input. Following external CUE references is an explicit
caller/CLI choice.

Entries retain source identity, metadata, file-local positions and rational time bases. CUE indexes
retain individual file references, including cross-file pregaps. Separate files described by a CUE
remain separate files. INDEX 00, INDEX 01, additional indexes and synthetic PREGAP/POSTGAP have
distinct meanings. Unknown ends remain absent; inferred ends identify their derivation. Data tracks
are represented explicitly without claiming Android playback support.

Text metadata preserves repeated/unknown fields and original values. Common fields, ReplayGain/R128,
pictures and chapter structures have normalized views. Gain sources and units remain distinct. Opus
header gain is separate from R128 gain. No gain is applied, no audio decoded and no tag or media
file is written. Artwork bytes are retrieved explicitly, with bounded reads.

RIFF analysis tags are interoperable typed observations, not an application dependency.
`RIFF_AROUSAL`, `RIFF_VALENCE` and `RIFF_PTE` each carry one finite value and optional
`_CONFIDENCE`. `RIFF_GENRE` is ordered/repeatable, with confidence only from an immediately
following `RIFF_GENRE_CONFIDENCE`. Missing confidence remains absent. Invalid/duplicate/orphan
fields retain their raw evidence and are diagnosed. Cuegrain does not clamp or infer values. See
[RiffFeatures](/Cuegrain/API/RiffFeatures).

## Media identity acquisition

Core owns MDIA-ID-V3.2 through injected random-access, encoded-AU and XXH3 interfaces. No Android,
SAF, MediaExtractor or Riff dependency enters core. Adapters supply selected AU payloads,
timestamps, duration, channels and seek. Desktop/Android equivalence is not required; no backend
identifier/flag/discriminator is added. Android retains its algorithm and ordinary collision rules.

Optional acquisition returns typed metadata and media_id from one caller-owned input session. Plain
read remains metadata-only. High formation and collision low are separate operations. Bounded shared
access avoids duplicate opens/reads without heap-loading complete media. The caller owns admission,
cancellation, publication and input lifetime. [Media identity API](/Cuegrain/API/MediaIdentity)
defines the boundary.

## Encoding and robustness

Core has no runtime dependency beyond Kotlin/JVM. CED is excluded by default: no native build tools,
binaries, JNA or CED implementation enter the ordinary artifact or its dependency graph. An explicit
compile-time option includes the native backend. Default CED settings are QUERY_CORPUS, unknown
encoding and language hints, no URL/HTTP hints and ignore_7bit_mail_encodings=true. Detection
reports charset, reliability and bytes consumed.

BOM/format encoding and explicit caller charset are deterministic. Unknown legacy encodings require
reliable detection or a caller-supplied charset. Conservative mojibake repair preserves originals
and is independently optional. Strict and compatible parsing use bounded reads, counts and nesting;
recovery is diagnosed with stable codes and source positions. The portable
[ID3 allocation validator](/Cuegrain/API/Id3MetadataBudget) rejects malformed/oversized header
declarations before body allocation and is reusable by consumer extractor-input adapters.
Cancellation propagates. Malformed input must not fabricate values or silently merge incompatible
sources.

## CLI and verification

The CLI is also distributed as a single executable JAR containing its JVM runtime dependencies,
launched with java -jar on Java 17 or later. The CED compile-time choice is preserved: the default
JAR has no native backend; the optional CED JAR includes the build host's native library and is
labelled with its operating system and architecture.

Every CLI invocation emits one UTF-8 JSON document on stdout, including help, version and errors.
JSON declares schemaVersion; binary pictures are omitted unless explicitly requested. Exit status
distinguishes success, parse/input failure and invalid arguments. Diagnostics are part of JSON.

Tests cover generated boundary fixtures and existing chapter fixtures. External corpora retain
provenance and are not silently relicensed as Apache-2.0. Both CED-disabled and CED-enabled
configurations require verification; each consumer verifies its platform boundary. Corpus runs and
installed release evidence belong in reports, never in the API contract.
