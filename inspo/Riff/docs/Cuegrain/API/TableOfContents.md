# TableOfContents

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Flat TOC graph node, notably ID3 CTOC. Kept separate from the chronological Entry sequence.

## Declaration

```kotlin
data class TableOfContents(
    val id: String,
    val ordered: Boolean,
    val root: Boolean,
    val children: List<String>,
    val tags: List<Tag> = emptyList(),
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter  | Meaning                                            |
| ---------- | -------------------------------------------------- |
| `id`       | TOC element identifier.                            |
| `ordered`  | Whether the source declares child ordering.        |
| `root`     | Whether the source declares a top-level table.     |
| `children` | Referenced element IDs, preserving declared order. |
| `tags`     | TOC-local tags, such as a title.                   |

## Member details

The model does not automatically traverse the graph, resolve cycles or select a navigation root.
Consumers must not assume a table is a new playback timeline.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val table = TableOfContents("root", ordered = true, root = true, children = listOf("chapter-1"))
check(table.children.single() == "chapter-1")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Entry](Entry.md), [Tag](Tag.md).
