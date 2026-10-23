# Riff CALIBRATION_SCREEN contract — 5. Layout

> Owner/scope: bounded section of Riff/LATEST/calibration-screen

## 5. Layout

Every dimension on this screen is a percentage of the framebuffer's pixel dimensions. **No absolute
pixel constant appears anywhere.** The resolution is not known in advance and must not be assumed —
neither a floor nor a ceiling.

Let `W = screenWidthPx`, `H = screenHeightPx`, `S = min(W, H)`.

| Element                            | Size                         |
| ---------------------------------- | ---------------------------- |
| Screen padding                     | 2.5% of `S`                  |
| Control height (bottom bar)        | 14% of `S`                   |
| Gap between regions                | 2% of `S`                    |
| Contour stroke                     | 0.8% of `S`                  |
| Hatch line width                   | 0.6% of `S`                  |
| Hatch period                       | 1.8% of `S`                  |
| Minimum clear margin at startup    | 12% of the canvas, each axis |
| Feather radius                     | 2.5% of `hatchWidth`         |
| Feather offset along measured axes | 1.2% of `hatchWidth`         |
| Corner radius, `CARD`              | 3.7% of `hatchWidth`         |
| Corner radius, note modes          | 0.6% of `hatchWidth`         |
| Heading text                       | 5.2% of `hatchWidth`         |
| Body text                          | 4.0% of `hatchWidth`         |
| Guidance and proposed-density text | 5.5% of `S`                  |
| Drag activation distance           | 1.0% of `S`                  |

Two of these are not arbitrary. The `CARD` corner radius of 3.7% reproduces the 3.18 mm specified by
ISO 7810 on an 85.60 mm edge, at any resolution and without knowing the density. The feather radius
of 2.5% of the hatched region conveys the tolerance, which is therefore a constant fraction of the
reference length rather than a constant physical distance — the right behaviour, since the error
that matters is relative.

Note the consequence of pure percentage sizing: on a physically small panel the controls may be
physically smaller. That is accepted for CALIBRATION_SCREEN. Reachability follows the registered
application-mode contract. This raw bootstrap layout remains an automotive deviation, is not
automatically AAOS-conformant, and is not a precedent for browse or playback surfaces.

### 5.1 Regions

There are two regions: the fixed canvas above the bottom bar and the bar itself. The reference
paints beneath fixed controls without an opaque full-width bar backing.

```text
┌──────────────────────────────────────────────┐
│ ▨▨▨▨▨▨▨▨▨▨▨▨▨▨▨▨▨│ │                        │
│ ▨ hatched region ▨│ │    clear canvas        │
│ ▨▨▨▨▨▨▨▨▨▨▨▨▨▨▨▨▨│ │    = touch surface     │
│ ──────────────────┘ │                        │
│                     │                  ⇱ hint│
├──────────────────────────────────────────────┤
│ [mode] [denomination]        [cancel] [OK]   │
└──────────────────────────────────────────────┘
```

**The complete work area above the bottom controls is the drag surface,** including the translucent
reference, guidance, proposed-DPI readout, padding, and visually clear area. It has no separate
border or fill. The bottom controls remain ordinary click targets and never initiate calibration
drag.

The common guidance says `Drag anywhere above buttons to resize`. No orientation branch or shrinking
clear-area target governs reachability.

### 5.2 Bottom bar

Full screen width, `controlHeight` tall, at the bottom.

- Leading: **mode selector**, then **denomination selector**. The mode selector uses exactly
  `[ic_scale_card_ruled] CARD`, `[ic_scale_banknote] $US`, and `[ic_scale_banknote] €`; it MUST NOT
  show the longer mode names. The denomination selector is icon-only in Euro mode and uses exactly
  `ic_scale_eur_5`, `ic_scale_eur_10`, `ic_scale_eur_20`, or `ic_scale_eur_50` for the selected
  value, with no visible text or Euro glyph. It is disabled and visually empty in Card and US-note
  modes. The empty CARD/US_NOTE plate uses the shared row-surface color at 20% opacity, retaining
  its inactive hit bounds. EURO_NOTE draws four opaque plates. The two selectors have equal compact
  widths and each receives 20% of usable width after gaps.
