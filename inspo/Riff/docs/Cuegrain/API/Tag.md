# Tag

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

One raw textual field with preserved original evidence and optional format identity.

## Declaration

```kotlin
data class Tag(
    val key: String,
    val value: String,
    val original: String = value,
    val location: String? = null,
    val formatKey: String? = null,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter   | Meaning                                                                                |
| ----------- | -------------------------------------------------------------------------------------- |
| `key`       | Logical tag key, including unknown keys.                                               |
| `value`     | Current decoded value, possibly conservatively repaired.                               |
| `original`  | Original decoded text; defaults to value. This is not the original encoded byte array. |
| `location`  | Optional source-specific location string.                                              |
| `formatKey` | Optional original frame key when key is a description, for example WXXX.               |

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val original = Tag("TITLE", "Example")
val edited = original.copy(value = "Display value")
check(edited.original == "Example")
val url = Tag("Home", "https://example.org", formatKey = "WXXX")
check(url.formatKey == "WXXX")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md).
