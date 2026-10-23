# Riff AI_SCREEN contract — 4. Arousal/valence canvas

> Owner/scope: bounded section of Riff/LATEST/ai-screen

## 4. Arousal/valence canvas

- The selector is one square direct-manipulation canvas with a continuous four-corner color field.
  Arousal and Valence selections remain finite normalized values in `[-1f,+1f]`; the ready canvas
  displays a data-fitted viewport within that domain instead of requiring both complete axes to
  remain visible. Valence runs from the viewport minimum at logical START to its maximum at logical
  END. Arousal runs from the viewport minimum at the bottom to its maximum at the top.
- Tap and continuous drag clamp to the current viewport and update both Float values through its
  linear mapping. The selected point is shown by high-contrast crosshair/marker geometry that
  remains visible in both themes and every corner. Whenever viewport bounds change, the numeric A/V
  selection is preserved and the marker is remapped to that same selection in the new coordinates.
- A live density heatmap uses only current validated MDRG A/V rows resolved by exact Cuetree MediaID
  or retained SHRT alias to distinct currently playable, non-effectively-banned Music candidates.
  MDRG generation matches its own immutable descriptor and is independent of the Cuetree generation.
  The analysis snapshot is sparse; unresolved or expired identities contribute no row and do not
  invalidate unrelated results. Exact full-key records take precedence over a short alias.
  Confidence, PTE, preferences, probabilistic weighting and requested queue size do not change
  membership or bin count. Construction runs off main/playback-critical threads and refreshes when
  accepted MDRG, Cuetree/playability, active profile or effective Ban changes.
- Genre decoding, GNRE equivalence, and Genre-list projection are not heatmap-readiness inputs. A
  malformed, missing, unknown, or standalone Genre mapping MUST NOT discard otherwise valid A/V
  rows, replace a valid heatmap with SMPTE, or delay an A/V refresh. Only the scalar MDRG and
  MDIA/playability validations named above control heatmap eligibility.
- Each ready axis viewport is derived independently from the eligible distinct points plus the
  current numeric selection as an anchor. Let `rawMin` and `rawMax` be their extrema,
  `baseSpan = max(rawMax - rawMin, 0.10)`, and `displaySpan = min(2.0, baseSpan * 1.10)`. Center
  that span on `(rawMin + rawMax) / 2`, then shift the complete interval without shrinking it until
  it lies inside `[-1,+1]`. Thus ordinary data gets five-percent padding on each side, identical or
  near-identical values retain a deterministic minimum extent, and domain-edge data remains visible.
  With no eligible point, the viewport is exactly `[-1,+1]`.
- A valid old raster and its bounds remain visible while a live replacement builds. The complete new
  raster and both bounds replace them atomically, with no blank, partial frame, fade, animation, or
  canvas-geometry change. If the numeric selection changes while a replacement is building, that
  stale result MUST NOT be installed; construction is repeated using the current selection anchor.
  Only the raster, viewport mapping/bounds, marker position, and current-cell candidate count may
  change on this update. The canvas outer bounds and every sibling selector, Play action, and
  status-control position remain unchanged.
- From first composition until the complete current heatmap pixel raster is ready, the canvas
  immediately displays the exact untinted `ic_smpte_bars` configuration drawable supplied by the
  user. Its `4:3` image fits the square viewport width without crop or stretch, is vertically
  centered, and has equal top/bottom letterbox bands in the current resolved AI-screen background
  color. Day, night, and SYSTEM use the same Player theme resolver. The first complete heatmap
  atomically replaces the placeholder with no blank or partial frame, fade, animation, or geometry
  change. Missing, invalid, or not-yet-current MDRG retains the placeholder and triggers no Indexer
  work, scan, or inference. While no ready current usable heatmap exists, the SMPTE placeholder is a
  non-interactive no-data state: effective displayed A/V is 0.00 / 0.00, the optional status remains
  A 0.00 V 0.00 C 0, pointer and accessibility A/V changes are disabled, Play is disabled, and the
  controller rejects point-selection and AI-queue-play commands. PTE, size, navigation, and
  playlist-add behavior remain available.
- Horizontal bin count is file-only `ai.heatmapValenceCells`; vertical bin count is file-only
  `ai.heatmapArousalCells`. Each is an integer `1..128`, defaults to `32`, and has no Settings or
  profile surface. Bins are equal within the current per-axis viewport. Valence follows logical
  START-to-END and mirrors in RTL; arousal follows bottom-to-top. A value at a viewport maximum
  clamps to the final bin. Proportional integer pixel boundaries cover the exact canvas and may
  differ by at most one physical pixel when an extent is not divisible by its bin count.
- The four registered colors are anchored to the absolute A/V domain corners `(-1,-1)`, `(-1,+1)`,
  `(+1,-1)` and `(+1,+1)`. For each physical pixel, resolve its actual A/V from the current fitted
  viewport, then sample that domain-wide bilinear field. A viewport change never recolors the same
  A/V value; RTL changes placement only. A fitted viewport shows its corresponding portion of the
  field. Density alpha and cell-corner omissions remain independent.
- Normative day corners are top-right `#C58C24`, bottom-right `#2C8D76`, bottom-left `#456FA8`, and
  top-left `#BD596A`. Normative night corners are top-right `#B49A5A`, bottom-right `#518D7B`,
  bottom-left `#617EA5`, and top-left `#AC687A`. Logical positive/high-arousal is amber,
  positive/low-arousal teal, negative/low-arousal blue and negative/high-arousal coral. The eight
  values live in complete day/night branches of file-only `ai.heatmapColors`; no Settings surface
  exists.
- Every bin, including an empty bin, draws a clipped fragment of one canvas-wide bilinear gradient
  sampled at every physical pixel's logical A/V coordinate. For `candidateCount == 0`, the
  logarithmic index is `NaN` and alpha is `0x3F`. For a positive count, let
  `L = ceil(log2(candidateCount))`: indices through `3` use `0x5F`, then `4 -> 0x7F`, `5 -> 0x9F`,
  `6 -> 0xBF`, `7 -> 0xDF`, and `8+` uses `0xFF`. The corresponding count ranges are `1..8`,
  `9..16`, `17..32`, `33..64`, `65..128`, and `129+`. File-only `ai.heatmapAlphaMap` provides
  exactly these seven alpha bytes in semantic order and has no Settings surface.
- The four one-physical-pixel corner samples of every cell always use alpha `0x00`, including empty
  cells; adjacent cells therefore expose `2x2` theme-background nodes. No line, stroke, flat
  per-cell fill, per-library normalization, or animation is added.