- Trailing: **Cancel** then **OK**, in that order, with OK flush to the right edge. Icon-only — a
  cross and a check. No text labels. They have equal widths and each receives 30% of the usable
  control width after gaps, making each action control exactly 1.5 times the width of either
  selector. Both require a `contentDescription`.
- All four controls use one consistent rounded-plate geometry derived only from raw framebuffer
  percentages and the resolved shared Player color roles. Icon-only action glyphs are centered;
  selector glyph-plus-text content is centered as a unit. No control may use SETTINGS_SCREEN theme
  geometry.

The confirming action takes the most reachable corner, which inverts the usual
destructive-action-last convention. It is justified here: this screen is reached only by a
deliberate path through settings, so a user who is on it intends to change the calibration. Cancel
is the rare outcome and does not need the best position.

Tapping the mode selector immediately cycles `CARD` → `US_NOTE` → `EURO_NOTE` → `CARD`; it MUST NOT
open a menu. Tapping the denomination selector in `EURO_NOTE` opens a list of the four euro values
and closes on selection. Denominations do not cycle on tap.

Changing mode or denomination resets the hatched region to its initial size for the new reference
(§4.3). Neither the canvas nor the bottom bar moves: no control may change position or size when the
mode changes.

### 5.3 Text and drawing stack

Render guidance in one opaque theme-resolved control-surface rounded box centered at the bottom of
the fixed canvas. Every line uses the same 5.5% of `S` raw text size as the proposed-DPI readout.
The box, wrapping width, line spacing, and anchor derive only from viewport geometry; no instruction
dimension may depend on candidate or template size. Wrap at measured word boundaries so every line
fits.

`CARD`:

```text
SCREEN CALIBRATION
CARD · 85.60 × 53.98 mm
Place card at upper-left.
Drag anywhere above buttons to resize.
Match solid right and bottom borders.
```

Both note modes use the same anchor and sentence structure in every orientation. Only the physical
long-edge direction and measured opposite border differ.

`US_NOTE` in a tall viewport:

```text
SCREEN CALIBRATION
US NOTE · 66.29 mm
Place note at upper-left.
Long edge left-to-right; match bottom border.
Drag anywhere above buttons to resize.
```

`US_NOTE` in a wide/square viewport replaces the fourth line with
`Long edge top-to-bottom; match right border.` `EURO_NOTE` uses the same lines with
`EURO NOTE · €20 · 72 mm`, reflecting the selected denomination.

The viewport displays live proposed density as exact rounded `X x Y dpi`, inert and right-aligned at
the upper-right inset. Its 5.5% of `S` text size is fixed; use the non-semantic shared
secondary-text role on an opaque theme-resolved control-surface plate. The plate follows the
measured label bounds and raw viewport fractions; the label anchor and measurement geometry are
fixed. Note modes show equal X/Y values because they assume square pixels.

## 5. Layout — continued 1

Drawing order is exact: background; opaque guidance and proposed-DPI plates/text; semi-transparent
sample body/hatching plus the clipped CARD magnetic-stripe cue; feather; fully opaque solid
reference border; individual bottom-control plates/icons and any open denomination menu. The
reference paints below every control, including the disabled placeholder. Bottom-bar gaps expose the
reference sample. The inactive CARD/US_NOTE denomination plate has the opacity defined in section
5.2; EURO_NOTE uses four opaque plates. Sample fill, hatching, magnetic stripe and feather exclude
both text plates, preserving their opaque contrast surfaces even when the reference grows across
them. The single solid alignment border retains its complete candidate-derived geometry and paints
above the plates. Plate protection introduces no border, hit target or measurement edge.
