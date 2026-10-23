# RiffMetric

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

One finite analysis metric observation and optional confidence. Parsing validates finite decimal
values; construction is a plain data-model operation.

## Declaration

```kotlin
data class RiffMetric(
    val value: Float,
    val confidence: Float? = null,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter    | Meaning                                                                                 |
| ------------ | --------------------------------------------------------------------------------------- |
| `value`      | Observed arousal, valence or PTE value; no classifier execution or clamping is implied. |
| `confidence` | Optional supplied confidence. Null is unknown, never inferred zero or one.              |

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val metric = RiffMetric(0.5f)
check(metric.confidence == null)
check(metric.copy(confidence = 0.8f).value == 0.5f)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/RiffFeatures.kt`. This reference
describes the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md).
