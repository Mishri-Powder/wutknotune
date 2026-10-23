# Indexer media admission

> Owner/scope: Indexer source admission, media integrity and text identity

## Media integrity and text identity

Media identity and physical source occurrences are separate. Equivalent available sources resolve
one logical MediaID and do not multiply its selection probability.
[Cuetree identity](/Cuetree/identity-access) owns high/nullable-low collision handling and alias
lifetime. [Cuetree catalogs](/Cuetree/catalogs) owns source availability, references and
publication-generation TTL.

### Device-derived audio admission

Android source admission uses the stock pinned Media3 playback stack. A concrete case-insensitive
`audio/<subtype>` provider MIME admits a regular-file candidate. Missing, generic, malformed and
non-audio MIME values do not admit media. CUE sidecars are metadata inputs and never enter the
playback probe; their referenced audio sources are admitted independently. The SAF adapter supplies
this candidacy to Cuetree through `SourceStat.mediaCandidate`; the standalone POSIX extension policy
belongs to its host adapter.

For a new or changed media source, the acquisition worker prepares the actual source through the
same stock Media3 source/extractor and renderer configuration as Player. It requires an audio track
whose complete Format is `FORMAT_HANDLED` by the stock renderer. The bounded cancellable probe
supplies the opaque DecoderHint and nullable positive duration from the same Media3 retriever,
retained in internal source evidence. Duration retrieval shares the probe budget and cancellation;
unavailable duration is not guessed and does not by itself reject supported audio. File identity and
metadata must also succeed before admission. A completed failure retains a FAILEDL1 binding and
unavailable prior factual evidence, without aborting unrelated sources.
[Failure suppression and crash recovery](/Riff/LATEST/indexer-source-failures) owns its lifetime.
Cancellation never fabricates a failed or successful source. Stock extractor header peeks must be
validated through Cuegrain before a declared ID3 body allocation; a delivered-byte budget alone does
not bound extractor allocation.

Completed unchanged size/mtime observations reuse their identity, metadata, structure and decoder
evidence without reopening the source. Changed parser or platform policy requires complete
derived-index recreation; metadata revisions are compatibility gates, never per-binding upgrade
procedures. Later analysis consumes scan-admitted handles and stored hints. Playback retains normal
runtime source/decoder error handling because source admission cannot guarantee future access.

Display text is the extracted spelling after accepted repair; canonical identity never replaces it.
Only a missing or blank embedded Title permits filename fallback, which receives the same
repair/canonicalization. Cuegrain owns parsing and repair; Indexer applies canonical comparison
through Android ICU, the pinned confusable map and XXH3.

`RIFF-TAG-V4` chooses one repair profile over the complete track text set defined under TOC
extraction, never independently by field. Supported reversible failures are Russian Windows-1251
misdecoding through UTF-8/ISO-8859-1/Windows-1252 and Latin-script UTF-8 misdecoding through
ISO-8859-1/Windows-1252. Every changed candidate must strictly encode/decode and reproduce the
observed field under the inverse round trip. Aggregate evidence, content thresholds and one uniquely
preferred result decide acceptance; compatible fields follow the result, while
unchanged/incompatible fields retain exact text. Ambiguous, low-confidence, lossy or
replacement-character sets stay unchanged. Partial external correction is an accepted data-quality
risk.

The sole statistical detector is offline `compact_enc_det` at
`d127078cedef9c6642cbe592dacdd2292b50bb19`, through Android JNI. For each profile, strictly encode
available display fields in the TOC extraction evidence order in its observed charset, join with one
`0x0A`, and submit the first at most 65,536 bytes using `QUERY_CORPUS`, ignoring seven-bit mail
encodings and supplying no external hint. Reliable exact `UTF8` or `RUSSIAN_CP1251` evidence,
respectively, is necessary but never sufficient to repair. Detector disagreement, failure,
malformed/lossy evidence or unsupported encoding rejects that profile. Pin/test source, generated
tables and adapter; evidence construction, accepted encodings, CED or JNI changes advance the
revision and rebuild derived state.

GNRE comparison uses repaired text, Android ICU NFKC/default case fold and the pinned one-to-one
Unicode confusable mapping. The checked-in table is refreshed only by an explicit host task; runtime
and ordinary builds are offline. [Cuetree attributes](/Cuetree/attributes) owns exact typed
attribute identity. Display spelling, GNRE comparison keys and attribute IDs are distinct
representations.

Genre evidence and visible identity remain separate. Preserve source IDs/confidence, precompute MTG
equivalence in GNRE under the synthetic-genres contract, and group/display/reaction-key by canonical
visible GNRE identity rather than text. Malformed Genre evidence cannot invalidate valid A/V/PTE or
heatmap readiness. Player does not recreate persisted tag/attribute identity or perform fuzzy
identity matching. The shared read-only GNRE comparison for GenreText and the explicit Artist
display-key comparison remain permitted.
