# Riff Media.ID contract — 1. Scope, acquisition and component formation

> Owner/scope: bounded section of Riff/LATEST/media-id

## 1. Scope and terminology

- The schema field is spelled Media.id. Media.ID is the pair `(high, low)` carried by that field.
- Media.id MUST use MDIA-ID-V3.2 and official unseeded XXH3-64 from the xxHash revision pinned by
  [runtime architecture](/Riff/LATEST/runtime-architecture#internal-hash-policy) for every
  fingerprint, component digest and final digest.
- An access unit (AU) is one encoded audio payload reported by the platform or a conforming adapter.
  It is not decoded PCM or an adapter-specific read-buffer subdivision.
- Cuegrain owns the algorithm through injected AU/random-access/hash interfaces. Riff owns Android
  acquisition; core has no Android/Riff dependency.
- Desktop/Android equivalence is not required. No backend identifier, flag or discriminator is
  added. Within an adapter, tags/paths/read-buffer boundaries remain excluded from identity.

`high` is formed during L1 after complete L0 enumeration; `low` is initially absent. A repeated high
triggers component computation for both sources under
[Cuetree identity](/Cuetree/identity-access#short-id-alias).

## 2. Source, track and constant

Select the lowest-numbered supported track whose MIME begins with `audio/`. No supported audio track
is a typed per-path failure. Do not decode PCM or include container tags.

The unique-payload target B is 131072 bytes. It is a build-time identity constant, not a setting or
hash field. Tests MUST independently assert it.

## 3. High component

For ACCESS_UNITS, maintain a set of unseeded `XXH3_64(AU.payload)` values while reading from the
first AU. The first occurrence of each hash adds that AU's payload length to `unique_bytes`;
repeated hashes add nothing. Include each AU whole and stop after the crossing AU when
`unique_bytes >= B`, or at EOF. Hash every selected occurrence, including repeats, into:

```text
head_hash = XXH3_64(
    ASCII "MDIA-ID-V3.2-HEAD"
    || for each selected AU:
       local_index:u32be || payload_length:u32be || exact payload bytes
)

high = XXH3_64(
    ASCII "MDIA-ID-V3.2-HIGH-ID"
    || 0x00
    || channel_descriptor
    || head_au_count:u64be
    || head_hash:u64be
)
```

Channel descriptor is `0x00` when unavailable, otherwise `0x01` followed by positive
`channel_count:u32be`. The AU count must be nonzero. Hash-only membership is final; do not add
length, byte comparison or acoustic-silence detection.

For supported uncompressed PCM or IEEE-float RIFF/WAVE, parse authoritative format (`0x666d7420`)
and `data` chunks, validate bounds, declared length and `blockAlign`, and select `B` bytes rounded
upward to a complete frame or the entire shorter complete payload:

```text
high = XXH3_64(
    ASCII "MDIA-ID-V3.2-HIGH-ID"
    || 0x01
    || channel_descriptor
    || selected_data_length:u64be
    || exact selected data bytes
)
```

Compressed WAVE uses ACCESS_UNITS only when the adapter exposes a conforming AU sequence.

## 4. Tail discriminator for ACCESS_UNITS

Tail formation runs only for a repeated high. Normalize PTS relative to the first AU and let D be
the selected stream's exclusive end in that timeline. Calculate the requested start with checked
integer arithmetic:

```text
S = floor(3 * D / 4)
```

Request previous-sync positioning at the corresponding source timestamp and discard preroll with
normalized PTS below S. Store normalized S, not the extractor landing point. The first AU with PTS
at least S starts the component. If that start precedes the head's exclusive end, select FULL.

Use a new unique-payload set and component hash. Every accepted AU occurrence enters the component
digest, including repeats. Stop after the crossing whole AU when `unique_bytes >= B`. If EOF occurs
before B unique payload bytes, select FULL. Missing duration, unusable timing or seek also selects
FULL.

Component framing is `local_index:u32be || payload_length:u32be || payload` under ASCII
`MDIA-ID-V3.2-HEAD`, `MDIA-ID-V3.2-TAIL` or `MDIA-ID-V3.2-FULL`. Final tail framing is:

```text
tail = XXH3_64(
    ASCII "MDIA-ID-V3.2-TAIL-ID"
    || 0x00
    || channel_descriptor
    || strategy:u8
    || strategy fields
)
```

`strategy` is `0x00` HEAD_TAIL with
`head_count:u64be || head_hash:u64be || requested_S:u64be || tail_count:u64be || tail_hash:u64be`,
or `0x01` FULL with `full_count:u64be || full_hash:u64be`. Counts describe actual hashed components.
HEAD_TAIL does not require a complete-stream count.

Before an additional complete scan, Indexer sends Player the bounded display filename and one of
`timing`, `seek`, `overlap` or `tail`. Player exposes `Full Media.ID scan (<reason>): <filename>` as
bounded detail after the
[shared cumulative counts and period speed](/Riff/LATEST/indexer-staging#progress-and-period-rates)
through the shared status line when enabled. A head read that already reached EOF needs no
extra-scan message.

## 5. Tail discriminator for WAVE_RAW

The WAVE tail discriminator hashes the complete declared, block-aligned audio payload:

```text
tail = XXH3_64(
    ASCII "MDIA-ID-V3.2-TAIL-ID"
    || 0x01
    || channel_descriptor
    || declared_data_length:u64be
    || exact complete data bytes
)
```

Container metadata, padding and decoded PCM transformations are excluded.

## Fragment component framing

The Android scheme is `riff.android.media-id-v3.2.fragment-span-v1`. A FILF high hashes a byte 0x46,
its base high as signed 64-bit big-endian, then begin and end, each encoded as one presence byte and
one signed 64-bit big-endian value (zero when absent). A FILF low uses byte 0x4c and the base tail
component with identical span framing. XXH3-64 supplies both digests. Span bounds are identity
inputs; metadata and physical source paths are excluded.
