# Cuetree filename ordinal evidence

> Owner/scope: conservative derived ordinals before ordinary grouping and ordering.

## Complete-listing evidence

After a complete successful directory enumeration, retain the numeric extension attribute
`cuetree.filenameSequenceSize` on that DIRECTORY only when its immediate audio file names form a
unique complete sequence 1..N with N at least two. Recognize a positive leading ASCII integer
followed by space/tab, dot, underscore, parentheses or dash; prefer the special iTunes prefix
`0 <integer> <separator>` over interpreting its first zero. Zero, repeated/gapped ordinals and
following letters do not qualify. Leading zeroes do not change the integer.

Count all recognized direct audio names, including unsupported WMA/failed candidates, without
admitting unsupported audio into playback. Recognized artwork/text/playlists are neutral. Nested
directories, CUE/TOC, unknown opaque children, failed child observations or incomplete enumeration
cannot supply fresh completeness evidence. No extra tree walk, source open or metadata parser is
introduced. This is observed local listing size, never authored Total Tracks.

## Use and retained membership

Derive a track ordinal only when the retained immediate audio members still form a unique 1..N
sequence consistent with their successful listing evidence and every existing usable Track agrees.
Conflicting declared totals, mixed substantive Album tags or chapter structures disqualify this
interpretation. Retained unavailable children count; a temporary smaller accessible subset must not
invent a smaller complete album. New unacquired children cannot prematurely enable inference.

A missing/invalid Track may use this ordinal transiently in `AlbumEvidence`, copy/position
resolution and ordering. Valid source tags win and remain stored exactly. Do not write derived Track
or Total Tracks into MDIA, do not supply a missing Album/Artist from folders, and do not create a
folder grouping boundary. Existing structural, artist and AG-9 rules remain available when no
filename proof exists. Unknown is neutral. Artist/title comparison and score weights are unchanged.

Verify mixed tagged/untagged ordering, iTunes names, all-format counts, conflicts, opaque siblings,
CUE/FILF, incomplete acquisition, temporary absence, source TTL and unchanged factual tags.
