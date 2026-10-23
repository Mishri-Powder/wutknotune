# Riff icon set

48 Android vector drawables, 24x24dp, square viewport, transparent background.
`riff-icons-preview-day.png` / `-night.png` show the whole set on a button
surface in both skins.

- `viewportWidth == viewportHeight == 24` — passes the CI squareness check
  (settings-structure.md Setting 3.1.1); no distortion when mapped onto the
  icon square of an icon-only or mixed button.
- `width`/`height` are declared as required, but per the same section they are
  ignored for layout; scale is driven by the viewport.
- Geometry is stroked, not filled: `strokeWidth="1.8"` in viewport units,
  round caps and joins. At the default `iconButtonIconPadding` of 12.5 the
  icon square is 75% of `min(b)`, so on a 205px button the stroke lands at
  ~11.5px.
- Filled state variants (`*_filled`) carry fill and stroke on the same
  geometry, so the active and inactive drawables have identical outer bounds
  and the icon does not appear to change size when toggled.
- `ic_dots.xml` is fill-only (three discs); a stroked ring at that radius
  would close up.
- `ic_arrow_up_dot.xml` is `ic_arrow_up.xml` plus a filled disc in the shaft,
  marking long-tap availability. Both share an outer bounding box, so they can
  be swapped without shifting layout.
- `ic_arrowhead_up.xml` / `ic_arrowhead_down.xml` are bare 45-degree triangles
  (17.2 wide by 8.6 tall), deliberately squat so they cannot be confused with
  the shafted `ic_arrow_up` or with a rotated `ic_play`. `ic_arrowhead_down`
  is the exact vertical mirror of `ic_arrowhead_up`.
- `ic_theme_day` / `ic_theme_night` / `ic_theme_auto` are one taijitu: the
  light half, the dark half, and the complete symbol. Because the set is
  tinted a single colour, day and night are told apart by mass rather than
  colour, and they hold under either skin. All three share the same outer
  circle and therefore the same bounding box.
- `ic_scale_auto` is an integrated circuit: the hardware reports its own
  geometry and is trusted. Pin spacing is 6 units so the gaps stay open at
  24dp; at 4.4 they merge into a band.
- `ic_scale_horizontal` and `ic_scale_bidirectional` are the scaling axes. The
  four-way form keeps a 3.2-unit gap at the centre rather than crossing,
  because a crossed version is `ic_plus` with arrowheads added.

## Numerals

`ic_scale_7` / `_9` / `_11` and the four `ic_scale_eur_*` icons are drawn as
stroke skeletons — open paths stroked at the set's 1.8 weight, exactly like
every other icon. There is no font, no `<text>` element and no traced outline
anywhere in the set; `glyphs.py` constructs each glyph from cap height and
width, so every numeral in the set comes from one source.

All seven share a 15.2 cap height on a common cap line and baseline: each
renders between y=3.5 and y=20.5 including the stroke. Two glyphs needed
correcting to reach it. The `9`'s bowl sat 1.1 units low and was raised. The
`2`'s top is a cubic curve whose control points were placed *on* the cap line,
but a cubic never reaches its control points — the apex landed 1.1 units
below, making the digit read a size smaller than its neighbours; the controls
now sit 1.5 units above the cap line so the curve itself touches it. The `5`
overshoots the baseline by 0.2, which is intentional: round terminals must
pass a flat baseline slightly to look level with it.

Digits that stand alone are 10.0 wide; where two must share the viewport they
condense to 8.2 with a 1.4 gap, which keeps `ic_scale_eur_20` and
`ic_scale_eur_50` inside the same 2.2-21.8 horizontal margins as the rest of
the set. Height is what makes numerals read as one size, width is what makes
them fit, so the digits condense rather than shrink. Two wide digits cost
roughly 23% ink against 9-13% for a single one; that is inherent to filling a
viewport with two glyphs instead of one.

Digits are Western Arabic and will not match a locale that renders numerals
differently.

`ic_scale_eur_10` and `ic_scale_11` differ by one glyph while meaning entirely
different things — a banknote and a screen size. They are never shown in the
same list, but the collision is worth knowing about.

## Calibration references

Eight drawables name a physical object of known size that the user holds
against the glass. They must be told apart at a glance, so each is separated
on more than one axis — aspect ratio *and* interior mark:

| file | aspect | interior | ink |
| :- | :- | :- | :- |
| `ic_scale_card` | 1.58 (ID-1) | none, dimension line above | 22.7% |
| `ic_scale_iso_card` | 1.59 (ID-1) | stripe and chip | 25.3% |
| `ic_scale_banknote` | 2.04 | centred medallion | 21.3% |
| `ic_scale_coins` | — | stacked cylinder | 23.0% |
| `ic_scale_eur_5` | — | numeral only | 13.3% |
| `ic_scale_eur_10` | — | numeral only | 17.7% |
| `ic_scale_eur_20` | — | numeral only | 22.7% |
| `ic_scale_eur_50` | — | numeral only | 23.5% |

