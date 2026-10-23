# A-Z final breadcrumb returns one filter level

Dated 0.1.4-rc correction evidence from 2026-10-14.
[Current release](/Riff/LATEST/rc-0-1-5-delivery-261014) owns the committed 0.1.5-rc artifact and
both current installations. The final current A-Z group plate now selects the preceding prefix both
with the keypad open and after manual or automatic closure.

## Reproduced cause

The preceding signed APK was installed correctly: its pulled SHA-256 matched the expected
ce41f28fd340dbd209033229de51d7623cbe6057c77b267f656c8ddfe8f61dec. On the real phone, Albums/GHI
automatically closed the keypad; the last GHI tap left that filter unchanged. The previous
implementation and tests incorrectly restricted the requested behavior to an open keypad. The user
request covers the retained A-Z breadcrumb after closure as well.

## Adopted behavior and implementation

Albums/GHI returns to Albums without a filter. In Albums/ABC/GHI, either ABC or final GHI selects
Albums/ABC. Repeated final-group activation removes one group at a time. Earlier prefixes and
independent ancestor-container filters retain their destinations. The root label selects an empty
prefix and gains no additional parent action.

PlayerHierarchyScreen.kt uses the same destination rule in both local hierarchy/search states. Open
A-Z stays open and saves through the existing typed navigation owner. After closure, ordinary prefix
navigation retains the existing automatic-entry policy, so an eligible large container may reopen
the keypad. No new mode branch, action type, persistent state or Settings control is added; the
file-only RGB subtraction default stays 32.

The [A-Z owner](/Riff/LATEST/player-az-navigation),
[hierarchy owner](/Riff/LATEST/hierarchy-screen-scope-and-shared-implementation) and
[official-source review](/Riff/LATEST/aaos-conformance#a-z-final-breadcrumb-parent-review) were
reconciled before code. Phone and direct Automotive share the local implementation; actual
AAOS/projected media retains supported host browsing/search.

## Fresh verification

297 Player JVM cases pass without failures/errors/skips. All 17 HU Android cases and five focused
phone cases pass. Added regressions activate the final applied prefix with the keypad closed across
every root, and close search before a real pointer tap, filter save and Home/re-entry. The
open-keypad, all-root NARROW, WIDE, descendant/ancestor, RTL/geometry and semantic-action cases
remain covered by these suites. Kotlin formatting/checks, Android lint, isolated APKs and signed
distribution/model/alignment/certificate verification pass.

The signed working application was then tested through its real UI on both devices. On phone,
Albums/GHI automatically closes A-Z; tapping final GHI now removes that plate and restores Albums
without a filter. The same complete workflow passes on HU. Device-local XML/screenshots before and
after record the displayed controls and exact destination; no playback action or media write was
requested. This closes the missing real automatic-completion branch; synthetic tests alone were
insufficient for the earlier handoff. No host-conformance, indexing or playback-performance claim is
made.

## Signed installation

The preceding 0.1.4-rc delivery installed exact APK SHA-256
**baa5e0181a0e73d0ad0f5e7a74c3e24ae163894debdac761b70eeee59d72239c**:
releases/261014-rc-0.1.4-az-breadcrumb-fix/riff-0.1.4-rc-261014.apk. That dated identity was
0.1.4-rc, versionCode 7, RC 261014. The persistent certificate, UID, first-install time and exact
persisted URI permissions agree; launch succeeds and Player remains alive without bounded startup
FATAL EXCEPTION. No uninstall, data clear, manual reset or persisted-format change occurred. A
private HU Player backup succeeds; full private phone backup is unavailable to unprivileged ADB and
is not claimed.

At that earlier handoff, source was uncommitted on codex/az-adjustment-layer and the website was
unchanged. The current release above commits/merges that work and updates both devices and website.
Original commands, source, initial failure and before/after UI evidence remain under
status/az-breadcrumb-fix-261014/. The outgoing correction edition is archived verbatim.
