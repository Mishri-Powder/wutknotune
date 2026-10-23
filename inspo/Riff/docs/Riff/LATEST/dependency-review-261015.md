# Dependency and consistency review — 2026-10-15

This is dated verification evidence for the 0.1.7-rc refresh. The
[dependency-security contract](/Riff/LATEST/dependency-security) owns the recurring requirement. The
next completed review is due **2026-11-15**.

## Adopted versions

Stable versions were checked against official Maven metadata, upstream release records and PyPI/npm
registries on 2026-10-15. The complete resolved inventory and downloaded metadata are in
~/riff/status/dependency-refresh-261015 on the canonical development host.

| Component                                             | Adopted version                   |
| ----------------------------------------------------- | --------------------------------- |
| Gradle / AGP / Kotlin and Compose compiler            | 9.8.1 / 9.4.1 / 2.4.21            |
| Compose BOM / Media3 / LiteRT                         | 2026.09.00 / 1.11.1 / 2.3.0       |
| Google FlatBuffers compiler, Kotlin and Java runtimes | 25.12.19                          |
| xxHash C / libebur128                                 | 0.8.4 / 1.2.6                     |
| hash4j / JetBrains annotations / org.json             | 0.31.0 / 26.1.0 / 20260814        |
| ktlint Gradle plugin / engine / Logback               | 14.2.0 / 1.8.0 / 1.6.5            |
| Markdownlint / markdown-it / Prettier / KaTeX         | 0.42.0 / 15.0.2 / 3.9.10 / 0.19.0 |
| lxml / httpx / anyio / Python xxhash                  | 6.1.3 / 0.28.1 / 4.15.1 / 4.0.1   |
| jsonschema / Mutagen / python-vxi11                   | 4.26.0 / 1.48.1 / 0.9             |

Activity 1.13.0, JUnit 4.13.2, AndroidX test runner 1.7.0 and ext-junit 1.3.0 remain the latest
stable releases. CED remains upstream revision d127078cedef9c6642cbe592dacdd2292b50bb19. The
application runtime graph is constrained to current stable transitive releases; the complete list
lives in gradle/runtime-dependencies.gradle. LiteRT's upstream ai-delivery:0.1.1-alpha01 has no
stable replacement. Its presence is recorded, not described as a stable release. Embedded native
components retain the upstream LiteRT build's pins and patches; they are not independently replaced
inside the binary AAR.

FlatBuffers uses the formal 25.12.19 release, rather than the timestamped development snapshot
exposed by GitHub's latest-release endpoint. Compiler/runtime versions match. Compile SDK is 37.2 as
required by current AndroidX; target 36 and minimum API 31 remain. Documentation checks used
[Node 24.22.0 LTS](https://nodejs.org/en/blog/release/v24.22.0).
[AGP's release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) and
[Gradle 9.8.1](https://github.com/gradle/gradle/releases/tag/v9.8.1) identify the host tool
releases.

## CVE coverage and remediation

OSV batch queries cover **429 exact coordinates**: 353 Maven, 59 npm and 17 PyPI combinations. Maven
includes application, tests, plugin classpaths, ktlint and both independent libraries. The final
results contain **zero matching advisories**; npm audit also reports zero findings. These results
describe published-index coverage, not proof that defects cannot exist.

The review found and removed or upgraded the following build/documentation dependencies:

- Logback 1.3.14: CVE-2024-12798, CVE-2024-12801, CVE-2025-11226, CVE-2026-1225, CVE-2026-9828 and
  CVE-2026-10532; use 1.6.5.
- Commons Lang 3.16.0: CVE-2025-48924; use 3.21.0.
- jose4j 0.9.5: CVE-2024-29371; use 0.9.7.
- Bouncy Castle 1.80.2: CVE-2026-5588, CVE-2026-0636, CVE-2026-8763 and CVE-2026-13506; align
  provider, PKIX and utility modules to 1.86.
- JDOM 2.0.6: CVE-2021-33813; use 2.0.6.1.
- Remove the Markdownlint CLI wrapper and its vulnerable braces/smol-toml dependencies; retain the
  same rules through the direct Markdownlint API. KaTeX is pinned to 0.19.0.

The exact advisory records and fixed-version ranges are retained in osv-remediated-logback.json,
osv-remediated-build.json, and the earlier npm audit evidence. Sources are [OSV](https://osv.dev/)
and the corresponding CVE/upstream references in those records.

Native review includes NVD searches for libebur128, compact_enc_det, xxHash, FlatBuffers and LiteRT,
LiteRT's public security-advisory endpoint, and 22 OSV commit queries covering adopted
CED/libebur128 and source pins from LiteRT 2.3.0's upstream workspace. Those commit queries return
no matches. LiteRT's TensorFlow source pin is 5c0b7a5946f0f485e3a532b2a00e03f42a6e14c1. Upstream
source recipes are evidence of declared inputs, not an independently reconstructed binary SBOM. The
exact AARs, licenses and bundled third-party notices have retained SHA-256 provenance.

NVD name matches were assessed individually: CVE-2021-39131 concerns the Node ced wrapper;
CVE-2022-49068 concerns Linux btrfs; CVE-2026-59949 concerns yawkat LZ4 Java JNI; CVE-2019-25004 and
CVE-2020-35864 concern Rust FlatBuffers; CVE-2026-88344/88345 concern flatcc, a separate C compiler.
These are not the adopted implementations. NVD returned no libebur128 or LiteRT keyword matches.
Failed or unindexed lookups must not be reinterpreted as security guarantees in future reviews.

## Clean cut and consistency

Metadata processing revision is now RIFF-METADATA-V3. Existing incompatibility handling requests
complete AU+IA recreation after storage admission and retains publication-generation watermarks. No
migration or old-format reader was added. Cuetree format 10 and Player schema 15 are unchanged.

The FlatBuffers Kotlin adapter now retains complete multiline JVM declarations and rejects an
incomplete expect/actual mapping; three focused tests cover that boundary. Playback logs read
Media3's actual runtime version. Current build guides, terminology, notices and dependency pins are
reconciled. Shared human/AI comments remain allowed; nonobvious maintenance invariants use
AI-INTENT: with scoped links. Formatting follows ktlint 1.8.0. Parentheses clarify mixed boolean
conditions without changing their precedence.

## Verification

Root verification passes: 29 configuration, 297 Player and 163 Indexer JVM tests, Kotlin checks,
Android lint, disposable APK assembly and distribution/model checks. Python suites pass 5 build, 3
FlatBuffers adapter, 12 genre and 3 icon tests. Cuegrain passes 51 tests with optional CED enabled;
Cuetree passes 140. Both build from isolated source-only exports; exported Cuetree consumes a
locally published independent Cuegrain artifact, without an adjacent source override. The Cuetree
demo passes against a fresh disposable catalog.

On each device the Indexer runner reports OK (49 tests), including packaged CPU LiteRT output parity
against the immutable producer vector; optional corpus/benchmark assumptions remain. HU UI results
are 193 passed, 5 skipped, zero failures. Phone UI results are 182 passed, 5 skipped, 11 failures.
Rebuilding the unchanged prior main commit and rerunning the phone suite produces the same eleven
failing methods. These existing phone fixture/rendering/brightness limitations are retained as open
test work, not counted as a green phone suite or hidden by disabling checks.

Evidence includes checks.log, remaining-checks.log, tests, baseline-tests, exact resolved
inventories, upstream metadata, and semantic-scope.json. The latter isolates handwritten Kotlin
token changes from formatter output; it is a review aid, not a formal equivalence proof. Release
installation/publication evidence is recorded separately after delivery.
