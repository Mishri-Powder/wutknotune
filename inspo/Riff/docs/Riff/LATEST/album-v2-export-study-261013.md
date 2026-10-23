# Album V2 — exported-library study, 2026-10-13

This is non-normative evidence for [RIFF-ALBUM-V2](/Riff/LATEST/album-grouping). Production grouping
and installed application APKs remain unchanged. The user adopted the study clarifications on
2026-10-13; their owners are linked from the grouping registry. This study records the earlier
base-V2 model, not a completed implementation or rerun of every newly adopted guard.

## Inputs and validity

Exported the working factual and Player-projection CURRENT cohorts from both devices. The phone uses
its selected external data root; HU uses Internal storage. An old HU external copy was rejected as
stale and excluded. Authorized adb root supplied HU access; phone root was not used.

The production SnapshotStore validated manifest hashes, lengths, references and header
compatibility. The packaged Cuetree JSON exporter produced the JSON companions. No source rescan,
audio read, library reset or format migration was performed. These are fixed snapshots, not a claim
about a later live generation.

| Observation                                          |    Phone |      HU |
| ---------------------------------------------------- | -------: | ------: |
| Publication generation                               |      335 |     545 |
| Format                                               |        9 |       9 |
| MEDIA sources                                        |    6,389 |   7,951 |
| FAILEDL1 sources                                     |    4,025 |       2 |
| Directories                                          |      957 |   1,293 |
| FILF nodes                                           |      114 |       0 |
| FAILEDL2 sources                                     |        3 |       0 |
| Nonempty ordinary albums                             |      512 |   1,099 |
| Ordinary one-source albums                           |      267 |     345 |
| Distinct ordinary titles                             |      293 |   1,051 |
| Repeated titles / their albums                       | 14 / 233 | 19 / 67 |
| Explicit albums                                      |        1 |       0 |
| Singles source positions                             |    2,785 |      92 |
| Retained empty albums, excluded from grouping counts |       24 |       3 |

All Singles positions have blank/absent Album. None was silently omitted from a same-Album
candidate. Physical occurrences, distinct MediaID and logical album positions are different
quantities.

The factual and Player nonempty catalog IDs/titles match exactly: 514 and 1,100 catalogs
respectively. The current Android projection deduplicates non-FILF positions by MediaID inside each
album. Ordered projection membership matches that rule for every catalog. It removes 68 occurrences
in six phone albums and 13 in nine HU albums. It does not join already fragmented albums. Thus the
observed album multiplication originates before Player navigation.

## Rule-model results

A metadata-only host model evaluated whole-candidate proofs and reported unresolved partitions
rather than inventing a complete replacement catalog. It covered all ordinary title buckets and
separately audited Singles, chapters, folder families and source failures.

| Result                                                   | Phone |    HU |
| -------------------------------------------------------- | ----: | ----: |
| Buckets with a decisive model result                     |   283 | 1,030 |
| Buckets deferred to ambiguity or chapter ownership       |    10 |    21 |
| Repeated-title buckets with an unambiguous consolidation |     7 |     5 |
| Album-entry reduction within those consolidations        |    55 |    22 |
| Same-title repeated buckets preserved by artist evidence |     1 |     4 |

These are model findings, not complete future catalog totals or verified ground truth for every
release. The model does not implement the full iterative residual partitioner, every optional
metadata feature, final IDs, presentation or production cancellation.

Breakbeat Kaos groups globally despite partial disc coverage and one missing Year. Lost In
Translation-OST groups through unnumbered-compilation evidence; its existing artist-only subgroup
does not block the remaining soundtrack. Contradictory optional totals need not split an album when
independent residual evidence still reaches the threshold.

Replay through the existing production Cuetree policy exactly reproduced the stored partitions for
all 14 phone and 19 HU repeated-title buckets.

An important counterexample contains two separate one-track concerts with equal Album, Album Artist
and Year. Their observed residual score is 77, above 65. A concrete competing-edition guard is
needed; merely adjusting missing-value scores does not solve it. Other buckets contain overlapping
full editions, same-position copies, isolated numbering errors and placeholder tags.

## Disc-family audit

The folder-name parser finds five phone families and 26 HU families, including real four-disc and
five-disc sets. Four phone name families have no admitted MEDIA; these counts are folder
observations, not 31 successfully reconstructed multi-disc releases.

One phone family and 12 HU families have different Album values across their discs. Removing only a
terminal disc marker yields a common comparison base for the phone family and eight HU families. The
other four HU families have substantive subtitle/edition differences. Current equal-Album candidate
admission does not merge any of these merely because their folders form a family.

Two phone and 43 HU CD-like names fail the standalone ordinal syntax. Examples of rejected syntax
include embedded myCD1 and CD 3T, where a trailing letter must not be discarded as if it were CD 3.
No generic numeric fallback should accidentally restore an explicitly rejected disc interpretation.

## Verification and limits

The model passes 660 shuffled-order comparisons across all 33 repeated-title buckets, nine disc
syntax assertions and 12 synthetic ambiguity/structure cases. Structural-only host runs cover
10,000; 125,000; 125,001; 200,000; and 200,001 members. This is not a device benchmark, an
end-to-end Cuetree benchmark or proof of residual scalability. A 125,000-member all-pairs matrix
contains 7,812,437,500 pairs; production needs bounded candidate processing without such a matrix.

The phone's raw JSON export contains unescaped U+0018/U+001D in string data, causing strict JSON
parsing to fail in six companions. Binary validation passes. Analysis retained raw files and used a
permissive-control-character parse with lossless reserialization and exact integer IDs. All eight HU
companions parse strictly. Correct escaping in the desktop exporter is a separate implementation
defect; source tags must not be silently rewritten to hide it.

A temporary signed instrumentation exporter was removed after HU access was established, its own
temporary output was deleted, and the working application was reopened. The main APKs and library
contents were not replaced. No screenshots were taken.

Exact exports, counts, private per-title tables, model sources and verification receipts remain on
the development host under status/album-v2-export-study-261013/. Public evidence excludes device
identifiers, source paths and the full private library inventory.
