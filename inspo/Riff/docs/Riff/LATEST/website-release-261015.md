# Website release — RC 261015

The [website](https://roadio.link/) publishes Riff **0.1.7-rc**, versionCode **10** and matching
source. The existing design, articles and dated phone screenshots are preserved. Screenshot captions
still identify the actual 0.1.6-rc / RC 261014 capture.

## Public artifacts

- [Signed APK](https://roadio.link/downloads/riff-0.1.7-rc-261015-c092fb580ce7.apk), SHA-256
  c092fb580ce76fb3f62dc89717527cd7f41ff5ad5e56372ca82f55493b913f4d.
- [Source archive](https://roadio.link/downloads/riff-source-0.1.7-rc-261015-26116fe209c8.tar.gz),
  SHA-256 26116fe209c875c0e08413adbe6b606ec94bd4146dcf378f68b11b4f2b9ecd1b.
- [Exact Git snapshot](https://roadio.link/repo/riff-18e64daa25bc05e68a066750b2f5ac2d537f21fc.git).
- Source root commit: 18e64daa25bc05e68a066750b2f5ac2d537f21fc.
- Source tree: 621386f65df7656091f18c2a179072a8b2928f91; 1319 files, one root commit.
- Canonical development source: 963fd4d56e9ff0d2d02afdc89bf67eef39206d8f.
- APK build source: e3b6fe9e1351a8f8cf9475ec87ee771175be1463; only handoff formatting differs from
  the published source.

The public cut excludes development history, signing material, local evidence, generated builds,
unrelated historical pipelines and excluded standalone experiments. It includes current generators,
dependency constraints, independent libraries, tests, model assets and complete retained notices.
The source-only cut builds and passes verifyDistributionRelease.

## Publication verification

All **1,428 origin files** match the staged SHA-256 inventory and required headers. Origin HTTP Git
cloning yields the exact one-commit tree. Public HTTPS cloning through the versioned Git URL also
yields the exact commit/tree and all **1,319 source files**.

The first publication passed all 31 selected ordinary public responses. After adding the versioned
Git address, 29/31 match the final bytes. The cached index.html and downloads/snapshot.json still
describe the same new 0.1.7-rc edition, but predate the new clone URL. The unversioned
/repo/riff.git endpoint also serves older cached refs; use the exact snapshot link above. No
Cloudflare credentials were accessed or purge issued. Manual cache clearing remains available for
the mutable URLs; the versioned clone is already verified.

Content-named APKs, archives, existing Git packs and screenshot/article assets are preserved. A
pre-deployment backup is retained on both development hosts under
riff/status/dependency-refresh-261015/live-before.tar.gz.

Evidence: site-inventory.json, source-inventory.json, origin-verification.json,
delivery-http-initial.json, delivery-http-verification.json, public-clone-verification.json and
canonical-public-clone-verification.json in the canonical task status directory.
