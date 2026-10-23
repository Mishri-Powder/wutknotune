# Indexer metadata and grouping

> Owner/scope: Indexer metadata parsing, grouping and chapter extraction

## Indexed genres, Albums and chapter data

Every admitted audio binding may reference repaired source GenreText regardless of content category.
One parser follows the registered genre-processing contract: perform the conservative track-wide
de-Mojibake decision before splitting the complete Genre value; the resulting components feed both
exact GenreText and GNRE-backed numeric Genre. GenreText has no separate permanent dictionary and
uses normal metadata replacement, active-reference liveness and publication-generation TTL. A
derived-data rebuild applies parser or GNRE changes to retained metadata. The same shared list
parser processes repaired Artist and Album Artist values. Semicolon, vertical bar, CR and LF are
Artist/Album Artist delimiters; empty components are omitted and exact components are deduplicated
while retaining first-occurrence order. Artist values do not use Genre's comma or GNRE-assisted weak
slash/ampersand delimiters, so comma, slash and ampersand remain literal. Each component is stored
as an independent ordinary canonical attribute; no second repair or copied delimiter implementation
is permitted.

Cuetree owns catalog construction through `AlbumGroupingPolicy` and `CatalogBuilder`.
[Cuetree catalogs](/Cuetree/catalogs#albm) owns retained Album identity, explicit table ownership,
ordinary base-file grouping, FILF expansion and Singles. Riff supplies GNRE matching and ready
Player projections. Album metadata and ordered positions remain intact while sources are temporarily
unavailable.

### Residual Album score

[RIFF-ALBUM-V2](/Riff/LATEST/album-grouping) owns global grouping, positive evidence rules,
disc-family and corroborated title-suffix normalization, copy/position resolution, release guards
and ambiguous-singleton admission. Its [residual score](/Riff/LATEST/album-grouping-residual-score)
is the single weight-table owner. Missing evidence is neutral; generic Year is not edition identity.
This page does not duplicate weights or add a folder prerequisite.

### Structural observations

[Cuegrain](/Cuegrain/contract) parses CUE/TOC/CHAP sources independently. Cuetree validates
multi-section observations and retains them in MDIA alongside hidden CUE metadata records. Bounds
use nullable microseconds; explicit zero is present data. [Cuetree indexing](/Cuetree/API/Indexing)
owns the table and section API; [catalogs](/Cuetree/catalogs#albm) owns Album formation and chapter
expansion.

For each field, CUE section metadata overrides CUE album metadata, which overrides file metadata.
Unspecified CUE fields use the file value. Separate-file CUE albums keep their declared membership
and order. The physical file's own attributes remain factual source metadata.

One conservative track-wide repair decision covers ordinary metadata and structural display text.
IDs, URLs, timestamps and syntax are excluded. Preserve originals when repair is not accepted.
Malformed optional structures retain diagnostics and valid ordinary tags. Cancellation and source
I/O failures propagate to the scan's availability handling. Player consumes validated FILF and
catalogs.

[Singles classification](/Cuetree/singles) runs after complete ordinary grouping;
[filename ordinal evidence](/Cuetree/filename-evidence) may supply transient missing ordinals first.
This adds no source-tag repair or new score weight.
