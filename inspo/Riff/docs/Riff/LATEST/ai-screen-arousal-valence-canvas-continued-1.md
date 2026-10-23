# Riff AI_SCREEN contract — 4. Arousal/valence canvas — continued 1

> Owner/scope: bounded section of Riff/LATEST/ai-screen

## 4. Arousal/valence canvas — continued 1

- The canvas has exactly three permanent trust overlays plus the independently optional status line.
  A is left-aligned in the physical upper-left corner, V is right-aligned in the physical
  lower-right corner, and the exact advisory AI can make mistakes. is centered at the physical
  bottom; each uses the same fixed 16dp inset from every applicable outer edge. The optional status
  line is centered at the physical top with the same 16dp top inset. The visible overlay set
  contains only these four roles; no additional axis label or decoration is rendered.
- The selected-point marker and crosshair use opaque `accentActive` with the shared contrast halo.
  Canvas focus uses the same full-target focus role and outline adapter as other local controls.
- Ready paint order is exactly current-theme background, alpha-mapped per-cell fragments of the one
  continuous four-way gradient, crosshair/selected-point marker, then the visible text overlays. All
  four text roles are therefore topmost and do not participate in raster generation or touch
  mapping.
- One shared renderer governs A, V, AI can make mistakes., and the optional AI status line. All use
  regular monospace 20sp, opaque black foreground by day and opaque white by night, with the
  [shared contrast halo](ui-contrast-halo.md). SYSTEM follows the resolved Player theme. A, V, and
  the advisory are always composed, noninteractive, non-focusable, absent as separate semantic nodes
  and live regions, non-scrolling, and bounded to one line. The advisory remains exposed once
  through the canvas accessibility description. Global file-only behavior.enableStatusLines controls
  only the status line: when true, that line is formatted with Locale.ROOT as A %.02f V %.02f C %2d,
  centered at the canvas top, and updated for every pointer/accessibility change; when false, only
  its complete foreground and complete halo are omitted. It never renders, prioritizes, appends, or
  exposes Indexer Binder status; Indexer progress belongs only to MAIN_SCREEN. C is the current
  cell's distinct candidateCount after availability/playability and effective-Ban filtering, before
  PTE preference, weights, sampling, or truncation. No Settings control exists.
- Accessibility exposes the current values, the advisory AI can make mistakes., and separate bounded
  actions to increase/decrease Arousal and Valence without requiring a precision pointer gesture.
  Each action moves by one tenth of the current visible span on its axis and clamps to the viewport.
  Keyboard/rotary focus and activation remain available through the same semantic node; the four
  visible overlay layers do not create duplicate focus nodes.

## 5. Generation and materialization boundary

- UI and controller code MUST call one plainly separated toolkit-neutral generator function with
  exactly the current `size`, PTE-band set, `arousal: Float`, and `valence: Float`. Candidate
  acquisition is injected into the generator boundary; Compose, Android View, Canvas, Media3,
  SQLite, and navigation types MUST NOT enter the generator implementation.
- The generator implements [Automatic selection](player-automatic-selection.md). It validates the
  four request arguments and one injected immutable selection snapshot, computes the configured
  deterministic coordinates and complete candidate log weights, and samples the finite queue without
  replacement. Generation runs off the main and playback-critical threads and triggers no scan,
  Indexer work, feature extraction, Level 2, or live queue refill.
- Play first freezes one generator result, then durably materializes it as one finite temporary
  `PlaybackQueueRevision` of kind `AI`, preserving returned order, before atomic activation and
  Media3 handoff. Failure or an empty result leaves the prior active queue unchanged. The AI status
  line is display-only and does not enter generator input.
- Add generates and freezes exactly one result before opening the existing playlist selector.
  Selecting an existing destination or creating a new one atomically appends that exact ordered
  result under [Profiles and queues](player-queues.md) and opens the committed destination Playlist
  at page one. Returning from the selector without committing changes no playlist and returns to
  AI_SCREEN with the draft preserved. Add never starts playback or regenerates after destination
  selection.
