# Riff AI_SCREEN contract — 6. Geometry, accessibility, and verification

> Owner/scope: bounded section of Riff/LATEST/ai-screen

## 6. Geometry, accessibility, and verification

- All non-canvas controls use the current configured physical tokens, core playback targets are at
  least `76dp`, other targets are at least `64dp`, and neighbouring targets preserve the normal
  `24dp` gap. Content remains inside safe insets in WIDE, NARROW, runtime resize, RTL, font scale
  `2.0`, day, and night. WIDE places canvas and controls in one body row and derives both from that
  row's available height: the controls are two three-choice rows over Play, the three cells in every
  row have equal width, each row and Play fill the identical control-column width, every selector
  and Play has the same height, and the common body measurement makes canvas and Play bottom edges
  equal without a cross-component anchor. NARROW gives the square canvas the complete safe-content
  width and uses the remaining height below one standard gap for a full-height lower strip. After
  subtracting the one standard inter-block horizontal gap, the physical-left six-choice grid and
  physical-right Play partition available width `2:1`; the grid contains two equal-height rows of
  three equal-width controls, all six are equal-sized, and grid and Play share the strip's complete
  height. This exact `2:1` safe-content ratio is the sole section-10 percentage exception. The six
  selector glyphs and Play glyph use exact `10dp` body-button padding, a square viewport derived
  from each padded target's shorter dimension, and centering on its longer dimension; every control
  bound, touch target, gap, and semantic action remains unchanged. A narrower phone window stacks
  the same bounded controls only when the split cannot preserve target minima. Neither responsive
  form may expose vertical or horizontal scrolling.
- The canvas remains large enough for gross motor use, but exact selection is never required because
  every point is valid and accessibility actions provide bounded steps. Labels, selected state,
  enabled state, action order, and current values have localized semantics.
- Tests MUST cover initial profile defaults, restoration across screen/process recreation and saved
  profile switches, absence of stored uncommitted generated queues; every PTE-band subset including
  none/all; exact single-size invariant; tap/drag/clamping and all four viewport corners; exact
  data-fitted bounds, padding, minimum span, domain-edge shifting, empty fallback, selection
  anchoring, forward/inverse coordinate mapping, marker remapping after a bounds change, and RTL;
  physical-right interactive `AI` title plate in both orientations and after repeated rotations;
  shared plate presentation, one accessible action target and absence of a separate ellipsis;
  plate/menu/Back draft preservation; no plus/minus; exact default and boundary heatmap
  configuration; complete day/night corner-color and seven-alpha-map validation; MDRG validation,
  sparse current-ID-subset acceptance, unresolved/expired MediaID omission and retained SHRT
  resolution, and Genre-projection isolation; playable/effective-Ban/distinct filtering; A/V bin
  boundaries including exact `+1`; exact candidate counts; logarithmic boundaries through `129`;
  continuous per-pixel bilinear gradient; empty alpha `0x3F`; four transparent corner pixels and
  `2x2` shared nodes; RTL mirroring; exact paint order; off-main cached construction/rasterization
  and live invalidation; retention of the valid old raster, atomic raster/bounds replacement,
  stale-selection rejection, stable canvas and sibling-control geometry, immediate exact SMPTE
  placeholder, `4:3` aspect, square-viewport centering, theme-following letterbox, atomic
  replacement, unavailable-data retention, exact zero A/V/C status, blocked pointer/accessibility
  selection, disabled Play, controller rejection of stale commands, and normal restoration when data
  becomes ready; one shared visible-text renderer; permanent A at physical upper-left, V at physical
  lower-right, and exact bottom-centered advisory; optional top-centered status only; identical 16dp
  edge insets, regular-monospace 20sp, opaque day/night foreground and shared glyph halo; retained
  full accessible A/V names and one advisory announcement; enabled/disabled status with exact A/V/C
  formatting and candidate-count updates; complete absence of Indexer text and of only the status
  overlay when disabled; exact shared text layout, single accessible semantics for each control, and
  file-only configuration; equal-width WIDE selector rows and Play, equal-height WIDE controls,
  common canvas/Play bottom edge, full safe-content-width NARROW canvas, exact NARROW `2:1`
  grid/Play partition after gaps, equal six-control geometry and shared lower-strip height; exact
  `10dp` control padding and short-side square glyph viewports with unchanged control/touch bounds;
  absence of screen scrolling; direct Play/title-plate-menu Play identity; one generator invocation
  per command; exact four-argument request and immutable injected snapshot; input/configuration
  validation; deterministic seeded weighted tests; score-coordinate, confidence, PTE
  partition/gate/maximum-two, reaction aggregation, folder/history decay,
  distinct/banned/unavailable/short-library behavior; empty/failure preservation; durable AI
  activation; Add freezing without playback; selector cancellation; new/existing destination commit;
  system Back; target bounds; semantics; WIDE/NARROW; font scale; day/night; and the
  application-mode matrix. Weighted-selector evidence does not prove Indexer model quality or
  training completion.

[TL;DR](/Riff/LATEST/tldr) owns current implementation evidence and remaining gaps. UI and selection
checks do not establish model quality or automotive host conformance.
