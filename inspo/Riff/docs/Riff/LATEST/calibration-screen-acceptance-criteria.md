# Riff CALIBRATION_SCREEN contract — 10. Acceptance criteria

> Owner/scope: bounded section of Riff/LATEST/calibration-screen

## 10. Acceptance criteria

- No `dp` literal, theme geometry token, `resources.displayMetrics` reference or absolute pixel
  dimension appears in this screen's source. Every dimension traces back to `W`, `H`, `S`, or
  `templateWidth`. Resolved shared color roles affect paint only and never candidate measurements.
- The screen lays out correctly at 800×480, 1024×600, 1280×800, 1920×720 and 3120×1440 without any
  resolution-specific branch.
- On a device reporting a deliberately wrong `densityDpi` (for example 160 on a 216 ppi panel),
  every control on this screen is still comfortably usable and the template still starts larger than
  a real card.
- Matching a real card in `CARD` mode and a real banknote in `US_NOTE` mode on the same device
  produces `ppiX` values agreeing within 3%.
- A fresh candidate has identical proposed X/Y DPI in portrait and landscape. Rotating
  mid-adjustment preserves candidate X/Y DPI, mode, and denomination while re-laying out the
  viewport.
- CARD always follows the current canvas orientation: landscape renders the 85.60 mm long edge
  horizontally and its magnetic stripe horizontally; portrait renders that edge vertically and its
  stripe vertically. The clipped stripe is 12.70 / 53.98 of the rendered short edge wide with its
  centre 12.00 / 53.98 inward from the seated long edge.
- Each mode-selector tap cycles exactly Card → US note → Euro note → Card. Changing mode or
  denomination resets the hatched region and does not carry over the previous size. No other element
  moves.
- Shrinking or expanding the sample changes live `X x Y dpi` but not the readout or center-bottom
  guidance geometry. Both remain below the translucent sample in the drawing stack; the solid border
  and controls remain above it.
- Note drag changes only the horizontal candidate in a wide/square viewport and only the vertical
  candidate in a tall viewport; the resulting note X/Y proposal remains equal, and both orientations
  use the same upper-left placement convention.
- Cancel leaves the parent draft and stored values untouched.
- Child OK returns a candidate only; storage and active density remain unchanged until
  SETTINGS_SCREEN OK.
- OK occupies the trailing edge of the bottom bar in both orientations; Cancel and OK are equal,
  each is exactly 1.5 times either equal selector width, and all four controls share one
  rounded-plate geometry. CARD/US_NOTE leave the denomination control empty, disabled and at 20%
  plate opacity; EURO_NOTE draws four opaque plates. Positions and hit bounds are identical across
  modes.
- An implied diagonal outside 4–20 inches is rejected with a message rather than stored.
- The screen is fully operable with colour vision simulated as monochrome, and with the feather tint
  set to any value the configuration allows.
- A CARD drag can expand the reference below the control row top to the padded viewport bottom.
  Control gaps and the inactive denomination plate reveal its hatch; opaque active plates cover it.
  Wide/square note modes expose their unmeasured full-height sample through the same gaps. A drag
  starting on a control or its disabled placeholder never resizes the reference.
- Exactly one hard-edged line exists per measured edge. A screenshot contains no second stroke,
  panel border, or container edge running parallel to it.

## 11. Icon status

The current registered icon corpus provides the exact CALIBRATION_SCREEN family.
`ic_scale_card_ruled` is the Card mode icon; `ic_scale_banknote` is used for both US-note and
Euro-note modes; `ic_scale_eur_5`, `ic_scale_eur_10`, `ic_scale_eur_20`, and `ic_scale_eur_50` are
the icon-only Euro denomination choices; `ic_scale_horizontal` and `ic_scale_bidirectional` are
diagnostics only; and `ic_arrow_up_dot` is the only long-tap hint where permitted. `ic_scale_card`
remains the SETTINGS_SCREEN Card-calibration entry and `ic_scale_iso_card` remains an adopted but
currently unselected reference icon. `ic_scale_coins` is reserved and MUST NOT be presented as a
calibration mode, payment action, or denomination selector. All icon-only controls require their
complete localized accessibility name.

## 12. Compose host and raw-pixel View boundary

CALIBRATION_SCREEN remains the sole specialized app-owned Android `View`/`Canvas` implementation
because its purpose is to measure a user-adjusted rectangle in raw physical display pixels before a
corrected density exists. The Player's single Compose root MUST host `CalibrationScreenView` through
exactly one `AndroidView` composable. The `AndroidView` is only a lifecycle and placement bridge:
all reference-object geometry, pointer handling, text placement, validation, and result calculation
remain inside `CalibrationScreenView` in raw pixels.

The bridge MUST pass the real activity context, current calibration input state, and typed action
sink. It MUST NOT wrap calibration in the corrected `LocalDensity`, translate the calibration
geometry to `Dp`/`Sp`, inflate XML, add a second composition root, or add another app-owned View
hierarchy. The existing acceptance criteria in this contract remain unchanged.
