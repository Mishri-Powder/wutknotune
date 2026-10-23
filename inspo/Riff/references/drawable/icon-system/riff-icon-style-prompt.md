# Regenerate the Riff icon set in a new visual style

Paste this whole file into a fresh conversation. It is self-contained: no
attachments, no prior context, no access to the existing drawings is needed.

You are producing a complete icon set for **Riff**, an Android media player.
The set is 48 drawables. It already exists in a plain outline style; the point
of this prompt is to rebuild it end to end in whatever visual style the user
asks for, without losing the constraints that make it work in the player.

Work through the steps in order. Two of them are hard stops where you show
work and wait.

---

## Step 1 — Ask for a design direction, then stop

Ask one question: what should the set look like? Offer examples to make the
question concrete, then wait for an answer. Do not draw anything yet.

- "Pick it for me"
- "Star Trek LCARS panel style"
- "Atompunk"
- "Mesoamerican codex glyphs"
- "A 70s hi-fi cassette deck"
- "Brutalist, heavy, all right angles"

Treat the answer as binding, including when it is one word. "Atompunk" is a
complete brief; do not interrogate the user for a palette, a mood board or a
list of adjectives before starting. Ask at most one follow-up, and only where
the brief is genuinely ambiguous about *construction* — for instance whether a
codex direction means outlined cartouches or solid stamped forms. Style
questions you can answer yourself, answer yourself.

## Step 2 — Write the style rules down before drawing

Once the direction is known, write a short block of rules that will make 48
icons look like one set rather than 48 separate drawings. Cover at least:

- **Construction** — outline, solid, or a defined mix (and if a mix, exactly
  which categories get which).
- **Weight** — one stroke width in viewport units, used everywhere. Not two.
- **Geometry** — the vocabulary: what angles are allowed, what corner
  treatment, whether curves are circular arcs or free curves, what terminals
  look like.
- **A motif** — one identifying gesture that recurs across the set (a notch, a
  chamfer, a doubled line, a particular corner). This is what makes a style
  read as deliberate rather than generic.

Show this block to the user with the samples in Step 4. Everything after this
point must obey it; when a later icon fights the rules, change the rules and
re-emit the whole set, never make an exception for one icon.

## Step 3 — Pick the grid and write it down

The existing set uses a 24-unit square viewport with a 1.8-unit stroke. That
is the Android convention and it is a fine default, but it is tight: at 24
units, ornament turns to mush. A 64-unit grid with a proportionally heavier
stroke gives the same optical weight with room for detail, and some styles
will need it. Choose deliberately, state the choice, and use it for all 48.

Whatever you pick, all dimensions in the rest of this prompt are quoted in
24-unit terms. Scale them.

## Step 4 — Draw three icons and stop again

Draw exactly three: `album`, `play`, `settings`. They are chosen to stress the
style in different directions — a nest of circles, a plain triangle, and a
form dense enough to fall apart at small sizes. Render them at the largest and
smallest sizes they will be used at, show them with the Step 2 rule block, and
wait for approval.

If the user asks for changes, change the rules and redraw all three. Do not
proceed to the full set on a maybe.

## Step 5 — Build the set

Only after approval. Generate all 48 icons in the approved style, then
verify (below), then deliver (below).

## Step 6 — Take corrections

After delivering, ask what should change, and keep asking until the user says
it is done. Three kinds of correction, handled differently:

- **Style-wide** ("strokes too heavy", "lose the chamfer") — change the Step 2
  rules, re-emit every icon, re-verify. Never patch a few files and leave the
  rest; that is how a set drifts apart.
- **Single icon** ("the gear reads as a flower") — redraw that one, then put it
  back on the contact sheet and confirm it still belongs.
- **Roster** ("add a sleep timer icon") — draw it in the established style and
  add it to the README table.

If a correction conflicts with a constraint below — a two-colour icon, an
active state that changes size — say so plainly, explain the consequence, and
offer the nearest thing that works. These constraints come from the player's
rendering pipeline, not from taste.

---

## Constraints that hold regardless of style

These apply to all 48 without exception. A set that violates them is not
shippable no matter how it looks.

1. **Square viewport.** `viewportWidth == viewportHeight`, and all four of
   `android:width`, `android:height`, `android:viewportWidth`,
   `android:viewportHeight` present in every file. Riff's CI rejects
   non-square icons, and the player maps the viewport onto a square: a
   non-square viewport renders distorted.
2. **Monochrome and tintable.** Every path is `#FFFFFFFF`, whether stroked or
   filled. No gradients, no second colour, no baked skin colours. The player
   tints at draw time to `buttonContentColor`, and it tints light-on-dark in
   one skin and dark-on-light in the other. Anything relying on hue, or on a
   specific background, breaks.
