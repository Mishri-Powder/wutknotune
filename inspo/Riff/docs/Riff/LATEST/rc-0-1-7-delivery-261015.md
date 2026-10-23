# Riff 0.1.7-rc — delivery 2026-10-15

Signed **0.1.7-rc**, versionCode **10**, **RC 261015** is installed and launched on HU and phone.
The implementation commit is e3b6fe9e1351a8f8cf9475ec87ee771175be1463; the published source uses
963fd4d56e9ff0d2d02afdc89bf67eef39206d8f, whose only difference is handoff Markdown formatting. Both
are pushed to main.

## Artifact and installation

- APK: riff-0.1.7-rc-261015-c092fb580ce7.apk; 109379697 bytes.
- SHA-256: c092fb580ce76fb3f62dc89717527cd7f41ff5ad5e56372ca82f55493b913f4d.
- Signing certificate SHA-256: 8f1dce0e9f5ae50fbead23ddb3d60182f9af4b3a584481eeee1f3df5c499e67d.
- Model source SHA-256: 8819c2b3723d448dc63e92c12135a57eab00a3f867697439e4eb327bc6f32813.
- Both installed APKs were pulled back and matched the distributed bytes.
- Certificate, package UID, first-install timestamp and persisted SAF grants match the prior
  installation.
- Both launches returned Status: ok, with Player/Indexer processes present and no observed Riff
  fatal startup.
- Both devices logged automatic_library_rebuild reason=IncompatibleLibraryException for metadata V3.
- No package uninstall or clear was used. The HU Player backup succeeded (773,632 bytes); an
  unprivileged full-private-data phone backup is unavailable.

The clean cut recreates derived AU+IA data through the existing selected-root and IPC fences. No
migration was introduced. Cuetree format 10 and Player schema 15 remain. This startup observation
does not claim full-library rebuild completion.

## Verification and remaining work

[Dependency review](/Riff/LATEST/dependency-review-261015) owns the exact versions, CVE review,
root/standalone tests and baseline comparison. The final signed release passed distribution/model,
license, zip alignment and signature checks. A fresh public-source checkout also passed
assembleRelease and verifyDistributionRelease independently.

HU UI: 193 passed, 5 skipped. Phone UI: 182 passed, 5 skipped, 11 failures also reproduced on the
unchanged previous release. Both Indexer runners report OK (49 tests), including CPU model reference
parity. Existing fixture/rendering/brightness failures are explicit open work.

[Website delivery](/Riff/LATEST/website-release-261015) records public artifacts and exact cloning.
[Open work](/Riff/LATEST/open-decisions) retains whole-library, performance, AAOS/host and
real-media acceptance gaps. Next monthly CVE review: **2026-11-15**.

Evidence: ~/riff/status/dependency-refresh-261015, especially install/hu, install/phone,
signed-build.log, public-source-build.log and dependency-review records. The signed original is
~/riff/releases/261015-rc/riff-0.1.7-rc-261015.apk.
