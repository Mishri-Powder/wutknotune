# Player playback transport

> Owner/scope: Player playback surface, media session and transport

## Playback surface and transport

PLAYBACK artwork is a noninteractive background above the passive status layer and below controls,
center-cropped without distortion. WIDE uses a full-viewport-height square physically right-aligned;
NARROW uses a safe-content-width square centered horizontally, starting exactly `24dp` below
Flip/top bar. Recompute on resize. File-only integer `artworkAlpha` is `0..255`, default `128`.
Metadata stays physically left-aligned above artwork, each field exactly one full-safe-content-width
line with terminal `…` and full accessibility text; artwork cannot narrow it. The title-to-artist
gap is `16dp`, artist-to-album `12dp`. Current typography/chapter-line requirements are defined with
shared TOC transport.

The complete control block is anchored at the bottom of the safe content after system/cutout insets
and `screenEdgeMargin`. Portrait local viewports (height greater than width) use three button rows:
equal-width `Previous/Rewind, Next/Forward`; then, after `targetSpacingMin`, equal-width
`Shuffle, Play/Pause, Repeat`; then the existing position slider and equal-width stable reaction
slots `Ban, Dislike, Like`. Landscape and square viewports retain one five-button transport row in
that original order, then the slider and reaction row. This explicit playback-only aspect rule is
independent of the general width-based WIDE/NARROW token and artwork resolution.

Every transport and reaction-slot control uses the same `geometry.playbackButtonHeight`, default
`120dp`, in both orientations; top-bar chrome retains its own dimensions. Within each row, targets
fill the usable width with equal shares after `targetSpacingMin` gaps. Constrained rows preserve the
shared minimum target width with bounded horizontal scrolling. If the controls and top bar cannot
fit vertically, retain full target heights and bounded vertical scrolling, initially at the bottom.
Metadata uses remaining space; no status slot is subtracted.

For AI-eligible Music the three reaction slots are ordinary actions. Book Title Bookmark retains its
Like slot under [Bookmarks](/Riff/LATEST/player-bookmarks); the Ban slot supplies the
[audiobook speed control](/Riff/LATEST/player-audiobook-speed) only under its own eligibility rule.
Other ineligible slots remain inert with no action or accessibility semantics. Button eligibility,
commands, short/long press and stable audiobook slot positions remain unchanged. There is no row
alpha setting; shared icon/control rendering applies.
[Shared contrast halo](/Riff/LATEST/ui-contrast-halo) owns the decorative surround of playback
metadata/status, transport/reaction/Bookmark pictograms and position shapes.

Without usable multi-chapter TOC, Previous uses the current track's effective Book/Music category to
select the validated immutable `player.rewindPreviousThresholdSeconds.audiobook` or `.music` value
captured at Media3 creation: at/below threshold select the previous active-order occurrence,
otherwise restart current; with no previous, restart current. Entry into a previous occurrence seeks
its last usable TOC marker when that binding has a usable multi-chapter TOC, otherwise zero. Forward
advances while preserving playing/paused state; at the final item Repeat wraps to first, otherwise
leaves the final item at its end, stops and starts a complete idle interval. In-file transport
preserves the current occurrence and applies the shared chapter boundaries below; crossing to
another queue item advances its occurrence without editing the revision.

System Back from PLAYBACK always reaches MAIN without exiting/backgrounding.
[Inactivity and Dim Backlight](/Riff/LATEST/player-inactivity) owns the shared idle timer, idle
navigation, input activity and local window brightness.

MAIN/PLAYBACK background primary-pointer short tap invokes visible Flip's exact surface-toggle
action only when initial down lies outside every interactive target, including disabled bounds.
Consumed input, long press, slop movement, drag, multipointer or cancellation does nothing. Use
Android tap arbitration; dispatch only on valid release. Passive metadata/artwork may not intercept
it. The shortcut has no accessibility/focus/keyboard/rotary node, applies nowhere else, and changes
no playback or navigation-memory state.

## Playback diagnostic status

PLAYBACK and MAIN reuse one shared decorative status-line UI object: regular monospace
`behavior.statusLineTextSp` (fixed `20sp`) secondary text, safe horizontal bounds, one line and
grapheme-safe terminal ellipsis. It is the lowest content layer above the solid screen background,
below artwork, metadata and controls. It reserves no layout slot, changes no measurements, has no
pointer/key/rotary handler or focus, and exposes no accessibility text, action or live region.

On both PLAYBACK and MAIN the text-layout box starts at the physical top-left corner after system
bar/cutout insets, with zero additional gap, padding or screen-edge margin. RTL does not mirror this
physical corner. The overlay is viewport-fixed, independent of control placement, scrolling, font
scale and orientation. Ordinary text shaping/ellipsis and system-safe clipping remain. Global
file-only `behavior.enableStatusLines` controls visibility; neither state reserves space or changes
interactive geometry. Its channel-specific packaged default is owned by
[project configuration](/Riff/LATEST/project-configuration#configuration-registry).

When enabled, PLAYBACK shows exactly `A %.1f V %.1f PTE %.1f RG %.1f %.1f/%s` for the current queue
occurrence, for example `A 0.3 V -0.4 PTE 0.1 RG -6.0 0.5/Disco`. A, V and PTE are the current
Media.ID's MDRG values without their confidence fields. Their missing forms are `A --`, `V --` and
`PTE --`. RG is the current Media.ID's canonical -18 LUFS MDRG gain selected by the active-queue
Album/Track policy; a missing record or required gain is `RG --`, while real zero is `RG 0.0`.

The final unlabeled `%.1f/%s` token is the confidence and GNRE-localized display name of the
highest-confidence automatic MDRG genre for the current Media.ID. It never uses embedded metadata
genres. Excluded or non-displayable genre evidence is ignored before selection; equal confidence is
resolved by ascending signed Genre.ID. Missing automatic genre data is `--/--`. Numeric formatting
is locale-independent; the localized genre spelling is preserved.

The diagnostic RG value is shown in N, I and F modes, including when N deliberately has no
ReplayGain AudioProcessor. It describes accepted MDRG metadata, not proof that gain is currently
applied. The line updates when the current occurrence, active queue, accepted MDRG, configured genre
exclusions or resolved system language changes. It causes no playback command, queue mutation,
audio-path rebuild or analysis. Player never derives missing diagnostics.
