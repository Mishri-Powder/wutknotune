# Cuetree API — progress and checkpoints

Indexer.scan(..., onCheckpoint) calls (ScanProgress, () -> Snapshot) synchronously. The supplier is
lazy; repeated calls within a callback return the same validated cohort. Request it when the host
publication deadline is due.

```kotlin
enum class ScanStage { DISCOVERY, ACQUISITION }
enum class ScanCheckpointKind { MEDIA, EXPLICIT_TABLE, DIRECTORY, STAGE }
data class ScanProgress(
    val completedMedia: Long,
    val completedDirectories: Long,
    val completedCueSheets: Long,
    val kind: ScanCheckpointKind,
    val factsRevision: Long = 0,
    val stage: ScanStage = ScanStage.ACQUISITION,
    val totalMedia: Long = 0,
)
```

DISCOVERY is complete L0 enumeration and opens no media. DIRECTORY follows each completed directory,
including roots. STAGE announces ACQUISITION after all L0 listings. TotalMedia is zero during L0 and
the fixed discovered media workset count afterward. MEDIA follows each consumed L1 media candidate,
including success, completed failure and an unchanged FAILEDL1 skip; EXPLICIT_TABLE follows ready
external CUE or titled embedded membership. Counts mean checked work, not accepted playable sources
or durable completion fields. Core estimates neither rates nor deadlines.

FactsRevision is a transient conservative assessment token. It advances when changed facts,
structural evidence or table readiness can affect an offer, including known absence at the L0/L1
barrier. Equal values mean no new input since the host assessment. It is not a stored generation and
must not be compared across scans.

L0 offers retain previous coherent catalogs and unvisited facts/availability. New media have no
playable provisional identity. L1 offers regroup coherent acquired/reused facts. External tables are
ready when all referenced bindings have completed valid acquisition. Unfinished changed bindings
retain unavailable old facts and their old successful stat; provider failure is not successful empty
enumeration. Later acquisitions can change provisional grouping.

Return true only after committing the requested cohort; Cuetree then advances generation. A
publication failure aborts the scan while accepted pointers stay unchanged. Offers never mutate the
running scan. Final scan completion returns a separate factual candidate; core neither writes it nor
forces a stage-boundary flush.
[Riff's cadence](/Riff/LATEST/immutable-publication#publication-cadence) governs all L0/L1/L2
streams; core owns no timer or publisher.
