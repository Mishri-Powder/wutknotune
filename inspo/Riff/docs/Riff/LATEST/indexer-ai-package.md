# Indexer AI package

> Owner/scope: Indexer Level 2 package, runtime artifact and production boundary

## Package and delivery

One immutable versioned model package binds frontend, backbone/hash, normalization, all 12 retained
head hashes/seeds, variance bounds, scalar calibration knots, exact 87 Genre
IDs/calibrators/thresholds, targets, dataset/splits, numeric format, conversion versions and
whole-package checksum. Reject partial, mismatched, unknown or corrupt packages. Production
activation requires dependency/license approval, package validation, Python/conversion parity,
regression/calibration reliability, failure/cancellation tests and real-QCM6125 time/memory/playback
evidence. Completed non-AI playback resilience, profile/playlist behavior and selection-math gates
remain prerequisites; evidence/status is recorded outside this registry.

The sole Android neural artifact is one float32 `.tflite` graph, stored uncompressed, opened by
exact offset/length descriptor and read-only mmap, never whole-model heap copy. Its mapped bytes are
checked against the packaged XXH3-64 inventory before interpreter activation. It contains one trunk,
global pooling, three-window mean, normalization, nine scalar value/log-variance heads and three
87-logit Genre heads. No AudioSet classifier, training augmentation/dropout, Essentia, duplicated
trunks, ONNX/ONNX Runtime, unapproved custom ops or SELECT_TF_OPS. Frontend stays outside TFLite
with reference parity. Input float32 NHWC `[3,1000,64,1]`; graph internally transposes to backbone
convention. Outputs: `value[3,3]`, `log_variance[3,3]` (feature axis PTE/A/V, member axis 1/2/3),
`genre_logits[3,87]` (member then class). Kotlin computes sigmoid, uncertainty, calibration and
thresholds. Quantization/new I/O are contract changes. Optional delegate internal precision requires
parity/calibration; explicit supported-device policy and startup vectors gate delegates. Failure
falls back to pinned XNNPACK/CPU with the same artifact/semantics and no partial results.
Initialization/inference is bounded, offline and Indexer-owned. Record exact pinned TFLite/LiteRT
coordinates, ABI, operators/delegates, owner/license/notices, APK impact and QCM6125 resource
evidence before introducing the interpreter.

Model preparation is an explicit offline operation as `riff-ai` on `baldr`, root
`~/riff-level2-model-production`; Android build/test/deploy/ADB runs as `riff-adb`. Do not run
model-production tooling/dependencies in the Android account/repository or retain corpora, sidecars,
teachers, embeddings, training checkpoints/environments/code there. Android setup follows the
repository's `BUILD.md`, installing its pinned dependencies/toolchains upstream rather than copying
machine state. Only one immutable evaluation/release-candidate package crosses accounts: ready
TFLite and closed specification bundle (manifest/calibrators, licenses/notices, compact vectors,
validation report, external SHA-256 provenance inventory), no training data/code/environments.
Ordinary Gradle builds validate that provenance and package an exact XXH3-64 runtime inventory with
the model; they never train, convert or download model-production inputs.

A public-beta model package may run before the stable production quality gates with
`deployment_class="public-beta"`, `distribution_allowed=true`, declared provenance and exact
deviations. It decodes real audio, validates and mmap-loads offline, isolates binding failures and
publishes finite valid MDRG AI records; provisional heads and calibration are identified as beta
quality. Discard synthetic generations before inferred output, never mix or migrate them. Attempt
every eligible current MDIA item; sparse successes are published and failures do not terminate
following work. The pinned CPU evidence includes at least 30 successful real tracks spanning
short/~30s/full-length when available; 30 is a sample minimum, never a scan cap. Record
source/decode/window/frontend/inference/postprocess/publication timing, cold initialization, warm
p50/p90/p95/max, sample/failure counts, threads/delegate/fallback, peak RSS, package/model bytes,
device/Android and uninterrupted playback. Optional accelerators use identical inputs/parity.
Throughput is distinct from model accuracy. Current-run progress excludes existing accepted rows and
inaccessible media.

The public beta always uses that content-derived package and MUST fail Level 2 clearly if it is
missing, mismatched or corrupt; it MUST NOT silently substitute synthetic analysis. The exact
CPython-compatible synthetic generator remains internal test/development scaffolding only and is
never packaged or selected as public-beta AI output. Synthetic and content-derived outputs never
coexist or claim equal meaning.
