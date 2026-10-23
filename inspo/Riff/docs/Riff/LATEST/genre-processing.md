# Riff list-tag and Genre processing contract

> Status: adopted normative scoped contract. Authority:
> [Genre metadata](/Riff/LATEST/indexer-metadata). Exact storage, versions and TTL:
> [Cuetree attributes](/Cuetree/attributes).

## GP-1. Shared repaired list input

One shared list-component parser owns explicit delimiter recognition for Genre, Artist and Album
Artist; copied delimiter lists or field-specific splitting implementations are prohibited. The
conservative de-Mojibake decision runs once over the complete track evidence before any of these
fields is split. Components are trimmed, empty components are omitted, and exact repaired/display
duplicates are removed while preserving the stable order of first occurrence. The parser retains
that order while producing components; publication keeps the components as a set of identities.

Artist and Album Artist split only on the parser's explicit semicolon, vertical bar, CR and LF
delimiters. Comma, slash and ampersand remain literal in Artist components because there is no
authoritative Artist vocabulary for Genre-style weak splitting. Every component becomes an
independent ordinary canonical Artist or Album Artist attribute. Presentation joins Track Artist
components with exactly comma followed by one space in canonical MDIA Attribute-ID order. That
deterministic order remains stable across repeated indexing, publication and restart for unchanged
component identities; canonical identity does not replace display spelling.

## GP-2. Genre input and repair order

Use embedded Genre values for every admitted audio file, independently of audiobook status. Missing
or blank values produce no components. Filename/path inference and a new free-form canonical genre
dictionary are prohibited.

Run the existing conservative de-Mojibake decision once over the complete track evidence set,
including the entire Genre string. Do not split Genre before this decision, repair individual
components independently, or repair the same field twice. The existing evidence includes Title (with
its existing fallback), Album, Artist, AlbumArtist, Genre and chapter display text in the order
defined by [TOC extraction](/Riff/LATEST/indexer-metadata). Structured chapter IDs, URLs and other
non-display data remain outside that repair operation.

The existing repair procedure requires reversible encoding candidates and reliable bounded charset
evidence, and accepts only an unambiguous non-dominated result. Replacement characters, ambiguous
candidates or insufficient evidence retain the original value. GenreText uses the resulting display
string. Canonical normalization is a separate comparison copy for GNRE; it must never overwrite the
stored display spelling.

If the format extractor supplies several independent Genre values, process each separately and
deduplicate the final components in first-occurrence order. Do not join separate values to infer
delimiters. [Cuegrain integration](/Riff/LATEST/cuegrain-integration) owns extraction and the
restricted platform fallback; preserve repeated Cuegrain observations independently through repair
and list parsing.

## GP-3. Genre delimiter precedence and compounds

For each repaired input value:

1. Trim outer Unicode whitespace. If it contains semicolon, comma, CR or LF, split exclusively on
   those explicit delimiters. Trim each result and omit empty results. Slash and ampersand inside
   each result remain literal, even when that result is unknown to GNRE. Explicit delimiter
   precedence applies to this complete input value.
2. Otherwise query GNRE for the whole value. A recognized value remains one component.
3. Otherwise treat slash and ampersand as possible separators. Starting at the left, retain the
   longest contiguous GNRE-recognized span ending at a possible separator or the end of the value.
   If no span is recognized, emit the next trimmed atom. Continue left to right. This protects
   embedded R&B inside R&B/Soul while splitting unknown compounds.
4. Remove empty results and deduplicate by exact repaired/trimmed text across the input values. Do
   not split at spaces or hyphens.

GNRE comparison uses the existing canonical tag normalization and lookup. It ignores outer
whitespace and whitespace immediately around slash/ampersand, but does not remove whitespace between
words. Retained GNRE localized labels are compared with the same rule; existing hashed aliases
remain usable. This comparison affects recognition only. Preserve case, punctuation and internal
spaces from each selected source span in GenreText.

Weak-delimiter search is bounded: more than 64 weak delimiters in an input retains that entire
trimmed input as one component. Explicit-delimiter splitting remains linear and is unaffected by
this bound. No source text is truncated.

Examples (recognition depends on the installed GNRE corpus):

| Input             | Components                                               |
| ----------------- | -------------------------------------------------------- |
| R&B; Soul         | R&B / Soul (two components)                              |
| R&B/Soul          | R&B / Soul (when R&B is recognized)                      |
| R & B             | R & B (when R&B is recognized; original spaces retained) |
| Pop/Rock          | Pop/Rock (if recognized whole), otherwise Pop / Rock     |
| Ужасы/Мистика     | Ужасы / Мистика (unless the whole label is recognized)   |
| Фантастика; Ужасы | Фантастика / Ужасы                                       |
| R&B/Soul; Ужасы   | R&B/Soul / Ужасы (explicit delimiters win)               |
| Rock, Pop, 60s    | Rock / Pop / 60s                                         |
| Любовный роман    | Любовный роман                                           |

## GP-4. Genre projection and identity

One shared parser supplies both projections. Every resulting component becomes GenreText, including
unknown literary genres and known musical genres. GNRE matches for those same components
additionally produce existing numeric Genre references, deduplicated by GNRE Genre.id. Unknown
components produce no canonical Genre attribute.

GenreText identity is exact repaired/trimmed UTF-8 text using the kind-specific framing in
[Cuetree attributes](/Cuetree/attributes). There is no NFKC, case folding, confusable replacement,
translation, alias merge or internal whitespace removal in the stored value or its identity. Thus
Ужасы and ужасы remain distinct source genres; equal components share one attribute across bindings.

Ordinary metadata replacement atomically replaces a binding's genre references. Failed/unavailable
scans retain their ordinary consistent metadata behavior. GenreText uses the existing accessibility
and publication-generation TTL machinery, with no independent permanent dictionary. Source-genre
availability uses active relevant bindings, not retained orphan/NoAccess rows.
[Catalog membership](/Riff/LATEST/player-catalog-membership) owns AUDIOBOOK consumption. GP-2..5 own
preparation, comparison and identity; content classification has its registered owner.

## GP-5. Revision and conformance

The list parser revision, Riff metadata-processing revision and validated GNRE generation form one
metadata compatibility fingerprint, independent of publication generation. A changed fingerprint,
MediaID scheme or wire format requires complete index recreation. Reject incompatible old cohorts.
No old identity reuse, per-source upgrade, pending-completion flag or partial metadata/catalog
migration is permitted during an update. A fresh index obtains identity and metadata normally.
Ordinary changed-file acquisition within a compatible index remains separate from update policy.

Verify repair ordering, delimiter precedence, GNRE matches, preserved spelling/deduplication,
bounded pathological input, both projections, liveness/TTL, exact-format and revision rejection,
passive Player acceptance, desktop export and full recreation. Publication timing remains governed
by [immutable publication](/Riff/LATEST/immutable-publication). Player does not rerun
repair/splitting.
