# Riff icon system contract — 3. Drawable and rendering contract

> Owner/scope: bounded section of Riff/LATEST/icon-system

## 3. Drawable and rendering contract

- Every shipped general UI icon MUST remain an Android vector resource with declared `24dp` width
  and height and a square `24 × 24` viewport. Layout MUST size the containing icon square in true
  `dp`; XML dimensions MUST NOT become screen-relative geometry.
- Paths MUST remain inside the viewport. For the 67 supplied pairs, exact default/day XML uses
  `#FF0C1015` and exact `drawable-night` XML uses `#FFFFFFFF`; transparent paths remain transparent.
  The other 26 general icons retain their adopted white tint-mask XML. These source colors are
  fallback artwork values, not permission to bypass the shared skin color roles.
- Runtime button presentation follows [UI and artwork](/Riff/LATEST/ui-foundation). Every ordinary
  app-owned button has exact `10dp` internal content padding; fixed breadcrumb/top-bar controls have
  exact `15dp`. Except for SETTINGS_SCREEN `ic_scale_diagonal` and `ic_scale_card`, a button
  pictogram is laid out in a `1:1` containing viewport whose side equals the padded target's shorter
  dimension and whose position is centered on the longer dimension. The renderer MUST NOT stretch,
  crop, or optically enlarge the vector to escape that square.
- `ic_scale_diagonal` and `ic_scale_card` are the only button-pictogram aspect exceptions. They
  retain their adopted artwork geometry inside the same applicable padded bounds; they are not
  padding exceptions.
- Runtime rendering MUST tint enabled icons to `buttonContentColor` and disabled icons to
  `buttonContentColorDisabled` or the controlling accessible color role. Android still resolves the
  exact Day/Night resource qualifier first; the shared tint then applies the active skin role.
- Strokes, fills, caps, joins, path data, fill rules, and internal padding are part of the adopted
  files and MUST NOT be edited independently.
- Filled variants MUST retain the same outer visual bounds as their outline counterparts.
  `ic_ban_filled`, `ic_theme_night`, and `ic_theme_auto` MUST retain their even-odd knockouts;
  Riff's `minSdk 31` satisfies the API 24+ requirement.
- Icon-only controls MUST expose a localized accessible name. When visible text already names the
  action, the icon SHOULD be decorative to avoid duplicate announcements.
- Icon visual bounds do not define the interaction target. Central automotive target, spacing,
  contrast, focus, and disabled-state requirements still apply.

[Shared contrast halo](/Riff/LATEST/ui-contrast-halo) may decorate the already rendered playback
pictogram silhouette without editing resources, parsing vector paths or changing foreground
geometry. The shared state-outline adapter decorates only its stroke silhouette under the
[local color system](/Riff/LATEST/ui-color-system). The plate and selected fill remain separate.

## 4. Source, packaging, and change control

- Exact source copies of all 93 normative default/day icons plus the separately adopted
  default-artwork and SMPTE drawings MUST live directly under `references/drawable/`. The exact 67
  supplied Night overrides MUST live under `references/drawable-night/`. Matching XML copies MUST be
  packaged under `app/src/main/res/drawable/` and `app/src/main/res/drawable-night/` as ordinary
  Android resources. General UI drawable XML is not part of packaged or user configuration, has no
  runtime override mechanism, and requires no Base32hex encoding, XML-in-JSON loader, parser, repair
  path or private renderer. Launcher, adaptive-icon, splash and other Android lifecycle resources
  remain ordinary Android resources under their existing contracts.
- The exact archive, style prompt, prompt attachment ZIP, supplied previews, archive README and
  explicitly excluded standalone launcher reference MUST remain under
  `references/drawable/icon-system/` for provenance. The archive and both previews MUST also be
  attached to this Wiki contract. The excluded launcher MUST NOT exist under either adopted
  `references/drawable*` source root or any APK resource directory.
- A proposed modification or addition MUST be staged outside `references/drawable`, evaluated as a
  complete coherent set, and explicitly adopted before promotion.
- Changing any normative XML byte, roster entry, semantic mapping, or prompt byte is a requirements
  change and MUST follow the central registry workflow.
- The prompt MUST remain text. It MUST NOT be silently edited to match an output, and output MUST
  NOT be used to infer or overwrite the prompt.

## 5. Required verification

Verification MUST cover the exact 93-name normative icon roster, the 67-name Night-override roster,
current source hashes and qualifier-matched source/resource XML equality; the new archive checksum
and member inventory; the supplied Day/Night contact sheets; explicit launcher exclusion from both
rosters and the APK; the SMPTE source hash and explicit exclusion from the icon roster; exact
untinted `4:3` SMPTE rendering through the standard resource path; exact context-correct dual use of
`ic_scale_eur_10`, `ic_scale_eur_20`, and `ic_scale_eur_50` for AI queue lengths with queue-only
accessibility semantics and no parallel digit assets; stable paired-state bounds; day/night tint,
disabled rendering, contrast, focus, and accessibility; absence of drawable XML in
packaged/default/user configuration; and absence of unauthorized prompt execution or unadopted
generated assets.

All app-owned Compose screens MUST use one shared composable icon renderer that maps each adopted
semantic icon name to a statically declared `R.drawable` resource and loads it through standard
Android/Compose resource inflation such as `painterResource`. Material Icons, stock platform icons,
emoji, text glyphs and dynamic name lookup are prohibited substitutes.

The shared semantic-name boundary accepts complete canonical `ic_*` names and the existing
unprefixed shorthand for registered general UI icons. Canonical names retain exactly one `ic_`
prefix. Unknown names, doubled prefixes and excluded non-icon drawings remain rejected by the same
finite roster; no dynamic resource lookup is introduced.

