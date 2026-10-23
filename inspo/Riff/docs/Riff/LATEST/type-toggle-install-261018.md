# Music/Book controls and status — local installation 2026-10-18

The user authorized installing the verified working update on the phone and HU. Both now run the
same newly signed release-variant APK with package `link.roadio.riff`, version **1.0.0**, code
**14**, channel **FINAL** and declared date **2026-10-18**. Version metadata is retained; the exact
APK hash below distinguishes this uncommitted local update from the separately published 1.0.0
release.

- Artifact: `~/riff/releases/1.0.0-type-toggle-261018/riff-1.0.0-261018.apk`.
- Size: 109428849 bytes.
- APK SHA-256: `e74e304e03575cf29322e2508c1ff4734a0b166b21ff8954af10cf37ec7cf9f1`.
- Signing certificate SHA-256: `8f1dce0e9f5ae50fbead23ddb3d60182f9af4b3a584481eeee1f3df5c499e67d`;
  matches the existing identity.
- Base commit: `9922812372c4905033e0e55049166eb28c3facea` plus the current working changes.
- Source patch, manifest, per-device install/package/log receipts and verification remain under
  `~/riff/status/type-toggle-install-261018`; new source/tests remain in the canonical working tree.

The signed APK has one configuration resource with `behavior.enableStatusLines=true`. It contains
the
[verified two-toggle menus and commit-completion fix](/Riff/LATEST/type-toggle-status-review-261018).
Both devices were updated using `adb install -r`. Each installed `/base.apk` SHA-256 was read back
from the device and matches the new signed artifact. UID and first-install time are unchanged. Both
MainActivity launches report `Status: ok`, and Player remains alive in the subsequent check. No Riff
crash, pipeline failure or rejected startup configuration was observed in the monitored startup
interval. No manual database/app clear or uninstall was performed.

The prior verification remains current for this code: 332 JVM cases, build/lint/distribution gates,
and three Android cases on each device. This task added signing, exact installed-byte verification
and real working-package startup checks. User file overrides still take priority over the packaged
status default.

No new commit, tag or push was requested or performed. The website and public source/APK still serve
the previously published 1.0.0 artifact; this installation did not publish the working changes.
