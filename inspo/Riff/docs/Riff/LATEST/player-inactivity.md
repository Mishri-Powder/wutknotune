# Player inactivity and Dim Backlight

> Owner/scope: local Player Activity inactivity, resolved theme and window brightness.

## Settings and defaults

Theme is profile-scoped and defaults to Auto (`SYSTEM`). Its single icon-only button cycles Day →
Night → Auto → Day using the adopted day/night/auto icons. Auto follows the current Android night
state. The rendered theme includes the primary draft preview while Settings is open.

Dim Backlight is installation-scoped, common to all profiles, and defaults to **Auto**. Its More row
immediately precedes ReplayGain. One icon-only button cycles Off → Day → Night → Auto → Off. Off
uses `ic_ban` with the operable inactive color and no selected circle; the button remains enabled
and reachable. Day, Night and Auto use their corresponding active icons. Accessibility exposes the
setting name, current value and next action; focus and input remain available in every value.

Off disables dimming. Day permits dimming only when the currently rendered theme is day; Night
permits it only when that theme is night. Auto permits both. Applicability depends on the resolved
rendered theme, including Theme Auto, independently of how it was selected. More uses its ordinary
independent draft/OK/Cancel transaction. Dim edits take effect only after More OK; primary Cancel
cannot undo a committed More value. Installation preferences persist this value without changing
Player queue/profile schema.

## One inactivity interval

The existing file-only `idleTimeoutSeconds` is the sole timer, default 30 seconds, range 1..86400.
User touch, key, rotary, generic-motion and accessibility interaction immediately release the window
brightness override and restart the complete interval. Input executes its ordinary action on the
same interaction. Held touch and ongoing user movement keep the UI active. Playback/catalog/status
updates are not user activity.

Resume and regained window focus/fullscreen eligibility start a complete interval at system
brightness. Transition to stopped playback and non-looping queue completion also start a complete
interval. Timeout shows PLAYBACK while actively playing, otherwise MAIN, discarding open drafts
under ordinary Back rules. Brightness policy is evaluated even if the destination is already open.
Pausing audio does not make the Activity inactive. SAF removal/mandatory migration confirmation,
active migration and startup acknowledgement block idle departure and dimming.

## Window brightness policy

Dimming requires a resumed, focused, fullscreen local Player Activity with an applicable Dim value.
Multi-window, floating/freeform window, picture-in-picture, background, focus loss and destruction
release the override to Android's system policy. Host AAOS/Android Auto surfaces retain their
supported rendering; Riff does not alter a host window or introduce a motion-gate exception.

At timeout resolve `behavior.dimBacklightPercent` from the rendered day/night theme, then set
`WindowManager.LayoutParams.screenBrightness` to `systemBaseline * percent / 100 / 255`. Percent is
a finite number in 0..100; both packaged theme defaults are 30. Read the independent system
baseline, never Riff's already-dimmed value. For example baseline 128 and percent 10 produce 12.8
units on the 0..255 scale, approximately 5% of maximum. Zero is a valid dim target, not Off. These
targets apply only after idle expiry with the ordinary applicability gates; active input retains
system brightness. Android/OEM minimum levels and granularity are accepted.

Observe system-brightness changes and re-evaluate the dimmed value. Theme changes select the new
percentage and re-evaluate applicability without resetting elapsed idle time. Returning from
ineligible to eligible starts a fresh interval. Restore with `BRIGHTNESS_OVERRIDE_NONE`; the default
path does not write global brightness settings or restore a cached absolute brightness. The optional
system-brightness workaround below is the sole exception.

Use public Android brightness APIs. When the system baseline is unavailable, retain system policy.
The public system value is a platform baseline, not a physical-luminance measurement; automatic
brightness/OEM curves can affect the hardware result.

## Optional system-brightness workaround

`skins.<name>.behavior.systemBrightnessWorkaroundEnabled` is a file-only scalar Boolean, default
false, independent of theme and with no day/night variants. Deployment enables it only for the
explicitly operator-selected direct HU; device identity grants no capability. Strict AAOS and
supported host surfaces never enable this local workaround. No UI switch or automatic
permission-screen navigation is introduced.

When enabled and Android grants WRITE_SETTINGS special access, the separate controller writes
SCREEN_BRIGHTNESS as `round(systemBaseline * percent / 100)` in manual-brightness mode. No minimum,
zero-specific correction or device-identity branch changes this calculation. It leaves brightness
mode unchanged. Its own setting changes are not new baselines. External setting changes become the
new baseline; release restores only a value still owned by Riff. Missing access, unreadable settings
or write/persistence failure retain the ordinary window policy with a bounded log. Automatic
brightness does not acquire a new global dim lease.

A successful global lease uses SCREEN_BRIGHTNESS alone and leaves the local window override at
BRIGHTNESS_OVERRIDE_NONE. Do not apply both controls simultaneously: platform normalization of a
window request can feed back into the system setting and corrupt the independent baseline.
Unavailable or failed global control retains the ordinary window fallback; late worker results must
not override a newer input/lifecycle release.

On the tested direct HU, raw system zero can leave the panel at its preceding level. This is a
platform observation, not a reason for a special product correction. HU provisioning instead uses
night 1% through the same ordinary percentage calculation. Zero remains valid configuration; neither
zero nor 1% promises optical blackness, and platform granularity/minimum levels remain accepted.

Before each global write, durably record baseline and applied value in a minimal installation-scoped
device-protected recovery record. Input, focus/background/fullscreen loss and destruction release
the lease. The next Activity start recovers an interrupted lease, including when the flag has since
been disabled; restore only if the current setting still equals the recorded Riff value. An external
value wins. Abrupt termination can leave the dim value until that recovery; equality alone cannot
distinguish an external write of the same numeric value. No root, hidden API, sysfs write or MCU
command is used.

HU provisioning places the enabling Boolean and `dimBacklightPercent` day 15 / night 1 in a partial
Internal `player/ui_config.json`, and separately grants special access. Other installations inherit
30 for both themes unless their valid user file overrides it; both controllers use this same key.
Install/update and explicit reset provisioning must reapply that partial file before cold startup.
The common configuration loader owns deep merge and one startup read; other devices receive neither
this file nor the access grant.

## Verification

Cover default Auto, complete cycles, Off reachability, current/next accessibility labels, separate
profile/installation drafts and durable Save/Cancel. Cover both resolved themes, 30% defaults, HU
15%/1%, fractional/zero/full targets, repeated expiry, live baseline changes, ordinary input
execution, full grace intervals and an already-open timeout destination. Verify
focus/background/fullscreen/PiP restoration, automatic theme re-evaluation, blocked flows and
unchanged global brightness in the default path. The opt-in path additionally covers permission/mode
gates, self-write feedback, external changes, interruption recovery and write/persistence failures.
Device evidence establishes window-policy behavior; hardware luminance and vehicle-host compliance
require their own evidence.
