# Riff CALIBRATION_SCREEN contract — 6. Interaction

> Owner/scope: bounded section of Riff/LATEST/calibration-screen

## 6. Interaction

A single drag beginning anywhere in the work area above the bottom controls resizes the template.

| Mode                                         | Horizontal drag        | Vertical drag           |
| -------------------------------------------- | ---------------------- | ----------------------- |
| `CARD`                                       | changes template width | changes template height |
| `US_NOTE`, `EURO_NOTE`, wide/square viewport | changes measured width | ignored                 |
| `US_NOTE`, `EURO_NOTE`, tall viewport        | ignored                | changes measured height |

Rules:

- **Relative, not absolute.** Apply the drag delta to the current size; never jump a corner to the
  finger position. A stray touch must not destroy the current adjustment.
- **1:1 mapping.** A movement of N pixels changes the dimension by N pixels. No acceleration, no
  smoothing, no inertia.
- First pointer only; ignore additional pointers.
- Do not resize until movement reaches 1.0% of `S`; a stationary tap or smaller movement changes
  nothing. Once activated, preserve the complete relative delta from the original down point.
- Clamp the hatched region to at least 20% of its initial size. CARD width is bounded by canvas
  width; CARD height is bounded by the padded viewport bottom, including the bottom-control region.
  Note measured axes remain bounded by the fixed canvas. Startup size is not the maximum.
- The translucent sample, its border, guidance, DPI readout, clear area, and padding all respond as
  one drag surface above the controls.
- No other gestures exist on this screen: no fling, no pinch, no long-press, no double-tap.

### 6.1 State

Hold the candidate measurement as **pixels per millimetre**, not as a template size in pixels:

```text
candidateXPxPerMm = measuredWidthPx / referenceWidthMm
```

Derive the drawn template from it each frame. Candidate X/Y pixels-per-millimetre, mode, and Euro
denomination MUST survive configuration-driven view recreation and rotation without resetting or
recomputation. A fresh entry or a mode/denomination change resets the candidate using the
orientation-invariant limiting fit in section 4.3.

## 7. Computing the result

The reference edge is correct when it lies anywhere inside the band, so take the **centre of the
band** as the measurement — this halves the worst-case error compared with using either contour.

The crisp stroke is drawn centred on the boundary of the hatched region, and it is the line the user
matched, so the measurement is the region itself with no correction term.

```text
measuredWidthPx  = hatchWidthPx
measuredHeightPx = hatchHeightPx                            // CARD only

xPxPerMm = measuredWidthPx / referenceWidthMm
yPxPerMm = if (mode == CARD) measuredHeightPx / referenceHeightMm else xPxPerMm

ppiX = xPxPerMm * 25.4
ppiY = yPxPerMm * 25.4
```

Note modes measure one viewport-selected axis and assume square pixels, so the single measured value
becomes both `ppiX` and `ppiY`. Only `CARD` mode detects an anisotropic panel.

### 7.1 Validation on OK

```text
impliedDiagonalInches = sqrt(
    (screenWidthPx / ppiX)^2 + (screenHeightPx / ppiY)^2
)

Only ppiX controls uniform Android density. ppiY is diagnostic.
```

If it falls outside 4.0…20.0 inches, do not store. Show a short message stating the implied diagonal
and return the user to adjustment. Everything else is accepted: this screen does not chase absolute
precision.

On success, return the candidate outputs to SETTINGS_SCREEN and finish. Do not write permanent
storage or activate density. Cancel discards without changing the parent draft. SETTINGS_SCREEN OK
is the only commit point.

## 8. Known limitations

This screen measures **the framebuffer, not the panel**. It has no way to see past the display
pipeline, and the following are explicitly out of scope:

- A unit that renders at one resolution and scales to a physically different panel — 1024×600
  upscaled onto a 1280×720 matrix, for example. Nothing here detects or compensates for that.
- Overscan, letterboxing, or any vendor post-processing between the framebuffer and the glass.
- Panels whose active area does not match what the window metrics report.

The stored `ppiX` and `ppiY` therefore describe framebuffer pixels per inch, which is the quantity
the rest of the application actually draws in. Where the pipeline scales uniformly this is exactly
what is wanted. Where it scales non-uniformly, Card mode records different horizontal and vertical
PPI for reference. Android rendering still uses the horizontal component only.

## 9. Non-goals

Do not implement: fine-nudge buttons, a separate height-adjustment screen, a confirmation or preview
screen after OK, a ruler mode, additional reference objects, animation of any kind, or automatic
detection. Detection from `DisplayMetrics.xdpi` happens before this screen is ever shown and is not
its concern.
