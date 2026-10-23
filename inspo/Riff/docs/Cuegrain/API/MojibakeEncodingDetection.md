# MojibakeEncodingDetection

Package: `link.roadio.cuegrain.repair`. [All classes](../API.md) · [Method index](methods.md)

Bounded detection evidence supplied to MojibakeRepair.

## Declaration

```kotlin
data class MojibakeEncodingDetection(
    val encoding: MojibakeEncoding,
    val isReliable: Boolean,
    val bytesConsumed: Int,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter       | Meaning                                                                  |
| --------------- | ------------------------------------------------------------------------ |
| `encoding`      | MojibakeEncoding classification.                                         |
| `isReliable`    | Whether the detector supports that classification reliably.              |
| `bytesConsumed` | Number of inspected input bytes; invalid evidence is rejected by repair. |

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val evidence = link.roadio.cuegrain.repair.MojibakeEncodingDetection(
    link.roadio.cuegrain.repair.MojibakeEncoding.OTHER, false, 0,
)
check(!evidence.isReliable)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/repair/MojibakeEncodingDetector.kt`. This
reference describes the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [MojibakeEncoding](MojibakeEncoding.md),
[MojibakeRepair](MojibakeRepair.md).