`ic_scale_card` is the generic "calibrate" affordance and deliberately carries
no payment cues. `ic_scale_iso_card` names the ISO/IEC 7810 ID-1 card
specifically, so the stripe and chip are wanted there. The four euro icons
carry the denomination as a bare numeral, no currency sign; the note's width
is the reference measurement — €5 is 120mm, €10 127mm, €20 133mm, €50 140mm.
`ic_scale_card_ruled.xml` is a lighter alternative drawing of
`ic_scale_card`; ship one or the other.

- `android:fillType="evenOdd"` is used in `ic_ban_filled.xml`,
  `ic_theme_night.xml` and `ic_theme_auto.xml` to knock a counter-form out of
  a solid shape; painting it in the background colour would fail on a
  translucent button surface. That attribute requires API 24+.
- `strokeColor` / `fillColor` are `#FFFFFFFF` so the drawable can be tinted to
  `buttonContentColor` / `buttonContentColorDisabled` at draw time
  (`android:tint`, or `ColorFilter.tint(...)` in Compose). Do not bake skin
  colors into the XML.

| File | Constant |
| :- | :- |
| ic_album.xml | ALBUM_BUTTON_ICON |
| ic_artist.xml | ARTIST_BUTTON_ICON |
| ic_genre.xml | GENRE_BUTTON_ICON |
| ic_playlist.xml | PLAYLIST_BUTTON_ICON |
| ic_ai.xml | AI_BUTTON_ICON |
| ic_folder.xml | FOLDER_BUTTON_ICON |
| ic_flip.xml | FLIP_BUTTON_ICON |
| ic_settings.xml | SETTINGS_BUTTON_ICON |
| ic_home.xml | HOME_BUTTON_ICON |
| ic_like.xml | LIKE_BUTTON_ICON |
| ic_like_filled.xml | LIKE_BUTTON_ICON_ACTIVE |
| ic_dislike.xml | DISLIKE_BUTTON_ICON |
| ic_dislike_filled.xml | DISLIKE_BUTTON_ICON_ACTIVE |
| ic_ban.xml | BAN_BUTTON_ICON |
| ic_ban_filled.xml | BAN_BUTTON_ICON_ACTIVE |
| ic_shuffle.xml | SHUFFLE_BUTTON_ICON |
| ic_rew.xml | REW_BUTTON_ICON |
| ic_play.xml | PLAY_BUTTON_ICON |
| ic_pause.xml | PAUSE_BUTTON_ICON |
| ic_fwd.xml | FWD_BUTTON_ICON |
| ic_repeat.xml | REPEAT_BUTTON_ICON |
| ic_plus.xml | PLUS_BUTTON_ICON |
| ic_minus.xml | MINUS_BUTTON_ICON |
| ic_dots.xml | DOTS_BUTTON_ICON |
| ic_ok.xml | OK_BUTTON_ICON |
| ic_cancel.xml | CANCEL_BUTTON_ICON |
| ic_arrow_up.xml | ARROW_UP_BUTTON_ICON |
| ic_arrow_up_dot.xml | ARROW_UP_LONGTAP_BUTTON_ICON |
| ic_arrowhead_up.xml | ARROWHEAD_UP_BUTTON_ICON |
| ic_arrowhead_down.xml | ARROWHEAD_DOWN_BUTTON_ICON |
| ic_theme_day.xml | THEME_DAY_BUTTON_ICON |
| ic_theme_night.xml | THEME_NIGHT_BUTTON_ICON |
| ic_theme_auto.xml | THEME_AUTO_BUTTON_ICON |
| ic_scale_7.xml | SCALE_7_BUTTON_ICON |
| ic_scale_9.xml | SCALE_9_BUTTON_ICON |
| ic_scale_11.xml | SCALE_11_BUTTON_ICON |
| ic_scale_auto.xml | SCALE_AUTO_BUTTON_ICON |
| ic_scale_card.xml | SCALE_CARD_BUTTON_ICON |
| ic_scale_card_ruled.xml | SCALE_CARD_BUTTON_ICON (alternative) |
| ic_scale_iso_card.xml | SCALE_ISO_CARD_BUTTON_ICON |
| ic_scale_banknote.xml | SCALE_BANKNOTE_BUTTON_ICON |
| ic_scale_coins.xml | SCALE_COINS_BUTTON_ICON |
| ic_scale_eur_5.xml | SCALE_EUR_5_BUTTON_ICON |
| ic_scale_eur_10.xml | SCALE_EUR_10_BUTTON_ICON |
| ic_scale_eur_20.xml | SCALE_EUR_20_BUTTON_ICON |
| ic_scale_eur_50.xml | SCALE_EUR_50_BUTTON_ICON |
| ic_scale_horizontal.xml | SCALE_HORIZONTAL_BUTTON_ICON |
| ic_scale_bidirectional.xml | SCALE_BIDIRECTIONAL_BUTTON_ICON |

Drop the XML files into `app/src/main/res/drawable/`.
