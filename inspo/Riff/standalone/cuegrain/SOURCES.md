# Sources and provenance

## Format references

- [Hydrogenaudio Cuesheet](https://wiki.hydrogenaudio.org/index.php?title=Cuesheet):
  FILE/TRACK/INDEX commands, EAC layouts and common extensions.
- [Wikipedia: cue sheet](<https://en.wikipedia.org/wiki/Cue_sheet_(computing)>): terminology and
  supplementary examples.
- [ID3v2.4 structure](https://id3.org/id3v2.4.0-structure),
  [frames](https://id3.org/id3v2.4.0-frames), and
  [ID3 chapters](https://id3.org/id3v2-chapters-1.0).
- [FLAC RFC 9639](https://www.rfc-editor.org/rfc/rfc9639.html): metadata blocks, CUESHEET and
  picture structure.
- [Ogg Opus RFC 7845](https://www.rfc-editor.org/rfc/rfc7845): Opus headers, comments and R128 gain.
- [Xiph chapter extension](https://wiki.xiph.org/Chapter_Extension): CHAPTERxxx fields and
  timestamps.
- [LAME VbrTag implementation](https://github.com/lameproject/lame/blob/master/libmp3lame/VbrTag.c):
  Xing/LAME gain field layout.

## Code provenance

The executable JAR contains Kotlin stdlib 2.4.21 and JetBrains annotations 26.1.0. Their attribution
is recorded in THIRD_PARTY_NOTICES.txt. The applicable Kotlin notices are preserved from the pinned
[v2.4.21 license directory](https://github.com/JetBrains/kotlin/tree/v2.4.21/license) under
licenses/kotlin and embedded in the executable JAR. Optional CED builds also include the original
CED license inside the JAR.

QuickTime/Nero chapter traversal and conservative mojibake repair are adapted from the owner's Riff
implementation with explicit permission to include the adaptation in this Apache-2.0 standalone
library. Riff's build, classes and Android/Media3 dependencies are not imported. The ID3 decoder is
independent Kotlin code; it does not redistribute Media3.

Google Compact Encoding Detection source is vendored under its Apache-2.0 license at revision
`d127078cedef9c6642cbe592dacdd2292b50bb19`. See `ced/vendor/REVISION` and the vendor license. The
JNI adapter belongs to Cuegrain. The default build does not compile or package CED.

Five small synthetic chapter fixtures were supplied with Riff, with provenance recorded in
`core/src/test/resources/chapters/README.md`. They are independent of private music corpora.

## Additional internet CUE fixtures

`tools/check_corpus.py` downloads the following files from
[libcue](https://github.com/lipnitsk/libcue) at pinned commit
`f35fd675703d8984c2ea26cca7cb403493368fd5`:

- `t/99_tracks.cue`
- `t/issue10.cue`
- `oss-fuzz/corpus/clusterfuzz-testcase-minimized-fuzz-6416083458064384.cue`
- `oss-fuzz/corpus/leak.cue`

They exercise a 99-track file, INDEX-00-only tracks and malformed fuzz cases. They are downloaded
only into ignored `evidence/`, retaining upstream licensing; they are not included in the Apache-2.0
source artifact. Downloaded URL/SHA-256 provenance is recorded locally with test results.

The private 172-CUE archive and the authorized Music corpus are read-only test inputs, not
redistributed fixtures. Published verification uses aggregate counts, not private track inventories
or media bytes.
