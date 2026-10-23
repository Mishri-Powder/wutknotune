# Direct HU system-brightness workaround — 2026-10-07

> Implementation and verification evidence. Requirements remain with the linked scoped owners.

## Result and scope

The signed working HU installation now has a separate optional system-brightness controller.
Packaged `skins.default.behavior.systemBrightnessWorkaroundEnabled` is false. Only the explicitly
selected direct HU received the partial Internal user file and WRITE_SETTINGS special access. The
working phone APK, user configuration and special-access policy were not updated. Actual AAOS and
resolved AAOS mode exclude this controller. No identity-based privilege or automatic permission
navigation was introduced.

Owners are
[Player inactivity](/Riff/LATEST/player-inactivity#optional-system-brightness-workaround),
[configuration](/Riff/LATEST/project-configuration#configuration-registry),
[permissions](/Riff/LATEST/runtime-architecture#packaged-android-permissions), and
[bootstrap recovery](/Riff/LATEST/storage-roots). Their edits were staged before code and published
after explicit user approval; the canonical publication receipt records the archive, API and
anonymous-route checks. The existing configuration registry and immutable startup loader own the new
Boolean and deep merge. The user filename is now `player/ui_config.json`; no pre-release migration
or second configuration reader was added.

The HU user file contains only:

```json
{
  "skins": {
    "default": {
      "behavior": {
        "systemBrightnessWorkaroundEnabled": true
      }
    }
  }
}
```

Its installed path is `/data/user/0/link.roadio.riff/no_backup/riff-data-v1/player/ui_config.json`.
A cold production loader confirms enabled=true, idle timeout=30, Indexer pipeline depth=3, bookmark
spacing=15 and day background=#EEF1F5. Three disposable data-clear/provision cycles also pass
through the common loader with this enabling-only file. The tool preserves existing valid user keys
and rejects invalid input. The explicit installer/reset procedure is in
`tools/hu-backlight/README.md` on the canonical dev box; data clear or uninstall requires rerunning
that procedure before ordinary cold startup. The working application data was never reset.

## Controller behavior

The normal window override remains in place. The opt-in controller additionally writes one third of
the independently read SCREEN_BRIGHTNESS only in manual mode and with Android special access. Own
writes do not become new baselines. External values are respected. Input and lifecycle release
restore only a still-owned value. Durable baseline/applied recovery precedes a write; the next
Activity start recovers interruption, even if the flag was subsequently disabled. All global writes
and durable lease I/O run outside UI/playback threads.

WRITE_SETTINGS is declared in the manifest and checked by the release permission allowlist.
Declaration alone is insufficient: the HU installer also grants Android special access, verified
with Settings.System.canWrite. Missing access and automatic mode retain ordinary window behavior.
The shipping implementation uses public Android APIs. It contains no root, hidden display API, sysfs
write or MCU operation. Provisioning is separate operator tooling with a temporary helper that is
removed after use; its reflection targets only Riff's own loader/storage classes.

## Fresh verification

The 26 configuration tests and 273 Player JVM tests pass with zero failures, errors or skips. The
new cases cover partial-file deep merge, schema rejection, default-off and AAOS gates, self-write
feedback, external change, interrupted recovery, denied/revoked access, unreadable baseline and
persistence/write failure. Ktlint, configuration checks, Android lint with zero errors and 23
existing warnings, signed distribution/model/license checks and exactly four release permissions
pass. Five Python release-tool tests pass. The final documentation formatting and reference receipts
are retained beside this report's source.

Four focused HU device tests pass: default window behavior, enabled dim/restore, denied special
access and automatic-mode fallback. One phone default-window regression passes. The phone fixture
uses manual brightness for stable assertions and restores its original automatic-mode setting; the
earlier unstable automatic-mode diagnostic is not counted as a passing result. The initial
short-timeout enabled fixture awaited configuration before the controller was ready; the final
fixture awaits both controller and focus. Its passing result supersedes that harness diagnostic. An
exploratory hidden-setter fixture was unavailable and was removed; no hidden API is present in the
maintained fixture or product, and no hidden-API policy was relaxed.

The enabled fixture verifies 102 -> 34 without repeated division, an external 180 -> 60 update,
restore to 180 on input/background, renewed grace and removal of the recovery record. The working
signed HU independently passes its unmodified 30-second timeout:

| Working HU phase              | Stored brightness | Panel driver brightness |
| ----------------------------- | ----------------- | ----------------------- |
| Installed, active             | 102               | 102                     |
| Normal idle timeout           | 34                | 34                      |
| Forced process stop while dim | 34                | 34                      |
| Cold Activity restart         | 102               | 102                     |
| Repeated normal idle timeout  | 34                | 34                      |
| F1 input and final Home       | 102               | 102                     |

The live phase records also show temporary brightness NaN and the expected window override while
idle. This is Android policy and driver-endpoint evidence, not optical measurement. The earlier
[collision investigation](/Riff/Experiments/hu-brightness-20261007) reproduced a temporary 0.51
state overriding the old window request, and normal system-slider interaction cleared it. This
implementation run does not recreate that collision or identify its originating caller. Clearing it
through a changed public setting follows the Android 13
[brightness-update implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-13.0.0_r1/services/core/java/com/android/server/display/DisplayPowerController.java);
that source explains the approach, while the fresh device table bounds what was directly observed.
No new HU firmware/MCU reverse engineering was conducted.

## Delivery, preservation and limits

Artifact: `releases/261007-rc-hu-backlight-final/riff-0.1.0-rc-261005.apk`, 106,683,600 bytes,
SHA-256 `73dce3ec8a8f85f8e128e3b9eff4608000935bcad21350c6e9e01182c37dc76a`. Existing certificate
SHA-256 remains `8f1dce0e9f5ae50fbead23ddb3d60182f9af4b3a584481eeee1f3df5c499e67d`; fixed RC 261005,
version 0.1.0-rc. The working HU update uses adb install -r; installed bytes match this artifact.
UID, first-install time and the exact persisted media grant set remain unchanged. Final stored
brightness and panel endpoint are 102, manual mode 0, with the launcher in foreground. Disposable
target/test packages on both devices and the provisioning helper are removed. The working package
was never uninstalled. No Git commit, tag or push was performed; unrelated working changes remain.

Abrupt termination can leave global dim until the next Activity launch; immediate recovery after
force-stop is not promised. Revoked access retains a pending recovery record. Numeric equality
cannot distinguish another writer deliberately choosing the same value. Automatic brightness
acquires no global lease. The original temporary state's source and recurrence, optical luminance
and vehicle-host acceptance remain unverified. `actual_brightness` reads zero even when the panel is
lit and is not used as proof.

Canonical evidence is `status/hu-brightness-fix-20261007`, including JVM totals, signed-release log,
HU/phone instrumentation results, three disposable reset results, working provisioning report,
before/after package and exact-grant comparisons, installed APK hash, seven live phase records and
final cleanup. The earlier collision evidence remains in `status/hu-brightness-20261007.2hmmkwv6`.

Representative commands from the canonical dev-box checkout:

```sh
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew --offline --console=plain \
  :configuration:check :app:testInstrumentedUnitTest :app:ktlintCheck \
  :app:lintDebug :app:verifyDistributionRelease
~/venv/riff-build/bin/python bin/build_release.py \
  --output-dir releases/261007-rc-hu-backlight-final
~/venv/riff-build/bin/python tools/hu-backlight/provision.py \
  --serial "$RIFF_HU_SERIAL" \
  --apk releases/261007-rc-hu-backlight-final/riff-0.1.0-rc-261005.apk \
  --output status/hu-brightness-fix-20261007/working-hu-provision
npm run lint --prefix tools/documentation
npm run references --prefix tools/documentation -- \
  --wiki-tree /home/riff-adb/riff/status/hu-brightness-fix-20261007/reference-check-wiki
```
