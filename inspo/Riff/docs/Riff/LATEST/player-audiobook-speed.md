# Player audiobook playback speed

> Owner/scope: profile speed, audiobook audio parameters and the local playback speed action.

## Profile preference and execution

Each built-in profile owns one speed shared by all audiobooks, persisted between sessions in
Player-owned state. It is not a per-book, per-queue or installation setting. Default is 1.0. The
complete fixed set is 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4 and 1.5. Persist the selected value as
integer tenths, never by repeated floating-point addition. A successful change is a semantic Player
transaction; setting the already stored value is a no-op.

In the active profile's AUDIOBOOK content mode, an effective Book current occurrence uses Media3
`PlaybackParameters(speed, 1.0f)` and `skipSilenceEnabled = true`, including at speed 1.0. MUSIC
mode, an effective Music occurrence, or no current occurrence uses `PlaybackParameters(1.0f, 1.0f)`
and disables silence skipping. Mixed queues resolve this per occurrence; the queue is never
rewritten. Apply the current profile's persisted preference at cold service start, profile/mode/
classification change, occurrence transition and audio-path recreation. Queue source-profile
provenance still owns history and bookmarks; it does not override the current profile's speed.

Commands capture the profile and store that received the gesture, serialize off main, commit before
publishing the new label/audio preference, and reject ended/replaced store sessions. Old-profile
completions cannot overwrite the current UI. Rapid short presses retain their order; a reset in that
order writes 1.0. Storage/write failure retains the durable value and reports failure. Profile
switch restores that profile's own speed without modifying any other profile or the active queue.
Current Player schema and pre-release recovery are owned by [state](/Riff/LATEST/player-state).

## Shared playback control

Only while AUDIOBOOK mode has an effective Book current occurrence, the Ban slot contains the speed
action. It uses exactly the existing shared action-button component, complete target, allocation,
padding, focus, disabled semantics, gestures and contrast halo; no separate speed button or copied
layout is permitted. Existing Like-to-Bookmark substitution follows its own Title-context rule.

Each short press advances by 0.1, with 1.5 wrapping to 0.7. Long press and its accessibility
equivalent reset to 1.0 and do not also advance. Render `%1.1fx` with locale-independent decimal
point, exactly one fractional digit and a trailing `x`: `0.7x` through `1.5x`. Text MUST use bold
monospace (weight 700), normal sp/fontScale and the shared driving-label minimum font size. The
complete accessible name includes current speed and the next action; reset has its own accessible
label. Text selection does not change the stable Ban bounds. The fixed four-character label is not
shortened by the general user-string length limit; ordinary constrained-layout overflow still
preserves the complete accessible value. Unavailable storage disables the control and its commands.

Phone and direct Automotive use the same local behavior and existing transport policy. Actual AAOS
and projected Android Auto use supported host surfaces under
[application modes](/Riff/LATEST/application-modes-modes); no custom speed widget, new motion bypass
or Settings preference is introduced.

## Verification

Verify all steps/wrap/reset, locale formatting, persisted reopen and independent profiles, no-op
revision, ordered gestures and stale-result rejection. Verify unity pitch, silence skipping at every
Book speed, ordinary Music parameters, mixed occurrence transitions and profile/mode changes.
Exercise the common UI renderer in both forms/themes with RTL, font scale, disabled actions,
accessibility and optional-hold isolation. Source refactoring must preserve existing navigation,
playlist, settings, modal and playback behavior. Audible output and supported-host acceptance
require their own evidence.
