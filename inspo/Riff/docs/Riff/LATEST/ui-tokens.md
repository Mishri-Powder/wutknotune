# Riff UI tokens

> Owner/scope: shared UI behavior, typography, color and emphasis tokens

## Search and disabled-control configuration

These installation-scoped, file-only scalar integers belong to `skins.<name>.behavior`. The common
schema validates and activates them in the immutable startup snapshot. No profile preference,
Settings control, variant or runtime reload exists.

| Key                        | Default | Exact effect                                                                                                                 |
| -------------------------- | ------: | ---------------------------------------------------------------------------------------------------------------------------- |
| `behavior.azRgbSubtract`   |    `32` | Integer `0..255`; each rendered 8-bit RGB channel c becomes max(0, c - value), alpha unchanged; zero disables the adjustment |
| `behavior.azDisabledAlpha` |    `51` | Complete unavailable A-Z control, plate and glyph at value/255 opacity; shared by disabled MAIN_SCREEN AI and Playlists      |

A-Z uses exactly one post-composition GPU adjustment over the complete ordinary selector content and
its background, below the keypad/pager and ordinary undimmed top chrome. This is absolute
subtraction, not a percentage, multiplication or normalization. No black-alpha or theme scrim is
painted. No search-specific per-component tint, opacity or contrast halo is added. Ordinary control
states render before the common adjustment. No per-frame CPU bitmap processing or readback is
permitted. The configured amount participates in the
[shared contrast validation](/Riff/LATEST/ui-color-system#validation-and-verification).

The layer has no pointer handler, focus or accessibility node. Exposed list scrolling, taps, long
presses and accessibility actions remain ordinary. Available keys remain opaque; unavailable keys
expose disabled semantics, with no active outline, hatching or local black darkening. Transparency
never permits action pass-through. Protected keypad gaps remain functional. Pending calculation
preserves last-complete same-source availability appearance. The hierarchy owner controls exact
input and gap behavior. Do not add platform dimming.

`azOverlayAlpha`, `azBackgroundDimAlpha` and `azDisabledDarkenAlpha` are unsupported keys; there is
no compatibility reader or migration. A minimal optional file override is:

```json
{ "skins": { "default": { "behavior": { "azRgbSubtract": 32 } } } }
```

## Physical and behavioral tokens

Under `skins.<name>`, dp tokens below belong to geometry; artworkAlpha, idleTimeoutSeconds and the
remaining non-geometric behavior tokens belong to behavior. Explicit behavior.\* paths above use the
same skin prefix. Typography and colors have their own sections, not root-level aliases. Exact
narrower screen rules still control their scoped widgets; a token for an absent control does not
create that control. Startup validation also requires topBarHeight >= navTarget, listRowHeight >=
ratingTarget, and listRowHeightTwoTier >= 2 × ratingTargetNarrow + targetSpacingMin.

| Key                       | Type         |      Default | Allowed variants | Strict constraint                                                                                                                             |
| ------------------------- | ------------ | -----------: | ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `wideThreshold`           | number (dp)  |        `800` | fixed            | finite and greater than `0`                                                                                                                   |
| `touchTargetMin`          | number (dp)  |         `76` | fixed            | at least `76`                                                                                                                                 |
| `touchTargetPrimary`      | number (dp)  |        `128` | responsive       | at least `76`                                                                                                                                 |
| `mainCategoryTileSide`    | number (dp)  |        `240` | responsive       | supported direct-HU MAIN_SCREEN uses exactly `240`; constrained phone/split-window fallback remains at least `128` and uses bounded scrolling |
| `mainCategoryIconSide`    | number (dp)  |        `160` | responsive       | exactly `160` inside the default `240` MAIN_SCREEN tile; must remain centered, preserve aspect ratio, and not create a separate target        |
| `targetSpacingMin`        | number (dp)  |         `24` | fixed            | at least `24`                                                                                                                                 |
| `screenEdgeMargin`        | number (dp)  |         `24` | fixed            | at least `24`                                                                                                                                 |
| `topBarHeight`            | number (dp)  |         `80` | responsive       | at least `76`                                                                                                                                 |
| `navTarget`               | number (dp)  |         `80` | responsive       | at least `76`                                                                                                                                 |
| `bottomBarHeight`         | number (dp)  |         `88` | responsive       | at least `76` when rendered                                                                                                                   |
| `listRowHeight`           | number (dp)  |        `100` | responsive       | at least `76`                                                                                                                                 |
| `listRowHeightTwoTier`    | number (dp)  |        `176` | responsive       | at least `176` for the HIERARCHY_SCREEN title/body tier plus reaction tier: `76 + 24 + 76`; targets MUST NOT overlap                          |
| `listRowGap`              | number (dp)  |         `10` | responsive       | finite and non-negative; interaction targets remain separated by `targetSpacingMin` where required                                            |
| `playbackButtonHeight`    | number (dp)  |        `120` | responsive       | at least `76` and the resolved `touchTargetMin`; common to every playback transport/reaction slot in both orientations                        |
| `ratingTarget`            | number (dp)  |        `100` | responsive       | at least `76`                                                                                                                                 |
| `ratingTargetNarrow`      | number (dp)  |         `76` | responsive       | at least `76`                                                                                                                                 |
| `ratingButtonWidth`       | number (dp)  |        `296` | responsive       | at least `76`                                                                                                                                 |
| `ratingButtonHeight`      | number (dp)  |        `128` | responsive       | at least `76`                                                                                                                                 |
| `aiStatusInset`           | number (dp)  |         `16` | fixed            | exactly `16`; physical top and left canvas inset                                                                                              |
| `artworkAlpha`            | integer      |        `128` | fixed            | integer `0..255`; configuration-file only; no Settings surface                                                                                |
| `idleTimeoutSeconds`      | integer (s)  |         `30` | fixed            | integer `1..86400`; configuration-file only; no Settings surface                                                                              |
| `progressBarHeight`       | number (dp)  |         `10` | responsive       | finite and greater than `0`; progress remains non-interactive                                                                                 |
| `cornerRadius`            | number (dp)  |         `20` | responsive       | finite and non-negative                                                                                                                       |
| `nowPlayingBorder`        | number (dp)  |          `4` | responsive       | finite and at least `2`                                                                                                                       |
| `countdownRingStroke`     | number (dp)  |          `6` | responsive       | finite and greater than `0`                                                                                                                   |
| `countdownRingInset`      | number (dp)  |          `8` | responsive       | finite and non-negative                                                                                                                       |
| `iconButtonIconInset`     | number (dp)  |         `10` | fixed            | exactly `10` for body controls; top-bar chrome uses exactly `15`                                                                              |
| `hierarchyRowIconInset`   | number (dp)  |         `10` | fixed            | exactly `10`; hierarchy-row icon square and selected circle diameter equal the resolved reaction target minus twice this inset                |
| `mixedButtonIconInset`    | number (dp)  |         `10` | fixed            | exactly `10` for body controls; top-bar chrome uses exactly `15`                                                                              |
| `mixedButtonTextGap`      | number (dp)  |          `8` | responsive       | finite and non-negative                                                                                                                       |
| `hierarchyRootUpBehavior` | string       | `"disabled"` | fixed            | exactly `"disabled"` or `"home"`                                                                                                              |
| `rowsPerPageMax`          | integer      |          `5` | fixed            | from `2` through `5`                                                                                                                          |
| `rowsPerPageMin`          | integer      |          `2` | fixed            | at least `1` and not greater than `rowsPerPageMax`                                                                                            |
| `maxStringLength`         | integer      |        `120` | fixed            | from `1` through `120` Unicode grapheme clusters                                                                                              |
| `pressFeedbackMaxMs`      | integer (ms) |        `100` | fixed            | positive and not greater than `100`                                                                                                           |
| `skipLatencyMaxMs`        | integer (ms) |        `200` | fixed            | positive and not greater than `200`                                                                                                           |
| `undoWindowSeconds`       | integer (s)  |          `4` | fixed            | one of `0`, `3`, `4`, or `5`; `0` disables undo only where immediate action remains safe                                                      |

See [Typography and color tokens](/Riff/LATEST/ui-typography-colors) for the remaining scoped rules.

[Shared contrast halo](/Riff/LATEST/ui-contrast-halo) owns fixed rendering constants for playback
and AI overlays.
