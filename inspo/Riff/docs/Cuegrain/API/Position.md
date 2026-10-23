# Position

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Pairs an input/file-reference identity with an exact time in that file.

## Declaration

```kotlin
data class Position(
    val file: String,
    val time: MediaTime,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter | Meaning                                                                   |
| --------- | ------------------------------------------------------------------------- |
| `file`    | File reference ID or input ID according to the containing representation. |
| `time`    | Exact file-local MediaTime.                                               |

## Member details

A Position does not define ordering across distinct files. Comparing its time to another file's time
does not construct an album-wide playback position.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val start = Position("disc-1/file/0", MediaTime(150, 75))
check(start.time.milliseconds() == 2000L)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [MediaTime](MediaTime.md).
