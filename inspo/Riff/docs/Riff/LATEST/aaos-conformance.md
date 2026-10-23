# AAOS and automotive — conformance not established — registry

The [1.0.1 draft feedback and collection-root review](/Riff/LATEST/ui-fixes-1-0-1-261018) records
the revised controls and navigation review.

The
[2026-10-18 classification-toggle and status review](/Riff/LATEST/type-toggle-status-review-261018)
records the current UI change and its official-source review.

**Conformance is not established.** Informative verification status. The
[mode contract](/Riff/LATEST/application-modes) defines requirements. HU rendering and policy unit
tests do not prove live vehicle/host enforcement.

| Boundary                                            | Current status                                                                                                                                                                                                                                             |
| --------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Local automotive/phone UI                           | Implemented; focused local/HU tests recorded.                                                                                                                                                                                                              |
| Resolved mode and Settings                          | Production controller consumes the cached resolved mode, forces strict AAOS on an actual AAOS display, and gates Settings commands on current motion. Automotive without a supported brake source stays UNKNOWN with the specified Settings recovery hold. |
| Playlist mutation and IME                           | Production UI and command handler gate create/add/rename/delete/remove by mode and motion. Automotive UNKNOWN does not enable editing through Settings recovery. Live host evidence remains open.                                                          |
| AAOS/projected Android Auto                         | Complete supported browse-host integration and distribution validation are not demonstrated. Custom hierarchy is not an AAOS host.                                                                                                                         |
| Settings/calibration/recovery                       | Draft, save, calibration and selected-root commit recheck access; AAOS RESTRICTED/UNKNOWN cannot use the Automotive recovery hold. Actual-AAOS device validation remains open.                                                                             |
| Hardware/distraction/rotary/accessibility/lifecycle | Focused evidence exists; complete acceptance matrix remains open.                                                                                                                                                                                          |

Material UI changes require the official-source review specified by
[application modes](/Riff/LATEST/application-modes). Review consequences, actual host/motion input
wiring and the relevant interaction matrix. Local policy tests and HU rendering establish only their
tested behavior. Current evidence belongs in [verification handoff](/Riff/LATEST/worklog).

## Technical Singles text review

