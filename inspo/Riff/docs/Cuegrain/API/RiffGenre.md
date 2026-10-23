# RiffGenre

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

One ordered RIFF_GENRE text observation and optional adjacent confidence.

## Declaration

```kotlin
data class RiffGenre(
    val value: String,
    val confidence: Float? = null,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter    | Meaning                                                                        |
| ------------ | ------------------------------------------------------------------------------ |
| `value`      | Nonblank genre text from a valid raw field; no dictionary lookup is performed. |
| `confidence` | Optional confidence paired only with the immediately preceding RIFF_GENRE.     |

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val genre = RiffGenre("Rock", confidence = 0.8f)
check(genre.value == "Rock")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/RiffFeatures.kt`. This reference
describes the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md).
