# Cuetree typed attributes

## Values and identity

MDIA owns one retained sorted attribute dictionary. Records in MDIA and Player catalogs reference
attribute IDs. Every attribute contains a common ID `(hi, null)`, IDHI plus ACSS, generation, kind
and exactly one of text/long. Null is not zero. Text bytes are UTF-8; long bytes are signed 64-bit
big-endian. IDs use unseeded XXH3-64 over kind as one byte, byte length as unsigned 32-bit
big-endian, then value bytes. A same-ID/different-value collision fails publication; it must not
silently combine metadata. Text normalization and grouping comparison are distinct from exact stored
text.

Kinds follow the adopted diagram: Unknown=0 (not admitted), MimeType=1, Title=2, Track=3,
TotalTracks=4, Album=5, Artist=6, AlbumArtist=7, Genre=8, Year=9, Disc=10, TOC=11, GenreText=12,
TotalDiscs=13, AlbumGroup=14, AlbumTotalTracks=15, MediaKind=16, Fragment=17, TotalFragments=18 and
ReplayGain=32. Long-valued kinds are Track, TotalTracks, Genre, Year, Disc, TotalDiscs, AlbumGroup,
AlbumTotalTracks, MediaKind, Fragment and TotalFragments. Others store text. Unknown values are
absent, never fabricated zero.

Cuegrain remains the only parser. Cuetree projects its normalized fields into typed attributes,
separating number/total representations and using the injected GenreResolver. Text genres remain
GenreText; known genres can also reference the external dictionary with numeric Genre. Fragment and
TotalFragments come from validated CUE/TOC order. Source observations not represented by these kinds
remain in the consumer metadata view using namespaced extension attributes; no source parser is
copied. Extension metadata is framed as text with kind 127 plus `<key length>:<key><value>` (key
length in UTF-16 code units) and is preserved by round trips.

## Liveness and ownership

An attribute's generation is refreshed by retained MDIA references regardless of source
availability. Projection-owned presentation attributes are also retained while the corresponding
projection has factual MDIA membership. ACSS reflects whether a current available referring source
exists. Once unreferenced, preserve generation and retain until the common TTL; clear references
before removing a dictionary record. Values referenced by retained orphan presentation cannot be
removed before their owner, but those owners do not refresh generation.

Attributes are interned by exact typed value. Equal audio identities never overwrite source-specific
metadata. A decoded JVM snapshot may expose convenient metadata maps; the wire representation uses
dictionary references and one common ID codec.
