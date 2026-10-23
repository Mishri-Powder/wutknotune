# ReplayGain analysis procedure

> Owner/scope: Indexer ReplayGain workset, measurement and dependency procedure

## RG-5. Measurement and dependencies

### Workset and no-work rules

Work is keyed by Media.ID plus the selected source facts and parser/decoder/analyzer dependencies,
not merely physical path creation. Reuse current valid results for another copy of the same
Media.ID. New or changed sources, observed dependency invalidation, interrupted pending work,
missing/corrupt MDRG and explicit shared AU rebuild may require acquisition even when no new
physical file appeared. A clean unchanged library with no unresolved work performs no gain source
I/O, launches no L2 and publishes no MDRG. Reference cleanup after authoritative Media removal may
publish changed retained results without analyzing audio.

After L1, L2 admits only technically eligible records with no usable Track Gain and no applicable
completed result or deterministic failure that suppresses retry. A real zero gain is usable.
Album-only/peak-only external data is not a usable candidate; L2 measures Track Gain without
carrying those fields into the measured candidate. Do not retry an unchanged deterministic failure
or completed no-result item on every startup. A recorded L1 content failure is not an eligible
source; an optional gain failure does not mark an otherwise playable binding ScanFailed.

Cancellation remains unfinished work. Any completed unsuccessful source attempt, including access or
handled resource failure, sets physical FAILEDL2 under
[source failures](/Riff/LATEST/indexer-source-failures), preserving independent valid outputs and
playability. Equal source attributes suppress retries; another copy may supply the shared result. AU
clears FAILEDL2 and its analysis recovery scope. Changed metadata rules require full index
recreation; no partial migration occurs. Empty eligible worksets do not create a decoder/analyzer or
emit a fictitious analysis stage.

### Shared bounded window measurement

Use libebur128 through Indexer-owned NDK/JNI. It measures decoded PCM and adds no codec. L2 owns one
union workset: unresolved ReplayGain MediaIDs plus Music MediaIDs missing accepted MDRG AI.
Audiobooks participate in ReplayGain but never AI. Load the AI model only when the workset contains
AI work. A source decode needed by both outputs is performed once; tagged Track Gain can suppress
loudness calculation without suppressing required AI.

For each selected source with a duration `D`, retain the existing three logical starts at 25%, 50%
and 75% using `clamp(D*p - 5 s, 0, max(0, D - 10 s))`. Decode up to 20 seconds from each start.
ReplayGain consumes the complete decoded interval; AI consumes only its first 10 seconds, preserving
the existing model input size and ordering. Combine the three accepted libebur128 states with
`ebur128_loudness_global_multiple`; never average LUFS values. Do not calculate, persist or use
window spread.

A window is unusable only when libebur128 returns no finite integrated loudness because the decoded
interval is silent or insufficient. Retry that logical window at `start + 20 s`, then
`start + 40 s`, clamped to a valid source start. The original plus two forward one-window moves are
the complete bounded policy; do not repeat an identical clamped start. AI uses the first 10 seconds
of the accepted moved window. Ordinary decoding/content errors retain their typed
retryable/deterministic outcome and are not disguised as silence.

If Music still lacks any required finite window after the bounded attempts, run one streaming
full-span integrated-loudness fallback for ReplayGain. Immediately before it starts, Indexer
publishes `Full loudness analysis: <filename>` as bounded detail after the
[shared cumulative counts and period speed](/Riff/LATEST/indexer-staging#progress-and-period-rates)
through the existing status Binder; `<filename>` is the selected binding display name only, never a
path, URI or document ID. Restore ordinary progress/empty status after the file boundary. If an
audiobook remains unresolved after bounded attempts, never run the full-track fallback; complete it
without usable gain so Player uses the safe default. No full-track work is triggered by spread,
metadata peak or AI alone.

The Cuetree source-admission adapter persists one signed XXH3 DecoderHint per MediaID in MDRG from
the accepted audio Format using MIME-qualified framing. L2 groups the union workset by equal hint.
The hint is formed and checked only from L1 observations; L2 treats it as an opaque cohort key and
MUST NOT reproduce or compare the hash from MediaExtractor data.

At the first source of each hint, calibrate one runtime decoder plan from real access units. All
supported Android releases use the same asynchronous MediaCodec callback pump for both calibration
and production. API 35+ uses only stock candidates advertising `FEATURE_MultipleFrames` and submits
batches with `queueInputBuffers`; older Android submits a callback-provided contiguous multiple-AU
input buffer only after empirical verification. For each candidate compare ordered batch sizes
2/4/8/16/32/64 with that candidate's single-AU PCM format, byte count and XXH3-64, subject to 64 AU,
128 KiB and two-second limits. Choose the smallest batch within 95% of the candidate's best probe
throughput, then choose the fastest decoder under three-window parallel load. Capability metadata
other than the API 35+ MultipleFrames gate is advisory; real configure/decode equivalence is
authoritative.

Keep the chosen plan in memory for that L2 run. One active hint owns three reusable callback codec
sessions, one per window worker; flush and restart between files on every API, and release all three
at cohort transition. Do not keep a cross-hint decoder pool or LRU. A batched production failure
disables batching for that hint, recreates the cohort and retries the current file in single-AU mode
through the same callback pump. Non-tuned MIME types use the stock format-selected decoder in
callback-driven single-AU mode.

For work requiring loudness, each window acquires access units spanning at least one second before
the requested start and 20 seconds of requested data. If usable L1 Track Gain exists and the item
needs AI only, acquire one second of pre-roll plus exactly the existing 10-second AI interval, do
not initialize libebur128, and do not read/decode the second 10 seconds. Album Gain alone does not
suppress Track Gain measurement. AI-only input does not invoke loudness-based silence retries or
full-track analysis.

Decode with the stock Android/Media3 ecosystem. Keep memory bounded independently of source
duration, close source/JNI/decoder resources on every outcome, remain cancellable and off
UI/playback-critical threads, and subordinate analysis to audio continuity. Retain completed results
and distinguish retryable source/resource failure from deterministic content/no-result outcomes.
Dependency identity includes the window/acquisition policy, DecoderHint schema and pinned
decoder/analyzer revisions, so this incompatible pre-release change uses no migration or backward
reader.

Pin libebur128 v1.2.6 commit `67b33ab` and vendor its MIT-licensed source/header/COPYING. Build it
statically into the existing Indexer JNI library with `EBUR128_MODE_I` only; no peak mode, Speex or
codec is added. The analyzer identity is `LIBEBUR128-1.2.6-67b33ab-I-W20-R2-V4`; the stored
composite dependency also includes `PCM-AU-TIMELINE-V1`, so stale gain outcomes are reconsidered
after the source-AU timeline correction. Required HU evidence includes metadata and window paths,
bounded retry, music full fallback/status filename, audiobook no-full behavior, shared AI PCM,
CPU/memory, cancellation and playback continuity.

The [Verification status](/Riff/LATEST/worklog) is supporting evidence. The adopted policy uses
20-second loudness windows, first-10-second AI inputs, silence/non-finite retries only, and no
spread threshold.
