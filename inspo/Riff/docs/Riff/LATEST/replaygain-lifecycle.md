# ReplayGain lifecycle and verification

> Owner/scope: ReplayGain rebuild, verification and delivery boundary

## RG-7. Rebuild and coordination

Two independent rebuild scopes exist: audio analysis (`AU`) and media index (`IA`). There is no
separate ReplayGain reset because gain and AI are outputs of one MDRG analysis pipeline.

AU clears MDRG content, physical FAILEDL2 suppression and L2 attempt-recovery entries, preserving
its generation watermark. The next Cuetree pass reuses unchanged source evidence and supplies
admitted handles to the shared analysis workset. IA clears Cuetree library/catalog content while
preserving MDRG for reference reconciliation. AU+IA clears both selected scopes. Preserve source
files, GNRE, Player state and independent generation watermarks.

Settings owns draft/submit-once/mask-union and cancellation/publication fences. Toggle labels and
mask values are AU=1, IA=2, AU+IA=3. Audio mode or target changes affect only the Player audio path.

The existing Media Folders barrier remains unchanged: it waits for the whole active pipeline before
grant mutation. L2 stays cancellable only through the existing pipeline cancellation fence; grants
never mutate under workers. Storage relocation keeps its separate acknowledged cancellation fence.

## RG-8. Required verification

- Settings primary/More order, independent drafts, installation scope, default F/23, exact mode and
  0/16/18/23 cycles/persistence, retained disabled target under N, I/F selected, full accessibility
  labels, resize/font scale/RTL/rotary and application-mode matrix.
- L1-only shared Cuegrain acquisition without a second LAME pass; zero-I/O unchanged fast path,
  explicit rebuild and interrupted-work recovery; every accepted gain representation/priority;
  strict ten-word iTunNORM shape, case, malformed/duplicate/partial values and negative/zero gains.
  Verify that iTunNORM's -16 LUFS reference is the documented empirical assumption and that R128
  never borrows an ordinary ReplayGain reference. Explicitly verify that peaks are retained only
  from the selected source and cannot affect current playback or measurement scheduling.
- Canonical -18 LUFS Q7.8/reference conversion/range/rounding, checked runtime target offsets,
  missing-gain neutrality and target 0 unity before limiting; nonzero Opus header gain exactly once
  in the actual signal basis; mode/target changes cause no source I/O or MDRG rewrite.
- One Media.ID across conflicting copies, source provenance/invalidation, unchanged fast path,
  whole-source candidate selection, no synthesized Album Gain; separately analyzed FILF chapter
  records.
- Three 20-second integrated-loudness windows, the adopted 25/50/75% starts, libebur128 multi-state
  combination, two forward 20-second retries, no spread trigger, music-only full fallback, audiobook
  no-full behavior, channels, short/silent/truncated audio, cancellation/volume loss/resource
  release; never publish partial measurement as complete; no peak calculation; supplied peaks remain
  unchanged.
- No Track Gain, deterministic failures versus cancellation/retryable unavailability; books eligible
  for ReplayGain L2 but not the AI part of L2, independent outcomes, one shared analysis stage with
  independent outcomes, sparse generations/checksums/descriptors/references, checkpoint/restart,
  AU-clears-MDRG behavior, AU/IA combined rebuilds and deterministic desktop exports.
- Playback application per the registered contract: N has no Riff processor; I/F topology and
  encoding, exact stream-boundary gain, target-only 75 ms bidirectional ramp without stream/limiter
  reset, target 0 through the active limiter, 75 ms limiter latency/envelope, mode rebuild
  restoration, format reconfiguration, missing-gain fallback, focus and underrun behavior. Missing
  gain never gates playback or AI heatmap readiness.

## RG-9. Delivery boundary

Indexer acquisition/storage and Player playback are both authorized. Playback must follow
[ReplayGain playback](/Riff/LATEST/replaygain-playback); it does not change Indexer analysis, MDRG
wire data, volume/focus policy or decoder admission.

## References

- [RFC 7845](https://www.rfc-editor.org/rfc/rfc7845.html#section-5.2) and
  [VorbisComment](https://www.xiph.org/vorbis/doc/v-comment.html).
- [ReplayGain 2.0](https://wiki.hydrogenaudio.org/index.php?title=Revised_ReplayGain_specification).
- [LAME writer](https://github.com/lameproject/lame/blob/master/libmp3lame/VbrTag.c) and
  [Sound Check parser](https://github.com/beetbox/mediafile/blob/master/mediafile/utils/soundcheck.py).
- [libebur128](https://github.com/jiixyj/libebur128) and
  [Media3 float-output boundary](<https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder#setEnableFloatOutput(boolean)>).

External representations inform this contract; they are not a fixed playback-format support list.