Review date: 2026-10-17. Official sources:
[AAOS media host](https://developer.android.com/training/cars/media/automotive-os) and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The [catalog presentation owner](/Riff/LATEST/player-catalog-membership#technical-singles-display)
adopts a Unicode diamond in ready Singles labels. Local phone/tablet and direct Automotive use the
existing shared text rows, physical targets, font fallback, actions and motion policy. Supported
AAOS/Auto hosts retain their host-owned surfaces. This text-only change adds no mode branch,
interaction or host replacement; local glyph/UI verification does not establish vehicle conformance.

## Boolean preference icon review

Review date: 2026-10-12. Official sources:
[AAOS media and parked Settings](https://developer.android.com/training/cars/media/automotive-os),
[Automotive target sizing](https://developers.google.com/cars/design/automotive-os/design-system/sizing)
and [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The user adopts `ic_yes` / `ic_no` for existing and future local Boolean preferences under the
[shared UI owner](/Riff/LATEST/ui-foundation#boolean-preference-controls). Phone/tablet and direct
Automotive retain one common action component, physical targets, padding, selected/focus/disabled
appearance, complete accessible values and short-press toggle. Current Settings entry, session
access, draft/Save and motion gates remain unchanged. Actual AAOS and projected Auto retain
supported host surfaces and parked restrictions; these icons do not introduce a custom host
replacement, recovery bypass or new mode branch.

Verified local evidence on 2026-10-12: 288 Player and 27 configuration JVM cases, plus ten focused
disposable UI cases on each local device, pass without failures/errors/skips. Both Boolean states
use exact adopted icon rendering in WIDE/NARROW and both themes, with RTL, fontScale 1.0/2.0, fixed
bounds, full selected semantics and saved-profile draft isolation. Other Settings controls and the
existing motion policy retain their regression checks.

Signed RC 261012, version 0.1.2-rc / versionCode 4, is installed in place on the local device and
direct HU. Actual tablet portrait/landscape and HU landscape verify Day/Night icons and all Boolean
toggles; Cancel preserves committed values. UID, first-install time and exact media grants survive.
Temporary tablet rotation settings are restored. This establishes local UI behavior, not supported
vehicle-host acceptance. The
[handoff](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#boolean-preference-icons-delivered-2026-10-12)
owns artifact, commands and exact evidence.

## A-Z physical keypad rows review

Review date: 2026-10-11. Official sources:
[AAOS media-host boundary](https://developer.android.com/training/cars/media/automotive-os),
[Automotive target sizing](https://developers.google.com/cars/design/automotive-os/design-system/sizing)
and [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The adopted [A-Z owner](/Riff/LATEST/player-az-navigation) orders local keypad blocks and hides
fully unavailable physical rows after responsive packing. The bottom double-width Backspace row
always remains. Existing physical targets, 24dp gaps, safe insets, disabled appearance/semantics and
protected input remain common to local phone and direct automotive. Pending same-source calculations
retain the last complete appearance; only a completed snapshot changes row visibility. Actual AAOS
and projected Android Auto retain supported host media surfaces, with no custom T9, new mode branch,
motion exception or host replacement.

Implementation uses the existing common grid and action renderer. Fresh verification passes 288
Player and 27 configuration JVM cases and 24 disposable HU A-Z cases without failures/errors/skips.
The device suite covers WIDE/NARROW fixtures, independently hidden NARROW halves, conditional
numeric rows, empty results, final double-width Backspace visibility/disabled input, completed and
pending snapshots, both themes, fixed targets/gaps, protected pointers, vertical grid scrolling and
ordinary A-Z navigation/chrome regressions. Signed 0.1.2-rc (RC 261011) is installed in place on HU
with unchanged UID, first-install identity and exact persisted media grant. These checks establish
local HU behavior and responsive fixtures; actual phone portrait and live vehicle-host conformance
remain outside this delivery.
[Worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#t9-physical-row-update-and-rc-012-installed-on-hu-2026-10-11)
owns exact commands, artifact and evidence.

## MAIN constrained-window geometry review

Review date: 2026-10-03.

Official sources: [Media apps for cars](https://developer.android.com/training/cars/media) and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The local MAIN grid fits square tiles to its measured dp viewport, including the space reserved by
safe insets, skin margins, top chrome and status. The normal sp text, configured gaps, stable row
forms and 128dp tile floor retain their contracts. Scroll actions exist only when the minimum grid
cannot fit. Automotive and phone share the same geometry; actual AAOS and projected Android Auto use
supported host media surfaces. This change introduces no mode branch, motion exception or custom
AAOS surface.

Six geometry tests and a Compose device test cover same-composition height/width/density changes,
RTL, font scale 2.0, complete nonoverlapping tiles and absence of scroll actions in fitting windows.
Signed Alpha 261003 was verified on Samsung SM-S918U1, Android 16, through four alternating
portrait/landscape rotations. All seven tiles remain visible; both landscape swipe directions leave
their bounds unchanged. These checks establish local geometry only; vehicle-host conformance remains
unverified.

## Playback artwork text review

Review date: 2026-10-04. Official sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles)
and [Color](https://developers.google.com/cars/design/android-auto/design-system/color).

The local playback glyph backdrop separates title/artist/album/chapter and diagnostics from album
art. It keeps negative polarity at night, existing foreground roles, sp/fontScale, layout, targets,
input and navigation. Automotive and phone share it; actual AAOS and projected Android Auto retain
supported host rendering. There is no mode branch, motion exception or brightness adjustment.
Authority: [shared contrast halo](/Riff/LATEST/ui-contrast-halo).

Four focused Compose tests passed on HU in the disposable target: visible pixels in both polarities
for metadata and monospace status, foreground preservation, one accessible label, stable layout and
ellipsis at font scale 2.0 with RTL/complex-script text, theme/width skin geometry, and six
synthetic PLAYBACK captures with white, black and patterned artwork. App ktlint and Android lint
passed. These checks establish local rendering, not vehicle-host compliance or contrast on every
possible image/custom skin. The focused tests use a disposable package and synthetic fixtures, with
evidence in `status/ui-artwork-readability-261004`. Signed Alpha 261004 is installed on HU as an
update; installed bytes match and the real PLAYBACK screen shows the glyph backdrop over existing
artwork. Application data and SAF grants are retained. Deployment evidence is in
`status/hu-artwork-text-261004` and the [current handoff](/Riff/LATEST/worklog).

## Inactivity and Dim Backlight review

Review date: 2026-10-04. Official sources:
[Window brightness](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness),
[System brightness](https://developer.android.com/reference/android/provider/Settings.System#SCREEN_BRIGHTNESS),
[Media apps for cars](https://developer.android.com/training/cars/media) and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The local Activity uses Android's window-only override and returns control through
BRIGHTNESS_OVERRIDE_NONE. Theme and Dim use adopted icons in single cyclic targets with complete
current/next labels. Dim defaults to Auto; Off remains an enabled action. The common idle clock owns
navigation and dimming. Phone and direct automotive use the same focused/fullscreen policy. Actual
AAOS and projected Android Auto retain supported host windows and their parked/motion gates; this
introduces no custom host surface or driving-state exception. The Settings rows inherit their
existing gate. Authority: [Inactivity and Dim Backlight](/Riff/LATEST/player-inactivity).

247 Player JVM tests, ktlint and Android lint passed. Five focused HU instrumented tests cover the
button cycles, Off reachability, the shipping Activity's shortened fixture interval, input restore,
background/resume restore, installation Save/restart, ReplayGain and storage-warning controls, and
whole-row Settings navigation. The signed installed Alpha also passes a normal 30-second timeout
check against the exposed system baseline, input/background restoration and unchanged global
brightness. This establishes local window policy; physical luminance, OEM automatic-brightness
curves, live split-window/PiP transitions and vehicle-host compliance are not measured.

## Shared contrast halo review

Review date: 2026-10-04. Sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles),
[AAOS color](https://developers.google.com/cars/design/automotive-os/design-system/color),
[Custom media screens](https://developers.google.com/cars/design/automotive-os/apps/media/guidelines-custom-screens)
and [WCAG contrast](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

[Shared contrast halo](/Riff/LATEST/ui-contrast-halo) requires the same silhouette surround for
local playback metadata, transport/reactions/Bookmark, position and AI overlays. AI text is opaque;
the dense opposite-color surround supplies the measured contrast background. Both local modes retain
geometry, accessibility, motion gates and negative night polarity. Host-rendered AAOS/Auto surfaces
retain their own icon/color contracts. Local checks do not establish host conformance or fix
unrelated palette, disabled-appearance or rotary-focus pairs. Implementation evidence follows in the
worklog after verification.

Verified local evidence: 247 Player JVM and 18 configuration tests, ktlint and Android lint passed.
Ten focused disposable-target HU tests passed: text shaping/ellipsis/RTL/font scale, preserved glyph
and icon cores, all 13 transport/reaction/Bookmark resource variants with measured adjacent contrast
at least `4.5:1` in both themes, broad blur visibility, seek and Bookmark actions/disabled
semantics, ready AI gradient overlays with opaque black/white cores, stable skin geometry and AI
readiness/PTE controls. Synthetic playback captures cover white, black and patterned artwork in both
themes. Signed Alpha 261004 is installed on HU with exact-byte verification, retained application
data and working playback/AI navigation. This establishes the tested local renderer, not full
palette or vehicle-host conformance. Evidence: `status/shared-contrast-halo-261004`.

## Local color system review

Review date: 2026-10-04. Sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles),
[AAOS color](https://developers.google.com/cars/design/automotive-os/design-system/color), and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

[Local UI color system](/Riff/LATEST/ui-color-system) adopts one shared role set, separate quiet
day/night palettes, readable operable Off/Ban, explicit selected/focus geometry, protected image
outlines and emotion-preserving AI corners. AAOS requires meaningful content and rotary highlight
contrast at least 4.5:1; the primary-text 7:1 rule remains a project reserve. Dim Off uses ic_ban
with inactive appearance but stays actionable. A-Z background paint preserves operable foreground.
Calibration consumes paint roles without importing theme dimensions. The same local policy serves
phone and automotive; supported AAOS/Auto hosts retain their own rendering and motion gates. No mode
branch or safety exception is added. Current-run JVM/device and installation evidence belongs in the
worklog; local checks do not establish host or physical-luminance conformance.

Verification on 2026-10-04: 249 Player and 23 configuration JVM tests, ktlint, Android lint and 25
focused HU tests passed without skips. Production adapters cover enabled/inactive/disabled states,
focus/selection against opposite artwork extremes, Dim Off's exact adopted Ban resource,
transport/reaction/seek actions, A-Z foreground, both AI themes, calibration paint with protected
guidance/DPI plates and MAIN native pixel fitting at fractional density. The four color-system cases
also pass the focused final calibration-protection run. Synthetic screenshots cover both themes
without working media data. Rounded RGB composite checks include ordinary panels and A-Z dim/scrim
backgrounds: the minimum readable secondary/inactive role is 4.59:1 by day and 4.91:1 by night.
Primary and FILF text exceed 7:1; selected foreground and fixed-halo pairs pass their registered
thresholds.

Signed Alpha 261004 is installed on HU with exact installed-byte verification and retained
application data. Actual playback controls and AI navigation were inspected. Day/night synthetic
screenshots and the bounded checks are recorded in `status/palette-aaos-261004`. These checks
establish the tested local color/geometry behavior; physical night-glare suitability and complete
AAOS/Auto host conformance require their own acceptance evidence.

## Calibration control-layer review

Review date: 2026-10-04. Sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles),
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality),
[notification permission exemptions](https://developer.android.com/develop/ui/compose/notifications/notification-permission),
and
[WorkManager initialization](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration).

The reference sample is passive and paints below stable bottom controls. Active plates remain
opaque; only the genuinely disabled, empty CARD/US_NOTE denomination plate uses 20% surface opacity.
Its reserved hit bounds do not become a drag target. Guidance/DPI contrast protection and the single
measured border retain their contracts. CARD vertical expansion reaches the padded viewport bottom;
wide/square note modes expose their unmeasured height through the same gaps. EURO_NOTE keeps four
opaque control plates. No application-mode branch, new gesture or motion-policy exception is added.
Raw-pixel calibration retains its registered automotive deviation and strict-AAOS parked-entry
requirements.

Seven focused tests passed on trinket HU, Android 13/API 33, using only the disposable application.
Two new native render/input tests cover CARD/US_NOTE/EURO_NOTE in both themes, hatch visibility,
opaque active plates, inactive input exclusion and CARD expansion in landscape/portrait. Four
color-system render tests and actual Media3 controller/service startup passed with the reduced
permission set. Eight synthetic calibration fixtures were captured. Player/configuration JVM suites
(249/23), app/Indexer lint and ktlint, signed distribution verification and merged-permission checks
passed. Five forbidden manifest variants are rejected by the release gate.

The signed Alpha 261004 update was checked by exact installed bytes, unchanged first-install
identity and installed requested-permission inspection. Data/SAF were retained; Player and Indexer
are present, and playback controls/AI navigation were checked. The Media3/AndroidX receiver boundary
for API 31/32 was reviewed against the pinned runtime binary graph; no Android 12 device run is
claimed. Removing unused WorkManager/Play Asset Delivery entry points creates no alternate
background or boot policy. Evidence: `status/calibration-permissions-261004`. These checks establish
local rendering and startup, not physical card-measurement accuracy, audible output or complete
vehicle-host conformance.

## Book hierarchy row-allocation review

Review date: 2026-10-04. Official sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles),
[Custom media screens](https://developers.google.com/cars/design/automotive-os/apps/media/guidelines-custom-screens),
and [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The local shared selector derives uniform row allocation from complete source action eligibility,
independently of A-Z filtering. Body-and-More sources use one configured row; eligible Music rows
retain their reaction form. The current playlist rule is recorded in the compact-row review below;
the 2026-10-04 evidence used the then-adopted inert Music playlist slots. Body and More remain
separate nonoverlapping targets with the registered 76dp floor, 24dp horizontal gap, sp text, RTL,
disabled semantics and common focus treatment. No application-mode branch, navigation action,
mutation, motion exception or custom AAOS host surface is introduced. Automotive and phone share
this local geometry; actual AAOS and projected Android Auto retain supported host rendering and
restrictions.

Four focused Compose tests on the disposable phone target verify Titles, Bookmarks collections and
marks across resizing, RTL and font scale 2.0; Music/inert playlist slots; stable A-Z allocation and
viewport reports; and a valid skin with 128dp NARROW targets. Compact allocation fits the configured
target without shrinking it. All 249 Player JVM tests, ktlint and Android lint passed.

The signed Alpha was verified on Samsung SM-S918U1, Android 16, with the working library in six
views: Titles, Bookmarks collections and marks in portrait and landscape. Body and More align with a
43px horizontal gap; repeated row pitch is 196px in both orientations. The original rotation policy
was restored. These checks establish local geometry, not vehicle-host conformance. Artifact,
installation and bounded evidence are recorded in the
[current device scope](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#installed-editions-and-device-scope).

## Bookmark spacing availability review

Review date: 2026-10-05. Official sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles)
and
[AAOS UX restrictions](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictions).

The local Bookmark control exposes true disabled appearance/semantics whenever the shared spacing
policy cannot admit the observed position. Existing icon, target, slot, halo and focus treatment
retain their contracts. Position, chapter, mark and duration changes refresh availability without
storage work on playback/UI updates. The command rechecks actual service position atomically.

One spacing behavior applies to aaos, automotive and phone. The centralized aaos motion gate still
requires UNRESTRICTED; automotive/phone retain their adopted Mark exception without confirmation.
Actual AAOS and projected Android Auto retain supported host surfaces; this adds no custom host
surface or motion bypass. Required tests cover both boundaries, cross-chapter/FILF logical time,
unknown durations, changing startup values, retained marks, concurrent writes, restart, disabled
activation and mode/motion combinations. Implementation results belong to the worklog; this review
is not a vehicle-host certification.

Fresh verification for this review: eight Player JVM distance cases plus schema/default/override
validation passed. All 257 Player, 24 configuration and 121 Indexer JVM tests passed with no skips;
app/Indexer/configuration ktlint and Android lint passed. Thirty-three focused HU tests passed on
the disposable target: SQLite/store recovery and concurrent-write cases, committed spacing-cache
lifecycle, Bookmark enabled/disabled input in both themes, compact Book rows, controller recreation
and profile/ordering round trips, and Media3/controller startup. The initial controller fixtures
omitted worker-side startup activation; their corrected admission/configuration preparation passed.
Shared mode/motion policy tests cover every reachable combination. These checks establish local
behavior, not audible output, physical luminance or live AAOS/Android Auto host conformance.

## Settings and playlist restriction wiring review

Review date: 2026-10-05. Official sources:
[AAOS parked-app guidance](https://developer.android.com/training/cars/parked/automotive-os),
[CarUxRestrictions](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictions),
and
[CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager).

At the 2026-10-05 review Phone used one-tap Settings; the current Phone hold is owned by the
compact-dialog/Settings review below. Local playlist edits remain permitted. Direct Automotive
permits playlist edits only with valid UNRESTRICTED motion evidence; an unavailable brake source is
UNKNOWN and allows only the deliberate Settings recovery hold. Strict AAOS uses current
CarUxRestrictions evidence: Settings is permitted only when UNRESTRICTED, local custom playlist
editing remains unavailable, and the Book Mark exception follows its separately specified motion
gate. No mode weakens the actual AAOS display through configuration. Settings actions and final
saves recheck access; migration checks again before root activation. A restricted transition
discards an open Settings draft.

The local policy tests cover the mode/motion matrix, the recovery exception and a restriction
arriving after a draft opens. Android JVM, style, lint and assembly results are in
[worklog](/Riff/LATEST/worklog). These checks establish local command enforcement, not a supported
AAOS media-host surface or live vehicle conformance. The explicit automotive policy has no supported
parking-brake adapter; its missing evidence remains UNKNOWN and local playlist editing stays
disabled in that mode. The configurable distribution default is now phone under the
[mode owner](/Riff/LATEST/application-modes-modes#2-resolution-and-distribution); local phone
behavior does not require a vehicle input.

## SAF empty-listing warning review

Review date: 2026-10-06. Official sources:
[AAOS media integration](https://developer.android.com/training/cars/media/automotive-os),
[Media browser service](https://developer.android.com/training/cars/media/create-media-browser), and
[Message template](https://developer.android.com/design/ui/cars/guides/templates/message-template).

The [adopted workaround](/Riff/LATEST/saf-empty-listing-workaround) adds one local information modal
with a single OK acknowledgement. Automotive and phone use the same root-composition dialog; it
changes no grants, navigation, playback state, permissions or Settings/motion gates. Actual AAOS and
projected Android Auto retain supported host rendering and restrictions. The custom dialog is not a
media-host error surface; complete host integration remains an existing open requirement. No control
receives a motion bypass or raw mode comparison.

Implementation must preserve complete modal input blocking, Back/outside/idle exclusion, bounded
labels, focus, font-scale/RTL and process-memory acknowledgement across Activity recreation. Tests
must distinguish local fixture/rendering evidence from actual AAOS/Auto host conformance. Current
implementation and device results belong in the worklog after verification.

The subsequent phone experiment replaced the guessed-ID proposal with a compliant temporary-file
diagnostic. Empty read-only roots are inconclusive and skipped. The same bounded information modal
distinguishes confirmed platform failures from unverified sources; only confirmed failures recommend
restart. This changes neither the local/host boundary nor any motion gate.

Verified local evidence on 2026-10-06: five focused disposable Player tests passed on the HU
(Android 13/API 33), including the existing release-warning regression; the two new SAF-dialog tests
also passed on the phone (Android 16/API 36). The tests cover sole OK acknowledgement,
Back/outside/status-clear exclusion, main-menu retention with no media, and a long RTL notice at
font scale 2 with the acknowledgement visible. Process-memory deduplication and recovery were
covered by three JVM tests; late delivery and bounded labels by one Indexer Binder test. The
Activity-independent repository and ordinary playback callbacks were also reviewed. These results do
not claim a live Activity-recreation experiment, actual AAOS/Auto host acceptance or immunity of
other Android/provider versions. [Worklog](/Riff/LATEST/worklog) records the exact commands and
separate phone/provider-fixture evidence.

## AI title-plate action review

Review date: 2026-10-06. Official sources:
[Media apps for cars](https://developer.android.com/training/cars/media),
[AAOS parked-app and inset guidance](https://developer.android.com/training/cars/parked/automotive-os),
and [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The [AI screen owner](/Riff/LATEST/ai-screen-scope-and-entry#2-stable-screen-actions) replaces the
local ellipsis with one physical-right interactive AI title plate. One tap opens the existing
bounded Play/Add child; Cancel remains independently reachable at physical left. Automotive and
phone share this safe-inset layout in both orientations, with the existing targets, focus and
mode/motion gates. No new mode branch, gesture, Settings entry, keyboard or safety exception is
introduced. Actual AAOS and projected Android Auto retain supported host-rendered media surfaces;
this custom plate is not a host replacement and cannot establish host conformance.

Verification must cover repeated portrait/landscape transitions, WIDE/NARROW, RTL, font scale 2,
day/night, exactly one plate action, removal of the ellipsis and menu/Back draft preservation. Fresh
results and deployment belong in [worklog](/Riff/LATEST/worklog).

Fresh local evidence on 2026-10-06: four focused disposable Player tests passed on each device. The
AI plate test verifies physical edges through narrow/wide/RTL/font-scale/theme changes and menu/Back
state preservation; the existing AI readiness, PTE and release-warning tests also passed. The
working phone RC passed four alternating portrait/landscape transitions. HU initially had no
persisted media grant and its launcher was correctly disabled. A later check observed one grant and
a running Indexer, then verified the working RC physical-right plate, menu and system Back. The
source of the intervening grant change was not established; its fixture evidence remains separate.
No vehicle-host acceptance is claimed.

## Configurable Phone default and playlist review

Review date: 2026-10-06. Official sources:
[Media apps for cars](https://developer.android.com/training/cars/media),
[AAOS parked-app guidance](https://developer.android.com/training/cars/parked/automotive-os), and
[CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager).

The user selects phone because these installations have no motion input. The existing root
applicationMode remains a file-only startup switch; the packaged ui_config.json changes its default
to phone. Local Phone Play/Add, playlist selection and name entry use their already adopted shared
controller and renderer. Explicit automotive retains its UNKNOWN/RESTRICTED mutation gates and
Settings recovery rule. Explicit aaos and actual AAOS retain strict supported-host restrictions; a
real AAOS driver display cannot be weakened by the new default. Projected Android Auto remains
host-controlled. No raw mode branch or motion adapter is added to a control.

Required verification covers packaged defaults, all three accepted modes, invalid/locked overrides,
one startup read, actual-AAOS precedence, the unchanged Settings/playlist matrix, AI Play versus
frozen Add, new/existing destination, cancellation, failure, current queue preservation, and
ordinary Track Add. Check working-device effective modes after process restart and preserve data and
SAF permissions on install. Disposable local tests do not establish audible output or AAOS/Auto host
conformance. Fresh results and artifact identity belong in [worklog](/Riff/LATEST/worklog).

Fresh verification for the Phone-default review: 263 Player and 25 configuration JVM cases passed;
style, Android lint, disposable assembly and signed distribution checks passed. Four disposable
cases on each device verify the actual packaged default, AI Play/Add and title plate. Both working
RC installations confirm phone with no user override; Add/name entry/create, named cleanup, cancel
and selector preservation pass. The unchanged central mode/motion matrix and locked configuration
cases pass. Device identity grants no policy privilege, and no actual AAOS/Auto host or audible
output acceptance is claimed. Exact logs, artifact identity and device scope are in
[worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#phone-configuration-and-playlist-regression-2026-10-06).

## Shared compact playlist row review

Review date: 2026-10-06. Official sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles)
and
[Custom media screens](https://developers.google.com/cars/design/automotive-os/apps/media/guidelines-custom-screens).

The user adopts the existing Bookmarks body-and-More form for Music playlist containers. Remove
empty reaction slots and the second narrow tier through one common action-eligibility rule and row
renderer; playlist member tracks keep their eligible reactions. Complete-source allocation remains
independent of A-Z filtering. Standard target floors, gaps, font-scale text, RTL, disabled state and
focus semantics retain their owners. One common behavior serves phone and direct automotive without
mode branches. Actual aaos and projected Android Auto retain supported host rendering and
restriction gates; a custom local hierarchy is not a supported-host replacement.

Verify Music playlists, Bookmarks collections/marks and Book titles through the same parameterized
renderer cases: repeated WIDE/NARROW resize, RTL, font scale 2, custom narrow targets, title
expansion, separate More/body actions and disabled states. Preserve eligible Music track reactions
and stable mixed-source allocation under A-Z. Rebuild the signed RC and check both installations
with data and SAF grants preserved. This review and local tests do not establish vehicle-host
conformance. Fresh results and artifact identity belong in [worklog](/Riff/LATEST/worklog).

Fresh local evidence for the compact-row review: all 263 Player JVM cases and four shared-row
instrumented cases on each device passed without failures or skips. The working phone passed actual
portrait/landscape transitions; HU kept its landscape window under rotation requests. Both working
RCs passed compact pitch/target alignment, container navigation and More actions; both fixture runs
cover WIDE/NARROW forms. Installation preserved exact grants/data; temporary playlists were removed
and rotation settings restored. Phone mode is confirmed by read-only probes. This bounded local
evidence does not establish audible output or actual AAOS/Android Auto host conformance.
[Worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#shared-compact-playlist-rows-2026-10-06)
owns exact commands and artifact.

## Compact playlist dialog and Phone Settings review

Review date: 2026-10-06. Official sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles),
[Custom media screens](https://developers.google.com/cars/design/automotive-os/apps/media/guidelines-custom-screens),
and
[CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager).

The user adopts a compact shared New/Rename dialog with breadcrumb-sized chrome text and existing
OK/Cancel icon controls. Keep standard target floors, spacing, font scaling, dark-theme roles and
accessible labels; bounded body scrolling preserves actions beside the IME. Phone Settings now uses
ordinary long press and equivalent accessibility activation without a vehicle-input warning. A
central code-only strategy supports press, long_press, motion and motion_with_recovery; no user
configuration or preference is introduced. Automotive keeps unrestricted tap, restricted blocking
and unavailable-source recovery hold. Strict AAOS keeps its parked gate and has no recovery path;
actual AAOS/projected Android Auto retain supported host surfaces. The local dialog is not a host
media replacement or formal conformance evidence.

Verify the strategy/mode/motion matrix, rejected short/direct entry, live command rechecks and
existing draft/Save guards. Verify shared create/rename typography, physical OK-left/Cancel-right,
icon-only semantics, RTL/font scale 2, constrained height/IME, blank/busy states, cancellation and
validation retry. Run both disposable-device fixtures and working signed-RC checks on phone/HU;
preserve exact SAF grants and data. Fresh outcomes belong in [worklog](/Riff/LATEST/worklog).

Verified delivery on 2026-10-07: 266 Player JVM cases and 15 focused disposable cases per device
passed without failures/errors/skips. Working signed-RC checks on phone/HU confirm Phone tap/hold
isolation, shared New/Rename/Cancel and physical icon order with the keyboard; phone portrait and
landscape transition, HU landscape only. Root density is preserved explicitly inside the Dialog
window, and synchronous entry retains draft/Save command ordering. All four code strategies retain
strict-AAOS/positive-restriction exclusion. Data/grants are preserved and temporary test state is
removed. These are local checks, not actual AAOS/Auto vehicle-host acceptance. Artifact and command
identity belong in
[worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#compact-playlist-name-dialog-and-phone-settings-hold-2026-10-07).

## Shared playlist deletion dialog review

Review date: 2026-10-07. Official sources:
[Visual principles](https://developers.google.com/cars/design/design-foundations/visual-principles),
[Custom media screens](https://developers.google.com/cars/design/automotive-os/apps/media/guidelines-custom-screens),
and
[CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager).

Delete now adopts the same compact shell, breadcrumb typography and existing OK-left/Cancel-right
icons as New/Rename. The named confirmation remains; Delete has no free-text field or keyboard.
Preserve standard targets, gaps, font scale, night roles, full accessible name and bounded scrolling
with visible actions. One shared local behavior serves Phone and unrestricted Automotive. Existing
restricted/UNKNOWN mutation gates stay live; actual AAOS and projected Android Auto keep supported
host surfaces. No new mode branch, motion escape or Settings-entry change is introduced.

Verify shared New/Rename/Delete layout, density, RTL, both themes, font scale 2, long-name
scrolling, Cancel, system Back/outside, disabled busy confirmation and exactly one accepted delete.
A production controller/store case must prove cancellation and deletion preserve the active queue.
Run focused fixtures and signed-RC checks on phone/HU, preserving data and exact SAF grants. This
review and local checks do not establish vehicle-host conformance. Results belong in
[worklog](/Riff/LATEST/worklog).

Verified local delivery on 2026-10-07: 266 Player JVM and 11 disposable cases per device pass,
including shared typography/icons, large-font long-name scrolling, Cancel/Back/outside, busy
confirmation and active-queue preservation. Both working signed-RCs pass named cancellation and
confirmation without a keyboard; actual phone portrait/landscape, HU landscape only. Data and exact
SAF grants are preserved, temporary state removed. These checks do not establish supported-host
conformance. Artifact and commands belong in
[worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#shared-newrenamedelete-playlist-dialog-2026-10-07).

## Optional HU system-brightness review

Review date: 2026-10-07. Official sources:
[Window brightness](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness),
[WRITE_SETTINGS access](<https://developer.android.com/reference/android/provider/Settings.System#canWrite(android.content.Context)>),
[Media apps for cars](https://developer.android.com/training/cars/media) and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The user adopts a file-only, default-off global-brightness workaround for this direct HU. Phone and
direct Automotive may explicitly opt in for the local fullscreen Activity. Strict AAOS and projected
host surfaces retain their supported host policy; no permission screen is automatically launched,
Settings entry added or motion gate relaxed. A separate worker owns bounded public Settings calls
and a minimal installation-scoped recovery lease. The original window policy remains the fallback.
The preceding window-only review describes the default path, not the new opt-in exception.

Verify partial-file deep merge, default-off and strict-AAOS exclusion, permission denial, manual
versus automatic mode, self-write feedback, external changes, input/focus/background release,
interrupted-write recovery and failure handling. Use disposable reset evidence; preserve working
media grants/data during in-place HU installation. Driver readback is not optical measurement or
vehicle-host conformance. Fresh results belong in worklog.

## Relative day/night idle-brightness review

Review date: 2026-10-07. Official sources:
[Window brightness](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness),
[Media apps for cars](https://developer.android.com/training/cars/media) and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The user adopts one file-only numeric percentage selected by the resolved rendered theme, used only
at idle expiry. The common local policy applies 30% of the independent system baseline by default in
both themes; the explicitly provisioned direct HU uses day 15% / night 1%. The existing mode,
focus/fullscreen, input, theme-preview and lifecycle gates remain authoritative. The optional global
controller consumes the same percentage and preserves its default-off scalar enable flag, manual
mode/special-access gate and ownership recovery. No UI control, permission flow or motion bypass is
added. Strict AAOS excludes that optional controller, and actual AAOS/projected surfaces retain
supported host rendering. This is one local calculation with configuration-selected values, not a
device-identity branch or host replacement.

Verify both resolved themes with and without the workaround, defaults, partial-branch deep merge,
scalar percentages, 0/100/fractional values, baseline 128 \* 10% = 12.8 units, theme change while
idle, self-write suppression, external changes and input/lifecycle restoration. Zero is a numeric
request; local driver/window evidence does not promise optical blackness or establish vehicle-host
safety. Fresh results and the requested RC 261007 installation belong in worklog.

## All-modal shared-component review

Review date: 2026-10-08. Official sources:
[AAOS media-app host and parked settings guidance](https://developer.android.com/training/cars/media/automotive-os),
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality),
[AAOS window insets](https://developer.android.com/training/cars/parked/automotive-os), and
[Message template](https://developer.android.com/design/ui/cars/guides/components/message-template).

The user adopts one permanent [local modal owner](/Riff/LATEST/ui-modal-dialogs) for all eight
existing scenarios and every future app-owned dialog. Phone and direct Automotive share compact
chrome typography, density, bounded scrolling and physical confirmation-left/cancel-right icons.
Existing playlist/motion/reset gates, recovery hold, warning acknowledgements and bootstrap-only
storage boundary remain unchanged. Actual AAOS and projected Auto require supported host surfaces;
this local Dialog is not a host message template or a parked/restriction bypass. Official host
primary-action placement follows vehicle layout and does not derive from the local physical order.

Verify all eight scenarios and SAF notice variants, chrome family/weight, day/night, fractional
physical density, RTL, font scale 2, complete accessible names, visible actions under scrolling/IME,
Back/outside/body policy, blank/busy/blocked state, hold/accessibility equivalence and exactly one
accepted callback. Verify cold waiting/reset admission without accessing the unavailable root, plus
release and playlist regressions. Run disposable HU tests before signed RC 261008 in-place install;
preserve working data/grants. Local evidence does not establish vehicle-host acceptance. Fresh
results belong in [worklog](/Riff/LATEST/worklog).

Verified local delivery on 2026-10-08: 303 JVM cases and 30 disposable HU cases pass without
failures/errors/skips. The latter include 17 UI cases and 13 storage/admission/reset cases. All
eight modal scenarios, mixed SAF copy, shared chrome family/weight, both themes, density, RTL, font
scale 2, visible icon actions, cancellation and recovery accessibility retain scoped behavior.
Working HU RC 261008 passes Remove-folder Cancel/Back with the exact media grant preserved. HU
evidence is landscape; responsive fixtures do not establish actual portrait or vehicle-host
acceptance.
[Worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#shared-modal-typography-delivered-2026-10-08)
owns artifact and commands.

## Audit correction review — 2026-10-08

Reviewed [AAOS media integration](https://developer.android.com/training/cars/media/automotive-os),
[parked UX restrictions](https://developer.android.com/training/cars/parked/automotive-os), and
[message templates](https://developer.android.com/design/ui/cars/guides/components/message-template).
The user approved a non-cancellable accepted playlist write, accessible inline errors, worker-owned
Settings persistence and packaged-mode bootstrap fallback. Phone and permitted direct-Automotive
local dialogs retain one component. Automotive mutation gates remain fail-closed for UNKNOWN or
RESTRICTED evidence; strict AAOS retains its live parked/host restrictions and actual-AAOS
precedence. A cached Phone or packaged fallback never bypasses actual-AAOS restrictions. No host
surface is replaced by a Compose modal. Verification covers busy Cancel/Back/idle, profile
ownership, failure retention/error semantics, main-thread disk detection, and storage
admission/default/override gates. Local HU tests do not establish vehicle-host acceptance.

## Audiobook speed control review

Review date: 2026-10-09. Official sources:
[Cars media integration](https://developer.android.com/training/cars/apps/media),
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality),
[Media3 playback parameters](https://developer.android.com/reference/androidx/media3/common/PlaybackParameters),
and
[ExoPlayer silence skipping](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer).

The user adopts the [speed owner](/Riff/LATEST/player-audiobook-speed). Phone and direct Automotive
share the existing whole-control Ban allocation, focus, gesture and contrast renderer, with a bold
monospace label. Short press cycles speed; the optional hold only resets it. No dialog, keyboard,
precision target, Settings entry or motion-policy exception is introduced. Actual AAOS and projected
Android Auto retain supported host surfaces; this change supplies no custom host speed widget. The
common Media3 execution policy keeps pitch at unity and restores ordinary Music parameters.

Verify all nine steps and wrap/reset, locale independence, profile isolation and process reopen,
queued gesture/profile ownership, Music and mixed-queue transitions, profile/mode/classification
changes, service startup and audio-path recreation. Local UI fixtures cover equal Ban/Bookmark
bounds, WIDE/NARROW, font scale, RTL, both themes, disabled input and accessibility reset. Existing
mode/motion gates remain covered by their regression suite. Evidence belongs in worklog after
implementation; local tests do not establish audible time stretch or vehicle-host acceptance.

## Audiobook tenths and suffix-label review

Review date: 2026-10-09. Official sources:
[Cars media integration](https://developer.android.com/training/cars/apps/media),
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality), and
[Media3 playback parameters](https://developer.android.com/reference/androidx/media3/common/PlaybackParameters).

The user changes only the bounded speed set and numeric label under the
[speed owner](/Riff/LATEST/player-audiobook-speed). The same whole-control target, bold monospace
renderer, short-cycle/optional-reset actions and disabled/accessibility policy remain common to
Phone and direct Automotive. No dialog, keyboard, precision target, host widget, Settings entry or
motion-policy exception is introduced. Actual AAOS/projected hosts retain supported surfaces. No new
mode branch is needed. Verify all nine labels, cycle/wrap/reset, locale independence, NARROW/WIDE
and theme/font-scale/RTL bounds, current-profile persistence and actual WAV tempo at both endpoints
across N/I/F. Physical output and supported vehicle-host acceptance remain separate.

## Automatic-theme configuration observation review

Review date: 2026-10-09. Official sources:
[Compose observable state](https://developer.android.com/develop/ui/compose/state),
[CompositionLocal](https://developer.android.com/develop/ui/compose/compositionlocal), and
[Cars media integration](https://developer.android.com/training/cars/apps/media).

The [existing theme owner](/Riff/LATEST/player-inactivity#settings-and-defaults) requires Auto to
follow Android's current night state. The local Compose root must observe configuration changes
independently of Player domain updates. Use the standard Compose configuration/theme observation for
the shared root and bootstrap surfaces, retaining explicit profile/draft precedence, immutable skin
activation, physical density, font scale, navigation and playback. Phone and direct Automotive share
this correction without a mode branch. Actual AAOS/projected surfaces retain supported host
rendering; no Settings action, motion bypass or global night-policy override ships in the product.

Two disposable HU regressions fail before the correction and pass afterward: a configuration-only
palette update and the real Activity after Android switches to night without input. All nine focused
HU cases pass, covering both directions, manual/draft precedence, Activity brightness, Theme/Dim,
audiobook control and modal regressions. The installed signed working HU also repaints through night
→ day → night → day → night without input; the temporary system night policy is restored. Fresh 281
Player JVM tests, style, zero-error lint and release checks pass. No current-schema Player reset
occurs. Phone is unavailable for fresh device evidence in this run. Exact scope and artifact belong
in
[worklog](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#automatic-theme-regression-corrected-on-hu-2026-10-09);
local repaint does not establish physical luminance or supported vehicle-host conformance.

## Release-audit artwork and pending-preview review

Review date: 2026-10-10. Official sources:
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality) and
[AAOS media integration](https://developer.android.com/training/cars/media/automotive-os).

The approved corrections retain one static context image and the existing local A-Z preview. Phone
and direct Automotive share result ownership, bounded artwork recovery and retained preview colors
without mode branches. Waiting controls remain nonfunctional and retain disabled input semantics;
ordinary disabled controls keep their normal visual treatment. Targets, layout, focus, motion gates
and navigation do not change. Actual AAOS/projected surfaces retain supported host rendering; local
checks do not establish host acceptance. Fresh disposable HU tests pass stale/prefetched/retried
artwork, actual SAF loading recovery, bounded timeout/retry handling and pending/ready A-Z preview
pixels in both themes. The full shared UI suite is included in the
[audit verification](/Riff/LATEST/release-audit-261010); physical vehicle-host acceptance remains
open.

## A-Z saved-filter interaction review

Review date: 2026-10-12. Official sources:
[Media apps for cars](https://developer.android.com/training/cars/media),
[AAOS media integration](https://developer.android.com/training/cars/media/automotive-os), and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The user adopts immediate process-memory filter saves for existing Backspace and breadcrumb
controls, and for an accepted contextual Play. Phone and direct Automotive share one navigation
implementation; no target, gesture, IME, animation, new surface or motion exception is added. Search
stays open for Backspace/breadcrumb saves. Playback retains explicit user initiation and the durable
READY-queue gate. Actual AAOS and projected Android Auto retain supported host browsing/search; this
local controller is not a host UI replacement. The review finds no need for another mode branch.

Verify empty and nonempty Backspace, current and ancestor breadcrumb prefixes, later draft
cancellation, Home/Flip/re-entry, contextual Play through body/long-press/ROW_ACTIONS/bookmark
routes, pending snapshot and source replacement, disabled Backspace, ordinary viewport thresholds,
Auto search on/off, and unchanged queue/failure policy. Fresh implementation and device evidence
belong in worklog; local controller/Compose checks do not establish vehicle-host acceptance.

## Staged indexing progress review

Official guidance reviewed 2026-10-12:
[AAOS media](https://developer.android.com/training/cars/media/automotive-os) and
[car quality](https://developer.android.com/docs/quality-guidelines/car-app-quality). Stage progress
stays in the existing bounded status line, with no new motion gate, navigation, permission or host
surface. Background work remains subordinate to playback. Fresh JVM and disposable Android
acquisition/progress checks pass; stage labels remain in the existing bounded status line.
Real-library speed and vehicle-host acceptance remain unestablished.
[Current verification](/Riff/LEGACY/2026-10-15-dependency-refresh/Riff_LATEST_worklog#staged-indexing-implemented-2026-10-12)
records coverage and the five skipped corpus experiments.

## A-Z file-only RGB adjustment adoption review

Review date: 2026-10-14. Official sources:
[AAOS media](https://developer.android.com/training/cars/media/automotive-os),
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality),
[ColorMatrix](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/ColorMatrix),
and
[GPU layer](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer).

The user adopts the common RGB adjustment with default 32, file-only configuration and no Settings
control, and authorizes today's new signed RC on phone and HU. Configuration uses one JVM
schema/merge/validation owner and one immutable process-startup snapshot. One reusable GPU layer
applies max(0, c - configured amount) per RGB byte with identity alpha; zero bypasses it. Ordinary
rows and background are composited before adjustment; chrome and keypad/pager remain outside. No
component-specific search paint, CPU bitmap processing, motion exception, mode branch, new control
or host replacement is introduced. Actual AAOS/projected media keeps its supported host rendering.

Verify default 32, file override 16, zero, strict types/range/variants, inactive skins and
whole-file fallback; contrast validation of adjusted pairs; actual default/override pixels in both
themes; remembered-filter invalidation and zero bypass; exposed and protected input. Record signed
RC date/version, exact artifacts, device startup and identity/grant continuity in worklog. Local
tests do not establish physical luminance or supported vehicle-host acceptance.

### A-Z single adjustment layer experiment review

The earlier local trial is adopted by the file-only configuration review above. Its stable section
anchor is retained for dated trial evidence.
[Current delivery](/Riff/LATEST/rc-0-1-4-delivery-261014) owns the new release and verification,
rather than the historical experiment artifacts.

## A-Z final breadcrumb parent review

Review date: 2026-10-14; corrected after reproducing automatic closure on the working phone.
Official sources:
[AAOS media integration](https://developer.android.com/training/cars/media/automotive-os) and
[Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The final current A-Z group plate returns to the preceding filter prefix both with the keypad open
and after manual or automatic closure. Phone and direct Automotive share the existing typed
breadcrumb/save operation and ordinary automatic-entry policy. No new surface, target, gesture, IME,
motion exception or mode branch is required. Accessibility describes the actual destination. Actual
AAOS and projected Android Auto retain supported host browsing/search; this local change does not
replace host controls. One shared local implementation remains suitable.

Verify all roots and independent ancestor filters, open/closed search, manual closure, automatic
completion, repeated return to an empty prefix, pointer/accessibility activation and Home/re-entry.
Reproduce the reported auto-close and final-plate tap on the real installed phone, and record
before/after evidence, signed bytes and two-device continuity in worklog. Local tests do not
establish vehicle-host conformance.

## Playback controls and passive status layering review

Review date: 2026-10-14. Official sources:
[AAOS media integration](https://developer.android.com/training/cars/media/automotive-os),
[Automotive sizing](https://developers.google.com/cars/design/automotive-os/design-system/sizing)
and [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality).

The user adopts 120dp local playback controls, portrait transport split into two rows, bottom-safe
control anchoring, and non-reserving passive status underneath foreground content on MAIN/PLAYBACK.
One shared local implementation serves phone and direct Automotive, retaining central command/motion
gates, stable audiobook slots, explicit labels, safe insets and bounded overflow. Status is omitted
from accessibility and handles no input. The explicit aspect rule changes row packing only; physical
density, WIDE/NARROW token resolution and artwork policy remain common.

Actual AAOS and projected Android Auto continue using supported host media surfaces; their hosts own
playback arrangement. This local layout adds no custom host replacement, mode exception or
restriction bypass. Local target measurements and HU rendering cannot establish vehicle-host
conformance. No new mode branches are needed.

Verify both orientations and resize, equal full target heights, safe bottom/row gaps, row ordering,
RTL, font scale through 2.0, constrained scrolling, ordinary transport/reaction/audiobook actions,
status position/visibility independence, absent status accessibility, pointer pass-through and
foreground precedence. Record current commands and device results in the handoff.

## Passive status top-left review

Review date: 2026-10-14. Official sources:
[AAOS media integration](https://developer.android.com/training/cars/media/automotive-os),
[Car app quality/insets](https://developer.android.com/docs/quality-guidelines/car-app-quality) and
[Automotive sizing](https://developers.google.com/cars/design/automotive-os/design-system/sizing).

The user replaces MAIN/PLAYBACK status positioning with the physical top-left system-safe corner and
zero additional gap. The same decorative underlay serves phone/direct Automotive, below artwork and
foreground controls, with no reserved slot, input, focus or accessibility content. Insets remain
respected; no interactive target is moved or reduced. RTL does not mirror the requested corner.
Current RC visibility stays enabled; the future FINAL default and file override are configuration
policy, without a new UI or mode branch.

Actual AAOS/projected Auto retain supported host media surfaces and motion/Settings restrictions.
This local change does not establish vehicle-host conformance. Verify both screens/orientations,
zero-gap safe bounds, fontScale 2.0/RTL, viewport-fixed position during scrolling, hidden-state
geometry, absent accessibility/input, background pass-through and foreground precedence.

## Simplified classification actions — 2026-10-18

The [1.0.2 review and delivery evidence](/Riff/LATEST/release-1-0-2-delivery-261018) records removal
of the explanation element and its supporting state/text code. One common local page retains
full-size Music/Book toggles, the static wrapped AI advisory and existing safety/persistence policy.
