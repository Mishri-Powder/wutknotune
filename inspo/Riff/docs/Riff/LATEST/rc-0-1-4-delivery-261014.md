# Riff 0.1.4-rc — file-only A-Z RGB adjustment

The user adopted the 32-byte A-Z adjustment, requested file-only configuration without a Settings
control, a version increment and today's RC on phone and HU. Signed **0.1.4-rc**, versionCode **7**,
**RC 261014** was installed and checked in that delivery on Samsung S23 Ultra and HU.

## Configuration and rendering

The complete packaged app/src/main/config/ui_config.json declares
`skins.default.behavior.azRgbSubtract: 32`. The single JVM configuration owner registers a scalar
integer 0..255, merges it through the existing four layers and activates the immutable startup
snapshot. A partial optional player/ui_config.json may override it; process restart activates edits.
No Settings row, profile preference, database column, secondary parser, file watcher or runtime
reload is added.

One GPU layer applies max(0, c - configured amount) to each composited 8-bit RGB channel, with alpha
unchanged. Zero bypasses the effect. The remembered filter is keyed by the amount. The list's own
background is included; top chrome and keypad/pager are outside. There are no search-specific
per-component effects. Ordinary states and protected/unavailable-key input remain intact. Legacy
azOverlayAlpha and azBackgroundDimAlpha keys and adapters are removed; unknown retired keys use the
ordinary whole-file rejection policy, with no migration/reader.

Shared validation now checks the ordinary and adjusted foreground/surface pairs, actual panel and
selected-circle composites, existing text/icon contrast thresholds and night luminance order. Unsafe
amounts reject the entire optional file and retain packaged defaults. The owning
UI/configuration/color/halo/hierarchy/distribution contracts and official-source review were updated
and publicly verified before implementation.

## Fresh validation

- 28 configuration and 297 Player JVM cases pass without failures/errors/skips.
- Five release-tooling tests pass; Kotlin formatting/checks, Android lint and distribution, model,
  alignment and signing checks pass.
- All 30 focused HU Android cases pass across A-Z, GPU adjustment and color/state suites.
- The high-density phone initially exposed a test-coordinate error: a fixed-pixel sample hit the
  active-key border. Sampling was corrected using relative bounds, without a product change. Final
  focused runs pass two HU and three phone cases.
- The real selector is verified in DAY/NIGHT with packaged default 32 and parsed file override 16,
  unchanged chrome/keys and exact restoration. The GPU fixture covers all byte values, translucent
  compositing, zero clamp, amounts 32/16/0, remembered-filter updates, alpha preservation and
  zero/disabled bypass. Existing exposed/protected input paths pass.

No complete-indexing, new playback timing, physical-luminance or vehicle-host conformance claim is
made. Local contrast validation does not replace supported host acceptance.

## Installed artifact and continuity

APK SHA-256: **08611dced32181e7698f91498ff8c1fd991c3b6352fd0cc343c86325e3feb2b4**. Manifest, source
inventory (including untracked files), changed-source archive, commands, test reports and private
device evidence remain under status/rc-0.1.4-261014/. Artifact:
releases/261014-rc-0.1.4/riff-0.1.4-rc-261014.apk. Both installed APK pulls match; versionCode 7, RC
date/channel, persistent certificate, UID, first-install time and exact persisted permissions are
verified. Both launch and remain alive with no bounded startup FATAL EXCEPTION or RiffConfig
rejection. No uninstall, data clear or manual reset occurred. HU Player backup succeeds privately;
full phone internal backup is unavailable to unprivileged ADB and is not claimed.

Cuetree format 10 and Player schema 15 were unchanged. At this dated adoption handoff, source was
uncommitted on codex/az-adjustment-layer and the website remained 0.1.3-rc.
[Current release](/Riff/LATEST/rc-0-1-5-delivery-261014) owns the subsequent commit/merge/push,
0.1.5-rc APK, both current installations and updated public website. Earlier evidence above remains
scoped to the original configuration-adoption run.
