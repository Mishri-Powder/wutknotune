# Riff shared contrast halo

> Owner/scope: decorative contrast surround for local playback and AI canvas overlays.

## Scope and rendering

One shared renderer owns the complete halo for PLAYBACK_SCREEN title, artist, album, optional
chapter and diagnostic status; Shuffle, Previous/Rewind, Play/Pause, Next/Forward and Repeat; Like,
Dislike, Ban and Bookmark; and the position track, filled progress and thumb. The same renderer
serves AI_SCREEN A, V, advisory and optional diagnostic status. MAIN_SCREEN status and ordinary
plated controls use their registered surface roles. One outline-only adapter serves selected
circles, focus/pressed borders and the AI frame/marker. Playback chrome shares this compositor when
its backdrop is uncontrolled. A-Z ordinary selector components retain their normal plated styles,
followed by the one shared RGB adjustment under
[UI tokens](/Riff/LATEST/ui-tokens#search-and-disabled-control-configuration). A-Z does not activate
a per-component halo override. Playback and AI halo behavior remains owned here.

The renderer follows the exact already rendered glyph, icon or progress silhouette. Text uses
ordinary Android/Compose shaping, fallback, ellipsis, sp/fontScale and bidi layout. Icons use the
adopted APK resources and standard resource inflation under the
[icon rendering contract](/Riff/LATEST/icon-system-drawable-and-rendering-contract). There is no
resource edit, vector XML parser or alternate vector renderer.

The resolved day theme uses white halo; the resolved night theme uses black halo. Auto follows the
actually resolved theme. Paint order is far zero-offset blur at `24dp` and `0.85` alpha, near
zero-offset blur at `8dp` and `0.95` alpha, dense opaque outward contour of nominal `2dp`, then the
unchanged foreground. All adapters share the compositor, contour and blur passes; sharing only
constants between independent implementations is insufficient. Reusable graphics layers/effects
retain device rendering: production rendering performs no per-frame CPU pixel readback or bitmap
allocation. Rasterization and antialiasing are ordinary device rendering details.

Halo is decorative and creates no plate, padding, layout or semantic node. Keep existing bounds,
viewport, hit target, focus ring, selected circle, enabled/disabled states, actions and safe-area
clipping. It cannot make a disabled action interactive. Paint the pictogram, progress or outline
stroke silhouette; keep filled plates and selected fills outside the decorated silhouette.
Foreground retains its own state tint; AI overlay foreground is opaque black by day and opaque white
by night, regular monospace `20sp`. These halo values and AI colors are fixed rendering constants,
with no skin key or Settings editor. The shared renderer must not mutate foreground paragraph paint.

## Verification and mode boundary

Measure enabled foreground cores against their dense adjacent halo and require at least `4.5:1`
under AAOS visual principles; blur alone is not a guaranteed contrasting background. Default
playback foreground/state roles use the opposite opaque surround; AI black/white cores have `21:1`
against that surround. Custom skin validation and other surface/state contrast requirements remain
owned by [Local UI color system](/Riff/LATEST/ui-color-system).

Device tests cover black, white and patterned backgrounds in both themes; primary, accent and
secondary foregrounds; outline/filled icons and Bookmark; progress endpoints and changes; preserved
foreground cores; visible dense contour and both blur layers; no rectangular plate; unchanged
geometry, semantics, accessible labels, focus/disabled states and seek input. Text checks include
font scale `2.0`, ellipsis, RTL and complex-script shaping. AI advisory remains exposed once through
the canvas description, with no overlay focus nodes. Test reusable layer disposal and repeated
redraws.

Phone and direct automotive use this same local renderer. Actual AAOS and projected Android Auto
media surfaces retain their supported host rendering. This contract introduces no motion exception,
new input, window-brightness action or full automotive conformance claim. The local halo does not
establish physical luminance or vehicle-host compliance. The local color owner validates palette and
state pairs; device tests verify dense surrounds for outline and glyph adapters.

Official review date: `2026-10-04`. Sources:
[Automotive visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles),
[AAOS color](https://developers.google.com/cars/design/automotive-os/design-system/color), and
[WCAG text contrast and halo background](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).
