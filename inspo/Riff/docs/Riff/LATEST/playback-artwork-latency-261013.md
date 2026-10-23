# Playback artwork latency — RC 261013

The user reported pauses on queue activation, automatic next and Forward, and authorized real tests
on phone and HU. The previous signed RC reproduced a 7.75-second Forward readiness delay on the
phone, followed by 1.46 seconds; two HU Forward samples were about 0.60 and 0.62 seconds.

## Cause and adopted correction

The production resolving datasource called PlayerArtworkLoader.prepare after SAF binding resolution
and before returning the audio URI. That method waited on the artwork worker's initial completion,
with a five-second timeout. Slow provider or embedded-image work therefore held audio source
opening. Artwork separately repeated the SAF path traversal. This was an explicit pre-audio artwork
gate in the previous UI contract, not an Indexer or album-format migration.

The user explicitly adopted
[independent artwork](/Riff/LATEST/ui-foundation#physical-ui-and-artwork). The blocking API and
completion future are removed. One existing service-owned resolver/cache requests bounded worker
work and immediately supplies the current placeholder/cache; Media3 continues with the resolved
audio URI. Current artwork updates later through the same owner. Exact
queue/occurrence/binding/source-observation keys, default policy, bounded cache, stale-result
fences, timeouts and at most two deferred retries remain. Identical binding metadata is no longer
replaced repeatedly on subsequent opens. Bounded source-ready, transport and playback-state log
milestones separate URI resolution from decoder/readiness and artwork completion without raw
locators.

## Verification

297 Player JVM cases pass, as do Kotlin style, Android compilation, release permission/model/license
checks and signed artifact verification. Eleven isolated Android cases pass on HU: the real
resolveDataSpec returns while an injected artwork provider is still blocked, later publishes the
same binding's bitmap, and existing recovery/cache/stale-result/timeout/retry and playback-history
cases retain their behavior. The disposable target is separate from the user's working package.

The new signed 0.1.2-rc, versionCode 5, RC 261013 is installed on both selected devices. Exact
installed bytes, the existing signing certificate, package UID, first-install time and persisted SAF
grants pass. Player backups are private. The APK SHA-256 is
2790dfd49e965a9b03a7f5397bbdb8bdfe6e697be86176921ecd5bbb9dafa491. No data-format change or library
reset is part of this correction.

Actual ordinary Album Play, two visible Next/FWD taps and a natural automatic transition after a
regular near-end UI seek pass on both devices:

| Device role | New queue: first PLAYING observed | Visible FWD samples |
| ----------- | --------------------------------- | ------------------- |
| Phone       | 705 ms                            | 219 ms, 560 ms      |
| HU          | 604 ms                            | 417 ms, 438 ms      |

These are command-to-first-observed MediaSession PLAYING upper observations with coarse ADB polling,
not a physical speaker-output benchmark or a controlled same-media before/after comparison. The
initial phone post-install empty temporary-queue probe is excluded; only actual playing queues enter
this table. New-queue runs had 13 and 17 occurrences. After the near-end seek had settled, the
genuine automatic occurrence transition retained playing state with no observed new buffering event.
Remaining cold SAF URI resolution measured 326–409 ms on phone and 121–174 ms on HU. This required
source I/O remains distinct from the removed artwork wait. Logs show audio playing before artwork
finishes on both devices. No playback/preparation/checkpoint error occurred in these bounded runs.
Both devices are left paused.

## Mode and evidence boundary

The engine change is common to phone, automotive and aaos. It adds no input, focus target, motion
exception, host template, animation or notification; artwork retains its existing geometry,
inert/default/theme rules and current-source fences. Review used official
[Android Automotive media guidance](https://developer.android.com/training/cars/media/automotive-os)
and
[media distraction safeguards](https://developer.android.com/training/cars/media/distraction-safeguards).
Local JVM, isolated HU and two direct-install device probes do not establish Android Auto/AAOS host
conformance, physical gaplessness, maximum-cardinality cold readiness or all-media performance.

Private probe XML/logs, original and updated APKs, preservation reports, exact commands and
publication receipts are under status/playback-gap-261013/. No screenshots were taken. The canonical
development repository's Git history is unchanged.
