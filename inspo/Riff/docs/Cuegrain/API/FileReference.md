# FileReference

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

A file declared by CUE, with optional caller-supplied input resolution.

## Declaration

```kotlin
data class FileReference(
    val id: String,
    val path: String,
    val declaredType: String? = null,
    val resolvedInput: String? = null,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter       | Meaning                                                                               |
| --------------- | ------------------------------------------------------------------------------------- |
| `id`            | Reference identifier used by Position.file; not necessarily a filesystem path.        |
| `path`          | Declared file text. It may be relative and is not automatically normalized or opened. |
| `declaredType`  | Optional declared CUE type, such as WAVE.                                             |
| `resolvedInput` | Optional resolved MediaInput.id. Absence is not permission to guess.                  |

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val reference = FileReference("file-1", "disc.flac", declaredType = "WAVE")
val bound = reference.copy(resolvedInput = "authorized-input")
check(bound.id == reference.id)
check(bound.resolvedInput == "authorized-input")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [MediaInput](MediaInput.md), [Position](Position.md).
