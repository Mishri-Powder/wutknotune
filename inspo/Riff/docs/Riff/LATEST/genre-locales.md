# Genre locales

> Owner/scope: project-wide genre locale set and retrieval procedure

## Supported genre locales

The project-wide source `config/project.json` declares exactly
`genreLocales = ["en", "es", "de", "ru", "pt"]`. Retrieval, project additions, GNRE
generation/verification and generated Android language data MUST consume this source. No independent
independent tool default exists. Ordinary builds use validated cached inputs and do not fetch
translations. Explicit locale maintenance fetches the supported official names and regenerates the
corpus.

All manually supplied genre identities and strings live only in
[`genreAdditions`](project-genre-additions.md) in the same configuration file. Every required
semantic identity MUST cover exactly the supported locale set; adding a locale therefore requires
adding its ordered names in the same configuration change. Runtime special cases and parallel alias
constants are prohibited. Corpus generation is 6; wire schema remains 1 and ordinary GNRE-generation
metadata invalidation applies. Display selects the first exact supported system-language name, then
English, then first available. Original media tags and identifiers are not rewritten by the locale
list.

Genre retrieval is an explicit host Gradle data operation using
`tools/genre-retrieval/{wikidata_indx.py,wikidata_i18n.py,musicbrainz.py,wikidata_musicbrainz_merge.py}`
and its declared dependencies: Wikidata graph with multilingual labels/official aliases, MusicBrainz
with contactable User-Agent/resumable paging, MusicBrainz-priority merge and union edges. Persist
completed/intermediate cache under `tools/genre-retrieval/cache/`. Valid completed
`genres.merged.json` suppresses network absent explicit refresh; reuse valid intermediates and
resume checkpoints. Each stage validates a temporary document before atomic replacement. Invalid
cache requires explicit refresh, never implicit redownload. Supply contact only through
`RIFF_GENRE_CONTACT` or `genreContact`, without persisting it in project artifacts. Ordinary
build/test/install/runtime has no taxonomy network access. Retrieval produces validated source
input; GNRE publication/packaging validates separately.
