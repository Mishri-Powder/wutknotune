# ReplayGain settings and units

> Owner/scope: ReplayGain setting scope, sound-path boundary and canonical units

## RG-2. Settings and sound-path boundary

The More page contains one `ReplayGain` row with two cyclic text-value controls. The mode control
cycles `N -> I -> F -> N`; N is unselected, while I/F use ordinary selected emphasis. The target
control cycles `0 -> 16 -> 18 -> 23 -> 0`, displays no minus sign, and exposes the corresponding
`minus 16`, `minus 18`, or `minus 23 LUFS` meaning to accessibility. It is truly disabled and
non-actionable while mode is N, but retains its value. Defaults are mode F and target 23.

Both values are installation-wide and belong only to the independent More draft. Successful More OK
atomically persists both. A mode change rebuilds and restores the Player audio path using the final
committed target. A target-only change while mode remains I or F updates the existing processor
without a Player rebuild, pause, stream drain or limiter reset. Primary Settings Save/Cancel and
switching a User cannot change either value.

L2 runs independently of mode and target. N contains no Riff AudioProcessor. I selects integer-PCM
processing and F selects float-PCM output processing under
[ReplayGain playback](replaygain-playback.md).

## RG-4. Units and canonical reference

Gain applied to PCM is additive dB. Absolute programme/reference loudness is expressed in LUFS; the
difference between two loudness values is LU, with 1 LU mapping numerically to 1 dB of gain. One
Q7.8 gain step is 1/256 dB. MDRG remains canonical relative to exactly -18 LUFS:

```text
gain_db = gain_q8 / 256
gain_q8 = round_half_away_from_zero(gain_db * 256)
```

Source-specific reference conversion happens once in Indexer before storage. Reference loudness and
Player target are not persisted in MDRG. Player derives effective gain without changing MDRG:

```text
target 0:  effective_gain_q8 = 0
target 16: effective_gain_q8 = canonical_gain_q8 + 512
target 18: effective_gain_q8 = canonical_gain_q8
target 23: effective_gain_q8 = canonical_gain_q8 - 1280
```

Target 0 is unity ReplayGain gain, not 0 LUFS and not a bypass. I/F retain the complete processor,
stream-boundary and limiter path; the limiter may still attenuate a peak. A missing selected MDRG
gain remains the safe 0 dB fallback for every target and must not receive a target offset. Stored
numeric zero remains an available canonical value and is distinct from missing data. Use checked
wide intermediates and reject out-of-range results.

Mode or target changes cause no source I/O, MDRG rewrite or Indexer analysis. Peaks remain optional
Float32 linear amplitudes and do not select a target. Codec/header semantics such as Opus output
gain remain mandatory independently of ReplayGain mode and are accounted exactly once.
