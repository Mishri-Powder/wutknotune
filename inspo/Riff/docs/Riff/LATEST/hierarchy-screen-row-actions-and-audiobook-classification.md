# Riff HIERARCHY_SCREEN contract — ROW_ACTIONS and audiobook classification

> Owner/scope: bounded section of Riff/LATEST/hierarchy-screen

## ROW_ACTIONS and audiobook classification

Unavailable track rows disable Play and Add but retain their indexed title. Music reaction controls
remain available when otherwise eligible; a playlist/bookmark Remove and collection Delete remain
available under ordinary policy. A Bookmarks collection row opens its marks and keeps Play visible
but disabled. An available Bookmarks mark row opens the frozen queue at the exact occurrence and
position. Its More menu removes that mark, and deleting the last mark deletes the collection.

ROW_ACTIONS is one shared page with fixed navigation chrome and a bounded scrollable action body
when content does not fit. It renders the exact actions for the selected row kind, followed where
applicable by two equal-width icon-only toggles: Music (`ic_genre`) and Book (`ic_audiobook`). Each
uses the resolved transport-button height, `geometry.playbackButtonHeight`, ordinary 10dp padding
and the shared icon/action renderer. No Auto button is rendered. Neither selected is Auto; pressing
the selected toggle returns to Auto. Both toggles remain enabled on editable objects: pressing the
other selects it directly, and pressing the selected one clears it to Auto. Touch, accessibility,
keyboard and rotary use the same transition. Artist/Author and Genre disable both toggles and write
nothing. A pending commit disables both. Playlist containers have no selector; playlist track
occurrences edit the same Media.id setting. These are presentation states of the existing
AUTO/MUSIC/BOOK draft and do not change commit-before-exit or classification precedence.

Selection does not write immediately. Every deliberate exit, including Back, Play, Add and future
exit actions, runs one parameterized commit-before-exit transaction exactly once. On success the
intended exit continues; on failure the prior durable value remains, the exit action does not run
and an error is shown. Folder/Track/Album identity, inheritance, Auto deletion and Album member
cleanup follow the central classification contract.

ROW_ACTIONS renders no dynamic classification explanation or saved-cause element. The passive
advisory directly follows the toggles:
`Changing a book to Music manually does not make it available for AI recommendations.` It wraps
without truncation at its natural text height; constrained bodies scroll rather than shrink buttons
or clip the message. The advisory uses safe horizontal content padding, centers its complete text
and adds no action, focus target or live-region semantics. It remains present for disabled
Artist/Author and Genre selectors. SETTINGS_SCREEN and playlist-container actions do not show it.

Play is first when supported. Add to playlist remains available under application-policy and
technical gates in either content mode. Album/Folder/Artist/Genre Add materializes its selected
complete source. Track Add targets that Media.id. Music playlist rows support Play, Rename and
Delete; Bookmarks collection rows show disabled Play and enabled Delete under the ordinary mutation
policy. Playlist-track occurrences support Play, Add and Remove. Delete requires named confirmation.
No nested playlist, reorder or AI placeholder exists. Mutation/name entry is disabled when the
automotive policy is RESTRICTED or UNKNOWN.

## A-Z search

A-Z operates only on immediate comparison names under the saved leading-article policy, preserves
source order/identity/actions and never searches descendants or changes queues/media. It uses the
central NFC, extended-grapheme, simple-case mapping and exact Latin/Cyrillic/numeric/# group table.
No IME, free text, wildcard or end-of-name constraint exists.

The bottom safe A-Z panel uses the central WIDE eight-column and NARROW four-column layouts, exact
`24dp` gaps and `100dp`/`76dp` target heights. A row is one physically displayed horizontal interval
of buttons; a script block occupies one WIDE row or two NARROW rows. Follow the
[central keypad order and row-visibility rule](/Riff/LATEST/player-az-navigation): Cyrillic, Latin,
numeric/control from top to bottom. Form physical rows before hiding each fully unavailable row. The
final row containing Backspace always remains, including all its inactive numeric buttons and the
inactive Backspace. Keep every slot inside a shown row and never repack groups between rows.
Backspace retains its exact double-column span, including the intervening gap, and is disabled when
the draft is empty.

