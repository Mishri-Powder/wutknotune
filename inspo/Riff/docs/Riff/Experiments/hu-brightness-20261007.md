# HU panel brightness and Riff override — 2026-10-07

> Evidence only. No requirement, application behavior or firmware change is adopted by this report.

## Findings

The panel endpoint recorded by
[Echo halservice triage](https://wiki.271.pics/AndroidRE/echo-halservice-re/docs/halservice-re) is
present on the live HU at `/sys/class/backlight/panel0-backlight`, resolving to the documented
`/sys/devices/platform/soc/5e00000.qcom,mdss_mdp/backlight/panel0-backlight` path. The ordinary
Android Settings brightness slider changes its `brightness` value. This validates the endpoint
identity; it does not identify a particular halservice function or establish whether halservice
performed an individual write.

The installed Riff window override reaches Android correctly. In the initially failing state,
`mTemporaryScreenBrightness=0.51` and `mBrightnessReason=temporary` keep the final display command
at 0.51 even though the focused Riff window requests 0.13333334. After ordinary system-slider
interaction clears that temporary state, the same installed Riff successfully changes the panel
command from 102 to 34 and restores 102 on user input.

## Controlled observations

| Phase                                   | Stored system brightness | Window request | Temporary brightness | Display result/reason | Panel brightness |
| --------------------------------------- | ------------------------ | -------------- | -------------------- | --------------------- | ---------------- |
| Initial focused Riff                    | 102                      | none           | 0.51                 | 0.51 / temporary      | 130              |
| Initial Riff after normal idle timeout  | 102                      | 0.13333334     | 0.51                 | 0.51 / temporary      | 130              |
| Initial Riff after input                | 102                      | none           | 0.51                 | 0.51 / temporary      | 130              |
| System slider low                       | 9                        | none           | NaN                  | 0.031810295 / manual  | 9                |
| System slider high                      | 141                      | none           | NaN                  | 0.551839 / manual     | 141              |
| System preference restored              | 102                      | none           | NaN                  | 0.3976378 / manual    | 102              |
| Repeated Riff after normal idle timeout | 102                      | 0.13333334     | NaN                  | 0.13333334 / override | 34               |
| Repeated Riff after input               | 102                      | none           | NaN                  | 0.3976378 / manual    | 102              |

In both Riff cycles the shipping 30-second idle policy was used; no test clock or application data
was changed. The implementation uses `WindowManager.LayoutParams.screenBrightness` and the
independent `Settings.System.SCREEN_BRIGHTNESS` baseline, as owned by
[Player inactivity](/Riff/LATEST/player-inactivity#window-brightness-policy).

## Interpretation and limits

The reproduced failure is a collision with Android temporary brightness, rather than a missing
window request: removing the temporary state makes the unchanged Riff request reach the same panel
endpoint controlled by the system slider. The caller that originally set 0.51 and its possible
recurrence were not identified; no new OEM reverse engineering or caller tracing was performed.

The user's established observation is that the system menu changes physical screen brightness. This
run verifies that Riff can reach that same panel-control endpoint. It does not independently measure
optical luminance, luminous response linearity or a threefold physical luminance ratio.
`actual_brightness` stays zero across all tested levels on this device and is not a usable optical
confirmation. Screenshots were not used as luminance measurements.

The OEM attributes `car_min_brightness=94`, `car_max_brightness=204` and
`car_run_mode_brightness=94` did not change during these observations. Their names alone must not be
used to infer a physical clamp: the observed ordinary panel command went below 94.

## Scope, restoration and reproducibility

Target: explicitly selected ADB serial `172.16.2.1:7088`, Android 13/API 33 HU, installed signed
`link.roadio.riff` version `0.1.0-rc`. The phone was not modified. The experiment used standard
Android UI, `settings get/put`, `dumpsys display/window`, read-only sysfs access and normal Riff
launch/input. No MCU command, firmware operation, root escalation, APK installation, production
source change or application-data reset was performed.

The initial stored preference 102 and manual mode 0 were restored, and the original launcher was
returned to the foreground. The old temporary 0.51 override was cleared by system-menu interaction
and was not recreated: the final panel command is 102, compared with the initial temporary
command 130. This distinction is deliberate in the restoration record.

Raw observations, UTC timestamps, command harness and all thirteen phase summaries are retained on
the canonical dev box at `/home/riff-adb/riff/status/hu-brightness-20261007.2hmmkwv6`. `probe.py`
contains the first comparison; the remaining exact phase commands are retained with the experiment.
The final state is phase `13-restored-final`.
