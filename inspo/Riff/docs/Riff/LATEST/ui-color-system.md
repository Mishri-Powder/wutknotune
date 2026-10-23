# Riff local UI color system

> Owner/scope: local Player color roles, enabled state, selection, focus and skin contrast.

## Palette and semantic roles

All local screens, dialogs, selectors and calibration chrome use the same resolved role set. Day
uses cool light neutral surfaces and dark content. Night uses dark neutral surfaces, light content
and restrained accents. Auto resolves the current theme before choosing roles. A color change never
changes an input gate, geometry, action, queue, layout or physical calibration.

The file-only paths are `skins.<name>.colors.<role>.<day|night>`. Registered colors are opaque
Android colors; transparency has separate tokens. The complete packaged default contains these
values:

| Role                    | Day       | Night     |
| ----------------------- | --------- | --------- |
| `screenBackground`      | `#EEF1F5` | `#101419` |
| `rowSurface`            | `#E1E6EC` | `#1C2229` |
| `rowSurfaceNowPlaying`  | `#CADBEA` | `#293846` |
| `controlPlate`          | `#D8DFE7` | `#232B34` |
| `textPrimaryColor`      | `#152536` | `#DEE5ED` |
| `fragmentTitleColor`    | `#12483D` | `#B4DCCB` |
| `textSecondaryColor`    | `#44566A` | `#ADB9C7` |
| `textSuppressedColor`   | `#515D69` | `#9AA7B6` |
| `iconInactive`          | `#515D69` | `#9AA7B6` |
| `accentActive`          | `#174B75` | `#9ABCD8` |
| `accentCircle`          | `#174B75` | `#9ABCD8` |
| `iconDisabled`          | `#96A1AD` | `#64717F` |
| `focusRingColor`        | `#174B75` | `#9ABCD8` |
| `nowPlayingBorderColor` | `#174B75` | `#9ABCD8` |
| `positive`              | `#226449` | `#A0CBB6` |
| `error`                 | `#923F48` | `#D7A0A6` |

`textPrimaryColor` serves ordinary entity names and enabled content. `textSecondaryColor` serves
supporting text. `textSuppressedColor` serves available effectively banned content; `iconInactive`
serves an operable control whose function is Off or restricted by a recovery policy. `iconDisabled`
serves genuinely unavailable controls with disabled semantics and no activation. Off and Ban remain
readable and their actions retain their independent ordinary gates. FILF title highlighting alone
uses `fragmentTitleColor` when the file-only switch is enabled. Positive/error feedback has its own
role; ordinary selected actions share the blue accent. Like/Dislike/Ban retain their adopted
outline/filled variants and full semantic state.

`circleFillActiveAlpha` is day `0.30`, night `0.28`; `circleFillPartialAlpha` is `0.13`. Both are
finite `[0,1]`. Selected icon/text choices retain the circle and add an opaque `2dp` circular
outline using `accentActive`; focused/pressed controls use the full-target `3dp` rounded outline
using `focusRingColor`. Selected plated rows use their configured outline, at least `2dp`. Selection
and focus have distinct geometry, remain visible without color discrimination and expose the
existing selected/focused semantics. Disabled controls have no active focus/selection outline.

## Controlled and image backgrounds

[Shared contrast halo](/Riff/LATEST/ui-contrast-halo) owns one silhouette compositor for content and
state outlines over artwork and AI canvas. Outline decoration contains only the stroke, never a
filled target. Its white day/black night surround establishes an adjacent contrast background
without changing hit bounds. Solid-surface controls use their opaque role contrast. Playback top
chrome follows the same image-background policy as playback body controls.

A-Z adjusts the complete ordinary list and its background once after composition with the file-only
RGB subtraction under [UI tokens](/Riff/LATEST/ui-tokens#search-and-disabled-control-configuration).
No per-component search decoration is added; chrome and keypad/pager remain outside the layer. The
list stays operable. The unavailable keypad/MAIN alpha treatment remains scoped to genuinely
disabled controls. Calibration receives resolved colors while every measurement and dimension
retains the raw-framebuffer contract; its single reference line and hatched/clear distinction carry
the meaning.

AI corner hues preserve the logical emotion mapping: high-arousal negative valence is coral,
high-arousal positive valence is amber, low-arousal positive valence is teal, and low-arousal
negative valence is blue. Night variants reduce saturation and field brightness. The
[AI canvas owner](/Riff/LATEST/ai-screen-arousal-valence-canvas) defines exact corners, density
alpha, viewport and RTL mapping. Axes, numeric/accessibility values and a contrasting marker convey
the coordinates independently of hue. Gradient colors are not button-state colors.

## Validation and verification

The shared JVM configuration owner validates every resolved theme of every effective skin. Require
primary text and FILF titles at least `7:1` against all four solid surfaces and actual panel
composites; require secondary, suppressed, inactive, accent, focus, selected-outline and feedback
roles at least `4.5:1`. Check the actual selected-circle composite against its text and icon;
primary text retains `7:1` and the icon retains `4.5:1`. Validate halo foreground roles against
white by day and black by night. Apply the same contrast and night-order checks to both ordinary
foreground/background pairs and their common post-composition A-Z subtraction, including panel and
selected-circle composites. Large amounts that destroy contrast reject the complete file. Night
content must be lighter than its surfaces in both states. Reject the complete user file on failure,
using ordinary startup fallback. No compatibility parser or runtime reload.

Device checks cover both themes, real enabled/disabled/selected/focus states, black/white/patterned
artwork and gradient, unchanged semantics/targets, RTL/font scale, Dim Off reachability, available
Ban labels, A-Z rows, and calibration palette activation with unchanged measurements. Formula and
pixel checks establish local rendering; physical glare, luminance and vehicle-host conformance need
separate evidence.

Official review date: `2026-10-04`. Sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles),
[AAOS color](https://developers.google.com/cars/design/automotive-os/design-system/color), and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).
