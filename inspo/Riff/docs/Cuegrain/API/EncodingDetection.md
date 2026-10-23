# EncodingDetection

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Result of a caller-supplied encoding detector. Reliability and consumed-byte evidence remain
explicit.

## Declaration

```kotlin
data class EncodingDetection(
    val charset: String?,
    val isReliable: Boolean,
    val bytesConsumed: Int,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter       | Meaning                                                                              |
| --------------- | ------------------------------------------------------------------------------------ |
| `charset`       | Supported charset name, or null when unavailable.                                    |
| `isReliable`    | Detector's reliability result; a guess is not automatically accepted.                |
| `bytesConsumed` | Bytes used by detection; CUE acceptance requires a value within the supplied sample. |

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val detection = EncodingDetection("UTF-8", isReliable = true, bytesConsumed = 3)
check(detection.charset == "UTF-8")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md).
