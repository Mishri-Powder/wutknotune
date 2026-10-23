# Riff application modes and AAOS review contract — 1. Modes

> Owner/scope: bounded section of Riff/LATEST/application-modes

## 1. Modes

Riff resolves exactly one application mode:

| Value      | Meaning                                                                                                                        |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------ |
| aaos       | Strict AAOS behavior. No Riff AAOS deviation or recovery bypass is permitted.                                                  |
| automotive | Direct-install aftermarket/AOSP automotive behavior with applicable AAOS safeguards and only explicitly registered deviations. |
| phone      | Local phone/tablet behavior outside projected Android Auto.                                                                    |

Mode is a policy input, not a skin, responsive form, theme, vehicle-state guess, or proof of
compliance. Physical dp/sp, density, stable layout, contrast, accessibility, playback, storage, and
durability rules remain common unless a normative contract explicitly states a difference.

## 2. Resolution and distribution

- Runtime controls MUST consume one immutable ResolvedApplicationMode from a centralized policy
  boundary.
- A configurable/free distribution reads root applicationMode from user configuration. Exact values
  are aaos, automotive, and phone. Its packaged default is phone.
- An AAOS-locked distribution MUST hard-code aaos, omit applicationMode from packaged and
  user-facing configuration, expose no selector or override, and reject a user file that supplies
  applicationMode.
- A real AAOS driver display MUST NOT be weakened by a stored automotive or phone value; effective
  mode is aaos.
- applicationMode is not a SETTINGS_SCREEN row.
- Hard-coding aaos is necessary for the intended locked distribution but is not sufficient evidence
  of formal compliance. Host, manifest, media, activity, UX-restriction, quality-test, and
  distribution requirements also apply.

## 3. Motion restrictions

RestrictionSource is AAOS_UXR, PARKING_BRAKE, or UNAVAILABLE. MotionRestriction is UNRESTRICTED,
RESTRICTED, or UNKNOWN.

- Actual AAOS UX MUST consume CarUxRestrictions or the supported host abstraction, not raw sensor
  guesses.
- A direct AOSP adapter MAY use a supported parking-brake input. Only a present, fresh, valid value
  resolves it; missing, stale, malformed, disconnected, or failed input is UNKNOWN.
- Wiring polarity is adapter configuration, not a universal assumption.
- aaos treats UNKNOWN as restricted and permits no recovery bypass.
- automotive keeps driving-safe behavior for RESTRICTED and UNKNOWN except the exact Settings
  recovery below.
- phone ignores vehicle motion for its local UI. Projected Android Auto remains host-controlled.
- Riff cannot detect an input deliberately wired to report parked permanently. Therefore automotive
  MUST NOT be advertised as formally AAOS-compliant.

## 4. Mandatory implementation review

Before an LLM or implementation agent adds a new UI function, first implements an existing specified
function, or materially changes a control, screen, gesture, navigation flow, or visible state, it
MUST:

1. Read current central requirements, this contract, the controlling screen contract, and applicable
   current official Android for Cars and AAOS guidance.
2. Record review date, official sources, affected behavior, result, implementation consequence, and
   tests in the conformance record and worklog.
3. Decide whether one AAOS-conformant behavior is suitable for all modes.
4. If yes, retain one common behavior without needless mode branches.
5. If no, update the controlling normative contract before code with exact aaos, automotive, and
   phone behavior.

For divergent behavior, aaos MUST conform or omit the function; automotive MAY use only a centrally
registered deviation; phone MUST be explicit. Tests cover every reachable combination and prove aaos
cannot reach automotive or phone escape paths. Raw mode comparisons MUST NOT be scattered through
controls.

## 5. Settings entry

MAIN_SCREEN remains the sole in-app Settings entry.

