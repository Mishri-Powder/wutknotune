# MediaTime

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Exact nonnegative rational time in ticks/timescale. File identity belongs to Position, not this
value.

## Declaration

```kotlin
data class MediaTime(
    val ticks: Long,
    val timescale: Long,
) : Comparable<MediaTime>
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter   | Meaning                             |
| ----------- | ----------------------------------- |
| `ticks`     | Nonnegative tick count.             |
| `timescale` | Strictly positive ticks per second. |

## Member details

### `compareTo(other: MediaTime): Int`

Compares exact rational values using arbitrary-precision products; does not round through floating
point. Data-class equality still compares stored fields, so rationally equal times with different
scales can compare as zero but not be equal.

### `milliseconds(): Long`

Floors ticks × 1000 / timescale using checked integer arithmetic. Throws ArithmeticException if the
result does not fit Long. Constructor rejects invalid signs with IllegalArgumentException.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val cueTime = MediaTime(76, 75)
check(cueTime.milliseconds() == 1013L)
check(MediaTime(75, 75).compareTo(MediaTime(1, 1)) == 0)
check(MediaTime(75, 75) != MediaTime(1, 1))
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Position](Position.md).