3. **Knockouts, not painted holes.** An interior mark on a solid shape must be
   cut out with `android:fillType="evenOdd"` in a single path. Painting it in
   the background colour fails on the player's translucent button surface.
   Note in the README that `evenOdd` needs API 24+.
4. **Paths only.** No `<text>` element, no font dependency, no traced or
   auto-vectorised outlines — including for numerals and currency signs.
   Every glyph is constructed geometry.
5. **Nothing crosses the viewport edge.** Account for stroke width: a path at
   the boundary sticks out by half the stroke. Keep the whole set to a
   consistent optical margin.
6. **Legible at both ends.** The set is used on ~205px mixed buttons and on
   ~72px icon-only buttons, and the previews are judged at 24dp. Detail that
   dissolves at the small end is a defect, not a trade-off.

## The roster

48 files, named `ic_<name>.xml`.

**Library navigation**

| file | meaning |
| :- | :- |
| `ic_album` | browse by album |
| `ic_artist` | browse by artist |
| `ic_genre` | browse by genre |
| `ic_playlist` | browse playlists |
| `ic_ai` | AI-assisted selection |
| `ic_folder` | browse the filesystem |

**Chrome**

| file | meaning |
| :- | :- |
| `ic_flip` | swap between the two main screens |
| `ic_settings` | open settings |
| `ic_home` | the breadcrumb root |
| `ic_dots` | more actions / overflow |

**Transport**

| file | meaning |
| :- | :- |
| `ic_play` / `ic_pause` | start and suspend playback |
| `ic_rew` / `ic_fwd` | previous and next |
| `ic_shuffle` | randomise order |
| `ic_repeat` | loop |

**Reactions** — each has an active variant

| file | meaning |
| :- | :- |
| `ic_like` / `ic_like_filled` | approve |
| `ic_dislike` / `ic_dislike_filled` | disapprove |
| `ic_ban` / `ic_ban_filled` | remove from rotation permanently |

**Controls**

| file | meaning |
| :- | :- |
| `ic_plus` / `ic_minus` | increase / decrease something. NOT volume; no speaker |
| `ic_ok` | apply and close; no text |
| `ic_cancel` | discard and close; no text |

**Affordance**

| file | meaning |
| :- | :- |
| `ic_arrow_up` | a large upward arrow, in the spirit of U+21E7 |
| `ic_arrow_up_dot` | the same arrow carrying an unmissable mark meaning "long-tap available here" |
| `ic_arrowhead_up` / `ic_arrowhead_down` | bare triangular arrowheads, no shaft |

**Theme**

| file | meaning |
| :- | :- |
| `ic_theme_day` / `ic_theme_night` / `ic_theme_auto` | force day, force night, follow the system |

**Screen scale** — how the player learns the physical size of the display

| file | meaning |
| :- | :- |
| `ic_scale_7` / `ic_scale_9` / `ic_scale_11` | assume a 7, 9 or 11 inch screen |
| `ic_scale_auto` | trust what the head unit reports |
| `ic_scale_horizontal` | scaling along one axis |
| `ic_scale_bidirectional` | scaling along both axes |
| `ic_scale_card` | open the calibration screen |
| `ic_scale_card_ruled` | a lighter alternative drawing of the same thing |
| `ic_scale_iso_card` | an ISO/IEC 7810 ID-1 card, as a measuring reference |
| `ic_scale_banknote` | a banknote, ditto |
| `ic_scale_coins` | a stack of coins, ditto |
| `ic_scale_eur_5` / `_10` / `_20` / `_50` | a specific euro note, by denomination |

---

## Groups that need care

### Active-state variants

`ic_like` / `ic_dislike` / `ic_ban` each pair with a `_filled` version, and
`ic_arrow_up` pairs with `ic_arrow_up_dot`. In every pair the two drawings
**must have identical outer bounds** — measure the rasterised alpha bounding
box and compare, do not eyeball it. If the icon changes size when a user taps
like, the layout twitches.

The usual device is fill plus stroke on the same geometry, which preserves the
silhouette exactly. If your style is *already* solid, "filled" cannot mean
adding fill: pick another device for the active state — a knocked-out
counter, a doubled outline, an added mark — and apply the same device to all
four pairs.

### The theme trio

One idea in three states, drawn as a family with the same construction,
differing only in how much is filled: day light, night heavy, auto visibly
both. The trap is reaching for colour. Everything is tinted a single colour,
so a pale sun and a dark moon are the same drawing once tinted. Day and night
must separate by **mass** — outline against solid — and the pair must read
correctly tinted light-on-dark *and* dark-on-light. Check both. All three
share one bounding box.

### Numerals

Seven icons carry digits: `ic_scale_7`, `_9`, `_11` and the four
`ic_scale_eur_*`. Build them from a parameterised glyph constructor —
cap height and width in, paths out — so every numeral in the set comes from
one skeleton rather than being drawn seven times. Geometry only; no font.

