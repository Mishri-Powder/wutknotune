# Riff typography and color tokens

> Owner/scope: delegated by [ui-tokens](/Riff/LATEST/ui-tokens).

## Typography

Paths are `skins.<name>.typography.<key>`; color paths are `skins.<name>.colors.<key>`.

| Key                      | Type        |        Default | Allowed variants | Strict constraint                                                                  |
| ------------------------ | ----------- | -------------: | ---------------- | ---------------------------------------------------------------------------------- |
| `textTitleList`          | number (sp) |           `40` | responsive       | at least `36`                                                                      |
| `textTitlePlayback`      | number (sp) |           `60` | responsive       | current PLAYBACK_SCREEN title is `60`; artist/album/chapter are `44`/`32`/`24`     |
| `textSecondary`          | number (sp) |           `24` | responsive       | at least `20`; content required while moving MUST use at least `36`                |
| `textStatusLine`         | number (sp) |           `20` | fixed            | exactly `20`; regular monospace MAIN_SCREEN, PLAYBACK_SCREEN and AI_SCREEN status  |
| `textInMotionFloor`      | number (sp) |           `36` | fixed            | at least `36`                                                                      |
| `mixedButtonMinFontSize` | number (sp) |           `36` | responsive       | at least `36` for driving-required labels                                          |
| `mixedButtonMaxFontSize` | number (sp) |           `40` | responsive       | at least the resolved minimum                                                      |
| `topBarFontFamily`       | string      | `"sans-serif"` | day/night        | exactly `sans-serif`, `serif`, or `monospace`; full system fallback chain retained |
| `topBarFontWeight`       | integer     |          `400` | day/night        | from `300` through `500`                                                           |

Respect fontScale, Android typeface/fallback measurement and complete accessible labels. Ellipsize
at grapheme boundaries; do not alter geometry for a chosen face/weight/size. Driving-required text
is at least 36sp. No raw-pixel text or neutralized font scale.

All app-owned modal text and name input follow the permanent
[shared modal typography contract](/Riff/LATEST/ui-modal-dialogs#permanent-shared-component-rule).

## Color ownership

[Local UI color system](/Riff/LATEST/ui-color-system) owns complete day/night defaults, semantic
roles, operable Off/Ban, selected/focus outlines, calibration colors and all skin contrast checks.

## Shared contrast surround

[Shared contrast halo](/Riff/LATEST/ui-contrast-halo) owns the common silhouette compositor for
playback, image-background controls, A-Z content/state outlines and AI overlays. Typography,
foreground shaping, geometry and accessibility remain governed by their scoped owners.
