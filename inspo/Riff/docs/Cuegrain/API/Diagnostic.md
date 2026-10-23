# Diagnostic

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

One bounded parser observation. Codes support programmatic handling; messages are explanatory text.

## Declaration

```kotlin
data class Diagnostic(
    val code: String,
    val severity: String,
    val source: String,
    val location: String? = null,
    val message: String,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter  | Meaning                                                               |
| ---------- | --------------------------------------------------------------------- |
| `code`     | Stable diagnostic identifier, such as INPUT_IO or FORMAT_UNSUPPORTED. |
| `severity` | warning or error in built-in readers; represented as String.          |
| `source`   | Originating input identifier.                                         |
| `location` | Optional reader-specific offset/line/field location.                  |
| `message`  | Human-readable explanation; not a stable machine protocol.            |

## Member details

A diagnostic is not a thrown exception. Cancellation is deliberately thrown, while read-time
IOException becomes INPUT_IO. A capped diagnostic list ends with LIMIT_DIAGNOSTICS when additional
entries are suppressed.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val diagnostic = Diagnostic(
    code = "EXAMPLE_MISSING_TITLE",
    severity = "warning",
    source = "memory",
    message = "No title supplied",
)
check(diagnostic.location == null)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md).
