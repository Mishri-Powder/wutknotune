# Cuetree analysis integration

> Owner/scope: Riff Indexer analysis inputs, one optional analysis artifact and Player binding.

Indexer reads one current Cuetree MDIA/ALBM/ARTS/GNRV cohort as the sole factual library input.
Cuegrain observations from the same source read supply independent ReplayGain candidates and the
technical decoder hint to the Indexer-owned analysis feed. The shared bounded decoder workset covers
every admitted playable base FILM and every FILF separately. A FILF is measured and inferred over
its own nullable-bounded audio span; whole-file Track Gain is not silently substituted for a
fragment Track Gain. Music receives AI; Books receive gain only and ignore AI tags. Valid RIFF
source value/confidence pairs are accepted independently; do not invent confidence or combine
incomplete source pairs. Identical full MediaIDs share one analysis result and selected-source
fallback. A source failure does not make otherwise valid Cuetree library facts unavailable.

MDRG is the single optional immutable analysis database. It stores gain evidence, independently
nullable AI PTE/Arousal/Valence pairs, accepted probabilistic Genre labels, source/dependency state
and failure state per captured Cuetree MediaID. Accepted source-pair dependency uses the existing
modelRevision field and unseeded XXH3-64 of selected finite pairs/genre IDs; no schema field or
identity discriminator is added. Changed/withdrawn pairs invalidate dependent AI results. Partial
pairs survive completed inference failure; FAILEDL2 suppresses the failed physical binding under
[source failures](/Riff/LATEST/indexer-source-failures). Cancellation remains unfinished work.
Explicit section Gain suppresses only its own measurement. A full usable tag set suppresses all L2
source I/O. Its generation is independent of Cuetree. Both streams share the single pipeline-wide
[clock](/Riff/LATEST/immutable-publication#publication-cadence). Missing, corrupt or partial
analysis never blocks library publication or playback. Failed AI inference may coexist with valid
gain in one record and vice versa. The model package, gain priority/measurement, AI calibration and
selection mathematics remain owned by their registered scoped contracts.

An MDRG record keeps the exact nullable-low MediaID captured when it was admitted. Collision
expansion never rewrites that key. At use time Indexer and Player resolve a retained short reference
through Cuetree SHRT to its original full identity, not another high-colliding ID. Exact full-key
results take precedence when present; one logical identity is counted once for analysis and AI
selection. Source/dependency invalidation may replace that identity's analysis outcome without
changing unrelated records. An unresolved or expired alias contributes no result. Player queues,
bookmarks, playlists, reactions, Ban, book overrides and analysis joins use the same alias mapping;
TTL pruning must not remove an otherwise retained short reference.

ReplayGain Album Gain/Peak come only from external source evidence. Select Album Gain only when
every queue occurrence has the same available canonical Album Gain; otherwise select each
occurrence's external or measured Track Gain. If neither is available, the playback coefficient is
neutral. Measured gain never invents Album Gain or peaks. Stored peaks do not currently drive the
processor. A base media file and each FILF are independently eligible; a fragment may inherit an
applicable external Album Gain from its album/source context but not the whole-file Track Gain
unless an explicit fragment-scoped Track Gain exists.

The full AI feature includes the probabilistic model, queue selection, PTE/heatmap, profile
Like/Dislike/Ban and independently accepted Genre labels. Indexer publishes canonical GNRE-based
Album/Genre projections that include model evidence while preserving its separate provenance. Player
applies profile filters and navigation only. Exact GNRE equivalence remains in scope; fuzzy or
nearby-genre expansion is deferred. Every changed Cuetree catalog republication prompted by new AI
evidence receives a new Cuetree generation and consumes TTL normally.

<!-- AI-INTENT: The captured MDRG key and independent outcome states protect existing references and partial results. Resolve aliases at joins; do not rewrite keys or couple factual availability to optional analysis. -->
