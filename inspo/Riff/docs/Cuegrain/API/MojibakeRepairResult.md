# MojibakeRepairResult

Package: `link.roadio.cuegrain.repair`. [All classes](../API.md) · [Method index](methods.md)

Per-field result of the grouped conservative repair decision.

## Declaration

```kotlin
data class MojibakeRepairResult(
    val value: String,
    val repaired: Boolean,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter  | Meaning                                                         |
| ---------- | --------------------------------------------------------------- |
| `value`    | Accepted repaired string or unchanged original.                 |
| `repaired` | True exactly when the accepted value differs from the original. |

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val result = link.roadio.cuegrain.repair.MojibakeRepairResult("Title", false)
check(!result.repaired)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/repair/MojibakeRepair.kt`. This reference
describes the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md).
