# Riff 1.0.0 delivery — 2026-10-18

## Release and source identity

The user-authorized **1.0.0 FINAL**, versionCode **14**, edition **261018** is built, signed,
installed on every currently connected device, and published. The canonical release branch
`release/1.0.0` was committed and pushed, merged into `main`, and tagged `v1.0.0`; main and the
annotated tag were pushed to the existing origin. The release tree is
`1257991758765af3a9445a85eaca55ffbdbd7740`; its implementation commit is `01d3d7f`. A subsequent
documentation-only main commit records this delivery without moving the tag.

The public source cut is `20db10f0a5da1b16093e9b815b9d11f8e10ed3e9`, tree
`658e3c71c36527200322615fcb072479a3deae20`: **1353 files**, one root commit, no development history
or private signing state. Every exported file was compared to the canonical release tag. Its
independent release assembly and configuration tests passed from the exported checkout.

- [Signed APK](https://roadio.link/downloads/riff-1.0.0-261018-77c12bcc961c.apk) — 109445233 bytes.
- APK SHA-256: `77c12bcc961c98b6d84ce8c428d9b0751b50c57e6bdf0bd684c2359882c305d0`.
- Signing certificate SHA-256: `8f1dce0e9f5ae50fbead23ddb3d60182f9af4b3a584481eeee1f3df5c499e67d`;
  identical to the previous release.
- [Source archive](https://roadio.link/downloads/riff-source-1.0.0-261018-8153292e74af.tar.gz).
- Archive SHA-256: `8153292e74af6adef37e5ab39d5a422e83f0f3371b9bd0c8394aa1feeef8cef7`.
- [Exact public Git snapshot](https://roadio.link/repo/riff-20db10f0a5da1b16093e9b815b9d11f8e10ed3e9.git).

## Behavior and verification

This release includes the [audit corrections](/Riff/LATEST/spec-code-audit-261018), build-configured
catalog language evidence and GNRE role identities. Singles retains weighted grouping and creates no
synthetic genre. FINAL has no diagonal channel label or age modal. Status lines default to false;
DEBUG/TEST defaults remain true and ordinary user overrides win before the immutable startup
snapshot. The Level 2 model remains public beta.

Fresh checks pass: **662 JVM cases** with no failures, errors or skips; five release-helper Python
cases; root Gradle check; app lint and debug assembly; signed-release distribution checks; and an
independent public-source release build. Actual debug/release APK JSON defaults were inspected. The
prior audit's 15 genre/config Python and 27 isolated HU recovery cases remain separately dated. No
dependency versions or declarations were changed; the current dependency review is due 2026-11-15.

The Samsung phone and HU were updated with `adb install -r`, preserving UID and original
installation time. Both Player and Indexer launched. The new metadata revision triggered the
expected automatic library recreation, and both devices published catalog progress. No Riff crash or
pipeline failure was observed in the monitored startup interval. Analysis continued in the
background; full-corpus completion was not an installation gate. The tablet was not connected and
was not updated. No manual application clear/uninstall was performed.

## Website delivery

The website and English/Russian Singles articles describe 1.0.0 and configurable language evidence.
All **1514 origin files** match their staged bytes and required HTTP headers. The public versioned
Git clone matches every source file and contains exactly one commit. Public APK, archive, manifests,
source page and article responses match the new release. All 21 checked routes return HTTP 200; 20
match the latest staged content exactly.

No Cloudflare purge was performed. The canonical homepage still has a cached first 1.0.0 revision
that loads the previous clone-button script; its APK and archive already point to 1.0.0. The final
origin revision copies the exact versioned command displayed in HTML, rather than substituting the
canonical Git URL. A fresh `?release=1.0.0` browser request verifies that final behavior. Both
revisions refer to the same release artifacts. Immutable prior downloads/assets were retained, and
outgoing origin bytes have a private rollback copy.

Browser checks at a 375 × 812 viewport confirm the homepage and both Singles articles render at 360
CSS pixels with no horizontal document overflow; screenshots were inspected and the temporary
viewport override reset. Existing screenshots remain explicitly dated 0.1.6-rc. No internal Wiki
anchors appear in rendered public pages.

Evidence: `~/riff/status/release-1.0.0-261018`; APK: `~/riff/releases/1.0.0-261018`.
[Open acceptance work](/Riff/LATEST/open-decisions) remains unchanged: vehicle-host conformance,
phone UI-fixture repair, broader listening/model and large-library performance verification.