| Mode and motion                                                | Behavior                                                                                                                                   |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| aaos + UNRESTRICTED                                            | Official permitted parked/settings path; one tap may open Settings.                                                                        |
| aaos + RESTRICTED or UNKNOWN                                   | Truly disabled and nonfunctional; no click, long-click, accessibility action, deep link, or recovery bypass.                               |
| automotive + UNRESTRICTED                                      | Enabled; one tap opens Settings.                                                                                                           |
| automotive + RESTRICTED                                        | Truly disabled and nonfunctional; no long-click bypass.                                                                                    |
| automotive + UNKNOWN because source is unavailable or unusable | Visually and semantically restricted, not disabled. Tap explains; deliberate long-click and equivalent accessibility action open Settings. |
| phone                                                          | Enabled; deliberate long press or equivalent accessibility action opens Settings. Tap does not open; no motion gate or recovery warning.   |

Entry strategy is selected only in centralized code: `press`, `long_press`, `motion`, or
`motion_with_recovery`. Current bindings are phone `long_press`, aaos `motion`, automotive
`motion_with_recovery`. No user configuration key, Settings row or persisted preference exists.
Entry gesture and open-session access are separate; entry selection cannot weaken strict-AAOS
restrictions, Save guards or playlist policy.

The automotive recovery hold unlocks no other control and does not prove parked state. An actionable
recovery control MUST NOT expose disabled semantics because an intentionally disabled control must
be nonfunctional.

## 6. Current per-surface policy

| Surface                                         | aaos                                                                                        | automotive                                                                                                          | phone                                                                                   |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Browse/playback host                            | Official AAOS/media host; custom MAIN_SCREEN and HIERARCHY_SCREEN are not host replacements | Current custom direct-HU surfaces                                                                                   | Same local visual system                                                                |
| MAIN_SCREEN categories                          | Host integration                                                                            | Six launcher positions plus Books toggle under [Content modes and classification](/Riff/LATEST/player-content-mode) | Same local seven-position form                                                          |
| Hierarchy                                       | Host constraints                                                                            | Existing bounded paging                                                                                             | Existing bounded paging; it already conforms, so no phone-only scroll branch is adopted |
| Settings                                        | Official parked gate, no fallback                                                           | Section 5, including recovery only for UNKNOWN unavailable source                                                   | Long press                                                                              |
| Settings, calibration, and saved data migration | Permitted parked path only; migration may stop playback                                     | Automotive policy; calibration deviation remains; migration may stop playback                                       | Normal local access; migration may stop playback                                        |

## 7. Verification

Verification MUST cover exact parsing and unsupported-version rejection under the public-beta reset
policy, configurable default phone, immutable activation, locked-AAOS absence and rejection of
overrides, actual-AAOS precedence, every Settings row, current storage child selection and commit,
TalkBack recovery equivalence, no positive-restriction bypass, common behavior without needless
branches, centralized policy use, and a recorded review plus exact three-mode contract before each
divergent implementation.

## 8. Profile and playlist interaction policy

The User row requires legitimately opened Settings and its normal draft/Save transaction. It creates
no new recovery path or Settings entry.

AAOS uses supported host browsing, never the custom hierarchy. Automotive UNRESTRICTED and phone
permit bounded local playlist actions. Automotive RESTRICTED or UNKNOWN leaves Play subject to
ordinary playback policy but visibly and semantically disables every playlist mutation and name
entry; no keyboard opens and no UNKNOWN bypass applies. New/Rename are the only playlist free-text
steps. Delete requires named confirmation. Queue construction is off playback-critical threads and
preserves the current queue until durable activation.

The Book Title playback Mark action is a narrowly registered exception: aaos permits it only under
UNRESTRICTED motion and disables it under RESTRICTED or UNKNOWN; automotive and phone permit it
without confirmation even under RESTRICTED/UNKNOWN. Enforce this at the centralized policy boundary
and the command handler, not only in the button. Existing mutation restrictions still govern
delete/remove/name entry. Actual AAOS and projected Android Auto expose only supported host
surfaces.

Verify every mode/restriction combination, including the Mark exception, no custom AAOS surface, no
restricted/UNKNOWN ordinary mutation or IME, permitted create/add/rename/delete/remove, saved
profile switching, stable targets, focus and TalkBack.

## 9. Release-age warning review

[Distribution](/Riff/LATEST/distribution) owns the local release-age acknowledgement.
[AAOS conformance](/Riff/LATEST/aaos-conformance) records its official-source review and acceptance
limits. The acknowledgement does not bypass motion restrictions or change playback service state.
