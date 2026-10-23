# ReplayGain playback

> Owner/scope: Player gain selection, runtime target, audio-processing topology and clipping
> protection

## Modes, target and active-queue gain selection

ReplayGain is installation-wide and defaults to mode F with target 23. N constructs the stock Media3
renderer/sink with no Riff AudioProcessor. I and F force decoded PCM through the shared ReplayGain
processor. Target 0/16/18/23 has the exact meaning defined by
[ReplayGain settings and units](replaygain-settings.md).

The active playback queue selects one canonical MDRG policy whenever the queue or MDRG table
changes. If and only if every queue occurrence has an Album Gain and every canonical Q7.8 Album Gain
value is exactly equal, that value is selected for every occurrence. Otherwise each occurrence
selects its Media.ID Track Gain. Album.ID is not consulted; duplicate Media.ID occurrences remain
independent queue occurrences. Peaks do not participate. Missing required gain remains explicitly
unavailable until the sound path applies its neutral 0 dB fallback; target offset is applied only to
an available selected gain.

For an available canonical gain, target 16 adds 2 dB, target 18 adds 0 dB and target 23 subtracts 5
dB. Target 0 selects 0 dB independently of MDRG. Checked Int arithmetic produces the effective Q7.8
value before sample conversion.

At each renderer stream boundary, Player resolves the exact queue occurrence and registers its
canonical gain availability by stream offset. A track boundary drains the old delayed tail, resets
limiter/transition state and applies the next stream's effective gain from its first sample without
a ramp. An MDRG replacement affects newly registered streams and does not rewrite uninterrupted PCM.

## Live target transition

A committed target-only change while mode stays I or F uses a target revision independent of the
renderer-stream revision. It neither rebuilds Media3 nor drains the ring buffer, resets detector or
limiter state, pauses playback, or changes output encoding. The processor schedules the new
base-gain envelope at the first PCM frame beyond audio already admitted under the old target, so an
upward change cannot bypass the existing look-ahead analysis. The envelope moves linearly in Q7.8 dB
from the then-current planned value to the new effective value over exactly the same 75 ms interval
as the limiter. Downward and upward changes both ramp. A later target change begins from the planned
envelope at its own safe start; transition storage is bounded.

A mode change rebuilds the path and applies the final committed target after the existing audible
handoff pause, without an additional ramp. A simultaneous mode and target commit follows this mode
change rule.

## Playback diagnostic projection

The PLAYBACK status line continues to display canonical MDRG for the current occurrence
independently of N/I/F and runtime target. Missing selected gain displays as unavailable; stored
zero displays as `0.0`. This passive projection neither applies gain nor rebuilds the sound path.

## Processor and limiter

One PCM16-input implementation owns I and F. I performs fixed-point multiplication and returns
PCM16. F converts PCM16 to normalized float, applies gain and returns PCM_FLOAT. Both modes always
run the same 75 ms look-ahead limiter with a -0.1 dBFS ceiling, including target 0, negative and
exactly-neutral effective gain. The target envelope is the base gain; limiter events constrain it
through the lowest safe envelope. Required attenuation and recovery move linearly in dB over at most
75 ms. End of stream drains all delayed frames. Flush, seek, cancellation, format reconfiguration
and release discard stale state.

Changing N/I/F rebuilds Media3 and restores the same occurrence, position, shuffle, repeat and
requested play state without editing the durable queue. Format changes drain and reconfigure the
ordinary path.

This is an automotive normalization path, not an audiophile/high-resolution path. The processor
receives PCM16; F avoids a second PCM16 quantization but cannot restore earlier precision. The
limiter is intentionally simple, and more advanced mastering algorithms are outside scope.

## Verification

Verify canonical selection, all four targets, target 0 with active limiter, missing-gain neutrality,
checked offsets, target-only 75 ms ramps in both directions, repeated changes, a change during
active limiting, safe look-ahead scheduling, stream boundaries during a transition,
positive/zero/negative gain, both encodings, limiter timing/EOS, duplicate occurrences,
seek/skip/repeat, MDRG replacement, and mode rebuild restoration. N must contain no processor. UI
tests cover default F/23, exact cycles, N-disabled retained target, More Save/Cancel, accessibility,
resize, font scale, RTL and rotary. HU verification covers target-only continuity, synchronization,
underruns, focus and format transitions.
