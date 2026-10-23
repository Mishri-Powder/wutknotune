# Indexer AI model

> Owner/scope: Indexer Level 2 model semantics, features, heads and calibration

## Model and inference semantics

Production feature extraction belongs exclusively to `:indexer`, offline and off main/playback
threads. Player consumes validated MDRG and Indexer-produced AI genre projections only. Automatic
audiobooks are vetoed before AI source resolution/I/O and do not enter AI worksets/counters/results.
Indexer never reads profile overrides. Valid external RIFF value/confidence pairs are accepted
before inference. Fully tagged AI contains valid PTE/Arousal/Valence pairs and usable probabilistic
Genre evidence; it needs no model load or AI source I/O. Missing confidence remains absent and is
not usable runtime evidence. Never mix fields from independent source candidates. Inference fills
unresolved outputs without replacing accepted source pairs. Books ignore every AI tag before AI
workset selection. One shared frozen EfficientAT MobileNetV3 `mn10_as_mels_64` trunk supplies three
scalar ensembles and one multilabel Genre ensemble; remove the AudioSet 527-class classifier and
consume its global-average-pooled 960-element embedding. PTE/arousal/valence are finite `[-1,+1]`
values with independent `[0,1]` confidences. Physical BPM and Mood are not runtime features or
fallbacks.

| Item                       | Exact contract                                                                                                                                                                                                                                                                                                                                                   |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Backbone source/checkpoint | EfficientAT commit `a425fdce92572e602a1d5634799bd9f1f2efa806`; width-1.0 static AudioSet/64-mel `mn10_as_mels_64_mAP_461.pt`, 19,696,465 bytes, SHA-256 `50d004a4d0bb2dc7bad63a9196b6117f4a3df528b3e8ffd7ec4350cf4a9fbf5c`                                                                                                                                       |
| Samples                    | Deterministic mono float32 at 32,000 Hz. For decoded duration `D>0`, three 10-second windows start at `clamp(D*p-5,0,max(0,D-10))`, `p={.25,.50,.75}`. Right-zero-pad shorter audio                                                                                                                                                                              |
| Decode scheduling          | [Shared L2 procedures](/Riff/LATEST/indexer-analysis-procedures) own bounded AU acquisition, three reusable sessions, pre-roll, retries and cancellation.                                                                                                                                                                                                        |
| Frontend                   | `y[n]=x[n+1]-.97*x[n]` (319,999 samples); centered unnormalized-power STFT: FFT 1024, hop 320, symmetric nonperiodic Hann 800, reflect padding. Kaldi mel 64 at 32 kHz, fmin 0/fmax 15000, vtln_low 100/vtln_high −500/warp 1, including padded final FFT-bin coefficient; `ln(mel+1e-5)`, then `(x+4.5)/5`. Exactly1000 frames/window, no training augmentation |
| Embedding                  | Same weights for all windows, batched or bounded sequential with parity. Elementwise mean of three 960-element vectors, then stored training mean/deviation standardization; training deviations below 1e−6 become 1                                                                                                                                             |
| Scalar members             | Exactly three independent retained seeds/shuffled training orders per feature. Validation-only selection, never test tuning. PTE 960→512→128→1; A/V 960→256→64→1; GELU after hidden layers, training dropout .20/.10, tanh value plus finite unconstrained log-variance from final hidden representation                                                         |
| Auxiliary outputs          | Each A/V member trains its own auxiliary PTE value/log-variance on the same item. Prune all auxiliaries in production; only dedicated PTE ensemble publishes PTE                                                                                                                                                                                                 |
| Genre members              | Three independent 960→256→87 heads, GELU/dropout .20, unconstrained logits in registered synthetic-genre class/ID order; independent sigmoid/BCE labels, no softmax, forced top1/topK or exclusive-class reduction                                                                                                                                               |

Decode failure uses per-binding fallback. All workers/futures/codecs are released on success,
failure, cancellation and close; cancellation fences apply through acquisition, windows, inference
and publication. No unbounded queue or duplicate per-feature backbone exists.

For each scalar feature and member `i`, the manifest supplies finite `Lmin<Lmax`. Compute
`vᵢ=exp(clamp(lᵢ,Lmin,Lmax))`, `m=mean(μᵢ)`, `V=max(0,mean(vᵢ+μᵢ²)−m²)`. Its
artist-disjoint-validation-fitted monotone nondecreasing piecewise-linear calibrator `E(sqrt(V))`
estimates absolute error on the normalized axis, clamping outside finite stored knots. Publish
`value=m`, `confidence=clamp(1−E(sqrt(V))/2,0,1)`. All three members/log-variances, normalization
and feature calibration must validate or both fields are absent. No tanh-magnitude, fixed, softmax,
cross-feature or auxiliary confidence exists. Dropout is off at inference.

For Genre class `g`, average three `sigmoid(logitᵢ,g)` probabilities, apply that class's monotone
validation-fitted calibrator and acceptance threshold, and publish only independently accepted
classes with calibrated applicability confidence. Invalid/missing outputs/calibrator/threshold omit
that class; metadata names are not fallback. Test data cannot select members/calibration/thresholds.
Mood may remain in training sidecars only.

Training supervision: A/V uses full-track Essentia teacher `clip((value−5)/4,−1,1)`; PTE uses
versioned `essentia-full-track-proxy-v1-no-bpm`. Train value targets and residual log-variance with
proper Gaussian negative-log-likelihood, calibrating on held-out validation. Genre uses all
zero-or-more official MTG-Jamendo `autotagging_genre` labels and registered stable ID/sidecar
contracts. Baseline scalar corpus is successfully labelled folders 00..29 with official
artist-disjoint split: 9552 train/3186 validation/3533 test. Backbone remains frozen. Changing
corpus/targets/split/backbone fine-tuning/head topology requires normative adoption. Essentia is
teacher-only: no HU invocation or APK/runtime artifact. Distribution requires reviewed rights for
teacher outputs, data, upstream code/checkpoint, derived weights and notices.
