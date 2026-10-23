# Album V2 — RC 261013 delivery

This evidence records implementation and bounded verification of the adopted
[Album V2 contract](/Riff/LATEST/album-grouping). It does not change its rules.

## Implementation

Cuetree resolves global candidates before provisional folder/artist groups, accepts numbered partial
albums and unnumbered same-year compilations, treats missing fields as neutral, corroborates
terminal Album disc suffixes, and guards independent releases and placeholder tags. Copy/position
resolution has one owner. MDIA retains bindings; ALBM preserves admitted positions. The Android
projection no longer removes ordinary positions by audio ID. Direct album queues receive the
retained multiplicity.

Catalog building, parsed evidence, disc families, logical positions, release guards, residual scores
and complete linkage use separate source files. Complete linkage retains linear working storage
instead of a full pair matrix. Ambiguous components do not stop unrelated merges. Cancellation is
checked throughout substantial work. Only metadata already available in MDIA is used.

The exact [Cuetree format](/Cuetree/persistence#mvp-format) is 10. Earlier versions are rejected;
the existing Riff host performs a full automatic derived-library reset and ordinary L0/L1/L2
restart. No migration, old-format reader, partial regrouping or new stored flag/field was added.

Desktop JSON export escapes literal controls left by flatc natural UTF-8 output. Schema conversion
still uses the packaged schemas and pinned converter; decoded text and 64-bit identity values are
preserved.

## Verification

Passed 124 Cuetree core, six desktop CLI, 162 Indexer, 297 Player and 27 configuration JVM cases,
with Kotlin checks and signed release verification. The focused fixtures cover global partial and
unnumbered compilations, disc spellings/suffixes, unknown-field bridges, copies, repeated physical
positions, local slot conflicts, independent concerts, overlapping editions, placeholder tags,
rejected embedded CD markers with no numeric-fallback bypass, and an ambiguous component beside an
unrelated merge.

Large host fixtures cover 125,001 structural members and 10,000 residual-folder members, plus
cancellation. These are bounded host correctness checks, not Android throughput or exhaustive
residual performance measurements.

The actual policy was replayed on private metadata fixtures from the previously validated full
exports. Breakbeat Kaos forms one seven-position album; Lost In Translation-OST forms one
12-position album; five distinct Greatest Hits groups remain separate. Independent HU concerts and
overlapping editions stay separated. The fixture loader preserves full wire keys and extension
attributes. An initial diagnostic run using convenience aliases was rejected and excluded from
evidence. No old database was converted or copied into an active store.

A disposable HU instrumentation test passed for version 9 rejection, delayed journal ACK before
deletion, full reset, preserved Player/source sentinels and generation watermarks. It never targeted
the working package.

Official-source review on 2026-10-13 used
[AAOS media support](https://developer.android.com/training/cars/media/automotive-os) and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality). The
existing Play/Add source policy changes multiplicity without adding controls, focus, IME or motion
exceptions. Existing mode/mutation and supported-host boundaries remain. Local queue tests do not
establish live vehicle-host conformance.

## Installation and live scope

Installed signed 0.1.2-rc, versionCode 5, RC 261013 on both selected devices. The installed APK
exactly matches SHA-256 4696cd8fff0b63a5d8b4ff8bd14c552b674ed56632072b000f7f75098b5ea7f6.
Certificate continuity, package UID, first-install time and the exact persisted grants remain
intact: two phone grants and one HU grant. No package uninstall, app-data clear or manual reset was
used. Independent Player backups were retained locally.

The first format-10 deployment triggered automatic full recreation. A final in-place queue
correction retains compatible format-10 facts, without another forced reset. The working factual and
Player cohorts were exported after automatic recreation and validated with the production format-10
reader and desktop exporter. All 16 JSON companions parse strictly.

| Device | Factual / Player generation | Format | Nodes | Nonempty albums |
| ------ | --------------------------- | ------ | ----: | --------------: |
| Phone  | 340 / 340                   | 10     | 2,831 |             123 |
| HU     | 551 / 551                   | 10     | 3,325 |             355 |

These are fixed interim L1 observations, not final library counts. Accessibility status shows the
full 10,414-source phone and 7,953-source HU worksets with current reporting-period rates. Both
Player and Indexer are alive. No Riff fatal exception or pipeline failure was found in the bounded
observation; an unrelated system-app exception is excluded from that claim.

Full on-device L1/L2 completion, final album census and representative audible playback remain
separate verification. No screenshots, Git history operation or website update was performed. Exact
artifacts, private fixtures, backups, test logs and publication receipts remain on the development
host under status/album-v2-implementation-261013/.