Unavailable keys are composited as plate-plus-glyph at behavior.azDisabledAlpha / 255 (default 20%),
without hatching or local darkening, with disabled semantics and no activation under the UI config
and icon contract. Unavailable controls block action pass-through. Exposed ordinary shared-list rows
retain normal scrolling and actions, but taps beginning in inter-control gaps do not activate
underlying controls.

Every group choice publishes an immutable preview snapshot off the main/playback-critical threads.
Cancel restores the latest saved list snapshot. Backspace and breadcrumb-prefix selection save both
the current and remembered filter, including an empty root prefix, without closing A-Z. Accepted
contextual Play saves the source draft and preview anchor before queue preparation, including
ROW_ACTIONS and bookmark marks; failure retains that navigation save. The
[central A-Z owner](/Riff/LATEST/player-az-navigation) governs the return snapshot and later draft
cancellation. The single A-Z chrome control opens the keypad and, while it is open, commits the
draft and closes it while preserving the applicable list's first visible row, pixel offset and
surviving focus. The ordinary list retains complete interaction while A-Z is open.

Per-profile Auto search uses only measured ordinary and panel-obscured viewports. It receives one
opening attempt for every navigation activation, including return to a visited parent or ancestor
through Back, Parent or breadcrumb and an ordinary current-container breadcrumb that changes the
committed A-Z prefix, when the complete immediate source content before filtering is at least three
ordinary viewports. Re-entry restores the destination filter, first visible row, pixel offset and
focus, then opens A-Z with that filter as its draft. The A-Z chrome control is the sole explicit
keypad open/close operation; breadcrumb prefixes retain their ordinary filtering and navigation
meaning. Closing or applying A-Z consumes the attempt for the current visit. Reload, refresh,
resize, Backspace, draft filter changes, auxiliary-menu return and a breadcrumb that leaves both
path and committed prefix unchanged do not retrigger it; an ordinary breadcrumb that changes the
committed prefix is a new navigation activation even when the path is unchanged. An explicit group
choice may auto-apply only a nonempty result fitting one ordinary viewport; automatic opening and
breadcrumb navigation never auto-apply. Manual A-Z open/apply/close remains available when Auto
search is off.

## Appearance, accessibility and safety

Apply safe drawing insets and central UI_CONFIG geometry. Text uses sp and supports font scale
through `2.0`; physical geometry uses true dp. Row text is grapheme-safe, with complete accessible
labels. Day/night themes meet central contrast rules. Focus, selection, disabled state and reaction
state are not conveyed by color alone.

TalkBack, rotary, directional keyboard and touch traverse chrome, rows and actions in visual logical
order. Disabled controls expose no activation. RTL mirrors physical placement without changing
semantic order. No animation, flashing, unsolicited navigation beyond specified Auto search
completion, custom driving-state bypass or hidden Settings entry exists.

## Verification

Verification parameterizes the same renderer/controller/actions across every root, descendant,
content mode and responsive form. It covers exact membership/order, Albums/Titles handoff, complete
Folders, mixed Playlists, provisional Genres, contextual Play/Add, book reaction omission, shared
compact playlist/Bookmarks rows, title expansion to More and direct-action guards, unified entity
color, row geometry, continuous scrolling, navigation restoration, A-Z open/close anchor
preservation, saved-filter restoration after Backspace, current/ancestor/empty breadcrumb and
contextual Play with pending snapshots or queue failure, shared-order double-width Backspace,
one-attempt Auto-search re-entry through Back/Parent/path-or-prefix breadcrumb navigation,
source-based thresholds, same-visit non-retriggering, ROW_ACTIONS draft/commit/footer placement,
disabled selectors, application-policy gates, safe insets, font scales, day/night contrast, RTL,
TalkBack, rotary, keyboard and source/profile/mode changes.
