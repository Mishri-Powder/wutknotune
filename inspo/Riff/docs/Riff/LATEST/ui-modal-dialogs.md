# Riff shared modal dialogs

> Owner/scope: typography and presentation of every app-owned local modal, including bootstrap UI.

## Permanent shared-component rule

Every current and future app-owned modal MUST use one shared Compose modal component and its shared
text-style resolver. Independent Dialog roots, copied window/action layouts and per-dialog text
sizes, font families or weights are prohibited. Scenarios supply content, typed callbacks, enabled
state and dismissal/hold policy; they do not recreate presentation. This is a permanent requirement,
not a rule limited to the dialogs present when adopted on 2026-10-08.

Title, body, name input, placeholder, hint and error MUST match the Playlist breadcrumb: exactly
`32sp`, the resolved `topBarFontFamily` and `topBarFontWeight`, normal Android fallback and user
font scale. Use the existing shared chrome resolver, not a parallel typeface implementation. Text
wraps and scrolls within the bounded body; retain complete accessible labels and safe grapheme
bounds.

## Window and actions

Use a maximum `420dp` width and `10dp` body padding, common physical density, resolved theme,
system-bar/cutout and IME insets, and existing corner/border/color tokens. Capture root density and
layout direction into the separate Dialog window. Body scrolling MUST keep actions visible.

Actions use adopted `ic_ok` and `ic_cancel` through the existing common icon-control renderer,
standard icon padding, `navTarget`, and at least `targetSpacingMin` separation. Confirmation is
physical left; Cancel, when present, is physical right even in RTL. A sole confirmation remains
left. Icons expose exact accessible action names such as OK, Remove, Reset, Cancel or Forget and
reset to Internal. Recovery intent and required hold instructions remain readable in the body. No
text-button substitute or new asset is introduced.

Dismissal is explicit scenario policy. Non-dismissible warnings/waiting cannot close through Back,
outside input or idle. Before confirmation, cancellable dialogs discard the draft without mutation
on Cancel/Back/outside. After playlist OK is accepted, its durable write is non-cancellable: disable
confirmation and Cancel, and ignore Back/outside/idle departure until completion. Validation/write
failure retains the draft and displays an accessible error through shared modal typography. Body
taps never count as outside taps. Busy/invalid actions remain disabled; recovery holds retain
equivalent accessibility activation. Presentation MUST NOT weaken command-time mode, motion or
storage gates.

## Scoped semantics and bootstrap boundary

Current scenarios are New, Rename and Delete Playlist (including Bookmarks), Remove media folder,
release-age acknowledgement, aggregate SAF listing/unavailability notice, selected-storage waiting,
and Reset to Internal confirmation. Their effects remain owned by
[queues](/Riff/LATEST/player-queues), [Media folders](/Riff/LATEST/settings-screen-scope-and-rows),
[SAF warning](/Riff/LATEST/saf-empty-listing-workaround), [distribution](/Riff/LATEST/distribution),
[Player storage](/Riff/LATEST/player-storage) and
[application modes](/Riff/LATEST/application-modes).

Before selected-root admission, use only the already available packaged/bootstrap configuration and
palette; the common component performs no storage, IPC or configuration loading. Reset remains a
two-step, separately authorized recovery. Actual AAOS/projected Android Auto retain supported host
surfaces and restrictions; local modal reuse is not host integration. Android-owned SAF pickers are
outside this app-owned presentation contract.
