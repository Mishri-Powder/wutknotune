# Riff — open work

This supporting page records unverified requirements and implementation gaps; the
[registry](/Riff/LATEST/requirements) owns scope.

- Verify complete on-device AU+IA and the final full-catalog census after the Singles clean cut
  delivered in RC 0.2.0 in [the current handoff](/Riff/LATEST/worklog). Unit/device boundaries and
  model reference parity pass; they do not establish whole-library completion.
- Make the eleven existing phone fixture/rendering/brightness tests portable. The complete phone
  suite has the same failing methods before and after the dependency refresh; the HU suite passes.
  Exact baseline comparison is in the refresh evidence.
- Verify bounded large-library memory and the cold-start playback deadline under
  [engineering verification](/Riff/LATEST/engineering-verification#performance-and-verification).
- Verify live AAOS and projected-host restrictions under
  [AAOS conformance](/Riff/LATEST/aaos-conformance). The configurable distribution now defaults to
  phone; these local installations need no motion adapter. Explicit automotive still requires valid
  UNRESTRICTED evidence for playlist editing under the
  [mode owner](/Riff/LATEST/application-modes-modes).
- Measure SAF discovery query counts and end-to-end metadata and AI/ReplayGain throughput on HU with
  the directory-observation change. Compare against the prior Alpha on the same media mix; host
  tests establish behavior, not device throughput.
- Verify full-library FILF playback, bookmark continuation and independent span analysis on
  representative real media.
- Verify audible time stretch, pitch and silence skipping on representative audiobooks under
  [audiobook speed](/Riff/LATEST/player-audiobook-speed). HU and phone now verify actual muted
  AudioTrack durations at 0.7x/1.5x and silence removal on synthetic WAV fixtures across N/I/F, plus
  the service's mode/profile projection regression. Physical output capture and representative
  audiobook listening remain unverified.

[Filename ordinal evidence](/Cuetree/filename-evidence) now has its adopted scoped implementation.
Broader MAY-INDEXER-01 contextual missing-tag inference remains subject to a separately adopted
specification in
[engineering verification](/Riff/LATEST/engineering-verification#deferred-metadata-inference).
