# RiffFeatures

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Typed RIFF analysis observations belonging to one Source or Entry. These field names describe
interoperable metadata, not a dependency on the Riff application.

## Declaration

```kotlin
data class RiffFeatures(
    val arousal: RiffMetric? = null,
    val valence: RiffMetric? = null,
    val pte: RiffMetric? = null,
    val genres: List<RiffGenre> = emptyList(),
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter | Meaning                                    |
| --------- | ------------------------------------------ |
| `arousal` | Optional RiffMetric for RIFF_AROUSAL.      |
| `valence` | Optional RiffMetric for RIFF_VALENCE.      |
| `pte`     | Optional RiffMetric for RIFF_PTE.          |
| `genres`  | Ordered repeatable RiffGenre observations. |

## Member details

Read through Source.riffFeatures, Entry.riffFeatures or Metadata.riffFeatures. Singular duplicate
metrics preserve the first valid value and produce diagnostics during Cuegrain.read; invalid
evidence remains in raw tags. Genre confidence is positional and must immediately follow its genre.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val features = Metadata.riffFeatures(
    listOf(Tag("RIFF_AROUSAL", "0.5"), Tag("RIFF_GENRE", "Rock"), Tag("RIFF_GENRE_CONFIDENCE", "0.8")),
)
check(features.arousal == RiffMetric(0.5f))
check(features.genres.single().confidence == 0.8f)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/RiffFeatures.kt`. This reference
describes the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Entry](Entry.md), [Metadata](Metadata.md),
[RiffGenre](RiffGenre.md), [RiffMetric](RiffMetric.md), [Source](Source.md).
