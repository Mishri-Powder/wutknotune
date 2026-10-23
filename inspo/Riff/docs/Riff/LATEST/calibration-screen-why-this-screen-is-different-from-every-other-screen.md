# Riff CALIBRATION_SCREEN contract — 1. Why this screen is different from every other screen

> Owner/scope: bounded section of Riff/LATEST/calibration-screen

## 1. Why this screen is different from every other screen

At the moment this screen runs, **the device density is unknown or known to be wrong** — that is the
problem the screen exists to solve. Cheap supported head units are known to report materially
incorrect values. [Physical UI](ui-foundation.md) therefore makes this screen the sole
raw-pixel/percentage bootstrap exception. Therefore:

- **Do not use `dp` anywhere in this screen.** Not for controls, not for text, not for padding, not
  for stroke widths.
- **Do not use the application's theme tokens.** They are expressed in dp and are meaningless until
  this screen has produced its result.
- Size everything in **raw pixels**, computed as fractions of the screen's pixel dimensions (§5).
  This guarantees the screen is usable at any density, including a completely wrong one.
- Read screen dimensions from `WindowManager.getCurrentWindowMetrics().bounds`, never from
  `resources.displayMetrics`.

All measurement, drawing and pointer geometry remains in CalibrationScreenView raw device pixels.
Its single AndroidView bridge does not apply corrected LocalDensity or convert measurement geometry
to dp/sp.

## 2. What the screen produces

| Output   | Type  | Meaning                        |
| -------- | ----- | ------------------------------ |
| `ppiX`   | Float | Horizontal pixels per inch     |
| `ppiY`   | Float | Vertical pixels per inch       |
| `source` | Enum  | `CARD`, `US_NOTE`, `EURO_NOTE` |

CALIBRATION_SCREEN returns these values to the SETTINGS_SCREEN draft. It does not persist or
activate them. SETTINGS_SCREEN OK atomically writes display_ppi_x, display_ppi_y, and
display_ppi_source.

Only the centrally resolved logical density is used for Android rendering. All devices use the
common calibration and AUTO policy defined in [Physical UI](ui-foundation.md); no device identity
changes calibration authority or exempts a testing device. ppiY is retained for local
reference/diagnostics and possible separately authorized future usage statistics. Android does not
support non-uniform dp scale; ppiY never affects layout and is not currently transmitted.

## 3. Reference objects

Three modes. The denomination selector is active in exactly one of them.

| Mode        | Label                        | Denomination selector | Reference (mm)                              | Axes measured    |
| ----------- | ---------------------------- | --------------------- | ------------------------------------------- | ---------------- |
| `CARD`      | `[ic_scale_card_ruled] CARD` | disabled and empty    | 85.60 × 53.98, with ISO/IEC 7811 stripe cue | width and height |
| `US_NOTE`   | `[ic_scale_banknote] $US`    | disabled and empty    | 66.29 (short edge)                          | width only       |
| `EURO_NOTE` | `[ic_scale_banknote] €`      | active, icon-only     | see below                                   | width only       |

Euro denominations, short edge:

| Denomination | Short edge |
| ------------ | ---------- |
| €5           | 62 mm      |
| €10          | 67 mm      |
| €20          | 72 mm      |
| €50          | 77 mm      |

Do not offer €100 or €200: their short edge changed between the first series (82 mm) and the Europa
series (77 mm), and the user cannot be expected to know which one they are holding.

`CARD` is the default mode and must be first in any ordering. It is the stiffest reference, the
longest edge available in a wallet, and the only mode that measures both axes.

Only the banknote short edge is measured. In a tall viewport, place that short edge vertically: the
note's long edge runs off the left/right sides and the bottom boundary is measured. In a wide or
square viewport, place the short edge horizontally: the long edge runs off top/bottom and the right
boundary is measured. The on-screen guidance states the active placement.
