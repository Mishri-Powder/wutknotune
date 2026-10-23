# Catalog language evidence

> Owner/scope: build-time language and semantic inputs for album/Singles classification.

`config/project.json` is the sole Riff authority. `genreLocales` selects the languages.
`catalogVocabulary.locales` must have exactly those keys; each locale supplies the same required
lexical categories: `genericArtists`, `placeholderArtists`, `placeholderAlbumArtists`,
`placeholderAlbums`, `genericTitles`, `versionWords`, `protectedWords`, `discWords` and
`setMarkers`. Arrays contain nonempty literal strings, not executable regular expressions. Empty
arrays are allowed when that category has no adopted evidence in a language. Reject missing/extra
locales, unknown/missing categories, invalid types and duplicate entries at build time.

`catalogVocabulary.genreRoles` assigns exact GNRE IDs to `spoken`, `live` and `electronic`. The
generator verifies those identities exist in the packaged corpus. GNRE remains the only localized
genre-name/alias source; do not copy translations into a Singles list. These are exact roles, not an
implicit traversal of all electronic subgenres. Existing repaired GenreText and numeric Genre facts
retain their identity and spelling.

The build generates immutable Kotlin data from the selected configuration. Riff supplies one
`CatalogVocabulary` to Cuetree's scan and publication-only TTL paths. There is no runtime file,
UI-language switch, language detection, user setting or fallback EN/RU classifier. Other Cuetree
consumers configure their own vocabulary; an omitted vocabulary contributes no lexical evidence.

Canonicalize only comparison copies. Escape configured words before constructing boundary/suffix
matchers; malformed bracket pairs and protected numbered works remain unstripped. The existing
weighted grouping, release guards, thresholds and basket routing remain in their respective owners.

The metadata compatibility fingerprint includes the canonical configured vocabulary using XXH3-64.
Changing languages, vocabulary or semantic roles invalidates derived facts through the existing full
recreation policy. No partial migration or device reset is performed by editing this config.

Verify a newly configured test language without changing Kotlin, missing-locale rejection, non-Latin
text, localized GNRE aliases, ordinary grouping, generic credits, protected titles, FILF exclusions,
persistence and vocabulary-preserving TTL reclassification.
