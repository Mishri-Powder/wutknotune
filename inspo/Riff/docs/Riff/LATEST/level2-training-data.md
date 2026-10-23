# Riff Level 2 training sidecar contract

> **Status:** Adopted normative scoped contract\
> **Authority:** Registered by [Riff system requirements](requirements.md).\
> **Scope:** Source-neutral per-track training-label sidecars, their validation, provenance and
> split boundaries, and the separation between training-only Mood and production Level 2 outputs.

## 1. Purpose and boundary

- A training corpus MUST be supplied to the Level 2 toolchain as one UTF-8 JSON sidecar per audio
  item. The sidecar has the same basename as the audio and mirrors its relative directory below a
  separately configured writable sidecar root. For example, audio `downloads/00/123.mp3` maps to
  `<sidecar-root>/downloads/00/123.json`.
- An absolute audio path, source-corpus checkout, downloaded checkpoint, generated sidecar corpus,
  embedding cache, teacher output, or trained model MUST NOT be committed to the Riff repository.
  The Android repository contains only Android runtime/package integration and compact transferred
  package evidence. Under [Level 2 model](indexer-ai-model.md), bulk data plus all preparation,
  training, calibration, and conversion scripts and outputs live only in the designated
  model-production environment, `riff-ai@baldr:~/riff-level2-model-production`; they MUST NOT be
  mirrored into `riff-adb`, the Android repository/workspace, or another Android development
  account.
- The same validated format accepts current MTG-compatible and Essentia-derived data or a legally
  substitutable future corpus. Dataset names and provenance describe the supplied source and MUST
  NOT alter the JSON shape or model I/O contract.
- Corpus preparation, training, calibration, and TFLite conversion are explicit offline
  model-production operations on the designated compute environment. An ordinary APK build consumes
  only the closed transferred package defined by [Level 2 model](indexer-ai-model.md) and MUST NOT
  download a checkpoint, read a training sidecar, invoke Essentia, train a head, or convert a model.

## 2. Exact `RIFF-L2-SIDECAR-V1` object

The top-level object has exactly these required members and no others:

```json
{
  "schema_version": "RIFF-L2-SIDECAR-V1",
  "audio": {
    "relative_path": "downloads/00/123.mp3",
    "track_id": "123",
    "artist_id": "45",
    "album_id": "67",
    "duration_seconds": 30.0,
    "sha256": null
  },
  "split": {
    "dataset": "mtg-jamendo",
    "split_id": "autotagging-split-0",
    "partition": "train"
  },
  "annotations": {
    "genre": ["60s", "dance"],
    "mood": ["energetic"],
    "essentia": {
      "duration": 30.0,
      "arousal": 5.6913,
      "valence": 5.5225,
      "onset_rate": 2.2,
      "bpm": 85.1,
      "danceability": 0.971,
      "dynamic_complexity": 4.644
    }
  },
  "targets": {
    "pte": {
      "value": 0.31,
      "transform": "essentia-full-track-proxy-v1-no-bpm"
    },
    "arousal": {
      "value": 0.172825,
      "transform": "essentia-emomusic-5-to-normalized-v1"
    },
    "valence": {
      "value": 0.130625,
      "transform": "essentia-emomusic-5-to-normalized-v1"
    },
    "genre": [-100022, -100000]
  },
  "provenance": [
    {
      "kind": "genre",
      "source": "mtg-jamendo/autotagging_genre split-0",
      "revision": "dataset-defined revision",
      "license": "source-declared license; review required"
    }
  ]
}
```

The repository JSON Schema is the machine-readable authority for member types, requiredness, bounds,
and closed objects. The following semantic rules additionally apply:

- `audio.relative_path` is normalized POSIX syntax, relative to the configured audio root, contains
  no empty, `.` or `..` component, and identifies the paired audio item. `track_id`, `artist_id`,
  and `album_id` are non-empty source identifiers. `duration_seconds` is finite and positive.
  `sha256`, when present, is exactly 64 lowercase hexadecimal digits; every item admitted to a
  release model manifest MUST have a verified non-null hash.
- `split.partition` is exactly `train`, `validation`, or `test`. One `track_id` and one artist MUST
  occur in only one partition for a `dataset`/`split_id`; training tools MUST reject cross-partition
  leakage. Selection, early stopping, thresholds, and calibration use validation only; test remains
  measurement-only.
- `annotations.genre` and `annotations.mood` contain unique non-empty source label tokens sorted by
  Unicode code point. Zero, one, or many labels are valid. The `essentia` member is either null or a
  non-empty closed object containing any available subset of the seven named fields shown above;
  every present number is finite. This permits source-preserving records whose experiment omitted
  BPM. Raw BPM may be retained as teacher provenance but MUST NOT enter the PTE transform.
- `targets.pte`, `targets.arousal`, and `targets.valence` are independently null or closed
  value/transform objects. Their values are finite in `[-1,+1]`. `targets.genre` contains zero or
  more unique signed genre IDs sorted in ascending signed order. Every current MTG-compatible label
  maps through the exact [synthetic genre table](synthetic-genres.md); parallel labels such as
  `dance` and `60s` remain separate simultaneous targets.
- `provenance` is non-empty. Each used annotation/target source, transform, or teacher must be
  traceable to a source, revision, and license statement. A sidecar being structurally valid is not
  license approval for training or distribution.
- Writers MUST create parent directories and publish each JSON file atomically by complete
  temporary-file write, flush, close, and same-directory rename. A generator MUST never modify the
  audio tree. Canonical serialization is UTF-8, no byte-order mark, valid JSON finite numbers only,
  sorted object keys, two-space indentation, and one final newline.

## 3. Production versus training-only data

- PTE, arousal, valence, and the zero-or-more genre IDs are production supervision. They may
  contribute to the adopted Level 2 model and its release evidence.
- Mood is deliberately training-only auxiliary metadata. It MAY be retained for experiments or
  future representation learning, but it is not a retained Level 2 head, TFLite output, calibrator,
  manifest class list, MDRG field, `data_raw` payload, Player feature, or selection input.
  Activating Mood in any of those places requires a later explicit contract change.
- The `annotations.essentia` object is teacher evidence only. It MUST NOT be copied to MDRG or
  packaged into the APK. Only validated transformed targets and their provenance may affect model
  training.

## 4. Verification

Verification MUST cover schema rejection of unknown or non-finite data; relative-path escape;
basename/directory mismatch; duplicate or unsorted Genre/Mood labels and genre IDs; invalid hashes;
invalid partitions; unknown genre IDs; artist/track split leakage; atomic-write interruption;
deterministic byte-for-byte regeneration; absent Essentia and independently absent scalar targets;
multi-label `dance` plus `60s`; Mood retained in the sidecar but absent from production outputs; and
release-manifest source/hash/ license completeness.
