# Riff CALIBRATION_SCREEN contract — 4. Canvas geometry

> Owner/scope: bounded section of Riff/LATEST/calibration-screen

## 4. Canvas geometry

### 4.1 Structure

The **canvas** occupies the fixed work area above the bottom controls. Its guidance and input
geometry never changes with candidate size. The reference sample paints below the controls, without
an opaque bottom-bar backing, so its texture remains visible through control gaps. CARD may extend
vertically to the padded viewport bottom behind the controls. Wide/square note modes extend their
unmeasured vertical span to that same bottom; their measured horizontal scale is unchanged.

Anchored to the canvas's **top-left corner**:

- A **semi-transparent hatched region**, filled with diagonal hatching at 45°. Its body and hatch
  marks remain translucent so lower-stack information stays visible. This is what shrinks.
- In `CARD` mode only, one clipped magnetic-stripe band is drawn inside the hatched card as an
  orientation cue, not as an alignment target. It is parallel to the card long edge, 12.70 / 53.98
  of the current rendered card short edge wide, and its centre is 12.00 / 53.98 of that short edge
  inward from the long edge seated against the canvas (top in a wide canvas, left in a tall canvas).
  The band scales and rotates with the card: landscape is a horizontal card with a horizontal
  stripe; portrait is a vertical card with a vertical stripe. It has no independent border, hit
  target, semantic node, measured edge, or colour-only meaning.
- **One crisp, fully opaque stroke** on its boundary, at full contrast. This is the single alignment
  target: the user brings the object's edge to this line and nothing else. Only this solid reference
  border is fully opaque.
- A **feathered margin** immediately outside the stroke: a soft gradient from a tint at the stroke,
  fading to nothing outward. It has **no outer edge of its own** — no second stroke, no hard
  boundary anywhere.

Exactly one hard line exists per measured edge. The shared primary-text role supplies the opaque
reference line in the resolved theme; translucent neutral hatching and feather supply texture and
tolerance without another alignment edge.

| Mode                             | Crisp stroke on      | Rounded                     | Feather on       |
| -------------------------------- | -------------------- | --------------------------- | ---------------- |
| `CARD`                           | full perimeter       | yes, `3.7%` of `hatchWidth` | right and bottom |
| Note modes, wide/square viewport | left and right edges | no                          | right            |
| Note modes, tall viewport        | top and bottom edges | no                          | bottom           |

In a wide/square note viewport the region spans full canvas height; left is the seating edge, right
is measured and feathered. In a tall note viewport it spans full canvas width; top is the seating
edge, bottom is measured and feathered. The full-span orthogonal edges are canvas edges, not
reference edges, and carry no stroke.

The feather tint is configurable, defaulting to a neutral grey. It must never be the sole carrier of
meaning — hatched versus plain already distinguishes the states — so any tint the user picks remains
correct.

Implementation note: a blurred stroke is the practical route. Draw the rounded-rect or line path,
then stroke it with a paint carrying a `BlurMaskFilter` at the feather radius, offset outward along
the measured axes. A stack of concentric strokes with decreasing alpha is an acceptable fallback.

Do not draw a bordered or tinted container for the touch zone: its edge would become a second hard
line and reintroduce exactly the ambiguity this design removes.

No colour carries meaning anywhere on this screen: the states are distinguished by hatched / clear /
absent, so the screen remains fully usable for a colour-blind user. Do not introduce red-green or
any other semantic colour coding.

### 4.2 What the user sees

With the reference object laid into the top-left corner:

| Visible                                            | Meaning                   |
| -------------------------------------------------- | ------------------------- |
| A strip of hatching beyond the object's edge       | Region too large — shrink |
| The crisp stroke sitting against the object's edge | Correct                   |
| The stroke hidden under the object                 | Region too small — grow   |

The feather makes the third state degrade gently rather than snapping: as the region shrinks past
correct, progressively more of the soft margin disappears under the object instead of a hard line
vanishing at once. That gradient is the tolerance signal.

No fine-nudge buttons are provided. Landing the edge within a feather's width is accurate enough,
and the residual error is far smaller than the variation in how a person places a fingertip.

### 4.3 Initial size

Set the hatched region plus its band from the **largest reference scale that fits both the current
viewport canvas and the canvas produced by swapping the same viewport width and height**, reduced by
2%, and further capped so that at least 12% remains clear along each measured axis. This limiting
fit makes the initial candidate PPI identical in portrait and landscape.

Do not derive the initial size from the reported density. The rule makes no assumption at all: every
supported panel is physically larger than every reference object along its long axis, so the hatched
region always starts larger than the object; the usual adjustment starts by shrinking it. The user
MAY expand a CARD horizontal edge to the canvas right boundary and its vertical edge to the padded
viewport bottom, including the bottom controls. Note measured-axis bounds remain the canvas bounds.
Startup size is unchanged and is not the maximum. The complete work area above the bottom controls
is the drag surface; input continues to use relative drag deltas.

Orient the hatched region so the reference object's long edge runs along the canvas's long edge.

The smallest configuration in the supported set is a 7-inch 800×480 panel, where the card's long
edge is about 449 px against a canvas of roughly 776 px — a margin of more than 70%. Assert the
condition in code, but do not build a fallback path for it: an in-vehicle display small enough to
fail this does not exist.
