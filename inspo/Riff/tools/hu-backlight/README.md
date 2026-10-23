# Direct HU brightness provisioning

The packaged `behavior.systemBrightnessWorkaroundEnabled` defaults to false. The separate optional
controller uses public SCREEN_BRIGHTNESS writes with WRITE_SETTINGS special access. Its scoped
[contract](https://wiki.271.pics/Riff/LATEST/player-inactivity#optional-system-brightness-workaround)
owns lifecycle, self-write suppression, external changes, manual-mode handling and crash recovery.
Successful manual-mode control uses the system setting alone; the window path is the fallback. On
the tested HU a raw system zero can leave the panel unchanged; deployment uses night 1% through the
ordinary calculation. No special minimum or zero correction is applied. The helper is operator
tooling, never shipped in the product APK. No root or MCU access is used.

Select only the intended direct HU. This tool does not infer trust or capability from its serial or
model. It requires selected Internal storage and rejects actual AAOS. It preserves valid existing
user keys and sets only the enabling Boolean and dim percentages (day 15, night 1), validates
through the production registry, writes Internal `player/ui_config.json` atomically, grants special
access and verifies a cold production startup snapshot. The same percentage leaf controls ordinary
window dimming, default day/night 30% of the independent system setting. The enable Boolean has no
theme variants. Other values inherit through the common deep merge; no copied default file is
installed. Existing invalid files are rejected without replacement.

From the dev-box repository, after building a signed release with the existing identity:

```sh
~/venv/riff-build/bin/python tools/hu-backlight/provision.py \
  --serial "$RIFF_HU_SERIAL" --apk /path/to/signed-riff.apk \
  --output status/hu-backlight-install-result
```

For provisioning an already installed HU, omit `--apk`. App updates preserve data. Android data
clear or uninstall removes the user file; rerun provisioning before ordinary cold startup. An
explicit destructive working-data reset requires both `--reset-data` and
`--confirm-reset-working-data`. This is an operator procedure, not automatic device detection or a
universal first-launch exception.

Disposable reset verification uses `--package link.roadio.riff.instrumented --reset-data` with its
built instrumented APK. The tool signs its temporary helper with the matching release/debug key,
removes that helper afterwards and never uninstalls the target. `--no-launch` leaves the target
stopped for an isolated test. Results stay in the requested new status directory.

Abrupt process termination can leave a global dim value until the next Activity launch. The durable
lease restores only a still-owned value; a different external value wins. Revoked access leaves
recovery pending. Driver readback is not a photometric measurement or vehicle-host acceptance.
