# Riff AI_SCREEN contract — 1. Scope and entry

> Owner/scope: bounded section of Riff/LATEST/ai-screen

## 1. Scope and entry

- `AI_SCREEN` is the app-owned local finite-queue entry surface opened only by the MAIN_SCREEN AI
  action. In MUSIC, that action is enabled only after current validated MDRG supplies at least one
  usable A/V pair and the corresponding heatmap is ready; missing, invalid, empty, PTE/Genre-only or
  pending data keeps the stable launcher disabled and blocks programmatic entry. AUDIOBOOK always
  disables the launcher. A screen already open when readiness is lost falls back to the existing
  no-data state. AI_SCREEN is not a hierarchy category, Settings entry, live recommendation stream,
  Indexer screen, or AAOS host replacement.
- Automotive and phone use one responsive Compose implementation. Real AAOS and projected Android
  Auto retain supported host-rendered media surfaces under the application-modes contract.
- Queue size, the three PTE toggle states, Arousal and Valence are durable controls of the active
  profile. A profile with no stored value starts at queue size `20`, no PTE band selected,
  `arousal = 0f`, and `valence = 0f`; every accepted control change updates that profile state.
  Returning through an AI action/playlist child, leaving through Cancel, system Back, MAIN,
  completed Play or committed Add, process recreation and later fresh entry all restore the same
  controls. A saved profile switch loads the selected profile's controls. No generated candidate
  list or uncommitted AI queue is part of this state; Play and Add retain the materialization rules
  in section 5.

## 2. Stable screen actions

- The fixed top bar exposes icon-only `Cancel AI queue` using `ic_cancel` at physical left and one
  interactive `AI` title plate at physical right, inside safe content. The plate uses the shared
  breadcrumb/control surface, outline, chrome typography and `15dp` padding, with one accessible
  button named `AI actions`. Its full plate is the action target. Portrait, landscape, WIDE, NARROW,
  runtime resize and RTL retain these physical edges; no free-standing AI title or separate
  `ic_dots`/ellipsis action exists. No Settings, Home, Parent, or hidden placeholder exists. Cancel
  and system Back are identical and return to MAIN_SCREEN without generating, persisting, or
  activating anything.
- The primary body contains, in logical order, the arousal/valence canvas, three perceived-speed
  choices, three queue-size choices, and one icon-only `Play AI queue` action using `ic_play`.
  Responsive reflow may place the canvas and selectors side by side in WIDE and place a compact
  selector/Play block below the full-width canvas in NARROW, but may not change action meaning,
  grouping, or selector state. The screen itself does not scroll.
- Activating the `AI` title plate opens one bounded AI action child with exactly `Play` first and
  `Add to playlist` second. Child Cancel/Back returns to AI_SCREEN and preserves the draft. The
  direct Play button and child Play invoke the identical request once.

## 3. Perceived-speed and size selectors

- Perceived speed is represented by three bistable icon-only PTE-band toggles: Slow uses
  `ic_tempo_slow`, Medium uses `ic_tempo_normal`, and Fast uses `ic_tempo_fast`. Initial profile
  state selects none. Zero, one or two selected bands are legal; all three is not. When two are
  selected, the remaining unselected control is visibly and semantically disabled. Clearing either
  selected band immediately enables it again. The retained `ic_tempo_*` filenames are immutable
  asset identifiers and do not give PTE a BPM or beat-count meaning.
- PTE controls are available only when the current eligible distinct library yields all three valid
  PTE groups with at least the configured `ai.selectionModel.pteMinimumGroupSize` observations each,
  default `42`. While the gate is closed all three controls are visibly and semantically disabled,
  their retained profile bits remain unchanged, and PTE contributes nothing to selection. Reopening
  the gate restores the retained zero/one/two selection. An invalid all-three selection is
  normalized deterministically by dropping Fast before exposure or persistence; this is current
  value validation, not an old-schema reader.
- Queue size is one mandatory single-selection group with exact values `10`, `20`, and `50`; initial
  profile state is `20`. It uses the exact existing `ic_scale_eur_10`, `ic_scale_eur_20`, and
  `ic_scale_eur_50` drawings, with queue-cardinality accessibility names and no currency semantics.
  Selecting another value replaces the prior value; activating the selected value does not clear it.
- Selected state uses both visual and semantic state and cannot depend on color alone.
  Perceived-speed and size controls remain in stable positions while their state changes.
