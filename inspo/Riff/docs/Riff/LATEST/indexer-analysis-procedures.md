# Indexer optional-analysis procedures

> Owner/scope: Indexer optional-analysis orchestration and bounded acquisition

## 5. Optional analysis stage

The Cuetree source-admission pass delivers current source handles and retained or newly acquired
Cuegrain external gain and one opaque DecoderHint from the accepted audio Format. These inputs are
stored as internal MDIA source evidence and reused when the source stat is unchanged. Later analysis
uses those admitted handles; it does not traverse SAF directories again. Unchanged media is not
reopened just to refresh ordinary metadata. Indexer then builds one union workset from unresolved
gain and Music AI identities in the current Cuetree snapshot. FILM and every FILF span are distinct
items. Books receive gain analysis only. A valid existing MDRG result is reused through the retained
SHRT alias after low-ID expansion. Missing or invalid optional MDRG never hides media.

The shared stage emits one [MDRG](/Riff/LATEST/format-mdrg) stream. Valid RIFF source pairs suppress
corresponding AI outputs; inference fills unresolved outputs without replacing accepted pairs. Books
ignore AI tags. A file needing both outputs decodes once. Indexer obtains access through a currently
available Cuetree physical source. The stored DecoderHint is an opaque cohort key; L2 does not
rederive it from extractor data. The first selected binding supplies real access units and its
format for in-memory decoder calibration.

Calibration discovers stock decoders for the actual MIME. Every supported Android release MUST drive
MediaCodec through the same asynchronous callback session for calibration and production.
Input-buffer, output-buffer, format-change and error callbacks feed bounded per-session queues;
window workers consume those queues and never mix callback and synchronous dequeue pumps. On API 35
or newer, candidates MUST additionally advertise `FEATURE_MultipleFrames` and submit a batch with
`queueInputBuffers`. On older Android, the callback-provided input buffer receives one empirically
verified contiguous multi-AU payload through `queueInputBuffer`. Every candidate first establishes
its own single-AU PCM baseline, then tests 2, 4, 8, 16, 32 and 64 AU batches in order. Stop a
candidate at the first error or mismatch in PCM format, byte count or XXH3-64. A production batch
MUST also fit the decoder input buffer, 64 AUs, 128 KiB and two seconds.

Choose the smallest passing batch whose probe throughput is at least 95% of that candidate's best.
Benchmark that choice with three parallel real windows and select the fastest candidate. Cache only
the resulting plan for the current L2 run. The active cohort owns three callback codec sessions, one
per window worker; configure once, then flush, call `start()` and reuse them for compatible files on
every API. Release all three at a hint transition. There is no resident cross-hint decoder pool or
LRU. If batched production decode fails, discard the three sessions, pin that hint to the selected
decoder's single-AU mode and retry the current file once through the same callback pump. Flush must
retire pending callbacks on their handler before clearing event queues and restarting, so an old
buffer or EOS cannot enter the next window.

Window trimming is anchored to the first extracted AU's source timestamp and counted decoded frames,
not the decoder's output timestamp. Approximate AU positioning is sufficient; some stock decoders
return inconsistent output timestamps across parallel sessions. Their timestamps must not discard
the entire requested PCM window.

Resolve bounded binding fallback and decode the three adopted windows once per item. When gain and
AI are both needed, each window acquires one second of pre-roll plus 20 seconds of data; ReplayGain
consumes 20 seconds and AI consumes the first 10. When usable L1 Track Gain already exists and only
AI is needed, acquire only one second of pre-roll plus 10 seconds of AI data and do not create or
feed libebur128. Group the union workset by DecoderHint, randomizing cohort order and per-directory
jobs within each cohort for each pass. Preserve codec reuse within a cohort. Isolated crash recovery
precedes normal pipelines and has no overlapping source or publication workers. One track-level
producer owns the decoder and prepares source resolution, the three parallel window decodes, and any
full-track gain fallback for each track. Its bounded queue overlaps preparation with the consumer's
AI frontend/inference, ordered result integration, progress and due publication. The maximum
in-flight track count includes the item being consumed and is configured by the file-only
[`indexer.pipelineDepth`](/Riff/LATEST/project-configuration) (default 3, allowed 1..8). This does
not change the three window codec sessions per track or allow parallel track-level decoder owners.
Pending preparations must be cancelled and their resources released on shutdown or failure.

MDRG keeps one complete snapshot and independent generation series. Publications share the L0/L1/L2
clock; entering analysis never starts another 30-second timer. Due publication copies the current
immutable record set to one serial writer. At most one publication is in flight: an overrun defers
the next due intermediate update instead of queuing stale cohorts. Decode and inference may continue
during the write. The final flush waits for every accepted publication; writer failures propagate to
the optional-analysis stage, and cancellation cannot promote an incomplete candidate. The writer
never traverses SAF or owns a decoder. Cancellation, decoder failure, shutdown, one-item/empty
worksets and checkpoints retain immutable-publication rules. There is no Level 3 stage.
