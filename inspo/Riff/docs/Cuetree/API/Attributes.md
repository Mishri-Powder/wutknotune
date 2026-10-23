# Cuetree API — attributes

## AttributeKind and AttributeValue

`AttributeKind(code, key, numeric)` defines stable wire codes and canonical metadata keys. The
[typed attribute contract](../attributes.md) owns the kind table and exact hash framing.

`AttributeValue(kind, text: String? = null, number: Long? = null)` accepts exactly one value
matching the kind. UNKNOWN is rejected. `id: MediaId` is the exact typed-value hash with absent low.
Numeric zero is a present value. Same text with different kinds has distinct framed input. EXTENSION
(127) preserves consumer metadata keys outside the MVP kinds, including noncanonical numeric
strings. Its text is `<key length>:<key><value>`; key length counts JVM UTF-16 code units. The
complete text is UTF-8 in the hash and wire string. Repeated values retain their order in the
referring record.

```kotlin
val year = AttributeValue(AttributeKind.YEAR, number = 0)
val title = AttributeValue(AttributeKind.TITLE, text = "Example")
check(year.id.lo == null)
check(title.id != AttributeValue(AttributeKind.ARTIST, text = "Example").id)
```

## AttributeRecord

`AttributeRecord(value, life)` exposes computed `id` and flags (IDHI plus ACSS). The indexer interns
values in `Snapshot.attributeCatalog` and refreshes generation from retained MDIA references
regardless of availability. Orphan display values are retained with their owning catalog without
refreshing them. Different values with a colliding ID fail publication. Missing dictionary
references fail validation.

## JVM and FlatBuffers views

Node/Album `attributes` remain `Map<String, List<String>>`; Artist/Genre expose computed maps.
Indexer normalization separates valid track/disc number and total fields and projects year from
date. Fragment and TotalFragments are populated from validated structures. Cuegrain retains parsing
ownership.

On disk, each record's attributes vector contains common IDs. `flat.attribute(id)` performs binary
lookup in MDIA's dictionary and returns the generated Attribute accessor, or null. Its `valueText()`
and `hasValueLong()`/`valueLong()` distinguish text and numeric presence. Loading validates IDs,
types, references and ordering before exposing these accessors.

`Snapshot.withCompleteAttributeCatalog()` interns all current factual and projection references and
verifies exact-value collisions after a consumer adds projection attributes. It retains existing
unreferenced values without applying TTL; the factual scan or `advancePublication` owns the normal
lifetime pass. It does not change source identity or availability.
