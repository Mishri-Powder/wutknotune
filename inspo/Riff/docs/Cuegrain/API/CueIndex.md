# CueIndex

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

One CUE INDEX statement, preserving its number and individual file-local position.

## Declaration

```kotlin
data class CueIndex(
    val number: Int,
    val position: Position,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter  | Meaning                                                                                 |
| ---------- | --------------------------------------------------------------------------------------- |
| `number`   | Declared index number: parsers validate CUE range 0..99; construction itself does not.  |
| `position` | Position for this index, which may differ in file from another index of the same track. |

## Member details

INDEX 00 and INDEX 01 are distinct. Synthetic PREGAP/POSTGAP belong to Entry, not invented CueIndex
values.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val index = CueIndex(1, Position("file-1", MediaTime(0, 75)))
check(index.number == 1)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Entry](Entry.md), [Position](Position.md).