The shared renderer MUST expose explicit square-viewport policy, content padding, tint, alpha,
enabled, semantic description, focus, and click behavior so
[UI and artwork](/Riff/LATEST/ui-foundation) and each registered screen contract control the
complete visual and hit target independently of Material defaults. Optical-bound normalization MUST
NOT resize or reposition ordinary button pictograms. No application code parses VectorDrawable XML
path data or implements a parallel VectorDrawable renderer.

## 6. Current semantic bindings

All Boolean Yes/No preference bindings follow the
[shared UI owner](/Riff/LATEST/ui-foundation#boolean-preference-controls), using `ic_yes` and
`ic_no` through the existing shared renderer.

A-Z entry uses ic_sort_az, named A-Z search, without changing source order. Backspace uses ic_rew;
the combined residual # group uses ic_az_symbol. No body OK/Apply, End-of-name/Like or Other control
exists. ic_az_other remains an unused exact corpus member, not permission to restore an Other
action.

Unavailable A-Z plate and glyph are composited together at behavior.azDisabledAlpha / 255, default
20%, without hatching, a darkening layer or action pass-through. The same source controls disabled
AI/Playlists in Books mode.

Books uses ic_audiobook inside one complete named checked-state action target. ROW_ACTIONS reuses
ic_genre for Music and ic_audiobook for Book through the same selected/disabled icon renderer; the
two-toggle interaction belongs to the hierarchy/content-mode owners. Album and Artist glyphs serve
Titles and Authors; no replacement variant is required.

## 7. Exact AZ corpus

All 16 letter groups use one line with equal 6x8 cells and 1.8-unit rounded strokes. Five digit
groups use separated 0−1, 2−3, 4−5, 6−7 and 8−9 ranges. Hidden aliases add no glyphs. Runtime font
glyphs, alternate renderers and new A-Z entry drawings are prohibited. tools/az-icons/generate.py is
the sole source generator; candidates require adoption before promotion.

| Source                     | SHA-256                                                          |
| -------------------------- | ---------------------------------------------------------------- |
| ic_az_digit_01.xml         | 4897f9651634e9dcda2f8c708353608871c6a5436ce4574b6068579ae16a5751 |
| ic_az_digit_23.xml         | 6ba43adbd183247a2bc011f2ba3fd950528945486422aec48b432f5526ed7852 |
| ic_az_digit_45.xml         | 5a23a5d6a138b1aa0559be82f4ccef51b91416c25b898c53c949499a86967b02 |
| ic_az_digit_67.xml         | 9a6556c183903613c673bc81618b5b212f862c4cb9b6bff8c7bc0466d22a2748 |
| ic_az_digit_89.xml         | 37bf29f07bff430d10d5dd33734e32fdc3fe3955533c0cb1b2d1d36e93e9532b |
| ic_az_latin_ac.xml         | e2a0725c541512eabae7404894a02a26921fbe64f9b9e85b1f6393e5ad4e286b |
| ic_az_latin_df.xml         | 54237b9ccff6402f19d1f9382487369c967738f6ef4560026f9ae27344958ed1 |
| ic_az_latin_gi.xml         | cb05fff6c94d28802b32194b42869fa8c8a7cec44776049e95e21db5c8649673 |
| ic_az_latin_jl.xml         | c0dadedcf27cc0e38c2996a255513a7b6e5bfb40edde7de760e681fddba0e576 |
| ic_az_latin_mo.xml         | 46613161c92715ed23e374e0007085fbf4b8ec5ea705b268df8e1dca10994bdb |
| ic_az_latin_ps.xml         | c44207b6bc6812899078593bb45fcb13e38d6b7cabaf62a660b148dff4a1334c |
| ic_az_latin_tv.xml         | df0625082d61d6e05f918161ab3f14a3fe4a0228ffa2f2f67bce40dc45dab0f1 |
| ic_az_latin_wz.xml         | 3ac78676fdcdbc5fd3641e7d6f902cdbf62512bc8f06171ddab9561f9e985d02 |
| ic_az_cyrillic_ag.xml      | 3c83741b640c7715fc564b219fc0476d5e41652558feccc98df42f955aa1db63 |
| ic_az_cyrillic_dz.xml      | 1fa51aff9eefe16cc5e0f76d5d67d843ff8c5a3f84371ad6ad777020ba30e937 |
| ic_az_cyrillic_il.xml      | a3867845c8303bb87d9f61938104c3195a9b48e9c33c13fbea71062ead4f7a88 |
| ic_az_cyrillic_mp.xml      | cbdc7d88227298fda319c8afc674409f5601165fb83f7f20e60c52ddb0353880 |
| ic_az_cyrillic_ru.xml      | 721ec8ac3de01aaacfa974dac9c55c5c6ca1f6ec669a75c3d558c3c66c2d58dd |
| ic_az_cyrillic_fch.xml     | 4adae26609d59d1c276564be00cc7fe07adab3a1d9000cd59f303e81236f089d |
| ic_az_cyrillic_shy.xml     | 02841867d87731d1dc3c9963a2b219a5cfc2cc8643ba20cfeae46261b81527d3 |
| ic_az_cyrillic_soft_ya.xml | 3b1b10073effb028a0627ab752f0ba9f0e6d2766adb91208f415ba5a2f155819 |
| ic_az_symbol.xml           | ce5234341119d8de2058d24f95c865879a6d0c001a4b2b1b6a4b3956c15dd5f6 |
| ic_az_other.xml            | b6da2546deb9f7f1da5070d1487199e208b5c3019648cf7025f5e680118a10f8 |