- Put every numeral on **one cap line and one baseline** and verify by
  measuring the rendered bounding box of each. This is where sets fall apart
  invisibly.
- Where two digits share a viewport, **condense their width, do not reduce
  their height**. Height is what makes numerals read as the same size; width
  is what makes them fit.
- The euro denominations carry no currency sign. A sign plus two digits forces
  every glyph small enough that counters close.
- Digits are Western Arabic; say so in the README, since a device set to
  another numeral system will not match.

### Calibration references

`ic_scale_card`, `ic_scale_iso_card`, `ic_scale_banknote`, `ic_scale_coins`
and the four euro notes all name a physical object held against the glass.
They appear in one list, so separate them on **two axes at once** — aspect
ratio *and* interior mark — never on interior detail alone, which is what
disappears first as the icon shrinks. Draw real proportions where a standard
exists: ID-1 is 1.585, a banknote is visibly longer.

`ic_scale_card` is the generic calibrate affordance and must carry no payment
cues — no stripe, no chip, no signature panel. `ic_scale_iso_card` names the
card as a specific object, so a stripe and chip belong there; that is what
separates the two. Neither is an offer to pay for anything.

### Collisions inside the set

Every one of these was hit while building the current version. Check each
before accepting a drawing:

- A crossed four-way arrow is `ic_plus` with arrowheads. Break the crossing —
  leave a gap at the centre — or find another construction.
- A tall triangle is `ic_play` rotated. Give `ic_arrowhead_up/down` a squat,
  wide aspect no other icon uses.
- `ic_arrowhead_up` must not read as `ic_arrow_up` minus its shaft; the aspect
  ratio has to do that work.
- `ic_ban` must not be a decorated `ic_dislike`. Ban is a different action,
  not a stronger dislike, and should look unrelated.
- `ic_plus` and `ic_minus` carry no speaker, no slider, no volume cue.
- `ic_scale_eur_10` and `ic_scale_11` differ by one glyph while meaning a
  banknote and a screen size. Nothing can be done about that in the drawing;
  note it in the README.

---

## Method

Generate the files from a script rather than writing 48 XML files by hand, so
that a change to the style rules is one edit and a re-run, not 48 edits. Keep
the glyph constructor separate from the icon table.

If the script wipes and rebuilds its output directory, keep the README in a
source file outside that directory and copy it back after each run — otherwise
it gets deleted silently on every regeneration.

## Verify before delivering — by measuring, not by looking

Rasterise each icon and check numerically. Eyeballing misses exactly the
defects that matter, and several of the following were caught only by
measurement:

1. Parse every XML. Confirm `viewportWidth == viewportHeight`, that the value
   is the same in all 48, and that all four dimension attributes are present.
2. Confirm every path is `#FFFFFFFF` and nothing carries a gradient or a
   second colour.
3. Compare alpha bounding boxes within each variant pair — the three
   reactions, the two arrows, the three theme icons. Identical, to the pixel.
4. Measure the rendered top and bottom of all seven numerals. They must match.
   **A cubic curve never reaches its control points**: control points placed
   on the cap line leave the apex roughly a quarter of the way down, which
   makes that digit read a size smaller than its neighbours. Put the controls
   above the line until the curve touches it. Round terminals should overshoot
   a flat baseline slightly, or they look like they float.
5. Check every counter and gap at the smallest size. A stroked ring's opening
   is its width minus one stroke width; below about 3 units it fills in at
   24dp. Parallel strokes need a gap of roughly 2 units beyond the stroke
   width to stay separate. Where a shape cannot survive, change the shape —
   `ic_dots` is fill-only for exactly this reason.
6. Compute ink coverage (share of the viewport painted) for each icon and
   compare across the set. Wildly heavier or lighter icons stand out on a
   button row. A reasonable spread is 9-25%; investigate anything outside it.
7. Confirm nothing crosses the viewport edge, stroke included.
8. View all 48 together as a contact sheet and judge them as a set, not one at
   a time. This is where family resemblance either holds or does not.

## Deliverables

- One `ic_<name>.xml` per roster entry, ready to drop into
  `app/src/main/res/drawable/`.
- A README recording the style rules, the grid, the file-to-constant table,
  and every deviation with its reason — the tint convention, the identical-
  bounds rule for pairs, the `evenOdd` API floor, the fill-only exceptions.
  A constraint that lives only in the drawings gets broken by the next person
  to add an icon.
- Contact-sheet PNGs of the full set on a button-like surface, in **both** a
  light and a dark treatment, since the drawables are tinted and must be
  judged both ways. Frame each icon so it occupies 75% of its box, matching
  the player's real button geometry.
- Everything above in a ZIP, with the PNGs included.
- The generation script, if the user wants it — it is what makes the set
  re-emittable at a different weight or grid.
