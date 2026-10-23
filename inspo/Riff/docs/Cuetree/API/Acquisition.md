# Cuetree API — acquisition

## Ordered source work

`processOrderedPipeline` is the shared bounded preparation/ordered-consumption engine. Depth
includes the consumed item. Only the caller integrates facts. Cancellation is observed before
preparation, while waiting for a result and before consumption. Source-local failed preparation
becomes FAILEDL1; cancellation and failed publication do not. Normal file jobs are randomized inside
each directory independently of catalog/track order. Host-requested isolated acquisition is executed
with no overlapping preparation or publication; a host may quarantine a matching repeated-crash
source before opening it. The Android host implements `AcquisitionGuard.mode(source)`,
`begin(source, mode, cancellation)`, `finish(source, outcome)`, `failed(source)` and
`resolved(source)`. `failed` runs on the integration thread after a new failed/quarantined binding
is committed in the candidate; unchanged failed fast skips and cancellation do not call it.
`SourceAcquisition` carries provider, handle, observed stamp, parent ID, name and hint. `begin` must
acknowledge journal registration before opening the source. A failed IPC exchange aborts the pass;
it never becomes FAILEDL1.

`ScanOptions(forceRefresh = false, pipelineDepth = 3, mediaExtensions = ..., metadataRevision = null)`
sets one pass. Metadata revision is only a compatibility fingerprint. When the caller supplies a
non-null revision, a nonempty previous snapshot with a different revision is rejected; complete
recreation is required. A null option does not request compatibility validation and retains the
previous fingerprint. Android always supplies its current fingerprint; this optional library API is
not a migration mechanism. There is no per-binding metadata-upgrade pass or completion flag. Equal
successful source stats reuse physical MEDIA or METADATA facts; equal FAILEDL1 stats suppress
attempts, including force-refresh attempts. `pipelineDepth` is 1..8. The default extension set is
mp3, flac, ogg, opus, m4a, m4b, mp4, wav, aac, aiff and alac; CUE sidecars are separate. Provider
candidacy does not establish decoder support. `Cancellation(cancelled: () -> Boolean = { false })`
throws CancellationException when requested or interrupted. Hosts keep handles/attempt state
transient and own timers, IPC and scheduling.

`SourceProvider.bindingId(handle): Long?` optionally supplies a high-only technical ID without
opening the source. POSIX hashes its normalized source path with unseeded XXH3-64; null uses the
normal high-only allocator. This is not Media.ID. Source reconciliation uses parent/name and typed
identity rules; equal technical hashes never merge physical occurrences.

## MetadataReader and CuegrainMetadataReader

`MetadataReader.read(input: MediaInput, hint: String, cancellation: Cancellation): Observation` is
the metadata extension boundary. The default `CuegrainMetadataReader()` calls Cuegrain; it does not
parse format bytes itself. Indexer owns input lifetime. Propagate cancellation and distinguish I/O
failure from successful empty metadata.

`acquire(input, hint, cancellation, high: ((MediaInput) -> Long)?): AcquiredObservation` pairs
metadata and optional high on the same input. Its default reads metadata, checks cancellation and
calls high. `AcquiredObservation(observation: Observation, high: Long?)` owns no source.
Changed-source acquisition shares one bounded CachedMediaInput. Collision rereads remain separate.

`MediaIdGenerator.fragmentComponent(contentComponent, span, low, cancellation): Long?` defaults to
null. Adapters can derive a clip component from a known base without I/O; null retains the mock
source-reading fallback. Missing base low is acquired once per binding for ordinary collisions.

`Observation(attributes, tables, diagnostics, gainSources, sourceId, sourceEvidence, sectionGainSources)`
contains independent Cuegrain observations. Transient sectionGainSources maps each table key to
ordered per-section sources; select decoded gain without reparsing/reordering. Source objects are
not persisted; selected evidence may be retained as attributes.
`TableObservation(key, attributes, sections)` describes one independent table. An embedded table
without album/title is a base chapter expansion; external or titled tables are explicit candidates.
A table key is input-local, not Album identity.
`Section(reference: String?, span: Span, attributes: Attributes)` refers to the containing input
(null) or a relative FILE; it contains no resolved provider ID.

The default adapter preserves MP4 stik as numeric mediaKind and validates multi-audio-section,
ordered, file-local tables. Malformed or competing structures do not concatenate; valid ordinary
tags survive optional-structure rejection. Admitted sections have one-based Fragment and
TotalFragments. Explicit candidates preserve ordered positions independently of MDIA sorting.
`Diagnostic(code: String, source: String, message: String)` is transient operational evidence, not
decoder admission.
