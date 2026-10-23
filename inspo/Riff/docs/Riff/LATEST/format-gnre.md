# GNRE format

> Owner/scope: GNRE wire schema and representation; no indexing procedure

## 8. Exact GNRE schema and delivery

GNRE contains localized aliases and a cycle-tolerant graph; readers bound graph traversal. Its
generation is the bundled corpus revision, not an Indexer publication epoch. It has no
extracted/private mutable authority.

Preserve all 64 ID bits, including negative synthetic evidence represented through ulong wire
fields. Build keyed vectors in their required wire-key order and do not renumber Q IDs. Names group
by locale with en first and remaining locales sorted alphabetically; the preferred name comes first
in each group. Display selects the first exact user-locale name, otherwise first English, otherwise
first available. Names also support registered GP compound comparison.

```fbs
enum GenreRelationEnum : ubyte {
  based_on = 0,
  basis_for = 1,
  child = 2,
  different_from = 3,
  facet_of = 4,
  followed_by = 5,
  follows = 6,
  fusion_of = 7,
  has_facet = 8,
  has_fusion_genre = 9,
  has_part = 10,
  influenced = 11,
  influenced_by = 12,
  inspired = 13,
  inspired_by = 14,
  parent = 15,
  part_of = 16,
  same_as = 17,
}

table GenreIndex {

  id:ulong (key);
  genre_index:int32;
}

table GenreRelation {
  kind:GenreRelationEnum;
  genre_index:int32;
}

table GenreName {
  locale:string (required);
  name:string (required);
}

table Genre {

  id:ulong (key);

  related:[GenreRelation];

  names:[GenreName] (required);
}

table GenreList {

  generation:int32;
  genres:[Genre] (required);
  indexes:[GenreIndex] (required);
}

root_type GenreList;
file_identifier "GNRE";
```

- `GenreList.generation` is `6` for the adopted corpus that includes project-required Audiobook
  `Q106833` and Live recording `Q56123235` identities with their supported localized aliases.
- The schema payload is packaged as uncompressed `assets/gnre/gnre.fb` and parsed directly from a
  read-only mapping of the APK asset's exact descriptor offset and length.
- There is no runtime GNRE decompression, extracted GNRE copy, GNRE checksum sidecar, or GNRE
  storage migration.
- The external artifact envelope carries `offset`, `length`, `schemaVersion`, `semanticVersion`,
  `generation`, and `xxh3`; offset is not a new FlatBuffer field.

GNRE readers validate keyed-vector order, indexes, required names, supported locales and bounded
references. Attribute and media invariants belong to [Cuetree](../../Cuetree/persistence.md).

GNRE localized names and project additions follow [Supported genre locales](genre-locales.md) and
[Project genre additions](project-genre-additions.md). Only configured locales enter corpus
generation 6; unsupported-language names and lookup aliases are not retained. GNRE wire schema
remains 1.
