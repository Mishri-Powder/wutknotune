# Riff MAIN_SCREEN contract — Scope and platform boundary

> Owner/scope: bounded section of Riff/LATEST/main-screen

## Scope and platform boundary

`MAIN_SCREEN` is the direct-HU root surface. It contains launchers, the per-profile Books
content-mode toggle and the passive Indexer status line. It never starts playback, changes a queue
or reaction, or requests text input. Actual AAOS and projected Android Auto use supported host media
surfaces; they do not project this custom activity.

After the selected-root startup gate succeeds, on custom-UI startup proven absence of every
persisted read tree grant bypasses the initial MAIN_SCREEN presentation and enters the Settings
Media Folders selector under the SETTINGS_SCREEN contract. This recovery route creates no additional
MAIN_SCREEN control or Settings entry point; cancellation may return to the stable empty
MAIN_SCREEN.

An unavailable selected external root shows the central bootstrap waiting modal before MAIN_SCREEN,
zero-grant onboarding or release-age acknowledgement.

The local root composition also renders the
[SAF platform warning](/Riff/LATEST/saf-empty-listing-workaround#scan-retention-and-warning). Its
sole OK acknowledgement leaves ordinary UI and the empty MAIN_SCREEN available; zero admitted roots
do not mean zero persisted grants and never cause an application exit.

## Stable controls and order

Seven positions always appear in logical order: Albums, Artists, Genres, Playlists, Folders, Books,
AI. MUSIC uses Albums and Artists. AUDIOBOOK relabels the same entity launchers Titles and Authors.
Genres and Folders retain their labels. Books retains its label and audiobook icon.

Enabled launchers open their root in one tap without autoplay. Playlists/Bookmarks always opens the
collection list, even immediately after creating a playlist or when the last visited view was one
collection's contents. Restore the collection-list root's own filter/position, never a child list's
state. Ordinary row descent and successful Add-to-playlist destination navigation remain unchanged.
Books is a checked toggle: unchecked selects MUSIC, checked selects AUDIOBOOK. It saves immediately
for the active profile and stays on MAIN. Player/process restart restores the saved checked state.
Profile 1 Books → Profile 2 Music → Profile 1 restores Books; profile selection never resets another
profile's mode. It remains enabled for an empty library and never changes the active queue or
position.

AUDIOBOOK relabels Playlists to Bookmarks and enables it only when the active profile owns at least
one Bookmarks collection containing a mark; AI stays disabled. MUSIC enables Playlists only when the
active profile owns at least one nonempty permanent Music playlist. With none, the launcher stays
visible but disabled. Temporary media unavailability alone does not disable a stored collection.
Creating/deleting a collection or changing profile updates this state from Player-owned data without
a library scan. MUSIC enables AI only when the current validated MDRG has at least one usable A/V
pair and its matching heatmap is ready. Disabled positions use the shared
`behavior.azDisabledAlpha / 255`, expose no pointer/accessibility/programmatic activation and block
background-Flip fall-through.

Albums/Titles and Artists/Authors remain visible. They are disabled only when the current accepted
MDIA lacks a valid generation-matching combined catalog. For an accepted cohort, Player retains
paired active-profile MUSIC/AUDIOBOOK projections and Books selects one atomically; the labels, rows
and enabled state change together without an intermediate empty root or transient catalog
disablement. Genres stays enabled during indexing for the progressive vocabulary defined by the
central contract. Folders remains the complete accessible hierarchy in both modes.

## Top bar and background Flip

The top bar contains enabled Flip at logical START, an empty nonsemantic flexible center and
mode-resolved Settings at END. MAIN_SCREEN is the sole Settings entry. Settings availability and
gesture and recovery behavior follow the application-mode contract. Phone requires deliberate long
press with equivalent accessibility activation; short tap does not enter. Entry strategy is a
central code policy, never a user setting; a positive driving restriction has no bypass.

A short primary-pointer tap beginning outside every tile and top-bar target dispatches the same
surface toggle as Flip. Consumed input, target input including disabled targets, long press, slop
movement, drag, multipointer input or cancellation does nothing. The background shortcut has no
focus or accessibility node.

System Back on MAIN_SCREEN is not intercepted by Riff. The root launcher Activity follows Android
12+ system behavior and moves the Riff task to the background, revealing the prior system task or
Launcher. Playback remains owned by the MediaSessionService and continues when requested.
Opening/restoring MAIN never autoplays.

## Geometry and appearance

Apply safe drawing insets, then at least `24dp` around interactive content. Top-bar targets are at
least `80dp × 80dp`. Supported direct-HU tiles are `240dp` square with a centered `160dp × 160dp`
icon square, exact `10dp` content padding, at least `36sp` one-line labels and `24dp` gaps.
Constrained surfaces retain a `128dp` tile floor and use bounded scrolling instead of overlap or
proportional scaling.

Root width at least `800dp` uses centered rows `[4,3]`; smaller width uses centered rows
`[2,2,2,1]`. Stable logical order is unchanged in RTL, which mirrors physical placement. Supported
direct-HU windows show all seven positions without scrolling.

Tiles are complete single Button nodes; Books is one checked toggle node. Icons are decorative when
the label conveys the action. Press, focus, selected and disabled states do not resize or move
positions. Use central day/night roles and contrast. No animation, video, flashing, automatic text
scrolling, badges, counts, subtitles, recommendations or advertisements appear.

For every non-final compile-time ReleaseIdentity, MAIN_SCREEN alone paints one passive translucent
diagonal overlay above its complete local surface with the exact immutable label
`<Channel>: YYMMDD`; allowed visible channel names and date ownership follow the central
distribution rule. A FINAL identity has no overlay or placeholder. The overlay does not participate
in measurement, move or obscure navigation structure, alter enabled state, consume
pointer/key/rotary input, receive focus, expose an accessibility node/live region, block background
Flip, or reserve space. It has no animation and remains absent from child screens, PLAYBACK_SCREEN
and host-rendered AAOS/projected surfaces.

At the central three-calendar-month boundary, each newly launched DEBUG, TEST, ALPHA or BETA Player
process presents the one required non-dismissible age-warning dialog over MAIN_SCREEN. The dialog
blocks the underlying MAIN_SCREEN only until its sole `OK` action; it does not substitute an
expiry-disabled screen and Settings has no special exception. After acknowledgement, Flip,
background Flip and all seven positions use only their ordinary technical, catalog and
application-mode gates. RC and FINAL never show the dialog. The non-FINAL release overlay remains
above the complete local surface before and after acknowledgement, and the dialog changes no
playback/service state.

## Status line

MAIN renders exactly one nonempty activity status only when global file-only
`behavior.enableStatusLines` is true. PLAYBACK alone reuses the exact shared decorative status-line
UI object for its diagnostic contract; no other child screen renders or reserves it. It uses the
vocabulary and priority in the central requirements: actionable/unavailable and active Indexer
Binder text precede Player's lower-priority artifact-loading or validated-empty-library fallback.
Player never estimates or recomputes Indexer progress. Indexer owns the
[publication-period counts and rates](/Riff/LATEST/indexer-staging#progress-and-period-rates).
Unknown totals are omitted. Status updates do not wait for a new catalog cohort. A successful
nonempty settled library leaves the line empty.

The status uses regular monospace `20sp` secondary text and grapheme-safe ellipsis. Its layout box
stays at the physical top-left system-safe corner with zero extra gap/padding, also in RTL. It
paints above the solid background, below grid/chrome; it reserves no space and never shifts tiles.
It has no animation, scrolling, input handler, focus, accessibility text/action or live-region
semantics. No status includes an ETA, pathname, URI or document ID. The filename exceptions are the
bounded `Full Media.ID scan (<reason>): <filename>`, `Full loudness analysis: <filename>`,
`Suspect file: <filename>` and `FERR file: <filename>` details after cumulative counts and period
speed. Indexer supplies only the selected binding display name; Player displays Binder text verbatim
with grapheme-safe ellipsis.
