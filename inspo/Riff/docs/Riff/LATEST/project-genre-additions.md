# Project genre additions

> Owner/scope: project-added GNRE identities, aliases and classifier evidence nodes

## Single source

`config/project.json` member `genreAdditions` is the only data source for genres or genre evidence
nodes added by the project beyond the retrieved Wikidata/MusicBrainz corpus. Python, Kotlin, JSON
sidecars and Wiki tables MUST NOT carry an independent addition list.

`genreAdditions.required` is keyed by a positive Wikidata Q-ID. Every record contains `labels` with
exactly the configured `genreLocales`; every locale contains a nonempty ordered alias list. The
first name for a locale is its preferred display name. Generation prepends these configured names to
retrieved names and creates the Q-ID record when retrieval did not supply it.

`genreAdditions.mtgEvidence` contains `firstId`, the ordered append-only `labels` array and the
`explicitSameAs` object. A row's stable ID is `firstId` minus its array index. Absence from
`explicitSameAs` requests unambiguous generated RIFF-TAG mapping, an explicit Q-ID requests that
exact canonical target, and explicit `null` requires a standalone node. Configuration validates
ID/label uniqueness, permanent descending allocation and every positive target before publication.

## Required semantic identities

- `Q106833`, Audiobook. Preferred names are en `audiobook`, es `audiolibro`, de `Hörbuch`, ru
  `аудиокнига`, pt `audiolivro`. Configured aliases include all adopted ordinary and iTunes
  spellings, including `Books & Spoken`, `Libros y voz` and `Livros e Discursos`.
- `Q56123235`, Live recording. Preferred names are en `Live`, es `En vivo`, de `Live`, ru
  `Концертная запись`, pt `Ao vivo`. Lookup aliases cover `Live performance`, `Live recording`,
  Spanish `En directo` and the full actuación/grabación forms, German
  `Liveaufnahme`/`Live-Aufnahme`/`Live-Auftritt`, Russian `Живое исполнение`/`Живое выступление`,
  and Portuguese `Gravação ao vivo`/`Apresentação ao vivo`.

`Live` describes recording provenance, not musical style. The recording identity is deliberately
used instead of the narrower live-album identity, so an individual live track resolves normally.

## Publication

The adopted corpus generation containing both required identities is GNRE generation 6; wire schema
remains 1. Changing additions or aliases requires generation advancement, asset regeneration,
manifest dependency invalidation and full generator/reader verification.

[Catalog language evidence](/Riff/LATEST/catalog-language-evidence) assigns existing GNRE IDs to
classifier roles. It does not create another genre-name or alias dictionary.
