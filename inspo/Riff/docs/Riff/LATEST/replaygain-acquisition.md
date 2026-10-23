# ReplayGain acquisition

> Owner/scope: Indexer ReplayGain eligibility, identity, external sources and selection

## RG-1. Scope and pipeline

L1 collects ReplayGain-related metadata while it already opens the selected source. L2 resolves the
collected candidates and performs decoded-audio measurement only when no candidate supplies usable
Track Gain. L2 remains the single decoded-audio analysis stage shared with AI; ReplayGain and AI
keep independent outcome states in the same MDRG record. Audiobooks are eligible for loudness
analysis even though they are excluded from AI.

Exactly one MDRG record exists per signed Cuetree MediaID, including each FILF. Binding selection
stays deterministic. Conflicting physical copies are neither averaged nor given per-binding records.
A FILF never inherits its container FILM TrackGain; its own span is analyzed. External
AlbumGain/AlbumPeak may be associated with a FILF from its container.

## RG-3. Source candidates and priority

Build independent candidates in this order:

1. ordinary ReplayGain metadata;
2. LAME/Xing or equivalent decoder side data;
3. Opus/R128 metadata;
4. iTunes Sound Check;
5. Riff measurement, only when no external candidate has usable Track Gain.

Never fill one candidate from another source. Ordinary ReplayGain and LAME candidates are complete
only when Track Gain, Track Peak, Album Gain and Album Peak are all valid. An R128 candidate is
complete when Track Gain and Album Gain are valid because that representation has no peak fields.
Sound Check is Track Gain only. Select the highest-priority complete external candidate if one
exists; otherwise select the highest-priority external candidate with usable Track Gain. Therefore a
lower-priority complete candidate beats a higher-priority incomplete candidate. Album-only or
peak-only data is not usable and does not suppress measurement.

Store only fields from the selected candidate. A supplied Album Gain is never synthesized or
recomputed by L2. Peaks are stored as finite non-negative linear amplitudes but are not used by the
current AudioProcessor. Range fields and reference loudness are not stored.

### Ordinary ReplayGain

Recognize `REPLAYGAIN_TRACK_GAIN`, `REPLAYGAIN_TRACK_PEAK`, `REPLAYGAIN_ALBUM_GAIN`,
`REPLAYGAIN_ALBUM_PEAK` and `REPLAYGAIN_REFERENCE_LOUDNESS` through the existing exact
container-specific metadata rules. Gains are decimal dB. Peaks are linear amplitudes and may exceed
1.0. The source reference is its unique valid reference value in LUFS, or -18 LUFS when absent. The
-18 default is a ReplayGain 2 interoperability convention; it does not claim that legacy ReplayGain
values are themselves BS.1770 loudness measurements.

### LAME and side data

Use Cuegrain's LAME observations from the shared L1 metadata read; no separate LAME pass or local
LAME parser is allowed. Radio is Track Gain and Audiophile is Album Gain. Only the ReplayGain
originator is eligible. Cuegrain owns the unsigned Q9.23 representation and returns linear peaks;
Riff MUST NOT repeat format conversion. Encoded zero, a missing valid ReplayGain gain field, or a
value outside [0,512) leaves peak unavailable; do not clamp, guess another representation or reread
the file. The one file peak supplies Track/Album Peak only beside the corresponding present gain.
The reference is -18 LUFS as a compatibility convention, not as a claim that the LAME field contains
an absolute LUFS measurement. A valid candidate still obeys the whole-candidate completeness rule.

### R128

`R128_TRACK_GAIN` and `R128_ALBUM_GAIN` are signed Q7.8 dB values. Apply mandatory codec header-gain
semantics exactly once as required by the pinned decoder path. Only an explicit valid
`R128_REFERENCE_LOUDNESS` belongs to this candidate; an ordinary `REPLAYGAIN_REFERENCE_LOUDNESS` is
never borrowed. Otherwise the reference is -23 LUFS. R128 defines no stored peaks.

### iTunes Sound Check

MP3 `iTunNORM` and MP4 `com.apple.iTunes:iTunNORM` must contain exactly ten whitespace-separated
ASCII hexadecimal words of exactly eight digits each. The first left/right pair produces the derived
Track Gain. Riff treats that value as referenced to -16 LUFS. This is an intentional, empirically
selected interoperability assumption for the observed library corpus: it is not an RFC, a published
format guarantee or an official Apple position. Sound Check contributes no Album Gain or peaks.

## Canonical normalization

MDRG always stores gain relative to Riff's fixed -18 LUFS reference:

```text
reference_delta_lu = -18_lufs - source_reference_lufs
gain18_db = source_gain_db + reference_delta_lu
gain18_q8 = round_half_away_from_zero(gain18_db * 256)
```

LUFS denotes absolute programme loudness, LU denotes the relative reference difference and dB
denotes the gain applied to PCM; a 1 LU reference difference maps numerically to a 1 dB gain
adjustment. Thus default R128 gains receive +5 dB and Sound Check gains receive -2 dB before
storage. Ordinary ReplayGain/LAME at -18 and Riff measurement require no reference offset.
Conversion rejects non-finite or out-of-range values; zero is a valid gain, never an absence
sentinel.
